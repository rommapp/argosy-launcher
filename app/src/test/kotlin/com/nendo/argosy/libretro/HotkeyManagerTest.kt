package com.nendo.argosy.libretro

import android.view.KeyEvent
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HotkeyManagerTest {

    private fun manager(keyCode: Int): HotkeyManager =
        HotkeyManager(mockk<InputConfigRepository>(relaxed = true)).apply {
            setHotkeys(
                listOf(
                    HotkeyEntity(
                        action = HotkeyAction.FAST_FORWARD,
                        buttonComboJson = "[$keyCode]"
                    )
                )
            )
        }

    @Test
    fun `platform button priority follows the event controller mapping`() {
        val keyCode = KeyEvent.KEYCODE_BUTTON_R2
        val manager = manager(keyCode)
        manager.setPlatformMappedButtons(
            mapOf(
                "gameplay-pad" to setOf(keyCode),
                "hotkey-pad" to emptySet()
            )
        )

        assertNull(manager.onKeyDown(keyCode, "gameplay-pad"))
        manager.onKeyUp(keyCode)
        assertEquals(HotkeyAction.FAST_FORWARD, manager.onKeyDown(keyCode, "hotkey-pad")?.action)
    }

    @Test
    fun `updated controller mapping replaces hotkey priority`() {
        val keyCode = KeyEvent.KEYCODE_BUTTON_R2
        val manager = manager(keyCode)
        manager.setPlatformMappedButtons(mapOf("pad" to emptySet()))

        assertEquals(HotkeyAction.FAST_FORWARD, manager.onKeyDown(keyCode, "pad")?.action)
        manager.onKeyUp(keyCode)

        manager.setPlatformMappedButtons(mapOf("pad" to setOf(keyCode)))

        assertNull(manager.onKeyDown(keyCode, "pad"))
    }
}
