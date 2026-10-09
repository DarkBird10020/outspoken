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

/** Session length as minutes and seconds, "MM:SS". */
fun formatClock(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
