package com.outspoken.tutorial

import java.util.Locale

/** Who a lesson is written for. */
enum class Audience(val label: String) {
    Everyone("Start here"),
    Speaker("For the person speaking"),
    Carer("For the carer"),
}

/** A hands-on round the lesson ends with, run with the live camera. */
enum class Practice(val label: String) {
    Blink("Practise blinking"),
    Move("Practise moving"),
    Help("Practise calling for help"),
}

/** A page of the app a lesson can open, so the carer can try what was just explained. */
enum class Destination(val label: String) {
    Calibrate("Calibrate now"),
    EyeCheck("Open the eye check"),
    Settings("Open Settings"),
    Profile("Open the profile"),
    Models("Open Model and logs"),
    Stats("Open session stats"),
    Transcript("Open the transcript"),
}

data class LessonStep(val title: String, val body: String)

/** [id] is saved when the lesson is finished, so it must never change. */
data class Lesson(
    val id: String,
    val title: String,
    val summary: String,
    val audience: Audience,
    val steps: List<LessonStep>,
    val practice: Practice? = null,
    val opens: Destination? = null,
)

/** How the eyes control the app on this phone right now, so the lessons describe what the person will see. */
data class EyeControls(
    val moveByEyes: Boolean = true,
    val lookDown: Boolean = false,
    val winks: Boolean = false,
    val handSigns: Boolean = false,
    val scanMs: Long = 1_200,
)

object Lessons {

    fun all(controls: EyeControls = EyeControls()): List<Lesson> = listOf(
        welcome,
        setUp,
        mainPage(controls),
        blink,
        move(controls),
        sayAnything(controls),
        help,
        hands(controls),
        listening,
        language,
        profile,
        sos,
        eyeCheck,
        tuning(controls),
        model,
        statsAndTranscript,
        logs,
    )

    /** The first lesson in the list not yet finished, for "Continue"; null when all are done. */
    fun nextUnfinished(lessons: List<Lesson>, finished: Set<String>): Lesson? =
        lessons.firstOrNull { it.id !in finished }

    private val welcome = Lesson(
        id = "welcome",
        title = "Welcome to Outspoken",
        summary = "What the app does and how these lessons work.",
        audience = Audience.Everyone,
        steps = listOf(
            LessonStep(
                "A voice for people who cannot speak",
                "Outspoken lets a person who cannot move or speak hold a spoken conversation with their eyes. " +
                    "The phone listens to the visitor, offers four short replies, and says the one the person chooses out loud.",
            ),
            LessonStep(
                "Everything stays on this phone",
                "The camera, the microphone, the reply model and the voice all run on the phone itself. " +
                    "Nothing is sent to the internet, and it works in airplane mode. What is said is kept in memory and is gone when the app closes.",
            ),
            LessonStep(
                "Two kinds of lessons",
                "Lessons for the person speaking teach the eye controls, and each ends with a practice round using the camera. " +
                    "Lessons for the carer explain setting up the phone, the settings and the safety features.",
            ),
            LessonStep(
                "Moving through a lesson",
                "Tap Next and Back to move between steps, or Read aloud to have the phone say the step. " +
                    "A tick appears beside each lesson once it is finished. Open the lessons again any time with the star button on the main page.",
            ),
            LessonStep(
                "Where to begin",
                "Go through the lessons in order the first time: set up the phone, then blinking, then moving. " +
                    "A few minutes of practice makes choosing much more reliable.",
            ),
        ),
    )

    private val setUp = Lesson(
        id = "setup",
        title = "Setting up the phone",
        summary = "Where to place the phone, the permissions, and the calibration at every start.",
        audience = Audience.Everyone,
        opens = Destination.Calibrate,
        steps = listOf(
            LessonStep(
                "Place the phone",
                "Put the phone on a stand 30 to 50 cm from the face, upright, with the front camera at or a little below eye level. " +
                    "The whole face should be in view and the phone should not move during the conversation.",
            ),
            LessonStep(
                "Light the face",
                "Light from the front or the side works best. Avoid a bright window or lamp behind the person: " +
                    "the camera then sees a dark face and cannot read the eyes.",
            ),
            LessonStep(
                "Allow the camera and microphone",
                "The first time the app opens it asks for the camera, to read the eyes, and the microphone, to hear the visitor. " +
                    "Both are needed. Pictures and sound are read on the phone and never saved or sent.",
            ),
            LessonStep(
                "Calibration at every start",
                "Each time the app starts it calibrates for about 30 seconds. The phone speaks each step: look at the screen, " +
                    "look up above the phone, look down below it, then close and open the eyes. This measures this person's eyes " +
                    "in this position, so let it finish.",
            ),
            LessonStep(
                "Glasses and moving the phone",
                "If the person puts glasses on or off, or the phone is moved, calibrate again: Settings, then Calibrate, 30 s. " +
                    "When the main page says closes are not being read, tap that line to calibrate.",
            ),
        ),
    )

