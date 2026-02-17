package com.nendo.argosy.libretro

import android.widget.Toast
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.swordfish.libretrodroid.GLRetroView

class HotkeyDispatcher(
    private val saveStateManager: SaveStateManager,
    private val videoSettings: VideoSettingsManager,
    private val hotkeyManager: HotkeyManager,
    private val getRetroView: () -> GLRetroView,
    private val showToast: (String) -> Unit,
    private val isHardcoreMode: () -> Boolean,
    private val onShowMenu: () -> Unit,
    private val onFastForwardChanged: (Boolean) -> Unit,
    private val onRewindChanged: (Boolean) -> Unit,
    private val onQuit: () -> Unit
) {
    fun dispatch(action: HotkeyAction): Boolean {
        when (action) {
            HotkeyAction.IN_GAME_MENU -> {
                onShowMenu()
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.QUICK_SAVE -> {
                if (isHardcoreMode()) {
                    showToast("Save states disabled in Hardcore mode")
                } else {
                    if (saveStateManager.performQuickSave(getRetroView())) {
                        showToast("State saved")
                    } else {
                        showToast("Failed to save state")
                    }
                }
                hotkeyManager.clearState()
                return true
            }
            HotkeyAction.QUICK_LOAD -> {
                if (isHardcoreMode()) {
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
                onFastForwardChanged(true)
                return true
            }
            HotkeyAction.REWIND -> {
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
                saveStateManager.saveSram(getRetroView())
                onQuit()
                return true
            }
        }
    }
}
