package com.nendo.argosy.ui.components

import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.HomeColumnCount
import com.nendo.argosy.domain.model.HomeColumnTheme
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutColumnsFieldsTest {

    private val shape = CustomGridShape(columns = 3, rows = 3)

    private val columns = HomeLayoutSettings(selected = HomeLayoutKind.COLUMNS)

    @Test
    fun `columns offers a switch for every picked theme`() {
        val themes = homeLayoutFieldsFor(HomeLayoutKind.COLUMNS).mapNotNull { it.columnTheme }

        assertEquals(HomeColumnTheme.entries, themes)
    }

    @Test
    fun `every columns field is shown only while columns is selected`() {
        homeLayoutFieldsFor(HomeLayoutKind.COLUMNS).forEach { field ->
            assertTrue(isHomeLayoutFieldShown(columns, field))
            assertFalse(isHomeLayoutFieldShown(columns.copy(selected = HomeLayoutKind.CAROUSEL), field))
        }
    }

    @Test
    fun `confirm on a theme switch hides it and confirm again shows it`() {
        val hidden = toggleHomeLayoutField(columns, HomeLayoutSettingField.COLUMNS_THEME_GATHERING_DUST)
        val shown = toggleHomeLayoutField(hidden, HomeLayoutSettingField.COLUMNS_THEME_GATHERING_DUST)

        assertEquals(setOf(HomeColumnTheme.GATHERING_DUST), hidden.columns.hiddenThemes)
        assertTrue(shown.columns.hiddenThemes.isEmpty())
    }

    @Test
    fun `left turns a theme off and right turns it on`() {
        val off = adjustHomeLayoutField(columns, HomeLayoutSettingField.COLUMNS_THEME_FAVORITES, -1, shape)
        val on = adjustHomeLayoutField(off, HomeLayoutSettingField.COLUMNS_THEME_FAVORITES, 1, shape)

        assertFalse(off.columns.showsTheme(HomeColumnTheme.FAVORITES))
        assertTrue(on.columns.showsTheme(HomeColumnTheme.FAVORITES))
    }

    @Test
    fun `column count steps through auto three five seven and stops at either end`() {
        val steps = generateSequence(columns) { adjustHomeLayoutField(it, HomeLayoutSettingField.COLUMNS_COUNT, 1, shape) }
            .take(5)
            .map { it.columns.columnCount }
            .toList()

        assertEquals(
            listOf(
                HomeColumnCount.AUTO,
                HomeColumnCount.COMPACT,
                HomeColumnCount.WIDE,
                HomeColumnCount.ULTRA_WIDE,
                HomeColumnCount.ULTRA_WIDE
            ),
            steps
        )
        assertTrue(isHomeLayoutFieldAtBound(columns, HomeLayoutSettingField.COLUMNS_COUNT, -1))
        assertFalse(isHomeLayoutFieldAtBound(columns, HomeLayoutSettingField.COLUMNS_COUNT, 1))
    }

    @Test
    fun `confirm leaves the column count alone`() {
        assertEquals(columns, toggleHomeLayoutField(columns, HomeLayoutSettingField.COLUMNS_COUNT))
    }

    @Test
    fun `confirm flips show every game, the info band and platform columns`() {
        val flipped = listOf(
            HomeLayoutSettingField.COLUMNS_SHOW_ALL,
            HomeLayoutSettingField.COLUMNS_INFO_BAND,
            HomeLayoutSettingField.COLUMNS_PLATFORMS
        ).fold(columns) { settings, field -> toggleHomeLayoutField(settings, field) }

        assertTrue(flipped.columns.showAllGames)
        assertFalse(flipped.columns.showInfoBand)
        assertFalse(flipped.columns.showPlatformColumns)
        assertFalse(flipped.autoGrid.showAllGames)
    }
}
