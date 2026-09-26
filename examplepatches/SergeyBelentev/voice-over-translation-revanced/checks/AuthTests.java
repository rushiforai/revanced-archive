import app.revanced.extension.youtube.vot.*;
import java.io.*;
import java.net.*;
import java.util.*;

public final class AuthTests {
    static int checks;
    static final String TOKEN = "test_token_for_local_fixtures_only";
    static final String STATE = "test_state_012345678901234567890123456789";
    static final String CALLBACK = AuthCallback.REDIRECT + "#access_token=" + TOKEN + "&expires_in=3600&token_type=bearer&state=" + STATE;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static void rejected(String url, String state) {
        boolean caught = false;
        try { AuthCallback.parse(url, state, 100000); } catch (IllegalArgumentException expected) { caught = true; }
        check(caught, "Invalid callback accepted");
    }
    static final List<FakeConnection> requests = new ArrayList<>();
    static boolean unauthorized;
    static boolean fallback;
    static class FakeConnection extends HttpURLConnection {
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        FakeConnection(URL url) { super(url); requests.add(this); }
        public void connect() { }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() { return body; }
        public int getResponseCode() { return unauthorized && url.getPath().endsWith("/translate") ? 401 : 200; }
        public InputStream getInputStream() {
            byte[] data = url.getPath().equals("/session/create")
                    ? new Proto().text(1, "local-session-secret").number(2, 3600).build()
                    : new Proto().number(4, fallback ? 6 : 1).text(7, "fixture").text(1, "https://example.org/voice.mp3").build();
            return new ByteArrayInputStream(data);
        }
    }
    public static void main(String[] args) throws Exception {
        AuthCallback account = AuthCallback.parse(CALLBACK, STATE, 100000);
        check(account.token.equals(TOKEN) && account.expiresAt == 3700000, "Token and expiry");
        for (String url : Arrays.asList(
                CALLBACK.replace("https:", "http:"), CALLBACK.replace("onrender.com", "onrender.com.evil.test"),
                CALLBACK.replace("/auth/callback", "/auth/callback/"), CALLBACK.replace("/auth/callback", "/auth/%63allback"),
                CALLBACK.replace("https://", "https://user@"), CALLBACK.replace("onrender.com/", "onrender.com:443/"),
                CALLBACK.replace("#", "?unexpected=1#"), CALLBACK.replace("3600", "0"), CALLBACK.replace("3600", "-1"),
                CALLBACK.replace("3600", "99999999999999999999999"), CALLBACK.replace("3600", "30"),
                CALLBACK + "&access_token=other", CALLBACK + "&%73tate=" + STATE,
                CALLBACK + "&error=access_denied", CALLBACK.replace(TOKEN, "bad%0d%0aheader"),
                CALLBACK.replace(TOKEN, "short"), CALLBACK.replace(TOKEN, "%ZZ"),
                CALLBACK.replace("bearer", "unexpected"), AuthCallback.REDIRECT)) rejected(url, STATE);
        rejected(CALLBACK, STATE + "wrong"); rejected(CALLBACK, ""); rejected(CALLBACK, null);
        check(!AuthCallback.validToken(null) && !AuthCallback.validToken(TOKEN + "\n"), "No missing token/header injection");
        boolean missing = false;
        try { new VotApi(true, ""); } catch (IllegalArgumentException expected) { missing = true; }
        check(missing, "Lively request requires account before network");

        // Entire protocol runs against an in-memory transport. No token or request leaves the JVM.
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { return new FakeConnection(url); }
        } : null);
        new VotApi(false, TOKEN).translate("dQw4w9WgXcQ", 213.5, "en", "ru");
        check(requests.size() == 2, "Classic session and translate");
        for (FakeConnection request : requests) check(request.getRequestProperty("Authorization") == null, "Classic never sends OAuth");
        requests.clear(); fallback = true;
        new VotApi(true, TOKEN).translate("dQw4w9WgXcQ", 213.5, "en", "ru");
        check(requests.size() == 4, "Lively session, translate, and audio fallback");
        for (FakeConnection request : requests) {
            check(request.getURL().getHost().equals("api.browser.yandex.ru") && !request.getInstanceFollowRedirects(), "Token destination and redirects");
            if (request.getURL().getPath().endsWith("/translate")) {
                check(("OAuth " + TOKEN).equals(request.getRequestProperty("Authorization")), "Lively authorization");
                boolean livelyField = false;
                Proto.Reader reader = new Proto.Reader(request.body.toByteArray());
                while (reader.next()) { if (reader.field == 18) livelyField = reader.number() == 1; else reader.skip(); }
                check(livelyField, "Lively request flag");
            } else check(request.getRequestProperty("Authorization") == null, "Session/fallback never receive OAuth");
        }
        requests.clear();
        boolean language = false;
        try { new VotApi(true, TOKEN).translate("dQw4w9WgXcQ", 213.5, "en", "kk"); } catch (IOException expected) { language = true; }
        check(language && requests.isEmpty(), "Unsupported lively target rejected locally");
        unauthorized = true;
        boolean expired = false;
        try { new VotApi(true, TOKEN).translate("dQw4w9WgXcQ", 213.5, "en", "ru"); } catch (VotApi.AuthorizationException expected) { expired = true; }
        check(expired, "Expired account gets distinct error");
        System.out.println("PASS: " + checks + " OAuth/lively assertions (in-memory HTTPS transport)");
    }
}
