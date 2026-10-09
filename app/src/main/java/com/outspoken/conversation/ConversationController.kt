package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.scan.Scanner
import com.outspoken.suggest.Turn
import com.outspoken.ui.ConversationUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The blink-to-speech loop: eye samples in, highlighted card and spoken sentences out.
 *
 * New replies are asked for through [requestReplies], which returns false when no model is
 * there to answer, and come back through [onReplies]; only the answer to the latest request is
 * used. After speaking, scanning waits up to [replyWaitMs] for that answer so the cards do not
 * change under the person's eyes. All times share one clock. Call every method from the same
 * thread.
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val requestReplies: (requestId: Int, turns: List<Turn>) -> Boolean = { _, _ -> false },
    private val detector: BlinkDetector = BlinkDetector(),
    private val scanner: Scanner = Scanner(),
    private val board: Board = Board(),
    private val replyWaitMs: Long = 2_500,
    private val maxSpeakMs: Long = 10_000,
    private val log: (timeMs: Long, text: String) -> Unit = { _, _ -> },
) {
    private val turns = mutableListOf<Turn>()
    private var latestRequest = 0
    private var repliesPending = false
    private var speakingSinceMs: Long? = null
    private var waitUntilMs: Long? = null

    /** The conversation this session, oldest first. */
    val history: List<Turn> get() = turns

    private val _ui = MutableStateFlow(ConversationUi(faceFound = false, heard = null, replies = board.replies, highlighted = -1))
    val ui: StateFlow<ConversationUi> = _ui.asStateFlow()

    private val speaking get() = speakingSinceMs != null
    private val scanning get() = detector.tracking && !speaking && waitUntilMs == null

    fun onSample(sample: EyeSample) {
        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> {
                val card = if (scanning) scanner.cardAt(event.startMs) else null
                log(sample.timeMs, "blink ${event.durationMs} ms, card ${card ?: "none"}")
                card?.let { choose(it, sample.timeMs) }
            }
            is BlinkEvent.Rejected -> log(sample.timeMs, "closure ${event.durationMs} ms ignored")
            BlinkEvent.FaceFound -> log(sample.timeMs, "face found")
            BlinkEvent.FaceLost -> log(sample.timeMs, "face lost")
            null -> Unit
        }
        publish(sample.timeMs)
    }

    fun onTick(nowMs: Long) = publish(nowMs)

    /** A tap on a card, for the person at the bedside. */
    fun onTap(card: Int, nowMs: Long) {
        if (!speaking) {
            log(nowMs, "tap card $card")
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
        log(nowMs, "replies from ${if (fromModel) "model" else "phrase bank"}: ${replies.joinToString(" | ")}")
        repliesPending = false
        waitUntilMs = null
        board.showSuggestions(if (fromModel) replies else null)
        scanner.restart(board.cards, nowMs)
        publish(nowMs)
    }

    fun onSpeechDone(nowMs: Long) {
        if (!speaking) return
        speakingSinceMs = null
        if (repliesPending) waitUntilMs = nowMs + replyWaitMs
        scanner.restart(board.cards, nowMs)
        publish(nowMs)
    }

    private fun choose(card: Int, nowMs: Long) {
        val sentence = board.choose(card)
        if (sentence == null) {
            scanner.restart(board.cards, nowMs)
            return
        }
        log(nowMs, "say \"$sentence\"")
        turns += Turn(fromListener = false, text = sentence)
        // The phrase bank shows at once while the model writes the next replies.
        board.showSuggestions(null)
        speakingSinceMs = nowMs
        scanner.restart(board.cards, nowMs)
        speak(sentence)
        refreshReplies()
    }

    private fun publish(nowMs: Long) {
        // Some speech engines never report done; do not let that freeze the board.
        speakingSinceMs?.let { if (nowMs - it > maxSpeakMs) onSpeechDone(nowMs) }
        waitUntilMs?.let {
            if (nowMs >= it) {
                waitUntilMs = null
                scanner.restart(board.cards, nowMs)
            }
        }
        if (scanner.cards != board.cards) scanner.restart(board.cards, nowMs)
        if (scanning) scanner.resume(nowMs) else scanner.pause(nowMs)
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = null,
            replies = board.replies,
            highlighted = if (speaking || waitUntilMs != null) -1 else scanner.cardAt(nowMs) ?: -1,
        )
    }
}
