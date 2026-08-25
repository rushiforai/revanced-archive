# Dual subtitles for language learning: research and plan

Status: research finished, implementation not started. Every claim below marked
"verified" was tested on this machine on 2026-08-23; everything else is design.

## 1. Goal

Show two subtitle tracks at the same time while a video plays: the language being
learned (L2) and a native-language line (L1) underneath it. Target platform is
Android, target app is the official YouTube app modified with ReVanced, unless a
cheaper target is chosen (see section 8).

## 2. State of the ReVanced project (verified)

* `github.com/ReVanced/revanced-patches` is offline: HTTP 403, taken down after a
  DMCA notice filed 2026-03-12. The other repositories (`revanced-patcher`,
  `revanced-cli`, `revanced-manager`, `revanced-patches-gradle-plugin`) are up.
* The patches source lives at `https://gitlab.com/revanced/revanced-patches.git`
  and is current: cloned here, HEAD is v6.1.0 plus a few commits.
* Component versions: patches 6.1.0, patcher 22.0.1, CLI 6.0.0, patches Gradle
  plugin 1.0.0-dev.10 (the plugin repository itself is already at dev.11).
* Building the patches repository needs the plugin from GitHub Packages, which
  requires credentials even for public packages. `./gradlew` fails with
  "The following Gradle properties are missing for 'githubPackages' credentials:
  githubPackagesUsername, githubPackagesPassword". Two ways out:
  1. a GitHub personal access token with `read:packages`, put into
     `~/.gradle/gradle.properties`, or
  2. build the plugin from source and publish it to the local Maven repository.
     Tried; it fails because the plugin sets `jvmToolchain(17)` and only JDK 21
     is installed. JDK 17 is in nixpkgs (17.0.20), so this is one line in
     `home/marius.nix` plus a deploy.
* Build task for the patch bundle is `./gradlew :patches:buildAndroid`, which
  produces a `.rvp` file. The CLI can be given several `.rvp` bundles at once,
  but a patch can only declare `dependsOn` on patches inside its own bundle.
  Since a dual subtitle patch must depend on `sharedExtensionPatch`,
  `videoInformationPatch`, `playerControlsPatch` and `settingsPatch`, the work
  has to happen inside a fork of the patches repository, not in a separate
  bundle.

## 3. How a ReVanced patch is built (from the source read here)

A patch has two halves.

* The *patch*, written in Kotlin, runs on the PC at patch time. It edits the
  app's Dalvik bytecode through the patcher API: it locates obfuscated methods by
  their attributes (access flags, return type, parameter types, opcode sequences,
  referenced string constants and resource identifiers) and inserts
  `invoke-static` calls into them. Modern patches use the declarative matching
  API, for example `gettingFirstMethodDeclaratively("DISABLE_CAPTIONS_OPTION")`
  in `patches/.../youtube/layout/autocaptions/Fingerprints.kt`.
* The *extension*, written in Java under `extensions/youtube/src/main/java`, is
  compiled to a dex file and merged into the patched APK. All real logic lives
  here; the bytecode edits only provide entry points. Extensions can use the
  Android framework, `HttpURLConnection`, JSON, protobuf, and the ReVanced
  settings and preference infrastructure.

Existing hooks the dual subtitle feature can reuse without any reverse
engineering of its own:

| Need | Existing mechanism |
| --- | --- |
| Current video id | `VideoInformation.getVideoId()`, hooked by `videoIdPatch` |
| Current playback position | `VideoInformation.getVideoTime()`, hooked by `videoTimeHook`; the hook fires once per second on the main thread |
| Playback speed, video length, seek | `VideoInformation` fields and `seekTo` |
| Play/pause state, Shorts detection | `VideoState`, `ShortsPlayerState`, `PlayerType` observers |
| A `ViewGroup` covering the player, to attach an overlay to | The hook SponsorBlock uses: `SponsorBlockViewController.initialize(ViewGroup)` is injected after the player controls overlay `FrameLayout` is created |
| Player controls fade in and out | `PlayerControlButton`, `injectVisibilityCheckCall` |
| Settings screen entries | `PreferenceScreen.PLAYER.addPreferences(...)` plus `addResources` strings |
| Logging | `Logger.printDebug`, tag prefix `revanced: `, plus an in-app log buffer that can be exported from the settings |

The 1 Hz time hook is too coarse for subtitles on its own. The fix is to
interpolate: store `videoTime` and `SystemClock.elapsedRealtime()` on every hook
call, and between calls advance the estimate by elapsed wall clock multiplied by
the current playback speed, resetting on seek and on pause.

## 4. Where the subtitle text comes from (verified experiments)

The Android InnerTube player endpoint answers unauthenticated requests.

