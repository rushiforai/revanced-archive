package app.revanced.extension.dcinside.voice;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/**
 * Normalize a picked audio file into the MPEG-4/AAC clip the recorder produces, using platform APIs
 * only (the app's bundled media3 Transformer is R8-obfuscated and not cleanly reusable).
 *
 * {@link #plan} classifies the source up front, so the caller knows whether the import is an instant
 * byte copy or a real conversion worth a wait notice:
 * <ul>
 *   <li>{@link #PLAN_COPY} — already an audio-only MPEG-4/AAC file, exactly what the recorder
 *       writes: <b>copy</b> the bytes.</li>
 *   <li>{@link #PLAN_REMUX} — an AAC track in another container (ADTS .aac, .3gp) or beside other
 *       tracks: <b>remux</b> into MPEG-4, no re-encode.</li>
 *   <li>{@link #PLAN_TRANSCODE} — anything else (mp3/wav/ogg/flac/…): <b>transcode</b> to AAC.</li>
 *   <li>{@link #PLAN_UNSUPPORTED} — nothing the device can read as audio.</li>
 * </ul>
 */
final class AudioNormalizer {
    private AudioNormalizer() {}

    static final int PLAN_UNSUPPORTED = 0;
    static final int PLAN_COPY = 1;
    static final int PLAN_REMUX = 2;
    static final int PLAN_TRANSCODE = 3;

    private static final int COPY_BUFFER = 64 * 1024;
    private static final int REMUX_FALLBACK_BUFFER = 256 * 1024;
    private static final int ENCODER_BITRATE = 128_000;

    /** Poll the codecs; only a pass that moved nothing waits, and then inside a dequeue. */
    private static final long POLL_NOW = 0;
    private static final long IDLE_WAIT_US = 1_000;
    /** Bail out rather than pump forever if the codecs go silent (truncated / corrupt source). */
    private static final long MAX_IDLE_NS = 10L * 1_000_000_000L;

    // --- classification -----------------------------------------------------------------------

