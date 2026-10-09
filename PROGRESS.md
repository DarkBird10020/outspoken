# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: the highlight no longer moves by itself. A short blink moves to the next card, a long blink (over 1 s, a tick sounds) chooses it. The model file can now be picked inside the app. Waiting for the next phone test.

## Phone test history

| Date | What was tested | Result | What changed |
|---|---|---|---|
| 2026-10-09 | M0 to M2 | Not working properly, picks felt random | Hold time 0.5 s, lines set from each person's own eyes, calibration and practice round first, noise tolerance, scanning waits for new replies, run logs |
| 2026-10-09 | M1, M2 | The highlight moved on its own; owner wants to move it themselves. Model not working | Blink stepping instead of auto scanning (owner's choice): short blink = next card, long blink = choose, with a tick while still shut. "Choose model file" in the app. The stats button opens the check screen |

## Eye detection fixes and debug view

The app does not follow where you look; it reads blinks. These changes make the blink part reliable and visible.

- [ ] **Highlight flicker fixed.** Camera frames are stamped about 30 ms before detection finishes, so they arrived behind the screen timer and pulled the highlight back one card at every step (seen in the phone log: More options → "I need the toilet" → More options within 70 ms). The highlight now only moves on blinks, and the controller's clock only moves forward. Code: `conversation/ConversationController.kt`.
- [ ] **Head turn limit 25° → 18°.** Google's ML Kit docs say eye-open values only work for faces turned at most 18° left or right. Between 18° and 25° the values were noise and could fake blinks. Head tilt (looking down at the phone) is allowed up to 35°, since ML Kit sets none. Code: `blink/BlinkDetector.kt`.
- [ ] **Sharper camera feed, 640x480 → 1280x960.** ML Kit needs the face at least 100 px wide for eye-open values (200 px for the eye outline). At arm's length the face was only about 120 px. Code: `eye/FrontCamera.kt`. The log line `detect ... ms avg, image ..., face width ... px` every 5 s shows the real numbers.
- [ ] **Debug dots.** The check screen (long-press the status pill) shows: 16 dots on each eye outline over the camera (green open, yellow unsure, red shut), a 5-second graph of both eyes (blue left, orange right) with the shut and open lines dashed, head turn and tilt marked ok or too far, and the last blink decisions written out. Code: `ui/EyeCheckScreen.kt`, `eye/EyeReader.kt`.
- [ ] **Eye shape measure.** From the outline: eye height over width, the eye aspect ratio from Soukupová and Čech (2016). Shown on the check screen and logged next to ML Kit's value, so the two can be compared before choosing which one drives blinks. Code: `eye/EyeShape.kt`.

How to test on the phone:
1. Long-press the status pill (the eye button now opens the practice round). Dots should sit on both eyes and follow them.
2. Blink slowly: dots turn red, both graph lines drop below the red dashed line, and "blink ... ms" appears at the bottom.
3. Blink normally: "ignored ... ms, shorter than 500 ms" (or the hold time calibration set).
4. Turn your head: "turn ... too far" and "face lost: head turned".

Unit tests:
- `EyeShapeTest`: open and shut outlines, size does not matter, too few points.
- `EyeSummaryTest`: the shape values in the log line.

## Logs

Every run writes a log, so a failed phone test can be explained without guessing. Code: `log/`.

- [ ] **What is logged.** One line per second with camera fps, face found, eye-open values and the lowest value in that second. Every blink with its length and why it was ignored (too short, too long, face lost), and the blink line it was measured against. Calibration and practice results, model loading, reply times and fallbacks, and waits for new replies. Face lost with the reason (no face, head turned). Each highlight, pick, tap and sentence. Speech start, finish and errors. Camera start, permission, model and voice checks, screen changes.
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

- [ ] **Blink detector** (F1). Both eyes shut for 0.5 to 1.5 s is a blink (calibration can lower the start to 0.4 s). Fast normal blinks, long closures and winks are ignored. The shut and open lines are set from the person's own open-eye level, so droopy lids or a low phone do not count as shut. One noisy frame cannot end a blink, and a face missing for under half a second is ignored. Pure Kotlin. Code: `blink/BlinkDetector.kt`.
- [ ] **Blink stepping** (F2, changed, see below). The highlight stays put. A short blink (about half a second) moves it to the next card: the four replies, then "More options", then "Yes / No", then round again. A long blink (over 1 second) chooses the lit card; a tick sounds at 1 second while the eyes are still shut, so the person knows letting go now will choose. When the face is lost the pill says "Looking for you" and blinks are not read. Code: `conversation/ConversationController.kt`.
- [ ] **Phrase bank** (F5). I need water, I am in pain, Please call the nurse, I need the toilet, then (More options) I am too hot, I am too cold, Thank you. "Yes / No" shows Yes and No. Code: `conversation/Board.kt`.
- [ ] **Speech** (F3). Says the chosen sentence with an offline English voice, Indian English if installed. Code: `speech/Speaker.kt`.
- [ ] **Conversation screen live.** The designed screen now runs the loop: highlight, blink, speak, start again from the first card. Code: `conversation/ConversationController.kt`, `MainActivity.kt`.

How to test on the phone:
1. Install the APK, allow the camera, put the phone on a stand at arm's length.
2. The pill says "Eyes found" and "I need water" is lit.
3. Shut your eyes, wait for the tick (1 second), open them. The phone says "I need water".
4. Do it ten times in a row. Count misses and wrong cards.
5. Short blink (half a second): the light moves to the next card. Short blink, then long blink: the phone says "I am in pain".
6. Also try: normal blinking (nothing should happen), a wink (nothing), turning away (pill says "Looking for you").
7. Tap the stats button (or long-press the "Eyes found" pill) to open the check screen with live numbers, the blink lines and the last few closures (picked or ignored). Back returns.

Unit tests:
- `BlinkDetectorTest`: short and long blinks, the tick, fast blinks, too long, wink, face lost, head turned, the gap between the two lines.
- `BoardTest`: pages, More options, Yes / No, PRD phrase list.
- `ConversationControllerTest`: stepping, wrap round, choosing, the tick, normal blinks doing nothing, the highlight never moving by itself.

Extras beyond the PRD:
- Tapping a card also speaks it, so the person at the bedside can test without blinking.
- Blinks are ignored while the phone is speaking, so one blink cannot pick twice.
- If the speech engine never says it finished, the board carries on after 10 s anyway.

Changed from the PRD, with reason:
- PRD F2 has the highlight move on its own every 1.2 s. On the phone the owner wanted to move it themselves, so a short blink steps to the next card and a long blink chooses. It still needs nothing but blinks.
- A face turned more than 18 degrees left or right counts as lost, since ML Kit gives no eye-open values past that. Head tilt is allowed up to 35 degrees for looking down at a phone.
- PRD says a blink is roughly 0.3 to 0.9 s. On the phone, normal blinks measured up to about 0.4 s and were picking cards, so a short blink is now 0.5 to 1 s and a long blink 1 to 1.9 s, as the PRD risk table suggests ("Raise the hold time"). Calibration sets it from the person's own blinks.

For later milestones:
- The eye button opens the practice round. The stats button opens the check screen until the stats screen arrives in M4.

## M2. Model replies

PRD pass test: after choosing "I am in pain", the next four cards are relevant (for example where it hurts), and they appear within 2 seconds.
Status: not passed yet. Needs M1 to pass first.

- [ ] **Model on the phone** (F4). Gemma runs through LiteRT-LM, loaded once when the app starts. Tries the GPU, falls back to the CPU. The model file is picked once inside the app ("Choose model file" on the check screen) and copied into the app's folder; no cable needed. Code: `suggest/ModelImporter.kt`. Code: `suggest/LiteRtLmModel.kt`, `suggest/OnDeviceModel.kt`.
- [ ] **Suggestion engine** (F4). Sends the last few lines of the conversation and the time of day, asks for exactly four replies as a JSON list (first person, at most 8 words). Code: `suggest/Prompt.kt`, `suggest/ModelSuggestionEngine.kt`. The `SuggestionEngine` interface is the swap point if another runtime is needed.
- [ ] **Reply checking.** Exactly four, different, short replies, or the answer is rejected. A bad answer gets one retry, then the phrase bank is used. Code: `suggest/ReplyParser.kt`.
- [ ] **Waits for the new replies.** After speaking, the board waits up to 2.5 s for the model so the cards do not change under the person's eyes; then it carries on with the phrase bank.
- [ ] **Instant fallback** (F5). The phrase bank shows at once after each sentence while the model thinks, and stays if the model fails. "More options" pages from the model's replies into the phrase bank.
- [ ] **Measured.** The check screen (stats button) shows the model state (loading, ready on GPU or CPU, failed) and the last reply time with tokens per second.

How to test on the phone:
1. On the phone's browser, open huggingface.co/litert-community/Gemma3-1B-IT, sign in, accept the Gemma licence, and download the `.litertlm` file (about 0.5 GB).
2. In the app tap the stats button, then "Choose model file", and pick the downloaded file. It copies, then loads: wait for "Model: ... ready on GPU" (or CPU). Back.
3. Choose "I am in pain". The phrase bank shows while it thinks, then four new cards should be about the pain.
4. Tap the stats button and read "Last replies". It should say under 2 s from the model.
5. If it says phrase bank, the model failed or answered badly twice. Tell me what it says.

Unit tests:
- `ReplyParserTest`: good lists, code fences, trailing comma, escapes, wrong count, long, empty or repeated replies, junk.
- `PromptTest`: asks for four short replies as JSON, time of day, who said what, only recent lines.
- `ModelSuggestionEngineTest`: good answer, one retry, fallback after two bad answers, fallback on error, timing.
- `ConversationControllerTest` and `BoardTest`: new replies after speaking, model page first, old answers ignored.

Extras beyond the PRD:
- The model starts writing the next replies while the phone is still speaking, which saves time.
- Only the answer to the latest request is used, so a slow answer can never replace newer cards.
- Output is capped at 96 tokens so a rambling answer cannot hold up the reply time.

To tune on the phone (PRD: pick the largest model that meets 2 s): try Gemma3-1B-IT first, then a larger Gemma if it stays under 2 s.

## M3. Listening and calibration (started)

PRD pass test: a stranger asks an unscripted question aloud and gets a sensible blinked answer in under 20 seconds, in airplane mode.
Status: in progress. Calibration and the practice round are built; listening is next.

- [ ] **Calibration** (F6). The app opens with it. For about 8 seconds of seeing the face it measures the person's open-eye level and the length of their normal blinks, then sets the blink lines and the hold time. Code: `practice/PracticeSession.kt`.
- [ ] **Practice round** (F6). The designed screen: the star lights up in one of three places, the person blinks while it is lit, three catches and they are ready. It shows the live eye level against their blink line. Blink accuracy (caught out of all chances and stray blinks) is kept for the stats screen. The eye button opens it again any time. A short or a long blink both count in practice.
- [ ] **Listening** (F7). Not built yet.

How to test on the phone:
1. Open the app. Look at the screen with eyes open; it says "Measuring" then "Steady".
2. When a star lights up, shut your eyes for about half a second. "Blinks caught" goes up.
3. After three, press "Start talking" (or back) to reach the conversation.

Unit tests:
- `PracticeSessionTest`: measuring, waiting for the face, hold time from natural blinks, rounds, accuracy, no misses while the face is away.
- `BlinkDetectorTest`: rewritten for the person-based lines, noise, short dropouts and long closures.

## Design (from the teammate)

The four designed screens are built exactly from the design file, as stand-alone screens. Each one is wired up in the milestone that needs it. See them in Android Studio with the Preview pane.

- [ ] **Theme.** Urbanist font, colours, glass cards, glowing gradients and shadows from the design. Code: `ui/theme/`. Icons from the design are in `res/drawable/ic_*.xml`.
- [ ] **Conversation** (live since M1): status pill, "Heard" card, four reply cards with the highlighted one, "More options" and "Yes / No". Code: `ui/ConversationScreen.kt`.
- [ ] **Practice round** (wired in M3): star targets, blinks caught, eye-open bar with your blink line, short and long blink times. Code: `ui/PracticeScreen.kt`.
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
- The practice tile "Scan speed" now reads "Long blink", since the highlight no longer moves by itself.
- No design for the "Choose model file" button; it is on the plain check screen.
- Practice round status words not in the design: "Measuring", "Shut", "Looking for you", "Keep your eyes open while it measures", "Two more...", "You are ready". Between rounds no star shows (three rings).
- The M0 check screen has no button; it opens with a long-press on the status pill.
- After speaking, while waiting for new replies, no card is lit.
- While the phone is speaking, no card is lit.

## CI checks

Run on every push and pull request (`.github/workflows/ci.yml`). The `main` ruleset requires all three to be green before a pull request can merge.

| Check | What it catches |
|---|---|
| Unit tests | Logic that broke (`./gradlew testDebugUnitTest`) |
| Android lint | Common Android mistakes (`./gradlew lintDebug`) |
| Build APK | Code that does not compile. Also fails if the APK asks for INTERNET. Uploads `outspoken-debug-apk` |

## Next

M3: listening (the visitor's question becomes text for the model), calibration and the practice round. Starts after M1 and M2 pass on the phone.
