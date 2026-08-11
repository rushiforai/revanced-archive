<div align="center">

# ReVanced Patches

**Focused patches for supported Android apps.**

[![Build](https://github.com/simnple/revanced-patches/actions/workflows/build.yml/badge.svg)](https://github.com/simnple/revanced-patches/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/simnple/revanced-patches)](https://github.com/simnple/revanced-patches/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

## Add to ReVanced Manager

Choose **Remote** and enter this patch bundle URL:

```text
https://raw.githubusercontent.com/simnple/revanced-patches/main/patches.json
```

For a local installation, [download the latest RVP bundle](https://github.com/simnple/revanced-patches/releases/latest/download/patches-1.4.1.rvp) and choose **Local** in ReVanced Manager.

## Supported apps

<details>
<summary><strong>Goondori (군돌이)</strong> · <code>com.goondori</code> · 5.6.0 (214)</summary>

### Adrop

| Patch | Function |
| --- | --- |
| `Disable Adrop Ads` | Stops the advertising SDK from initializing and receiving a user ID. |
| `Disable Adrop Metrics` | Blocks Adrop user properties and analytics events. |

### Telemetry

| Patch | Function |
| --- | --- |
| `Disable Firebase Analytics` | Blocks events, user IDs, user properties, and default event parameters. |
| `Disable Firebase Startup Collection` | Disables collection and advertising-personalization signals at startup. |
| `Disable Sentry` | Disables error and performance telemetry. |

### Network services

| Patch | Function |
| --- | --- |
| `Disable Firebase Logging` | Stops Firebase and Google DataTransport telemetry sent through `firebaselogging.googleapis.com`. |
| `Disable Firebase Remote Config` | Stops Firebase from fetching server-controlled settings. |
| `Disable Funding Choices` | Stops Google Funding Choices and UMP advertising-consent requests. |
| `Disable RevenueCat` | Stops RevenueCat subscription, entitlement, purchase-status, and paywall requests. RevenueCat-backed purchases will not work. |

### Installation

| Patch | Function |
| --- | --- |
| `Disable Google Play Automatic Protection` | Makes Goondori's Google Play PairIP installer check accept APKs installed outside Google Play while preserving PairIP initialization. |

### Navigation

| Patch | Function |
| --- | --- |
| `Hide Vacation Tab` | Hides the Vacation tab from the bottom navigation. |
| `Hide Community Tab` | Hides the Community tab from the bottom navigation. |
| `Hide Content Tab` | Hides the Content tab from the bottom navigation. |
| `Hide Store Tab` | Hides the Store tab from the bottom navigation. |

</details>

## License

[MIT](LICENSE)
