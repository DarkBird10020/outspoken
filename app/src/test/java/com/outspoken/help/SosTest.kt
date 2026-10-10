package com.outspoken.help

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SosTest {

    @Test
    fun `a typed number keeps its digits and a leading plus`() {
        assertEquals("+919876543210", sosNumber("+91 98765-43210"))
        assertEquals("09876543210", sosNumber("098765 43210"))
    }

    @Test
    fun `text that cannot be a number is refused`() {
        assertNull(sosNumber(""))
        assertNull(sosNumber("call mum"))
        assertNull(sosNumber("12"))
        assertNull(sosNumber("91+98765"))
        assertNull(sosNumber("1234567890123456"))
    }

    @Test
    fun `emergency numbers are told apart, as an app cannot call them`() {
        assertTrue(isEmergencyNumber("112"))
        assertTrue(isEmergencyNumber("108"))
        assertFalse(isEmergencyNumber("+919876543210"))
    }

    @Test
    fun `the log only gets the last digits`() {
        assertEquals("…3210", maskedNumber("+919876543210"))
    }

    @Test
    fun `the text says help is needed, when, and the last thing said`() {
        assertEquals(
            "Outspoken: help needed now. Eyes were held shut to call for help at 18:42. Last said: \"I am in pain\".",
            sosMessage("18:42", "I am in pain"),
        )
        assertEquals("Outspoken: help needed now. Eyes were held shut to call for help at 18:42.", sosMessage("18:42", ""))
    }
}
