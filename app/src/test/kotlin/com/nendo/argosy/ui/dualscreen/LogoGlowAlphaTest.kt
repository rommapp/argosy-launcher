package com.nendo.argosy.ui.dualscreen

import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoGlowAlphaTest {

    private val logo = PresentationStyle(layout = PresentationLayout.LOGO)

    @Test
    fun `no shade draws no glow behind the logo`() {
        assertEquals(0f, logoGlowAlpha(logo.copy(scrim = PresentationScrim.NONE, scrimStrength = 90)))
    }

    @Test
    fun `a shade at zero strength draws no glow`() {
        PresentationScrim.entries.forEach { scrim ->
            assertEquals(scrim.name, 0f, logoGlowAlpha(logo.copy(scrim = scrim, scrimStrength = 0)))
        }
    }

    @Test
    fun `the default strength keeps the glow it always had and stronger shades darken it`() {
        assertEquals(0.75f, logoGlowAlpha(logo.copy(scrim = PresentationScrim.GRADIENT, scrimStrength = 90)), 0.0001f)
        assertTrue(logoGlowAlpha(logo.copy(scrimStrength = 50)) < logoGlowAlpha(logo.copy(scrimStrength = 90)))
        assertTrue(logoGlowAlpha(logo.copy(scrimStrength = 100)) <= 1f)
    }
}
