package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * Segmented setup progress. Not focusable and not tappable: pages change through Back and Next
 * only.
 */
@Composable
fun SetupRail(
    state: SetupRailState,
    modifier: Modifier = Modifier,
) {
    val s = LocalUiScale.current.scale
    val segmentHeight = (ComponentDefaults.Setup.railSegmentHeightDp * s).dp
    val gap = (ComponentDefaults.Setup.railSegmentGapDp * s).dp
    val description = stringResource(R.string.setup_rail_desc, state.stepNumber, state.stepCount)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.segments.forEachIndexed { index, segment ->
            if (segment.subTicks <= 1) {
                RailBar(state.stateOf(index), segmentHeight)
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap / 2),
                ) {
                    repeat(segment.subTicks) { tick ->
                        RailBar(state.stateOf(index, tick), segmentHeight)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.RailBar(state: SetupSegmentState, height: Dp) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val target = when (state) {
        SetupSegmentState.DONE -> theme.focusAccent.copy(alpha = ComponentDefaults.Setup.railDoneAlpha)
        SetupSegmentState.CURRENT -> theme.focusAccent
        SetupSegmentState.UPCOMING -> theme.hairlineHigh
    }
    val color: Color by animateColorAsState(
        targetValue = target,
        animationSpec = if (reduced) snap() else tween(Motion.durationSlide, easing = Motion.argosyEase),
        label = "setup-rail-segment",
    )
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(height)
            .clip(shape)
            .background(color),
    )
}