    private val blink = Lesson(
        id = "blink",
        title = "Blink to choose",
        summary = "A deliberate close of the eyes says the lit card.",
        audience = Audience.Speaker,
        practice = Practice.Blink,
        steps = listOf(
            LessonStep(
                "One card is always lit",
                "On the main page one card glows pink with a Blink badge. That is the card the phone will say if you choose now.",
            ),
            LessonStep(
                "Close both eyes on purpose",
                "To choose, close both eyes for about half a second, then open them. " +
                    "The phone says the card as soon as your eyes open again.",
            ),
            LessonStep(
                "Normal blinks are ignored",
                "Quick everyday blinks are too short to count, so you can blink freely while you read the cards. " +
                    "Closing one eye only does not choose either.",
            ),
            LessonStep(
                "Not too long",
                "A close of up to about two seconds chooses. Keeping the eyes shut for two seconds or more is the help signal, " +
                    "which never says a card. It has its own lesson.",
            ),
            LessonStep(
                "Practice tunes the app to you",
                "In the practice round a star lights up; close your eyes to catch it. Catch three stars. " +
                    "Each catch adjusts the blink line and hold time to your own eyes.",
            ),
        ),
    )

    private fun move(c: EyeControls) = Lesson(
        id = "move",
        title = "Moving the highlight",
        summary = when {
            !c.moveByEyes -> "In Blink only the highlight moves by itself; choose when it reaches your card."
            c.lookDown -> "Look down for the next card and up for the one before."
            else -> "Look up to move the highlight to the next card."
        },
        audience = Audience.Speaker,
        practice = Practice.Move,
        steps = if (c.moveByEyes) lookSteps(c) else timedSteps(c),
    )

    private fun lookSteps(c: EyeControls) = listOfNotNull(
        LessonStep(
            "Your eyes move the highlight",
            "The cards are in a list: four replies, then More options, Yes / No and Say anything. " +
                "The highlight starts on the first reply and moves one card for each look.",
        ),
        if (c.lookDown) {
            LessonStep(
                "Look down or up",
                "Look down, past the bottom of the screen, to move to the next card. Look up, towards the top of the phone, " +
                    "to go back one card. From the last card a look down goes round to the first, and from the first a look up goes to the last.",
            )
        } else {
            LessonStep(
                "Look up to move on",
                "Look up, towards the top edge of the phone, then back at the screen. Each look moves the highlight one card down the list. " +
                    "After the last card it goes round to the first again.",
            )
        },
        LessonStep(
            "One look, one step",
            "Hold the look for about a third of a second, then come back to the screen. Return to the middle before the next look: " +
                "keeping the eyes up does not keep moving. Reading the cards with small glances does not move anything.",
        ),
        if (c.winks) {
            LessonStep(
                "Winks move too",
                "Winks are switched on: a left wink moves to the next card and a right wink to the one before. " +
                    "Closing both eyes still chooses.",
            )
        } else {
            null
        },
        LessonStep(
            "Then close your eyes to choose",
            "When your card is lit, close both eyes for about half a second. While the phone speaks no card is lit; " +
                "afterwards the highlight goes back to the first card of the new replies.",
        ),
        LessonStep(
            "Practise",
            "In the practice, a star marks the card to reach. Move to it and close your eyes. Reach three stars to finish. " +
                "Wrong choices are only counted, so take your time.",
        ),
    )

