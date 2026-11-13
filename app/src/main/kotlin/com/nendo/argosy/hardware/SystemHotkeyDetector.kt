package com.nendo.argosy.hardware

import android.view.KeyEvent
import com.nendo.argosy.core.input.HomeKeys

enum class SystemHotkeyAction { HOME, BACK }

/**
 * The global Home and Back combos as physical keycodes. [homeCombo] fires once every key in it
 * has been held for [SystemHotkeyDetector.HOME_HOLD_MS]; [backCombo] completes when its last key
 * is pressed while all the keys before it are held.
 */
data class SystemHotkeyConfig(
    val enabled: Boolean,
    val homeCombo: Set<Int>,
    val backCombo: List<Int>
) {
    fun tracks(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_BUTTON_MODE || keyCode in homeCombo || keyCode in backCombo

    companion object {
        val DISABLED = SystemHotkeyConfig(enabled = false, homeCombo = emptySet(), backCombo = emptyList())

        fun resolve(
            enabled: Boolean,
            customHome: List<Int>?,
            customBack: List<Int>?,
            swapAB: Boolean,
            swapStartSelect: Boolean
        ) = SystemHotkeyConfig(
            enabled = enabled,
            homeCombo = SystemHotkeyCombos.home(customHome).toSet(),
            backCombo = SystemHotkeyCombos.back(customBack, swapAB, swapStartSelect)
        )
    }
}

object SystemHotkeyCombos {
    const val MIN_KEYS = 2
    const val MAX_KEYS = 3

    private val DEFAULT_HOME = listOf(KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START)

    fun home(custom: List<Int>?): List<Int> = custom?.takeIf(::isValid) ?: DEFAULT_HOME

    /**
     * The default is the button Argosy reads as Select followed by the button Argosy reads as
     * Back, both after the user's Start/Select and A/B swaps, matching
     * mapKeycodeToGamepadEvent.
     */
    fun back(custom: List<Int>?, swapAB: Boolean, swapStartSelect: Boolean): List<Int> =
        custom?.takeIf(::isValid) ?: listOf(selectIntentKey(swapStartSelect), backIntentKey(swapAB))

    fun backIntentKey(swapAB: Boolean): Int =
        if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B

    fun selectIntentKey(swapStartSelect: Boolean): Int =
        if (swapStartSelect) KeyEvent.KEYCODE_BUTTON_START else KeyEvent.KEYCODE_BUTTON_SELECT

    fun isRecordable(keyCode: Int): Boolean = keyCode !in RESERVED

    fun isValid(combo: List<Int>): Boolean =
        combo.size in MIN_KEYS..MAX_KEYS && combo.distinct().size == combo.size && combo.all(::isRecordable)

    private val RESERVED = HomeKeys.RESERVED + setOf(KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_BACK)
}

/**
 * Global Home, Back and quick panel from controller combos while another app is in front; true
 * marks a consumed event. While [quickPanelAvailable], the Back combo fires Back on release and
 * opens the quick panel once held for [BACK_COMBO_HOLD_MS]. A guide tap goes Home and a guide
 * held for [GUIDE_HOLD_MS] calls [openRunning].
 */
class SystemHotkeyDetector(
    private val config: () -> SystemHotkeyConfig,
    private val foreignAppInFront: () -> Boolean,
    private val scheduleHold: (Runnable, Long) -> Unit,
    private val cancelHold: (Runnable) -> Unit,
    private val perform: (SystemHotkeyAction) -> Unit,
    private val quickPanelAvailable: () -> Boolean = { false },
    private val openQuickPanel: () -> Unit = {},
    private val openRunning: () -> Unit = {}
) {
    private val held = mutableSetOf<Int>()
    private val consumedUps = mutableSetOf<Int>()
    private var chord: SystemHotkeyConfig? = null
    private var homeArmed = false
    private var backArmed = false
    private var guideHoldFired = false

    private val backHold = Runnable {
        if (!backArmed) return@Runnable
        backArmed = false
        openQuickPanel()
    }

    private val homeHold = Runnable {
        homeArmed = false
        val active = chord ?: return@Runnable
        if (!held.containsAll(active.homeCombo)) return@Runnable
        consumedUps += active.homeCombo
        perform(SystemHotkeyAction.HOME)
    }

    private val guideHold = Runnable {
        guideHoldFired = true
        openRunning()
    }

    fun onKeyDown(keyCode: Int, isRepeat: Boolean): Boolean {
        if (isRepeat) return keyCode in consumedUps
        if (!(chord ?: config()).tracks(keyCode)) return false
        if (held.isEmpty()) chord = config().takeIf { it.enabled && foreignAppInFront() }
        held += keyCode
        val active = chord ?: return false
        return when {
            keyCode == KeyEvent.KEYCODE_BUTTON_MODE -> guideDown()
            completesBack(active, keyCode) -> fireBack(keyCode)
            else -> {
                armHome(active, keyCode)
                false
            }
        }
    }

    fun onKeyUp(keyCode: Int): Boolean {
        val consumed = consumedUps.remove(keyCode)
        if (!held.remove(keyCode)) return consumed
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE && consumed) guideUp()
        if (homeArmed && keyCode in chord?.homeCombo.orEmpty()) cancelHome()
        if (backArmed && keyCode in chord?.backCombo.orEmpty()) {
            disarmBack()
            perform(SystemHotkeyAction.BACK)
        }
        if (held.isEmpty()) chord = null
        return consumed
    }

    fun reset() {
        cancelHome()
        disarmBack()
        cancelHold(guideHold)
        guideHoldFired = false
        held.clear()
        consumedUps.clear()
        chord = null
    }

    private fun guideDown(): Boolean {
        guideHoldFired = false
        consumedUps += KeyEvent.KEYCODE_BUTTON_MODE
        cancelHold(guideHold)
        scheduleHold(guideHold, GUIDE_HOLD_MS)
        return true
    }

    private fun guideUp() {
        cancelHold(guideHold)
        if (!guideHoldFired) perform(SystemHotkeyAction.HOME)
        guideHoldFired = false
    }

    private fun completesBack(active: SystemHotkeyConfig, keyCode: Int): Boolean =
        active.backCombo.size >= SystemHotkeyCombos.MIN_KEYS &&
            keyCode == active.backCombo.last() &&
            held.containsAll(active.backCombo)

    private fun fireBack(keyCode: Int): Boolean {
        cancelHome()
        consumedUps += keyCode
        if (quickPanelAvailable()) {
            backArmed = true
            scheduleHold(backHold, BACK_COMBO_HOLD_MS)
        } else {
            perform(SystemHotkeyAction.BACK)
        }
        return true
    }

    private fun disarmBack() {
        if (!backArmed) return
        backArmed = false
        cancelHold(backHold)
    }

    private fun armHome(active: SystemHotkeyConfig, keyCode: Int) {
        if (homeArmed || keyCode !in active.homeCombo) return
        if (active.homeCombo.size < SystemHotkeyCombos.MIN_KEYS || !held.containsAll(active.homeCombo)) return
        homeArmed = true
        scheduleHold(homeHold, HOME_HOLD_MS)
    }

    private fun cancelHome() {
        if (!homeArmed) return
        homeArmed = false
        cancelHold(homeHold)
    }

    companion object {
        const val HOME_HOLD_MS = 2000L
        const val GUIDE_HOLD_MS = 1000L
        const val BACK_COMBO_HOLD_MS = 3000L
    }
}
