package com.nendo.argosy.ui.dualscreen.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorSection
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorState
import com.nendo.argosy.ui.screens.gamedetail.components.ReviewEditorContent
import com.nendo.argosy.ui.screens.gamedetail.components.reviewEditorFooterHints
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.touchOnly

/**
 * The review editor on the upper display. It draws its hints inline because it covers the
 * surface that holds the root bar, the same way the file picker does.
 */
@Composable
internal fun DualReviewEditorModal(
    state: ReviewEditorState,
    onSectionFocus: (ReviewEditorSection) -> Unit,
    onVerdictSelect: (Boolean) -> Unit,
    onVisibilitySelect: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onSubmit: () -> Unit,
    onDeletePrompt: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDiscard: () -> Unit,
    onConfirmDismiss: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .touchOnly { onBack() },
        contentAlignment = Alignment.Center
    ) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.9f)
                .touchOnly { }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.spacingLg)
            ) {
                ReviewEditorContent(
                    state = state,
                    onSectionFocus = onSectionFocus,
                    onVerdictSelect = onVerdictSelect,
                    onVisibilitySelect = onVisibilitySelect,
                    onBodyChange = onBodyChange,
                    onSubmit = onSubmit,
                    onDeletePrompt = onDeletePrompt,
                    onDeleteConfirm = onDeleteConfirm,
                    onDiscardConfirm = onDiscard,
                    onConfirmDismiss = onConfirmDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                FooterBar(
                    hints = reviewEditorFooterHints(state),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.A -> onConfirm()
                            InputButton.B -> onBack()
                            InputButton.START -> onSubmit()
                            InputButton.Y -> onDeletePrompt()
                            else -> {}
                        }
                    }
                )
            }
        }
    }
}
