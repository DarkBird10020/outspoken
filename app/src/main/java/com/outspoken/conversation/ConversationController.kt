package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.help.HelpStep
import com.outspoken.help.HelpTrigger
import com.outspoken.log.EventLog
import com.outspoken.scan.GazeStep
import com.outspoken.scan.GazeStepper
import com.outspoken.scan.Scanner
import com.outspoken.suggest.Turn
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
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val detector: BlinkDetector = BlinkDetector(),
    private val scanner: Scanner = Scanner(),
    private val board: Board = Board(),
    private val log: EventLog = EventLog.None,
    private val gaze: GazeStepper = GazeStepper(log = log),
    private val requestReplies: (requestId: Int, turns: List<Turn>) -> Boolean = { _, _ -> false },
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
            BlinkEvent.FaceFound -> scanner.resume(nowMs)
            BlinkEvent.FaceLost -> scanner.pause(nowMs)
            null -> Unit
        }
        // Help works at any time, even while the phone speaks.
        help.onEyes(detector.shutSinceMs, nowMs)?.let(onHelp)
        if (moveByEyes && !speaking && !waiting && detector.tracking) {
            // Only shut eyes stop a look; half-lowered lids still count as open here.
            val eyesOpen = !detector.eyesShut(sample)
            when (gaze.onSample(sample.gaze, eyesOpen, nowMs, sample.irisY)) {
                GazeStep.Next -> moveCursor(cursor + 1, nowMs)
                GazeStep.Previous -> moveCursor(cursor - 1, nowMs)
                null -> Unit
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
        if (!speaking) startCards(nowMs)
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
            onHelp(it)
            return
        }
        if (help.isHold(blink.durationMs)) return
        if (speaking || waiting) {
            log.write("scan", "blink ignored while ${if (speaking) "speaking" else "waiting for new replies"}")
            return
        }
        val card = if (moveByEyes) cursorCardAt(blink.startMs) else scanner.cardAt(blink.startMs)
        if (card == null) {
            log.write("scan", "blink ignored, it began before the cards changed")
            return
        }
        log.write("scan", "blink picked ${label(card)}")
        choose(card, nowMs)
    }

    private fun choose(card: Int, nowMs: Long) {
        val sentence = board.choose(card)
        if (sentence == null) {
            log.write("scan", "cards now ${board.replies}")
            moveCursor(0, nowMs)
            scanner.restart(board.cards, nowMs)
            return
        }
        turns += Turn(fromListener = false, text = sentence)
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
        if (scanner.cards != board.cards) scanner.restart(board.cards, nowMs)
        if (waiting) scanner.pause(nowMs)
        val highlighted = when {
            speaking || waiting -> -1
            moveByEyes -> board.cards.getOrNull(cursor.coerceIn(0, board.cards.size - 1)) ?: -1
            else -> scanner.cardAt(nowMs) ?: -1
        }
        if (highlighted != lastHighlighted && highlighted != -1) log.write("scan", "highlight ${label(highlighted)}")
        lastHighlighted = highlighted
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = heard,
            replies = board.replies,
            highlighted = highlighted,
        )
    }

    /** Moves the eye-mode highlight, wrapping round the ends of the list. */
    private fun moveCursor(position: Int, nowMs: Long) {
        val size = board.cards.size
        previousCursor = cursor
        cursor = ((position % size) + size) % size
        cursorMovedMs = nowMs
    }

    private fun cursorCardAt(timeMs: Long): Int? {
        val position = if (cursorMovedMs > timeMs) previousCursor else cursor
        return board.cards.getOrNull(position)
    }

    private fun label(card: Int) = when (card) {
        Board.MORE_OPTIONS -> "More options"
        Board.YES_NO -> "Yes / No"
        else -> "\"${board.replies.getOrNull(card)}\""
    }
}
