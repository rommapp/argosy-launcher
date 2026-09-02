package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
import com.nendo.argosy.ui.screens.gamedetail.ReviewEditorState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * Single-screen host for the review editor. It sits above the reader so the two stack the way
 * they were opened, and it registers its own hints on the root bar because the game detail hints
 * below it describe controls that are no longer reachable.
 */
@Composable
fun ReviewEditorOverlay(
    state: ReviewEditorState,
    viewModel: GameDetailViewModel,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        ReviewEditorContent(
            state = state,
            onSectionFocus = viewModel::focusReviewEditorSection,
            onVerdictSelect = viewModel::setReviewEditorVerdict,
            onVisibilitySelect = viewModel::setReviewEditorVisibility,
            onBodyChange = viewModel::setReviewEditorBody,
            onSubmit = viewModel::submitReview,
            onDeletePrompt = viewModel::promptReviewDelete,
            onDeleteConfirm = viewModel::confirmReviewDelete,
            onDiscardConfirm = viewModel::closeReviewEditor,
            onConfirmDismiss = viewModel::dismissReviewEditorConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(Dimens.spacingXl)
        )
        FooterHints(
            hints = reviewEditorFooterHints(state),
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> viewModel.confirmReviewEditorAction()
                    InputButton.B -> viewModel.backFromReviewEditor()
                    InputButton.START -> viewModel.submitReview()
                    InputButton.Y -> viewModel.promptReviewDelete()
                    else -> {}
                }
            }
        )
        FooterSpacer()
    }
}
