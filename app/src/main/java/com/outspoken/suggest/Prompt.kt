package com.outspoken.suggest

/** Turns older than this are left out to keep the prompt, and so the reply time, short. */
const val PROMPT_TURNS = 6

fun buildPrompt(request: SuggestionRequest): String = buildString {
    appendLine("You suggest replies for a person who cannot move or speak. They pick one by blinking, and the phone says it aloud.")
    appendLine("Write 4 different replies they may want to say next. If the last line is a question, answer it.")
    appendLine("Each reply: first person, plain everyday words, at most 8 words.")
    appendLine("Answer with only a JSON list of 4 strings.")
    appendLine()
    // One worked example: small models copy the shape of an example far more reliably than they
    // follow a description of it.
    appendLine("Example. Question: Do you want some water?")
    appendLine("Answer: ${EXAMPLE_ANSWER}")
    appendLine()
    appendLine("It is ${partOfDay(request.hourOfDay)}.")
    val turns = request.turns.takeLast(PROMPT_TURNS)
    if (turns.isEmpty()) {
        appendLine("Nothing has been said yet.")
    } else {
        appendLine("Conversation so far:")
        turns.forEach { appendLine("${if (it.fromListener) "Visitor" else "Person"}: ${it.text}") }
    }
    append("Answer:")
}

const val EXAMPLE_ANSWER = """["Yes, please", "A little, thank you", "No, I am fine", "Can I have juice instead"]"""

fun partOfDay(hour: Int): String = when (hour) {
    in 5..11 -> "morning"
    in 12..16 -> "afternoon"
    in 17..20 -> "evening"
    else -> "night"
}
