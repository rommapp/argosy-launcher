package com.nendo.argosy.libretro

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.nendo.argosy.ui.notification.InGameNotice
import com.nendo.argosy.ui.notification.NotificationSeverity
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
        serializable: Boolean = true,
        quickLoadSucceeds: Boolean = true,
        noticeSink: MutableList<InGameNotice> = mutableListOf(),
        saveStateManager: SaveStateManager = mockk(relaxed = true),
        retroView: GLRetroView = mockk(relaxed = true),
        sunSteps: MutableList<Int> = mutableListOf(),
        recenters: MutableList<Unit> = mutableListOf()
    ): HotkeyDispatcher {
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        every { saveStateManager.performQuickLoad(any()) } returns quickLoadSucceeds
        every { retroView.serializeState() } returns ByteArray(8)
        every { retroView.captureRawFrame() } returns null
        return HotkeyDispatcher(
            saveStateManager = saveStateManager,
            videoSettings = mockk(relaxed = true),
            hotkeyManager = mockk(relaxed = true),
            getRetroView = { retroView },
            showNotice = { noticeSink.add(it) },
            isHardcoreMode = { hardcore },
            canSerialize = { serializable },
            isNetplayInSession = { netplayInSession },
            getNetplayRole = { netplayRole },
            onShowMenu = {},
            onFastForwardChanged = {},
            onRewindChanged = {},
            onResetGame = {},
            onAutoSaveState = {},
            onCycleCoreOption = { _, _, _ -> },
            onSendCoreInput = { _, _ -> },
            onSpeedrunSplit = {},
            onSpeedrunUndoSplit = {},
            onSpeedrunSkipSplit = {},
            onSpeedrunToggleTimer = {},
            onSpeedrunResetTimer = {},
            onStepSunLevel = { sunSteps.add(it) },
            onRecenterGyroAim = { recenters.add(Unit) },
            onQuit = {}
        )
    }

    private fun notice(severity: NotificationSeverity, title: Int, body: Int? = null) = InGameNotice(
        severity = severity,
        title = NotificationText.Res(title),
        body = body?.let { NotificationText.Res(it) }
    )

    @Test
    fun sunHotkeysStepTheLevelUpAndDown() {
        val steps = mutableListOf<Int>()
        val dispatcher = build(false, null, sunSteps = steps)
        dispatcher.dispatch(config(HotkeyAction.SUN_BRIGHTER))
        dispatcher.dispatch(config(HotkeyAction.SUN_DIMMER))
        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun recenterHotkeyRecentersGyroAim() {
        val recenters = mutableListOf<Unit>()
        val dispatcher = build(false, null, recenters = recenters)
        dispatcher.dispatch(config(HotkeyAction.RECENTER_GYRO_AIM))
        assertEquals(1, recenters.size)
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
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        val dispatcher = build(false, null, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 1) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(
            listOf(notice(NotificationSeverity.SUCCESS, R.string.ingame_hotkey_quicksave_success_title)),
            notices
        )
    }

    @Test
    fun quickSave_host_in_session_performs_save() {
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        every { saveStateManager.performQuickSave(any(), any()) } returns true
        val dispatcher = build(true, NetplayMenuRole.Host, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 1) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(
            listOf(notice(NotificationSeverity.SUCCESS, R.string.ingame_hotkey_quicksave_success_title)),
            notices
        )
    }

    @Test
    fun quickSave_guest_in_session_blocked() {
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Guest, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 0) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(
            listOf(
                notice(
                    NotificationSeverity.WARNING,
                    R.string.ingame_hotkey_quicksave_netplay_title,
                    R.string.ingame_hotkey_quicksave_netplay_body
                )
            ),
            notices
        )
    }

    @Test
    fun quickLoad_host_in_session_blocked() {
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Host, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_LOAD))
        verify(exactly = 0) { saveStateManager.performQuickLoad(any()) }
        assertEquals(
            listOf(
                notice(
                    NotificationSeverity.WARNING,
                    R.string.ingame_hotkey_quickload_netplay_title,
                    R.string.ingame_hotkey_quickload_netplay_body
                )
            ),
            notices
        )
    }

    @Test
    fun quickLoad_guest_in_session_blocked() {
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Guest, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_LOAD))
        verify(exactly = 0) { saveStateManager.performQuickLoad(any()) }
        assertEquals(
            listOf(
                notice(
                    NotificationSeverity.WARNING,
                    R.string.ingame_hotkey_quickload_netplay_title,
                    R.string.ingame_hotkey_quickload_netplay_body
                )
            ),
            notices
        )
    }

    @Test
    fun quickLoad_core_rejection_reports_failure() {
        val notices = mutableListOf<InGameNotice>()
        val dispatcher = build(
            netplayInSession = false,
            netplayRole = null,
            quickLoadSucceeds = false,
            noticeSink = notices
        )

        dispatcher.dispatch(config(HotkeyAction.QUICK_LOAD))

        assertEquals(
            listOf(notice(NotificationSeverity.ERROR, R.string.ingame_hotkey_quickload_failure_title)),
            notices
        )
    }

    @Test
    fun quickSave_hardcore_blocks_even_for_host() {
        val notices = mutableListOf<InGameNotice>()
        val saveStateManager: SaveStateManager = mockk(relaxed = true)
        val dispatcher = build(true, NetplayMenuRole.Host, hardcore = true, noticeSink = notices, saveStateManager = saveStateManager)
        dispatcher.dispatch(config(HotkeyAction.QUICK_SAVE))
        verify(exactly = 0) { saveStateManager.performQuickSave(any(), any()) }
        assertEquals(
            listOf(
                notice(
                    NotificationSeverity.WARNING,
                    R.string.ingame_hotkey_quicksave_hardcore_title,
                    R.string.ingame_hotkey_quicksave_hardcore_body
                )
            ),
            notices
        )
    }
}
