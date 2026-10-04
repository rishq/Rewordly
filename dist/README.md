# Rewordly 0.1.0 — direct download

Signed release build, published here so it can be downloaded without a GitHub Release page.

| | |
| --- | --- |
| File | `rewordly-0.1.0.apk` |
| Version | `0.1.0` (versionCode 1) |
| Size | 3.7 MB |
| Package | `com.rewordly.app` |
| Requires | Android 8.0+ (minSdk 26), targetSdk 35 |
| SHA-256 | `e4df60d0de2fff03c5c8bc286710d19f2aa6d7b5239005c45e677dfa10f1b34c` |

## Install

On a connected device:

```bash
adb install rewordly-0.1.0.apk
```

Or copy the file to the phone and open it. Android will ask you to allow installing from this source,
because the build is sideloaded rather than distributed through Play — that prompt is expected.

## Verify the download

The checksum is in `rewordly-0.1.0.apk.sha256`:

```bash
sha256sum -c rewordly-0.1.0.apk.sha256
```

The APK is signed with APK Signature Scheme v2 and v3. To inspect the signer:

```bash
apksigner verify --print-certs rewordly-0.1.0.apk
```

Signer certificate SHA-256 fingerprint:

```
53:4D:3B:55:2A:E5:AC:79:FB:4B:94:2B:E7:4F:DC:87:CD:2D:02:87:30:B6:75:A5:BD:33:B3:29:47:AD:BE:E2
```

If a future build reports a different fingerprint, it was signed with a different key and Android will
refuse to install it as an update.

## About this directory

`dist/` holds the APK that the release workflow stages (`dist/rewordly-<version>.apk`), so the filename
here matches what a published release would produce.

`*.apk` is ignored globally by `.gitignore`; `dist/*.apk` is explicitly re-included, so this one file is
tracked on purpose. Nothing else in this directory should be committed.
