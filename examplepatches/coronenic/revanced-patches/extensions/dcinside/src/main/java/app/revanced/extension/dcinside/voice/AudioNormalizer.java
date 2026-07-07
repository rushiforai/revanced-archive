package app.revanced.extension.dcinside.voice;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Normalize a picked audio file to the MPEG-4/AAC clip the recorder produces, using platform APIs
 * only (the app's bundled media3 Transformer is R8-obfuscated and not cleanly reusable).
 *
 * <ul>
 *   <li>Source already carries an AAC track (.m4a/.mp4/.aac/.3gp/…) → <b>remux</b> into an MPEG-4
 *       container, no re-encode.</li>
 *   <li>Otherwise (mp3/wav/ogg/flac/…) → <b>transcode</b> to AAC via a two-pass MediaCodec pipeline
 *       (decode → PCM temp → encode), muxed into MPEG-4.</li>
 * </ul>
 */
final class AudioNormalizer {
    private AudioNormalizer() {}

    private static final int REMUX_FALLBACK_BUFFER = 256 * 1024;
    private static final int ENCODER_BITRATE = 128_000;
    private static final long DEQUEUE_TIMEOUT_US = 10_000;

    /** @return true if {@code dst} now holds an MPEG-4/AAC clip; false if the source has no decodable audio or on failure. */
    static boolean toM4a(Context context, Uri src, File dst) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, src, null);

            int audioTrack = -1;
            MediaFormat format = null;
            String mime = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    audioTrack = i;
                    format = f;
                    mime = m;
                    break;
                }
            }
            if (audioTrack < 0 || format == null) return false;

            boolean ok = MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)
                    ? remux(extractor, audioTrack, format, dst)
                    : transcode(extractor, audioTrack, format, dst);
            if (!ok) deleteQuietly(dst);
            return ok;
        } catch (Throwable t) {
            deleteQuietly(dst);
            return false;
        } finally {
            releaseQuietly(extractor);
        }
    }

    // --- AAC container -> MPEG-4, no re-encode ------------------------------------------------

    private static boolean remux(MediaExtractor extractor, int track, MediaFormat format, File dst) {
        MediaMuxer muxer = null;
        try {
            extractor.selectTrack(track);
            int bufferSize = REMUX_FALLBACK_BUFFER;
            if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                bufferSize = Math.max(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            }
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            muxer = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int outTrack = muxer.addTrack(format);
            muxer.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            while (true) {
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                muxer.writeSampleData(outTrack, buffer, info);
                extractor.advance();
            }
            muxer.stop();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            releaseQuietly(muxer);
        }
    }

    // --- non-AAC -> AAC/MPEG-4, two passes via a PCM temp file --------------------------------

    private static boolean transcode(MediaExtractor extractor, int track, MediaFormat format, File dst) {
        File pcm = new File(dst.getParentFile(), dst.getName() + ".pcm");
        try {
            int[] params = decodeToPcm(extractor, track, format, pcm); // {sampleRate, channelCount}
            if (params == null) return false;
            return encodePcmToM4a(pcm, params[0], params[1], dst);
        } catch (Throwable t) {
            return false;
        } finally {
            deleteQuietly(pcm);
        }
    }

    /** Decode the source audio track to raw 16-bit PCM in {@code pcmOut}; returns {sampleRate, channels} or null. */
    private static int[] decodeToPcm(MediaExtractor extractor, int track, MediaFormat format, File pcmOut) {
        MediaCodec decoder = null;
        OutputStream out = null;
        try {
            extractor.selectTrack(track);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            out = new BufferedOutputStream(new FileOutputStream(pcmOut));

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            byte[] scratch = null;
            while (true) {
                if (!inputDone) {
                    int in = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (in >= 0) {
                        ByteBuffer buf = decoder.getInputBuffer(in);
                        int size = buf == null ? -1 : extractor.readSampleData(buf, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(in, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = decoder.getOutputFormat();
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                } else if (outIndex >= 0) {
                    if (info.size > 0) {
                        ByteBuffer buf = decoder.getOutputBuffer(outIndex);
                        if (buf != null) {
                            buf.position(info.offset);
                            buf.limit(info.offset + info.size);
                            if (scratch == null || scratch.length < info.size) scratch = new byte[info.size];
                            buf.get(scratch, 0, info.size);
                            out.write(scratch, 0, info.size);
                        }
                    }
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outIndex, false);
                    if (eos) break;
                }
            }
            out.flush();
            if (sampleRate <= 0 || channels <= 0) return null;
            return new int[]{sampleRate, channels};
        } catch (Throwable t) {
            return null;
        } finally {
            closeQuietly(out);
            stopQuietly(decoder);
        }
    }

    /** Encode raw 16-bit PCM to AAC in an MPEG-4 container. */
    private static boolean encodePcmToM4a(File pcm, int sampleRate, int channels, File dst) {
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        InputStream in = null;
        try {
            MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, ENCODER_BITRATE);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            in = new BufferedInputStream(new FileInputStream(pcm));

            int muxTrack = -1;
            boolean muxerStarted = false;
            int bytesPerFrame = 2 * channels; // 16-bit PCM
            long ptsUs = 0;
            byte[] chunk = new byte[16 * 1024];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            while (true) {
                if (!inputDone) {
                    int inIndex = encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
                    if (inIndex >= 0) {
                        ByteBuffer buf = encoder.getInputBuffer(inIndex);
                        buf.clear();
                        int toRead = Math.min(buf.capacity(), chunk.length);
                        int n = in.read(chunk, 0, toRead);
                        if (n < 0) {
                            encoder.queueInputBuffer(inIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            buf.put(chunk, 0, n);
                            encoder.queueInputBuffer(inIndex, 0, n, ptsUs, 0);
                            ptsUs += 1_000_000L * (n / bytesPerFrame) / sampleRate;
                        }
                    }
                }
                int outIndex = encoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    muxTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (outIndex >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                    if (info.size > 0 && muxerStarted) {
                        ByteBuffer buf = encoder.getOutputBuffer(outIndex);
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        muxer.writeSampleData(muxTrack, buf, info);
                    }
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(outIndex, false);
                    if (eos) break;
                }
            }
            muxer.stop();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(in);
            stopQuietly(encoder);
            releaseQuietly(muxer);
        }
    }

    // --- cleanup helpers ----------------------------------------------------------------------

    private static void stopQuietly(MediaCodec codec) {
        if (codec == null) return;
        try { codec.stop(); } catch (Throwable ignored) {}
        try { codec.release(); } catch (Throwable ignored) {}
    }

    private static void releaseQuietly(MediaMuxer muxer) {
        if (muxer == null) return;
        try { muxer.release(); } catch (Throwable ignored) {}
    }

    private static void releaseQuietly(MediaExtractor extractor) {
        if (extractor == null) return;
        try { extractor.release(); } catch (Throwable ignored) {}
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (Throwable ignored) {}
    }

    private static void deleteQuietly(File f) {
        try { if (f != null && f.exists()) f.delete(); } catch (Throwable ignored) {}
    }
}
