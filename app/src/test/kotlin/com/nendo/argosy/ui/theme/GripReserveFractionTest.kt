package com.nendo.argosy.ui.theme

import com.nendo.argosy.data.preferences.DisplayPreferences
import com.nendo.argosy.data.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

private const val PORTRAIT_W = 412
private const val PORTRAIT_H = 915
private const val LANDSCAPE_W = 915
private const val LANDSCAPE_H = 412

class GripReserveFractionTest {

    @Test
    fun `enabled in portrait reserves the configured percent`() {
        assertEquals(
            0.20f,
            resolveGripReserveFraction(true, 20, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
    }

    @Test
    fun `enabled in landscape reserves nothing`() {
        assertEquals(
            0f,
            resolveGripReserveFraction(true, 20, LANDSCAPE_W, LANDSCAPE_H),
            0.0001f
        )
    }

    @Test
    fun `disabled reserves nothing even in portrait`() {
        assertEquals(
            0f,
            resolveGripReserveFraction(false, 40, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
    }

    @Test
    fun `secondary display never reserves even when enabled in portrait`() {
        assertEquals(
            0f,
            resolveGripReserveFraction(true, 40, PORTRAIT_W, PORTRAIT_H, isSecondaryDisplay = true),
            0.0001f
        )
    }

    @Test
    fun `a square display is not portrait`() {
        assertEquals(
            0f,
            resolveGripReserveFraction(true, 20, 800, 800),
            0.0001f
        )
    }

    @Test
    fun `a portrait tablet still reserves`() {
        assertEquals(
            0.15f,
            resolveGripReserveFraction(true, 15, 800, 1280),
            0.0001f
        )
    }

    @Test
    fun `the stored defaults agree with the constant the ui defaults from`() {
        assertEquals(GRIP_RESERVE_DEFAULT_PERCENT, DisplayPreferences().gripReservePercent)
        assertEquals(GRIP_RESERVE_DEFAULT_PERCENT, UserPreferences().gripReservePercent)
    }

    @Test
    fun `the default sits inside the range the settings slider offers`() {
        assertEquals(
            GRIP_RESERVE_DEFAULT_PERCENT,
            GRIP_RESERVE_DEFAULT_PERCENT.coerceIn(GRIP_RESERVE_MIN_PERCENT, GRIP_RESERVE_MAX_PERCENT)
        )
    }

    @Test
    fun `a percent outside the settings range is pulled back into it`() {
        assertEquals(
            GRIP_RESERVE_MIN_PERCENT / 100f,
            resolveGripReserveFraction(true, -10, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
        assertEquals(
            GRIP_RESERVE_MAX_PERCENT / 100f,
            resolveGripReserveFraction(true, 250, PORTRAIT_W, PORTRAIT_H),
            0.0001f
        )
    }
}
