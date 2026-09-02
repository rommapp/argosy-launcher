package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.gamedetail.modals.COVER_PICKER_COLUMNS

/**
 * The cover art picker renders on the showcase surface while the pad stays with the interactive
 * one, and its state belongs to DualScreenManager rather than to a detail view model. Returns null
 * when the picker is closed so the caller falls through to its own handling.
 */
fun handleDualCoverPickerInput(event: GamepadEvent): InputResult? {
    val dsm = DualScreenManagerHolder.instance ?: return null
    if (dsm.dualGameDetailState.value?.modalType != ActiveModal.COVER_PICKER) return null
    when (event) {
        GamepadEvent.Up -> dsm.moveDualCoverPickerFocus(-COVER_PICKER_COLUMNS)
        GamepadEvent.Down -> dsm.moveDualCoverPickerFocus(COVER_PICKER_COLUMNS)
        GamepadEvent.Left -> dsm.moveDualCoverPickerFocus(-1)
        GamepadEvent.Right -> dsm.moveDualCoverPickerFocus(1)
        GamepadEvent.Confirm -> dsm.confirmDualCoverAtFocus()
        GamepadEvent.ContextMenu -> dsm.searchDualCovers()
        GamepadEvent.Back -> dsm.dismissDualModal()
        else -> {}
    }
    return InputResult.HANDLED
}
