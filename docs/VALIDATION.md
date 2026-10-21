# Verification record

Run date: 2026-10-21. Tested implementation: `59266fd`. Final documentation-only commits do not change the APK source.

## Successful CI evidence

[Android build run 39668972890](https://github.com/okthisisitright/prism-gallery/actions/runs/39668972890) is **successful**, with both build and API 35 emulator jobs green. The successful device job is the retry after a corrupt Android SDK emulator ZIP failed during setup; that download failure did not execute tests.

- **25 JVM tests passed**, 0 failures/errors: 13 media/geometry/identity/filter tests and 12 vault cipher/import-transaction tests. Production uses the tested `VerifiedImport` transaction helper.
- **6 Android instrumentation tests passed**, 0 failures: real MediaStore scanning, category metadata, multi-membership/cleanup, Keystore-backed vault import/unlock/tamper/relock and sensitive-bitmap clearing, rejected video import without source deletion, and the actual activity/UI smoke flow.
- **Android lint passed with 0 errors**. The final workspace report contains 22 warnings: dependency update notices, KTX/style suggestions, intentional synchronous persistence of retry throttling on IO, resource-folder convention and release resource-shrinking advice. No lint baseline or blanket suppression was added.
- `assembleDebug` and `assembleDebugAndroidTest` passed. The APK is development-signed; release signing is deliberately not configured.

Download the real [Prism-Gallery-debug-apk artifact](https://github.com/okthisisitright/prism-gallery/actions/runs/39668972890/artifacts/12377844485). The [device-test-reports artifact](https://github.com/okthisisitright/prism-gallery/actions/runs/39668972890/artifacts/12379925509) contains reports, raw results, device logs and `ui-screenshots.tar` with public Albums light/dark captures. GitHub may require sign-in. Artifact retention follows GitHub's settings; these are not permanent release URLs.

## What the Android tests establish

The scanner test grants image access on a clean AVD, inserts and publishes a unique test-owned PNG through MediaStore, finds it through the production scanner, opens/decodes its returned volume URI, and verifies stable identity across a second scan. It deletes only that fixture. Permission revocation is not attempted inside instrumentation because Android can kill the instrumented process.

The vault tests use actual Android Keystore wrapping and platform AES-GCM. They verify a real image import, retained source bytes, wrong-passphrase rejection, successful re-unlock, authenticated corruption rejection, lock state, clearing of a mutable protected bitmap copied from immutable input, and explicit video-import rejection. Room tests establish duplicate prevention, multiple categories per media key, persisted metadata, and cascading only category membership while favorites survive.

The UI test launches the real activity, creates and observes a Room-backed category, changes theme through DataStore, captures public UI screenshots, navigates the scrollable Albums page to Private Vault, and checks FLAG_SECURE on its window. This is smoke coverage; it does not establish that every gesture or device security behavior is correct. Screenshots were captured by CI; they were not visually inspected in this workspace because artifact-blob downloads were denied here.

## Executed in the workspace

The target repository was empty. No existing implementation or AGENTS.md was present. Android SDK platform 37.0 / Build Tools 36.0.0, full Temurin JDK 17 and Gradle 9.6.0 were installed.

The complete JVM-test/lint/app-package/test-package set passed locally after the final implementation changes. Instrumentation test compilation also passed after the final scanner/UI test corrections. `apksigner verify` passed for the local APK. The merged manifest has **no INTERNET or ACCESS_NETWORK_STATE permission**, and backup/device-transfer extraction is disabled across app-data domains.

Local delivered APK: `/workspace/artifacts/Prism-Gallery-debug.apk` (approximately 25 MiB). SHA-256:

```
7ebac33a2afd1852dbff241fe53282dcee83faf3ac54eaf7a86dbc04bbd0052e
```

The CI APK uses a different development signing identity and therefore has different bytes. Never uninstall an app containing the only vault copies merely to switch debug signing keys.

An API 35 software emulator was also attempted locally. Without `/dev/kvm`, startup was too slow; a concurrent compiler/emulator attempt exhausted memory headroom. That emulator was stopped. Local device/UI tests are **not** claimed; the executed device evidence above comes from GitHub's KVM-backed runner.

## Still needs physical-device acceptance

Unverified: Vivo/Funtouch OS; physical biometric enrollment/cancellation/key invalidation; device lock-screen removal; full and selected-photo/reselection/revocation UX; screen-off/idle timing on OEM devices; recents/recording blocking behavior beyond the window flag; low storage/power loss; very large libraries and frame times; actual public video codecs/play/seek; full EXIF/odd-image/pinch/pan/paging/rotation combinations; Android 8/9 mutations; SAF provider behavior; share recipients and interrupted public copies/exports. No hardware FPS or absolute-security claim is made.

On a device, check full/partial/denied access and revocation, odd image geometry, video controls/focus, multi-category operations without original deletion, source-removal acceptance/cancellation, all vault lock paths and biometric fallback, consented public restore, and airplane-mode operation. Check destinations after interrupted public operations; those operations are not journaled across abrupt process death. See README and SECURITY.md for deliberately unsupported features.
