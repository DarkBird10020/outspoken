# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: looking down is read from the iris position between the eye corners, so it works as easily as looking up even with the phone below eye level. Waiting for the phone test.

## Phone test history

| Date | What was tested | Result | What changed |
|---|---|---|---|
| 2026-10-09 | M0 to M2 | Not working properly, picks felt random | Blink rules rebuilt, run logs added |
| 2026-10-09 | M1, M2 | The highlight moved on its own; the owner wants to move it with the eyes. The model was missing | Eye movement control kept as the way to move (look down / up, blink to say). Gemma model replies added on top. "Choose model file" in the app |
| 2026-10-09 | Eye movement build | Still not working well | "Save logs" button added so the run logs can be sent and read |
| 2026-10-09 | Look up / down build | Looks down never registered (they read as eyes closing), crash when the app closed | Look up = next only, crash fixed, portrait lock, eyes shown on the main page |

## Look down read from the iris (owner report)

- [ ] **Looking down moves the highlight with a small eye movement, like looking up.** The owner found up responsive and down dead. Phone run at 00:21: resting gaze 0.52 to 0.71 down, the furthest look down 0.78, so the "look down" blendshape had about 0.1 of room, the same as its wobble at rest, and calibration turned looking down off (reach 0.09). The lids also drop when looking down (eye-open 0.45/0.52, lid gap 0.14 at 00:21:25), close to a real close. Now looking down is measured from where the iris centre sits between the two eye corners (`FaceMesh.irisDrop`): the corners do not move with the lids, the iris does move with the eye. Calibration measures the iris drop when looking down and the line is half of it, like up. Up is unchanged. Code: `eye/FaceMesh.kt`, `eye/EyeReader.kt`, `scan/GazeStepper.kt`, `setup/Calibration.kt`.
- [ ] **A look down is not a close.** While the iris clearly points down, dropping lids do not start a close, so looking down cannot choose a card. Code: `blink/BlinkDetector.kt` (`lookingDown`), `conversation/ConversationController.kt`.
- [ ] **Gaze box shows both directions in line units**: the dashed lines are where up and down move the highlight; down follows the iris when that is in use. The iris drop is in the per-second log line and the calibration line.
- [ ] **APK for arm64 only**: native libraries for every chip type made it 140 MB and over 20 minutes to reach the phone.

Unit tests:
- `FaceMeshTest`: iris below the corner line reads down, a head tilt alone does not, both eyes averaged.
- `GazeStepperTest`: look down read from the iris even with lids drooping; a small iris movement does nothing.
- `BlinkDetectorTest`: dropping lids while the iris looks down do not start a close.
- `CalibrationTest`: iris look down line is half the measured iris drop.

## Calibration uses the deeper close

- [ ] **One weak close no longer sets the lines.** Calibration took the shallower of the two "Close your eyes" steps. In the 00:13 phone run that was closed 0.54 against open 0.88 and a lid gap of 0.22 against 0.31, so the shut lines landed at 0.71 and 0.25, next to open, and normal looking chose "I need water" by itself four times. It now takes the deeper close. Code: `setup/Calibration.kt`. Test: `CalibrationTest` "one weak close does not set the lines".

## Gaze box centred on the resting gaze (owner report)

- [ ] **Still eyes sit in the middle of the gaze box.** The owner saw the dot pinned to the bottom with the eyes still: the box drew the raw gaze, and with the phone below eye level the resting gaze reads about 0.5 down. It now draws the gaze up and down from the resting gaze, the same measure the highlight uses, with the look up and look down lines dashed; crossing a line moves the highlight. The word under it says "up: move up", "down: move down" or "at rest" with the distance. Code: `ui/EyeCheckScreen.kt` (`GazeBox`), `scan/GazeStepper.kt` (`restGaze`).
- [ ] **Calibration sets the resting gaze directly**, so looks are measured from the right centre from the first frame instead of re-learning it. Code: `scan/GazeStepper.kt` (`restAt`), `MainActivity.kt`.

Unit tests:
- `GazeStepperTest`: a measured rest is the centre from the first frame.

## Reply speed and accuracy brief

