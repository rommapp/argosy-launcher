package com.nendo.argosy.ui.screens.savetimeline

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotCardBadges
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotDeviceUi
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotThumb
import com.nendo.argosy.ui.common.savechannel.snapshot.label
import com.nendo.argosy.ui.common.savechannel.snapshot.snapshotRelativeTime
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.max

@Composable
internal fun SaveTimelineFloaterPlacement(
    anchor: IntOffset?,
    gapPx: Float,
    marginPx: Float,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    content: @Composable () -> Unit
) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var placed by remember { mutableStateOf(false) }
    val position = remember { Animatable(IntOffset.Zero, IntOffset.VectorConverter) }
    val animate = !reducedMotion()
    if (anchor == null) {
        SideEffect { placed = false }
        return
    }
    val target = floaterTarget(anchor, measured, gapPx, marginPx, viewportWidthPx, viewportHeightPx)
    LaunchedEffect(target, measured) {
        if (!animate || !placed || measured == IntSize.Zero) {
            position.snapTo(target)
            placed = measured != IntSize.Zero
        } else {
            position.animateTo(target, tween(Motion.durationSlide, easing = Motion.argosyEase))
        }
    }
    Box(
        modifier = Modifier
            .offset { position.value }
            .onSizeChanged { measured = it }
    ) {
        content()
    }
}

private fun floaterTarget(
    anchor: IntOffset,
    size: IntSize,
    gapPx: Float,
    marginPx: Float,
    viewportWidthPx: Int,
    viewportHeightPx: Int
): IntOffset {
    val maxX = max(marginPx, viewportWidthPx - marginPx - size.width)
    val x = (anchor.x - size.width / 2f).coerceIn(marginPx, maxX)
    val below = anchor.y + gapPx
    val above = anchor.y - gapPx - size.height
    val spareBelow = viewportHeightPx - marginPx - below - size.height
    val spareAbove = above - marginPx
    val y = when {
        spareBelow >= 0 && spareAbove >= 0 -> if (spareBelow >= spareAbove) below else above
        spareBelow >= 0 -> below
        spareAbove >= 0 -> above
        else -> below.coerceIn(marginPx, max(marginPx, viewportHeightPx - marginPx - size.height))
    }
    return IntOffset(x.toInt(), y.toInt())
}

@Composable
internal fun SaveTimelineFloater(
    lane: SaveTimelineLaneUi,
    node: SaveTimelineNodeUi,
    laneColor: Color,
    coverPath: String?,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val card = node.card
    val shape = RoundedCornerShape(Dimens.radiusLg)
    Row(
        modifier = Modifier
            .widthIn(max = Dimens.saveTimelineFloaterWidth)
            .shadow(Dimens.elevationLg, shape)
            .clip(shape)
            .background(theme.surfaceElevated)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        SnapshotThumb(
            url = card.thumbnailUrl,
            fallbackPath = coverPath,
            modifier = Modifier.width(Dimens.saveTimelineFloaterThumb)
        )
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.dotSm)
                        .clip(CircleShape)
                        .background(laneColor)
                )
                Text(
                    text = lane.tile.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SnapshotCardBadges(card, LocalLauncherTheme.current.semanticColors.warning)
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                Text(
                    text = snapshotRelativeTime(card.savedAt).orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1
                )
                card.snapshotId?.let {
                    Text(
                        text = stringResource(R.string.save_channels_timeline_floater_id, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textMute,
                        maxLines = 1
                    )
                }
            }
            if (!card.isOlderClient) {
                Text(
                    text = (card.device ?: SnapshotDeviceUi.Unknown).label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
