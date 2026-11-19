package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutColumnsTest {

    @Test
    fun `columns survives a round trip`() {
        val settings = HomeLayoutSettings(selected = HomeLayoutKind.COLUMNS)

        val restored = HomeLayoutSettings.fromJson(settings.toJson())

        assertEquals(HomeLayoutKind.COLUMNS, restored.selected)
        assertEquals(ColumnsConfig, restored.active)
    }

    @Test
    fun `stored columns token resolves to the columns layout`() {
        val stored = """{"selected":"COLUMNS","columns":{}}"""

        assertEquals(HomeLayoutKind.COLUMNS, HomeLayoutSettings.fromJson(stored).selected)
    }

    @Test
    fun `json written before columns existed keeps its layout`() {
        val legacy = """{"selected":"CUSTOM_GRID","carousel":{},"autoGrid":{},"customGrid":{"columns":4,"rows":3}}"""

        val restored = HomeLayoutSettings.fromJson(legacy)

        assertEquals(HomeLayoutKind.CUSTOM_GRID, restored.selected)
        assertEquals(GridAxis.Fixed(4), restored.customGrid.columns)
        assertEquals(ColumnsConfig, restored.columns)
    }

    @Test
    fun `an absent layout key still resolves to the carousel`() {
        assertEquals(HomeLayoutKind.CAROUSEL, HomeLayoutSettings.fromJson(null).selected)
    }
}