- [ ] **Gemma answers in one call, under the 2 s target.** Temperature 0.8 to 0.3 so small models keep the four-item JSON shape; one worked example in the prompt, which ends on "Answer:". A badly formatted answer used to get a second generation, doubling the reply time; now `extractReplies` takes what it can (JSON list, quoted strings, numbered or bulleted lines), drops long and repeated replies, and the phrase bank fills the rest. Nothing usable or a model error gives the phrase bank. Code: `suggest/LiteRtLmModel.kt`, `suggest/Prompt.kt`, `suggest/ReplyParser.kt`, `suggest/ModelSuggestionEngine.kt`. The tests "bad format is retried once" and "two bad answers fall back to the phrase bank" were replaced on purpose: there is no retry any more.
- [ ] **Quick topics on the main page** (PRD F7 fallback): one-tap questions under the cards for when the room is too loud for the microphone. Code: `ui/ConversationScreen.kt`, `listen/QuickTopics.kt`.
- [ ] **Blink smoothing.** A running average of the eye-open values (newest frame weighted 0.65) evens out single-frame jitter; it adds about 15 ms. The shut and open lines already give the hysteresis. Code: `blink/BlinkDetector.kt` (`smoothing`), `setup/Tuning.kt`.
- Not done from the brief, with the reason from the phone logs:
  - Subtracting a share of the downward gaze from the blink score: real closes also read gaze down 0.73 to 0.76, the same as looking down (0.60 to 0.81), so it shifts both and separates neither.
  - Averaging the lid gap (40%) into the eye-open value: the lid gap is already used as a second check that must also say shut, which is stricter than an average where one reading can cover for the other.
- Already in place: the on-device offline speech recognizer (`listen/Listener.kt`: `createOnDeviceSpeechRecognizer`, `EXTRA_PREFER_OFFLINE`, free form, one result) and `RECORD_AUDIO`; no INTERNET permission (CI checks it).

Unit tests:
- `ReplyParserTest`: lenient extraction keeps a short list, pulls quoted strings and numbered or bulleted lines, drops long and repeated replies, empty when nothing usable.
- `ModelSuggestionEngineTest`: a near miss is used and topped up with one model call, an unusable answer falls back with one call, top up skips phrases already given.
- `PromptTest`: one worked example, ends on "Answer:".
- `BlinkDetectorTest`: smoothing ignores a single jittery frame, a real close still counts.

Design gaps: quick-topic chips on the main page use the glass surface and a 15 pt label.

## Faster choosing (owner request)

- [ ] **Choose while the eyes are still shut.** Before, a close only chose when the eyes opened again, so every choice waited the whole close plus the reopen. Now it chooses the moment the close reaches the shortest-blink time; the reopen does nothing more. Code: `blink/BlinkDetector.kt` (`chooseWhileShut`), `setup/Tuning.kt`.
- [ ] **Shortest close 0.4 s to 0.3 s.** Published blink studies put spontaneous blinks at about 100 to 150 ms; with the lid gap check, 0.3 s still keeps them out.
- [ ] **Analysed frames 1280x960 to 640x480.** Phone logs: 30 fps with no face in view, 20 to 25 fps with a face, so the per-frame work was the limit. MediaPipe crops the face to 256 x 256 anyway; preview stays 1280x960, same 4:3 shape so the dots line up. Code: `eye/FrontCamera.kt`.

Unit tests:
- `BlinkDetectorTest`: choosing while shut fires once at the shortest-blink time and not again on reopen; a normal blink still does nothing.

## Look down and up (owner request)

- [ ] **Look down = highlight moves down, look up = moves up, both wrap round.** From the top card one look up reaches "Yes / No", two reach "More options". Looking down was off because on the phone it read as the eyes closing; the lid gap check now tells them apart (looking down about 0.15, closed 0.04 to 0.09), so a look only stops when the eyes are really shut. Code: `scan/GazeStepper.kt`, `blink/BlinkDetector.kt` (`eyesShut`), `conversation/ConversationController.kt`.
- Why: in the 23:41 phone run blinking chose cards fine, but "More options" and "Yes / No" were only reachable with four or five looks up in a row, and the highlight goes back to the top after every sentence, so they were only ever opened by tapping.
- [ ] **Calibration measures the look down too** ("Look down, below the phone", twice). The look down line is half of the smaller look down measured; if none is seen, looking down is turned off and up still works. Slider: "Look down distance". Code: `setup/Calibration.kt`, `setup/Tuning.kt`, `ui/EyeCheckScreen.kt`.

