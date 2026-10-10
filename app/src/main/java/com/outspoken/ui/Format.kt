package com.outspoken.ui

import com.outspoken.hand.HandReading
import java.util.Locale
import kotlin.math.roundToInt

private const val UNKNOWN = "-"

/** "1.3 s", one decimal place. */
fun formatSeconds(seconds: Float?): String =
    seconds?.let { String.format(Locale.US, "%.1f s", it) } ?: UNKNOWN

/** A whole number with an optional unit, for example "18 tok/s" or "36 °C". */
fun formatWhole(value: Float?, unit: String = ""): String =
    value?.let { "${it.roundToInt()}$unit" } ?: UNKNOWN

/** One decimal place with a unit, for example "36.3 °C". */
fun formatTenths(value: Float?, unit: String = ""): String =
    value?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: UNKNOWN

/** Every number on the stats screen, written as the screen writes it, for the log. */
fun describeStats(ui: StatsUi): String =
    "reply ${formatSeconds(ui.replyTimeSeconds)}, ${formatWhole(ui.tokensPerSecond)} tok/s, ${ui.repliesWritten} replies written, " +
        "${formatTenths(ui.phoneTempCelsius, " °C")}, blink accuracy ${formatWhole(ui.blinkAccuracyPercent, "%")}, " +
        "session ${formatClock(ui.sessionMillis)}, ${ui.sentencesSpoken} sentences spoken"

/** The live model numbers for the main page, one value each: "Reply 0.9 s", "61 tok/s", "8 replies written". */
fun liveStatsLine(replyTimeSeconds: Float?, tokensPerSecond: Float?, repliesWritten: Int): List<String> =
    listOf(
        "Reply ${formatSeconds(replyTimeSeconds)}",
        "${formatWhole(tokensPerSecond)} tok/s",
        "$repliesWritten replies written",
    )

/** What the camera reads from the hand now, for the settings screen: "Seen now: ✌️ lights card 2 (1.00)". */
fun describeHandReading(reading: HandReading): String {
    val sign = reading.sign
    return when {
        sign != null -> "Seen now: ${sign.symbol} ${sign.meaning} (${String.format(Locale.US, "%.2f", reading.score)})"
        reading.fingers != null -> "A hand with ${reading.fingers} fingers out, no sign"
        else -> "No sign seen. Hold a hand up where the camera can see it."
    }
}

/** Session length as minutes and seconds, "MM:SS". */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** One line about the last reply request, for the check screen: "1.4 s from the model, 24 tok/s". */
fun describeReplies(elapsedMs: Long, fromModel: Boolean, tokensPerSecond: Float?): String {
    val time = formatSeconds(elapsedMs / 1000f)
    if (!fromModel) return "$time, phrase bank (model failed or gave a bad answer twice)"
    return "$time from the model" + (tokensPerSecond?.let { ", ${formatWhole(it, " tok/s")}" } ?: "")
}
