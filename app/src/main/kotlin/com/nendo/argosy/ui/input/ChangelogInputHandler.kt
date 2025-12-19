package com.nendo.argosy.ui.input

import com.nendo.argosy.domain.model.ChangelogEntry
import com.nendo.argosy.domain.model.RequiredAction

class ChangelogInputHandler(
    private val getEntry: () -> ChangelogEntry?,
    private val onDismiss: () -> Unit,
    private val onAction: (RequiredAction) -> Unit
) : InputHandler {

    private fun hasRequiredAction(): Boolean =
        getEntry()?.requiredActions?.isNotEmpty() == true

    override fun onConfirm(): InputResult {
        if (hasRequiredAction()) {
            return InputResult.HANDLED
        }
        onDismiss()
        return InputResult.HANDLED
    }

    override fun onBack(): InputResult {
        if (hasRequiredAction()) {
            return InputResult.HANDLED
        }
        onDismiss()
        return InputResult.HANDLED
    }

    override fun onContextMenu(): InputResult {
        val entry = getEntry() ?: return InputResult.UNHANDLED
        val firstAction = entry.requiredActions.firstOrNull() ?: return InputResult.UNHANDLED
        onAction(firstAction)
        return InputResult.HANDLED
    }
}
