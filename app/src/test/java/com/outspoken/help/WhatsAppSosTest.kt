package com.outspoken.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The SOS over WhatsApp on a phone with no SIM (owner request, 2026-10-11). */
class WhatsAppSosTest {

    @Test
    fun `WhatsApp takes the number with its country code, without the plus`() {
        assertEquals("919876548548", whatsAppNumber("+919876548548"))
        // No country code: WhatsApp cannot tell whose number it is.
        assertNull(whatsAppNumber("9876548548"))
        assertNull(whatsAppNumber("+91"))
    }

    @Test
    fun `a number saved without the country code gets the phone's own`() {
        // 06:55:51 to 06:59:14 on the phone: saved without "+91", every WhatsApp SOS was skipped.
        assertEquals("919876548548", whatsAppNumber("9876548548", "91"))
        assertEquals("919876548548", whatsAppNumber("09876548548", "91"))
        assertEquals("919876548548", whatsAppNumber("919876548548", "91"))
        // A 10-digit number that starts with 91 is still a national one.
        assertEquals("919123456789", whatsAppNumber("9123456789", "91"))
        // The saved code wins over the phone's.
        assertEquals("447700900123", whatsAppNumber("+447700900123", "91"))
        assertNull(whatsAppNumber("12345", "91"))
        assertEquals("91", callingCode("in"))
        assertNull(callingCode("ZZ"))
    }

    @Test
    fun `the chat link carries the message, spaces and quotes encoded`() {
        assertEquals(
            "https://api.whatsapp.com/send?phone=919876548548&text=Help%20needed%20now.%20Last%20said%3A%20%22I%20am%20in%20pain%22.",
            whatsAppChatLink("919876548548", "Help needed now. Last said: \"I am in pain\"."),
        )
        assertEquals("https://api.whatsapp.com/send?phone=919876548548", whatsAppChatLink("919876548548", ""))
    }

    @Test
    fun `a message is sent before the call, and each can be on alone`() {
        assertEquals(listOf(WhatsAppStep.Send, WhatsAppStep.Call, WhatsAppStep.ConfirmCall), whatsAppSteps(message = true, call = true))
        assertEquals(listOf(WhatsAppStep.Send), whatsAppSteps(message = true, call = false))
        assertEquals(listOf(WhatsAppStep.Call, WhatsAppStep.ConfirmCall), whatsAppSteps(message = false, call = true))
    }

    private val chat = listOf(
        ScreenButton("com.whatsapp:id/back", "Navigate up"),
        ScreenButton("com.whatsapp:id/menuitem_video_call", "Video call"),
        ScreenButton("com.whatsapp:id/menuitem_voice_call", "Voice call"),
        ScreenButton("com.whatsapp:id/send", "Send"),
    )

    @Test
    fun `the send and voice call buttons are found by their ids, never the video call`() {
        assertEquals(3, buttonFor(WhatsAppStep.Send, chat))
        assertEquals(2, buttonFor(WhatsAppStep.Call, chat))
        assertNull(buttonFor(WhatsAppStep.Call, chat.filter { "voice" !in it.id!! }))
    }

    @Test
    fun `without ids the labels find them, in English or Hindi`() {
        assertEquals(1, buttonFor(WhatsAppStep.Send, listOf(ScreenButton(null, "Attach"), ScreenButton(null, "भेजें"))))
        assertEquals(0, buttonFor(WhatsAppStep.Call, listOf(ScreenButton(null, "Voice call"))))
        assertEquals(1, buttonFor(WhatsAppStep.ConfirmCall, listOf(ScreenButton("android:id/button2", "Cancel"), ScreenButton("android:id/button1", "Call"))))
        assertNull(buttonFor(WhatsAppStep.ConfirmCall, listOf(ScreenButton(null, "Video call"))))
    }
}
