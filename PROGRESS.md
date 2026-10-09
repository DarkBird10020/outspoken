# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: the teammate's design for four screens is in code, not yet wired to features. M0 is waiting for the phone test.

## M0. Skeleton

PRD pass test: the eye-open numbers move when you blink.
Status: not passed yet.

- [ ] **Project setup.** Kotlin, Jetpack Compose, one activity, Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml` holds every library version). minSdk 31 because the on-device speech recognizer (M3) needs Android 12.
- [ ] **Live eye-open numbers.** The front camera feeds ML Kit Face Detection. The screen shows left and right eye-open values (1 = open, 0 = shut), head angle and camera fps (frames per second). Why: blink detection in M1 is built on these numbers. Code: `eye/EyeReader.kt`, `eye/FrontCamera.kt`, `ui/EyeCheckScreen.kt`.
- [ ] **"Looking for you".** Shown when no face is found. PRD section 6 asks for it.
- [ ] **Model file check.** The screen shows whether a Gemma `.litertlm` file is on the phone, and where to push it. Why: M0 pass needs the model on the phone. Code: `setup/ModelFile.kt`.
- [ ] **Offline voice check.** The screen shows whether the text-to-speech engine has an English voice that works without internet. Why: PRD risk table says check this in M0. Code: `setup/OfflineVoice.kt`.
- [ ] **No INTERNET permission.** ML Kit tries to add it. The manifest removes it, and CI fails if it ever comes back.

How to test on the phone:
1. Install the APK (from CI, or `./gradlew installDebug`).
2. Allow the camera. Put the phone on a stand at arm's length.
3. Blink. Both eye numbers should drop near 0 and come back near 1.
4. Turn away. "Looking for you" should show.
5. Check "Model" and "Offline voice" lines. Push the model file if it says missing.

Unit tests:
- `FpsMeterTest`: frame rate maths (first frame, steady rate, smoothing, bad timestamps).
- `ModelFileTest`: finds the model file, ignores other files, picks the largest.

Extras beyond the PRD:
- Screen stays on, since the speaker cannot touch the phone.
- Camera fps on screen, to see if the camera is fast enough to catch a 0.3 s blink.
- CI builds a ready-to-install APK on every push.

## Design (from the teammate)

The four designed screens are built exactly from the design file, as stand-alone screens. Each one is wired up in the milestone that needs it. See them in Android Studio with the Preview pane.

- [ ] **Theme.** Urbanist font, colours, glass cards, glowing gradients and shadows from the design. Code: `ui/theme/`. Icons from the design are in `res/drawable/ic_*.xml`.
- [ ] **Conversation** (wired in M1): status pill, "Heard" card, four reply cards with the highlighted one, "More options" and "Yes / No". Code: `ui/ConversationScreen.kt`.
- [ ] **Practice round** (wired in M3): star targets, blinks caught, eye-open bar with your blink line, hold time and scan speed. Code: `ui/PracticeScreen.kt`.
- [ ] **Help alert** (wired in M4): alarm screen, last thing said, sound off, "I am here". Code: `ui/HelpAlertScreen.kt`.
- [ ] **Session stats** (wired in M4): reply time, model speed, temperature, blink accuracy, session length, sentences spoken. Code: `ui/StatsScreen.kt`.

Unit tests:
- `FormatTest`: how numbers on the stats and practice screens are written ("1.2 s", "24 tok/s", "04:12", "-" when not measured).

Design gaps, for the teammate to decide. Each uses the closest existing style for now:
- Highlight on "More options" and "Yes / No": pink glow, no "Blink" badge (the badge is taller than these cards).
- Face lost: same pill reading "Looking for you" with a grey dot.
- Nothing heard yet: the "Heard" card is hidden.
- Practice round: the "Steady" and "One more and you are ready" lines will come from the calibration logic in M3.
- Help alert: no design yet for after the sound is turned off.
- Help alert glass blur is left out; the background behind it is a smooth gradient, so it looks the same.

## CI checks

Run on every push and pull request (`.github/workflows/ci.yml`). The `main` ruleset requires all three to be green before a pull request can merge.

| Check | What it catches |
|---|---|
| Unit tests | Logic that broke (`./gradlew testDebugUnitTest`) |
| Android lint | Common Android mistakes (`./gradlew lintDebug`) |
| Build APK | Code that does not compile. Also fails if the APK asks for INTERNET. Uploads `outspoken-debug-apk` |

## Next

M1: blink detector, scanner, phrase bank, speech output. Starts after M0 passes.
