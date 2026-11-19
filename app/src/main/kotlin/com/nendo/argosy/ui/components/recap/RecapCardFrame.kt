package com.nendo.argosy.ui.components.recap

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun RecapCardFrame(
    maxWidth: Dp,
    cursor: RecapCursor,
    header: @Composable ColumnScope.() -> Unit,
    actions: @Composable RowScope.() -> Unit,
    body: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    RecapBodyScrollEffect(scrollState, cursor)
    GlassPanel(
        modifier = Modifier
            .padding(Dimens.spacingLg)
            .widthIn(max = maxWidth)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Dimens.spacingLg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg),
                content = header
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalEdgeFade(
                        scrollState,
                        fadeHeight = ComponentDefaults.UnlockRecap.bodyFadeHeightDp.dp,
                        top = false
                    )
                    .verticalScroll(scrollState),
                content = body
            )
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.End),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingLg),
                content = actions
            )
        }
    }
}

@Composable
private fun RecapBodyScrollEffect(scrollState: ScrollState, cursor: RecapCursor) {
    val stepPx = with(LocalDensity.current) { ComponentDefaults.UnlockRecap.scrollStepDp.dp.toPx() }
    val reduced = reducedMotion()
    val latest by rememberUpdatedState(cursor)
    LaunchedEffect(scrollState, stepPx, reduced) {
        var seen = latest.bodyScrolls
        var target = scrollState.value
        snapshotFlow { latest.bodyScrolls }.collectLatest { requests ->
            val steps = requests - seen
            seen = requests
            if (steps <= 0) return@collectLatest
            val from = if (scrollState.isScrollInProgress) target else scrollState.value
            target = RecapLayout.bodyScrollTarget(
                from = from,
                direction = latest.bodyScrollDirection,
                steps = steps,
                stepPx = stepPx,
                maxValue = scrollState.maxValue
            )
            if (reduced) scrollState.scrollTo(target) else scrollState.animateScrollTo(target)
        }
    }
}

@Composable
internal fun RecapChip(text: String, color: Color = LocalArgosyTheme.current.textPrimary) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .background(LocalArgosyTheme.current.surfaceElevated)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}
