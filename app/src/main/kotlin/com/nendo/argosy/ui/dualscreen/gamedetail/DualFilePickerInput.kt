package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult

/**
 * The download file picker renders on the showcase surface while the pad stays with the
 * interactive one, and its state belongs to DualScreenManager rather than to a detail view model.
 * Returns null when the picker is closed so the caller falls through to its own handling.
 */
fun handleDualFilePickerInput(event: GamepadEvent): InputResult? {
    val dsm = DualScreenManagerHolder.instance ?: return null
    if (dsm.dualGameDetailState.value?.modalType != ActiveModal.FILE_PICKER) return null
    when (event) {
        GamepadEvent.Up -> dsm.moveDualFilePickerFocus(-1)
        GamepadEvent.Down -> dsm.moveDualFilePickerFocus(1)
        GamepadEvent.Left -> if (!dsm.moveDualFilePickerButtonFocus(-1)) {
            dsm.setDualFocusedFilePickerGroupCollapsed(collapse = true)
        }
        GamepadEvent.Right -> if (!dsm.moveDualFilePickerButtonFocus(1)) {
            dsm.setDualFocusedFilePickerGroupCollapsed(collapse = false)
        }
        GamepadEvent.Confirm -> dsm.activateDualFilePickerFocused()
        GamepadEvent.PrevSection -> dsm.jumpDualFilePickerGroup(-1)
        GamepadEvent.NextSection -> dsm.jumpDualFilePickerGroup(1)
        GamepadEvent.ContextMenu -> dsm.confirmDualFilePicker()
        GamepadEvent.Back -> dsm.dismissDualModal()
        else -> {}
    }
    return InputResult.HANDLED
}
