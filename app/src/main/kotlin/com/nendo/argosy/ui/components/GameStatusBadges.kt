package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.stream.LocalStreamFirst
import com.nendo.argosy.ui.theme.Dimens

/**
 * The favourite heart and the downloaded check, or the stream mark when the game streams first and
 * is not on the device. Draws nothing when none apply. A running game shows neither the check nor
 * the stream mark; [showDownloaded] off drops the check alone.
 */
@Composable
fun GameStatusBadges(
    game: HomeGameUi,
    downloadIndicator: GameDownloadIndicator,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    showDownloaded: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween
) {
    val isDownloaded = game.isDownloaded && showDownloaded && !isRunning
    val isStreamable = !isRunning && LocalStreamFirst.current.leads(
        platformSlug = game.platformSlug,
        isRommGame = game.isRommGame,
        isOnDevice = game.isDownloaded || game.needsInstall || downloadIndicator.isShown
    )
    if (!game.isFavorite && !isDownloaded && !isStreamable) return
    Row(modifier = modifier, horizontalArrangement = horizontalArrangement) {
        IndicatorBadge(
            visible = game.isFavorite,
            icon = Icons.Default.Favorite,
            contentDescription = stringResource(R.string.ui_game_card_favorite)
        )
        if (isStreamable && !isDownloaded) {
            StreamMark()
        } else {
            IndicatorBadge(
                visible = isDownloaded,
                icon = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.ui_game_card_downloaded)
            )
        }
    }
}

@Composable
private fun StreamMark() {
    Box(
        modifier = Modifier
            .size(Dimens.iconSm + Dimens.borderMedium)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_stream_play_network),
            contentDescription = stringResource(R.string.stream_tile_mark_description),
            tint = Color.White,
            modifier = Modifier.size(Dimens.iconXs)
        )
    }
}

@Composable
private fun IndicatorBadge(
    visible: Boolean,
    icon: ImageVector,
    contentDescription: String
) {
    if (visible) {
        Box(
            modifier = Modifier
                .size(Dimens.iconSm + Dimens.borderMedium)
                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(Dimens.iconXs)
            )
        }
    } else {
        Box(modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium))
    }
}
