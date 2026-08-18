package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.entity.CoreInputMode
import com.nendo.argosy.data.local.entity.FastForwardMode
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.libretro.ui.NetplayMenuRole
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.swordfish.libretrodroid.GLRetroView

/**
 * Activity-level hotkey coordinator: detects per-key hotkey activations via
 * [HotkeyManager], routes triggered actions through the inner [HotkeyDispatcher],
 * and owns the runtime fast-forward / rewind state that those actions toggle.
 *
 * Lifecycle hooks ([onKeyDown], [onKeyUp]) are invoked by the activity from
 * its key dispatch overrides; the activity also calls [releaseFastForward]
 * when netplay rules need to drop accelerated playback.
 */
class LibretroHotkeyDispatcher(
    private val hotkeyManager: HotkeyManager,
    private val saveStateManager: SaveStateManager,
    private val videoSettings: VideoSettingsManager,
    private val getRetroView: () -> GLRetroView,
    private val showToast: (String) -> Unit,
    private val isHardcoreMode: () -> Boolean,
    private val canSerialize: () -> Boolean,
    private val isNetplayInSession: () -> Boolean,
    private val getNetplayRole: () -> NetplayMenuRole?,
    private val onShowMenu: () -> Unit,
    private val onAutoSaveState: () -> Unit,
    private val onCycleCoreOption: (String, Int, List<String>) -> Unit,
    private val onSendCoreInput: (Int, CoreInputMode) -> Unit,
    private val onQuit: () -> Unit,
    private val onGameReset: () -> Unit,
    private val onSpeedrunSplit: () -> Unit,
    private val onSpeedrunUndoSplit: () -> Unit,
    private val onSpeedrunSkipSplit: () -> Unit,
    private val onSpeedrunToggleTimer: () -> Unit,
    private val onSpeedrunResetTimer: () -> Unit,
    private val onHotkeyFired: () -> Unit = {}
) {
    var isFastForwarding: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set
    var isRewinding: Boolean = false
        private set

    private val inner = HotkeyDispatcher(
        saveStateManager = saveStateManager,
        videoSettings = videoSettings,
        hotkeyManager = hotkeyManager,
        getRetroView = getRetroView,
        showToast = showToast,
        isHardcoreMode = isHardcoreMode,
        canSerialize = canSerialize,
        isNetplayInSession = isNetplayInSession,
        getNetplayRole = getNetplayRole,
        onShowMenu = onShowMenu,
        onFastForwardChanged = ::handleFastForwardRequest,
        onRewindChanged = ::handleRewindRequest,
        onResetGame = ::handleResetRequest,
        onAutoSaveState = onAutoSaveState,
        onCycleCoreOption = onCycleCoreOption,
        onSendCoreInput = onSendCoreInput,
        onQuit = onQuit,
        onSpeedrunSplit = onSpeedrunSplit,
        onSpeedrunUndoSplit = onSpeedrunUndoSplit,
        onSpeedrunSkipSplit = onSpeedrunSkipSplit,
        onSpeedrunToggleTimer = onSpeedrunToggleTimer,
        onSpeedrunResetTimer = onSpeedrunResetTimer
    )

    init {
        hotkeyManager.setDispatch { config ->
            onHotkeyFired()
            inner.dispatch(config)
        }
    }

    fun onKeyDown(keyCode: Int, controllerId: String?): Boolean {
        val triggered = hotkeyManager.onKeyDown(keyCode, controllerId) ?: return false
        onHotkeyFired()
        return inner.dispatch(triggered)
    }

    fun isPotentialComboKey(keyCode: Int, controllerId: String?): Boolean =
        hotkeyManager.isPotentialComboKey(keyCode, controllerId)

    fun onKeyUp(keyCode: Int) {
        hotkeyManager.onKeyUp(keyCode)
        val holdMode = videoSettings.fastForwardMode == FastForwardMode.HOLD
        if (holdMode && !hotkeyManager.isHotkeyActive(HotkeyAction.FAST_FORWARD) && isFastForwarding) {
            isFastForwarding = false
            getRetroView().frameSpeed = 1
        }
        if (!hotkeyManager.isHotkeyActive(HotkeyAction.REWIND) && isRewinding) {
            isRewinding = false
            getRetroView().isRewinding = false
        }
    }

    fun releaseFastForward() {
        if (isFastForwarding) {
            isFastForwarding = false
            getRetroView().frameSpeed = 1
        }
    }

    private fun handleFastForwardRequest(requested: Boolean) {
        if (isNetplayInSession()) return
        if (!requested || !videoSettings.fastForwardEnabled) return
        val toggleMode = videoSettings.fastForwardMode == FastForwardMode.TOGGLE
        val rv = getRetroView()
        if (toggleMode && isFastForwarding) {
            isFastForwarding = false
            rv.frameSpeed = 1
        } else if (!isFastForwarding) {
            isFastForwarding = true
            rv.frameSpeed = videoSettings.fastForwardSpeed
        }
    }

    private fun handleRewindRequest(requested: Boolean) {
        if (isNetplayInSession()) return
        if (!requested || isRewinding) return
        val rv = getRetroView()
        isRewinding = true
        rv.isRewinding = true
        rv.frameSpeed = 1
    }

    private fun handleResetRequest() {
        if (isNetplayInSession()) return
        getRetroView().reset()
        onGameReset()
        showToast("Game reset")
    }
}
