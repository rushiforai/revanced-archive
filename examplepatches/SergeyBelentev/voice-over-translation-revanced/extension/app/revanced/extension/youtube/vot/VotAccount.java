package app.revanced.extension.youtube.vot;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import app.revanced.extension.shared.Utils;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** OAuth token is encrypted with a non-exportable Android Keystore key. */
public final class VotAccount {
    private static final String ALIAS = "revanced_vot_oauth_v1";
    private static SharedPreferences storage() {
        return Utils.getContext().getApplicationContext().getSharedPreferences("revanced_vot_account", Context.MODE_PRIVATE);
    }
    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey) store.getKey(ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    public static synchronized void save(AuthCallback account) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal((account.expiresAt + "\n" + account.token).getBytes(StandardCharsets.UTF_8));
        if (!storage().edit().putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit()) throw new Exception("Storage unavailable");
    }
    public static synchronized String token() {
        SharedPreferences prefs = storage();
        if (!prefs.contains("data")) return "";
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
            String[] value = new String(cipher.doFinal(Base64.decode(prefs.getString("data", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8).split("\n", 2);
            if (value.length == 2 && Long.parseLong(value[0]) > System.currentTimeMillis() + 60000 && AuthCallback.validToken(value[1])) return value[1];
        } catch (Exception ignored) { /* Restored backups cannot decrypt a different device's key. */ }
        clear(); return "";
    }
    public static synchronized void clear() { storage().edit().clear().commit(); }
    public static synchronized void invalidate(String rejectedToken) {
        if (rejectedToken.equals(token())) clear();
    }
}
