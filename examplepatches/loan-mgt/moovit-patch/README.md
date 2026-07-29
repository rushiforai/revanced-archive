# Moovit Patches

![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)

ReVanced patches for the [Moovit](https://moovitapp.com) transit app.

## Patches

| Patch | Description |
|---|---|
| **Remove ads** | Removes all ads — banners, interstitials, and map overlay ads. |
| **Unlock Moovit+** | Unlocks Moovit+ client-side features and skips the upgrade interstitials. |
| **GmsCore support** | Allows Moovit to run without stock Google Play Services, using [ReVanced GmsCore](https://github.com/revanced/gmscore). |
| **Fix location for GmsCore** | Forces native Android location instead of GmsCore's FusedLocationProvider, avoiding FLP reliability issues. |

## Supported versions

`com.tranzmate` — `5.194.0.1785`

## Building

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew build
```

Output: `patches/build/libs/patches-*.jar`

No external tokens or repos are required — shared patch infrastructure (extension hooks, GmsCore support, resource patching utilities) is vendored directly under `patches/src/main/kotlin/app/revanced/{patches/shared,patches/all,util}/`, sourced from [revanced/revanced-patches](https://github.com/revanced/revanced-patches) v6.1.1-dev.4.

## License

GPLv3. See [LICENSE](LICENSE).

This project vendors source files from [revanced/revanced-patches](https://github.com/revanced/revanced-patches), also GPLv3-licensed. Original copyright notices are preserved in each vendored file.
