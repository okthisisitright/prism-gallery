# Third-party notices

Prism Gallery source: Apache License 2.0 (root LICENSE).

Bundled Manrope font: Mikhail Sharanda / Manrope contributors, SIL Open Font License 1.1. Full text: [Manrope-OFL.txt](Manrope-OFL.txt). [Source](https://github.com/google/fonts/tree/main/ofl/manrope). No Apple San Francisco font is included.

Direct libraries and their published licenses:

| Library | Authors | License |
| --- | --- | --- |
| AndroidX / Compose / Material / Material Icons / Room / Lifecycle / Navigation / DataStore / Biometric / ExifInterface | Android Open Source Project / Google | Apache-2.0 |
| Media3 / ExoPlayer | Android Open Source Project / Google | Apache-2.0 |
| Kotlin / Kotlin Coroutines | JetBrains / contributors | Apache-2.0 |
| Coil | Coil contributors | Apache-2.0 |
| Backdrop 2.0.1 / Shapes 1.2.1 | Kyant / contributors | Apache-2.0 |
| JUnit (test only) | JUnit contributors | EPL-1.0 |

Apache-2.0 text is in the root LICENSE. Dependency source and artifact metadata retain their own notices. Gradle resolves transitive components from Google Maven and Maven Central. See app/build.gradle.kts for exact direct versions and `./gradlew :app:dependencies` for the resolved graph. Design inspiration sources are cited in RESEARCH.md; no web components were redistributed.
