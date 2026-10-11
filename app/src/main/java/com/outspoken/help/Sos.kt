package com.outspoken.help

/**
 * The number as the phone dials it ("+91 98765-43210" -> "+919876543210"), or null when it
 * cannot be a phone number: 3 to 15 digits, with a plus only at the start.
 */
fun sosNumber(typed: String): String? {
    val kept = typed.filter { it.isDigit() || it == '+' }
    if (kept.drop(1).contains('+')) return null
    val digits = kept.filter { it.isDigit() }
    if (digits.length !in 3..15) return null
    return (if (kept.startsWith("+")) "+" else "") + digits
}

/**
 * Emergency numbers (India's 100, 101, 102, 108 and 112, and 911). Android does not let an app
 * call these itself; it opens the dialer instead, where nobody in bed can press call.
 */
fun isEmergencyNumber(number: String): Boolean = number.removePrefix("+") in EMERGENCY

/** The last digits only, for the log: the run logs get shared, the number stays out of them. */
fun maskedNumber(number: String): String = "…" + number.takeLast(4)

/** The text the contact gets, before the call. [time] as the phone shows it, "18:42". */
fun sosMessage(time: String, lastSaid: String?): String =
    "Outspoken: help needed now. Eyes were held shut to call for help at $time." +
        (lastSaid?.takeIf { it.isNotBlank() }?.let { " Last said: \"$it\"." } ?: "")

/** The SIM an SOS text goes out on, and why that one, for the log. */
data class SimChoice(val subId: Int?, val why: String)

/**
 * The SIM for the SOS text: the phone's SIM for texts, else its default SIM (the one for calls,
 * else the first SIM). Picked here, as with none set for texts Android would ask, and nobody in
 * bed can answer. [setFor] and [phoneDefault] below 0 mean none.
 */
fun sosSim(setFor: Int, phoneDefault: Int, use: String): SimChoice = when {
    setFor >= 0 -> SimChoice(setFor, "the phone's SIM for $use")
    phoneDefault >= 0 -> SimChoice(phoneDefault, "no SIM set for $use, so the phone's default SIM")
    else -> SimChoice(null, "no SIM found")
}

private val EMERGENCY = setOf("100", "101", "102", "108", "112", "911")
