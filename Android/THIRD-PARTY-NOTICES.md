# Android third-party notices

Original application code is distributed under the repository's MIT license.
Third-party code and generated audio retain their own licenses and attribution.

The Android application uses the following open-source libraries. Their upstream
license is Apache License 2.0; its full text is bundled under
`assets/licenses/KOKORO-APACHE-2.0.txt` (the license text also applies to the
libraries below; the filename identifies the existing shared license resource).

| Component | Selected version | Source / copyright holder |
| --- | --- | --- |
| AndroidX Activity | 1.11.0 | [Android Open Source Project](https://android.googlesource.com/platform/frameworks/support/) |
| AndroidX Core | 1.17.0 | Android Open Source Project |
| AndroidX Lifecycle | 2.9.4 | Android Open Source Project |
| Jetpack Compose UI / Foundation / Runtime | 1.9.4 (BOM 2025.10.01) | Android Open Source Project |
| Compose Material 3 / Adaptive / icons | 1.4.0 / 1.2.0 / 1.7.8 | Android Open Source Project |
| AndroidX Window | 1.4.0 | Android Open Source Project |
| AndroidX Media3 | 1.8.0 | [Android Open Source Project](https://github.com/androidx/media) |
| Other transitive AndroidX components | See Gradle lockfile | Android Open Source Project |
| Kotlin standard library and annotations | 2.3.20 / see lockfile | [JetBrains](https://github.com/JetBrains/kotlin) and contributors |
| kotlinx.coroutines | 1.10.2 | [JetBrains](https://github.com/Kotlin/kotlinx.coroutines) and contributors |
| kotlinx.serialization | 1.9.0 | [JetBrains](https://github.com/Kotlin/kotlinx.serialization) and contributors |
| OkHttp | 4.12.0 | [Square](https://github.com/square/okhttp) and contributors |
| Okio | 3.6.0 | [Square](https://github.com/square/okio) and contributors |
| Guava / failureaccess | 33.3.1-android / 1.0.2 | [Google](https://github.com/google/guava) and contributors |
| JSpecify annotations | 1.0.0 | [JSpecify](https://github.com/jspecify/jspecify) contributors |

Gradle Wrapper 8.13 is generated from the official Gradle distribution and is
Apache-2.0 licensed. Build tools, JDK, Android SDK, test-only dependencies and
Kokoro model weights are not bundled in the application.

Kokoro-generated audio is reused without modification from the shared canonical
resources: Kokoro-82M v1.0, `af_heart` (US) and `bf_emma` (UK). Model revision,
voice-weight hashes, generation details, attribution and full license are in
the bundled `assets/licenses/AUDIO-SOURCES.md` and
`assets/licenses/KOKORO-APACHE-2.0.txt`. No Google Translate audio is included.

Nothing here implies endorsement by the upstream authors. Dependency changes
require reviewing the resolved lockfile and updating this inventory.
