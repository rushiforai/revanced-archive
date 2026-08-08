# workspace/

Gitignored scratch area. Everything in here except this file is untracked —
APKs, decompiled trees, and build output stay local.

| Directory | Contents |
| --- | --- |
| `apks/` | Input APKs, plus any `.apkm`/`.xapk`/`.apks` bundles and the merged APK produced from them. Name them `<package>-<version>.apk` so the version is never ambiguous, and keep the `-merged` suffix on merge output so it's obvious which file is patchable. |
| `decompiled/` | `apktool d` output — smali + decoded resources. This is what patches actually modify. |
| `jadx/` | `jadx` output — readable Java. For reading only; never rebuild from this. |
| `notes/` | Per-app findings: class names, method signatures, what worked, what didn't. |
| `tools/` | Downloaded JARs — APKEditor, ReVanced CLI. Not committed; re-download as needed. |
| `out/` | Rebuilt and signed APKs. |

Recreate the layout after a fresh clone:

```bash
mkdir -p workspace/{apks,decompiled,jadx,notes,tools,out}
```

Decompiled trees get large (a mid-size app is 1–3 GB across both tools). Delete
them when done rather than letting them accumulate.