```
POST https://youtubei.googleapis.com/youtubei/v1/player?fields=captions,responseContext
Headers: Content-Type: application/json
         User-Agent: com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip
         X-YouTube-Client-Name: 3
         X-YouTube-Client-Version: 20.10.38
Body: {"context":{"client":{"clientName":"ANDROID","clientVersion":"20.10.38",
       "androidSdkVersion":34,"hl":"en","gl":"DE"}},"videoId":"...",
       "contentCheckOk":true,"racyCheckOk":true}
```

* The response carries `captions.playerCaptionsTracklistRenderer` with
  `captionTracks` (language code, `kind` = `asr` for auto-generated, display name,
  signed `/api/timedtext` `baseUrl`), `translationLanguages` (the languages
  YouTube will translate into) and `defaultTranslationSourceTrackIndices`. About
  30 KB with `fields=captions`. `responseContext.visitorData` comes back in the
  same call.
* Fetching a `baseUrl` with `fmt=json3` returns the cues as JSON with `tStartMs`
  and `dDurationMs`, and each cue is split into segments carrying `tOffsetMs`,
  which is word-level timing.
* Adding `tlang=<language>` returns YouTube's own machine translation of that
  track. The first requests answer 429; retried, the same request eventually
  answers 200 and from then on answers immediately. Measured on a video not
  requested before: five 429s over roughly forty seconds, then 200 with correctly
  translated text, then an instant 200 on a repeat. Asking for a different target
  language starts the same cycle again. Sending `X-Goog-Visitor-Id` with the
  `visitorData` from the player response makes success more likely.
  So 429 means "not ready yet or throttled", not "forbidden", and a background
  retry with backoff is all that is needed for the one language pair a viewer
  actually wants.
* The translated track is index aligned with its source: same number of events,
  identical start times, and word offsets preserved. Pairing the L2 and the L1
  line is a zip over two arrays, with no timing alignment work.
* The WEB client returns no caption tracks at all without a proof-of-origin
  token, so the Android client is the one to use.

ReVanced already captures the app's own InnerTube request headers, including
`Authorization` for signed-in users and `X-Goog-Visitor-Id`, and replays them on
its own requests (`StreamingDataRequest.java`). The same mechanism is available
to this patch if plain requests ever stop being enough.

## 5. Translation

None of our own is needed. YouTube produces the second line, through `tlang`,
in any language listed in `translationLanguages` (18 for the videos sampled).
What the extension has to do is:

* fetch the caption track list once per video,
* pick the L2 track (a real track in that language, else the `asr` track),
* fetch it as `json3`,
* fetch it again with `tlang=<L1>`, retrying with backoff while the answer is
  429, and show the L2 line alone until the second answer arrives,
* cache both on disk keyed by video id and language pair, so a rewatch and a
  restart cost nothing.

If a language pair turns out to be missing or the quality is unusable for a
particular purpose, a translation backend can be added later behind the same
interface. It is not needed to ship.

## 6. Architecture of the patch

```
YouTube app (patched)
├── bytecode hooks (Kotlin patch)
│     ├── video id      → DualSubtitlesPatch.setVideoId
│     ├── video time    → DualSubtitlesPatch.setVideoTime
│     └── player overlay ViewGroup → DualSubtitleOverlay.initialize
└── extension (Java, in the APK)
      ├── CueRepository     fetch, parse, cache tracks per video id
      ├── TranslationClient nasx service or fallback
      ├── Timeline          interpolated playback clock, binary search for cues
      └── DualSubtitleView   two TextViews, styling, position, fade with controls
```

Settings to add under the player preference screen: enable/disable, L2 language,
L1 language, source of the second line (real track, service, none), text sizes,
background opacity, whether to hide the app's own captions, service URL, and a
debug toggle that shows the current cue timing.

The app's own subtitle rendering is left alone for the first version; the overlay
is drawn above it and the user turns the native captions off. Forcing them off
from the patch is a later refinement, and there is precedent for touching that
code path in the existing `Disable auto captions` patch.

## 7. Development, debugging and reverse engineering loop

Tooling to add declaratively in `home/marius.nix` (all present in nixpkgs):
`jdk17` (17.0.20), `jadx` (1.5.5) for reading the decompiled app, `apktool`
(3.0.2) for resources, `apkeep` (1.0.0) to download a specific YouTube version
without fighting APKMirror's bot protection.

Loop:

1. Get the APK for a version listed in `compatibleWith`, currently up to
   20.40.45, arm64-v8a.
2. `./gradlew :patches:buildAndroid` in the fork to produce the bundle.
3. `java -jar revanced-cli-6.0.0-all.jar patch -p patches.rvp --exclusive -e "Dual subtitles" -e ... youtube.apk -o out.apk`, optionally `-i <serial>` to
   install over ADB.
4. Watch `adb logcat | grep revanced`.

