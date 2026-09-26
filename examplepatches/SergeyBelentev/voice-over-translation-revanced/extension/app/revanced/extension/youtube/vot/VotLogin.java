package app.revanced.extension.youtube.vot;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.UUID;

/** Uses the public OAuth application already used by upstream VOT. No password handling. */
public final class VotLogin {
    private static final String CLIENT_ID = "331eef9fe3fd4382a051897d5275170e";
    public static void show(Context context, Runnable changed) {
        String state = UUID.randomUUID().toString() + UUID.randomUUID();
        WebView web = new WebView(context);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSaveFormData(false); settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("VOT — вход в Яндекс ID")
                .setView(web).setNegativeButton("Закрыть", null).create();
        web.setWebViewClient(new WebViewClient() {
            private boolean finished;
            private boolean intercept(String url) {
                if (finished) return true;
                if (AuthCallback.isCallback(url)) {
                    finished = true; web.stopLoading();
                    try {
                        AuthCallback account = AuthCallback.parse(url, state, System.currentTimeMillis());
                        VotAccount.save(account);
                        Toast.makeText(context, "Вход в Яндекс выполнен", Toast.LENGTH_LONG).show();
                        VotController.translationSettingsChanged();
                    } catch (Exception ex) {
                        Toast.makeText(context, "Не удалось подтвердить или сохранить вход. Попробуйте снова.", Toast.LENGTH_LONG).show();
                    }
                    // Never load the third-party callback page or expose the token to its JavaScript.
                    web.post(() -> { dialog.dismiss(); changed.run(); });
                    return true;
                }
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                boolean allowed = "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                        && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                        && (host.equals("yandex.ru") || host.endsWith(".yandex.ru")
                        || host.equals("yandex.com") || host.endsWith(".yandex.com")
                        || host.equals("ya.ru") || host.endsWith(".ya.ru"));
                if (!allowed) {
                    web.stopLoading();
                    Toast.makeText(context, "Используйте вход на странице Яндекса внутри этого окна", Toast.LENGTH_LONG).show();
                }
                return !allowed;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.isForMainFrame() || intercept(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return intercept(url); }
            @Override public void onPageStarted(WebView view, String url, Bitmap favicon) { intercept(url); }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(context, "Не удалось проверить защищённое соединение с Яндексом", Toast.LENGTH_LONG).show();
            }
        });
        dialog.setOnDismissListener(ignored -> {
            web.stopLoading(); web.setWebViewClient(new WebViewClient());
            web.clearHistory(); web.removeAllViews(); web.destroy();
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(-1, -1);
            // AlertDialog inspects text editors during onCreate, before the web page loads.
            // An empty WebView is not an editor yet, so it sets ALT_FOCUSABLE_IM and blocks
            // the keyboard for later HTML inputs. Clear it AFTER show/onCreate.
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        web.requestFocus();
        web.loadUrl("https://oauth.yandex.ru/authorize?client_id=" + CLIENT_ID
                + "&response_type=token&force_confirm=1&redirect_uri=" + Uri.encode(AuthCallback.REDIRECT)
                + "&state=" + Uri.encode(state));
    }
}
