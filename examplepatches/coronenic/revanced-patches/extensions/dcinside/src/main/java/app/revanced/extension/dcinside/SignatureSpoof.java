package app.revanced.extension.dcinside;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.util.Base64;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Signature spoofing for the DCInside app's native + client-side anti-tamper.
 *
 * The app reads its own signing certificate via
 *   context.getPackageManager().getPackageInfo(pkg, GET_SIGNATURES).signatures[0]
 * (both natively in libnative-lib.so — which SHA-256s it and POSTs to
 * msign.dcinside.com/mobile_app_verification — and in Kotlin AppSignatureVerifier).
 * ReVanced re-signs the APK, so that cert changes and the server rejects it
 * (error 2109 "service id mismatched") and the client blanks the live-best list.
 *
 * We return the ORIGINAL certificate bytes (not a precomputed hash), so every
 * downstream consumer recomputes the correct digest regardless of algorithm.
 * This is future-proof: if DCInside changes SHA-256 -> anything, we still pass.
 */
public final class SignatureSpoof {
    private SignatureSpoof() {}

    /** Package this spoof applies to. Only this package's info is ever altered. */
    private static final String TARGET_PACKAGE = "com.dcinside.app.android";

    /** Base64 of the original DCInside signing certificate (DER, 1421 bytes). */
    private static final String ORIGINAL_CERT_B64 =
        "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"
      + "GNtkTGohCbAje2Ry5gSXrYY=";

    // GET_SIGNATURES = 0x40 (deprecated but what the native reader uses);
    // GET_SIGNING_CERTIFICATES = 0x08000000 (API 28+).
    private static final int GET_SIGNATURES = 0x40;
    private static final int GET_SIGNING_CERTIFICATES = 0x08000000;

    /** Master switch. Defaults on; a patch may flip this from a settings pref. */
    public static volatile boolean enabled = true;

    private static volatile Signature originalSignature;

    private static Signature original() {
        Signature s = originalSignature;
        if (s == null) {
            byte[] der = Base64.decode(ORIGINAL_CERT_B64, Base64.DEFAULT);
            s = new Signature(der);
            originalSignature = s;
        }
        return s;
    }

    /** Wrap a real PackageManager so its getPackageInfo is spoofed. */
    public static PackageManager wrap(PackageManager real) {
        if (real instanceof SpoofPackageManager) return real;
        return new SpoofPackageManager(real);
    }

    /**
     * Inject the original signature into a PackageInfo when it is ours and the
     * caller asked for signatures. Never throws; on any failure returns {@code info} untouched.
     */
    public static PackageInfo maybeSpoof(PackageInfo info, int flags) {
        try {
            if (!enabled || info == null) return info;
            if (!TARGET_PACKAGE.equals(info.packageName)) return info;

            if ((flags & GET_SIGNATURES) != 0) {
                info.signatures = new Signature[] { original() };
            }
            if ((flags & GET_SIGNING_CERTIFICATES) != 0) {
                SigningInfo spoofed = buildSigningInfo();
                if (spoofed != null) info.signingInfo = spoofed;
            }
        } catch (Throwable ignored) {
            // Fail open: a spoof failure must never crash the host app.
        }
        return info;
    }

    /**
     * Best-effort construction of a SigningInfo wrapping the original signature.
     * SigningInfo has no public constructor; use the hidden SigningDetails path via
     * reflection. Returns null if unavailable (caller then leaves signingInfo as-is).
     */
    private static SigningInfo buildSigningInfo() {
        try {
            Signature[] sigs = new Signature[] { original() };
            // Try SigningInfo(SigningDetails) — internal ctor present on many builds.
            for (Constructor<?> c : SigningInfo.class.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 1 && p[0].getSimpleName().equals("SigningDetails")) {
                    Object details = buildSigningDetails(p[0], sigs);
                    if (details != null) {
                        c.setAccessible(true);
                        return (SigningInfo) c.newInstance(details);
                    }
                }
            }
            // Fallback: some builds expose a SigningInfo() then a settable field.
            Constructor<?> empty = SigningInfo.class.getDeclaredConstructor();
            empty.setAccessible(true);
            SigningInfo si = (SigningInfo) empty.newInstance();
            Field f = findField(SigningInfo.class, "mSigningDetails");
            if (f != null) {
                Object details = buildSigningDetails(f.getType(), sigs);
                if (details != null) { f.setAccessible(true); f.set(si, details); return si; }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object buildSigningDetails(Class<?> detailsClass, Signature[] sigs) {
        try {
            // SigningDetails(Signature[] signatures, int signatureSchemeVersion,
            //                ArraySet<PublicKey> keys, Signature[] pastSigningCertificates)
            for (Constructor<?> c : detailsClass.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length >= 2 && p[0] == Signature[].class && p[1] == int.class) {
                    c.setAccessible(true);
                    Object[] args = new Object[p.length];
                    args[0] = sigs;
                    args[1] = 3; // SIGNING_BLOCK_V3
                    for (int i = 2; i < p.length; i++) args[i] = null;
                    return c.newInstance(args);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { return k.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }
}
