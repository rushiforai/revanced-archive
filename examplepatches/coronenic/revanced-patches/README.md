# DCInside ReVanced Patches

ReVanced patches for the DCInside app (`com.dcinside.app.android`).

## Install in ReVanced Manager

Add this URL under **Patches → ✏️ → + → Enter URL**:

```
https://github.com/coronenic/revanced-patches/releases/latest/download/patches.json
```

Leave **Auto-update** on to receive new releases automatically. Then patch the DCInside app from the **Apps** tab.

Alternatively, download the `.rvp` from [Releases](https://github.com/coronenic/revanced-patches/releases/latest) and use **Select from storage**, or the [ReVanced CLI](https://github.com/ReVanced/revanced-cli).

## Patches

| Patch | Description |
| --- | --- |
| `Spoof signature` | Presents the original signing certificate to the app's own tamper checks so the re-signed build passes client- and server-side verification (fixes the blank 실시간 베스트 feed, error 2109, and the intermittent "error" popup on post submission). |
| `Add ReVanced patch version field` | Adds a "Revanced 패치 버전" field below the current and latest version in Settings > About. |

## License

GNU General Public License v3.0.
