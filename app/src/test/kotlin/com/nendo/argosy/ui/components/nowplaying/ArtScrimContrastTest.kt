package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.nendo.argosy.ui.theme.argosyThemeTokens
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtScrimContrastTest {

    private val readable = 4.5f
    private val dark = argosyThemeTokens(isDark = true)
    private val light = argosyThemeTokens(isDark = false)

    private fun contrast(foreground: Color, background: Color): Float {
        val opaque = foreground.compositeOver(background)
        val lf = opaque.luminance() + 0.05f
        val lb = background.luminance() + 0.05f
        return maxOf(lf, lb) / minOf(lf, lb)
    }

    private fun scrimmed(cover: Color, isDark: Boolean): Color = artScrimFor(isDark).compositeOver(cover)

    private fun assertReadable(label: String, foreground: Color, background: Color) {
        val ratio = contrast(foreground, background)
        assertTrue("$label is $ratio:1", ratio >= readable)
    }

    @Test
    fun `dark text reads over a scrimmed pure white cover`() {
        val worst = scrimmed(Color.White, isDark = true)

        assertReadable("primary", dark.textPrimary, worst)
        assertReadable("secondary", dark.textDim, worst)
        assertReadable("accent", dark.focusAccent, worst)
    }

    @Test
    fun `light text reads over a scrimmed pure black cover`() {
        val worst = scrimmed(Color.Black, isDark = false)

        assertReadable("primary", light.textPrimary, worst)
        assertReadable("secondary", light.textDim, worst)
    }

    @Test
    fun `primary text on an accent fill is too faint for text`() {
        assertTrue(contrast(dark.textPrimary, dark.focusAccent) < readable)
    }

    @Test
    fun `muted text over a scrimmed cover is decoration, never content`() {
        assertTrue(contrast(dark.textMute, scrimmed(Color.White, isDark = true)) < readable)
    }
}
