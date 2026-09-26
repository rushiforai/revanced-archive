package app.revanced.extension.youtube.vot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Native port of the voice protocol in FOSWLY/vot.js 3.1.1 (MIT; see NOTICE). */
public final class VotApi {
    public static final String HOST = "https://api.browser.yandex.ru";
    public static final String COMPONENT = "26.8.3.1002";
    private static final String HMAC_KEY = "bt8xH3VOlb4mqf0nqAibnDOoiPlXsisf";
    private static final String TRANSLATE = "/video-translation/translate";
    private static final int MAX_RESPONSE = 1024 * 1024;
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    private String uuid, secret;
    private long expiresAt;
    private boolean sentAudioFallback;
    private final boolean lively;
    private final String oauthToken;

    public VotApi() { this(false, ""); }
    String accountToken() { return oauthToken; }
    public VotApi(boolean lively, String token) {
        if (lively && !AuthCallback.validToken(token)) throw new IllegalArgumentException("Для живых голосов войдите в Яндекс в настройках ReVanced → VOT");
        this.lively = lively;
        this.oauthToken = lively ? token : "";
    }

    public static final class Result {
        public int status, remaining;
        public String url = "", id = "", message = "";
        public static Result parse(byte[] bytes) throws IOException {
            Result result = new Result(); Proto.Reader reader = new Proto.Reader(bytes);
            while (reader.next()) {
                switch (reader.field) {
                    case 1: result.url = reader.text(); break;
                    case 4: result.status = (int) reader.number(); break;
                    case 5: result.remaining = (int) reader.number(); break;
                    case 7: result.id = reader.text(); break;
                    case 9: result.message = reader.text(); break;
                    default: reader.skip();
                }
            }
            return result;
        }
    }

    public void cancel() {
        cancelled = true;
        HttpURLConnection current = connection;
        if (current != null) current.disconnect();
    }
    private void checkCancelled() throws IOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
    }

    public static byte[] translationRequest(String id, double seconds, String from, String to) {
        return translationRequest(id, seconds, from, to, false);
    }
    public static byte[] translationRequest(String id, double seconds, String from, String to, boolean lively) {
        Proto request = new Proto().text(3, "https://youtu.be/" + id).number(5, 1).decimal(6, seconds)
                .number(7, 1).text(8, from).text(14, to).number(15, 1).number(16, 2);
        if (lively) request.number(18, 1);
        return request.build();
    }

    public Result translate(String id, double seconds, String from, String to) throws IOException {
        if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IOException("Некорректный ID видео");
        if (lively && (!"ru".equals(to) || "auto".equals(from))) throw new IOException("Живые голоса: выберите язык оригинала и перевод на русский");
        ensureSession();
        byte[] body = translationRequest(id, seconds, from, to, lively);
        Map<String, String> headers = signed(TRANSLATE, body);
        if (lively) headers.put("Authorization", "OAuth " + oauthToken);
        Result result = Result.parse(request(TRANSLATE, "POST", body, headers, false));
        if (result.status == 6 && !sentAudioFallback && !result.id.isEmpty()) {
            // Same no-client-audio fallback used by vot.js. The service may still refuse the video.
            String url = "https://youtu.be/" + id;
            request("/video-translation/fail-audio-js", "PUT",
                    ("{\"video_url\":\"" + url + "\"}").getBytes(StandardCharsets.UTF_8),
                    new LinkedHashMap<>(), true);
            String path = "/video-translation/audio";
            byte[] audio = new Proto().text(1, result.id).text(2, url).bytes(6,
                    new Proto().text(1, "fallback-empty-audio:video-translation:" + id).build()).build();
            request(path, "PUT", audio, signed(path, audio), false);
            sentAudioFallback = true;
        }
        return result;
    }

    private void ensureSession() throws IOException {
        if (secret != null && System.currentTimeMillis() < expiresAt) return;
        uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        byte[] body = new Proto().text(1, uuid).text(2, "video-translation").build();
        Map<String, String> headers = new LinkedHashMap<>(); headers.put("Vtrans-Signature", sign(body));
        Proto.Reader reader = new Proto.Reader(request("/session/create", "POST", body, headers, false));
        int expires = 0; secret = null;
        while (reader.next()) {
            if (reader.field == 1) secret = reader.text();
            else if (reader.field == 2) expires = (int) reader.number();
            else reader.skip();
        }
        if (secret == null || secret.isEmpty() || expires <= 0) throw new IOException("VOT: неверная сессия");
        expiresAt = System.currentTimeMillis() + Math.max(1L, expires - 30L) * 1000;
    }
    private Map<String, String> signed(String path, byte[] body) throws IOException {
        String token = uuid + ":" + path + ":" + COMPONENT;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Vtrans-Signature", sign(body));
        headers.put("Sec-Vtrans-Sk", secret);
        headers.put("Sec-Vtrans-Token", sign(token.getBytes(StandardCharsets.UTF_8)) + ":" + token);
        return headers;
    }
    public static String sign(byte[] body) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(body)) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
            return hex.toString();
        } catch (GeneralSecurityException ex) { throw new IOException(ex); }
    }
    private byte[] request(String path, String method, byte[] body, Map<String, String> headers,
                           boolean json) throws IOException {
        checkCancelled();
        HttpURLConnection current = (HttpURLConnection) new URL(HOST + path).openConnection();
        connection = current;
        try {
            checkCancelled();
            current.setConnectTimeout(15000); current.setReadTimeout(25000);
            current.setInstanceFollowRedirects(false); current.setRequestMethod(method);
            current.setRequestProperty("Content-Type", json ? "application/json" : "application/x-protobuf");
            current.setRequestProperty("Accept", json ? "application/json" : "application/x-protobuf");
            current.setRequestProperty("Cache-Control", "no-cache");
            current.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 YaBrowser/26.8.0.0 Safari/537.36");
            for (Map.Entry<String, String> header : headers.entrySet()) current.setRequestProperty(header.getKey(), header.getValue());
            current.setDoOutput(true); current.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream stream = current.getOutputStream()) { stream.write(body); }
            int status = current.getResponseCode();
            if (status == 401 && lively && TRANSLATE.equals(path)) throw new AuthorizationException();
            if (status < 200 || status >= 300) throw new IOException("VOT HTTP " + status);
            try (InputStream stream = current.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    checkCancelled();
                    if (bytes.size() + count > MAX_RESPONSE) throw new IOException("VOT: слишком большой ответ");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toByteArray();
            }
        } finally { current.disconnect(); connection = null; }
    }
    public static final class AuthorizationException extends IOException {
        public AuthorizationException() { super("Вход в Яндекс истёк или отклонён. Войдите снова: ReVanced → VOT — Живые голоса"); }
    }
}
