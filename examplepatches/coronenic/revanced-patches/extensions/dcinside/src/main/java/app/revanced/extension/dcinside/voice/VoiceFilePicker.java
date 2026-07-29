package app.revanced.extension.dcinside.voice;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;

import app.revanced.extension.dcinside.settings.Settings;

/**
 * "Upload an audio file as a voice reply": normalizes any audio the device can decode into the
 * MPEG-4/AAC clip the recorder produces — copy an audio-only .m4a, remux other AAC containers,
 * transcode everything else (see {@link AudioNormalizer}).
 *
 * The patch adds an upload button (id {@code voice_recorder_file_pick}) to the record tab of
 * DCInside's VoiceRecordView and injects a call to {@link #attach(View)} at the end of the view's
 * constructor. It also injects two stable-named accessor methods onto the (obfuscated) view, which
 * we call by reflection:
 * <ul>
 *   <li>{@code File revancedVoiceTarget()} — the view's record output file ({@code getInputRecord()});
 *       the file recording writes and upload reads.</li>
 *   <li>{@code void revancedVoiceFinalize()} — reuses the view's own stop/finalize so the imported
 *       clip is presented as a finished recording (PLAY_STATE + duration + re-record shown).</li>
 * </ul>
 *
 * Flow: upload button -> {@link AudioPickerActivity} (SAF {@code ACTION_GET_CONTENT audio/*}) ->
 * {@link #deliver(Uri)} -> normalize the picked audio into the record file -> finalize. Submit then
 * uploads it unchanged (RECORD input type). A conversion drives the progress bar the resource patch
 * added to the record area (id {@code voice_recorder_convert_progress}).
 *
 * Uses named nested classes rather than lambdas/anonymous classes: the extension compiles against
 * android.jar with the JDK bootclasspath stripped (no {@code LambdaMetafactory}), and d8 rejects
 * anonymous-in-anonymous classes.
 */
public final class VoiceFilePicker {
    private VoiceFilePicker() {}

    private static final String M_TARGET = "revancedVoiceTarget";
    private static final String M_FINALIZE = "revancedVoiceFinalize";

    private static final String ID_UPLOAD_BUTTON = "voice_recorder_file_pick";
    private static final String ID_CONVERT_PROGRESS = "voice_recorder_convert_progress";

    /**
     * Switch registered by {@code VoiceFilePickerPatch}: upload the picked file byte-for-byte, even
     * when it is not the MPEG-4/AAC the recorder produces. Off by default — whether the server
     * accepts a foreign format is unverified (see local/notes/voice-file-picker-spec.md, tier (c)).
     */
    private static final String KEY_FORCE_ORIGINAL_FORMAT = "voice_force_original_format";

    /** The VoiceRecordView awaiting a pick result. Single-flight; only one picker is open at a time. */
    private static WeakReference<View> pendingView = new WeakReference<View>(null);

    /** Wire the upload button that the resource patch added to the record tab. */
    public static void attach(View recordView) {
        try {
            Context ctx = recordView.getContext();
            int id = ctx.getResources().getIdentifier(
                    ID_UPLOAD_BUTTON, "id", ctx.getPackageName());
            if (id == 0) return;
            View button = recordView.findViewById(id);
            if (button == null) return;
            button.setOnClickListener(new UploadClick(recordView));
        } catch (Throwable ignored) {
        }
    }

    private static void start(View recordView) {
        try {
            Activity activity = activityOf(recordView.getContext());
            if (activity == null) return;
            pendingView = new WeakReference<View>(recordView);
            AudioPickerActivity.start(activity);
        } catch (Throwable t) {
            toast(recordView, "오디오 파일을 불러올 수 없습니다.");
        }
    }

    /** Called by {@link AudioPickerActivity} with the picked audio (null = cancelled). */
    static void deliver(Uri uri) {
        View recordView = pendingView.get();
        pendingView = new WeakReference<View>(null);
        if (recordView == null || uri == null) return;

        Context appContext = recordView.getContext().getApplicationContext();
        File target = invokeFile(recordView, M_TARGET);
        if (target == null) {
            toast(recordView, "오디오 파일을 불러올 수 없습니다.");
            return;
        }
        boolean forceOriginal = Settings.isEnabled(recordView.getContext(), KEY_FORCE_ORIGINAL_FORMAT);
        new Thread(new ImportTask(appContext, uri, target, recordView, forceOriginal),
                "revanced-voice-import").start();
    }

