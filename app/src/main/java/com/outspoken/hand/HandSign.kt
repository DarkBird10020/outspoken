package com.outspoken.hand

/** What a held hand sign does: says a phrase, or chooses the lit card as a blink would. */
sealed interface HandAction {
    data class Say(val text: String) : HandAction
    data object ChooseLit : HandAction
}

/**
 * The seven signs MediaPipe's gesture model knows, by the label it gives them, each with what it
 * does here. Phrases are short things a person in bed needs said at once; a fist is a second way
 * to choose, for someone whose blinks are hard to read.
 */
enum class HandSign(val label: String, val symbol: String, val action: HandAction) {
    ThumbUp("Thumb_Up", "👍", HandAction.Say("Yes")),
    ThumbDown("Thumb_Down", "👎", HandAction.Say("No")),
    Victory("Victory", "✌️", HandAction.Say("Thank you")),
    LoveYou("ILoveYou", "🤟", HandAction.Say("I love you")),
    PointingUp("Pointing_Up", "☝️", HandAction.Say("Please call the nurse")),
    OpenPalm("Open_Palm", "✋", HandAction.Say("Please wait")),
    ClosedFist("Closed_Fist", "✊", HandAction.ChooseLit);

    /** What it does, in a few words for the settings screen. */
    val meaning: String
        get() = when (action) {
            is HandAction.Say -> "says \"${action.text}\""
            HandAction.ChooseLit -> "chooses the lit card"
        }

    companion object {
        /** The sign for one of the model's labels; null for "None" or anything else. */
        fun fromLabel(label: String?): HandSign? = entries.firstOrNull { it.label == label }
    }
}
