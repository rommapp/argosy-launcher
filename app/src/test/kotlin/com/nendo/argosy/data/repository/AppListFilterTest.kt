package com.nendo.argosy.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AppListFilterTest {

    private val installed = InstalledApp("emu.installed", "Emu", isSystemApp = false)
    private val hiddenInstalled = InstalledApp("emu.hidden", "Hidden", isSystemApp = false)
    private val shownSystem = InstalledApp("sys.settings", "Settings", isSystemApp = true)
    private val defaultSystem = InstalledApp("sys.files", "Files", isSystemApp = true)
    private val all = listOf(installed, hiddenInstalled, shownSystem, defaultSystem)

    private fun shown(showHidden: Boolean) = all.filter {
        AppListFilter.isShown(
            it,
            hiddenApps = setOf(hiddenInstalled.packageName),
            visibleSystemApps = setOf(shownSystem.packageName),
            showHidden = showHidden
        )
    }

    @Test
    fun `main list keeps installed apps and system apps the user made visible`() {
        assertEquals(listOf(installed, shownSystem), shown(showHidden = false))
    }

    @Test
    fun `hidden list holds hidden apps and system apps never made visible`() {
        assertEquals(listOf(hiddenInstalled, defaultSystem), shown(showHidden = true))
    }

    @Test
    fun `full list puts the visible group before the hidden group`() {
        val grouped = AppListFilter.visibleThenHidden(
            listOf(defaultSystem, hiddenInstalled, shownSystem, installed),
            hiddenApps = setOf(hiddenInstalled.packageName),
            visibleSystemApps = setOf(shownSystem.packageName),
            appOrder = listOf(shownSystem.packageName, defaultSystem.packageName)
        )
        assertEquals(listOf(shownSystem, installed, defaultSystem, hiddenInstalled), grouped)
    }

    @Test
    fun `custom order leads and unordered apps follow by label`() {
        val apps = listOf(
            InstalledApp("a", "Alpha", false),
            InstalledApp("b", "beta", false),
            InstalledApp("c", "Charlie", false)
        )
        val ordered = AppListFilter.ordered(apps, appOrder = listOf("c"))
        assertEquals(listOf("c", "a", "b"), ordered.map { it.packageName })
    }

    @Test
    fun `no custom order keeps the incoming order`() {
        val apps = listOf(InstalledApp("z", "Zed", false), InstalledApp("a", "Alpha", false))
        assertEquals(apps, AppListFilter.ordered(apps, appOrder = emptyList()))
    }
}
