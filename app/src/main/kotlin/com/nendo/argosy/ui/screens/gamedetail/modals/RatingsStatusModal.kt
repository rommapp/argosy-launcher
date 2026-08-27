package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.MoreOptionAction
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.common.labelRes

@Composable
fun RatingsStatusModal(
    game: GameDetailUi,
    focusIndex: Int,
    onAction: (MoreOptionAction) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = stringResource(R.string.gamedetail_ratings_status_title),
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_VERTICAL to
                stringResource(R.string.gamedetail_ratings_status_footer_navigate),
            InputButton.A to stringResource(R.string.gamedetail_ratings_status_footer_select),
            InputButton.B to stringResource(R.string.gamedetail_ratings_status_footer_back)
        )
    ) {
        OptionItem(
            icon = Icons.Default.Star,
            label = stringResource(R.string.gamedetail_ratings_status_rate_game),
            value = if (game.userRating > 0) {
                stringResource(R.string.gamedetail_ratings_status_score_value, game.userRating)
            } else {
                stringResource(R.string.gamedetail_ratings_status_rate_game_unset)
            },
            isFocused = focusIndex == 0,
            onClick = { onAction(MoreOptionAction.RateGame) }
        )
        OptionItem(
            icon = Icons.Default.Whatshot,
            label = stringResource(R.string.gamedetail_ratings_status_set_difficulty),
            value = if (game.userDifficulty > 0) {
                stringResource(R.string.gamedetail_ratings_status_score_value, game.userDifficulty)
            } else {
                stringResource(R.string.gamedetail_ratings_status_difficulty_unset)
            },
            isFocused = focusIndex == 1,
            onClick = { onAction(MoreOptionAction.SetDifficulty) }
        )
        OptionItem(
            icon = Icons.Default.CheckCircle,
            label = stringResource(R.string.gamedetail_ratings_status_set_status),
            value = CompletionStatus.fromApiValue(game.status)?.let { stringResource(it.labelRes) }
                ?: stringResource(R.string.gamedetail_ratings_status_status_unset),
            isFocused = focusIndex == 2,
            onClick = { onAction(MoreOptionAction.SetStatus) }
        )
    }
}
