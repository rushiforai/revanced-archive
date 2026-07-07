# dcinside extension — signature spoof

Source for the signature-spoof extension merged by `Spoof signature`
(`patches/.../dcinside/SignatureSpoofPatch.kt`). The prebuilt dex is committed at
`patches/src/main/resources/dcinside/signature-spoof.rve` and loaded via `extendWith`.

## Classes

- `SignatureSpoof.java` — holds the original DCInside signing certificate (base64 DER),
  `wrap(PackageManager)` and `maybeSpoof(PackageInfo, flags)`. Injects the original
  `Signature` into the app's own `getPackageInfo(..., GET_SIGNATURES / GET_SIGNING_CERTIFICATES)`.
- `SpoofPackageManager.java` — **auto-generated** `PackageManager` delegate. Every
  *overridable instance* method forwards to the real instance — not only the abstract
  ones: many `PackageManager` methods are concrete base stubs that throw
  `UnsupportedOperationException` (the real impl lives in the hidden
  `ApplicationPackageManager`), so skipping them crashes the host the moment one is
  called (e.g. `getInstallSourceInfo`). Only the `getPackageInfo` overloads are intercepted.
- `Gen.java` — regenerates `SpoofPackageManager.java` from an `android.jar` by reflection.

## Regenerate the dex (no gradle needed)

Requires a JDK, an Android `android.jar` (API ≥ 34), and `d8` (Android build-tools).

```bash
# 1. regenerate the delegate for the target API (only if PackageManager's contract changed)
javac Gen.java
java Gen /path/to/android.jar > src/main/java/app/revanced/extension/dcinside/SpoofPackageManager.java

# 2. compile both classes against android.jar
javac -bootclasspath /path/to/android.jar -source 8 -target 8 \
  src/main/java/app/revanced/extension/dcinside/*.java

# 3. dex at the app's minSdk (23) and install as the patch resource
java -cp /path/to/d8.jar com.android.tools.r8.D8 --min-api 23 --lib /path/to/android.jar \
  --output . src/main/java/app/revanced/extension/dcinside/*.class
cp classes.dex ../../patches/src/main/resources/dcinside/signature-spoof.rve
```

## Updating the embedded certificate

If the target's official signing certificate changes, replace `ORIGINAL_CERT_B64` in
`SignatureSpoof.java` with `base64(DER of cert)` from the vanilla APK's v2/v3 signing block,
then regenerate the dex (steps 2–3).
