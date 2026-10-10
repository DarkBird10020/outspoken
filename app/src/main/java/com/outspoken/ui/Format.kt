package com.outspoken.ui

import java.util.Locale
import kotlin.math.roundToInt

private const val UNKNOWN = "-"

/** "1.3 s", one decimal place. */
fun formatSeconds(seconds: Float?): String =
    seconds?.let { String.format(Locale.US, "%.1f s", it) } ?: UNKNOWN

/** A whole number with an optional unit, for example "18 tok/s" or "36 °C". */
fun formatWhole(value: Float?, unit: String = ""): String =
    value?.let { "${it.roundToInt()}$unit" } ?: UNKNOWN

/** One decimal place with a unit, for example "33.8 °C". */
fun formatTenths(value: Float?, unit: String = ""): String =
    value?.let { String.format(Locale.US, "%.1f%s", it, unit) } ?: UNKNOWN

/** The model's live numbers in one line for the main page: "Reply 0.9 s · 61 tok/s · 8 replies written". */
fun liveStatsLine(replyTimeSeconds: Float?, tokensPerSecond: Float?, repliesWritten: Int): String =
    "Reply ${formatSeconds(replyTimeSeconds)} · ${formatWhole(tokensPerSecond)} tok/s · $repliesWritten replies written"

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
