package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class HotkeyDispatcherTest {

    private fun build(
        netplayInSession: Boolean,
        netplayRole: NetplayMenuRole?,
        hardcore: Boolean = false,
        toastSink: MutableList<String> = mutableListOf(),
        saveStateManager: SaveStateManager = mockk(relaxed = true),
        retroView: GLRetroView = mockk(relaxed = true)
    ): HotkeyDispatcher {
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        every { saveStateManager.performQuickLoad(any()) } returns true
        every { retroView.serializeState() } returns ByteArray(8)
        every { retroView.captureRawFrame() } returns null
        return HotkeyDispatcher(
            saveStateManager = saveStateManager,
            videoSettings = mockk(relaxed = true),
            hotkeyManager = mockk(relaxed = true),
            getRetroView = { retroView },
            showToast = { toastSink.add(it) },
            isHardcoreMode = { hardcore },
            isNetplayInSession = { netplayInSession },
            getNetplayRole = { netplayRole },
            onShowMenu = {},
            onFastForwardChanged = {},
            onRewindChanged = {},
            onResetGame = {},
            onAutoSaveState = {},
            onCycleCoreOption = { _, _, _ -> },
            onSendCoreInput = { _, _ -> },
            onQuit = {}
        )
    }

    private fun config(action: HotkeyAction) = HotkeyManager.HotkeyConfig(
        action = action,
        keyCodes = setOf(1),
        controllerId = null,
        isEnabled = true,
        holdMs = 0
    )

    @Test
    fun quickSave_offline_performs_save() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        val dispatcher = build(false, null, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 1) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(listOf("State saved"), toasts)
    }

    @Test
    fun quickSave_host_in_session_performs_save() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        val dispatcher = build(true, NetplayMenuRole.Host, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 1) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(listOf("State saved"), toasts)
    }

    @Test
    fun quickSave_guest_in_session_blocked() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Guest, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 0) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(listOf("Save states disabled during netplay"), toasts)
    }

    @Test
    fun quickLoad_host_in_session_blocked() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Host, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_LOAD))
        verify(exactly = 0) { saveStateManager.performQuickLoad(any()) }
        assertEquals(listOf("Save states disabled during netplay"), toasts)
    }

    @Test
    fun quickLoad_guest_in_session_blocked() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Guest, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_LOAD))
        verify(exactly = 0) { saveStateManager.performQuickLoad(any()) }
        assertEquals(listOf("Save states disabled during netplay"), toasts)
    }

    @Test
    fun quickSave_hardcore_blocks_even_for_host() {
        val toasts = mutableListOf<String>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Host, hardcore = true, toastSink = toasts, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 0) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(listOf("Save states disabled in Hardcore mode"), toasts)
    }
}