    private fun timedSteps(c: EyeControls) = listOf(
        LessonStep(
            "Blink only",
            "This phone is set to Blink only, for people who find looking up and down tiring. " +
                "The highlight moves through the cards by itself, one card every ${seconds(c.scanMs)}.",
        ),
        LessonStep(
            "Wait for your card",
            "Follow the pink highlight. It goes down the four replies, then More options, Yes / No and Say anything, and starts again at the top.",
        ),
        LessonStep(
            "Close your eyes when it arrives",
            "When the highlight is on your card, close both eyes for about half a second. " +
                "The card lit when your eyes closed is the one said, even if the highlight moves on while they are shut.",
        ),
        LessonStep(
            "Faster or slower",
            "The carer can change how long each card stays lit with Scan speed in Settings, or switch to Look up to move.",
        ),
        LessonStep(
            "Practise",
            "In the practice, a star marks the card to reach. Wait for the highlight and close your eyes when it is on the star. Three stars finish it.",
        ),
    )

    private fun mainPage(c: EyeControls) = Lesson(
        id = "main-page",
        title = "The main page",
        summary = "Reply cards, the fixed cards and the buttons along the top.",
        audience = Audience.Everyone,
        steps = listOf(
            LessonStep(
                "The top bar",
                "The pill on the left shows whether the camera can see the face. The round buttons open, in order: these lessons (star), " +
                    "the eye check (eye), the live transcript, and the session stats.",
            ),
            LessonStep(
                "The camera box",
                "Under the top bar is the live camera with dots on the eyes, and a hint line saying how to move and choose " +
                    "(\"${hint(c)}\"). If the dots are missing, the face is not in view.",
            ),
            LessonStep(
                "Heard",
                "When a visitor speaks, their words appear in the Heard card, live as they are said. " +
                    "The replies below are written for that question.",
            ),
            LessonStep(
                "Four replies",
                "The four reply cards are written by the Gemma model on the phone from the conversation so far and the patient profile. " +
                    "Until the model is ready, or if it is too slow, cards from the built-in phrase list are shown instead.",
            ),
            LessonStep(
                "More options, Yes / No, Say anything",
                "More options shows the next four phrases. Yes / No shows just Yes and No, for quick answers. " +
                    "Say anything opens the word builder for a sentence of your own.",
            ),
            LessonStep(
                "Speaking",
                "A chosen card is said out loud in the phone's offline voice and added to the transcript. " +
                    "The carer can also tap any card to say it.",
            ),
        ),
    )

    private fun sayAnything(c: EyeControls) = Lesson(
        id = "say-anything",
        title = "Say anything",
        summary = "Build your own sentence one word at a time.",
        audience = Audience.Speaker,
        steps = listOf(
            LessonStep(
                "Opening it",
                "Move to Say anything, the last card on the main page, and choose it. The page changes to the word builder.",
            ),
            LessonStep(
                "Pick words",
                "Four next words are shown. Choose one and it is added to the sentence at the top. New words that fit the sentence then appear, " +
                    "suggested by the model. ${if (c.moveByEyes) "Move between them as on the main page." else "The highlight moves through them by itself."}",
            ),
            LessonStep(
                "Finish it for me",
                "Once you have started, the model suggests the whole sentence on the Finish it for me card. Choose it to take that sentence " +
                    "as it is. While the model is still thinking the card reads Thinking of the rest.",
            ),
            LessonStep(
                "More words, Delete, Speak",
                "More words shows another four words. Delete removes the last word. Speak says the sentence out loud and goes back to the main page.",
            ),
            LessonStep(
                "Leaving without speaking",
                "When the sentence is empty, Delete reads Exit: choose it to go back to the reply cards.",
            ),
        ),
    )

    private val help = Lesson(
        id = "help",
        title = "Calling for help",
        summary = "Hold the eyes shut, then blink, to sound the alarm and alert the SOS contact.",
        audience = Audience.Speaker,
        practice = Practice.Help,
        steps = listOf(
            LessonStep(
                "When to use it",
                "Use the help signal when you need someone at once, for example pain, trouble breathing or feeling unwell. " +
                    "It works on the main page at any time, even while the phone is speaking.",
            ),
            LessonStep(
                "Step 1: hold your eyes shut",
                "Close both eyes and keep them closed for two seconds. A short beep tells you the hold is done.",
            ),
            LessonStep(
                "Step 2: open, then blink",
                "After the beep, open your eyes, then within five seconds blink on purpose once, or close them again. " +
                    "This second step means resting or sleeping with your eyes closed never raises the alarm: eyes shut for more " +
                    "than ten seconds count as rest, and so does a confirm close that stays shut.",
            ),
            LessonStep(
                "What happens",
                "A loud alarm sounds and the screen shows Help needed with the last thing you said. " +
                    "If the carer has saved an SOS contact, the phone also texts and then calls them.",
            ),
            LessonStep(
                "Stopping it",
                "The person who comes taps Turn alarm sound off, then I am here to go back to the conversation.",
            ),
            LessonStep(
                "Practice is safe",
                "The practice round goes through the same two steps but sounds no alarm and contacts nobody. " +
                    "If the carer has switched the confirm blink off in Settings, the two second hold alone calls for help, in practice too.",
            ),
        ),
    )

