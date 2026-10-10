# Outspoken

Outspoken lets people who cannot move or speak talk out loud by blinking at a phone, with no internet.

The listener asks a question, an on-device Gemma model suggests four replies, and the speaker blinks to pick one. The phone speaks it. Everything runs on the phone and the app has no internet permission.

## Measured on the phone

iQOO (vivo I2501, Android 16), from the app's own run logs of 2026-10-10:
- Gemma 4 E2B through LiteRT-LM on the GPU with multi-token prediction (MTP): four replies in 0.6 to 1.4 s, 37 to 93 tokens per second. Without MTP: 1.6 to 1.9 s, 26 to 29 tokens per second.
- Speech recognition and speech on the phone, offline.
- Eye tracking at about 25 frames per second.
- One run (05:45 to 05:52, build 61abde1): 42 model replies, median 0.9 s (0.67 to 1.8 s), median 60 tokens per second; 35 cards chosen by closing the eyes for 0.45 to 1.4 s; the 10 shorter blinks (up to 0.37 s) were ignored.

## Status

Working on M1 to M4 together. Look up above the phone for the next card and close your eyes for about half a second to say the lit card, or switch to blink only mode, where the highlight moves on a timer. The phone listens to the visitor's question, and Gemma on the phone writes four replies that fit; the built-in phrase bank shows while it thinks and whenever it fails. A practice round, a help alarm, a stats screen and a live transcript are built.

## Setup

Every push builds a debug APK on GitHub Actions. Download `outspoken-debug-apk` from the latest green run and install it, or build it yourself with JDK 17+ and the Android SDK (API 36).

1. Build and install:
   ```
   ./gradlew installDebug
   ```
2. Get a Gemma model: on the eye check page (eye button, top right), tap Download next to Gemma 4 E2B (faster, the default) or E4B (more accurate, slower). The phone's browser downloads it and the app loads it by itself; the app never goes online. A `.litertlm` file already on the phone can be picked with "Choose model file". The model is not in this repo; it comes under the Gemma terms of use.
3. Make sure the phone's text-to-speech engine has an offline English voice installed. The app shows "Offline voice: ready" when it does.

Stand the phone on a table or holder at arm's length, front camera facing the speaker.

To run exactly the same app on two phones, install the CI APK (`outspoken-debug-apk`) on both: it is signed with one shared key. To build on a laptop with that same key, save the team's shared debug keystore (ask the owner; it is never in the repo) as `~/.android/outspoken-debug.keystore`. Then every APK installs over the last one and the phone keeps its settings and models. Models stay in Downloads, so even an uninstall does not delete them. The eye check page shows "Build:" with the commit, so the two can be compared.

## Using it

1. At every start the phone talks the speaker through calibration (about 30 s): look at the screen, look up, look down, close the eyes. It sets the look and blink lines from this person's eyes.
2. On the main page, the visitor asks a question out loud. It shows on the "Heard" card and four replies appear.
3. The speaker moves the highlight and closes both eyes for about half a second to say the lit card.
4. Holding the eyes shut for 2 s (a beep), then opening them and closing them again within 5 s, as a blink or a longer close, sounds the help alarm.

Two ways to move the highlight, switched on the eye check page:
- **Eyes** (default): look up, above the phone, for the next card; it wraps round. Looking down and winks can be switched on too.
- **Blink only**: the highlight moves on a timer (scan speed slider); blink when the right card lights.

Buttons on the main page, top right:
- **Star**: practice round. Catch three stars by blinking; it sets the blink length and gives the blink accuracy on the stats screen.
- **Eye**: eye check page. Live graph of both eyes with the shut and open lines, OPEN / SHUT in large letters, the gaze box, the last decisions, the mode switches and sliders, "Calibrate my eyes", "Choose model file", "Share logs" and "Save logs". Sized to be read from about two metres for a demo.
- **Transcript**: the conversation in large type, for a laptop through Office Kit.
- **Stats**: reply time, model speed, blink accuracy, session length, sentences spoken.

Run logs stay on the phone in `Android/data/com.outspoken/files/logs/`. Tap "Share logs" on the eye check page to send them to another app, or "Save logs" to keep a copy; on a laptop, `tools/phone-logs.ps1` copies them over USB or wireless debugging. Logs are never committed: they hold what people said.

## Checks

`./gradlew testDebugUnitTest` runs the unit tests. CI also runs Android lint, builds the APK and fails if the APK asks for the INTERNET permission.

## Open-source libraries

- [Kotlin](https://kotlinlang.org) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), Apache 2.0
- [Android Gradle Plugin](https://developer.android.com/build), Apache 2.0
- [AndroidX Core, Activity, Lifecycle](https://developer.android.com/jetpack/androidx), Apache 2.0
- [Jetpack Compose UI, UI tooling and Material 3](https://developer.android.com/jetpack/compose), Apache 2.0
- [CameraX](https://developer.android.com/media/camera/camerax), Apache 2.0
- [JUnit 4](https://junit.org/junit4/) (tests only), EPL 1.0
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), runs the Gemma model on the phone, Apache 2.0. Brings in [Gson](https://github.com/google/gson), Apache 2.0
- [MediaPipe Tasks Vision](https://github.com/google-ai-edge/mediapipe), Apache 2.0, with the [Face Landmarker model](https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker) (`app/src/main/assets/face_landmarker.task`), Apache 2.0

## Fonts

- [Urbanist](https://github.com/coreyhu/Urbanist), SIL Open Font License 1.1. License text in `licenses/Urbanist-OFL.txt`.
