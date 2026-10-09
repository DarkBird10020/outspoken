# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: run logs added, so phone tests can be read back and bugs found from them. M1 is waiting for the phone test (M0 and M1 together).

## Logs

Every run writes a log, so a failed phone test can be explained without guessing. Code: `log/`.

- [ ] **What is logged.** One line per second with camera fps, face found, eye-open values and the lowest value in that second. Every blink with its length and why it was ignored (too short, too long, face lost). Face lost with the reason (no face, head turned). Each highlight, pick, tap and sentence. Speech start, finish and errors. Camera start, permission, model and voice checks, screen changes.
- [ ] **Crashes.** The full error is written to the log before the app closes.
- [ ] **Where.** One file per app start in `Android/data/com.outspoken/files/logs/` (last 10 kept), and logcat with the tag `Outspoken`. Nothing leaves the phone.

How to read them (phone on USB):
- Live: `adb logcat -s Outspoken`
- Files: `adb pull /sdcard/Android/data/com.outspoken/files/logs`

Unit tests:
- `EyeSummaryTest`: the once-per-second eye line.
- `LogLinesTest`: blink picked and said, fast blink and long closure explained, face lost reasons, taps and card changes.

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

## M1. Blink to speech with fixed phrases

PRD pass test: say "I need water" by blinking, ten times in a row.
Status: not passed yet. This test also covers M0, since it needs the live eye values.

- [ ] **Blink detector** (F1). Both eyes shut for 0.3 to 0.9 s is a blink. Fast normal blinks, long closures and winks are ignored. Uses two lines with a gap between them (shut below 0.3, open above 0.5) so noise does not flicker. Pure Kotlin. Code: `blink/BlinkDetector.kt`.
- [ ] **Scanner** (F2). The highlight moves every 1.2 s over the four replies, "More options" and "Yes / No". It stops while the face is lost and shows "Looking for you". Code: `scan/Scanner.kt`.
- [ ] **Phrase bank** (F5). I need water, I am in pain, Please call the nurse, I need the toilet, then (More options) I am too hot, I am too cold, Thank you. "Yes / No" shows Yes and No. Code: `conversation/Board.kt`.
- [ ] **Speech** (F3). Says the chosen sentence with an offline English voice, Indian English if installed. Code: `speech/Speaker.kt`.
- [ ] **Conversation screen live.** The designed screen now runs the loop: highlight, blink, speak, start again from the first card. Code: `conversation/ConversationController.kt`, `MainActivity.kt`.

How to test on the phone:
1. Install the APK, allow the camera, put the phone on a stand at arm's length.
2. The pill says "Eyes found" and the highlight moves card to card.
3. When "I need water" is highlighted, shut your eyes for about half a second. The phone says "I need water".
4. Do it ten times in a row. Count misses and wrong cards.
5. Also try: normal blinking (nothing should happen), a wink (nothing), turning away (pill says "Looking for you", highlight stops).
6. The eye button at the top opens the M0 screen with live numbers. Back returns.

Unit tests:
- `BlinkDetectorTest`: blink window, fast blink, long closure, wink, face lost, head turned, the gap between the two lines.
- `ScannerTest`: timing, wrap round, pause, restart, skipping cards that are not shown.
- `BoardTest`: pages, More options, Yes / No, PRD phrase list.
- `ConversationControllerTest`: the full loop, including picking the card that was lit when the eyes shut.

Extras beyond the PRD:
- The card chosen is the one lit when the eyes shut, not when they open, since the highlight can move during a blink.
- Tapping a card also speaks it, so the person at the bedside can test without blinking.
- Blinks are ignored while the phone is speaking, so one blink cannot pick twice.

For later milestones:
- Scan speed is fixed at 1.2 s in code. The design shows it but has no control to change it yet (design gap).
- The eye button opens the M0 number screen until the practice round (M3). The stats button does nothing until M4.

## Design (from the teammate)

The four designed screens are built exactly from the design file, as stand-alone screens. Each one is wired up in the milestone that needs it. See them in Android Studio with the Preview pane.

- [ ] **Theme.** Urbanist font, colours, glass cards, glowing gradients and shadows from the design. Code: `ui/theme/`. Icons from the design are in `res/drawable/ic_*.xml`.
- [ ] **Conversation** (live since M1): status pill, "Heard" card, four reply cards with the highlighted one, "More options" and "Yes / No". Code: `ui/ConversationScreen.kt`.
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
- No control to change scan speed (the practice round only shows it).
- While the phone is speaking, no card is lit.

## CI checks

Run on every push and pull request (`.github/workflows/ci.yml`). The `main` ruleset requires all three to be green before a pull request can merge.

| Check | What it catches |
|---|---|
| Unit tests | Logic that broke (`./gradlew testDebugUnitTest`) |
| Android lint | Common Android mistakes (`./gradlew lintDebug`) |
| Build APK | Code that does not compile. Also fails if the APK asks for INTERNET. Uploads `outspoken-debug-apk` |

## Next

M2: Gemma writes the four replies, with the phrase bank as fallback. Starts after M1 passes on the phone.
