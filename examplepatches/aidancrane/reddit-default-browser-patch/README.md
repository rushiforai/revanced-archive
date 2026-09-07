# Reddit Default Browser patch

A small ReVanced patch that sends Reddit web links to Android's configured default browser instead of Reddit's in-app browser or full-bleed player.

The repository contains patch source only. It does **not** contain or distribute Reddit APKs, modified APKs, signing keys, or proprietary Reddit resources.

> [!IMPORTANT]
> This code was generated with AI assistance and has not been independently reviewed for code quality or security. It has been empirically tested and works on the author's OnePlus 9 Pro with Reddit `2026.15.1`; broader device or version compatibility is not guaranteed.

## Compatibility

- Android package: `com.reddit.frontpage`
- Tested Reddit version: `2026.15.1` (`versionCode 2615150`)
- Patch bundle version: `1.0.1`
- Build requirement: JDK 21

Patch fingerprints intentionally target one Reddit release. A newer Reddit build may fail to patch or may introduce new navigation routes.

## Covered routes

Version 1.0.1 was verified on a physical Android device for:

- an external-link thumbnail in a subreddit feed;
- the title/text area of an external-link post in the full post view; and
- the external-link thumbnail in the full post view.

The runtime extension asks Android for the configured default handler for browsable HTTP links. If Android cannot identify or launch one, Reddit's original behavior remains as a fallback. No browser package is hard-coded.

## Build

Clone the repository, install JDK 21, then run:

```shell
./gradlew clean buildAndroid
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean buildAndroid
```

The Manager-compatible Android/DEX patch bundle is written beneath `patches/build/` with an `.rvp` extension. The exact filename contains the project version. A normal `build` may produce JVM-oriented outputs; use `buildAndroid` for ReVanced Manager.

The Gradle wrapper downloads Gradle, and the build resolves the ReVanced patcher and Android build dependencies from public dependency repositories. Network access is therefore required for a cold build. A GPL-3.0 ReVanced Gradle-plugin source snapshot is included under `build-logic/` because upstream's compiled plugin is distributed through authenticated GitHub Packages; its provenance is recorded in [NOTICE](NOTICE).

## Use with ReVanced Manager

1. Build or download the release `.rvp` file.
2. In a ReVanced Manager version that supports local patch bundles, open the patch source/bundle selector and import the `.rvp` from local storage.
3. Select a clean Reddit `2026.15.1` APK for package `com.reddit.frontpage`.
4. Select **Open links in default browser**, then patch and install using Manager's normal workflow.

## License and attribution

Copyright (C) 2026 Aidan.

The independently authored patch and runtime extension are dual-licensed under your choice of the permissive [MIT License](LICENSE) or GPL-3.0-only. Official ReVanced build configuration and the vendored build plugin remain GPL-3.0-only, and Gradle wrapper files remain Apache-2.0. See [NOTICE](NOTICE) and `LICENSES/` for the exact file-by-file boundaries and full license texts.