    /** @return the {@code PLAN_*} that turns {@code src} into the recorder's MPEG-4/AAC clip. */
    static int plan(Context context, Uri src) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, src, null);

            String audioMime = null;
            boolean otherTracks = false;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (audioMime == null && mime != null && mime.startsWith("audio/")) {
                    audioMime = mime;
                } else {
                    otherTracks = true;
                }
            }
            if (audioMime == null) return PLAN_UNSUPPORTED;
            if (!MediaFormat.MIMETYPE_AUDIO_AAC.equals(audioMime)) return PLAN_TRANSCODE;
            return !otherTracks && isMpeg4(context, src) ? PLAN_COPY : PLAN_REMUX;
        } catch (Throwable t) {
            return PLAN_UNSUPPORTED;
        } finally {
            releaseQuietly(extractor);
        }
    }

    /** True for an ISO base media file ({@code ....ftyp} box first) — .m4a/.mp4 as opposed to ADTS. */
    private static boolean isMpeg4(Context context, Uri src) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(src);
            if (in == null) return false;
            byte[] head = new byte[12];
            int read = 0;
            while (read < head.length) {
                int n = in.read(head, read, head.length - read);
                if (n < 0) break;
                read += n;
            }
            return read == head.length
                    && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    // --- execution ----------------------------------------------------------------------------

    /** How far a conversion has got, 0-100, called on the thread driving {@link #normalize}. */
    interface Progress {
        void onProgress(int percent);
    }

    /**
     * Carry out {@code plan} from {@link #plan} (or {@link #PLAN_COPY} when the user forces the
     * original format).
     *
     * @param progress notified as the conversion advances; null, or never called, when there is
     *                 nothing to convert or the source does not say how long it is.
     * @return true if {@code dst} now holds the clip to upload; false (and no {@code dst}) on failure.
     */
    static boolean normalize(Context context, Uri src, File dst, int plan, Progress progress) {
        boolean ok = plan == PLAN_COPY ? copy(context, src, dst) : convert(context, src, dst, progress);
        if (!ok) deleteQuietly(dst);
        return ok;
    }

    /** Report only whole percent changes: at most a hundred hops to whoever is listening. */
    private static int report(Progress progress, int reported, long done, long total) {
        if (progress == null || total <= 0) return reported;
        int percent = (int) Math.min(100L, 100L * done / total);
        if (percent <= reported) return reported;
        progress.onProgress(percent);
        return percent;
    }

    private static boolean copy(Context context, Uri src, File dst) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(src);
            if (in == null) return false;
            out = new FileOutputStream(dst);
            byte[] buffer = new byte[COPY_BUFFER];
            while (true) {
                int n = in.read(buffer);
                if (n < 0) break;
                out.write(buffer, 0, n);
            }
            out.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            closeQuietly(out);
            closeQuietly(in);
        }
    }

    /** Remux or transcode, decided by the codec the source actually carries. */
    private static boolean convert(Context context, Uri src, File dst, Progress progress) {
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
            if (audioTrack < 0) return false;

            // Without a duration there is nothing to measure progress against; the caller then
            // leaves its indicator indeterminate rather than inventing a number.
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION)
                    : 0;

            boolean ok = MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime)
                    ? remux(extractor, audioTrack, format, dst, progress, durationUs)
                    : transcode(extractor, audioTrack, format, dst, progress, durationUs);
            if (ok && progress != null) progress.onProgress(100);
            return ok;
        } catch (Throwable t) {
            return false;
        } finally {
            releaseQuietly(extractor);
        }
    }

    // --- AAC container -> MPEG-4, no re-encode ------------------------------------------------

    private static boolean remux(MediaExtractor extractor, int track, MediaFormat format, File dst,
                                Progress progress, long durationUs) {
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
            int reported = 0;
            while (true) {
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) break;
                info.offset = 0;
                info.size = size;
                info.presentationTimeUs = extractor.getSampleTime();
                info.flags = MediaCodec.BUFFER_FLAG_KEY_FRAME;
                muxer.writeSampleData(outTrack, buffer, info);
                extractor.advance();
                reported = report(progress, reported, info.presentationTimeUs, durationUs);
            }
            muxer.stop();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            releaseQuietly(muxer);
        }
    }

    // --- non-AAC -> AAC/MPEG-4, one streaming pass --------------------------------------------

    /**
     * Decode to PCM and re-encode to AAC in a single pass: source samples go to the decoder, decoded
     * PCM straight into the encoder's input buffers, encoded frames straight into the muxer.
     *
     * Every dequeue polls with a zero timeout, so no side ever waits while the other has work; a pass
     * that moves nothing at all is the only one that waits, and it does so inside a dequeue, waking
     * the instant that codec has something. That is what makes it fast. The two-pass version this
     * replaces spooled the whole PCM stream through a temp file and blocked up to 10 ms per dequeue
     * attempt, so whichever side was saturated stalled once per pass — the encoder's input, since one
     * 16 KB PCM chunk is four AAC frames. Measured against the fake codecs in local/verify-audio,
     * that cost 19.2 s of dead waiting per minute of stereo 44.1 kHz audio, where this pump waits 0.
     */
    private static boolean transcode(MediaExtractor extractor, int track, MediaFormat srcFormat, File dst,
                                    Progress progress, long durationUs) {
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        try {
            extractor.selectTrack(track);
            srcFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            decoder = MediaCodec.createDecoderByType(srcFormat.getString(MediaFormat.KEY_MIME));
            decoder.configure(srcFormat, null, null, 0);
            decoder.start();
            muxer = new MediaMuxer(dst.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            MediaCodec.BufferInfo decoded = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encoded = new MediaCodec.BufferInfo();
            int pcmIndex = -1;      // decoder output buffer held while it feeds the encoder
            ByteBuffer pcm = null;
            int muxTrack = -1;
            int bytesPerFrame = 0;
            int sampleRate = 0;
            long framesQueued = 0;  // PCM frames handed to the encoder, i.e. the next timestamp
            boolean sourceDone = false;
            boolean decodeDone = false;
            boolean encodeInputDone = false;
            long idleSince = 0;     // when the pump last moved nothing, to catch a codec gone silent
            long totalFrames = 0;   // PCM frames the source says it holds, for progress
            int reported = 0;

            while (true) {
                boolean moved = false;
                // Only a pass that follows a fully idle one waits, and only once nothing else moved.
                boolean mayWait = idleSince != 0;

                // 1. source -> decoder
                if (!sourceDone) {
                    int index = decoder.dequeueInputBuffer(POLL_NOW);
                    if (index >= 0) {
                        ByteBuffer in = decoder.getInputBuffer(index);
                        int size = in == null ? -1 : extractor.readSampleData(in, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            sourceDone = true;
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        moved = true;
                    }
                }

                // 2. decoder -> held PCM buffer; its output format defines the encoder
                if (!decodeDone && pcmIndex < 0) {
                    int index = decoder.dequeueOutputBuffer(decoded,
                            encoder == null && mayWait && !moved ? IDLE_WAIT_US : POLL_NOW);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat pcmFormat = decoder.getOutputFormat();
                        if (pcmFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
                                && pcmFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                != AudioFormat.ENCODING_PCM_16BIT) {
                            return false; // the pump copies 16-bit PCM only
                        }
                        sampleRate = pcmFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int channels = pcmFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        if (sampleRate <= 0 || channels <= 0) return false;
                        bytesPerFrame = 2 * channels;
                        totalFrames = durationUs / 1_000L * sampleRate / 1_000L;
                        encoder = startEncoder(sampleRate, channels);
                        moved = true;
                    } else if (index >= 0) {
                        if (decoded.size > 0) {
                            if (encoder == null) return false; // PCM before a format: nothing to encode into
                            pcm = decoder.getOutputBuffer(index);
                            pcm.position(decoded.offset);
                            pcm.limit(decoded.offset + decoded.size);
                            pcmIndex = index;
                        } else {
                            decoder.releaseOutputBuffer(index, false);
                        }
                        if ((decoded.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) decodeDone = true;
                        moved = true;
                    }
                }

                // 3. held PCM -> encoder, as many input buffers as it will take right now
                while (pcmIndex >= 0) {
                    int index = encoder.dequeueInputBuffer(POLL_NOW);
                    if (index < 0) break;
                    ByteBuffer in = encoder.getInputBuffer(index);
                    in.clear();
                    int size = Math.min(in.remaining(), pcm.remaining());
                    int wholeFrames = size - (size % bytesPerFrame);
                    if (wholeFrames > 0) size = wholeFrames; // never split a frame across buffers
                    int limit = pcm.limit();
                    pcm.limit(pcm.position() + size);
                    in.put(pcm);
                    pcm.limit(limit);
                    encoder.queueInputBuffer(index, 0, size, 1_000_000L * framesQueued / sampleRate, 0);
                    framesQueued += size / bytesPerFrame;
                    reported = report(progress, reported, framesQueued, totalFrames);
                    if (!pcm.hasRemaining()) {
                        decoder.releaseOutputBuffer(pcmIndex, false);
                        pcmIndex = -1;
                        pcm = null;
                    }
                    moved = true;
                }

                // 4. all PCM handed over -> tell the encoder
                if (decodeDone && pcmIndex < 0 && !encodeInputDone) {
                    if (encoder == null) return false; // the source decoded to nothing
                    int index = encoder.dequeueInputBuffer(POLL_NOW);
                    if (index >= 0) {
                        encoder.queueInputBuffer(index, 0, 0, 1_000_000L * framesQueued / sampleRate,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        encodeInputDone = true;
                        moved = true;
                    }
                }

                // 5. encoder -> muxer
                while (encoder != null) {
                    int index = encoder.dequeueOutputBuffer(encoded,
                            mayWait && !moved ? IDLE_WAIT_US : POLL_NOW);
                    mayWait = false;
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = muxer.addTrack(encoder.getOutputFormat());
                        muxer.start();
                        moved = true;
                        continue;
                    }
                    if (index < 0) break;
                    if ((encoded.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) encoded.size = 0;
                    if (encoded.size > 0 && muxTrack >= 0) {
                        ByteBuffer out = encoder.getOutputBuffer(index);
                        out.position(encoded.offset);
                        out.limit(encoded.offset + encoded.size);
                        muxer.writeSampleData(muxTrack, out, encoded);
                    }
                    boolean eos = (encoded.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(index, false);
                    moved = true;
                    if (eos) {
                        if (muxTrack < 0) return false;
                        muxer.stop();
                        return true;
                    }
                }

                if (moved) {
                    idleSince = 0;
                } else if (idleSince == 0) {
                    idleSince = System.nanoTime();
                } else if (System.nanoTime() - idleSince > MAX_IDLE_NS) {
                    return false;
                }
            }
        } catch (Throwable t) {
            return false;
        } finally {
            stopQuietly(decoder);
            stopQuietly(encoder);
            releaseQuietly(muxer);
        }
    }

    private static MediaCodec startEncoder(int sampleRate, int channels) throws IOException {
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, ENCODER_BITRATE);
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder;
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
