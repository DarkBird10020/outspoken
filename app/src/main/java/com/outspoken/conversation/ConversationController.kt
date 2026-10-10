package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.blink.Wink
import com.outspoken.blink.WinkDetector
import com.outspoken.eye.EyeSample
import com.outspoken.hand.HandAction
import com.outspoken.help.HelpStep
import com.outspoken.help.HelpTrigger
import com.outspoken.log.EventLog
import com.outspoken.scan.GazeStep
import com.outspoken.scan.GazeStepper
import com.outspoken.scan.Scanner
import com.outspoken.stats.BlinkTally
import com.outspoken.suggest.Turn
import com.outspoken.ui.BuilderUi
import com.outspoken.ui.ConversationUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The blink-to-speech loop: eye samples in, highlighted card and spoken sentences out.
 * The highlight moves either by the eyes (look down for the card below, up for the one above) or on a timer, see
 * [moveByEyes].
 *
 * New replies are asked for through [requestReplies], which returns false when no model is there
 * to answer, and come back through [onReplies]; only the answer to the latest request is used.
 * After speaking, the highlight waits up to [replyWaitMs] for that answer so the cards do not
 * change under the person's eyes. All times share one clock. Call every method from the same thread.
 *
 * The "Say anything" card opens a [SentenceBuilder]: while it is open the cards are its words and
 * buttons, its next words are asked for through [requestWords] and come back through [onWords].
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val detector: BlinkDetector = BlinkDetector(),
    private val scanner: Scanner = Scanner(),
    private val board: Board = Board(),
    private val log: EventLog = EventLog.None,
    private val gaze: GazeStepper = GazeStepper(log = log),
    private val wink: WinkDetector = WinkDetector(log = log),
    private val requestReplies: (requestId: Int, turns: List<Turn>) -> Boolean = { _, _ -> false },
    private val requestWords: (requestId: Int, turns: List<Turn>, sentence: String) -> Boolean = { _, _, _ -> false },
    private val help: HelpTrigger = HelpTrigger(log = log),
    private val onHelp: (HelpStep) -> Unit = {},
    private val replyWaitMs: Long = 2_500,
    private val maxSpeakMs: Long = 10_000,
) {
    /** True: the highlight only moves when the eyes look down or up. False: it moves on a timer. */
    var moveByEyes = false
        set(value) {
            field = value
            moveCursor(0, clockMs)
        }

    /**
     * True when looking down is off: a look up is then the only look and moves to the next card,
     * wrapping round, so every card can be reached with it alone.
     */
    var upMovesNext = false

    /** Left wink moves down, right wink up. Off unless turned on in the settings. */
    var winks = false

    // Eye mode: position in board.cards, and the one before the last move, so a blink that
    // began just before a move still picks the card that was lit when the eyes shut.
    private var cursor = 0
    private var previousCursor = 0
    private var cursorMovedMs = Long.MIN_VALUE

    private val turns = mutableListOf<Turn>()
    private var heard: String? = null
    private var speakingSinceMs: Long? = null
    private var waitUntilMs: Long? = null
    private var latestRequest = 0
    private var repliesPending = false
    private var lastHighlighted = -1
    private var clockMs = Long.MIN_VALUE

    /** Sentences said this session, oldest first. Memory only. */
    val history: List<String> get() = turns.filter { !it.fromListener }.map { it.text }

    /** All conversation turns this session, both visitor and speaker (PRD S2). */
    val allTurns: List<Turn> get() = turns.toList()

    private val phraseFrequencies = mutableMapOf<String, Int>()

    /** Phrases spoken this session, most frequent first (PRD S3). */
    val frequentPhrases: List<String>
        get() = phraseFrequencies.entries.sortedByDescending { it.value }.map { it.key }

    // Blink accuracy (PRD S1), counted as the conversation goes: a close held long enough to
    // choose, begun while a card was lit, is a try; it is caught when it chose a card.
    private var tryStartMs: Long? = null
    private var tryCounts = false
    private var tryChose = false

    /** Tries to choose with the eyes this session, and how many chose a card. */
    var blinks = BlinkTally()
        private set

    /** The sentence being built with "Say anything"; null while the reply cards show. */
    private var builder: SentenceBuilder? = null
    private var latestWordRequest = 0

    /** When the builder's cards last changed; a blink that began before then picks nothing. */
    private var builderChangedMs = Long.MIN_VALUE

    /** The cards in scan order: the builder's while it is open, otherwise the board's. */
    private val cards: List<Int> get() = builder?.cards ?: board.cards

    private val speaking get() = speakingSinceMs != null
    private val waiting get() = waitUntilMs != null

    private val _ui = MutableStateFlow(ConversationUi(faceFound = false, heard = null, replies = board.replies, highlighted = -1))
    val ui: StateFlow<ConversationUi> = _ui.asStateFlow()

    init {
        scanner.pause(0)
    }

    fun onSample(sample: EyeSample) {
        val nowMs = advance(sample.timeMs)
        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> onBlink(event, nowMs)
            BlinkEvent.FaceFound -> {
                scanner.resume(nowMs)
                gaze.forgetRest()
            }
            BlinkEvent.FaceLost -> scanner.pause(nowMs)
            null -> Unit
        }
        // Help works at any time, even while the phone speaks.
        help.onEyes(detector.shutSinceMs, nowMs, detector.settings.maxBlinkMs)?.let {
            tryCounts = false
            onHelp(it)
        }
        countTry()
        if (moveByEyes && !speaking && !waiting && detector.tracking) {
            // Only shut eyes stop a look; half-lowered lids still count as open here.
            // Owner request: left wink moves down, right wink moves up; both eyes shut chooses.
            val winked = wink.onSample(sample, detector.settings)?.takeIf { winks }
            if (winked != null) {
                // The eye starting to close shifts the gaze reading, so a look can fire just
                // before the wink does (phone 02:29:49). Take that look back.
                if (nowMs - lastLookMs <= LOOK_UNDO_MS) {
                    log.write("gaze", "look undone, a wink came ${nowMs - lastLookMs} ms after it")
                    moveCursor(beforeLook, nowMs)
                }
                lastLookMs = Long.MIN_VALUE / 2
                moveCursor(if (winked == Wink.Left) cursor + 1 else cursor - 1, nowMs)
            }
            // One eye lower than the other pauses looks, and for a moment after: half closing one
            // eye moved the gaze reading as far as a real look up (phone 02:29:36 to 02:29:51).
            if (wink.active || wink.oneEyeLower) gaze.pauseUntil(nowMs + AFTER_WINK_MS)
            // Either eye shut pauses looks: closing one eye for a wink shifts the gaze and iris
            // readings, and on the phone a left wink also fired a "look down" (01:35:38).
            val eyesOpen = !detector.eitherEyeShut(sample)
            val step = gaze.onSample(sample.gaze, eyesOpen, nowMs, sample.irisY)
            if (step != null) {
                beforeLook = cursor
                lastLookMs = nowMs
                val forward = step == GazeStep.Next || upMovesNext
                moveCursor(if (forward) cursor + 1 else cursor - 1, nowMs)
            }
        }
        publish(nowMs)
    }

    fun onTick(nowMs: Long) = publish(advance(nowMs))

    /** A tap on a card, for the person at the bedside. */
    fun onTap(card: Int, timeMs: Long) {
        val nowMs = advance(timeMs)
        if (speaking) {
            log.write("scan", "tap on ${label(card)} ignored while speaking")
        } else {
            log.write("scan", "tap on ${label(card)}")
            choose(card, nowMs)
        }
        publish(nowMs)
    }

    /**
     * A hand sign held on purpose: a phrase is said as if its card were chosen, a finger count
     * lights that card, and a fist chooses the lit card as a blink would. Nothing while the phone
     * speaks or waits for new cards, and no phrase while "Say anything" is open, where it would
     * cut the sentence being built.
     */
    fun onHandSign(action: HandAction, timeMs: Long) {
        val nowMs = advance(timeMs)
        when {
            speaking || waiting -> log.write("hand", "sign ignored while ${if (speaking) "speaking" else "waiting for new replies"}")
            action is HandAction.Say && builder != null -> log.write("hand", "\"${action.text}\" ignored while Say anything is open")
            action is HandAction.Say -> say(action.text, nowMs)
            action is HandAction.Light -> lightCard(action.index, nowMs)
            else -> {
                val card = if (moveByEyes) cards.getOrNull(cursor) else scanner.cardAt(nowMs)
                if (card == null) {
                    log.write("hand", "fist ignored, no card lit")
                } else {
                    log.write("hand", "fist picked ${label(card)}")
                    choose(card, nowMs)
                }
            }
        }
        publish(nowMs)
    }

    /**
     * A hand is in the camera's view: looks pause until a moment after it goes, so the eyes do not
     * move the highlight off a card the hand has lit. On the phone a hand lit card 4, a look down
     * a second later moved to More options, and the fist then chose More options (15:55:08 to
     * 15:55:10); people look at their hand, or down at the phone, while they sign. Blinks still choose.
     */
    fun onHandInView(timeMs: Long) {
        gaze.pauseUntil(advance(timeMs) + HAND_LOOK_PAUSE_MS)
    }

    /** Moves the eye-mode highlight to the card at [index]. In timed scanning the timer owns it. */
    private fun lightCard(index: Int, nowMs: Long) {
        val card = cards.getOrNull(index)
        when {
            !moveByEyes -> log.write("hand", "card ${index + 1} sign ignored: cards are lit by hand only in Look up to move")
            card == null -> log.write("hand", "no card ${index + 1} to light")
            else -> {
                moveCursor(index, nowMs)
                log.write("hand", "lit ${label(card)}")
            }
        }
    }

    fun onSpeechDone(timeMs: Long) {
        if (!speaking) return
        val nowMs = advance(timeMs)
        speakingSinceMs = null
        if (repliesPending) {
            waitUntilMs = nowMs + replyWaitMs
            log.write("scan", "speech done, waiting up to $replyWaitMs ms for new replies")
        } else {
            log.write("scan", "speech done, scanning again")
            startCards(nowMs)
        }
        publish(nowMs)
    }

    /** Asks for replies that fit the conversation so far. */
    /** A question from the visitor, heard or typed. The replies are asked for at once. */
    fun onHeard(question: String, timeMs: Long) {
        val nowMs = advance(timeMs)
        if (speaking) {
            log.write("listen", "ignored \"$question\" while speaking")
            return
        }
        log.write("listen", "heard \"$question\"")
        heard = question
        turns += Turn(fromListener = true, text = question)
        refreshReplies()
        publish(nowMs)
    }

    fun refreshReplies() {
        latestRequest++
        repliesPending = requestReplies(latestRequest, turns.toList())
    }

    fun onReplies(requestId: Int, replies: List<String>, fromModel: Boolean, timeMs: Long) {
        if (requestId != latestRequest) return
        val nowMs = advance(timeMs)
        log.write("scan", "new cards from ${if (fromModel) "the model" else "the phrase bank"}: $replies")
        repliesPending = false
        waitUntilMs = null
        board.showSuggestions(if (fromModel) replies else null)
        if (!speaking && builder == null) startCards(nowMs)
        publish(nowMs)
    }

    /** The model's next words and finished sentence for "Say anything"; only the latest request counts. */
    fun onWords(requestId: Int, words: List<String>, completion: String?, timeMs: Long) {
        val sentence = builder ?: return
        if (requestId != latestWordRequest) return
        val nowMs = advance(timeMs)
        val lit = if (moveByEyes) cards.getOrNull(cursor) else null
        sentence.showModel(words, completion)
        log.write("scan", "words from the model: ${sentence.shown}, finish: ${sentence.completion ?: "none"}")
        // A look already on Speak, Delete or More words stays there, as those still do the same:
        // on the phone the new words pulled the highlight off "Speak" 0.5 s after a look up had
        // reached it (10:18:13). On a word, the highlight goes back to the first new word.
        if (lit != null && lit in STEADY_CARDS) moveCursor(cards.indexOf(lit), nowMs) else restartBuilderCards(nowMs)
        publish(nowMs)
    }

    /**
     * Camera frames are stamped before face detection runs, so they arrive a little behind the
     * screen ticks. The highlight only ever moves forward on the newest time seen; otherwise it
     * flickers back to the previous card at every step.
     */
    private fun advance(timeMs: Long): Long {
        clockMs = maxOf(clockMs, timeMs)
        return clockMs
    }

    private fun onBlink(blink: BlinkEvent.Blink, nowMs: Long) {
        help.onBlink(blink.startMs)?.let {
            tryCounts = false
            onHelp(it)
            return
        }
        if (help.isHold(blink.durationMs)) {
            tryCounts = false
            return
        }
        if (speaking || waiting) {
            log.write("scan", "blink ignored while ${if (speaking) "speaking" else "waiting for new replies"}")
            return
        }
        val card = if (moveByEyes) cursorCardAt(blink.startMs) else scanner.cardAt(blink.startMs)
        if (card == null || (builder != null && blink.startMs < builderChangedMs)) {
            log.write("scan", "blink ignored, it began before the cards changed")
            return
        }
        log.write("scan", "blink picked ${label(card)}")
        tryChose = true
        choose(card, nowMs)
    }

    /**
     * Counts each close once it ends. A close that ended with no face, or was part of a help
     * call, is not a try; nor is one shorter than the pick hold, as unprompted blinks reach 242 ms.
     */
    private fun countTry() {
        val shutSince = detector.shutSinceMs
        if (shutSince != null) {
            if (tryStartMs == null) {
                tryStartMs = shutSince
                tryCounts = !speaking && !waiting
                tryChose = false
            }
            return
        }
        if (tryStartMs == null) return
        tryStartMs = null
        val closeMs = detector.lastCloseMs
        if (tryChose) {
            blinks = blinks.copy(caught = blinks.caught + 1)
        } else if (tryCounts && detector.tracking && closeMs >= detector.settings.minBlinkMs && !help.isHold(closeMs)) {
            blinks = blinks.copy(missed = blinks.missed + 1)
            log.write("scan", "missed try: eyes shut $closeMs ms, no card chosen (${blinks.caught} of ${blinks.caught + blinks.missed} caught)")
        }
    }

    private fun choose(card: Int, nowMs: Long) {
        builder?.let {
            chooseInBuilder(it, card, nowMs)
            return
        }
        if (card == Board.SAY_ANYTHING) {
            openBuilder(nowMs)
            return
        }
        val sentence = board.choose(card)
        if (sentence == null) {
            log.write("scan", "cards now ${board.replies}")
            moveCursor(0, nowMs)
            scanner.restart(board.cards, nowMs)
            return
        }
        say(sentence, nowMs)
    }

    /** The bedside helper leaves "Say anything" without speaking (the back button). */
    fun closeSayAnything(timeMs: Long) {
        if (builder == null) return
        val nowMs = advance(timeMs)
        closeBuilder(nowMs, "back button")
        publish(nowMs)
    }

    private fun openBuilder(nowMs: Long) {
        builder = SentenceBuilder()
        log.write("scan", "say anything opened")
        askWords()
        restartBuilderCards(nowMs)
    }

    private fun closeBuilder(nowMs: Long, why: String) {
        builder = null
        latestWordRequest++
        log.write("scan", "say anything closed, $why")
        moveCursor(0, nowMs)
        scanner.restart(board.cards, nowMs)
    }

    private fun chooseInBuilder(sentence: SentenceBuilder, card: Int, nowMs: Long) {
        when (card) {
            SentenceBuilder.MORE_WORDS -> sentence.more()
            SentenceBuilder.DELETE -> if (!sentence.deleteLast()) {
                closeBuilder(nowMs, "nothing to delete")
                return
            } else {
                askWords()
            }
            SentenceBuilder.SPEAK -> {
                val text = sentence.sentence
                closeBuilder(nowMs, "spoken")
                say(text, nowMs)
                return
            }
            SentenceBuilder.FINISH -> {
                val text = sentence.completion ?: return
                closeBuilder(nowMs, "finished by the model")
                say(text, nowMs)
                return
            }
            else -> {
                val word = sentence.shown.getOrNull(card) ?: return
                sentence.add(word)
                log.write("scan", "sentence now \"${sentence.sentence}\"")
                askWords()
            }
        }
        restartBuilderCards(nowMs)
    }

    private fun askWords() {
        val sentence = builder ?: return
        latestWordRequest++
        requestWords(latestWordRequest, turns.toList(), sentence.sentence)
    }

    /** Back to the first word whenever the builder's cards change, so a pick never lands on a word not seen. */
    private fun restartBuilderCards(nowMs: Long) {
        builderChangedMs = nowMs
        moveCursor(0, nowMs)
        scanner.restart(cards, nowMs)
    }

    /** Says [sentence], adds it to the conversation and the frequent phrases, and asks for new replies. */
    private fun say(sentence: String, nowMs: Long) {
        turns += Turn(fromListener = false, text = sentence)
        phraseFrequencies[sentence] = (phraseFrequencies[sentence] ?: 0) + 1
        board.updateFrequent(frequentPhrases)
        // The question has its answer now.
        heard = null
        // The phrase bank shows at once while the model writes the next replies.
        board.showSuggestions(null)
        speakingSinceMs = nowMs
        scanner.pause(nowMs)
        log.write("scan", "say \"$sentence\"")
        speak(sentence)
        refreshReplies()
    }

    /** Puts the highlight back on the first card and starts the timer again if the face is seen. */
    private fun startCards(nowMs: Long) {
        moveCursor(0, nowMs)
        scanner.restart(board.cards, nowMs)
        if (detector.tracking) scanner.resume(nowMs)
    }

    private fun publish(nowMs: Long) {
        // Some speech engines never report done; do not let that freeze the board.
        speakingSinceMs?.let {
            if (nowMs - it > maxSpeakMs) {
                log.write("scan", "speech never reported done, carrying on")
                onSpeechDone(nowMs)
            }
        }
        waitUntilMs?.let {
            if (nowMs >= it) {
                log.write("scan", "no new replies yet, carrying on with the phrase bank")
                waitUntilMs = null
                startCards(nowMs)
            }
        }
        if (scanner.cards != cards) scanner.restart(cards, nowMs)
        if (waiting) scanner.pause(nowMs)
        val highlighted = when {
            speaking || waiting -> -1
            moveByEyes -> cards.getOrNull(cursor.coerceIn(0, cards.size - 1)) ?: -1
            else -> scanner.cardAt(nowMs) ?: -1
        }
        if (highlighted != lastHighlighted && highlighted != -1) log.write("scan", "highlight ${label(highlighted)}")
        lastHighlighted = highlighted
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = heard,
            replies = board.replies,
            highlighted = highlighted,
            builder = builder?.let {
                BuilderUi(
                    sentence = it.sentence,
                    wordCount = it.wordCount,
                    words = it.shown,
                    completion = it.completion,
                    canSpeak = !it.isEmpty,
                )
            },
        )
    }

    /** Moves the eye-mode highlight, wrapping round the ends of the list. */
    private var lastLookMs = Long.MIN_VALUE / 2
    private var beforeLook = 0

    private fun moveCursor(position: Int, nowMs: Long) {
        val size = cards.size
        previousCursor = cursor
        cursor = ((position % size) + size) % size
        cursorMovedMs = nowMs
    }

    private fun cursorCardAt(timeMs: Long): Int? {
        val position = if (cursorMovedMs > timeMs) previousCursor else cursor
        return cards.getOrNull(position)
    }

    private fun label(card: Int): String {
        val sentence = builder
        if (sentence != null) {
            return when (card) {
                SentenceBuilder.MORE_WORDS -> "More words"
                SentenceBuilder.DELETE -> if (sentence.isEmpty) "Exit" else "Delete"
                SentenceBuilder.SPEAK -> "Speak"
                SentenceBuilder.FINISH -> "Finish it for me"
                else -> "word \"${sentence.shown.getOrNull(card)}\""
            }
        }
        return when (card) {
            Board.MORE_OPTIONS -> "More options"
            Board.YES_NO -> "Yes / No"
            Board.SAY_ANYTHING -> "Say anything"
            else -> "\"${board.replies.getOrNull(card)}\""
        }
    }

    private companion object {
        /** Builder buttons that mean the same before and after the model's words come. */
        val STEADY_CARDS = setOf(SentenceBuilder.MORE_WORDS, SentenceBuilder.DELETE, SentenceBuilder.SPEAK)

        /** Looks are ignored this long after a hand was last seen. */
        const val HAND_LOOK_PAUSE_MS = 1_000L

        /** Looks are ignored this long after one eye stops reading lower than the other. */
        const val AFTER_WINK_MS = 800L

        /** A look this soon before a wink was the wink starting, not a look. */
        const val LOOK_UNDO_MS = 1_000L
    }
}
