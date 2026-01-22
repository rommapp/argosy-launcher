package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem

@Composable
fun RatingsStatusModal(
    game: GameDetailUi,
    focusIndex: Int,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "RATINGS & STATUS",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_VERTICAL to "Navigate",
            InputButton.SOUTH to "Select",
            InputButton.EAST to "Back"
        )
    ) {
        OptionItem(
            icon = Icons.Default.Star,
            label = "Rate Game",
            value = if (game.userRating > 0) "${game.userRating}/10" else "Not rated",
            isFocused = focusIndex == 0,
            onClick = { onAction(MoreOptionAction.RateGame) }
        )
        OptionItem(
            icon = Icons.Default.Whatshot,
            label = "Set Difficulty",
            value = if (game.userDifficulty > 0) "${game.userDifficulty}/10" else "Not set",
            isFocused = focusIndex == 1,
            onClick = { onAction(MoreOptionAction.SetDifficulty) }
        )
        OptionItem(
            icon = Icons.Default.CheckCircle,
            label = "Set Status",
            value = CompletionStatus.fromApiValue(game.status)?.label ?: "Not set",
            isFocused = focusIndex == 2,
            onClick = { onAction(MoreOptionAction.SetStatus) }
        )
    }
}
