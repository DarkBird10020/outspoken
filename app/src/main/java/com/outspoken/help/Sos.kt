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

private val EMERGENCY = setOf("100", "101", "102", "108", "112", "911")
