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
https://raw.githubusercontent.com/simnple/revanced-patches/refs/heads/main/patches.json
```

For a local installation, [download the latest RVP bundle](https://github.com/simnple/revanced-patches/releases/latest/download/patches-1.7.1.rvp) and choose **Local** in ReVanced Manager.

## Supported apps

<details>
<summary><strong>Goondori (군돌이)</strong> · <code>com.goondori</code> · 5.6.0 (214)</summary>

### Ads

| Patch | Function |
| --- | --- |
| `Remove Ads` | Removes ads and their empty layout spaces, preserves the dashboard's ad-free spacing, and blocks Adrop initialization, user IDs, user properties, and analytics events without changing Goondori Premium entitlement. |

### Telemetry

| Patch | Function |
| --- | --- |
| `Disable Firebase Analytics` | Blocks events, user IDs, user properties, and default event parameters. |
| `Disable Firebase Startup Collection` | Disables collection and advertising-personalization signals at startup. |
| `Disable Sentry` | Disables error and performance telemetry. |

### Privacy and updates

| Patch | Function |
| --- | --- |
| `Remove Sensitive Logs` | Removes the access-token console log from session initialization. |
| `Disable Hot Updates` | Keeps the embedded patched UI bundle active while completing HotUpdater startup normally. Hermes UI patches select this dependency automatically. |
| `Disable Install Referrer` | Stops requests for the Google Play install-referrer value. |
| `Disable Push Registration` | Stops FCM and Expo push-token registration. Push notifications will not work. |

### Interface

| Patch | Function |
| --- | --- |
| `Hide Premium Promotions` | Hides the Premium entry without changing entitlement or purchase status. |

### Network services

| Patch | Function |
| --- | --- |
| `Disable Firebase Logging` | Stops Firebase and Google DataTransport telemetry sent through `firebaselogging.googleapis.com`. |
| `Disable Firebase Remote Config` | Stops Firebase from fetching server-controlled settings. |
| `Disable Funding Choices` | Stops Google Funding Choices and UMP advertising-consent requests. |

### Subscription network (optional)

| Patch | Function |
| --- | --- |
| `Disable RevenueCat` | Optionally stops RevenueCat subscription, entitlement, purchase-status, and paywall requests. RevenueCat-backed purchases will not work. |

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

### Home dashboard

| Patch | Function |
| --- | --- |
| `Hide Home Benefits` | Hides Benefits from the home dashboard. |
| `Hide Home Delivery` | Hides Goondori Delivery from the home dashboard. |
| `Hide Home Mailbox` | Hides Goondori Mailbox from the home dashboard. |
| `Hide Home Community` | Hides Community from the home dashboard. |
| `Hide Home Food Menu` | Hides the Food Menu from the home dashboard. |
| `Hide Home Vacation` | Hides Vacation from the home dashboard. |
| `Hide Home Feedback` | Hides the “How was your new home?” feedback card from the home dashboard. |

### More menu

| Patch | Function |
| --- | --- |
| `Hide More Benefits` | Hides Benefits from the More menu. |
| `Hide More Delivery` | Hides Goondori Delivery from the More menu. |
| `Hide More Food Menu` | Hides the Food Menu from the More menu. |
| `Hide More Mailbox` | Hides Goondori Mailbox from the More menu. |
| `Hide More Celebrity Support` | Hides Celebrity Support from the More menu. |

When all five More-menu functions are hidden, the empty **Features** section is removed automatically.

</details>

## License

[MIT](LICENSE)
