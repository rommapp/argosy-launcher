package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsBackStackTest {

    private fun at(section: SettingsSection, focus: Int) =
        SettingsUiState(currentSection = section, focusedIndex = focus)

    @Test
    fun `back returns to the row the section was entered from`() {
        val entered = at(SettingsSection.BUILTIN_EMULATOR, BuiltinEmulatorItem.CORE_OPTIONS.focusIndex)
            .pushedSection(SettingsSection.CORE_OPTIONS)

        assertEquals(SettingsSection.CORE_OPTIONS, entered.currentSection)
        assertEquals(0, entered.focusedIndex)

        val back = entered.poppedSection()!!
        assertEquals(SettingsSection.BUILTIN_EMULATOR, back.currentSection)
        assertEquals(BuiltinEmulatorItem.CORE_OPTIONS.focusIndex, back.focusedIndex)
        assertEquals(emptyList<SettingsNavEntry>(), back.backStack)
    }

    @Test
    fun `the same section backs out to whichever parent opened it`() {
        val fromBuiltin = at(SettingsSection.BUILTIN_EMULATOR, BuiltinEmulatorItem.VIDEO.focusIndex)
            .pushedSection(SettingsSection.BUILTIN_VIDEO)
        val fromPlatform = at(SettingsSection.PLATFORM_DETAIL, 7)
            .pushedSection(SettingsSection.BUILTIN_VIDEO)

        assertEquals(SettingsSection.BUILTIN_EMULATOR, fromBuiltin.poppedSection()!!.currentSection)
        assertEquals(SettingsSection.PLATFORM_DETAIL, fromPlatform.poppedSection()!!.currentSection)
        assertEquals(7, fromPlatform.poppedSection()!!.focusedIndex)
    }

    @Test
    fun `nesting unwinds one level at a time`() {
        val deep = at(SettingsSection.MAIN, 3)
            .pushedSection(SettingsSection.PLATFORMS)
            .let { it.copy(focusedIndex = 5) }
            .pushedSection(SettingsSection.PLATFORM_DETAIL)
            .let { it.copy(focusedIndex = 2) }
            .pushedSection(SettingsSection.CORE_OPTIONS)

        val first = deep.poppedSection()!!
        assertEquals(SettingsSection.PLATFORM_DETAIL, first.currentSection)
        assertEquals(2, first.focusedIndex)

        val second = first.poppedSection()!!
        assertEquals(SettingsSection.PLATFORMS, second.currentSection)
        assertEquals(5, second.focusedIndex)

        val third = second.poppedSection()!!
        assertEquals(SettingsSection.MAIN, third.currentSection)
        assertEquals(3, third.focusedIndex)
    }

    @Test
    fun `a section with no parent leaves settings rather than popping`() {
        assertNull(at(SettingsSection.MAIN, 0).poppedSection())
        assertNull(at(SettingsSection.ACCOUNTS, 0).poppedSection())
    }

    @Test
    fun `an explicitly resolved focus wins over the remembered index`() {
        val entered = at(SettingsSection.PLATFORMS, 4).pushedSection(SettingsSection.PLATFORM_DETAIL)
        assertEquals(9, entered.poppedSection(restoredFocus = 9)!!.focusedIndex)
    }
}
