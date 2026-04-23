package com.nendo.argosy.libretro

import android.os.SystemClock
import android.view.KeyEvent
import com.nendo.argosy.data.local.entity.HotkeyAction
import com.nendo.argosy.data.local.entity.HotkeyEntity
import com.nendo.argosy.data.repository.InputConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class HotkeyManager(
    private val inputConfigRepository: InputConfigRepository,
    private val scope: CoroutineScope? = null
) {
    private var hotkeys: List<HotkeyConfig> = emptyList()
    private val pressedKeys = mutableSetOf<Int>()
    private var triggeredAction: HotkeyAction? = null
    private var limitToPlayer1 = true
    private var player1ControllerId: String? = null
    private var platformMappedButtons: Set<Int> = emptySet()
    private var dispatchCallback: ((HotkeyAction) -> Unit)? = null

    // Pending combo state — used when a combo has a hold variant, an instant+hold
    // pair, or any hold-only hotkey. Instant actions that share a combo with a hold
    // variant are deferred so we can distinguish tap vs hold on release.
    private var pendingHoldJob: Job? = null
    private var pendingComboKeyCodes: Set<Int> = emptySet()
    private var pendingInstantAction: HotkeyAction? = null
    private var pendingHoldAction: HotkeyAction? = null
    private var pendingHoldMs: Long = 0
    private var pendingComboStartTime: Long = 0
    private var holdHasFired: Boolean = false

    data class HotkeyConfig(
        val action: HotkeyAction,
        val keyCodes: Set<Int>,
        val controllerId: String?,
        val isEnabled: Boolean,
        val holdMs: Long
    )

    fun setHotkeys(entities: List<HotkeyEntity>) {
        hotkeys = entities.map { entity ->
            HotkeyConfig(
                action = entity.action,
                keyCodes = parseComboJson(entity.buttonComboJson).toSet(),
                controllerId = entity.controllerId,
                isEnabled = entity.isEnabled,
                holdMs = entity.holdMs
            )
        }
    }

    fun setLimitToPlayer1(limit: Boolean) {
        limitToPlayer1 = limit
    }

    fun setPlayer1ControllerId(controllerId: String?) {
        player1ControllerId = controllerId
    }

    fun setPlatformMappedButtons(buttons: Set<Int>) {
        platformMappedButtons = buttons
    }

    /**
     * Register the dispatcher used for async firing (hold-timer completion and
     * deferred tap-on-release). Instant hotkeys with no hold sibling still fire
     * via [onKeyDown]'s return value so the existing synchronous call path keeps
     * working.
     */
    fun setDispatch(callback: (HotkeyAction) -> Unit) {
        dispatchCallback = callback
    }

    fun onKeyDown(keyCode: Int, controllerId: String?): HotkeyAction? {
        if (!isHotkeyKey(keyCode)) return null

        if (limitToPlayer1 && player1ControllerId != null && controllerId != player1ControllerId) {
            return null
        }

        val alreadyPressed = pressedKeys.toSet()
        pressedKeys.add(keyCode)
        triggeredAction = null

        val newlyMatched = hotkeys.filter { hotkey ->
            if (!hotkey.isEnabled) return@filter false
            if (hotkey.controllerId != null && hotkey.controllerId != controllerId) return@filter false
            if (hotkey.keyCodes.isEmpty()) return@filter false
            if (!pressedKeys.containsAll(hotkey.keyCodes)) return@filter false
            if (alreadyPressed.containsAll(hotkey.keyCodes)) return@filter false
            if (hotkey.keyCodes.size == 1 && hotkey.keyCodes.first() in platformMappedButtons) return@filter false
            true
        }

        if (newlyMatched.isEmpty()) return null

        val instantHotkey = newlyMatched.firstOrNull { it.holdMs == 0L }
        val holdHotkey = newlyMatched.firstOrNull { it.holdMs > 0L }

        return when {
            holdHotkey == null && instantHotkey != null -> {
                triggeredAction = instantHotkey.action
                instantHotkey.action
            }
            holdHotkey != null -> {
                startPending(holdHotkey, instantHotkey)
                null
            }
            else -> null
        }
    }

    private fun startPending(holdHotkey: HotkeyConfig, instantHotkey: HotkeyConfig?) {
        cancelPending()
        pendingInstantAction = instantHotkey?.action
        pendingHoldAction = holdHotkey.action
        pendingComboKeyCodes = holdHotkey.keyCodes
        pendingHoldMs = holdHotkey.holdMs
        pendingComboStartTime = SystemClock.elapsedRealtime()
        holdHasFired = false

        val launchScope = scope
        if (launchScope != null) {
            pendingHoldJob = launchScope.launch {
                delay(holdHotkey.holdMs)
                // Re-check inside the scope -- the combo might have been released.
                if (pressedKeys.containsAll(pendingComboKeyCodes) &&
                    pendingHoldAction == holdHotkey.action
                ) {
                    holdHasFired = true
                    triggeredAction = holdHotkey.action
                    dispatchCallback?.invoke(holdHotkey.action)
                }
            }
        }
    }

    private fun cancelPending() {
        pendingHoldJob?.cancel()
        pendingHoldJob = null
    }

    private fun clearPending() {
        cancelPending()
        pendingInstantAction = null
        pendingHoldAction = null
        pendingComboKeyCodes = emptySet()
        pendingHoldMs = 0
        pendingComboStartTime = 0
        holdHasFired = false
    }

    fun onKeyUp(keyCode: Int): HotkeyAction? {
        val wasInCombo = keyCode in pendingComboKeyCodes
        pressedKeys.remove(keyCode)

        // Pending combo broken before hold timer fired — decide whether tap wins.
        if (wasInCombo && !holdHasFired && pendingHoldAction != null) {
            val elapsed = SystemClock.elapsedRealtime() - pendingComboStartTime
            val instantAction = pendingInstantAction
            val tapQualifies = instantAction != null && elapsed < TAP_THRESHOLD_MS
            clearPending()
            if (tapQualifies) {
                triggeredAction = instantAction
                dispatchCallback?.invoke(instantAction!!)
                return instantAction
            }
            // 200ms <= elapsed < holdMs: discard both (nothing fires).
        }

        val action = triggeredAction
        if (pressedKeys.isEmpty()) {
            triggeredAction = null
            clearPending()
        }
        return action
    }

    fun isHotkeyActive(action: HotkeyAction): Boolean {
        val hotkey = hotkeys.find { it.action == action && it.isEnabled } ?: return false
        return hotkey.keyCodes.isNotEmpty() && pressedKeys.containsAll(hotkey.keyCodes)
    }

    fun getTriggeredAction(): HotkeyAction? = triggeredAction

    fun clearState() {
        pressedKeys.clear()
        triggeredAction = null
        clearPending()
    }

    private fun parseComboJson(jsonStr: String): List<Int> {
        return try {
            val result = mutableListOf<Int>()
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getInt(i))
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        /**
         * Max elapsed ms between combo-press and combo-release that still counts as
         * a "tap" when an instant+hold pair shares a combo. Above this but below the
         * hold's own threshold, both actions are discarded -- the user didn't commit
         * to either.
         */
        private const val TAP_THRESHOLD_MS = 200L

        private val HOTKEY_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_C,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_Z,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2,
            KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_THUMBL,
            KeyEvent.KEYCODE_BUTTON_THUMBR,
            KeyEvent.KEYCODE_BACK
        )

        fun isHotkeyKey(keyCode: Int): Boolean = keyCode in HOTKEY_KEYS

        fun getKeyName(keyCode: Int): String {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> "A"
                KeyEvent.KEYCODE_BUTTON_B -> "B"
                KeyEvent.KEYCODE_BUTTON_C -> "M1"
                KeyEvent.KEYCODE_BUTTON_X -> "X"
                KeyEvent.KEYCODE_BUTTON_Y -> "Y"
                KeyEvent.KEYCODE_BUTTON_Z -> "M2"
                KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
                KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
                KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
                KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
                KeyEvent.KEYCODE_BUTTON_START -> "Start"
                KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
                KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
                KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
                KeyEvent.KEYCODE_BACK -> "Back"
                else -> KeyEvent.keyCodeToString(keyCode)
            }
        }

        fun formatCombo(keyCodes: List<Int>): String {
            if (keyCodes.isEmpty()) return "Not set"
            return keyCodes.joinToString(" + ") { getKeyName(it) }
        }
    }
}
