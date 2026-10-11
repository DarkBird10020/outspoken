package com.outspoken.setup

import com.outspoken.conversation.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class EyeResetTest {

    @Test
    fun eyeSettingsGoBackToDefaultsAndTheRestStays() {
        val changed = Tuning().let {
            it.copy(
                blink = it.blink.copy(closedBelow = 0.2f, minBlinkMs = 700),
                gaze = it.gaze.copy(lookHoldMs = 1_000, lookStrength = 0.8f),
                lookDown = true,
                winks = true,
                moveByEyes = false,
                scanMs = 2_500,
                language = AppLanguage.Hindi,
                languages = listOf(AppLanguage.Hindi, AppLanguage.Tamil),
                sosNumber = "+911234567890",
                sosWhatsApp = true,
                handGestures = true,
                helpConfirm = false,
            )
        }
        val reset = changed.withEyeDefaults()
        val defaults = Tuning()
        assertEquals(defaults.blink, reset.blink)
        assertEquals(defaults.gaze, reset.gaze)
        assertEquals(defaults.lookDown, reset.lookDown)
        assertEquals(defaults.winks, reset.winks)
        assertEquals(defaults.moveByEyes, reset.moveByEyes)
        assertEquals(defaults.scanMs, reset.scanMs)
        assertEquals(changed.copy(blink = reset.blink, gaze = reset.gaze, lookDown = reset.lookDown, winks = reset.winks, moveByEyes = reset.moveByEyes, scanMs = reset.scanMs), reset)
    }
}
