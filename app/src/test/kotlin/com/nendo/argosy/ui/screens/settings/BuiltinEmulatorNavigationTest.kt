package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltinEmulatorNavigationTest {
    @Test
    fun `child sections return to their originating built-in emulator rows`() {
        assertEquals(
            BuiltinEmulatorItem.VIDEO,
            builtinEmulatorParentItem(SettingsSection.BUILTIN_VIDEO)
        )
        assertEquals(
            BuiltinEmulatorItem.CONTROLS,
            builtinEmulatorParentItem(SettingsSection.BUILTIN_CONTROLS)
        )
        assertEquals(
            BuiltinEmulatorItem.CORE_MANAGEMENT,
            builtinEmulatorParentItem(SettingsSection.CORE_MANAGEMENT)
        )
        assertEquals(
            BuiltinEmulatorItem.CORE_OPTIONS,
            builtinEmulatorParentItem(SettingsSection.CORE_OPTIONS)
        )
    }

    @Test
    fun `unrelated sections have no built-in emulator parent row`() {
        assertNull(builtinEmulatorParentItem(SettingsSection.MAIN))
    }
}
