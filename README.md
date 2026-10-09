# Outspoken

Outspoken lets people who cannot move or speak talk out loud by blinking at a phone, with no internet.

The listener asks a question, an on-device Gemma model suggests four replies, and the speaker blinks to pick one. The phone speaks it. Everything runs on the phone and the app has no internet permission.

## Status

M1: blink at the highlighted card and the phone says it, using a built-in phrase bank. The model comes in M2.

## Setup

Every push builds a debug APK on GitHub Actions. Download `outspoken-debug-apk` from the latest green run and install it, or build it yourself with JDK 17+ and the Android SDK (API 36).

1. Build and install:
   ```
   ./gradlew installDebug
   ```
2. Open the app once so it creates its files folder, then push the Gemma model (a `.litertlm` file):
   ```
   adb push <model>.litertlm /sdcard/Android/data/com.outspoken/files/
   ```
3. Make sure the phone's text-to-speech engine has an offline English voice installed. The app shows "Offline voice: ready" when it does.

Stand the phone on a table or holder at arm's length, front camera facing the speaker.

## Checks

`./gradlew testDebugUnitTest` runs the unit tests. CI also runs Android lint, builds the APK and fails if the APK asks for the INTERNET permission.

## Open-source libraries

- [Kotlin](https://kotlinlang.org) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), Apache 2.0
- [Android Gradle Plugin](https://developer.android.com/build), Apache 2.0
- [AndroidX Core, Activity, Lifecycle](https://developer.android.com/jetpack/androidx), Apache 2.0
- [Jetpack Compose UI, UI tooling and Material 3](https://developer.android.com/jetpack/compose), Apache 2.0
- [CameraX](https://developer.android.com/media/camera/camerax), Apache 2.0
- [JUnit 4](https://junit.org/junit4/) (tests only), EPL 1.0
- [ML Kit Face Detection](https://developers.google.com/ml-kit/vision/face-detection) (bundled model), [ML Kit Terms](https://developers.google.com/ml-kit/terms)

## Fonts

- [Urbanist](https://github.com/coreyhu/Urbanist), SIL Open Font License 1.1. License text in `licenses/Urbanist-OFL.txt`.
