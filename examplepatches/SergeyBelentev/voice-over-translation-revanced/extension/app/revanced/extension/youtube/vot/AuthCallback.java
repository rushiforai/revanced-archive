package app.revanced.extension.youtube.vot;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/** Strict OAuth implicit-flow callback parser; deliberately contains no Android/UI code. */
public final class AuthCallback {
    public static final String REDIRECT = "https://rust-server-531j.onrender.com/auth/callback";
    public final String token;
    public final long expiresAt;
    private AuthCallback(String token, long expiresAt) { this.token = token; this.expiresAt = expiresAt; }
    public static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_.~-]{16,4096}");
    }
    public static boolean isCallback(String url) {
        try {
            URI uri = URI.create(url);
            return "https".equals(uri.getScheme()) && "rust-server-531j.onrender.com".equals(uri.getHost())
                    && uri.getPort() == -1 && uri.getUserInfo() == null && "/auth/callback".equals(uri.getRawPath());
        } catch (RuntimeException ex) { return false; }
    }
    public static AuthCallback parse(String url, String expectedState, long now) {
        if (!isCallback(url) || expectedState == null || expectedState.length() < 32) throw invalid();
        URI uri = URI.create(url);
        if (uri.getRawQuery() != null || uri.getRawFragment() == null || uri.getRawFragment().length() > 8192) throw invalid();
        Map<String, String> params = new HashMap<>();
        for (String part : uri.getRawFragment().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) throw invalid();
            String key = decode(pair[0]);
            String value = decode(pair[1]);
            if (params.put(key, value) != null) throw invalid();
        }
        if (!expectedState.equals(params.get("state")) || params.containsKey("error")) throw invalid();
        String token = params.get("access_token");
        String type = params.get("token_type");
        if (!validToken(token) || (type != null && !"bearer".equalsIgnoreCase(type))) throw invalid();
        long seconds;
        try { seconds = Long.parseLong(params.get("expires_in")); } catch (RuntimeException ex) { throw invalid(); }
        if (seconds <= 60 || seconds > 10L * 366 * 86400 || now < 0 || now > Long.MAX_VALUE - seconds * 1000) throw invalid();
        return new AuthCallback(token, now + seconds * 1000);
    }
    private static String decode(String value) {
        try { return URLDecoder.decode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException ex) { throw invalid(); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Яндекс не подтвердил вход. Попробуйте войти снова."); }
}
