package com.nendo.argosy.ui.dualscreen.gamedetail

import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult

/**
 * The review editor renders on the showcase surface while the pad stays with the interactive
 * one, and its draft belongs to DualScreenManager rather than to a detail view model. Returns
 * null when the editor is closed so the caller falls through to its own handling.
 */
fun handleDualReviewEditorInput(event: GamepadEvent): InputResult? {
    val dsm = DualScreenManagerHolder.instance ?: return null
    if (dsm.dualGameDetailState.value?.modalType != ActiveModal.REVIEW_EDITOR) return null
    when (event) {
        GamepadEvent.Up -> dsm.moveDualReviewEditorSection(-1)
        GamepadEvent.Down -> dsm.moveDualReviewEditorSection(1)
        GamepadEvent.Left -> dsm.adjustDualReviewEditor(-1)
        GamepadEvent.Right -> dsm.adjustDualReviewEditor(1)
        GamepadEvent.Confirm -> dsm.confirmDualReviewEditor()
        GamepadEvent.Menu, GamepadEvent.Select -> dsm.submitDualReview()
        GamepadEvent.SecondaryAction -> dsm.promptDualReviewDelete()
        GamepadEvent.Back -> dsm.backDualReviewEditor()
        else -> {}
    }
    return InputResult.HANDLED
}
