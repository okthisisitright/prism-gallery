# Verification record

Run date: 2026-10-21. This record reports executed checks separately from intended acceptance checks.

## Executed in the workspace

- Empty target repository cloned and inspected; no existing implementation or local AGENTS.md was present.
- Android SDK platform 37.0 / Build Tools 36.0.0, full Temurin JDK 17 and Gradle 9.6.0 installed for the build.
- `:app:assembleDebug`: **passed**, produced the installable development-signed `app/build/outputs/apk/debug/app-debug.apk` (approximately 25 MiB).
- `:app:assembleDebugAndroidTest`: **passed**, compiled/packaged instrumentation tests.
- `:app:testDebugUnitTest`: **25 passed, 0 failures, 0 errors**. 13 media/geometry/identity/filter tests and 12 vault cipher/import-transaction tests. Production uses the tested `VerifiedImport` transaction helper.
- `:app:lintDebug`: **passed with 0 errors and 21 warnings** in the initial complete run. Warnings are dependency update notices, URI/KTX style suggestions, deliberate synchronous persistence of retry throttling on IO, resource-folder convention and release resource-shrinking advice. No lint baseline or blanket suppression was added.
- App and instrumentation sources compile against the pinned stable dependency graph, including Backdrop 2.0.1 APIs.
- Merged manifest inspected: **no INTERNET permission**; backup disabled. The transitive ACCESS_NETWORK_STATE permission was explicitly removed in the final source.

Small final fixes (transactional category creation, legacy write permission request and forced-theme system bars) are followed by another complete build/test/lint/package run. Its results and runtime checks are updated as verification completes.

## Device checks

An API 35 x86_64 Google APIs emulator was provisioned in the workspace with software CPU emulation because `/dev/kvm` is unavailable. Device boot, instrumentation execution and UI screenshots are pending. Compiling instrumentation tests does not establish that they ran. The workflow additionally runs the instrumentation suite on a KVM-backed API 35 GitHub runner; consult the actual run result.

Not yet verified on physical hardware: Vivo/Funtouch OS, biometric hardware and enrollment invalidation, device lock-screen removal, low-storage/power-loss behavior, large-library frame times, all EXIF/codec combinations, Android 8/9 mutation behavior, Android 14/15 partial-access/revocation UX, public SAF providers, share recipient behavior and the full gesture acceptance matrix. No hardware FPS or absolute-security claim is made.

## Manual acceptance checklist for a device

- Grant image/video full access; then partial access; reselect; revoke while backgrounded; confirm stale inaccessible media is removed and no operation reports success on failure.
- Verify panorama, tall, square, EXIF-rotated and older encodings fit inside the viewer without stretching/cropping; test pinch/pan/double-tap/page/downward dismissal.
- Play, pause and seek supported video; rotate and background; confirm focus and player release.
- Create/rename/reorder/pin/hide/change cover/delete categories. Add one image to two categories, remove from one, restart. Confirm originals remain and inaccessible members are not counted or reassociated.
- Verify a vault import with Keep original, canceled source deletion, approved source deletion, interrupted import and insufficient storage. Ensure public-original warnings are accurate.
- Verify passphrase fallback, wrong credentials/throttling, biometric cancellation/enrollment invalidation, background, screen-off, idle lock, manual lock and screenshot blocking.
- Restore only after explicit consent; check public destination and retained encrypted copy. Delete only the vault copy after confirmation.
- Confirm no vault material appears in ordinary Photos/Albums/Explore, public thumbnails or backups, and that airplane-mode operations work.
