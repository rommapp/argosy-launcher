package com.nendo.argosy.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeColumnsGeometryTest {

    @Test
    fun `a 16 by 9 handheld shows five columns`() {
        assertEquals(5, homeColumnsSlotCount(screenWidth = 853f, screenHeight = 480f))
    }

    @Test
    fun `a 4 by 3 handheld shows three columns`() {
        assertEquals(3, homeColumnsSlotCount(screenWidth = 853f, screenHeight = 640f))
    }

    @Test
    fun `a 3 by 2 handheld shows three columns`() {
        assertEquals(3, homeColumnsSlotCount(screenWidth = 960f, screenHeight = 640f))
    }

    @Test
    fun `a phone held landscape shows one column`() {
        assertEquals(1, homeColumnsSlotCount(screenWidth = 915f, screenHeight = 412f))
    }

    @Test
    fun `a portrait screen shows one column`() {
        assertEquals(1, homeColumnsSlotCount(screenWidth = 412f, screenHeight = 915f))
    }
}