Testing device: the local x86_64 emulator can run the arm64 YouTube build.
Verified on the existing `dbg` AVD (Android 34, x86_64): `ro.product.cpu.abilist`
is `x86_64,arm64-v8a` and `/system/lib64/libndk_translation.so` is present, so
ARM64 native libraries are translated. Video decoding uses the platform codecs,
so playback should be usable; the physical phone stays for final checks only.

Reverse engineering, when a hook does turn out to be needed: decompile the target
APK with jadx, search for the string constants and resource names the feature
touches (the existing patches show the pattern, for instance
`accessibility_captions_button_name` identifying the captions button controller),
then express the find as attributes in a `Fingerprints.kt` rather than as a
hard-coded obfuscated name, so the patch survives app updates. Runtime
inspection with Frida is possible on the emulator if static reading is not
enough; it is not packaged yet but is in nixpkgs.

A useful shortcut for the first iterations: build the overlay, the cue parser and
the timeline as a plain Android library module and drive it from a throwaway test
app with a local video. Compile cycles there are seconds instead of minutes, and
the finished classes move into `extensions/youtube` unchanged.

## 7a. Learning features beyond the two lines

**Tap a word.** The overlay renders the L2 line as a `SpannableString` with one
clickable span per word. Word boundaries are already known: `json3` splits every
cue into segments with their own `tOffsetMs`, so the split is the data's own, not
a guess from whitespace. A tap pauses playback and opens a panel showing the
word, its IPA, the sentence translation already on screen, and a dictionary
entry.

**IPA.** Reuse the data from the phonetix extension rather than building a second
pipeline. Its dictionaries are plain `word -> IPA` JSON maps, gzipped per
language, and they are small enough to live on the phone one language at a time
(English 1.8 MB, German 4.8 MB, most others far less; 35 MB only if all of them
are taken). The extension downloads the dictionary for the chosen L2 once into
the app's files directory and looks words up in memory. The espeak-ng fallback
for words no dictionary has is 24 MB of WASM in phonetix and does not belong in a
patched APK; words that miss simply show no IPA at first. A native espeak-ng or a
lookup service can fill that gap later. Homograph disambiguation and accent
overlays are also phonetix data and can follow the same route.

**Anki export.** The panel gets an "add card" action. AnkiDroid takes cards
either through its `AddContentApi` ContentProvider, which needs a permission
declared in the manifest, or through an intent, which needs none. Start with the
intent; move to the provider if duplicate handling or deck selection needs it.
The card carries the word, the IPA, the L2 sentence and the L1 sentence, so the
context comes along.

## 7b. Phases

0. **Setup.** Add `jdk17`, `jadx`, `apktool` and `apkeep` to `home/marius.nix`
   and deploy. Build the patches Gradle plugin from source into the local Maven
   repository. Fork the patches repository onto a working branch. Download a
   YouTube build listed in `compatibleWith`. Patch it with an unmodified bundle,
   install it on the emulator, confirm it runs. This proves the whole toolchain
   before a line of feature code exists.
1. **Overlay skeleton.** A patch that attaches an empty overlay to the player and
   logs the interpolated playback clock. Confirms the injection point, the
   settings entry and the debug loop.
2. **Dual subtitles.** Track list fetch, `json3` parsing, `tlang` fetch with
   backoff, disk cache, cue lookup against the interpolated clock, two styled
   lines that fade with the player controls and survive rotation and fullscreen.
3. **Tap a word.** Clickable spans, pause on tap, the panel, dictionary lookup
   and IPA.
4. **Anki export** from the panel.
5. **Polish.** Native caption handling, Shorts, background playback, settings for
   sizes and positions, and rebasing on upstream once to see how much of the
   patch survives an update.

## 8. Cheaper alternatives, for comparison

* **An open-source client instead of the YouTube app.** NewPipe, Tubular or
  LibreTube are ExoPlayer based, and ExoPlayer renders cues through a
  `SubtitleView` that a second instance can simply be added next to. No
  obfuscation, no fingerprints, no patch bundle, and the same InnerTube caption
  fetching works there. Estimated effort is a fraction of the patch route. What
  is lost is the YouTube app itself: account, recommendations, watch history,
  picture in picture behaviour, and everything else the official client does.
* **An external overlay app** that reads playback position from the media session
  YouTube publishes. Avoids patching entirely, but the media session exposes no
  video id, so the video would have to be identified by title, and the position
  updates are coarse. Fragile.

## 9. Risks

* App updates break fingerprints; keeping the fork rebased on upstream is
  ongoing work.
* YouTube keeps tightening InnerTube. The caption fetch used here is the same
  surface `yt-dlp` fights over; expect to need proof-of-origin tokens eventually.
  Moving the fetch to the nasx service isolates that fight from the phone.
* Upstreaming to ReVanced is unlikely to be accepted while the feature depends on
  a personal translation service; plan for a private fork. The fork is GPLv3, so
  changes have to be documented if it is ever published.
* Patched clients violate YouTube's terms of service. Personal use.