    private static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static File invokeFile(View view, String method) {
        try {
            Object result = view.getClass().getMethod(method).invoke(view);
            return result instanceof File ? (File) result : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void invokeVoid(View view, String method) {
        try {
            view.getClass().getMethod(method).invoke(view);
        } catch (Throwable ignored) {
        }
    }

    static void toast(View view, String message) {
        try {
            Toast.makeText(view.getContext(), message, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    /** The conversion progress bar in the record area, or null if the layout patch is not applied. */
    private static ProgressBar convertProgress(View recordView) {
        try {
            Context ctx = recordView.getContext();
            int id = ctx.getResources().getIdentifier(
                    ID_CONVERT_PROGRESS, "id", ctx.getPackageName());
            if (id == 0) return null;
            View view = recordView.findViewById(id);
            return view instanceof ProgressBar ? (ProgressBar) view : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Upload-button click -> open the picker. */
    private static final class UploadClick implements View.OnClickListener {
        private final View recordView;

        UploadClick(View recordView) {
            this.recordView = recordView;
        }

        @Override
        public void onClick(View v) {
            start(recordView);
        }
    }

    /**
     * Background: work out what the picked audio needs, announce it only if that is a real
     * conversion, write it into the record file, then hand off to the UI thread.
     */
    private static final class ImportTask implements Runnable {
        private final Context context;
        private final Uri source;
        private final File target;
        private final View recordView;
        private final boolean forceOriginal;

        ImportTask(Context context, Uri source, File target, View recordView, boolean forceOriginal) {
            this.context = context;
            this.source = source;
            this.target = target;
            this.recordView = recordView;
            this.forceOriginal = forceOriginal;
        }

        @Override
        public void run() {
            int plan = forceOriginal ? AudioNormalizer.PLAN_COPY : AudioNormalizer.plan(context, source);
            if (plan == AudioNormalizer.PLAN_UNSUPPORTED) {
                recordView.post(new FinishTask(recordView, "지원하지 않는 오디오 파일입니다."));
                return;
            }
            // A copy is instant; only a remux/transcode is worth a notice and a progress bar.
            boolean converting = plan != AudioNormalizer.PLAN_COPY;
            AudioNormalizer.Progress reporter = null;
            if (converting) {
                recordView.post(new ToastTask(recordView, "오디오를 변환하는 중..."));
                recordView.post(new ProgressTask(recordView, ProgressTask.START));
                reporter = new ProgressReporter(recordView);
            }
            boolean ok = AudioNormalizer.normalize(context, source, target, plan, reporter);
            if (converting) recordView.post(new ProgressTask(recordView, ProgressTask.HIDE));
            recordView.post(new FinishTask(recordView, ok ? null
                    : plan == AudioNormalizer.PLAN_COPY
                    ? "오디오 파일을 불러올 수 없습니다."
                    : "오디오 변환에 실패했습니다."));
        }
    }

    /** Import thread -> UI thread, once per whole percent of the conversion. */
    private static final class ProgressReporter implements AudioNormalizer.Progress {
        private final View recordView;

        ProgressReporter(View recordView) {
            this.recordView = recordView;
        }

        @Override
        public void onProgress(int percent) {
            recordView.post(new ProgressTask(recordView, percent));
        }
    }

    /** UI thread: show, advance or hide the conversion progress bar. */
    private static final class ProgressTask implements Runnable {
        /** Shown but indeterminate: a source that does not state its duration never reports a percent. */
        static final int START = -1;
        static final int HIDE = -2;

        private final View recordView;
        private final int percent;

        ProgressTask(View recordView, int percent) {
            this.recordView = recordView;
            this.percent = percent;
        }

        @Override
        public void run() {
            ProgressBar bar = convertProgress(recordView);
            if (bar == null) return;
            if (percent == HIDE) {
                bar.setVisibility(View.GONE);
                return;
            }
            if (percent == START) {
                bar.setProgress(0);
                bar.setIndeterminate(true);
            } else {
                if (bar.isIndeterminate()) bar.setIndeterminate(false);
                bar.setProgress(percent);
            }
            bar.setVisibility(View.VISIBLE);
        }
    }

    /** UI thread: a message from the import thread. */
    private static final class ToastTask implements Runnable {
        private final View recordView;
        private final String message;

        ToastTask(View recordView, String message) {
            this.recordView = recordView;
            this.message = message;
        }

        @Override
        public void run() {
            toast(recordView, message);
        }
    }

    /** UI thread: present the imported clip as a finished recording, or report why it was refused. */
    private static final class FinishTask implements Runnable {
        private final View recordView;
        private final String failure;

        /** @param failure the message to show, or null when the import succeeded. */
        FinishTask(View recordView, String failure) {
            this.recordView = recordView;
            this.failure = failure;
        }

        @Override
        public void run() {
            if (failure == null) {
                invokeVoid(recordView, M_FINALIZE);
            } else {
                toast(recordView, failure);
            }
        }
    }
}
