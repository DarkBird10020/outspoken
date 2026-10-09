# OUTSPOKEN: Product Requirements

Working name: Outspoken. A phone app that lets a person who cannot move or speak hold a spoken conversation using only their eyes. Fully on-device.

This file is the build brief. Read it fully before writing code.

## 1. One sentence

We built Outspoken, which lets people who cannot move or speak talk out loud by blinking at a phone, with no internet.

## 2. Who it is for

- The speaker: a person who is awake and aware but cannot speak or use their hands. Examples: a patient on a ventilator, a person with ALS, a person paralysed after a stroke.
- The listener: a family member, nurse or caregiver at the bedside.

Problem in plain words: the speaker can think clearly but cannot say "I am in pain" or "I need water". Dedicated eye-tracking devices cost a great deal. Existing phone apps offer fixed phrase lists that need many selections, or letter-by-letter spelling that is slow and tiring.

Numbers for the pitch are not verified yet. Find one sourced figure before pitching (for example, how many people in India live with ALS, or how many ICU patients are ventilated and unable to speak). Do not invent a number.

## 3. What makes it different

Existing tools (Google Look to Speak, blink-to-Morse projects, blink-to-select phrase boards) make the user pick from fixed phrases or spell letters.

Outspoken does three things together that none of them do:
1. It listens to what the other person says and offers replies that fit the question.
2. A language model on the phone writes those replies fresh each turn, so one blink can say a full, relevant sentence.
3. Everything runs on the phone. The app has no internet permission.

## 4. The core loop

1. Listener speaks. The phone hears the question (on-device speech recognition).
2. The on-device language model writes four short replies the speaker might want to give.
3. The four replies appear as large cards. A highlight moves from card to card.
4. The speaker blinks on purpose when the right card is highlighted.
5. The phone says that sentence out loud.
6. The sentence joins the conversation history, and the loop repeats.

If the listener says nothing, the model offers replies based on the conversation so far and the time of day.

## 5. Features

### Must have (the demo path)
- F1. Blink detection from the front camera. An intentional blink is eyes shut for roughly 0.3 to 0.9 seconds. Normal fast blinks are ignored.
- F2. Scanning selection. Four reply cards plus two fixed cards: "More options" and "Yes / No". The highlight moves about every 1.2 seconds. The speed is adjustable.
- F3. Speech output using the phone's offline text-to-speech.
- F4. On-device language model that returns four replies, each under about eight words, first person, plain language.
- F5. Instant fallback. A small built-in phrase bank (yes, no, pain, water, toilet, too hot, too cold, call the nurse, thank you) shows at once while the model is thinking, and is used if the model fails.
- F6. Calibration, about 20 seconds: measure this person's eye-open level and normal blink length, then set the thresholds. Include a practice round ("blink when the star is highlighted"). An existing app was criticised for having no instructions and no training mode. Ours must have both.
- F7. Listening. On-device speech recognition turns the listener's question into text for the model. Fallback: the listener types the question, or taps a quick-topic chip.
- F8. Help alarm. Eyes held shut for 2 seconds, then a confirm blink, triggers a loud alarm and a full-screen alert.

### Should have (after the must-haves pass ten runs in a row)
- S1. Stats screen: model response time, tokens per second, blink accuracy from the practice round, session length. These are the numbers shown to judges.
- S2. Listener view mirrored to the laptop through Office Kit: large transcript of the conversation.
- S3. The model learns the speaker's frequent phrases within a session and offers them sooner.

### Later, only if time remains
- L1. Hindi and Kannada speech output.
- L2. Spelling mode for words the model does not offer.
- L3. Looking left or right as extra inputs.

### Not building
- Control of other apps or the whole phone.
- Any cloud feature, account or sync.
- Any medical measurement or diagnosis.

## 6. Architecture

Single Android app. Kotlin, Jetpack Compose, one activity. No INTERNET permission in the manifest.

| Module | Job | Built with |
|---|---|---|
| Eye reader | Camera frames in, eye-open values out | CameraX + ML Kit Face Detection (bundled model, classification on) |
| Blink detector | Turns eye-open values into events: intentional blink, long hold | Our own state machine with calibrated thresholds |
| Scanner | Moves the highlight, handles selection | Compose UI + a timer |
| Suggestion engine | Conversation in, four replies out | On-device Gemma through LiteRT-LM, behind an interface so the runtime can be swapped |
| Listener | Microphone in, question text out | Android on-device speech recognizer |
| Speaker | Text in, speech out | Android TextToSpeech, offline voice |
| Conversation store | Keeps the turns for this session | In memory |
| Pit stats | Records timings and counts | Simple logger |

Rules for the model:
- Prompt it for exactly four replies as a JSON list. Reject and retry once if the format is wrong, then fall back to the phrase bank.
- Target: replies on screen within 2 seconds. Measure it.
- Load the model once at startup and keep it loaded.
- Pick the largest model that meets the 2-second target on the iQOO 15.

Rules for blink detection:
- Use both eyes together. A wink does not count.
- Require the face to be found and roughly facing the camera. If the face is lost, pause the scanner and show "Looking for you".
- Phone stands on a table or holder at arm's length. Do not design for a hand-held phone.