    private fun hands(c: EyeControls) = Lesson(
        id = "hand-signs",
        title = "Hand signs",
        summary = "For people who can move a hand a little: signs the camera can read.",
        audience = Audience.Speaker,
        opens = Destination.Settings,
        steps = listOf(
            LessonStep(
                "Switching them on",
                "Hand signs are " + (if (c.handSigns) "switched on" else "off at the moment") + ". The carer turns them on in Settings, " +
                    "under Hand signs. Each sign can be turned on or off on its own there.",
            ),
            LessonStep(
                "Hold each sign",
                "Hold the sign still in view of the front camera for about half a second. A sign seen only for a moment is ignored.",
            ),
            LessonStep(
                "Signs that speak",
                "Thumb up says Yes. Thumb down says No. The I love you hand says I love you. An open hand says Please wait.",
            ),
            LessonStep(
                "Choosing a card by hand",
                "One to four fingers, with the thumb folded, light the first to the fourth reply card. A closed fist then says the lit card. " +
                    "Lighting a card by hand works in Look up to move; in Blink only the fist says the card the highlight is on.",
            ),
            LessonStep(
                "Eyes still work",
                "While a hand is in view, looks are paused so the highlight stays where the hand put it. Blinks still choose.",
            ),
        ),
    )

    private val listening = Lesson(
        id = "listening",
        title = "Listening to visitors",
        summary = "How the phone hears questions, and what to do without a microphone.",
        audience = Audience.Carer,
        steps = listOf(
            LessonStep(
                "Always listening",
                "On the main page the chip at the start of the topic row reads Listening. The phone keeps listening until you tap it, " +
                    "when it reads Mic off. Tap again to listen again.",
            ),
            LessonStep(
                "Speak naturally",
                "Sit near the phone and ask a question as you normally would. The words appear in the Heard card as you speak, " +
                    "and new replies follow a moment after you stop.",
            ),
            LessonStep(
                "It does not hear itself",
                "Listening pauses while the phone speaks and for a moment after, so its own voice is never taken for a question.",
            ),
            LessonStep(
                "Topic buttons",
                "The topic buttons on the main page, such as Pain, Comfort and Food and drink, ask a common question with one tap.",
            ),
            LessonStep(
                "Typing a question",
                "In a noisy room, or with the microphone off, open Settings and use Ask without the microphone: type the question and tap Ask.",
            ),
            LessonStep(
                "Offline speech",
                "Listening needs the phone's offline speech pack for the language. If Settings says listening wants the internet, " +
                    "download the offline speech pack in the phone's own settings once.",
            ),
        ),
    )

    private val language = Lesson(
        id = "language",
        title = "Languages",
        summary = "Pick the languages spoken around the person; the cards follow the visitor.",
        audience = Audience.Carer,
        opens = Destination.Settings,
        steps = listOf(
            LessonStep(
                "17 languages",
                "Outspoken speaks English, Hindi, Bengali, Marathi, Tamil, Telugu, Kannada, Gujarati, Malayalam, Urdu, Spanish, French, " +
                    "German, Portuguese, Arabic, Chinese and Japanese. The cards, topic buttons, voice and replies all use the language chosen.",
            ),
            LessonStep(
                "Choosing them",
                "In Settings, under Languages, tap every language spoken around the person, then pick the one the app starts in. " +
                    "At least one always stays chosen.",
            ),
            LessonStep(
                "Follow the visitor",
                "With Follow the visitor on, the cards switch to whichever chosen language the visitor speaks, " +
                    "so each visitor can speak their own language.",
            ),
            LessonStep(
                "Speech packs and voices",
                "Each language needs its offline speech pack, to listen, and its offline voice, to speak. If one is missing, " +
                    "Settings offers its download; the phone fetches it, not this app. The natural voice speaks English only; " +
                    "other languages use the phone's voice.",
            ),
        ),
    )

