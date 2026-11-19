package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.HeroFocus
import com.nendo.argosy.data.model.HeroFocusSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class HeroFocusAnalyzerTest {

    private val spec = HeroFocusSpec(narrowAspect = 0.45f, wideAspect = 0.9f, maxZoom = 1.15f, analysisSize = 128)
    private val background = 0xFF5A6470.toInt()

    private fun flat(width: Int, height: Int) = IntArray(width * height) { background }

    private fun withDetail(
        pixels: IntArray,
        width: Int,
        left: Int,
        top: Int,
        size: Int
    ): IntArray {
        var seed = 12345
        for (y in top until top + size) {
            for (x in left until left + size) {
                seed = seed * 1103515245 + 12345
                val level = (seed ushr 16) and 0xFF
                pixels[y * width + x] = (0xFF shl 24) or (level shl 16) or (((level * 3) and 0xFF) shl 8) or (255 - level)
            }
        }
        return pixels
    }

    private fun windowCentre(bias: Float, imageWidth: Int, imageHeight: Int, aspect: Float, zoom: Float): Float {
        val windowHeight = (imageHeight / zoom).roundToInt()
        val windowWidth = (windowHeight * aspect).roundToInt()
        return (1f + bias) / 2f * (imageWidth - windowWidth) + windowWidth / 2f
    }

    @Test
    fun `a detailed subject on the right of wide art pans the side column right`() {
        val pixels = withDetail(flat(128, 40), width = 128, left = 96, top = 14, size = 12)

        val focus = HeroFocusAnalyzer.analyze(pixels, 128, 40, spec)

        assertTrue("narrowX was ${focus.narrowX}", focus.narrowX > 0.3f)
        assertTrue("wideX was ${focus.wideX}", focus.wideX > 0.3f)
    }

    @Test
    fun `flat art stays centred`() {
        val focus = HeroFocusAnalyzer.analyze(flat(128, 40), 128, 40, spec)

        assertEquals(HeroFocus.CENTERED, focus)
    }

    @Test
    fun `art no wider than the focused column skips the scan and stays centred`() {
        val pixels = withDetail(flat(56, 64), width = 56, left = 40, top = 20, size = 12)

        val focus = HeroFocusAnalyzer.analyze(pixels, 56, 64, spec)

        assertEquals(HeroFocus.CENTERED, focus)
    }

    @Test
    fun `art busy everywhere stays centred`() {
        val pixels = withDetail(flat(128, 40), width = 128, left = 0, top = 0, size = 40)
            .let { withDetail(it, width = 128, left = 40, top = 0, size = 40) }
            .let { withDetail(it, width = 128, left = 80, top = 0, size = 40) }

        val focus = HeroFocusAnalyzer.analyze(pixels, 128, 40, spec)

        assertTrue("narrowX was ${focus.narrowX}", abs(focus.narrowX) < 0.1f)
        assertTrue("wideX was ${focus.wideX}", abs(focus.wideX) < 0.1f)
    }

    @Test
    fun `a subject at the far edge never pulls the crop past the cap`() {
        val pixels = withDetail(flat(160, 40), width = 160, left = 148, top = 14, size = 12)

        val focus = HeroFocusAnalyzer.analyze(pixels, 160, 40, spec)

        assertTrue("narrowX was ${focus.narrowX}", focus.narrowX in 0.6f..0.65f)
        assertEquals(1f, focus.zoom)
    }

    @Test
    fun `an off-centre subject gets a different bias per column width with both windows on it`() {
        val subjectCentre = 85f
        val pixels = withDetail(flat(128, 40), width = 128, left = 79, top = 14, size = 12)

        val focus = HeroFocusAnalyzer.analyze(pixels, 128, 40, spec)

        assertNotEquals(focus.narrowX, focus.wideX)
        val narrowCentre = windowCentre(focus.narrowX, 128, 40, spec.narrowAspect, 1f)
        val wideCentre = windowCentre(focus.wideX, 128, 40, spec.wideAspect, focus.zoom)
        assertTrue("narrow window centred at $narrowCentre", abs(narrowCentre - subjectCentre) < 8f)
        assertTrue("wide window centred at $wideCentre", abs(wideCentre - subjectCentre) < 8f)
    }
}