Unit tests:
- `GazeStepperTest`: down and up each step once, down can be switched off, reading and short glances do nothing, shut eyes never step.
- `EyeModeTest`: down moves to the card below, up from the top wraps to Yes / No then More options, a blink on More options opens the next page, blink says the card, back to top after speaking.
- `CalibrationTest`: look down line from the measured look down, no look down turns it off.

## Calibration (PRD F6)

- [ ] **Measures the eyes at every start, about 25 s, spoken.** "Look at the screen", "Look up, above the phone" (twice), "Close your eyes" (twice). From those: the look needed to move is half of the smaller look up measured, the shut line is halfway between closed and open, the open line three quarters of the way up. Saved, and logged as `calibration: ok: ...`. If a step is not seen (face out of view, no look up, eyes not closed) it says why and keeps the old lines; "Try again" or "Skip". Also a "Calibrate my eyes" button on the eye check page. Code: `setup/Calibration.kt`, `ui/CalibrationScreen.kt`, `MainActivity.kt`.
- Why: the phone logs showed the eye readings move with where the phone sits. One test had the resting gaze at about 0.55 and eye closes at 0.22 to 0.36; the next had the resting gaze at about 0.0 and eye closes at 0.39 to 0.58. Fixed lines that worked in one test stopped looks or blinks completely in the next, and with the phone at eye level a look up had to reach the very top of the eyes.

- [ ] **Lid gap check on blinks.** First calibrated run on the phone (23:24): open 0.88, closed 0.48, so the shut line went to 0.68. Looking down at the screen alone then read 0.55 to 0.65 and "I need water" was said without a real close (23:25:04, gaze 0.59 to 0.65). The lid gap told them apart: about 0.30 open, about 0.15 looking down, 0.04 to 0.09 closed. Now the eyes only count as shut when both readings say so, and open again when either does. Calibration measures the gap too: shut line 10% of the way from closed to open, open line 30%. (It was 30% and 50%; the 23:38 run measured closed 0.12 and open 0.30, which put the shut line at 0.17, while looking down at the screen read 0.14 to 0.17.) Code: `blink/BlinkDetector.kt`, `setup/Calibration.kt`, `setup/Tuning.kt`.

Unit tests:
- `CalibrationTest`: lines set from the measured eyes, lid gap lines set when read and off when not, prompts in order, fails and says why with no look up, no eye close or no face, other settings kept.

## Eyes move the highlight (owner decision)

The owner asked for the highlight to follow the eyes instead of moving on a timer. This replaces PRD F2 timed scanning as the default; the timer stays as a switch.

