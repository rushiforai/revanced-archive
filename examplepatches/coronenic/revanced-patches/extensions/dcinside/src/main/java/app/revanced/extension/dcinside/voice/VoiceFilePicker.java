package app.revanced.extension.dcinside.voice;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.net.Uri;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;

/**
 * "Upload an audio file as a voice reply": normalizes any audio the device can decode into the
 * MPEG-4/AAC clip the recorder produces (remux AAC-bearing containers; transcode others).
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
 * uploads it unchanged (RECORD input type).
 *
 * Uses named nested classes rather than lambdas/anonymous classes: the extension compiles against
 * android.jar with the JDK bootclasspath stripped (no {@code LambdaMetafactory}), and d8 rejects
 * anonymous-in-anonymous classes.
 */
public final class VoiceFilePicker {
    private VoiceFilePicker() {}

    private static final String M_TARGET = "revancedVoiceTarget";
    private static final String M_FINALIZE = "revancedVoiceFinalize";

    /** The VoiceRecordView awaiting a pick result. Single-flight; only one picker is open at a time. */
    private static WeakReference<View> pendingView = new WeakReference<View>(null);

    /** Wire the upload button that the resource patch added to the record tab. */
    public static void attach(View recordView) {
        try {
            Context ctx = recordView.getContext();
            int id = ctx.getResources().getIdentifier(
                    "voice_recorder_file_pick", "id", ctx.getPackageName());
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
        toast(recordView, "오디오를 변환하는 중...");
        new Thread(new ImportTask(appContext, uri, target, recordView),
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

    /** Background: remux the picked audio into the record file, then hand off to the UI thread. */
    private static final class ImportTask implements Runnable {
        private final Context context;
        private final Uri source;
        private final File target;
        private final View recordView;

        ImportTask(Context context, Uri source, File target, View recordView) {
            this.context = context;
            this.source = source;
            this.target = target;
            this.recordView = recordView;
        }

        @Override
        public void run() {
            boolean ok = AudioNormalizer.toM4a(context, source, target);
            recordView.post(new FinishTask(recordView, ok));
        }
    }

    /** UI thread: present the imported clip as a finished recording, or report an unsupported file. */
    private static final class FinishTask implements Runnable {
        private final View recordView;
        private final boolean ok;

        FinishTask(View recordView, boolean ok) {
            this.recordView = recordView;
            this.ok = ok;
        }

        @Override
        public void run() {
            if (ok) {
                invokeVoid(recordView, M_FINALIZE);
            } else {
                toast(recordView, "오디오 변환에 실패했습니다.");
            }
        }
    }
}
