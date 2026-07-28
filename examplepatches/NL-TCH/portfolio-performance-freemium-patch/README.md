# Portfolio Performance Freemium – ReVanced Patch

A ReVanced patch that unlocks all premium features of
**Portfolio Performance** (`software.msm.portfolio_performance`) by
spoofing the RevenueCat `CustomerInfo` response inside the Flutter plugin.
No network calls or RevenueCat account are needed.

---

## What it patches

| Patch | Target method | What changes |
|---|---|---|
| **getCustomerInfo** | `PurchasesFlutterPlugin.getCustomerInfo()` | Immediately resolves the Flutter callback with fake premium `CustomerInfo` |
| **getOfferings** | `PurchasesFlutterPlugin.getOfferings()` | Immediately resolves the Flutter callback with fake premium `Offerings` |
| **channelSend** | `PurchasesFlutterPlugin.b()` (static synthetic) | Swaps `Purchases-CustomerInfoUpdated` and `Purchases-OfferingsUpdated` payloads with fake data |

The fake `CustomerInfo` contains:
- Entitlement `premium` → `isActive: true`, expires `2099-01-01`
- Product `pp_premium_v1` in `activeSubscriptions` and `allPurchasedProductIdentifiers`

---

## Version compatibility

Tested and verified on:

| App version | Status |
|---|---|
| 1.2.4 (APK from APKMirror) | Working – **requires matching obfuscated types** (see below) |
| 1.11.1 (ReVanced Manager 1.25.1) | Previously working |

### Obfuscated type mapping

The RevenueCat Flutter SDK classes are obfuscated by R8. These mappings
**change between app versions** and must be verified before patching:

| Type | Description | v1.2.4 value |
|---|---|---|
| `CALLBACK_TYPE` | Flutter `Result` callback interface | `Lb4/j$d;` |
| `CALLBACK_METHOD` | Callback method `(Object)V` | `Lb4/j$d;->a(Ljava/lang/Object;)V` |
| `MethodCall` class | First param of `onMethodCall` | `Lb4/i;` |

If the app crashes with `NoClassDefFoundError` or `NoSuchFieldError`, these
types have changed in the new version. Use androguard or jadx to find the
correct obfuscated names:

```python
from androguard.misc import AnalyzeAPK
apk, dalvik, dx = AnalyzeAPK("path/to/app.apk")
for c in dx.get_classes():
    if "PurchasesFlutterPlugin" in c.name and "$" not in c.name:
        for m in c.get_methods():
            if m.name == "getCustomerInfo":
                print(f"getCustomerInfo: {m.get_descriptor()}")
```

---

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 17+ | Required by Gradle and ReVanced patcher |
| Gradle (via wrapper) | 8.8 | Bundled in the repo (`./gradlew`) |
| ReVanced Patcher | **22.0.1** | Updated for ReVanced Manager v2 |
| GitHub token | `read:packages` scope | For ReVanced Maven packages |
| ADB | any | For direct install to device (optional) |

---

## Build

### 1. Set up GitHub credentials

The ReVanced patcher library is hosted on GitHub Packages and requires authentication.

Create `~/.gradle/gradle.properties` (or add to the existing file):

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PERSONAL_ACCESS_TOKEN
```

Your token needs the **`read:packages`** scope.
Create one at: https://github.com/settings/tokens

### 2. Build the patch bundle

```bash
cd portfolio-performance-freemium-patch
./gradlew buildAndroid
```

The output `.rvp` file will be at:

```
patches/build/libs/patches-2.0.0.rvp
```

---

## Patch & install workflow

### Option 1: ReVanced Manager v2 (recommended – patch from phone)

1. Open **ReVanced Manager v2** (latest version, Jetpack Compose)
2. Go to **Settings** → **Patch Sources**
3. Tap **+** to add a new source
4. Enter this URL:
   ```
   https://raw.githubusercontent.com/NL-TCH/portfolio-performance-freemium-patch/master/patch-bundles/portfolioperformance-latest-patches-bundle.json
   ```
5. The **Portfolio Performance Freemium** patch source will appear
6. Go to **Patcher** → select **Portfolio Performance** APK
7. Enable **Unlock premium** → **Patch**

### Option 2: Import .rvp file manually

1. Copy `.rvp` file to device storage
2. ReVanced Manager → **Settings** → **Patch Sources** → **+** → **Import from storage**
3. Select the `.rvp` file
4. **Patcher** → select Portfolio Performance APK
5. Enable **Unlock premium** → **Patch**

### Option 3: CLI + ADB

```bash
# Patch the APK
java -jar revanced-cli-5.0.1-all.jar patch \
  -p patches/build/libs/patches-*.rvp \
  -b \
  -e "Unlock premium" \
  -o patched.apk \
  -i \
  -f \
  path/to/portfolio_performance.apk
```

---

## CI/CD: Automated releases

This repo uses [semantic-release](https://github.com/semantic-release/semantic-release)
to automatically create GitHub Releases with `.rvp` files.

### Setup (GitHub Actions)

1. Go to **Settings** → **Secrets and variables** → **Actions**
2. Add repository secrets:
   - `GPG_PRIVATE_KEY` – your GPG private key (for signing)
   - `GPG_PASSPHRASE` – your GPG passphrase
3. Add repository variables:
   - `GPG_FINGERPRINT` – your GPG subkey fingerprint
4. Push to `main` or `master` – a release will be created automatically

### Updating the bundle JSON after a new release

After each release, update the version in:
```
patch-bundles/portfolioperformance-latest-patches-bundle.json
```

Change `download_url` to point to the new release:
```json
{
  "download_url": "https://github.com/NL-TCH/portfolio-performance-freemium-patch/releases/download/v2.0.0/patches-2.0.0.rvp",
  "version": "v2.0.0"
}
```

---

## Troubleshooting

### App stuck on splash screen / crashes on launch

Check ADB logs for the root cause:

```bash
adb logcat -c
adb shell am force-stop software.msm.portfolio_performance
adb shell am start -n software.msm.portfolio_performance/.MainActivity
sleep 5
adb logcat -d --pid=$(adb shell pidof software.msm.portfolio_performance) | grep -iE "error|exception|verify|crash"
```

Common errors and fixes:

| Error | Cause | Fix |
|---|---|---|
| `NoClassDefFoundError: j2.j$d` | Wrong callback type in smali | Update `CALLBACK_TYPE` constant in `FreemiumPatch.kt` |
| `NoSuchFieldError: No field a` | Wrong MethodCall class/field | Use androguard to find correct obfuscated names |
| `VerifyError: register v1 has type HashMap but expected CustomerInfo` | Method signature changed | Remove the offending patch step (usually method `c`) |

---

## Architecture

```
patches/src/main/kotlin/.../FreemiumPatch.kt   ← Patch definition + smali injection
extensions/portfolioperformance/.../FakePremium.java  ← Java helper (builds fake data)
```

The patch works by injecting calls to `FakePremium` static methods at the
beginning of RevenueCat SDK methods, before the real SDK logic executes.
The fake data is a `HashMap` shaped exactly like the real RevenueCat
`CustomerInfo` / `Offerings` response that the Flutter side expects.

---

## License

GPL-3.0 – see [LICENSE](LICENSE)