    private val profile = Lesson(
        id = "profile",
        title = "Patient profile",
        summary = "Tell the model who the person is, so replies sound like them.",
        audience = Audience.Carer,
        opens = Destination.Profile,
        steps = listOf(
            LessonStep(
                "Why it matters",
                "Replies are written from what the model knows about the person. With a profile it can use family names, " +
                    "favourite things and health needs instead of generic answers.",
            ),
            LessonStep(
                "Opening it",
                "Open Settings, then Patient profile. Tap the pencil to edit.",
            ),
            LessonStep(
                "About me",
                "Fill in the full name, what family calls them, gender, the languages they speak, where they live, their old work and what they like. " +
                    "Gender is used for correct Hindi wording.",
            ),
            LessonStep(
                "People and health",
                "Add the people close to them, one per line as Relation: Name, for example Wife: Meera. " +
                    "Under Health and care add conditions, allergies, dislikes and any notes, then tap Save.",
            ),
            LessonStep(
                "A photo",
                "Tap Add photo to pick one, or Take selfie: the front camera counts down from three, then takes the photo and crops it to the face.",
            ),
            LessonStep(
                "Private",
                "The profile stays on this phone and only the model on the phone reads it. It is never written to the logs.",
            ),
        ),
    )

    private val sos = Lesson(
        id = "sos",
        title = "SOS contact",
        summary = "Who the help alarm texts and calls.",
        audience = Audience.Carer,
        opens = Destination.Settings,
        steps = listOf(
            LessonStep(
                "Add a contact",
                "In Settings, under SOS contact, type one phone number with its country code, for example +91 98765 43210, and tap Save. " +
                    "Allow calls and texts when the phone asks.",
            ),
            LessonStep(
                "Text, then call",
                "When the help alarm sounds, the phone texts the contact that help is needed, with the time and the last thing said, " +
                    "then calls them. Each can be switched off.",
            ),
            LessonStep(
                "No SIM card",
                "On a phone without a SIM, switch on the WhatsApp message and call instead, then tap Turn on Outspoken in Accessibility and allow it. " +
                    "The helper only presses Send and Call in WhatsApp for the SOS.",
            ),
            LessonStep(
                "Check it",
                "The line under the number says exactly what the alarm will do, or what is still missing. " +
                    "Tap Test: text and call now to try it with the contact's agreement.",
            ),
            LessonStep(
                "Use a person's number",
                "Emergency numbers cannot be called by an app. Use a family member or carer who can come quickly.",
            ),
        ),
    )

    private val eyeCheck = Lesson(
        id = "eye-check",
        title = "Eye check and calibration",
        summary = "See what the camera reads, and measure the eyes again.",
        audience = Audience.Carer,
        opens = Destination.EyeCheck,
        steps = listOf(
            LessonStep(
                "Opening it",
                "Tap the eye button on the main page. Back to talking returns. Looks and closes tried here never move the main page.",
            ),
            LessonStep(
                "Eyes open or shut",
                "The pill at the top reads Eyes open, Half open, Eyes shut or Looking for you, as the camera sees it now.",
            ),
            LessonStep(
                "The eye graph",
                "How open your eyes are shows the last five seconds for each eye, with the green open line and the pink shut line. " +
                    "A deliberate close should drop below the pink line; open eyes should sit above the green one.",
            ),
            LessonStep(
                "Gaze and decisions",
                "The gaze box shows where the eyes are looking against their resting point, and the cards below list the latest blink and look decisions in words.",
            ),
            LessonStep(
                "Calibrate again",
                "If closes are missed or the highlight moves on its own, open Settings with the sliders button and tap Calibrate, 30 s. " +
                    "Follow the spoken steps with the phone where it will be used.",
            ),
        ),
    )

