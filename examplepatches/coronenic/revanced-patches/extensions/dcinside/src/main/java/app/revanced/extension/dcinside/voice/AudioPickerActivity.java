package app.revanced.extension.dcinside.voice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Transparent proxy that owns the {@code ACTION_GET_CONTENT} pick so it reliably receives
 * {@code onActivityResult} (a View cannot; framework-fragment routing under AppCompat is flaky).
 * Declared in the manifest by the resource patch. Launches the system audio picker, hands the
 * result back to {@link VoiceFilePicker#deliver(Uri)}, and finishes.
 */
public final class AudioPickerActivity extends Activity {

    private static final int REQUEST_PICK = 0x7C9A;

    static void start(Activity host) {
        host.startActivity(new Intent(host, AudioPickerActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return; // picker already launched before recreation
        try {
            Intent pick = new Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("audio/*");
            startActivityForResult(Intent.createChooser(pick, "오디오 선택"), REQUEST_PICK);
        } catch (Throwable t) {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK) {
            VoiceFilePicker.deliver(
                    resultCode == RESULT_OK && data != null ? data.getData() : null);
        }
        finish();
    }
}
