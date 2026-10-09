package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import com.outspoken.suggest.Turn
import com.outspoken.ui.ConversationUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The blink-to-speech loop. The highlight never moves by itself: a short blink moves it to the
 * next card and a long blink chooses the lit card. [cue] sounds while the eyes are still shut,
 * once they have been shut long enough to choose, since the person cannot see the screen then.
 *
 * New replies are asked for through [requestReplies], which returns false when no model is
 * there to answer, and come back through [onReplies]; only the answer to the latest request is
 * used. After speaking, the board waits up to [replyWaitMs] for that answer so the cards do not
 * change under the person's eyes. All times share one clock. Call every method from the same
 * thread.
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val requestReplies: (requestId: Int, turns: List<Turn>) -> Boolean = { _, _ -> false },
    private val detector: BlinkDetector = BlinkDetector(),
    private val board: Board = Board(),
    private val cue: () -> Unit = {},
    private val replyWaitMs: Long = 2_500,
    private val maxSpeakMs: Long = 10_000,
    private val log: EventLog = EventLog.None,
) {
    private val turns = mutableListOf<Turn>()
    private var latestRequest = 0
    private var repliesPending = false
    private var speakingSinceMs: Long? = null
    private var waitUntilMs: Long? = null
    private var cursor = 0
    private var cardsChangedAtMs = Long.MIN_VALUE
    private var clockMs = Long.MIN_VALUE

    /** The conversation this session, oldest first. */
    val history: List<Turn> get() = turns

    private val _ui = MutableStateFlow(ConversationUi(faceFound = false, heard = null, replies = board.replies, highlighted = 0))
    val ui: StateFlow<ConversationUi> = _ui.asStateFlow()

    private val speaking get() = speakingSinceMs != null
    private val ready get() = detector.tracking && !speaking && waitUntilMs == null

    fun onSample(sample: EyeSample) {
        val nowMs = advance(sample.timeMs)
        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> onBlink(event, nowMs)
            BlinkEvent.LongReached -> if (ready) cue()
            is BlinkEvent.Rejected, BlinkEvent.FaceFound, BlinkEvent.FaceLost, null -> Unit
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

    /** Asks for replies that fit the conversation so far. */
    fun refreshReplies() {
        latestRequest++
        repliesPending = requestReplies(latestRequest, turns.toList())
    }

    fun onReplies(requestId: Int, replies: List<String>, fromModel: Boolean, nowMs: Long) {
        if (requestId != latestRequest) return
        val now = advance(nowMs)
        log.write("scan", "new cards from ${if (fromModel) "the model" else "the phrase bank"}: $replies")
        repliesPending = false
        waitUntilMs = null
        board.showSuggestions(if (fromModel) replies else null)
        cardsChanged(now)
        publish(now)
    }

    fun onSpeechDone(timeMs: Long) {
        if (!speaking) return
        val nowMs = advance(timeMs)
        speakingSinceMs = null
        if (repliesPending) {
            waitUntilMs = nowMs + replyWaitMs
            log.write("scan", "speech done, waiting up to $replyWaitMs ms for new replies")
        } else {
            log.write("scan", "speech done")
        }
        cardsChanged(nowMs)
        publish(nowMs)
    }

    /**
     * Camera frames are stamped before face detection runs, so they arrive a little behind the
     * screen ticks. Time only ever moves forward on the newest value seen.
     */
    private fun advance(timeMs: Long): Long {
        clockMs = maxOf(clockMs, timeMs)
        return clockMs
    }

    private fun onBlink(blink: BlinkEvent.Blink, nowMs: Long) {
        when {
            !ready -> log.write("scan", "blink ignored, ${if (speaking) "speaking" else "waiting for new replies"}")
            blink.startMs < cardsChangedAtMs -> log.write("scan", "blink ignored, it began before the cards changed")
            blink.long -> {
                val card = board.cards[cursor]
                log.write("scan", "long blink chose ${label(card)}")
                choose(card, nowMs)
            }
            else -> {
                cursor = (cursor + 1) % board.cards.size
                log.write("scan", "short blink, now on ${label(board.cards[cursor])}")
            }
        }
    }

    private fun choose(card: Int, nowMs: Long) {
        val sentence = board.choose(card)
        if (sentence == null) {
            log.write("scan", "cards now ${board.replies}")
            cardsChanged(nowMs)
            return
        }
        log.write("scan", "say \"$sentence\"")
        turns += Turn(fromListener = false, text = sentence)
        // The phrase bank shows at once while the model writes the next replies.
        board.showSuggestions(null)
        speakingSinceMs = nowMs
        cardsChanged(nowMs)
        speak(sentence)
        refreshReplies()
    }

    private fun cardsChanged(nowMs: Long) {
        cursor = 0
        cardsChangedAtMs = nowMs
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
                log.write("scan", "no new replies yet, using the phrase bank")
                waitUntilMs = null
                cardsChanged(nowMs)
            }
        }
        if (cursor >= board.cards.size) cursor = 0
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = null,
            replies = board.replies,
            highlighted = if (speaking || waitUntilMs != null) -1 else board.cards[cursor],
        )
    }

    private fun label(card: Int) = when (card) {
        Board.MORE_OPTIONS -> "More options"
        Board.YES_NO -> "Yes / No"
        else -> "\"${board.replies.getOrNull(card)}\""
    }
}
