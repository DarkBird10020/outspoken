package com.outspoken.conversation

/** The built-in phrase bank (PRD F5). Shown at once and used whenever there is nothing better. */
object PhraseBank {
    val phrases = listOf(
        "I need water",
        "I am in pain",
        "Please call the nurse",
        "I need the toilet",
        "I am too hot",
        "I am too cold",
        "Thank you",
    )
    val yesNo = listOf("Yes", "No")
}

/**
 * What the reply cards show. Card ids 0 to 3 are replies; [MORE_OPTIONS] and [YES_NO] are the
 * two fixed cards. Model suggestions, when there are any, are the first page and the phrase bank
 * follows.
 */
class Board(phrases: List<String> = PhraseBank.phrases) {

    private val phrasePages = phrases.chunked(REPLY_COUNT)
    private var suggestions: List<String>? = null
    private val pages get() = listOfNotNull(suggestions) + phrasePages
    private var page = 0
    private var showingYesNo = false

    val replies: List<String>
        get() = if (showingYesNo) PhraseBank.yesNo else pages[page]

    /** Cards in scan order. */
    val cards: List<Int>
        get() = replies.indices.toList() + listOf(MORE_OPTIONS, YES_NO)

    /** Returns the sentence to say, or null when the card only changed what is shown. */
    fun choose(card: Int): String? = when (card) {
        MORE_OPTIONS -> {
            page = if (showingYesNo) 0 else (page + 1) % pages.size
            showingYesNo = false
            null
        }
        YES_NO -> {
            showingYesNo = true
            null
        }
        else -> replies.getOrNull(card)
    }

    /** Shows [replies] as the first page, or only the phrase bank when null. Goes to the first page. */
    fun showSuggestions(replies: List<String>?) {
        suggestions = replies?.takeIf { it.isNotEmpty() }
        page = 0
        showingYesNo = false
    }

    companion object {
        const val REPLY_COUNT = 4
        const val MORE_OPTIONS = 4
        const val YES_NO = 5
    }
}
