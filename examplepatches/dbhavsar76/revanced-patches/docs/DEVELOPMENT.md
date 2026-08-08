# Development

How patches in this repo get made: acquiring an APK, finding a hook, writing the
patch, and verifying it actually did something. Plus the traps that cost real
time — most of this file is trap documentation, because almost none of these
failures point at their own cause.

## Toolchain

| Tool | Purpose | Install |
| --- | --- | --- |
| JDK 21 | required by the Kotlin toolchain (see [JDK](#the-jdk-is-too-new)) | `brew install openjdk@21` |
| Gradle 8.9+ | builds the patches | `brew install gradle` |
| [apktool](https://apktool.org) | decode APK to smali + resources | `brew install apktool` |
| [jadx](https://github.com/skylot/jadx) | decompile to readable Java for analysis | `brew install jadx` |
| [APKEditor](https://github.com/REAndroid/APKEditor) | merge split bundles into one APK | release JAR → `workspace/tools/` |
| [ReVanced CLI](https://github.com/ReVanced/revanced-cli) | applies patches | release JAR → `workspace/tools/` |
| Android SDK build-tools | `aapt2`, `zipalign`, `apksigner` | `sdkmanager "build-tools;35.0.0"` |

## Workspace

`workspace/` is gitignored scratch space — APKs, decompiled trees, and build
output stay local. See [workspace/README.md](../workspace/README.md).

```bash
mkdir -p workspace/{apks,decompiled,jadx,notes,tools,out}
```

Decompiled trees get large (1–3 GB per app across both tools). Delete them when
done rather than letting them accumulate.

## Workflow

1. **Acquire** — put a legitimately obtained APK in `workspace/apks/`. Record the
   exact version; patches are pinned to what you tested against.
2. **Recon** — decompile and read. Find the class/method deciding whether an ad
   shows or a video plays.
3. **Feasibility gate** — decide whether a stable hook exists *before* writing
   patch code. Obfuscation, server-side ad stitching, or integrity checks can make
   a patch impractical; if so, stop and write up why.
4. **Patch** — implement against `revanced-patcher`, matching the method by shape
   rather than by name where possible.
5. **Verify** — apply, install, and confirm behaviour changed. Never trust an exit
   code.

### Recon commands

```bash
apktool d workspace/apks/<app>.apk -o workspace/decompiled/<app> -f
```

```bash
jadx --no-res -d workspace/jadx/<app> workspace/apks/<app>.apk
```

`jadx` output is for reading only — never rebuild from it. Patches operate on the
smali.

A fast first pass on which ad stack an app uses, before decompiling anything:

```bash
unzip -p workspace/apks/<app>.apk 'classes*.dex' | strings -n 8 | grep -oiE 'com/google/ads/interactivemedia[a-z0-9/]*' | sort -u | head
```

## Finding a good hook

The best hook is a **boolean the app already uses to disable the thing itself** —
a premium/subscriber check, a remote-config flag, a debug switch. Forcing one of
those puts the app into a state it already ships and tests, so there is no
untested code path and no null it was never written to handle.

Rank candidates by:

- **Does one hook cover every path?** Look upstream. An app with both client-side
  and server-side ad delivery may still gate both on one config object built in
  one place.
- **Does the app already produce this value?** `return null` where null is
  already a legal, handled result beats inventing a state.
- **Does it survive obfuscation?** Match on return type, parameter count and
  shape rather than a class name — R8 renames classes between releases, and the
  same interface is often implemented by different classes in the phone and TV
  builds.
- **Is it ads-only?** Check the hook doesn't also gate entitlement, licensing or
  content access.

## Bundles

APKMirror often serves `.apkm` (APKMirror Installer bundle) instead of a plain
APK; APKPure uses `.xapk`, SAI uses `.apks`. All are ZIPs holding `base.apk` plus
the split APKs Play would deliver separately.

Only `base.apk` carries the dex, so that is where patchable code lives — but the
splits carry native libraries, densities and languages. Patch `base.apk` alone and
you get an app that installs and then crashes hunting for its `.so` files.
ReVanced CLI wants a single APK, so flatten first.

Check what you got — if APKMirror offers a plain `APK` variant alongside the
`BUNDLE`, take it and skip all of this:

```bash
unzip -l workspace/apks/<app>.apkm
```

A readable listing of `base.apk` + splits + `info.json` means an ordinary ZIP.

### Getting a single APK from a bundle

Neither ReVanced CLI nor ReVanced Manager accepts a bundle. Manager's
[troubleshooting guide](https://github.com/ReVanced/revanced-manager/blob/main/docs/3_troubleshooting.md)
names it directly — patch "a full APK file and not an APK bundle". This bites more
often than expected, because a Play Store install is *itself* split, so Manager's
*Select an app* path fails on it just as *Select from storage* does.

Two further Manager constraints worth knowing before planning around it:

- It requires **Android 8.0+**, so it cannot run on older devices at all — many
  Android TV boxes are Android 6/7.
- It patches on-device, so the output matches whatever APK you fed it. Patching on
  a phone for a different device means feeding it an APK merged for *that*
  device's ABI, not the phone's.

In rough order of preference:

**1. Find a non-bundle download.** APKMirror lists some apps under both an `APK`
and a `BUNDLE` variant, and often publishes `universal` / `nodpi` builds carrying
every ABI and density. Either sidesteps the problem entirely. Check the variant
table on the app's version page before downloading.

**2. Merge the bundle yourself** with [APKEditor](https://github.com/REAndroid/APKEditor)
— see [Merging](#merging) below. This is the general answer when no plain APK
exists. The merged APK can then go to ReVanced CLI on a desktop, or be handed to
Manager via *Select from storage*.

**3. Pull the splits off a device.** Works even when a bundle is protected and
will not open, and gives exactly the splits your device resolved:

```bash
adb shell pm path <package>
```

Then `adb pull` each printed path into a directory and merge that directory.

**4. Patch on desktop instead.** If the target runs Android 6/7, or you want the
verification steps in [Verifying a patched APK](#verifying-a-patched-apk), skip
Manager and use ReVanced CLI. That is the route this repo's patches are tested
with.

Whichever you choose, verify the merged APK before patching — a merge that
silently keeps the wrong ABI produces an app that installs and then crashes:

```bash
aapt2 dump badging <merged>.apk | grep -iE 'minsdkversion|native-code'
```

### Merging

**Merge only the splits for your target device's ABI.** Feeding in both
`arm64_v8a` and `armeabi_v7a` leaves both in the merged APK.

```bash
mkdir -p workspace/tmp/<app>-splits
cp base.apk split_config.armeabi_v7a.apk split_config.xhdpi.apk workspace/tmp/<app>-splits/
```

```bash
java -jar workspace/tools/APKEditor-1.4.9.jar m -i workspace/tmp/<app>-splits -o workspace/tmp/<app>-merged.apk
```

APKEditor also sanitizes the manifest, stripping `isSplitRequired` and
`com.android.vending.splits` — leftovers that otherwise cause a splits error at
launch.

If a bundle is protected and will not open, install the app on a device and pull
what it actually resolved:

```bash
adb shell pm path <package>
```

Then `adb pull` each path into a directory and merge that directory instead.

## Traps

### Never ship a full apktool rebuild

apktool's resource decode/re-encode is lossy on modern apps. A full `apktool b`
routinely produces an APK that compiles and installs, then crashes at runtime:

```
android.view.InflateException: Binary XML file line #9 in layout/foo:
You must supply a layout_height attribute.
```

That is a resource artifact, not a bug in your patch. The early tell is
`Unresolved resource reference` warnings during `apktool d`.

Fix: keep the rebuild for its **dex output only** and swap that dex into the
untouched original APK, so `resources.arsc` and every XML stay byte-identical.
Find which `smali_classes<N>` folder holds the patched class — that is the only
dex you need:

```bash
unzip -o -q workspace/out/<app>-patched.apk classes<N>.dex -d workspace/tmp/dexswap
```

```bash
cp workspace/apks/<app>.apk workspace/out/<app>-surgical.apk && (cd workspace/tmp/dexswap && zip -q ../../out/<app>-surgical.apk classes<N>.dex)
```

Then `zipalign -p -f 4` and sign. Alternatively `apktool d -r` skips resource
decoding entirely, which works when the patch touches no resources.

### The patcher version must match the CLI

The `.rvp` is loaded by **ReVanced CLI's own bundled patcher** at apply time. A
mismatch fails at *apply* time, not build time:

```
NoClassDefFoundError: app/revanced/patcher/patch/BytecodePatch
```

…and the CLI still emits a valid, completely unpatched APK. Find the CLI's
patcher version and match it exactly:

```bash
curl -s https://raw.githubusercontent.com/ReVanced/revanced-cli/v6.0.0/gradle/libs.versions.toml | grep revanced-patcher
```

Do not use a newer patcher than the CLI ships, however tempting.

### The coordinate was renamed

Up to 21.x the module is `app.revanced:revanced-patcher`. From 22.0.0 it is
`app.revanced:patcher`. Asking for the old name at a 22.x version resolves to
nothing and reads as "22 was never published".

Published artifacts also lag the GitHub release tags — the repo may be tagged
v22.x while the registry holds something older. List what actually exists with a
scratch Gradle project resolving `app.revanced:patcher:+`.

### The API changed in 22.0.0

| | ≤ 21.x | ≥ 22.0.0 |
| --- | --- | --- |
| matching | `Fingerprint.kt` — `fingerprint { }` | `Matching.kt` — `firstMethod { }` |
| patch body | `execute { }` | `apply { }` |
| compiler flag | `-Xcontext-receivers` | `-Xcontext-parameters` |

Code written for one will not compile against the other. Most examples online —
and the `main`-branch docs — describe a version you may not be able to depend on.

### Patcher is built with a pre-release Kotlin

A release Kotlin compiler refuses to load it:

```
Class 'app.revanced.patcher.patch.Patch' was compiled by a pre-release version
of Kotlin and cannot be loaded by this version of the compiler
```

Add `-Xskip-prerelease-check`. Your output is then also marked pre-release, which
costs nothing when the only consumer is the CLI running that same patcher.

### The JDK is too new

Homebrew's Gradle launches on JDK 26; the Kotlin compiler cannot parse a
two-digit major version:

```
java.lang.IllegalArgumentException: 26.0.1
    at intellij.util.lang.JavaVersion.parse → JavaVersion.current
```

It surfaces only as "Internal compiler error" and points nowhere near the cause.

The build declares `jvmToolchain(21)`, which runs the Kotlin compiler on JDK 21
no matter which JVM Gradle itself launched on. Prefer this to
`org.gradle.java.home`: a toolchain scopes the old JDK to compilation instead of
forcing the whole build onto it, and it needs no absolute path. `gradle.properties`
is a Java properties file with **no shell expansion** — `~` in
`org.gradle.java.home` is rejected as "Java home supplied is invalid", so the only
alternative is a hardcoded home directory that breaks on every other machine.

If Gradle cannot locate a JDK 21 it fails with "No matching toolchains found".
Install one, or list your install directories in `~/.gradle/gradle.properties`
(user-level, never committed):

```properties
org.gradle.java.installations.paths = /Users/you/Library/Java/JavaVirtualMachines/corretto-21.0.7/Contents/Home
```

### The CLI wants a BKS keystore

ReVanced's signer uses BouncyCastle, so a `keytool`-made PKCS12 store fails:

```
java.io.IOException: Wrong version of key store.
```

Let the CLI generate and reuse its own keystore. The consequence is that CLI
output carries a different signing key than any hand-patched build, so the two
cannot upgrade over each other.

### The upstream patches repo is gone

`github.com/ReVanced/revanced-patches` — the usual worked example — has been
DMCA-blocked since March 2026. The patcher repo and its `docs/` are still up and
remain the reference.

## Verifying a patched APK

Never trust the exit code. Confirm the bytecode changed:

```bash
unzip -o -q workspace/out/<app>-patched.apk 'classes*.dex' -d /tmp/dex
```

```bash
jadx --no-res -d /tmp/verify /tmp/dex/classes8.dex
```

Then read the patched method. Note the class may move between dex files after
merging and recompilation, so search rather than assuming an index:

```bash
for d in /tmp/dex/classes*.dex; do strings -n 8 "$d" | grep -q '<ClassName>' && echo "$d"; done
```

Check packaging suits the target device:

```bash
aapt2 dump badging workspace/out/<app>-patched.apk | grep -iE 'minsdkversion|native-code'
```

```bash
apksigner verify --verbose workspace/out/<app>-patched.apk
```

### A/B testing against a control

To prove a patch did something, build a control: the **unmodified** APK re-signed
with the **same key**. Only the patched dex then differs, and because the
signatures match it swaps in place without uninstalling, so app data and login
survive:

```bash
zipalign -p -f 4 workspace/apks/<app>.apk workspace/out/orig-aligned.apk
```

```bash
apksigner sign --ks workspace/keystore.jks --out workspace/out/base-original.apk workspace/out/orig-aligned.apk
```

Watch for confounds. An ad-removal patch hooking a subscriber check proves
nothing on a subscribed account — the control shows no ads either. Test on a free
account.
