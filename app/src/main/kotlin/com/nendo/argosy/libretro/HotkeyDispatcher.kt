package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.CoreInputMode
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import com.swordfish.libretrodroid.GLRetroView

class HotkeyDispatcher(
    private val saveStateManager: SaveStateManager,
    private val videoSettings: VideoSettingsManager,
    private val hotkeyManager: HotkeyManager,
    private val getRetroView: () -> GLRetroView,
    private val showToast: (String) -> Unit,
    private val isHardcoreMode: () -> Boolean,
    private val isNetplayInSession: () -> Boolean,
    private val getNetplayRole: () -> NetplayMenuRole?,
    private val onShowMenu: () -> Unit,
    private val onFastForwardChanged: (Boolean) -> Unit,
    private val onRewindChanged: (Boolean) -> Unit,
    private val onResetGame: () -> Unit,
    private val onAutoSaveState: () -> Unit,
    private val onCycleCoreOption: (String, Int, List<String>) -> Unit,
    private val onSendCoreInput: (Int, CoreInputMode) -> Unit,
    private val onQuit: () -> Unit
) {
    fun dispatch(config: HotkeyManager.HotkeyConfig): Boolean {
        when (config.action) {
            HotkeyAction.IN_GAME_MENU -> {
                onShowMenu()
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.QUICK_SAVE -> {
                if (isNetplayInSession() && getNetplayRole() != NetplayMenuRole.Host) {
                    showToast("Save states disabled during netplay")
                } else if (isHardcoreMode()) {
                    showToast("Save states disabled in Hardcore mode")
                } else {
                    val rv = getRetroView()
                    val stateData = try { rv.serializeState() } catch (_: Exception) { null }
                    val bitmap = try { rv.captureRawFrame() } catch (_: Exception) { null }
                    if (stateData != null && saveStateManager.performQuickSave(stateData, bitmap)) {
                        showToast("State saved")
                    } else {
                        showToast("Failed to save state")
                    }
                }
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.QUICK_LOAD -> {
                if (isNetplayInSession()) {
                    showToast("Save states disabled during netplay")
                } else if (isHardcoreMode()) {
                    showToast("Save states disabled in Hardcore mode")
                } else {
                    if (saveStateManager.performQuickLoad(getRetroView())) {
                        showToast("State loaded")
                    } else {
                        showToast("Failed to load state")
                    }
                }
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.FAST_FORWARD -> {
                if (isNetplayInSession()) return true
                onFastForwardChanged(true)
                return true
            }
            HotkeyAction.REWIND -> {
                if (isNetplayInSession()) return true
                if (isHardcoreMode()) {
                    showToast("Rewind disabled in Hardcore mode")
                    return true
                }
                if (videoSettings.rewindEnabled) {
                    onRewindChanged(true)
                }
                return true
            }
            HotkeyAction.QUICK_SUSPEND -> {
                onAutoSaveState()
                saveStateManager.saveSram(getRetroView())
                onQuit()
                return true
            }
            HotkeyAction.RESET_GAME -> {
                if (isNetplayInSession()) {
                    showToast("Reset disabled during netplay")
                    return true
                }
                onResetGame()
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.CYCLE_CORE_OPTION -> {
                val key = config.coreOptionKey ?: return true
                onCycleCoreOption(key, config.coreOptionDirection, config.coreOptionValues)
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.SEND_CORE_INPUT -> {
                val retropadId = config.coreInputRetropadId ?: return true
                onSendCoreInput(retropadId, config.coreInputMode)
                hotkeyManager.clearState()
                return true
            }
        }
    }
}
