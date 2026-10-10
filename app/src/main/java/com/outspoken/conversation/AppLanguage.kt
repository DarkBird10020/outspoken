package com.outspoken.conversation

/**
 * The language of the cards, the phrase bank, the topic buttons and the phone's voice, and the
 * one the visitor is heard in (owner request: cards in Hindi too). The replies themselves come
 * from the on-device model, asked to write in this language; nothing is translated, so nothing
 * leaves the phone and no extra model is needed.
 */
enum class AppLanguage(
    val label: String,
    /** BCP 47 tags for the speech recogniser, best first; null leaves the English choice to the app. */
    val speechTags: List<String>?,
    /** ISO 639 code of the offline voice to speak with. */
    val voice: String,
    val phrases: List<String>,
    val yesNo: List<String>,
    /** One-tap questions for the visitor: button label to question. */
    val topics: List<Pair<String, String>>,
    /** The three fixed cards on the main page: more options, yes / no, say anything. */
    val fixedCards: List<String>,
    /** Built-in first words for "Say anything" before the model answers. */
    val firstWords: List<String>,
) {
    English(
        label = "English",
        speechTags = null,
        voice = "en",
        phrases = listOf(
            "I need water",
            "I am in pain",
            "Please call the nurse",
            "I need the toilet",
            "I am too hot",
            "I am too cold",
            "Thank you",
        ),
        yesNo = listOf("Yes", "No"),
        topics = listOf(
            "Pain" to "Are you in pain?",
            "Comfort" to "Are you comfortable?",
            "Food and drink" to "Are you hungry or thirsty?",
            "Feelings" to "How are you feeling?",
            "Family" to "Do you want to see your family?",
        ),
        fixedCards = listOf("More options", "Yes / No", "Say anything"),
        firstWords = listOf("I", "Please", "My", "Can you", "I need", "I want", "I feel", "Thank you", "Yes", "No", "Where is", "When"),
    ),
    Hindi(
        label = "हिन्दी",
        speechTags = listOf("hi-IN"),
        voice = "hi",
        phrases = listOf(
            "मुझे पानी चाहिए",
            "मुझे दर्द हो रहा है",
            "कृपया नर्स को बुलाइए",
            "मुझे शौचालय जाना है",
            "मुझे बहुत गर्मी लग रही है",
            "मुझे बहुत ठंड लग रही है",
            "धन्यवाद",
        ),
        yesNo = listOf("हाँ", "नहीं"),
        topics = listOf(
            "दर्द" to "क्या आपको दर्द हो रहा है?",
            "आराम" to "क्या आप आराम से हैं?",
            "खाना-पानी" to "क्या आपको भूख या प्यास लगी है?",
            "हालचाल" to "आप कैसा महसूस कर रहे हैं?",
            "परिवार" to "क्या आप अपने परिवार से मिलना चाहते हैं?",
        ),
        fixedCards = listOf("और विकल्प", "हाँ / नहीं", "कुछ भी कहें"),
        firstWords = listOf("मुझे", "कृपया", "मैं", "मेरा", "क्या", "हाँ", "नहीं", "धन्यवाद", "अभी", "थोड़ा", "पानी", "दर्द"),
    );

    companion object {
        fun fromName(name: String?): AppLanguage = entries.firstOrNull { it.name == name } ?: English
    }
}
