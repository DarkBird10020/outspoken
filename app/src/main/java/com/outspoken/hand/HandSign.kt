package com.outspoken.hand

/** The sign read now, how sure the model is, and the fingers counted; for the settings screen. */
data class HandReading(val sign: HandSign?, val score: Float, val fingers: Int? = null)

/** What a held hand sign does: says a phrase, lights one of the cards, or says the lit card. */
sealed interface HandAction {
    data class Say(val text: String) : HandAction

    /** Moves the highlight to the card at [index] (0 is the first reply), without saying it. */
    data class Light(val index: Int) : HandAction
    data object ChooseLit : HandAction
}

/**
 * The hand signs and what each does. One to four fingers light the first to the fourth card and a
 * fist says the lit card, so any card can be chosen by hand in two steps, as with the eyes: a
 * count read on the way to another (one and two pass by on the way to three) only lights a card,
 * it never says one. Thumbs up and down, the I-love-you hand and the whole hand open (owner: "full
 * hand open means please wait") say their phrase at once. [key] is how a sign is saved in the
 * settings.
 */
enum class HandSign(val key: String, val symbol: String, val action: HandAction) {
    ThumbUp("Thumb_Up", "👍", HandAction.Say("Yes")),
    ThumbDown("Thumb_Down", "👎", HandAction.Say("No")),
    LoveYou("ILoveYou", "🤟", HandAction.Say("I love you")),
    OneFinger("One_Finger", "☝️", HandAction.Light(0)),
    TwoFingers("Two_Fingers", "✌️", HandAction.Light(1)),
    ThreeFingers("Three_Fingers", "3️⃣", HandAction.Light(2)),
    FourFingers("Four_Fingers", "4️⃣", HandAction.Light(3)),
    OpenPalm("Open_Palm", "✋", HandAction.Say("Please wait")),
    ClosedFist("Closed_Fist", "✊", HandAction.ChooseLit);

    /** What it does, in a few words for the settings screen. */
    val meaning: String
        get() = when (action) {
            is HandAction.Say -> "says \"${action.text}\""
            is HandAction.Light -> "lights card ${action.index + 1}"
            HandAction.ChooseLit -> "says the lit card"
        }

    companion object {
        fun fromKey(key: String?): HandSign? = entries.firstOrNull { it.key == key }

        /**
         * The sign for one reading: the model's own label for the thumbs, the I-love-you hand, the
         * open hand and the fist; its pointing up and victory as one and two fingers; and the
         * counted [fingers] when the model has no label for the hand ("None"), as three and four
         * fingers (thumb folded) are not among its signs.
         */
        fun read(label: String?, fingers: Int?): HandSign? = when (label) {
            "Thumb_Up" -> ThumbUp
            "Thumb_Down" -> ThumbDown
            "ILoveYou" -> LoveYou
            "Closed_Fist" -> ClosedFist
            "Pointing_Up" -> OneFinger
            "Victory" -> TwoFingers
            "Open_Palm" -> OpenPalm
            "None" -> when (fingers) {
                1 -> OneFinger
                2 -> TwoFingers
                3 -> ThreeFingers
                4 -> FourFingers
                else -> null
            }
            else -> null
        }
    }
}