- [ ] **Look up = next card, close eyes about 0.4 s = say it.** One look up (above the top of the phone) is one step; the eyes come back to rest before the next; after the last card it goes back to the first. Looking down and sideways do nothing. Why only up: in the phone logs every look up registered (gaze -0.65 to -0.92), but every look down (gaze 0.81 to 0.87) arrived as "eyes shut", because looking down drops the upper lids. So down looks never moved the highlight ("stuck in the middle") and could fake a blink. Code: `scan/GazeStepper.kt`, `conversation/ConversationController.kt`.
- [ ] **Eyes and cards on one page.** The main page shows the live camera with the eyelid and iris dots above the cards, and a one-line hint ("Look up: next. Close eyes: choose."), so the person sees their eyes are being read while choosing. The eye check page stays for tuning. Code: `ui/ConversationScreen.kt` (`eyeView`), `ui/EyeCheckScreen.kt` (`EyeMonitor`).
- [ ] **Portrait only.** The log showed Android rebuilding the screen mid-test (`app: closed` then a fresh start at 22:38:21), the usual sign of a rotation, and that rebuild led to the crash below. The app is now locked to portrait and does not restart on rotation. Code: `AndroidManifest.xml`.
- [ ] **Switching pages keeps the camera.** Leaving one page could stop the camera preview the next page had just started. Only the page showing the preview can stop it now. Code: `eye/FrontCamera.kt`.
- [ ] **Crash on close fixed.** Logged `MediaPipeException: The task graph hasn't been started` from `EyeReader.analyze`: a camera frame reached the face tracker after it was closed. It now closes on the camera thread and ignores late frames. Code: `eye/EyeReader.kt`, `MainActivity.kt`.
- [ ] **Blink and look settings from the logs.** Deliberate closes went down to 0.22 to 0.36 and lasted 0.31 to 0.65 s; normal blinks stayed at 0.40 to 0.53 and up to 0.25 s; at 0.25 s minimum, normal blinks said "I need water" three times in 8 s. Now: shut line 0.45, open line 0.55, 0.4 to 1.5 s. Looks up measured 0.65 to 0.92 from rest while reading the cards moved the eyes about 0.3, so a look must be 0.45 and last 0.25 s. Code: `setup/Tuning.kt`, `scan/GazeStepper.kt`.
- [ ] **Eye reading switched from ML Kit to MediaPipe Face Landmarker.** Phone logs showed ML Kit could not do this: its dots trace the eyelids, not the eyeball, so they never moved with the eyes; one eye often read 0.01 while the other read 0.3; and it lost the face for single frames 16 times in a minute. MediaPipe gives 478 face points including both irises, a blink score per eye and gaze scores (look up, down, in, out). Model: `assets/face_landmarker.task` (Google, Apache 2.0). Code: `eye/EyeReader.kt`, `eye/FaceMesh.kt`.
- [ ] **Face lost only after 0.4 s.** Single dropped frames no longer flip "Eyes found" off and on, and a face dropout during a blink no longer cancels it. Code: `blink/BlinkDetector.kt`.
- [ ] **Tuning on the phone.** Eye check screen sliders: shut line, open line, shortest and longest blink, look distance, look hold, eyes or timer. Saved on the phone, reset button. No rebuild needed to tune. Code: `setup/Tuning.kt`, `ui/EyeCheckScreen.kt`.
- [ ] **Cleaner debug dots.** Small dots on the eyelids, a ring on each iris (green open, yellow unsure, red shut), and a gaze box showing where the eyes look.

How to test on the phone:
1. Main screen: the highlight stays on the first card until you move your eyes.
2. Look up above the top of the phone for a moment, then back: the highlight moves to the next card.
3. On the card you want, shut your eyes for about half a second: the phone says it.
4. If looks are missed or too easy, open the eye check screen and move "Look distance" and "Look hold". If blinks are missed, watch the graph and move the shut line.

Unit tests:
- `GazeStepperTest`: rest does nothing, up steps once, down, sideways, small reading glances and short glances do nothing, shut eyes never step, rest follows posture.
- `EyeModeTest`: highlight waits for the eyes, up moves to the next card and wraps after the last, down does nothing, blink says the card the eyes moved to, back to top after speaking.
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

- [ ] **Save logs.** The eye check screen's "Save logs" button writes every run log into one text file at a place you pick (for example Downloads). The file stays on the phone; send it on from there. Code: `log/LogExport.kt`.

How to read them (phone on USB):
- Live: `adb logcat -s Outspoken`
- Files: `adb pull /sdcard/Android/data/com.outspoken/files/logs`

Unit tests:
- `EyeSummaryTest`: the once-per-second eye line.
- `LogLinesTest`: blink picked and said, fast blink and long closure explained, face lost reasons, taps and card changes.
- `LogExportTest`: all run logs in one file, oldest first, with headers.

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
3. Look up once to move to "I am in pain", then close your eyes for about half a second. The phrase bank shows while it thinks, then four new cards should be about the pain.
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

## M3. Listening and calibration

PRD pass test: a stranger asks an unscripted question aloud and gets a sensible blinked answer in under 20 seconds, in airplane mode.
Status: in progress. Calibration is in its own section above; listening is built; the practice round is still to come.

