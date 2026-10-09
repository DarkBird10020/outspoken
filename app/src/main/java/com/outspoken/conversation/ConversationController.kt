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
 * New replies are asked for through [requestReplies] and come back through [onReplies]; only the
 * answer to the latest request is used. All times share one clock. Call every method from the
 * same thread.
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val requestReplies: (requestId: Int, turns: List<Turn>) -> Unit = { _, _ -> },
    private val detector: BlinkDetector = BlinkDetector(),
    private val scanner: Scanner = Scanner(),
    private val board: Board = Board(),
) {
    private val turns = mutableListOf<Turn>()
    private var speaking = false
    private var latestRequest = 0

    /** The conversation this session, oldest first. Memory only. */
    val history: List<Turn> get() = turns

    private val _ui = MutableStateFlow(ConversationUi(faceFound = false, heard = null, replies = board.replies, highlighted = -1))
    val ui: StateFlow<ConversationUi> = _ui.asStateFlow()

    init {
        scanner.pause(0)
    }

    fun onSample(sample: EyeSample) {
        when (val event = detector.onSample(sample)) {
            is BlinkEvent.Blink -> if (!speaking) scanner.cardAt(event.startMs)?.let { choose(it, sample.timeMs) }
            BlinkEvent.FaceFound -> scanner.resume(sample.timeMs)
            BlinkEvent.FaceLost -> scanner.pause(sample.timeMs)
            null -> Unit
        }
        publish(sample.timeMs)
    }

    fun onTick(nowMs: Long) = publish(nowMs)

    /** A tap on a card, for the person at the bedside. */
    fun onTap(card: Int, nowMs: Long) {
        if (!speaking) choose(card, nowMs)
        publish(nowMs)
    }

    /** Asks for replies that fit the conversation so far. */
    fun refreshReplies() {
        latestRequest++
        requestReplies(latestRequest, turns.toList())
    }

    fun onReplies(requestId: Int, replies: List<String>, fromModel: Boolean, nowMs: Long) {
        if (requestId != latestRequest) return
        board.showSuggestions(if (fromModel) replies else null)
        scanner.restart(board.cards, nowMs)
        publish(nowMs)
    }

    fun onSpeechDone(nowMs: Long) {
        speaking = false
        scanner.restart(board.cards, nowMs)
        if (detector.tracking) scanner.resume(nowMs)
        publish(nowMs)
    }

    private fun choose(card: Int, nowMs: Long) {
        val sentence = board.choose(card)
        if (sentence == null) {
            scanner.restart(board.cards, nowMs)
            return
        }
        turns += Turn(fromListener = false, text = sentence)
        // The phrase bank shows at once while the model writes the next replies.
        board.showSuggestions(null)
        speaking = true
        scanner.pause(nowMs)
        speak(sentence)
        refreshReplies()
    }

    private fun publish(nowMs: Long) {
        if (scanner.cards != board.cards) scanner.restart(board.cards, nowMs)
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = null,
            replies = board.replies,
            highlighted = if (speaking) -1 else scanner.cardAt(nowMs) ?: -1,
        )
    }
}
