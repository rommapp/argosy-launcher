package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.common.rememberTaskSnapshotRequest
import com.nendo.argosy.ui.components.RunPlayTimeChip
import com.nendo.argosy.ui.components.showsRunPlayTime
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The running game's last frame with this run's play time, when it last saved and its achievement
 * progress. Draws nothing when the game has no frame.
 */
@Composable
fun RunningFramePanel(
    running: RunningGameUi,
    savedAt: String?,
    raEarned: Int,
    raTotal: Int,
    modifier: Modifier = Modifier
) {
    val request = rememberTaskSnapshotRequest(running.snapshotPath, running.snapshotTakenAtMillis) ?: return
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .widthIn(max = ComponentDefaults.RunningFrame.panelMaxWidthDp.dp)
            .fillMaxWidth()
            .aspectRatio(FRAME_ASPECT)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(theme.surfaceBase)
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (showsRunPlayTime(running.playedThisRunMs)) {
            RunPlayTimeChip(
                playedMs = running.playedThisRunMs,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spacingSm)
            )
        }
        if (savedAt != null || raTotal > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                theme.surfaceBase.copy(alpha = ComponentDefaults.RunningFrame.statsScrimAlpha)
                            )
                        )
                    )
                    .padding(
                        start = Dimens.spacingSm,
                        end = Dimens.spacingSm,
                        top = Dimens.spacingMd,
                        bottom = Dimens.spacingXs
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                savedAt?.let {
                    Text(
                        text = stringResource(R.string.gamedetail_running_frame_saved, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = theme.textPrimary,
                        maxLines = 1
                    )
                }
                if (raTotal > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = ColorTokens.Domain.AchievementTier.softcore,
                            modifier = Modifier.size(Dimens.iconSm)
                        )
                        Text(
                            text = stringResource(R.string.gamedetail_running_frame_ra_progress, raEarned, raTotal),
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private const val FRAME_ASPECT = 16f / 9f
