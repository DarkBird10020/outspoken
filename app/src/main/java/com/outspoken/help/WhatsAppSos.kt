package com.outspoken.help

import java.net.URLEncoder

/**
 * The SOS over WhatsApp, for a phone with no SIM (owner request: the owner's phone has none, so
 * no SOS text or call could go out). WhatsApp only opens the chat with the message typed in;
 * the app's accessibility helper then presses Send, and the call button, as nobody in bed can.
 * WhatsApp sends it over the internet; this app still has no internet permission.
 */

/** WhatsApp's apps, the everyday one first. */
val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * The number as WhatsApp's chat link takes it, the country code and the rest with no plus, or
 * null when the saved number has no country code: WhatsApp cannot tell whose number it is then.
 */
fun whatsAppNumber(number: String): String? =
    number.takeIf { it.startsWith("+") }?.drop(1)?.takeIf { it.length >= MIN_WHATSAPP_DIGITS }

/** WhatsApp's link that opens the chat with [number] ([whatsAppNumber]), [message] typed in. */
fun whatsAppChatLink(number: String, message: String): String =
    "https://api.whatsapp.com/send?phone=$number" +
        if (message.isEmpty()) "" else "&text=" + URLEncoder.encode(message, "UTF-8").replace("+", "%20")

/** What the helper presses in WhatsApp, in this order. */
enum class WhatsAppStep(val what: String) {
    Send("Send"),
    Call("the voice call button"),
    /** Some WhatsApp versions ask "Start voice call?" first; none on screen is not a failure. */
    ConfirmCall("Call in the \"Start voice call?\" question"),
}

/** The steps for a message and, or, a call. */
fun whatsAppSteps(message: Boolean, call: Boolean): List<WhatsAppStep> = buildList {
    if (message) add(WhatsAppStep.Send)
    if (call) {
        add(WhatsAppStep.Call)
        add(WhatsAppStep.ConfirmCall)
    }
}

/** A button on WhatsApp's screen: its view id ("com.whatsapp:id/send") and its label. */
data class ScreenButton(val id: String?, val label: String?)

/**
 * Which of [buttons] does [step], or null when none is on screen yet. View ids first, as they
 * do not change with the phone's language; then the labels in English and Hindi. A video call
 * button never matches.
 */
fun buttonFor(step: WhatsAppStep, buttons: List<ScreenButton>): Int? {
    fun id(b: ScreenButton) = b.id?.substringAfter(":id/")?.lowercase().orEmpty()
    fun label(b: ScreenButton) = b.label?.trim()?.lowercase().orEmpty()
    val byId: (ScreenButton) -> Boolean = when (step) {
        WhatsAppStep.Send -> { b -> id(b) == "send" }
        WhatsAppStep.Call -> { b -> "voice_call" in id(b) || "audio_call" in id(b) }
        WhatsAppStep.ConfirmCall -> { _ -> false }
    }
    val labels = when (step) {
        WhatsAppStep.Send -> SEND_LABELS
        WhatsAppStep.Call -> CALL_LABELS
        WhatsAppStep.ConfirmCall -> CONFIRM_LABELS
    }
    val index = buttons.indexOfFirst(byId).takeIf { it >= 0 } ?: buttons.indexOfFirst { label(it) in labels }
    return index.takeIf { it >= 0 && "video" !in id(buttons[it]) && "video" !in label(buttons[it]) }
}

private const val MIN_WHATSAPP_DIGITS = 8
private val SEND_LABELS = setOf("send", "भेजें")
private val CALL_LABELS = setOf("voice call", "audio call", "वॉइस कॉल", "ऑडियो कॉल")
private val CONFIRM_LABELS = setOf("call", "कॉल करें", "कॉल")
