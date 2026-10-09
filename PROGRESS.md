# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: the highlight moves with the eyes (look down / up), and Gemma on the phone now writes the next four replies after each sentence (M2). The model file is picked inside the app. Waiting for the next phone test.

## Phone test history

| Date | What was tested | Result | What changed |
|---|---|---|---|
| 2026-10-09 | M0 to M2 | Not working properly, picks felt random | Blink rules rebuilt, run logs added |
| 2026-10-09 | M1, M2 | The highlight moved on its own; the owner wants to move it with the eyes. The model was missing | Eye movement control kept as the way to move (look down / up, blink to say). Gemma model replies added on top. "Choose model file" in the app |

## Eyes move the highlight (owner decision)

The owner asked for the highlight to follow the eyes instead of moving on a timer. This replaces PRD F2 timed scanning as the default; the timer stays as a switch.

- [ ] **Look down = next card, look up = previous, blink = say it.** One look is one step; the eyes come back to rest before the next. Looks are measured from where the eyes rest, which slowly follows posture, because the phone sits below eye level. Sideways looks do nothing. Shut eyes never count as looking down. Code: `scan/GazeStepper.kt`, `conversation/ConversationController.kt`.
- [ ] **Eye reading switched from ML Kit to MediaPipe Face Landmarker.** Phone logs showed ML Kit could not do this: its dots trace the eyelids, not the eyeball, so they never moved with the eyes; one eye often read 0.01 while the other read 0.3; and it lost the face for single frames 16 times in a minute. MediaPipe gives 478 face points including both irises, a blink score per eye and gaze scores (look up, down, in, out). Model: `assets/face_landmarker.task` (Google, Apache 2.0). Code: `eye/EyeReader.kt`, `eye/FaceMesh.kt`.
- [ ] **Face lost only after 0.4 s.** Single dropped frames no longer flip "Eyes found" off and on, and a face dropout during a blink no longer cancels it. Code: `blink/BlinkDetector.kt`.
- [ ] **Tuning on the phone.** Eye check screen sliders: shut line, open line, shortest and longest blink, look distance, look hold, eyes or timer. Saved on the phone, reset button. No rebuild needed to tune. Code: `setup/Tuning.kt`, `ui/EyeCheckScreen.kt`.
- [ ] **Cleaner debug dots.** Small dots on the eyelids, a ring on each iris (green open, yellow unsure, red shut), and a gaze box showing where the eyes look.

How to test on the phone:
1. Main screen: the highlight stays on the first card until you move your eyes.
2. Look down at the bottom of the phone for a moment, then back: the highlight moves down one card. Look up and back: it moves up one.
3. On the card you want, shut your eyes for about half a second: the phone says it.
4. If looks are missed or too easy, open the eye check screen and move "Look distance" and "Look hold". If blinks are missed, watch the graph and move the shut line.

Unit tests:
- `GazeStepperTest`: rest does nothing, down and up step once, sideways and short glances do nothing, closing eyes is not a look, rest follows posture.
- `EyeModeTest`: highlight waits for the eyes, down moves, up wraps, blink says the card the eyes moved to, back to top after speaking.
- `FaceMeshTest`: head turn from the nose position, gaze direction, point lists.
- `BlinkDetectorTest`: short face dropout keeps the blink and is not face lost. The tests "losing the face cancels a blink" and "turning away counts as face lost" now hold the face away for 0.5 s, since a shorter gap is ignored on purpose.

## Eye detection fixes and debug view (earlier, ML Kit)

The highlight is meant to move on its own, one card every 1.2 s (PRD F2, "scanning"). The app does not follow where you look; it waits for a deliberate blink while the right card is lit. These changes make the blink part reliable and visible.

- [ ] **Highlight flicker fixed.** Camera frames are stamped about 30 ms before detection finishes, so they arrived behind the screen timer and pulled the highlight back one card at every step (seen in the phone log: More options → "I need the toilet" → More options within 70 ms). The highlight now only moves forward on the newest time. Code: `conversation/ConversationController.kt`, test `a late camera frame does not move the highlight back`.
- [ ] **Head turn limit 25° → 18°.** Google's ML Kit docs say eye-open values only work for faces turned at most 18° left or right. Between 18° and 25° the values were noise and could fake blinks. Head tilt (looking down at the phone) keeps its 25° limit, since ML Kit sets none. Code: `blink/BlinkDetector.kt`.
- [ ] **Sharper camera feed, 640x480 → 1280x960.** ML Kit needs the face at least 100 px wide for eye-open values (200 px for the eye outline). At arm's length the face was only about 120 px. Code: `eye/FrontCamera.kt`. The log line `detect ... ms avg, image ..., face width ... px` every 5 s shows the real numbers.
- [ ] **Debug dots.** The eye button opens the check screen: 16 dots on each eye outline over the camera (green open, yellow unsure, red shut), a 5-second graph of both eyes (blue left, orange right) with the shut and open lines dashed, head turn and tilt marked ok or too far, and the last blink decisions written out. Code: `ui/EyeCheckScreen.kt`, `eye/EyeReader.kt`.
- [ ] **Eye shape measure.** From the outline: eye height over width, the eye aspect ratio from Soukupová and Čech (2016). Shown on the check screen and logged next to ML Kit's value, so the two can be compared before choosing which one drives blinks. Code: `eye/EyeShape.kt`.

