# Personal ReVanced Patches

Patches for Android apps, built with [ReVanced Patcher](https://github.com/ReVanced/revanced-patcher)
and applied with [ReVanced Manager](https://github.com/ReVanced/revanced-manager)
or [ReVanced CLI](https://github.com/ReVanced/revanced-cli).

Personal use, on hardware I own. These are not affiliated with ReVanced.

## 📦 Patches

| 💊 Patch | 🖥️ App | 📦 Package | 📱 Builds | 🏷️ Versions | 📝 Description |
| --- | --- | --- | --- | --- | --- |
| `Disable ads` | ZEE5 | `com.graymatrix.did` | Android TV + mobile | any | Removes pre-roll, mid-roll and post-roll video ads, on both on-demand content and live channels. |

Verified on Android TV **5.82.7** and mobile **39.55.9**. One patch covers both —
no need to pick.

<details>
<summary><b>How <code>Disable ads</code> works</b></summary>

ZEE5 serves ads down two independent paths, both driven by Google IMA:

- **Client-side (CSAI)** — the VMAP playlist is assembled locally and handed to
  ExoPlayer as a `data:text/xml` URI.
- **Server-side (DAI)** — `ImaServerSideAdInsertionUriBuilder`, where ads are
  stitched into the video stream itself and normally cannot be removed on-device.

Both are chosen inside the player module's `toMediaItem()`, and both hang off a
single `AdConfig`:

```java
if (adConfig != null && adConfig.getDaiAssetId()?.isNotEmpty()) return toImaServerSideMediaItem(...)
builder.setAdsConfiguration(adConfig?.getAdTagUrl()?.let { AdsConfiguration(it) })
```

Defeat that one object and both paths go with it — playback falls through to the
plain content URL.

The two builds reach it differently, so the patch tries both and applies whichever
fits:

**Android TV** — `PlaybackViewModel.toMediaConfig()` discards the whole `AdConfig`
when `PlaybackBridge.canDisableAds()` returns true. Stock, that is
`isSubscribed() && !enableAdsForSubscribed(remoteConfig)`, so forcing it true puts
the app into a state it already ships and exercises for every paying subscriber —
no untested code path. Preferred wherever it exists, for exactly that reason. The
method is matched by *shape* (boolean, no parameters, non-abstract) rather than by
class name.

**Mobile** — there is no such bridge. The mobile build **always** constructs an
`AdConfig` and disables ads by leaving its fields empty, so there is nothing to
force true. Instead the patch empties it at the source: `getAdTagUrl()` and
`getDaiAssetId()` are made to return null, which `toMediaItem()` cannot
distinguish from a null `AdConfig`. Both getters are annotated `@Nullable` in the
app's own code and every caller null-checks them, so this stays inside the
contract the app already declares. R8 minifies the class to
`com/zee/mediaplayer/config/a` in this build, but the package and the getter names
survive — and the fingerprint filters on that package so it cannot accidentally
match the IMA SDK's own `getAdTagUrl()`.

Either way the change governs only whether ads are attached to playback;
entitlement, DRM licensing and which content is playable are enforced elsewhere
and are untouched.

</details>

## 📲 Applying with ReVanced Manager

Manager takes a **JSON manifest URL**, not a `.rvp` directly.

1. Open ReVanced Manager (v2.6.0 or newer)
2. Go to the **Patches** tab
3. Tap the ✏️ edit button, then the **+** button
4. Choose **Enter URL** and paste:

   ```
   https://raw.githubusercontent.com/dbhavsar76/revanced-patches/main/patches.json
   ```

5. Select the target app and apply


> [!IMPORTANT]
> **Use *Select from storage*, not *Select an app*.** Patching an installed app
> fails at the very last step with
> `FileNotFoundException: …/result.apk: EACCES` — Manager copies the installed APK
> preserving its mode, and installed APKs are read-only and fs-verity sealed, so
> the output cannot be reopened for writing. Patching a file from storage avoids
> it entirely.
>
> **Manager cannot patch app bundles.** If your only download is an `.apkm`,
> `.xapk` or `.apks`, Manager will fail — its troubleshooting guide lists
> "patching a full APK file and not an APK bundle" as a cause. A Play Store
> install is itself split, so this affects *Select an app* too. Flatten the bundle
> first, merging only the splits matching your device's ABI **and density**:
> [Getting a single APK from a bundle](docs/DEVELOPMENT.md#getting-a-single-apk-from-a-bundle).
>
> **Manager needs Android 8.0+.** On older devices — many Android TV boxes —
> patch elsewhere and sideload the result. The
> [CLI route](#-applying-with-revanced-cli) runs on a desktop and has no such
> limit.

## 🖥️ Applying with ReVanced CLI

Download `patches-1.0.0.rvp` from the [releases](../../releases), then:

```bash
java -jar revanced-cli-6.0.0-all.jar patch -p patches-1.0.0.rvp -b -o patched.apk input.apk
```

`-b` is required: these bundles are not PGP-signed, and `revanced-cli` only
accepts verification when given a signature, a public key ring **and** an
attestation together — no subset of those lets you drop `-b`.

Each release does carry a **build provenance attestation**, which you can check
independently before patching. It proves the `.rvp` was built by this
repository's workflow and not substituted afterwards:

```bash
gh attestation verify patches-1.0.0.rvp --repo <user>/revanced-patches
```

Success is `INFO: "Disable ads" succeeded` — check the **head** of the log, not
the tail. A bundle that fails to load prints `SEVERE` at the start and then
continues through dex recompilation and signing, exiting 0 with a valid but
**completely unpatched** APK.

## 📱 Before you patch

**ReVanced needs a single APK.** Apps shipped as `.apkm` / `.xapk` / `.apks`
bundles must be flattened first, merging only the splits matching your device's
ABI — see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md#bundles).

**Older or 32-bit devices.** Merge the `armeabi-v7a` split rather than
`arm64-v8a`, and confirm the result before sideloading:

```bash
aapt2 dump badging patched.apk | grep -iE 'minsdkversion|native-code'
```

For Android 6 (API 23) the APK also needs a **v1 JAR signature** — v2 alone is not
enough, as v1 is what pre-Nougat Android understands:

```bash
apksigner verify --verbose patched.apk
```

**Signature mismatch.** A patched APK is signed with a different key than the
Play Store build, so it will not install over it. Uninstall the original first —
that clears its data and signs you out.

## 🔨 Building from source

Requires **JDK 21** and Gradle 8.9+. Gradle itself may run on a newer JVM, but a
JDK 21 must be installed for the toolchain — see the note below.

ReVanced publishes only to GitHub Packages, which needs authentication even for
public packages. Create a token with the single scope `read:packages`
[here](https://github.com/settings/tokens/new?scopes=read:packages&description=ReVanced),
then add it to `~/.gradle/gradle.properties` — outside this repo, so it is never
committed:

```properties
gpr.user = <your-github-username>
gpr.key = <the-token>
```

```bash
gradle :patches:jar
```

The `.rvp` lands in `patches/build/libs/`.

> **A JDK 21 toolchain is required, and the build declares it.** Homebrew's Gradle
> launches on JDK 26, and the Kotlin compiler cannot parse a two-digit major
> version — it dies with `IllegalArgumentException: 26.0.1` and surfaces only as
> "Internal compiler error", pointing nowhere near the cause. `jvmToolchain(21)`
> in [patches/build.gradle.kts](patches/build.gradle.kts) runs the compiler on
> JDK 21 whatever Gradle itself started on, so no `org.gradle.java.home` and no
> machine-specific path is needed. If Gradle cannot find a JDK 21 it fails with
> "No matching toolchains found" — install one, or point it at yours with
> `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.

## 🚀 Publishing a release

Tag a commit on `main` and push the tag — [the release workflow](.github/workflows/release.yml)
does the rest:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

It builds `patches-1.1.0.rvp` (version taken from the tag), verifies the bundle
actually contains patch classes, publishes it as a release asset, then rewrites
[patches.json](patches.json) on `main` to point at the new release.

**Users keep the same URL.** What they add to Manager is the *manifest* URL, not
the `.rvp`:

```
https://raw.githubusercontent.com/<user>/revanced-patches/main/patches.json
```

That never changes. Each release only rewrites the file it serves, and Manager
re-reads it and compares `version` to spot an update. Expect a few minutes' lag —
`raw.githubusercontent.com` is CDN-cached.

### Signing

Releases are **not PGP-signed**. Each one does get a build provenance attestation
(free, keyless — GitHub's OIDC identity), published as a `.rvp.sigstore.json`
asset and verifiable with `gh attestation verify`.

To add PGP signing later, sign the `.rvp` in the release job, publish the
detached `.asc` and the public key as assets, and set `signature_download_url` in
the manifest. Two things worth knowing before doing so:

- **ReVanced Manager ignores signatures entirely** — it has no verification code
  and reads only `download_url`, `version` and `created_at`. This only helps CLI
  users.
- `revanced-cli` requires signature **and** key ring **and** attestation together,
  so PGP alone still would not let anyone drop `-b`.

> [!NOTE]
> The workflow needs to read `app.revanced:patcher` from GitHub Packages. The
> automatic `GITHUB_TOKEN` is scoped to this repository and generally **cannot**
> read packages owned by another org. If the build fails with 401/403, add a PAT
> with scope `read:packages` as the repository secret `GPR_KEY` (plus `GPR_USER`
> if your username differs from whoever pushes the tag).

## 🛠️ Development

Reverse-engineering workflow, bundle handling, and the toolchain traps that cost
real time are documented in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## ⚖️ Scope and legality

For personal use on apps installed on hardware I own. These patches modify a
locally installed app's behaviour; they do not redistribute the app, defeat
paid-content licensing, or share proprietary code. No APKs, decompiled sources,
or app assets are committed to this repository.

Removing ads has real consequences for the developers of apps you like — pay for
the ones worth paying for.
