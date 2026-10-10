# Progress

What has been built, why, and how to test it. Updated with every change.

Marks: `[ ]` built, not yet tested on the phone. `[x]` passed on the iQOO (date).

Latest: the 05:45 to 05:52 phone run showed no new bug (42 replies, median 0.9 s; 35 cards chosen, none missed). fixed open eyes picking cards after calibration (05:37 to 05:38: the gap check had switched off). "Are you in pain" now gets answers about pain. what the visitor just said now decides the four replies ("Are you in pain" got rest and water cards at 05:27:38). Help sounded three times on the phone at 05:18 to 05:19. help now sounds when the eyes close again after the beep, blink or longer hold (three phone tries were cancelled at 05:08); calibration no longer sets a lid gap line so low that held closes miss; no more visitor-style question cards. the model's earlier cards no longer go into its prompt (it copied them back five times in a row, through "In pain"), and the line just said is not offered again. Phone logs 04:19 to 04:55: MTP on, replies 0.6 to 1.4 s. holding the eyes shut for help no longer says a card first (a card is now chosen when the eyes open). "Share logs" on the eye check page sends the run logs straight into a chat, and `tools\phone-logs.ps1` keeps a live copy on the laptop (CLAUDE.md section 12). A damaged model download is caught by its checksum and named plainly, instead of "Engine is not initialized" every 3 s. Download the model again; the app checks it once (about 2 s). Waiting for the phone test.

Also: choosing now needs the eyes held shut for 0.4 s. The phone logs of 2026-10-10 showed unprompted blinks of 200 to 242 ms picking cards ("I need water" five times in 11 s) and a double blink picking one, so the hold went up from 0.2 s and double blinks are off by default. Listening no longer drops the working on-device recogniser when the phone speaks. Time with no face no longer counts as eyes shut (a close seen under 0.2 s, then the face lost for 0.2 s, picked a card). The model now starts with Gemma 4's own drafter (MTP) on the GPU for faster replies, falling back to the plain GPU and then the CPU. Model replies now fit a person in bed being cared for (no more "I want a glass of wine" small talk) and avoid repeating cards just passed over. Also: laptop builds can sign with the shared key (`~/.android/outspoken-debug.keystore`), so any APK installs over the last one and nothing is uninstalled; a model picked with "Choose model file" is found at every start. Models stay in Downloads across reinstalls. Waiting for the phone test.

## Phone test history

| Date | What was tested | Result | What changed |
|---|---|---|---|
| 2026-10-09 | M0 to M2 | Not working properly, picks felt random | Blink rules rebuilt, run logs added |
| 2026-10-09 | M1, M2 | The highlight moved on its own; the owner wants to move it with the eyes. The model was missing | Eye movement control kept as the way to move (look down / up, blink to say). Gemma model replies added on top. "Choose model file" in the app |
| 2026-10-09 | Eye movement build | Still not working well | "Save logs" button added so the run logs can be sent and read |
| 2026-10-09 | Look up / down build | Looks down never registered (they read as eyes closing), crash when the app closed | Look up = next only, crash fixed, portrait lock, eyes shown on the main page |
| 2026-10-10 | Logs of the 01:13 run on this iQOO (read from the phone) | 3 blinks chose; 13 closes ignored, including 595 ms and 554 ms ones, because the saved shortest blink was 600 ms. Looks fired about 40 times in 15 min | Shortest blink over 400 ms from old builds cleared; practice round sets 200 to 400 ms; look down off by default; blink only mode |
| 2026-10-10 | Logs of the 02:28 to 03:34 runs (sent from the phone) | Cards picked on their own: every pick came from a close of 200 to 242 ms, the same length as the unprompted blinks that were ignored (74 to 197 ms); a double blink picked "Good evening, how are you?". Listening stopped after the first sentence was spoken (error 5 dropped the working on-device recogniser, then error 13 every 5 s). Gemma 4 E2B replied in 1.5 to 2.3 s | Pick hold 0.4 s by default and never below 0.35 s (saved lower values and the slider included); double blink off by default; listening keeps the on-device recogniser and only restarts it after error 5; a close only lasts as long as the eyes were seen shut, so a lost face does not stretch it into a pick (03:24:07: under 200 ms seen shut, then no face, picked "I need water" as 397 ms) |
| 2026-10-10 | Logs of the 04:08 to 04:55 runs ("Share logs") | MTP works: "ready on GPU with MTP", replies 0.6 to 1.4 s at 37 to 93 tok/s (before: 1.6 to 1.9 s at 26 to 29). Listening heard every question ("Hey do you hungry", "Do you want to eat in the breakfast"). Short blinks of 35 to 262 ms were all ignored. With the new prompt the cards stayed the same five times in a row, through "Hello" and "In pain" | Earlier cards no longer go into the prompt (the model copied them back); the line just said is dropped from the next cards |
| 2026-10-10 | Logs of the 04:56 to 05:09 runs | Cards now change with each question ("Are you in pain" gave new cards). Choosing on reopen works (picks of 0.5 to 1.5 s). Help: three holds reached the beep, but each confirm was a second 2.6 to 2.8 s hold, not a blink, so help was cancelled. One calibration set the lid gap line at 0.09, below held closes (0.10 to 0.15). Cards still had visitor-style questions | A second close confirms help; the lid gap line comes from the typical held close; "a question back" removed from the prompt |
| 2026-10-10 | Logs of the 05:12 to 05:29 runs (build 32f67a9) | Help alarm sounded three times: once confirmed by closing again (05:18:14), twice by a blink (05:19:11, 05:19:35); no card was said. Picks came from closes of 0.44 to 1.4 s. Replies 0.7 to 1.2 s. "Are you in pain" got rest and water cards, nothing about pain | The visitor's last line now decides the replies |
| 2026-10-10 | Logs of the 05:36 to 05:39 run (build 5c68c60) | "Are you in pain" got "A little bit, yes.", "No, I feel okay now.", "Just a little ache today.", "No pain, just tired.". Help sounded (05:37:29). But calibration measured lid gap closed 0.28 against open 0.29, the gap check went off, and open eyes looking down picked five cards | The lid gap is measured only on frames that read shut |
| 2026-10-10 | Logs of the 05:45 to 05:52 run (build 61abde1) | Calibration lid gap open 0.30, closed 0.15, line 0.17. 42 replies, median 0.9 s, 60 tok/s. 35 cards chosen by closes of 0.45 to 1.4 s; only blinks up to 0.37 s ignored. Quick topics answered fittingly ("Are you in pain?": "A little ache in my back.") | No new bug in the logs; numbers added to the README |
| 2026-10-09 | Reading options / accuracy test | Jitter when reading options, accidental selection blinks, no tutorial | Practice round tutorial screen wired up, asymmetric eyelid detection, yaw jitter tolerance, upward gaze boundary |