How to test on the phone:
1. Tap the eye button. Dots should sit on both eyes and follow them.
2. Blink slowly: dots turn red, both graph lines drop below the red dashed line, and "blink ... ms" appears at the bottom.
3. Blink normally: "ignored ... ms, shorter than 300 ms".
4. Turn your head: "turn ... too far" and "face lost: head turned".

Unit tests:
- `EyeShapeTest`: open and shut outlines, size does not matter, too few points.
- `EyeSummaryTest`: the shape values in the log line.

## Logs

Every run writes a log, so a failed phone test can be explained without guessing. Code: `log/`.

- [ ] **What is logged.** One line per second with camera fps, face found, eye-open values and the lowest value in that second. Every blink with its length and why it was ignored (too short, too long, face lost). Face lost with the reason (no face, head turned). Each highlight, pick, tap and sentence. Speech start, finish and errors. Camera start, permission, model and voice checks, screen changes.
- [ ] **Crashes.** The full error is written to the log before the app closes.
- [ ] **Where.** One file per app start in `Android/data/com.outspoken/files/logs/` (last 10 kept), and logcat with the tag `Outspoken`. Nothing leaves the phone.
- [ ] **Same key for every build.** CI signs the APK with one shared debug key (repo secret `DEBUG_KEYSTORE_B64`). Without it each build has a new key, the phone refuses the update, and uninstalling first deletes the logs.

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

## M2. Model replies

PRD pass test: after choosing "I am in pain", the next four cards are relevant (for example where it hurts), and they appear within 2 seconds.
Status: not passed yet.

- [ ] **Model on the phone** (F4). Gemma runs through LiteRT-LM, loaded once when the app starts. Tries the GPU, falls back to the CPU. Code: `suggest/LiteRtLmModel.kt`, `suggest/OnDeviceModel.kt`.
- [ ] **Model file picked in the app.** "Choose model file" on the eye check screen copies a downloaded `.litertlm` file into the app's folder and loads it. No cable or adb needed. Code: `suggest/ModelImporter.kt`.
- [ ] **Suggestion engine** (F4). Sends the last few lines of the conversation and the time of day, asks for exactly four replies as a JSON list (first person, at most 8 words). The `SuggestionEngine` interface is the swap point if another runtime is needed. Code: `suggest/Prompt.kt`, `suggest/ModelSuggestionEngine.kt`.
- [ ] **Reply checking.** Exactly four, different, short replies, or the answer is rejected. A bad answer gets one retry, then the phrase bank is used. Code: `suggest/ReplyParser.kt`.
- [ ] **Instant fallback** (F5). The phrase bank shows at once after each sentence while the model thinks, and stays if the model fails. "More options" pages from the model's replies into the phrase bank.
- [ ] **No surprise card changes.** After speaking, the highlight waits up to 2.5 s for the new replies, so the cards do not change under the person's eyes; then it carries on with the phrase bank. Only the answer to the latest request is used.
- [ ] **Speech cannot freeze the board.** If the speech engine never says it finished, the board carries on after 10 s.
- [ ] **Measured.** The eye check screen shows the model state (loading, ready on GPU or CPU, failed) and the last reply time with tokens per second. Reply times are also in the logs.

How to test on the phone:
1. On the phone's browser, open huggingface.co/litert-community/Gemma3-1B-IT, sign in, accept the Gemma licence, and download the `.litertlm` file (about 0.5 GB).
2. Open the eye check screen (eye button), tap "Choose model file" and pick the downloaded file. It copies, then loads: wait for "Model: ... ready on GPU" (or CPU). Back.
3. Look down to "I am in pain" and blink. The phrase bank shows while it thinks, then four new cards should be about the pain.
4. Open the eye check screen again and read "Last replies". It should say under 2 s from the model.
5. If it says phrase bank, the model failed or answered badly twice. Send the log.

Unit tests:
- `ReplyParserTest`: good lists, code fences, trailing comma, escapes, wrong count, long, empty or repeated replies, junk.
- `PromptTest`: asks for four short replies as JSON, time of day, who said what, only recent lines.
- `ModelSuggestionEngineTest`: good answer, one retry, fallback after two bad answers, fallback on error, timing.
- `ConversationControllerTest` and `BoardTest`: new replies after speaking, model page first, old answers ignored, the wait, the speech time-out.

Extras beyond the PRD:
- The model starts writing the next replies while the phone is still speaking, which saves time.
- Output is capped at 96 tokens so a rambling answer cannot hold up the reply time.

To tune on the phone (PRD: pick the largest model that meets 2 s): try Gemma3-1B-IT first, then a larger Gemma if it stays under 2 s.

## M3. Listening and calibration (not started)

- Calibration and the practice round were built once on the old ML Kit reader and taken out when eye reading moved to MediaPipe. They will be rebuilt on the new reader, alongside the tuning sliders.

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
- No design for the "Choose model file" button; it is on the plain eye check screen.
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
