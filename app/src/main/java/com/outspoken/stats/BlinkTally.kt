package com.outspoken.stats

/** Closes made to choose something, and how many the phone caught (PRD S1 blink accuracy). */
data class BlinkTally(val caught: Int = 0, val missed: Int = 0) {

    /** Share caught, 0 to 100; null before the first try. */
    val percent: Float? get() = (caught + missed).takeIf { it > 0 }?.let { caught * 100f / it }

    operator fun plus(other: BlinkTally) = BlinkTally(caught + other.caught, missed + other.missed)
}