## Models: download and switch in the app (owner request)

- [ ] **Models list on the eye check page:** Gemma 4 E2B (2.6 GB, fastest, about 1 s a reply) and Gemma 4 E4B (3.7 GB, more accurate, about 2 to 3 s). Each shows "not downloaded", "downloading in the browser", "downloaded", "loading" or "in use", with a Download or Use button. Code: `setup/ModelCatalog.kt`, `setup/ModelShelf.kt`, `ui/EyeCheckScreen.kt` (`ModelList`), `MainActivity.kt`. Tests: `ModelCatalogTest`.
- [ ] **The app still has no internet** (PRD rule, CI check). Download opens the file in the phone's browser. While the app is open it checks Downloads every 3 s and loads the model it was asked for by itself. Needs "All files access" once per install (button "Allow access to Downloads"; asked by itself at start when no model can be seen).
- [ ] **Models stay in Downloads and are never moved or copied** (owner report: the model was gone after a restart). Phone log 03:20:30: moving the 2.6 GB file into the app's folder was cut short when the app came back to the front, and by 03:20:37 no model file was left; that folder is also wiped on uninstall. Now the model is loaded from Downloads, so a restart, reinstall or uninstall keeps it. The chosen model is saved in `Documents/Outspoken/model.txt`, so it stays loaded until another is picked with Use. Code: `setup/ModelShelf.kt`, `MainActivity.kt` (`watchDownloads`, `importModel`).
- [ ] **Switching models:** Use frees the model in use and loads the other one; no restart. The choice is saved and loaded at the next start. Code: `suggest/OnDeviceModel.kt` (`switchTo`), `suggest/LiteRtLmModel.kt` (`close`).
- [ ] **Choose model file** still works. A model already in Downloads is used where it is; another file is copied into Downloads (into the app folder only without access), and other model files are never deleted.
- [ ] **Faster replies with the model's drafter (MTP).** Gemma 4 files carry a small multi-token drafter (it guesses the next few words, the model checks them in one step) that LiteRT-LM leaves off unless asked; Google reports up to 3x faster writing. The app tries GPU with the drafter first, then plain GPU, then CPU, and logs why a step failed ("could not start on GPU with MTP: ..."). A 4-word warm-up reply checks the drafter works before it is used. Before: Gemma 4 E2B on GPU, 1.5 to 2.3 s a reply at 24 to 32 tok/s (03:20 to 03:22 runs). Code: `suggest/ModelStart.kt`, `suggest/LiteRtLmModel.kt`. Tests: `ModelStartTest`. Phone, 04:19 to 04:55: "ready on GPU with MTP", replies 0.6 to 1.4 s at 37 to 93 tok/s.
- [ ] **Model size shown right.** The eye check page said "ready on GPU, 0.00 GB" (03:20:37) because it measured the Downloads file, which was gone by then; it now measures the file that was loaded. Code: `suggest/OnDeviceModel.kt`, `MainActivity.kt`.
- [ ] **Replies fit a person being cared for.** The phone logs (03:20 to 03:22, "new cards from the model") showed small talk and lines for the visitor to say: "Good night, sleep well.", "Hope you have a nice night.", "I want a glass of wine.", "More wine, please now.", with no need such as pain or water; "What is happening now?" came back in four sets in a row. The prompt now says the person is in bed and cared for by family or a nurse, that every reply is the person's own words, never the visitor's, and to mix yes or no, a need or feeling, and a question back. Code: `suggest/Prompt.kt`, `suggest/Suggestions.kt`, `MainActivity.kt` (`lastModelReplies`). Tests: `PromptTest` "says who is speaking...", "earlier cards are not put in the prompt". Judged only on the phone: read the "new cards" lines in the saved logs.
- [ ] **Earlier cards stay out of the prompt; the line just said is not offered again.** Listing the cards offered before made the model copy them back: the same four cards five times in a row, through "Hello" and "In pain" (04:53 to 04:54). Now nothing earlier is listed, and the reply matching the line the person just said is dropped and topped up from the phrase bank ("Fruit sounds good now." came straight back at 04:23:03). Code: `suggest/Prompt.kt`, `suggest/ModelSuggestionEngine.kt`. Tests: `ModelSuggestionEngineTest` "the line just said is not offered again", "the top up skips the line just said too"; two top-up tests now expect the next phrase, since their example had just said "I am in pain".
- [ ] **No more questions the visitor would ask.** The prompt said to mix in "a question back". From 04:53 to 05:08 that gave cards like "Thirsty? Do you want water?", "Need water, yes?" and "Do you want quiet now?", and that last one was picked and said. Now it asks for yes or no and a need or feeling only. Code: `suggest/Prompt.kt`. Tests: `PromptTest`.
- [ ] **What the visitor just said decides the replies.** Phone 05:27:38: "Are you in pain" got "Yes, I need rest now.", "No, I feel okay today.", "Thirsty, please bring some water.", "No, I don't need anything.", with nothing about pain; the "mix of needs" line pulled every set towards rest and water. That is the demo's main moment (PRD section 10: "Are you in pain?" then "Yes, my back hurts."). Now, when the visitor spoke last, the prompt ends with what they said and asks for 4 replies to it; for a yes or no question (told from the first word, since heard speech has no question mark) it asks for a yes, a no and two more specific answers. The mix of needs is only asked for after the person speaks. Code: `suggest/Prompt.kt` (`isYesNoQuestion`). Tests: `PromptTest` "a yes or no question just asked decides the replies", "a greeting is replied to without asking for yes or no", "after the person speaks the replies mix needs", "yes or no questions are told apart from the first word". Judged only on the phone.

How to test the speed on the phone: open the eye check page; the model line should say "ready on GPU with MTP". Ask three questions; compare reply time and tok/s on the Stats screen with the numbers above. Save logs and send them.
- Not offered: the Qualcomm NPU build on Hugging Face is made for the previous chip (SM8750), not the iQOO 15's.