## 7. Event rules this build must follow

From the printed rulebook:
- Original work only. All code is written inside the event window. Start from an empty repo.
- Open-source libraries and frameworks are allowed with attribution. List each one in the README. Do not copy code from existing blink apps. Read them for ideas only.
- The app must run and be pitched on the iQOO phone.
- A local or open-source model at the core earns extra credit. The rulebook's tip to win says to run everything on-device, backend included, and that the highest on-device builds are preferred for the final pitches.
- HackTracker records on-device inference calls, tokens and thermals. Never tamper with it. Make sure the model runs through a runtime HackTracker can see (ask an organiser which ones).
- Red Light hours: no direct laptop use. Work on the phone, or reach the laptop only through Office Kit.
- Submit the repo and demo assets on the Reskilll platform before the hard cutoff.
- Scoring: end product 30, novelty 20, creative phone use 15, technical depth 15, Office Kit 10, demo 10.
- Track: Community App. Open Innovation if an organiser advises it.

## 8. Method this build must follow (hackathon guide)

1. The demo works from start to finish. One user, one flow, one wow moment. Build the demo path first, ugly, then polish. Run it ten times in a row before calling it done. Seed data so nothing is typed live.
2. Public GitHub repo, an installable APK, and a README with the one-line pitch, a screenshot or GIF, and setup steps. No keys in the repo.
3. Talk to judges and mentors early. Show the rough version, ask what would impress them, build that, and tell them you did.
4. Record the demo video while it works. Hook in five seconds, real product, captions, under the limit.
5. Make every rubric item visible in the demo and say the rubric words in the pitch.

## 9. Milestones

Do them in order. Each has a pass test. There are three scored evaluation rounds, so something must work at each one.

**M0. Skeleton.** Empty repo, first commit, Compose app running on the iQOO, model file on the phone, camera preview with live eye-open numbers on screen.
Pass: numbers move when you blink.

**M1. Blink to speech with fixed phrases.** Blink detector, scanner, phrase bank, speech output.
Pass: say "I need water" by blinking, ten times in a row. This is a complete demo with no model. Ready for evaluation round 1.

**M2. Model replies.** Suggestion engine wired in, with fallback.
Pass: after choosing "I am in pain", the next four cards are relevant (for example where it hurts). Replies appear within the target time.

**M3. Listening and calibration.** Listener, calibration, practice round.
Pass: a stranger asks an unscripted question aloud and gets a sensible blinked answer in under 20 seconds, in airplane mode. Ready for evaluation round 2.

**M4. Alarm, stats, laptop view.** F8, S1, S2.
Pass: all three shown in one run.

**M5. Freeze and ship.** Code freeze two hours before the cutoff. README, APK, video, submission. Three timed pitch rehearsals.

Work split for phone-only hours: threshold tuning, prompt tuning, phrase bank, practice-round testing, timing measurements and rehearsals all happen on the phone. Save project setup, dependency changes and large refactors for laptop hours.

## 10. The 90-second demo

1. 0:00 One teammate sits silent with hands flat on the table. The other says: "He cannot move or speak. Ask him anything."
2. 0:10 A judge asks a question aloud, for example "Are you in pain?"
3. 0:15 Four replies appear. The highlight moves. He blinks. The phone says: "Yes, my back hurts."
4. 0:35 The judge asks a follow-up. He answers again by blinking.
5. 0:55 He holds his eyes shut. The help alarm sounds.
6. 1:10 Swipe down to show airplane mode. Open the stats screen: response time, tokens per second, blink accuracy.
7. 1:20 Point at the laptop showing the transcript through Office Kit.

Backup: a recording of a clean run, open in another tab. If speech recognition struggles in the noise, the judge taps a topic chip instead.

## 11. Three-minute pitch outline

- 0:00 The one sentence.
- 0:15 The problem: one person, one story, one sourced number.
- 0:45 The live demo above.
- 2:00 How it works in two sentences, naming the on-device model, the camera, and Office Kit.
- 2:30 Who uses it tomorrow: ICU wards, home caregivers, ALS support groups.
- 2:50 The one sentence again.

## 12. Risks

| Risk | Sign | Response |
|---|---|---|
| Blink detection is unreliable in venue light or with glasses | Missed or false selections | Calibrate at the demo table. Raise the hold time. Test with and without glasses early |
| Model is too slow | More than 2 seconds per turn | Smaller model, shorter prompt, show the phrase bank first |
| Model output is off-topic or badly formatted | Odd cards | Stricter prompt, retry once, fall back to the phrase bank |
| Speech recognition fails in noise | Wrong question text | Topic chips and typed input |
| Offline speech voice is missing on the loaner phone | Silence | Check in M0. Install the voice during laptop hours |
| HackTracker cannot see our model runtime | No inference credit | Ask an organiser in the first hour. Swap the runtime behind the interface |
| Scope creep | New features before M3 passes | Refuse them. The must-have list is the product |

## 13. Honest limits to state if asked

- It is a communication aid, not a medical device.
- It needs the speaker to control blinking and to see the screen.
- Replies are suggestions. The speaker always chooses what is said.