- [ ] **Listening** (F7). The microphone listens all the time on the conversation page, on the phone itself: Android's on-device recogniser when there is one, otherwise the normal recogniser told to stay offline. Each finished sentence shows on the designed "Heard" card and goes to Gemma with the conversation, so the four replies answer it. Code: `listen/Listener.kt`, `conversation/ConversationController.kt`.
- [ ] **Does not hear itself.** Listening pauses while the phone speaks (replies and calibration prompts), drops anything heard in the 1.5 s after, and drops text that matches what the phone just said. Code: `listen/HeardFilter.kt`.
- [ ] **Typed question and topic buttons** (F7 fallback). On the eye check page: type the visitor's question and tap Ask, or tap a topic (Pain, Comfort, Food and drink, Feelings, Family). Either goes to the conversation like a spoken question. Code: `ui/EyeCheckScreen.kt` (`AskBox`), `listen/QuickTopics.kt`.
- [ ] **Microphone permission** asked once after the camera. The eye check page shows what the listener is doing ("listening on the phone", "no microphone permission", "wants the internet: download the offline English speech pack").
- [ ] **Practice round** (F6). Not rebuilt yet on the new eye reader.

How to test on the phone:
1. Turn on airplane mode. Open the app and allow the microphone.
2. After calibration, ask out loud: "Are you in pain?". The "Heard" card shows it, and the cards change to answers about pain once the model replies.
3. If nothing shows, open the eye check page: the "Listening:" line says why. Type the question there instead, or tap "Pain".
4. Answer by looking up to the right card and closing your eyes. Time it from the end of the question: the PRD target is under 20 seconds.

Unit tests:
- `HeardFilterTest`: questions kept and cleaned, blank results dropped, nothing kept while the phone speaks or just after, the phone's own words dropped, topic buttons are all questions.
- `ConversationHeardTest`: the question shows and goes to the model, the answer follows it, answering clears it, questions during speech are ignored.

Extras beyond the PRD:
- The echo filter, since the speaker and microphone sit a few centimetres apart.

## M4. Alarm, stats, laptop view (started)

PRD pass test: the help alarm, the stats screen and the laptop view all shown in one run.
Status: in progress. The help alarm and the stats screen are built; the laptop view is next.

- [ ] **Help alarm** (F8). Eyes held shut for 2 s: a beep says the hold is done. Open the eyes and blink once within 5 s: the phone's alarm sound plays on the alarm channel, looping, at full volume, and the designed help screen fills the display with the last thing said. "I am here" (or back) stops it and returns to the cards; the speaker button silences it. The confirm blink never picks a card, and the hold itself never counts as a blink. Works even while the phone is speaking. Code: `help/HelpTrigger.kt`, `help/HelpAlarm.kt`, `conversation/ConversationController.kt`, `ui/HelpAlertScreen.kt`.
- Why two steps: a person resting with their eyes closed should not set it off alone.
- [ ] **Stats screen** (S1). The designed screen, opened with the stats button (bar chart). Average reply time and model speed (tokens per second) over this session's model answers, replies written, the phone's temperature (battery sensor), model name and runtime, session length, sentences spoken. Updates every second. Blink accuracy shows "-" until the practice round is rebuilt. Code: `stats/PitStats.kt`, `ui/StatsScreen.kt`, `MainActivity.kt`.

How to test on the phone:
1. On the conversation page, close your eyes and keep them closed until the beep (2 s).
2. Open your eyes, then close them for about half a second once.
3. The alarm sounds and the pink help screen shows "Help needed". Tap "I am here".
4. Also try: close your eyes for 3 s and just open them, then wait 6 s. Nothing should happen.
5. Tap the stats button (bar chart). After a few answers from the model, reply time and model speed fill in; session length counts up.

Unit tests:
- `HelpTriggerTest`: the cue once, alarm on the confirm blink, short closes do nothing, no confirm in 5 s cancels, the hold is never a pick.
- `ConversationHelpTest`: hold, open, blink raises the alarm and says no card; a normal pick raises nothing.
- `PitStatsTest`: averages, replies written, fallbacks kept apart, session length.

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
- Help screen after the sound is turned off: it still reads "Alarm is sounding".
- No design for the typed question box and topic buttons; they are on the plain eye check page.
- No design for the "Choose model file" button; it is on the plain eye check screen.
- Main page live camera with eye dots (owner asked for it): a 150 dp rounded box under the status row, and a hint line in the soft ink style.
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