How to test on the phone:
1. Eye check page (eye button), scroll to Models. Tap "Allow access to Downloads" and switch it on, then go back.
2. Tap Download next to Gemma 4 E2B. The browser downloads it (2.6 GB). Come back to the app: the row shows "loading", then "in use, on GPU". The log says "downloaded Gemma 4 E2B, moved into the app".
3. Same for E4B, then tap Use on either to switch. Compare reply time and tokens per second on the Stats screen.

## Same app on both phones (owner report: the friend's APK is less steady)

- [ ] **The build is shown.** The eye check page ("Build:") and the first lines of every run log say which commit the APK came from, whether it had uncommitted edits, and whether it is the CI APK or built on a laptop. Two phones running different code show different lines. Code: `app/build.gradle.kts` (`COMMIT`, `CHANGED`, `FROM_CI`), `MainActivity.kt`, `ui/EyeCheckScreen.kt`.
- [ ] **E2B loads when no model was picked.** It used to load the largest model file, so with both downloaded the phone ran E4B: 2 to 3 s a reply (over the PRD's 2 s target), and it shares the GPU with the face tracker, which can drop the camera frames blinks are read from. A model picked in the app still wins. Not yet confirmed from a log; compare the "eyes fps" lines while the model writes replies. Code: `setup/ModelCatalog.kt` (`atStart`), `setup/ModelShelf.kt`. Tests: `ModelCatalogTest` "with nothing picked E2B loads, not the larger E4B", "at start the picked model wins when it is on the phone", "a picked model that is gone falls back to E2B", "with neither model on the phone there is no pick".
- Why the two APKs can differ even from one repo:
  - Built from different commits, or with uncommitted edits. The "Build:" line shows it.
  - Settings are saved on each phone: shortest blink, look lines, winks, look down, mode, scan speed. A new APK keeps them. "Reset to defaults" on the eye check page, then calibrate, puts both phones on the same start.
  - Each laptop signs its own build with its own debug key, so one person's APK cannot install over the other's without uninstalling, which also deletes the downloaded model. The CI APK (`outspoken-debug-apk`) is signed with the shared key: install that on both phones for exactly the same app.

## Damaged model downloads (owner report: "Engine is not initialized" on Use)

- Cause, from the phone (04:04 run): `gemma-4-E2B-it.litertlm` in Downloads had the right size (2,588,147,712 bytes) but SHA-256 `3b711ceb...` instead of Hugging Face's `18193810...`, so LiteRT-LM could not read it ("Invalid flatbuffer") on the GPU or the CPU. The app only checked the size.
- [ ] **The real error is shown.** When a start failed, closing the half-made engine threw "Engine is not initialized" and hid the real reason. The log now says why the GPU failed, then why the CPU failed. Code: `suggest/LiteRtLmModel.kt`.
- [ ] **Each model file is checked against Hugging Face's SHA-256 before loading.** Done once per file (about 2 s on the iQOO for E2B), remembered by path, size and time. A damaged file fails at once with "the download is damaged ... download it again". A damaged copy is skipped, so a fresh download saved as "gemma-4-E2B-it (1).litertlm" is used instead. Models not in the catalog cannot be checked and still load. Code: `setup/ModelCheck.kt`, `setup/ModelCatalog.kt` (`sha256`, `findDownloads`), `setup/ModelShelf.kt`, `suggest/OnDeviceModel.kt`. Tests: `ModelCheckTest`, `ModelCatalogTest` "every finished copy is listed...".
- [ ] **A failed model is not loaded again every 3 s.** The download watcher retried a failed file every 3 s (04:04 log), using CPU the camera needs. It now retries only a new or changed file. Code: `MainActivity.kt` (`watchDownloads`).

How to test on the phone:
1. Delete `gemma-4-E2B-it.litertlm` from Downloads (Files app), or leave it: the damaged copy is skipped.
2. Tap Download next to Gemma 4 E2B on the eye check page and wait for the browser to finish.
3. The model line goes "loading", then "ready on GPU". If it says the download is damaged again, the browser download itself breaks; send the logs.

## Reinstalls keep everything (owner report: the model had to be downloaded again after each reinstall)

- Models already stay in Downloads and are loaded from there (#45), so an uninstall no longer deletes them. What was left: an uninstall still clears the app's settings, logs and "All files access", and Android asks for that access again after every uninstall.
- [ ] **Laptop builds can sign with the shared key.** Each laptop signed with its own debug key (this laptop: `DA:3B:78...`, CI: `51:17:6F...`), so a laptop APK would not install over a CI APK or the other laptop's APK, and the only way was to uninstall. When `~/.android/outspoken-debug.keystore` exists, debug builds sign with it. The "Build:" line says "shared key" or "this laptop's own key". Code: `app/build.gradle.kts` (`sharedDebugKey`, `SHARED_KEY`), `MainActivity.kt`.
- Setup, once per laptop: the owner sends the shared debug keystore privately (the one in the CI secret `DEBUG_KEYSTORE_B64`), saved as `~/.android/outspoken-debug.keystore`. Never commit it.
- [ ] **A model picked with "Choose model file" is found at every start.** Its copy goes to Downloads under its own name, but the start only looked for E2B and E4B there, so another model (for example Gemma 3 1B) went missing at the next start. Downloads is now searched for any model file after those two. Code: `setup/ModelShelf.kt` (`fileToLoad`), `setup/ModelFile.kt`. Tests: `ModelFileTest`.

How to test on the phone:
1. With the shared key in place on both laptops, install one person's APK, then the other's, then the CI APK, each over the last one. None should ask to uninstall.
2. After each install, the model is "ready" on the eye check page without downloading, the tuning sliders keep their values, and "Build:" says "shared key".

## Models in the app

- [ ] **Download Gemma 4 E2B or E4B from the eye check page.** The phone's browser downloads the file (the app stays offline); the app moves it in from Downloads and loads it, and can switch between them. Needs the "see Downloads" permission. Code: `setup/ModelCatalog.kt`, `setup/ModelShelf.kt`, `suggest/OnDeviceModel.kt`, `MainActivity.kt`, `ui/EyeCheckScreen.kt`. Tests: `ModelCatalogTest`.
- [ ] **App icon.** Code: `res/mipmap-*`.

## Help hold says no card (PRD F8)

- [ ] **Holding the eyes shut for help no longer says a card first.** With the app's own settings, a card was chosen 0.4 s into the close while the eyes were still shut, so every help hold said the lit card (for example "I need water") before the alarm. The help test passed only because it used the old blink settings. Now a card is chosen when the eyes open after 0.4 to 1.5 s shut, and a 2 s hold is only ever the help hold. A choice now comes at the reopen, a little later than before. Code: `setup/Tuning.kt` (`chooseWhileShut = false`). Tests: `ConversationHelpTest` "with the app's own blink settings a help hold says no card" (fails on the old setting).
- [ ] **Closing the eyes again also confirms help.** Phone, 05:08:31 to 05:08:56: three holds reached the beep, and each time the person opened, then closed again for 2.6 to 2.8 s instead of a short blink, so the confirm never came and help was cancelled every time. Now any deliberate close that starts within 5 s of opening confirms: a blink of 0.4 to 1.5 s when the eyes open, or a longer close the moment it passes 1.5 s. That close never re-arms the help or picks a card. A second close starting after the 5 s is a new hold. Code: `help/HelpTrigger.kt`, `conversation/ConversationController.kt`. Tests: `HelpTriggerTest` "closing the eyes again for longer than a blink sounds the alarm once", "the close that sounded the alarm is not a pick", "a second close starting after the five seconds is a new hold"; `ConversationHelpTest` "hold, open, hold again raises the alarm without saying a card" (the first two and the last fail on the old code).

How to test on the phone: with "I need water" lit, hold both eyes shut until the beep (2 s), open, then blink once. The alarm sounds and nothing is said. Then close the eyes about half a second and open: the lit card is said.

## Getting the phone logs (owner request: "so you can check what is going on")

- [ ] **"Share logs" on the eye check page.** One tap puts every run log in one file and opens the phone's share sheet; pick a chat app, Drive or Gmail and send it. No laptop needed, and the app itself still sends nothing (no INTERNET permission). Code: `MainActivity.kt` (`shareLogs`), `ui/EyeCheckScreen.kt`, `AndroidManifest.xml` (file provider: lets the picked app read that one file), `res/xml/log_paths.xml`.
- [ ] **Live copy on the laptop.** `tools\phone-logs.ps1` copies the run logs and the logcat lines into `phone-logs/` every 30 s while the phone is on USB or wireless debugging. Steps in `CLAUDE.md` section 12.
- [ ] **Logs never go into the repo.** The repo is public and the logs hold what people said near the phone. `phone-logs/` is ignored by git, and CI fails if a log file is ever committed.

How to test on the phone: eye check page → Share logs → a chat app; the file "outspoken-logs.txt" should attach. On the laptop: run the script with the phone plugged in; `phone-logs/` fills with `outspoken-*.log` files.

## Picks only from a held close (phone logs of 2026-10-10)

- [ ] **Choosing needs the eyes shut for 0.4 s** (was 0.2 s), never below 0.35 s, whatever was saved or set on the slider. Unprompted blinks on the phone lasted 74 to 242 ms and every pick came from one of 200 to 242 ms. Code: `setup/Tuning.kt` (`MIN_PICK_HOLD_MS`), `ui/EyeCheckScreen.kt`. Tests: `TuningDefaultsTest`.
- [ ] **Double blink off by default.** Two unprompted blinks picked "Good evening, how are you?" (03:21:10). It stays as a switch on the eye check page. Code: `setup/Tuning.kt`.
- [ ] **A lost face does not stretch a close.** A close only lasts as long as the eyes were seen shut. At 03:24:07 the eyes were seen shut under 200 ms, the face was lost for about 200 ms, came back open, and the pick counted it as a 397 ms blink. Code: `blink/BlinkDetector.kt`. Tests: `BlinkDetectorTest` "time with no face does not count as shut", "no face while choosing while shut does not choose".
- [ ] **Listening keeps working after the phone speaks.** Error 5 after each pause dropped the working on-device recogniser for the normal one, which then failed with error 13 every 5 s. Code: `listen/Listener.kt`.

How to test on the phone: look at the screen normally for a minute with "I need water" lit; nothing should be said. Then close both eyes about half a second, ten times; each one should say it. Ask a question aloud after each; the heard text should show. Tap "Save logs" and send the file.

## Choosing reliably (goal: "I need water" ten times in a row, no wrong card, no missed blink)

- [ ] **Look down is off by default.** It failed about eight phone runs in a row. With it off, a look up moves to the next card and wraps round, so every card can still be reached. It stays as a switch on the eye check page ("Looking down also moves"); the look down sliders show only when it is on. Calibration still measures it. Code: `setup/Tuning.kt` (`lookDown`, `activeGaze`), `conversation/ConversationController.kt` (`upMovesNext`), `MainActivity.kt`. Test: `EyeModeTest` "with looking down off a look up moves to the next card and wraps round".
- [ ] **Two modes, switch on the eye check page.** Eyes (default): look up or wink to move, close both eyes to choose. Blink only: the highlight moves on a timer and a blink chooses; "Scan speed" sets the timer (0.6 to 3 s per card). Code: `ui/EyeCheckScreen.kt` (`TuningSliders`), `scan/Scanner.kt`.
- [ ] **Old 0.6 s shortest blink cleared.** The practice round saved 85% of a practice blink as the shortest blink, up to 600 ms, and the saved value beat the 0.2 s default. Phone log, this iQOO, 01:13 run: `01:30:06.667 blink ignored 595 ms, shorter than 600 ms` (13 closes ignored in that run). Settings saved before this build with a shortest blink over 400 ms go back to the default once. The practice round now sets half the practice blink, 200 to 400 ms. Code: `setup/Tuning.kt` (`TuningStore.load`, `VERSION`), `practice/PracticeController.kt`. Test: `PracticeControllerTest` "a long practice blink never sets the shortest blink above 400 ms".
- [ ] **Blink accuracy on the stats screen** (PRD S1) comes from the practice round: stars caught out of tries. A try is any close of 150 ms or more (normal blinks are about 100 to 150 ms); one that ends without catching a star is a miss and is logged ("practice: missed try"). Shows "-" before the practice round. Code: `practice/PracticeController.kt` (`accuracyPercent`), `MainActivity.kt`. Tests: `PracticeControllerTest` "accuracy counts caught stars against missed tries", "normal quick blinks are not counted as missed tries".
- Double blink is now off by default (see "Picks only from a held close" below).

How to test on the phone (each mode):
1. Open the app, let calibration finish. Tap the eye button (top right), check the mode switch, tap "Back to talking".
2. Eyes mode: with "I need water" lit, close both eyes about half a second. After it is spoken, the highlight returns to the first card. Repeat ten times; count wrong cards and missed blinks.
3. Blink only mode: switch it on the eye check page; set scan speed (1.5 s is a good start). Blink when "I need water" lights. Ten times.
4. Tap "Save logs" on the eye check page after each mode and send the file.

## Eye graph for the demo

- [ ] **One tap from the main page.** The eye button in the top bar opens the eye check page; the practice round moved to the star button. "Back to talking" returns. Code: `ui/ConversationScreen.kt`, `MainActivity.kt`.
- [ ] **Readable from two metres.** Large OPEN / SHUT / BETWEEN word in the line colours, a 200 dp graph of both eyes with thick shut and open lines, a 140 dp gaze box, and the last four blink, look and choose decisions in large type. Looks now show in the on-screen lines too. Code: `ui/EyeCheckScreen.kt` (`EyeState`, `Decisions`), `log/EventLog.kt`.

## Tutorial onboarding and accuracy fixes

The app now launches with an interactive practice round on first use, teaches the user how to step and blink, catches 3 stars, and auto-calibrates the eye thresholds to their personal eyes. Inaccurate automatic card hopping and premature selections have been resolved.

- [x] **Tutorial practice round (PRD F6).** First launch opens `PracticeScreen`: 3 star targets, catch stars with deliberate blinks (~0.4 s), personalized calibration of `closedBelow` and `openAbove`, spoken audio feedback, and transition to ConversationScreen. The eye icon on ConversationScreen returns to practice anytime. Code: `ui/PracticeScreen.kt`, `practice/PracticeController.kt`, `MainActivity.kt`.
- [x] **Asymmetric eyelid detection.** Average aperture `(left + right) / 2` and combined recovery ensure users with eyelid asymmetry (e.g. one lid closing to 0.50 and the other to 0.31) trigger deliberate blinks reliably without single-frame flicker cutoffs. Code: `blink/BlinkDetector.kt`.
- [x] **Yaw jitter tolerance during eye closure.** When eyes shut, MediaPipe facial mesh points contract and yaw temporarily jumps up to -50°. In-progress closures are no longer cancelled by transient yaw spikes while the face remains detected. Code: `blink/BlinkDetector.kt`.
- [x] **Reading glance isolation in GazeStepper.** Gaze stepping now requires eyes to look towards or above the top bezel (`gaze.y < 0.20f`) in addition to `-dy >= lookStrength`, preventing glances between cards on the screen from triggering unintended card jumps. Code: `scan/GazeStepper.kt`.
- [x] **Stale preferences guard.** `TuningStore` upgrades hyper-sensitive legacy values (`minBlinkMs < 350` or `lookStrength < 0.35`) to recommended stable defaults. Code: `setup/Tuning.kt`.
- [x] **Live transcript mirrored to laptop via Office Kit (PRD S2).** High-contrast, large-font full-screen transcript view accessible via top bar icon button. Renders real-time visitor speech (22sp) and speaker eye-blink replies (26sp pink card), live listening status pill, and session sentence counters. Zero internet used. Code: `ui/TranscriptScreen.kt`, `conversation/ConversationController.kt`, `MainActivity.kt`.
- [x] **Frequent phrases in-session learning (PRD S3).** Dynamically learns sentences chosen by the speaker and prioritizes them to earlier phrase-bank pages and LLM top-up/fallback candidates, reducing eye movements required for habitual needs. Code: `conversation/Board.kt`, `conversation/ConversationController.kt`, `suggest/ModelSuggestionEngine.kt`.

## Moving on its own, and winks behind a switch (owner report)

- [ ] **Winks are off unless switched on.** Eye check screen: "Winks move too (left wink down, right wink up)". Saved on the phone. Off by default, owner decision. Code: `setup/Tuning.kt` (`winks`), `conversation/ConversationController.kt`, `ui/EyeCheckScreen.kt`. Test: `EyeModeTest` "winks do nothing while the setting is off".
- [ ] **No moves right after a pause in readings.** Phone 02:38:48, 02:38:54, 02:39:37, 02:39:45, 02:39:52: a "look up" fired 0.4 s after every spoken phrase, because the look from before the speech was still remembered. Coming back from Practice fired a "look down held 10474 ms" (02:39:30). Now after 0.4 s with no readings, nothing moves for 1 s while the resting point is learned again. Code: `scan/GazeStepper.kt` (`STALE_AFTER_MS`). Test: `GazeStepperTest` "a look left over from before a pause in readings does not step".
- [ ] **Eyes away from rest for 2.5 s become the new rest.** Phone 02:39:24: the resting gaze moved from 0.6 to -0.1 and looks up kept firing on their own (02:39:32 to 02:39:52). Coming back to the old rest within 6 s restores it without a step. The log says "new resting point" and "back at the old resting point". Code: `scan/GazeStepper.kt` (`RECENTRE_AFTER_MS`). Tests: `GazeStepperTest` "eyes resting somewhere new become the rest", "holding a look steps once and coming back does not step" (renamed on purpose: the held look now becomes the rest after 2.5 s, and coming back restores the old one).

## Winks moving up and down (owner report)

Phone run 02:29:33 to 02:29:52: every wink registered, but extra "look up" steps came in between. While one eye was shut or half shut (0.5 against 0.9), the gaze reading sat at 0.28 against a rest of 0.60, as far as a real look up.

- [ ] **Looks stop while one eye reads lower than the other**, and for 0.8 s after. "Lower" means 0.2 beyond this person's usual difference between the eyes, so eyes that always read a bit apart still work. Code: `blink/WinkDetector.kt` (`oneEyeLower`), `conversation/ConversationController.kt`. Tests: `EyeModeTest` "an eye left half shut between winks is not a look up", "looks still work when one eye always reads lower"; `WinkDetectorTest` "one eye half shut reads as one eye lower".
- [ ] **A look up to 1 s before a wink is taken back.** Phone 02:29:49: the eye starting to close fired a "look up" 0.4 s before the right wink. The log says "look undone". Test: `EyeModeTest` "a look fired just before a wink is taken back".

## Winks: same fixes (owner report)

- [ ] **Looks pause after a wink** (now 0.8 s after the eye is back level, see above). Phone 02:23:51: each left wink was followed about 0.45 s later by a "look up" as the eye reopened, undoing it. Code: `scan/GazeStepper.kt` (`pauseUntil`), `conversation/ConversationController.kt` (`AFTER_WINK_MS`). Test: `EyeModeTest` "the eye reopening after a wink is not a look up".
- [ ] **Wink hysteresis:** after a wink, both eyes must be open together for 0.2 s before the next counts, and winks are at least 0.6 s apart, so an eye flickering open mid-wink counts once. Code: `blink/WinkDetector.kt`. Test: `WinkDetectorTest` "an eye flickering open mid wink counts once".

## Looks up: same fixes (owner report)

- [ ] **The way back from a look is ignored for 1 s** (was 0.7 s). Phone 02:22:47 look down, 02:22:48.7 a "look up": the eyes coming back took 1.3 s. Code: `scan/GazeStepper.kt` (`REBOUND_MS`).
- [ ] **For 1 s after the face comes back, only the resting gaze is learned, quickly; nothing moves.** Phone 02:23:30.9 face found, 02:23:31.4 a "look up 0.56": the rest had been taken from the first, still-moving frame. Code: `scan/GazeStepper.kt` (`SETTLE_MS`). Test: `GazeStepperTest` "just after the face comes back nothing steps while the rest is learned".
- Tests updated on purpose: the rebound test and the `EyeModeTest` helpers pause 1 s between opposite looks, longer than the new window.

## Looks with hysteresis (owner report: down "too fast, hard to register, extra steps")

- [ ] **A look starts at its line and ends only below half of it.** With one line both ways, a single wobbly frame under the line restarted the 0.35 s hold (hard to register), and hovering near the line stepped twice (extra steps). Code: `scan/GazeStepper.kt` (`REARM`). Tests: `GazeStepperTest` "a wobble under the line during a look down does not stop it registering", "hovering around the line steps only once".
- [ ] **Look up line at least 0.2.** The 02:14 calibration set it at 0.15 and resting wobble of 0.16 to 0.24 read as looks up (three in 1.4 s at 02:16:03), undoing looks down; real looks up measured 0.4 to 0.7. Code: `setup/Calibration.kt` (`MIN_UP_LINE`).
- Merged with the demo-reliability changes (#32). The repeated-looks-down fix had not reached main because of that merge; Atul's absolute "look up must reach gaze 0.2" rule is replaced by the relative 0.2 floor, which works at any phone height.

## Repeated looks down (owner report)

- [ ] **Looking down many times in a row keeps working.** Owner: centre to down, many times, and sometimes it stopped going down. During each look down the iris resting point crept 1% a frame toward the look, so over several looks it moved down and later looks no longer reached the line. The creep is gone; the resting point follows only near rest (within 3/4 of the line) and more gently (3% a frame). Code: `scan/GazeStepper.kt`. Test: `GazeStepperTest` "many looks down in a row all register" (8 of 8).

## Face coming back (owner report)

- [ ] **The resting gaze is taken afresh when the face comes back.** Owner: after moving the face out of the camera, things stopped working. The old resting point stayed, so coming back in a new position made looks fire by themselves or never reach the line until it slowly re-adjusted. Now it resets on face found; the calibrated look lines stay. Code: `scan/GazeStepper.kt` (`forgetRest`), `conversation/ConversationController.kt`. Test: `EyeModeTest` "coming back in a new position does not move by itself and looks still work".
- Note from the 02:00 phone screen: with the phone lying flat the camera sees the ceiling and only the top of the head, so "Looking for you" is correct then; the face must be in view to be found again.

## Winks after calibration (owner report)

- [ ] **Winks no longer use the lid gap check.** After calibrating with glasses the gap shut line was 0.10 (full closes 0.08), but a winking eye's gap stayed at about 0.18 (01:56:48: left 0.33, right 0.98, left gap 0.18), so no wink registered. A wink is now the winking eye under the shut line, the other above the open line, at least 0.35 apart. Looking down lowers both eyes together and is not a wink. Code: `blink/WinkDetector.kt`. Tests: `WinkDetectorTest` "a wink counts even when the lid gap of the winking eye stays shallow", "both eyes lowered together is not a wink".

## Double blink chooses (owner report: glasses)

- [ ] **Two quick closes within 0.8 s choose**, each at least 60 ms, however short. In the 01:44 run with glasses winks worked (13 in a row) but six closes of 109 to 168 ms were ignored as shorter than 0.2 s; with glasses the lid gap only reached 0.09 to 0.14 when closed. One close of 0.2 s or more still chooses. Code: `blink/BlinkDetector.kt` (`doubleBlink`), `setup/Tuning.kt`. Tests: `BlinkDetectorTest` "two quick closes in a row choose", "one quick close alone does not choose", "two quick closes far apart do not choose".
- Glasses: lens reflections make the eye points less precise; no software change removes them. Tilting the phone so room lights do not reflect in the lenses helps.

## Wink and blink fixes from the 01:35 phone run

- [ ] **Quick deliberate closes count: shortest blink 0.3 s to 0.2 s.** The owner's closes lasted 42 to 239 ms and every one was ignored ("ignored 164 ms, shorter than 300 ms"). Normal blinks run about 100 to 150 ms. Code: `setup/Tuning.kt`.
- [ ] **A wink moves exactly once.** At 01:35:38 a left wink also fired a "look down" 0.26 s earlier, because closing one eye shifts the gaze and iris readings. Looks now pause while either eye reads shut (eye-open value and lid gap), which a wink does and a look down does not. Code: `blink/BlinkDetector.kt` (`eitherEyeShut`), `conversation/ConversationController.kt`. Tests: `EyeModeTest` "a wink moves exactly once even when it shifts the gaze", `BlinkDetectorTest` "one eye shut counts for either eye shut...".
- [ ] **Calibration no longer fails on a weak look up.** It failed with "Look up not seen" and left the blink lines unset. One good look up of the two is now enough, and if none is seen the current look up line is kept while everything else is still set. Code: `setup/Calibration.kt`. Tests: `CalibrationTest` "one good look up is enough", "no look up keeps the current look line and still sets the blink lines" (replaces "no look up fails and says so", on purpose).

## Wink control and easier look up (owner request)

- [ ] **Left wink = highlight down, right wink = highlight up, both eyes shut = choose.** One eye below the shut lines (eye-open value and lid gap) while the other stays above the open line, held 0.3 s; fires once per wink while the eye is still shut. A blink that shuts one eye a frame early is not a wink, and a half-shut other eye is not either. Works alongside looking up and down. Left and right are the person's own. Code: `blink/WinkDetector.kt`, `conversation/ConversationController.kt`. Tests: `WinkDetectorTest`, `EyeModeTest` "a left wink moves down and a right wink moves up", "after winking, closing both eyes chooses".
- [ ] **Look up needs less movement:** 35% of the smaller measured look up (was half); default 0.3 before calibration. Code: `setup/Calibration.kt` (`LOOK_SHARE`), `setup/Tuning.kt`.

## Steady cursor and highlight (owner report: "shaking")

- [ ] **Gaze readings are averaged before a look is judged** (newest frame weighted 0.35, about 0.1 s of lag). In the 01:16 phone run single-frame wobble kept crossing the up line (0.30 to 0.34 against 0.28), so the highlight stepped every few seconds without a real look. Code: `scan/GazeStepper.kt` (`SMOOTHING`).
- [ ] **Steps are at least 0.6 s apart.** Code: `scan/GazeStepper.kt` (`MIN_STEP_GAP_MS`).
- [ ] **The gaze box dot is steady:** it draws the same averaged gaze and iris values the steps use, not each raw frame. Code: `ui/EyeCheckScreen.kt`, `MainActivity.kt`.
- Test: `GazeStepperTest` "single frame wobble across the line does not step".
- [ ] **Looking down takes a smaller share of its own, smaller range.** Owner: there is much less room to push the eyes down than up. Phone runs: iris drop about 0.03 down, gaze 0.5 to 0.6 up. The 0.02 floor needed two thirds of the downward range; now the look down line is 30% of the measured look down with a 0.01 floor (default 0.012). Code: `setup/Calibration.kt` (`DOWN_SHARE`, `MIN_IRIS_DOWN_LINE`), `setup/Tuning.kt`.

## No more moving on its own (owner report)

- [ ] **The resting iris is followed as it drifts.** In the 00:51 phone run the iris moved between -0.036 and +0.005 with the eyes at rest; the rest point only followed within half the 0.017 line, so once the eyes drifted further it stayed behind and resting eyes read as a look down again and again (five steps down in 7 s, 00:51:40 to 00:51:47). It now follows across the whole band below the line, and creeps 1% per frame during a look down. Code: `scan/GazeStepper.kt`. Test: `GazeStepperTest` "a drifting resting iris is followed instead of stepping again and again".
- [ ] **The look down line is at least 0.02**, above the resting drift; calibration can only set it higher. Default 0.02. Code: `setup/Calibration.kt` (`MIN_IRIS_DOWN_LINE`), `setup/Tuning.kt`.

## Blinks restored: the iris no longer blocks a close (owner report)

- [ ] **Removed "a clear look down is not a close".** After the 0.15 cap, closes still did not start: in the 00:46 phone run a real close (eye-open 0.39/0.42, lid gap 0.07/0.10) read an iris drop of only +0.039 from rest, inside the look down range, so it was still taken for a look. The lid gap check alone separates them in the same run: looks down 0.15 to 0.24, closes 0.07 to 0.10, gap shut line 0.13. Whether the eyes are shut is now the eye-open value plus the lid gap only, as in the builds where blinking worked. Code: `blink/BlinkDetector.kt`, `conversation/ConversationController.kt`.
- [ ] **Shut eyes never move the highlight**, whatever the iris reads, so a blink cannot also step down. Code: `scan/GazeStepper.kt`.
- Tests: `EyeModeTest` "a blink chooses when shut eyes read inside the iris look down range" (the 00:46:18 numbers); `GazeStepperTest` "shut eyes never step even when the iris reads in the look down range". The BlinkDetector test for the removed rule was deleted on purpose.

## Blinks blocked by the iris look down (owner report)

- [ ] **Shut eyes are not a look down.** With the eyes closed the iris reads far below any real look (phone log 00:42:18: 0.31, against 0.01 to 0.08 for looks down). The rule "a clear look down is not a close" therefore treated every close as a look down and no blink started. A look down is now an iris drop from the look-down line up to 0.15; beyond that is shut eyes. Code: `scan/GazeStepper.kt` (`irisLooksDown`, `IRIS_SHUT_ABOVE`). Tests: `GazeStepperTest` "shut eyes read as a huge iris drop are not a look down", `EyeModeTest` "a blink still chooses when the iris look down is on".

## Looking down never switched off

- [ ] **Iris look down is on by default (0.012).** On the phone a new install wipes the saved settings, and calibration at start failed with no face in view ("Face not seen while looking at the screen"); the defaults had the iris look down off, so looking down stopped working. Calibrated runs gave 0.008 to 0.011. Code: `setup/Tuning.kt`.
- [ ] **A frame without an iris reading falls back to the blendshape look down** instead of doing nothing. Code: `scan/GazeStepper.kt`. Test: `GazeStepperTest` "with the iris line set but no iris reading, the blendshape look down still works".

## Steadier looks (owner report: "too fast up and down")

- [ ] **The way back from a look is not a look.** In the 00:31 phone run every look down was followed by an "up" 0.4 to 0.8 s later (00:31:56 down, 00:31:56.6 up, 00:31:57.3 down, 00:31:57.7 up...): the eyes passing rest on the way back. For 0.7 s after a step, a look the other way is ignored. Code: `scan/GazeStepper.kt` (`REBOUND_MS`).
- [ ] **Holding a look no longer moves the resting point.** After 3 s of looking one way the rest jumped there ("new resting gaze 0.75", then "-0.19"), so normal gaze afterwards read as a look the other way. The rest now only follows slowly while the eyes are near it.
- [ ] **A look must last 0.35 s** (was 0.25 s), so quick glances do not step.

Unit tests:
- `GazeStepperTest`: holding a look steps once and does not move the rest; coming back from a look is not a look the other way; a real opposite look after the rebound time still steps. The old test "holding one way for long becomes the new rest" was replaced on purpose.
- `EyeModeTest`: helpers pause 0.8 s between looks, longer than the rebound guard.

## Look down read from the iris (owner report)

- [ ] **Looking down moves the highlight with a small eye movement, like looking up.** The owner found up responsive and down dead. Phone run at 00:21: resting gaze 0.52 to 0.71 down, the furthest look down 0.78, so the "look down" blendshape had about 0.1 of room, the same as its wobble at rest, and calibration turned looking down off (reach 0.09). The lids also drop when looking down (eye-open 0.45/0.52, lid gap 0.14 at 00:21:25), close to a real close. Now looking down is measured from where the iris centre sits between the two eye corners (`FaceMesh.irisDrop`): the corners do not move with the lids, the iris does move with the eye. Calibration measures the iris drop when looking down and the line is half of it, like up. Up is unchanged. Code: `eye/FaceMesh.kt`, `eye/EyeReader.kt`, `scan/GazeStepper.kt`, `setup/Calibration.kt`.
- [ ] **Looking down needs less movement than looking up.** The owner had to take the eyes nearly to the bottom: people look down as far as they can when calibration asks, so half of that was still most of the way. The look down line is now 35% of the measured look down (up stays at half), and a "Look down (iris)" slider on the eye check screen tunes it live. Code: `setup/Calibration.kt` (`DOWN_SHARE`), `ui/EyeCheckScreen.kt`.
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
- [ ] **The lid gap line comes from the held close, not one squeezed frame.** Calibration set the gap line just above the deepest single frame of the "Close your eyes" steps, and a hard squeeze reads 0.00 to 0.03 for a frame or two. On the phone the line came out anywhere from 0.045 to 0.21 within one hour. In the 05:04 run it was 0.09 while held closes read 0.10 to 0.15: at 05:05:15 both eyes read 0.27/0.30 (as deep as the calibrated close) and did not count, and a help hold at 05:06:37 only counted from 1.2 s in, so it never reached 2 s. Now each close step gives its typical (median) gap, and the deeper of the two sets the line. Code: `setup/Calibration.kt`. Tests: `CalibrationTest` "a squeezed frame or two does not set the lid gap line" (fails on the old code).
- [ ] **The lid gap is measured only on frames that read shut.** The median above took in the whole step, and "Close your eyes" takes about 1.3 s to say, so each step starts with open eyes. In the 05:36 run that gave closed 0.28 against open 0.29, the gap check switched off, and open eyes looking down at the phone (left 0.70 to 0.76, right 0.51 to 0.59, lid gap 0.20 to 0.25) counted as shut and picked five cards (05:37:58 to 05:38:54). Now the closed gap is the median over the close-step frames whose eye-open value is below the shut line; a half-done close with no such frames is skipped. Code: `setup/Calibration.kt`. Tests: `CalibrationTest` "open frames before the person closes do not set the lid gap line" (fails on the median of the whole step).

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

- [ ] **Choose while the eyes are still shut.** Before, a close only chose when the eyes opened again, so every choice waited the whole close plus the reopen. Now it chooses the moment the close reaches the shortest-blink time; the reopen does nothing more. Code: `blink/BlinkDetector.kt` (`chooseWhileShut`), `setup/Tuning.kt`. Now off by default (see "Help hold says no card" above): choosing while shut said the lit card 0.4 s into every help hold. It stays in the code as `chooseWhileShut`.
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
- [ ] **Practice round** (F6). Built: see "Tutorial onboarding and accuracy fixes" and "Choosing reliably" above. Opened with the star button.

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

- [ ] **Help alarm** (F8). Eyes held shut for 2 s: a beep says the hold is done. Open the eyes and close them again within 5 s, as a blink or a longer close: the phone's alarm sound plays on the alarm channel, looping, at full volume, and the designed help screen fills the display with the last thing said. "I am here" (or back) stops it and returns to the cards; the speaker button silences it. The confirm blink never picks a card, and the hold itself never counts as a blink. Works even while the phone is speaking. Code: `help/HelpTrigger.kt`, `help/HelpAlarm.kt`, `conversation/ConversationController.kt`, `ui/HelpAlertScreen.kt`.
- Why two steps: a person resting with their eyes closed should not set it off alone.
- [ ] **Stats screen** (S1). The designed screen, opened with the stats button (bar chart). Average reply time and model speed (tokens per second) over this session's model answers, replies written, the phone's temperature (battery sensor), model name and runtime, session length, sentences spoken. Updates every second. Blink accuracy comes from the practice round ("-" before it). Code: `stats/PitStats.kt`, `ui/StatsScreen.kt`, `MainActivity.kt`.

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
- App icon (owner request, the default Android icon was in use): a speech bubble with a glowing green eye on a dark gradient, with a one-colour layer for themed icons. Code: `res/mipmap-anydpi/ic_launcher.xml`, `res/drawable/ic_launcher_*.xml`. For the teammate to replace if the design has its own.
- Main page top bar: a fourth round button (eye icon) opens the eye check page; the practice round uses the star icon.

## CI checks

Run on every push and pull request (`.github/workflows/ci.yml`). The `main` ruleset requires all three to be green before a pull request can merge.

| Check | What it catches |
|---|---|
| Unit tests | Logic that broke (`./gradlew testDebugUnitTest`). Also fails if a phone log is committed (`phone-logs/` or any `.log` file) |
| Android lint | Common Android mistakes (`./gradlew lintDebug`) |
| Build APK | Code that does not compile. Also fails if the APK asks for INTERNET. Uploads `outspoken-debug-apk` |

## Next

1. Owner runs "I need water" ten times in a row in both modes on the iQOO and sends the counts and logs. Copy the Gemma `.litertlm` file to the phone first ("Choose model file"); the last run had no model.
2. Fix what the logs show, then mark M1 and the practice round.
3. Sentence builder (plan, starts after step 1):
   - A "Write my own" card opens a builder: the sentence so far where "Heard" sits, four next-word cards (one to three words each), then Speak, More words, Delete last word and Exit, all reachable by eyes.
   - A built-in list of common next words shows at once; Gemma's four suggestions replace it when ready, and the list stays if the model fails.
   - Speed: ask for about 32 tokens instead of 96, keep one model session open for the whole sentence, and work out the next words for the lit card while the person is still choosing.
   - Each word step logs the model reply time.
   - Open question for the owner: add "Starts with..." letter groups, or words only first.
