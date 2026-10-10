package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage

/** Turns older than this are left out to keep the prompt, and so the reply time, short. */
const val PROMPT_TURNS = 6

fun buildPrompt(request: SuggestionRequest): String = buildString {
    // Without the setting the model wrote small talk, and lines for the visitor to say ("Good
    // night, sleep well", "I want a glass of wine"; 03:20 to 03:22 phone runs).
    appendLine("You suggest replies for a person in bed who cannot move or speak, cared for by family or a nurse (the Visitor). They pick one by blinking, and the phone says it aloud.")
    appendLine("Write 4 different replies they may want to say next.")
    appendLine("Each reply: first person, plain everyday words, at most 8 words, said by the Person, never by the Visitor.")
    if (request.language == AppLanguage.Hindi) appendLine(HINDI_LINE)
    appendLine("Answer with only a JSON list of 4 strings.")
    appendLine()
    // One worked example: small models copy the shape of an example far more reliably than they
    // follow a description of it.
    if (request.language == AppLanguage.Hindi) {
        appendLine("Example. Question: क्या आपको पानी चाहिए?")
        appendLine("Answer: $EXAMPLE_ANSWER_HINDI")
    } else {
        appendLine("Example. Question: Do you want some water?")
        appendLine("Answer: ${EXAMPLE_ANSWER}")
    }
    appendLine()
    appendLine("It is ${partOfDay(request.hourOfDay)}.")
    val turns = request.turns.takeLast(PROMPT_TURNS)
    if (turns.isEmpty()) {
        appendLine("Nothing has been said yet.")
    } else {
        appendLine("Conversation so far:")
        turns.forEach { appendLine("${if (it.fromListener) "Visitor" else "Person"}: ${it.text}") }
    }
    val last = turns.lastOrNull()
    if (last != null && last.fromListener) {
        // With the mix of needs below always in, "Are you in pain" got rest and water cards and
        // no answer about pain (05:27:38 phone run). What the visitor just said comes first.
        append("The Visitor just said: \"${last.text}\". All 4 replies must reply to that directly.")
        if (isYesNoQuestion(last.text)) append(" Give a yes, a no, and two more specific answers.")
        appendLine()
    } else {
        // "and a question back" gave questions the visitor would ask ("Thirsty? Do you want
        // water?", "Do you want quiet now?"; 04:53 to 05:08 phone runs), and one was picked.
        appendLine("Mix the kinds: yes or no, and a need or feeling such as pain, thirst or rest.")
    }
    append("Answer:")
}

const val EXAMPLE_ANSWER = """["Yes, please", "A little, thank you", "No, I am fine", "Can I have juice instead"]"""

const val EXAMPLE_ANSWER_HINDI = """["हाँ, कृपया", "थोड़ा सा, धन्यवाद", "नहीं, मैं ठीक हूँ", "क्या मुझे जूस मिल सकता है"]"""

/** Asked of the model for Hindi cards: the instructions stay in English, the words it writes do not. */
const val HINDI_LINE = "Write in Hindi, in Devanagari script, the everyday Hindi a family in India speaks."

/**
 * Whether [text] asks for a yes or a no, from its first word. Heard speech comes without a
 * question mark ("Are you in pain"), often after a greeting, and casually ("Hey you need anything").
 */
fun isYesNoQuestion(text: String): Boolean {
    val words = text.lowercase().split(NOT_LETTERS).filter { it.isNotEmpty() }
    return words.firstOrNull { it !in LEAD_INS } in YES_NO_STARTS
}

private val NOT_LETTERS = Regex("[^\\p{L}\\p{M}']+")
private val LEAD_INS = setOf("hey", "hi", "hello", "so", "and", "okay", "ok", "well", "you")
private val YES_NO_STARTS = setOf(
    "are", "is", "am", "was", "were", "do", "does", "did", "can", "could", "will", "would",
    "should", "shall", "may", "have", "has", "had", "want", "need",
    // Hindi asks yes or no with "kya" at the start.
    "क्या",
)

fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    in 17..20 -> "evening"
    else -> "night"
}
