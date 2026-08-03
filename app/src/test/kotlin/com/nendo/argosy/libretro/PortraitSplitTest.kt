package com.nendo.argosy.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitSplitTest {

    private fun assertWeights(
        expected: Triple<Float, Float, Float>,
        actual: Triple<Float, Float, Float>
    ) {
        assertEquals(expected.first, actual.first, 0.0001f)
        assertEquals(expected.second, actual.second, 0.0001f)
        assertEquals(expected.third, actual.third, 0.0001f)
    }

    @Test
    fun `no reserve keeps the game centred on the whole column`() {
        assertWeights(Triple(0f, 1f, 0f), portraitSplitWeights("Center", 0f))
    }

    @Test
    fun `no reserve puts top and bottom on the halves they always used`() {
        assertWeights(Triple(0f, 0.5f, 0.5f), portraitSplitWeights("Top", 0f))
        assertWeights(Triple(0.5f, 0.5f, 0f), portraitSplitWeights("Bottom", 0f))
    }

    @Test
    fun `a reserve takes its band off the bottom before the game is placed`() {
        assertWeights(Triple(0f, 0.8f, 0.2f), portraitSplitWeights("Center", 0.2f))
    }

    @Test
    fun `top halves what is left rather than what the screen is`() {
        assertWeights(Triple(0f, 0.4f, 0.6f), portraitSplitWeights("Top", 0.2f))
    }

    @Test
    fun `bottom parks the game directly above the reserved band`() {
        assertWeights(Triple(0.4f, 0.4f, 0.2f), portraitSplitWeights("Bottom", 0.2f))
    }

    @Test
    fun `an unknown position is treated as centre`() {
        assertWeights(portraitSplitWeights("Center", 0.3f), portraitSplitWeights("Auto", 0.3f))
    }

    @Test
    fun `weights always describe the whole column`() {
        for (position in listOf("Top", "Center", "Bottom")) {
            for (reserved in listOf(0f, 0.05f, 0.2f, 0.4f, 0.9f)) {
                val (top, game, bottom) = portraitSplitWeights(position, reserved)
                assertEquals("$position at $reserved", 1f, top + game + bottom, 0.0001f)
            }
        }
    }

    @Test
    fun `the game never loses more than nine tenths of the column`() {
        val (_, game, bottom) = portraitSplitWeights("Center", 1.5f)
        assertEquals(0.9f, bottom, 0.0001f)
        assertTrue(game > 0f)
    }
}
