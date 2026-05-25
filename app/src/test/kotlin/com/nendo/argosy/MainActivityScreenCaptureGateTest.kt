package com.nendo.argosy

import com.nendo.argosy.data.preferences.UserPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityScreenCaptureGateTest {

    @Test
    fun `returns true only when ambient LED and screen colors are both enabled`() {
        assertTrue(
            shouldInitializeScreenCapture(
                UserPreferences(ambientLedEnabled = true, ambientLedScreenEnabled = true)
            )
        )

        assertFalse(
            shouldInitializeScreenCapture(
                UserPreferences(ambientLedEnabled = true, ambientLedScreenEnabled = false)
            )
        )

        assertFalse(
            shouldInitializeScreenCapture(
                UserPreferences(ambientLedEnabled = false, ambientLedScreenEnabled = true)
            )
        )

        assertFalse(
            shouldInitializeScreenCapture(
                UserPreferences(ambientLedEnabled = false, ambientLedScreenEnabled = false)
            )
        )
    }
}
