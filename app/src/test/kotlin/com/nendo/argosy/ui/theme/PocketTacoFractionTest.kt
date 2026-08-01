package com.nendo.argosy.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

private const val PORTRAIT_W = 412
private const val PORTRAIT_H = 915
private const val LANDSCAPE_W = 915
private const val LANDSCAPE_H = 412

class PocketTacoFractionTest {

    @Test
    fun `enabled in portrait reserves the configured percent`() {
        assertEquals(
            0.20f,
            resolvePocketTacoFraction(true, 20, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
    }

    @Test
    fun `enabled in landscape reserves nothing`() {
        assertEquals(
            0f,
            resolvePocketTacoFraction(true, 20, LANDSCAPE_W, LANDSCAPE_H),
            0.0001f
        )
    }

    @Test
    fun `disabled reserves nothing even in portrait`() {
        assertEquals(
            0f,
            resolvePocketTacoFraction(false, 40, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
    }

    @Test
    fun `secondary display never reserves even when enabled in portrait`() {
        assertEquals(
            0f,
            resolvePocketTacoFraction(true, 40, PORTRAIT_W, PORTRAIT_H, isSecondaryDisplay = true),
            0.0001f
        )
    }

    @Test
    fun `a square display is not portrait`() {
        assertEquals(
            0f,
            resolvePocketTacoFraction(true, 20, 800, 800),
            0.0001f
        )
    }

    @Test
    fun `a portrait tablet still reserves`() {
        assertEquals(
            0.15f,
            resolvePocketTacoFraction(true, 15, 800, 1280),
            0.0001f
        )
    }

    @Test
    fun `percent is clamped into a usable fraction`() {
        assertEquals(0f, resolvePocketTacoFraction(true, -10, PORTRAIT_W, PORTRAIT_H), 0.0001f)
        assertEquals(1f, resolvePocketTacoFraction(true, 250, PORTRAIT_W, PORTRAIT_H), 0.0001f)
    }
}
