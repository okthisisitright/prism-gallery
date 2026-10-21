# API and design references

Reviewed 2026-10-21. Versions are pinned, rather than floating. Repository Maven metadata and actual compilation are the compatibility checks; no prerelease libraries are intentionally selected.

- AGP 9.4.1, Gradle 9.6.0, built-in Kotlin plus Compose compiler plugin 2.4.21, KSP 2.3.12. [AGP compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- Compose BOM 2026.09.00; Backdrop 2.0.1 (published with Kotlin 2.4.10 / Compose 1.12.0). [Maintained source/API examples](https://github.com/Kyant0/AndroidLiquidGlass/tree/kmp), [documentation](https://kyant.gitbook.io/backdrop). Android-native drawBackdrop/layerBackdrop only.
- Room 2.8.5, Coil 3.6.3, Media3 1.11.1, Activity 1.13.0, Lifecycle 2.11.0, Navigation 2.9.5, DataStore 1.2.0, Biometric 1.1.0 (stable baseline rather than 1.4 alpha). [AndroidX release notes](https://developer.android.com/jetpack/androidx/versions), [Coil](https://coil-kt.github.io/coil/), [Media3](https://developer.android.com/media/media3).
- [Partial visual media access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access): evaluate on resume, declare selected access, support reselection, don't assume permissions persist.
- [MediaStore/scoped storage](https://developer.android.com/training/data-storage/shared/media): per-volume URIs, ContentResolver, authorized mutations and system delete/trash requests.
- [Android cryptography](https://developer.android.com/privacy-and-security/cryptography), [Keystore](https://developer.android.com/privacy-and-security/keystore), [biometric crypto](https://developer.android.com/identity/sign-in/biometric-auth): platform authenticated encryption and per-use biometric keys.
- [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider) reviewed. A private provider was deliberately not exposed; public media shares use existing narrow MediaStore URIs. Vault exports create an explicitly public copy after consent.
- [Impeccable](https://github.com/pbakaus/impeccable) and [21st.dev](https://21st.dev/) reviewed for visual hierarchy, restraint, typography and glass inspiration. No React/CSS library or code from either is installed or embedded. Native components are original to this project.

Target/compile API 37, SDK package `platforms;android-37.0`; minimum API 26. Hardware rendering falls back below API 33. Test API 35 is used independently of compile SDK. Updates should recheck dependency metadata and run the complete build/test/lint set.
