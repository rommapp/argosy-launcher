package com.nendo.argosy.ui.screens.settings.search

import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.common.DisplayText
import com.nendo.argosy.ui.screens.settings.BuiltinVideoState
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.EmulatorState
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.BuiltinSavesItem
import com.nendo.argosy.ui.screens.settings.sections.BuiltinSavesLayoutState
import com.nendo.argosy.ui.screens.settings.sections.BuiltinVideoItem
import com.nendo.argosy.ui.screens.settings.sections.BuiltinVideoLayoutState
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import com.nendo.argosy.ui.screens.settings.sections.builtinEmulatorLayout
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesLayout
import com.nendo.argosy.ui.screens.settings.sections.builtinSavesMaxFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.builtinVideoLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchLauncherSectionsTest {

    @Test
    fun `built-in emulator rows follow its switch`() {
        val off = buildSettingsSearchIndex(SettingsUiState(emulators = EmulatorState(builtinLibretroEnabled = false)))
        val on = buildSettingsSearchIndex(SettingsUiState(emulators = EmulatorState(builtinLibretroEnabled = true)))
        val builtinChildren = setOf(
            SettingsSection.BUILTIN_VIDEO,
            SettingsSection.BUILTIN_CONTROLS,
            SettingsSection.BUILTIN_SAVES
        )

        assertEquals(
            listOf(BuiltinEmulatorItem.ENABLE.key),
            off.rowKeysIn(SettingsSection.BUILTIN_EMULATOR)
        )
        assertFalse(off.any { (it.target as? SettingsSearchTarget.Row)?.section in builtinChildren })
        assertTrue(on.rowKeysIn(SettingsSection.BUILTIN_EMULATOR).contains(BuiltinEmulatorItem.VIDEO.key))
        builtinChildren.forEach { section -> assertTrue("$section missing", on.rowKeysIn(section).isNotEmpty()) }
    }

    @Test
    fun `built-in emulator layout keeps focus index equal to the row ordinal`() {
        listOf(
            EmulatorState(builtinLibretroEnabled = false),
            EmulatorState(builtinLibretroEnabled = true, hudEnabled = false),
            EmulatorState(builtinLibretroEnabled = true, hudEnabled = true)
        ).forEach { state ->
            builtinEmulatorLayout.focusableItems(state).forEachIndexed { index, item ->
                assertEquals(item.focusIndex, index)
            }
        }
    }

    @Test
    fun `built-in video layout orders rows the way the screen focuses them`() {
        val video = BuiltinVideoState()
        val items = builtinVideoLayout.focusableItems(BuiltinVideoLayoutState.from(video, emptyMap()))

        assertEquals(builtinVideoMaxFocusIndex(video, emptyMap()), items.lastIndex)
        items.forEachIndexed { index, item ->
            if (item is BuiltinVideoItem.Setting) assertEquals(builtinVideoItemAtFocusIndex(index, video), item.def)
        }
    }

    @Test
    fun `built-in saves layout orders rows the way the screen focuses them`() {
        val video = BuiltinVideoState(savePath = "/storage/saves")
        val items = builtinSavesLayout.focusableItems(BuiltinSavesLayoutState.from(video, emptyMap()))

        assertEquals(builtinSavesMaxFocusIndex(video, emptyMap()), items.lastIndex)
        assertEquals(listOf(BuiltinSavesItem.SavePath, BuiltinSavesItem.StatePath), items.takeLast(2))
        items.forEachIndexed { index, item ->
            if (item is BuiltinSavesItem.Setting) assertEquals(builtinSavesItemAtFocusIndex(index, video), item.def)
        }
    }

    @Test
    fun `presentation rows need a usable second screen`() {
        val single = SettingsUiState(display = DisplayState())
        val dual = SettingsUiState(display = DisplayState(dualScreenEnabled = true, hasSecondaryDisplay = true))

        assertTrue(buildSettingsSearchIndex(single).rowKeysIn(SettingsSection.PRESENTATION).isEmpty())
        assertTrue(buildSettingsSearchIndex(dual).rowKeysIn(SettingsSection.PRESENTATION).isNotEmpty())
    }

    @Test
    fun `platform rows are indexed for enabled platforms under the platform name`() {
        val ui = SettingsUiState(
            emulators = EmulatorState(
                platforms = listOf(platform(1, "SNES", enabled = true), platform(2, "Saturn", enabled = false))
            )
        )

        val rows = enabledPlatformRowEntries(ui)
        val emulatorRow = rows.single {
            it.target == SettingsSearchTarget.Platform(1, PlatformDetailItem.Emulator.key)
        }

        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { (it.target as SettingsSearchTarget.Platform).platformId == 1L })
        assertEquals(DisplayText.Raw("SNES"), emulatorRow.path.last())
        assertTrue(buildSettingsSearchIndex(ui).any { it.target == emulatorRow.target })
    }

    private fun List<SettingsSearchEntry>.rowKeysIn(section: SettingsSection): List<String> =
        mapNotNull { (it.target as? SettingsSearchTarget.Row)?.takeIf { row -> row.section == section }?.key }

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
}
