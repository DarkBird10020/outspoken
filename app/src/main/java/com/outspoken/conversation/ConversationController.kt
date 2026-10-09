package com.outspoken.conversation

import com.outspoken.blink.BlinkDetector
import com.outspoken.blink.BlinkEvent
import com.outspoken.eye.EyeSample
import com.outspoken.log.EventLog
import com.outspoken.scan.Scanner
import com.outspoken.ui.ConversationUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The blink-to-speech loop: eye samples in, highlighted card and spoken sentences out.
 * All times share one clock. Call every method from the same thread.
 */
class ConversationController(
    private val speak: (String) -> Unit,
    private val detector: BlinkDetector = BlinkDetector(),
    private val scanner: Scanner = Scanner(),
    private val board: Board = Board(),
    private val log: EventLog = EventLog.None,
) {
    private val spoken = mutableListOf<String>()
    private var speaking = false
    private var lastHighlighted = -1
    private var clockMs = Long.MIN_VALUE

    /** Sentences said this session, oldest first. Memory only. */
    val history: List<String> get() = spoken

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
        val nowMs = advance(timeMs)
        log.write("scan", "speech done, scanning again")
        speaking = false
        scanner.restart(board.cards, nowMs)
        if (detector.tracking) scanner.resume(nowMs)
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
        if (speaking) {
            log.write("scan", "blink ignored while speaking")
            return
        }
        val card = scanner.cardAt(blink.startMs)
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
            scanner.restart(board.cards, nowMs)
            return
        }
        spoken += sentence
        board.home()
        speaking = true
        scanner.pause(nowMs)
        log.write("scan", "say \"$sentence\"")
        speak(sentence)
    }

    private fun publish(nowMs: Long) {
        if (scanner.cards != board.cards) scanner.restart(board.cards, nowMs)
        val highlighted = if (speaking) -1 else scanner.cardAt(nowMs) ?: -1
        if (highlighted != lastHighlighted && highlighted != -1) log.write("scan", "highlight ${label(highlighted)}")
        lastHighlighted = highlighted
        _ui.value = ConversationUi(
            faceFound = detector.tracking,
            heard = null,
            replies = board.replies,
            highlighted = highlighted,
        )
    }

    private fun label(card: Int) = when (card) {
        Board.MORE_OPTIONS -> "More options"
        Board.YES_NO -> "Yes / No"
        else -> "\"${board.replies.getOrNull(card)}\""
    }
}
