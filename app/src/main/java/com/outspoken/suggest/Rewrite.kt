package com.outspoken.suggest

import com.outspoken.conversation.AppLanguage
import com.outspoken.conversation.Script
import com.outspoken.conversation.dominantScript

/**
 * Asks the model to write a heard sentence in the language it was spoken in. Listening in Hindi,
 * the recogniser wrote English in Hindi letters ("व्हाट'स योर नेम", phone 19:18), and the owner
 * wants English shown as English, not a mix: the model turns it back into "What's your name?".
 */
fun buildRewritePrompt(text: String, spokenIn: AppLanguage): String = buildString {
    val target = "${spokenIn.englishName}, in ${spokenIn.script.promptName} script"
    val other = dominantScript(text)?.let { "${it.promptName} letters" } ?: "other letters"
    appendLine("A speech recogniser heard a visitor speak ${spokenIn.englishName} but wrote the sounds in $other.")
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
 * The model's sentence when it is in [spokenIn]'s script and about as long as what was heard;
 * null otherwise, and the caller keeps what the recogniser wrote.
 */
fun parseRewrite(output: String, heard: String, spokenIn: AppLanguage): String? {
    val line = output.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
    val sentence = line.removePrefix("Sentence:").trim().trim('"', '\u201C', '\u201D', '\'').trim()
    if (sentence.isEmpty()) return null
    if (dominantScript(sentence) != spokenIn.script || mixesScripts(sentence, spokenIn.script)) return null
    val heardWords = heard.split(Regex("\\s+")).count { it.isNotBlank() }
    val words = sentence.split(Regex("\\s+")).count { it.isNotBlank() }
    if (words > heardWords * 2 + 2) return null
    return sentence
}

/** True when [text] has letters of a script other than [script] (a half-rewritten sentence). */
private fun mixesScripts(text: String, script: Script): Boolean =
    text.any { c -> Script.of(c).let { it != null && it != script && !(script == Script.Japanese && it == Script.Han) } }

/** True when [text] is written in another script than [language]'s, as when the recogniser listened in the wrong language. */
fun inWrongScript(text: String, language: AppLanguage): Boolean {
    val script = dominantScript(text) ?: return false
    return script != language.script && !(language.script == Script.Japanese && script == Script.Han)
}

/** True when most letters are Latin, as when the recogniser writes Hindi in English letters. */
fun mostlyLatin(text: String): Boolean = dominantScript(text) == Script.Latin
