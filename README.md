# Prism Gallery

A native, offline Android gallery in Kotlin and Jetpack Compose. Package `com.prism.gallery`. Android 8.0 / API 26 or later. No account, ads, analytics, backend, or Internet permission.

**Version 0.1.0 is a functional first release, not completion of every item in the original brief.** Known omissions and verification limits are below. Read the vault recovery warning before storing your only copy.

## Install an APK

Open [Android build runs](https://github.com/okthisisitright/prism-gallery/actions/workflows/android-build.yml), select a successful run for `main`, and download **Prism-Gallery-debug-apk** under Artifacts. Unzip and install `Prism-Gallery-debug.apk` on your Android device. GitHub may require sign-in to download Actions artifacts. Allow installation from your chosen file manager/browser when Android asks. A debug APK is signed with a development key and is not a Play Store release. CI can use a different debug key from a local build; switching signatures requires uninstalling, which **destroys vault access**. Never uninstall a vault containing the only copies of your photos.

Local output after building: `app/build/outputs/apk/debug/app-debug.apk`. The CI artifact exists only after its build job succeeds; no release-download URL is assumed.

## What is built

- Real MediaStore images/videos across accessible device volumes; full, selected-photo, denied and reselect flows on modern Android. Observer and on-resume rescans. No bundled fake library.
- Photos grid with 2–6 columns, chronological day/month headers, newest/oldest ordering, bounded thumbnail decoding and stable keys.
- Image viewer starts fit-contained and preserves geometry/EXIF orientation. Pinch, double-tap, bounded pan, horizontal paging, tap controls and downward dismissal when unzoomed. Media3 video playback, pause, seek, audio focus and lifecycle release.
- Device folders, smart collections, persistent local favorites, and unlimited Room-backed virtual categories. Multi-select, multi-category membership, duplicate prevention, quick creation, curated symbols, four accents, cover selection, rename, pin/hide, earlier/later ordering and safe category deletion.
- Explore searches filename, dates, folders, media type and assigned category names. Filters include videos, screenshots, downloads, favorites, recent and large files. This is metadata search, not image recognition.
- Public media sharing/open-with, real details, SAF directory copies, Android-confirmed trash (API 30+) / permanent single-file deletion (older versions), and distinct removal from a category.
- An image-only encrypted Private Vault: passphrase wrapping, optional cryptographically bound strong biometrics, verified imports, explicit source-removal requests, fit-contained private viewing, batch deletion, and consented public restore on API 29+. Screenshots blocked on vault screens where Android enforces FLAG_SECURE. Locks on background, screen-off, idle timeout and manual lock.
- System/light/dark themes, capability-aware glass modes with real Backdrop blur/refraction on supported devices, haptics, motion toggle, library/category preferences, rescan, permissions and local privacy/license explanations. Bundled OFL Manrope and original prism launcher art.

## Get started

1. Open Photos and choose media access. Android 14+ lets you select individual items. Use **Manage** or Settings → **Manage / reselect media access** to change that selection. If Android no longer offers a prompt, use the Photos permission-settings button or Android App Info. Photos and videos can have separate access.
2. Open Albums → **+ / New Category**, type a name and save. Categories are virtual references: no original bytes move. A photo can be in Family, Pets and Memes at once.
3. Long-press a tile to select it, tap others, then use the folder-plus action to add to a category or create one inline. In a category, the minus-circle action removes membership only. Category counts include only currently accessible items.
4. Use the category's three-dot control to rename, choose a cover/accent/symbol, pin/hide or change order. Show hidden categories from Settings. Deleting a category never deletes original media.
5. Viewer heart/category/share/trash/details actions affect real media. Copy chooses a destination through Android's folder picker. Android 8–10 deletion is permanent and single-item only; a failed or denied request reports failure.

## Vault: read before importing

Open Albums → **Private Vault**. Create and preserve a strong passphrase of at least eight characters. Optional strong biometrics can be enrolled under the unlocked vault's **Security** control. Do not depend solely on biometrics.

The key hierarchy uses a random AES-256 master key, PBKDF2-HMAC-SHA256 (600,000 iterations, unique salt) passphrase wrapping, and a device Keystore wrapping key. Each image has a fresh GCM nonce/tag. The filename and image bytes are encrypted in app-private no-backup storage. Private images are decoded in memory without Coil, plaintext disk caches, or an exported provider. See [SECURITY.md](docs/SECURITY.md) for the exact format, lifecycle and limits.

**Import does not automatically remove the public original.** Supported images up to 32 MiB are streamed into encrypted temporary storage, authenticated and byte-verified, atomically finalized and recorded. Only then choose **Keep originals** or **Remove originals…**. The latter opens Android's permanent deletion request. A canceled, failed or unknown result keeps a warning: a plaintext original may still exist. Trash is not private removal. Android-confirmed deletion cannot rule out copies elsewhere or cloud backups made by other apps.

Inside the vault, long-press for batch restore/delete. **Restore public copy** warns before placing an unencrypted copy in `Pictures/Prism Restored`, keeping the encrypted copy. Those restored images are no longer private. Direct private sharing and vault video import/playback are disabled. Ordinary public videos play normally.

**There is no recovery service or portable backup.** Forgotten credentials without working biometrics, clearing app data, uninstall/reinstall, device loss, or lost/invalidated device Keystore keys can make the vault permanently unrecoverable. Biometric enrollment/lock-screen changes can invalidate the biometric key; passphrase fallback works if the separate device wrapping key survives. Never promise recovery without the required secrets and device key.

## Build and checks

Use JDK **17**, Android SDK command-line tools, platform `platforms;android-37.0` and Build Tools 36.0.0. The checked-in Gradle wrapper is version 9.6.0 with a pinned distribution SHA-256. AGP 9.4.1 uses built-in Kotlin; the Compose compiler plugin is 2.4.21.

```sh
sdkmanager 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
# Set ANDROID_HOME or create local.properties with sdk.dir for your machine.
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# With an API 26+ emulator/device (CI uses API 35):
./gradlew :app:connectedDebugAndroidTest
```

No signing credentials are required for debug builds. Local checks and actual results are recorded in [VALIDATION.md](docs/VALIDATION.md). Hardware functions must still be checked on your device; no Vivo, physical biometric sensor or frame-rate claim is implied by an emulator run.

[CI](.github/workflows/android-build.yml) runs JVM tests, Android lint, debug packaging, test APK compilation and an API 35 emulator instrumentation job on push/PR/manual dispatch. Reports are uploaded separately. The instrumentation suite covers Room category persistence/multi-membership and a real Keystore-backed vault import/unlock/tamper/relock path. JVM tests cover geometry, identity, filters, KDF/AEAD and the import transaction used by production.

## Signed releases

Keep a release keystore outside the repository, backed up securely. Use Android Studio's **Generate Signed App Bundle / APK** with your own persistent key, or add a separate release-only Gradle signing config fed by CI secret variables/files. Never put passwords/keystores into source, command arguments echoed in logs, or artifacts. A release workflow should decode a secret keystore into runner temporary storage, read passwords from the environment through Gradle providers, run `assembleRelease`, upload only the signed APK, and delete temporary signing material. Current CI deliberately builds only a debug APK; release signing is not configured. Preserve one signing identity for updates to avoid vault-destroying reinstalls.

## Deliberate limits and remaining work

- Vault is **images only, at most 32 MiB each**; no private video, direct sharing, portable backup, credential change, private category/rename, or zoom/paging in its private viewer. Public restore requires Android 10+. It has not had an independent security audit.
- No public-file move/rename, semantic recognition, sticky headers, thumbnail shared-element transition, drag reorder, per-category sort preferences or live frame-time-based glass adaptation. Cover picker shows up to the first 24 accessible members.
- Media metadata is queried in cancellable batches but retained as a complete in-memory set; this is not Paging/Room-backed library pagination. Very large libraries and low-end hardware require profiling. No measured 60/90/120 fps claim.
- External metadata edits/database rebuilds can orphan category references; inaccessible memberships are retained and excluded from counts rather than silently reassigned. Older Android cannot fully distinguish a reused ID with identical metadata.
- Navigation/grid preferences survive normal use; active viewers/dialogs close after process recreation/resume. System permission/deletion and SAF flows remain OS-controlled, and cancellation never reports success. Imports interrupted by process death retain originals and recover verified finalized copies.
- Hardware/OEM behaviors, biometric key invalidation, low storage, unexpected shutdown and all gesture combinations need a physical-device acceptance pass. See the validation matrix, which separates executed tests from unverified cases.

Architecture: [ARCHITECTURE.md](docs/ARCHITECTURE.md). Security: [SECURITY.md](docs/SECURITY.md). Current API/design sources: [RESEARCH.md](docs/RESEARCH.md). Credits/licenses: [THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md) and [LICENSE](LICENSE).
