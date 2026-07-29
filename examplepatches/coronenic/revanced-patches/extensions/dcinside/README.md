# dcinside extension

Runtime code the patches merge into the app with `extendWith`. Each feature is dexed on its own and
committed as a `.rve` under `patches/src/main/resources/dcinside/`:

| `.rve` | Merged by | Source |
|--------|-----------|--------|
| `signature-spoof.rve` | `Spoof signature` | `.../dcinside/SignatureSpoof.java`, `SpoofPackageManager.java` |
| `settings.rve` | `ReVanced settings` | `.../dcinside/settings/` |
| `voice-file-picker.rve` | `Voice reply file upload` | `.../dcinside/voice/` |

Everything compiles against `android.jar` with the JDK bootclasspath stripped, so there is no
`LambdaMetafactory`: **no lambdas, no method references, no anonymous classes** (d8 also rejects
anonymous-in-anonymous). Use named nested classes.

## Classes

### signature spoof

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

### settings

- `Settings.java` — the registry and SharedPreferences (`revanced`) storage. `declarePatchSettings()`
  is **empty on purpose**: `addSwitchSetting` in `SettingsPatch.kt` appends one `registerSwitch(...)`
  call to it per setting, so only applied patches contribute, and the default a patch declares is the
  one `isEnabled(context, key)` falls back to. Do not put code or locals in that method — the patch
  replaces its implementation to give the appended calls registers.
- `SettingsEntryView.java` — the "ReVanced 패치 버전" row. The resource patch writes this class name
  as the tag in `res/layout/fragment_settings.xml`, so the row wires its own click when the app
  inflates the layout; nothing hooks the app's obfuscated settings fragment.
- `SettingsActivity.java` — the page, built in code (the patch adds no layout resources).
- `Ui.java` — theme/metric helpers. The app picks one of ~30 skin themes at runtime and there is no
  public API to ask an activity which one it got, so the entry row resolves the colors in its own
  themed context and passes them to the page in the intent.

### voice reply file upload

- `VoiceFilePicker.java` — the flow: upload button → picker → normalize into the recorder's output
  file → the view's own finalize. Announces "오디오를 변환하는 중..." and drives the progress bar in
  the record area only when the import actually converts something. The bar starts indeterminate and
  goes determinate on the first percent, so a source that does not state its duration still shows
  activity.
- `AudioNormalizer.java` — `plan()` classifies the picked audio (copy / remux / transcode /
  unsupported), `normalize()` carries it out and reports progress against the track duration. The
  transcode is a single streaming pass with zero-timeout polls; see the class comment for why the
  two-pass version was slow.
- `AudioPickerActivity.java` — transparent proxy that owns the `ACTION_GET_CONTENT` result.

## Build the dexes (no gradle needed)

Requires a JDK, an Android `android.jar` (the level the **target app** compiles against, currently
API 36 for DCInside 5.3.2) and `d8` from the build tools.

```bash
AJ=/path/to/android.jar

# 1. compile every extension source together (they reference each other across features)
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $AJ -d /tmp/ext \
  $(find src/main/java -name '*.java')

# 2. dex each feature separately, with the whole tree on --classpath so cross-feature
#    references resolve without pulling the other feature's classes into the dex
java -cp /path/to/d8.jar com.android.tools.r8.D8 --min-api 23 --lib $AJ --classpath /tmp/ext \
  --output /tmp/dex-settings /tmp/ext/app/revanced/extension/dcinside/settings/*.class
cp /tmp/dex-settings/classes.dex ../../patches/src/main/resources/dcinside/settings.rve
```

Repeat step 2 per feature (`voice`, and `SignatureSpoof.class`/`SpoofPackageManager.class` for the
spoof). `--min-api 23` is the app's minSdk.

### Regenerate the PackageManager delegate

Only when the target app's `targetSdk` rises: a new API level usually adds `PackageManager` methods
(API 36 added `parseAndroidManifest(ParcelFileDescriptor, Function)`, 159 → 160 methods), and every
one of them **must** be delegated or it falls through to a base stub that throws
`UnsupportedOperationException`. The second argument is only the API level in the header comment.

```bash
javac Gen.java && java Gen $AJ 36 > src/main/java/app/revanced/extension/dcinside/SpoofPackageManager.java
```

### Updating the embedded certificate

If the target's official signing certificate changes, replace `ORIGINAL_CERT_B64` in
`SignatureSpoof.java` with `base64(DER of cert)` from the vanilla APK's v2/v3 signing block, then
rebuild `signature-spoof.rve`.