    private fun tuning(c: EyeControls) = Lesson(
        id = "tuning",
        title = "Fine-tuning the controls",
        summary = "The settings sliders, and when to change them.",
        audience = Audience.Carer,
        opens = Destination.Settings,
        steps = listOf(
            LessonStep(
                "Calibrate first",
                "Calibration sets every line from the person's own eyes. Use the sliders only when a problem remains after a calibration, " +
                    "and change one thing at a time.",
            ),
            LessonStep(
                "Look up to move or Blink only",
                "The switch at the top of Settings chooses how the highlight moves. It is now set to " +
                    "${if (c.moveByEyes) "Look up to move" else "Blink only"}. Blink only suits people who find eye movements tiring.",
            ),
            LessonStep(
                "Blink sliders",
                "Shut line and Open line say how closed the eyes must be to count as shut and how open to count as open again. " +
                    "Shortest blink and Longest blink set how long a close must last to choose.",
            ),
            LessonStep(
                "Look sliders",
                "Look hold is how long a look must last before the highlight moves. Look up distance is how far the eyes must move. " +
                    "Looking down also moves and Winks move too add those controls. In Blink only, Scan speed sets how long each card stays lit.",
            ),
            LessonStep(
                "Common fixes",
                "Closes missed: raise the shut line a little or lower Shortest blink. Cards chosen by accident: raise Shortest blink. " +
                    "Highlight moves by itself: raise Look hold or Look up distance.",
            ),
            LessonStep(
                "Undo",
                "Changes are saved at once. Reset puts every slider back to the starting values; calibrate after a reset.",
            ),
        ),
    )

    private val model = Lesson(
        id = "model",
        title = "The reply model",
        summary = "Download, choose and check the on-device Gemma model.",
        audience = Audience.Carer,
        opens = Destination.Models,
        steps = listOf(
            LessonStep(
                "What it does",
                "Gemma runs on the phone and writes the four replies and the Say anything words. Without it the app still works with the built-in phrases.",
            ),
            LessonStep(
                "Opening the page",
                "Open Settings, then Model and logs. The pill at the top says whether a model is ready, loading or missing.",
            ),
            LessonStep(
                "Downloading a model",
                "Tap Allow access to Downloads if asked, then Download next to a model. The download is a large file and runs in the browser; " +
                    "the app loads it by itself when it finishes. E2B is faster, E4B writes better replies.",
            ),
            LessonStep(
                "Switching",
                "Tap Use beside a downloaded model to switch. Choose a model file loads one already copied to the phone.",
            ),
            LessonStep(
                "Checking speed",
                "The page shows how long the newest reply took and the model speed in tokens per second. " +
                    "A phone that gets hot slows down; let it rest if replies become slow.",
            ),
        ),
    )

    private val statsAndTranscript = Lesson(
        id = "stats",
        title = "Stats and transcript",
        summary = "How the session is going, and everything said so far.",
        audience = Audience.Carer,
        opens = Destination.Stats,
        steps = listOf(
            LessonStep(
                "Session stats",
                "The stats button on the main page shows reply time, model speed, phone temperature, blink accuracy, " +
                    "session length and sentences spoken. Listening carries on while it is open.",
            ),
            LessonStep(
                "Blink accuracy",
                "Blink accuracy is the share of deliberate closes that chose a card, counting the practice rounds. " +
                    "A low number usually means a calibration is due.",
            ),
            LessonStep(
                "Live transcript",
                "The transcript button shows the whole conversation, the visitor's questions and the person's replies, as it happens.",
            ),
            LessonStep(
                "Nothing is stored",
                "The conversation is kept in memory only and is cleared when the app closes.",
            ),
        ),
    )

    private val logs = Lesson(
        id = "logs",
        title = "Sharing logs for support",
        summary = "Send the app's run logs when something goes wrong.",
        audience = Audience.Carer,
        opens = Destination.Models,
        steps = listOf(
            LessonStep(
                "What the logs are",
                "Each run the app writes a log of what it saw and did, in plain words: blinks, looks, cards chosen, model timing and errors. " +
                    "The last ten runs are kept on the phone.",
            ),
            LessonStep(
                "Sharing them",
                "Open Settings, then Model and logs, and tap Share logs to send the file through a chat or mail app, " +
                    "or Save logs to put it in a folder.",
            ),
            LessonStep(
                "Privacy",
                "The logs can include what was said near the phone. They never leave the phone unless you share or save them yourself.",
            ),
        ),
    )

    private fun hint(c: EyeControls) = when {
        !c.moveByEyes -> "Close your eyes when your choice lights up"
        c.lookDown -> "Look up or down to move. Close both eyes: choose"
        else -> "Look up to move to the next card. Close both eyes: choose"
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.1f s", ms / 1000f)
}
