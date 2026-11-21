package com.nendo.argosy.ui.screens.home

import com.nendo.argosy.domain.model.HomeColumnCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `a phone held landscape shows seven columns`() {
        assertEquals(7, homeColumnsSlotCount(screenWidth = 915f, screenHeight = 412f))
    }

    @Test
    fun `a portrait screen shows one column`() {
        assertEquals(1, homeColumnsSlotCount(screenWidth = 412f, screenHeight = 915f))
    }

    @Test
    fun `a square screen counts as portrait`() {
        assertEquals(1, homeColumnsSlotCount(screenWidth = 600f, screenHeight = 600f))
    }

    @Test
    fun `just past square shows three columns`() {
        assertEquals(3, homeColumnsSlotCount(screenWidth = 601f, screenHeight = 600f))
    }

    @Test
    fun `the wide boundary at 1 point 6 shows five columns`() {
        assertEquals(3, homeColumnsSlotCount(screenWidth = 159f, screenHeight = 100f))
        assertEquals(5, homeColumnsSlotCount(screenWidth = 160f, screenHeight = 100f))
    }

    @Test
    fun `the ultra wide boundary at 2 shows seven columns`() {
        assertEquals(5, homeColumnsSlotCount(screenWidth = 199f, screenHeight = 100f))
        assertEquals(7, homeColumnsSlotCount(screenWidth = 200f, screenHeight = 100f))
    }

    @Test
    fun `a fixed column count ignores the screen`() {
        assertEquals(3, homeColumnsSlotCount(915f, 412f, HomeColumnCount.COMPACT))
        assertEquals(5, homeColumnsSlotCount(412f, 915f, HomeColumnCount.WIDE))
        assertEquals(7, homeColumnsSlotCount(853f, 640f, HomeColumnCount.ULTRA_WIDE))
    }

    @Test
    fun `auto follows the ratio rule`() {
        assertEquals(
            homeColumnsSlotCount(853f, 480f),
            homeColumnsSlotCount(853f, 480f, HomeColumnCount.AUTO)
        )
        assertNull(HomeColumnCount.AUTO.fixedSlots)
    }
}
