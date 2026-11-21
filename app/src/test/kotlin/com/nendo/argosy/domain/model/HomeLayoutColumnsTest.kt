package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutColumnsTest {

    @Test
    fun `columns survives a round trip`() {
        val settings = HomeLayoutSettings(selected = HomeLayoutKind.COLUMNS)

        val restored = HomeLayoutSettings.fromJson(settings.toJson())

        assertEquals(HomeLayoutKind.COLUMNS, restored.selected)
        assertEquals(ColumnsConfig(), restored.active)
    }

    @Test
    fun `every columns option survives a round trip`() {
        val columns = ColumnsConfig(
            showAllGames = true,
            columnCount = HomeColumnCount.ULTRA_WIDE,
            hiddenThemes = setOf(HomeColumnTheme.GATHERING_DUST, HomeColumnTheme.PINNED_COLLECTION),
            showPlatformColumns = false,
            showInfoBand = false
        )
        val settings = HomeLayoutSettings(selected = HomeLayoutKind.COLUMNS, columns = columns)

        assertEquals(columns, HomeLayoutSettings.fromJson(settings.toJson()).columns)
    }

    @Test
    fun `an empty columns object loads today's behaviour`() {
        val stored = """{"selected":"COLUMNS","columns":{}}"""

        val restored = HomeLayoutSettings.fromJson(stored)

        assertEquals(HomeLayoutKind.COLUMNS, restored.selected)
        assertEquals(ColumnsConfig(), restored.columns)
        assertFalse(restored.columns.showAllGames)
        assertEquals(HomeColumnCount.AUTO, restored.columns.columnCount)
        assertTrue(restored.columns.hiddenThemes.isEmpty())
        assertTrue(restored.columns.showPlatformColumns)
        assertTrue(restored.columns.showInfoBand)
    }

    @Test
    fun `one missing columns key keeps the others`() {
        val stored = """{"selected":"COLUMNS","columns":{"showAllGames":true,"hiddenThemes":["FAVORITES"]}}"""

        val columns = HomeLayoutSettings.fromJson(stored).columns

        assertTrue(columns.showAllGames)
        assertEquals(setOf(HomeColumnTheme.FAVORITES), columns.hiddenThemes)
        assertEquals(HomeColumnCount.AUTO, columns.columnCount)
        assertTrue(columns.showPlatformColumns)
        assertTrue(columns.showInfoBand)
    }

    @Test
    fun `unknown column count and theme tokens fall back`() {
        val stored = """{"columns":{"columnCount":"NINE","hiddenThemes":["CONTINUE","RETIRED_THEME"]}}"""

        val columns = HomeLayoutSettings.fromJson(stored).columns

        assertEquals(HomeColumnCount.AUTO, columns.columnCount)
        assertEquals(setOf(HomeColumnTheme.CONTINUE), columns.hiddenThemes)
    }

    @Test
    fun `columns writes its options under the columns key as tokens`() {
        val settings = HomeLayoutSettings(
            columns = ColumnsConfig(columnCount = HomeColumnCount.COMPACT, hiddenThemes = setOf(HomeColumnTheme.JUST_ADDED))
        )

        val json = org.json.JSONObject(settings.toJson()).getJSONObject("columns")

        assertEquals("COMPACT", json.getString("columnCount"))
        assertEquals("JUST_ADDED", json.getJSONArray("hiddenThemes").getString(0))
    }

    @Test
    fun `a theme switched off and on again leaves nothing hidden`() {
        val config = ColumnsConfig()
            .withTheme(HomeColumnTheme.FAVORITES, shown = false)
            .withTheme(HomeColumnTheme.FAVORITES, shown = true)

        assertTrue(config.hiddenThemes.isEmpty())
        assertTrue(config.showsTheme(HomeColumnTheme.FAVORITES))
    }

    @Test
    fun `json written before columns existed keeps its layout`() {
        val legacy = """{"selected":"CUSTOM_GRID","carousel":{},"autoGrid":{},"customGrid":{"columns":4,"rows":3}}"""

        val restored = HomeLayoutSettings.fromJson(legacy)

        assertEquals(HomeLayoutKind.CUSTOM_GRID, restored.selected)
        assertEquals(GridAxis.Fixed(4), restored.customGrid.columns)
        assertEquals(ColumnsConfig(), restored.columns)
    }

    @Test
    fun `the auto grid's show every game does not reach columns`() {
        val stored = """{"selected":"COLUMNS","autoGrid":{"showAllGames":true},"columns":{}}"""

        val restored = HomeLayoutSettings.fromJson(stored)

        assertTrue(restored.autoGrid.showAllGames)
        assertFalse(restored.columns.showAllGames)
    }

    @Test
    fun `an absent layout key still resolves to the carousel`() {
        assertEquals(HomeLayoutKind.CAROUSEL, HomeLayoutSettings.fromJson(null).selected)
    }
}
