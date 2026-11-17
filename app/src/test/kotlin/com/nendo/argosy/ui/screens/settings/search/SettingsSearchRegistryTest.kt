package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.matchSettingsSearch
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.ServerState
import com.nendo.argosy.ui.screens.settings.sections.RAItem
import com.nendo.argosy.ui.screens.settings.sections.RALayoutState
import com.nendo.argosy.ui.screens.settings.sections.raLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchRegistryTest {

    @Test
    fun `every section resolves to a source naming that section`() {
        SettingsSection.entries.forEach { section ->
            assertEquals(section, settingsSearchSource(section).section)
        }
    }

    @Test
    fun `every focusable row of a registered layout carries a label`() {
        rowSources().forEach { source ->
            source.layout.allItems.filter(source.layout.isFocusable).forEach { item ->
                assertTrue(
                    "${source.section} row ${source.keyOf(item)} is focusable without a label",
                    item is SearchableSetting
                )
                assertNotEquals(
                    "${source.section} row ${source.keyOf(item)} has an empty label",
                    0,
                    (item as SearchableSetting).labelRes
                )
            }
        }
    }

    @Test
    fun `no registered layout repeats a key`() {
        rowSources().forEach { source ->
            val keys = source.layout.allItems.map(source.keyOf)
            val repeated = keys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue("${source.section} repeats keys $repeated", repeated.isEmpty())
        }
    }

    @Test
    fun `index ids are unique across every source`() {
        val ui = SettingsUiState(
            display = DisplayState(ambientLedAvailable = true, hasPhysicalSecondaryDisplay = true),
            emulators = EmulatorState(platforms = listOf(platform(1, "SNES", enabled = true))),
            settingsSearch = SettingsSearchState(
                coreOptions = listOf(
                    SettingsSearchCoreOption("snes9x", "Snes9x", "snes9x_region", "Console Region"),
                    SettingsSearchCoreOption("snes9x", "Snes9x", "snes9x_aspect", "Preferred Aspect Ratio")
                )
            )
        )
        val ids = buildSettingsSearchIndex(ui).map { it.target.id }
        val repeated = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue("repeated index ids $repeated", repeated.isEmpty())
    }

    @Test
    fun `rows hidden by live state stay out of the index`() {
        val hidden = buildSettingsSearchIndex(SettingsUiState(display = DisplayState(ambientLedAvailable = false)))
        val shown = buildSettingsSearchIndex(SettingsUiState(display = DisplayState(ambientLedAvailable = true)))
        val ledRow = SettingsSearchTarget.Row(SettingsSection.DISPLAYS, "ambientLedSettings")

        assertFalse(hidden.any { it.target == ledRow })
        assertFalse(hidden.any { (it.target as? SettingsSearchTarget.Row)?.section == SettingsSection.AMBIENT_LED })
        assertTrue(shown.any { it.target == ledRow })
        assertTrue(shown.any { (it.target as? SettingsSearchTarget.Row)?.section == SettingsSection.AMBIENT_LED })
    }

    @Test
    fun `retroachievements rows keep their focus order in both login states`() {
        val loggedOut = RALayoutState(isLoggedIn = false, proxyEnabled = true, canPushToRetroArch = true)
        val loggedIn = RALayoutState(isLoggedIn = true, proxyEnabled = false, canPushToRetroArch = true)

        assertEquals(
            listOf(RAItem.Login, RAItem.ProxyToggle, RAItem.ProxyField),
            raLayout.focusableItems(loggedOut)
        )
        assertEquals(
            listOf(RAItem.Logout, RAItem.WebApiKey, RAItem.PlayMode, RAItem.ProxyToggle, RAItem.PushToRetroArch),
            raLayout.focusableItems(loggedIn)
        )
    }

    @Test
    fun `server gated save rows stay out of the index until a server is paired`() {
        val offline = buildSettingsSearchIndex(SettingsUiState())
        val online = buildSettingsSearchIndex(
            SettingsUiState(server = ServerState(connectionStatus = ConnectionStatus.ONLINE))
        )
        val saveSync = SettingsSearchTarget.Row(SettingsSection.SAVES, "saveSync")
        val cacheLimit = SettingsSearchTarget.Row(SettingsSection.SAVES, "saveCacheLimit")

        assertFalse(offline.any { it.target == saveSync })
        assertTrue(offline.any { it.target == cacheLimit })
        assertTrue(online.any { it.target == saveSync })
    }

    @Test
    fun `only enabled platforms are indexed`() {
        val ui = SettingsUiState(
            emulators = EmulatorState(
                platforms = listOf(platform(1, "SNES", enabled = true), platform(2, "Saturn", enabled = false))
            )
        )

        val targets = enabledPlatformEntries(ui).map { it.target }

        assertEquals(listOf<SettingsSearchTarget>(SettingsSearchTarget.Platform(1)), targets)
    }

    @Test
    fun `core option rows follow the built-in emulator switch`() {
        val option = SettingsSearchCoreOption("snes9x", "Snes9x", "snes9x_region", "Console Region")
        val on = SettingsUiState(
            emulators = EmulatorState(builtinLibretroEnabled = true),
            settingsSearch = SettingsSearchState(coreOptions = listOf(option))
        )
        val off = on.copy(emulators = EmulatorState(builtinLibretroEnabled = false))
        val target = SettingsSearchTarget.CoreOption("snes9x", "snes9x_region")

        assertTrue(buildSettingsSearchIndex(on).any { it.target == target })
        assertFalse(buildSettingsSearchIndex(off).any { it.target == target })
    }

    @Test
    fun `matches rank label prefix, then label infix, then keyword`() {
        val keywordOnly = entry("a", "Brightness", keywords = "dim")
        val infix = entry("b", "Screen dimmer")
        val prefix = entry("c", "Dim level")

        val results = matchSettingsSearch(listOf(keywordOnly, infix, prefix), " dim ") { it.text() }

        assertEquals(listOf("c", "b", "a"), results.map { (it.entry.target as SettingsSearchTarget.Row).key })
        assertEquals(0, results[0].matchStart)
        assertEquals(7, results[1].matchStart)
        assertEquals(3, results[1].matchLength)
        assertEquals(-1, results[2].matchStart)
    }

    @Test
    fun `a blank query matches nothing`() {
        assertTrue(matchSettingsSearch(listOf(entry("a", "Dim level")), "  ") { it.text() }.isEmpty())
    }

    private fun rowSources(): List<SettingsSearchSource.Rows<Any, Any?>> =
        SettingsSection.entries.mapNotNull { section ->
            @Suppress("UNCHECKED_CAST")
            settingsSearchSource(section) as? SettingsSearchSource.Rows<Any, Any?>
        }

    private fun platform(id: Long, name: String, enabled: Boolean) = PlatformEmulatorConfig(
        platform = PlatformEntity(
            id = id,
            slug = name.lowercase(),
            name = name,
            shortName = name,
            romExtensions = "",
            syncEnabled = enabled
        ),
        selectedEmulator = null,
        isUserConfigured = false,
        availableEmulators = emptyList()
    )

    private fun entry(key: String, label: String, keywords: String? = null) = SettingsSearchEntry(
        target = SettingsSearchTarget.Row(SettingsSection.DISPLAYS, key),
        label = DisplayText.Raw(label),
        keywords = keywords?.let { DisplayText.Raw(it) },
        path = emptyList()
    )

    private fun DisplayText.text(): String = (this as DisplayText.Raw).text
}
