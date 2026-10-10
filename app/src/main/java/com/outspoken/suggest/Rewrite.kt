package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage

/**
 * Asks the model to write a heard sentence in the language it was spoken in. Listening in Hindi,
 * the recogniser wrote English in Hindi letters ("व्हाट'स योर नेम", phone 19:18), and the owner
 * wants English shown as English, not a mix: the model turns it back into "What's your name?".
 */
fun buildRewritePrompt(text: String, spokenIn: AppLanguage): String = buildString {
    val target = if (spokenIn == AppLanguage.Hindi) "Hindi, in Devanagari script" else "English, in English letters"
    val other = if (spokenIn == AppLanguage.Hindi) "English letters" else "Hindi (Devanagari) letters"
    appendLine("A speech recogniser heard a visitor speak ${spokenIn.label} but wrote the sounds in $other.")
    appendLine("Write the sentence they said, in $target, with normal spelling and punctuation. Do not translate it or answer it.")
    appendLine("Answer with only the sentence.")
    appendLine()
    if (spokenIn == AppLanguage.Hindi) {
        appendLine("Example. Heard: kya aapko pani chahiye")
        appendLine("Sentence: क्या आपको पानी चाहिए?")
    } else {
        appendLine("Example. Heard: हाउ आर यू फीलिंग")
        appendLine("Sentence: How are you feeling?")
    }
    appendLine()
    appendLine("Heard: $text")
    append("Sentence:")
}

/**
 * The model's sentence when it is in the right script and about as long as what was heard; null
 * otherwise, and the caller keeps what the recogniser wrote.
 */
fun parseRewrite(output: String, heard: String, spokenIn: AppLanguage): String? {
    val line = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val sentence = line.removePrefix("Sentence:").trim().trim('"', '“', '”', '\'').trim()
    if (sentence.isEmpty()) return null
    val letters = sentence.filter { it.isLetter() }
    if (letters.isEmpty()) return null
    val devanagari = letters.count { it in 'ऀ'..'ॿ' }
    val rightScript = if (spokenIn == AppLanguage.Hindi) devanagari * 2 > letters.length else devanagari == 0
    if (!rightScript) return null
    val heardWords = heard.split(Regex("\\s+")).count { it.isNotBlank() }
    val words = sentence.split(Regex("\\s+")).count { it.isNotBlank() }
    if (words > heardWords * 2 + 2) return null
    return sentence
}

/** True when most letters are Latin, as when the recogniser writes Hindi in English letters. */
fun mostlyLatin(text: String): Boolean {
    val letters = text.filter { it.isLetter() }
    return letters.isNotEmpty() && letters.count { it in 'a'..'z' || it in 'A'..'Z' } * 2 > letters.length
}
