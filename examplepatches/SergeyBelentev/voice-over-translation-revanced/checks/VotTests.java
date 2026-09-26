import app.revanced.extension.youtube.vot.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public final class VotTests {
    static int checks;
    static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    static byte[] hex(String text) {
        byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(text.substring(i*2, i*2+2), 16);
        return bytes;
    }
    public static void main(String[] args) throws Exception {
        Properties fixtures = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) { fixtures.load(input); }
        byte[] request = VotApi.translationRequest("dQw4w9WgXcQ", 213.5, "en", "ru");
        check(Arrays.equals(request, hex(fixtures.getProperty("request"))), "Request must match original vot.js protobuf encoder byte-for-byte");
        check(VotApi.sign(request).equals(fixtures.getProperty("sign")), "HMAC must match Node crypto");
        check(Arrays.equals(VotApi.translationRequest("dQw4w9WgXcQ", 213.5, "en", "ru", true), hex(fixtures.getProperty("lively"))), "Lively request matches upstream encoder");
        VotApi.Result result = VotApi.Result.parse(hex(fixtures.getProperty("response")));
        check(result.status == 1 && result.id.equals("fixture") && result.url.equals("https://example.org/voice.mp3"), "Read upstream protobuf response and skip duration/language");
        check(VotApi.Result.parse(new Proto().number(4, 7).build()).status == 7, "Auth required remains distinct");
        check(VotApi.Result.parse(new Proto().number(4, 5).number(5, 125).build()).remaining == 125, "Partial translation delay");
        byte[][] invalid = { {10, 127}, {0}, {10, -1}, {8, -128}, {10, -1,-1,-1,-1,-1,-1,-1,-1,-1,127} };
        for (byte[] bytes : invalid) {
            boolean caught = false;
            try { Proto.Reader reader = new Proto.Reader(bytes); while (reader.next()) reader.skip(); }
            catch (IOException expected) { caught = true; }
            check(caught, "Malformed protobuf must be rejected");
        }
        Random random = new Random(421);
        for (int i = 0; i < 2000; i++) {
            byte[] bytes = new byte[random.nextInt(96)]; random.nextBytes(bytes);
            try { Proto.Reader reader = new Proto.Reader(bytes); while (reader.next()) reader.skip(); }
            catch (IOException expected) { }
        }
        check(true, "Malformed input fuzz: no bounds/unchecked exception");

        PlaybackClock clock = new PlaybackClock();
        check(!clock.shouldPlay(0), "No timestamp means silent");
        clock.state(true, 0); clock.sample(10000, 0);
        check(clock.shouldPlay(250) && clock.expected(250) == 10250, "Foreground sync");
        // 30 minutes of screen-off/background playback: no Activity event is required.
        for (long time = 1000; time <= 1800000; time += 1000) {
            clock.sample(10000 + time, time);
            check(clock.shouldPlay(time + 250), "Background playback continues");
            check(clock.expected(time + 250) == 10250 + time, "Background timeline has no cumulative drift");
        }
        clock.state(false, 1800100);
        check(!clock.shouldPlay(1800101), "Notification pause is immediate");
        long pausedPosition = clock.expected(1800100);
        check(clock.expected(1900000) == pausedPosition, "Long pause freezes time");
        clock.state(true, 1900000);
        check(!clock.shouldPlay(1900001), "Resume waits for a fresh position");
        clock.sample(pausedPosition + 1000, 1901000);
        check(clock.shouldPlay(1901000), "Notification resume");
        clock.speed(2f, 1901000);
        check(clock.expected(1901500) == pausedPosition + 2000, "Double playback speed");
        clock.sample(60000, 1902000);
        check(clock.expected(1902100) == 60200, "Forward/backward seek rebases timeline");
        clock.sample(60000, 1903000); clock.sample(60000, 1904000); clock.sample(60000, 1905000);
        check(!clock.shouldPlay(1905000), "Frozen buffering clock cannot continue speech");
        clock.sample(62000, 1906000);
        check(clock.shouldPlay(1906000), "Buffering recovery");
        check(!clock.shouldPlay(2000000), "Lost background hooks / sleep fail silent");
        clock.speed(Float.NaN, 1906000); clock.speed(0, 1906000);
        check(clock.speed() == 2, "Invalid speed ignored");
        clock.reset();
        check(!clock.shouldPlay(2000000) && clock.expected(2000000) == -1, "Next playlist item clears stale audio position");
        System.out.println("PASS: " + checks + " assertions; 2000 malformed protobuf samples");
    }
}
