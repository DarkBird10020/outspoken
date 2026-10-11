package com.outspoken.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A press by eye that opened another app, told from the app in front after it. */
class OpenedAppTest {

    @Test
    fun `another app in front soon after a press is an app opened, once`() {
        val opened = OpenedApp()
        // The 09:40 run: a tap on the home screen, Contacts in front 1.1 s later.
        opened.pressed("com.android.launcher3", 47_424)
        assertFalse(opened.onScreen("com.android.launcher3", 47_900))
        assertTrue(opened.onScreen("com.android.contacts", 48_573))
        assertFalse(opened.onScreen("com.android.contacts", 48_900))
    }

    @Test
    fun `a new app long after the press, or with no press, was not opened by eye`() {
        val opened = OpenedApp()
        assertFalse(opened.onScreen("com.android.contacts", 10_000))
        opened.pressed("com.android.launcher3", 20_000)
        assertFalse(opened.onScreen("com.android.contacts", 23_500))
    }

    @Test
    fun `no app read is not an app opened`() {
        val opened = OpenedApp()
        opened.pressed("com.android.launcher3", 1_000)
        assertFalse(opened.onScreen(null, 1_500))
        assertTrue(opened.onScreen("com.whatsapp", 2_000))
    }
}
