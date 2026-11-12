package com.nendo.argosy.ui.screens.setup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nendo.argosy.R
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import java.util.Locale

@Composable
fun SetupStepBody(
    lead: SetupStepLead,
    actions: List<SetupAction>,
    onAction: (actionIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    contextOnCompanion: Boolean = false,
    stackLead: Boolean = false,
    context: (@Composable () -> Unit)? = null,
    leadTrailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val frame = LocalSetupStepFrame.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val keyboardPadding by animateDpAsState(
        targetValue = if (frame.docked) frame.dockedHeight else 0.dp,
        animationSpec = if (reduced) snap() else compactionTween(),
        label = "setup-keyboard-padding",
    )
    val shownActions = remember(actions, frame.docked) {
        actions.withIndex().filter { !frame.docked || !it.value.hiddenWhenDocked }
    }
    val companionHoldsContext = contextOnCompanion && context != null
    val shownContext = context.takeUnless { contextOnCompanion }
    if (frame.layout == SetupLayout.WIDE && !stackLead) {
        WideBody(
            lead = lead,
            frame = frame,
            reduced = reduced,
            keyboardPadding = keyboardPadding,
            actions = shownActions,
            onAction = onAction,
            contextOnCompanion = companionHoldsContext,
            context = shownContext,
            leadTrailing = leadTrailing,
            modifier = modifier,
            content = content,
        )
    } else {
        StackedBody(
            lead = lead,
            frame = frame,
            reduced = reduced,
            keyboardPadding = keyboardPadding,
            actions = shownActions,
            onAction = onAction,
            contextOnCompanion = companionHoldsContext,
            context = shownContext,
            leadTrailing = leadTrailing,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun WideBody(
    lead: SetupStepLead,
    frame: SetupStepFrame,
    reduced: Boolean,
    keyboardPadding: Dp,
    actions: List<IndexedValue<SetupAction>>,
    onAction: (Int) -> Unit,
    contextOnCompanion: Boolean,
    context: (@Composable () -> Unit)?,
    leadTrailing: (@Composable () -> Unit)?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = LocalUiScale.current.scale
    val leadWidth = (DimensionTokens.Layout.setupLeadColumnWidth * s).dp
    val tvCap = leadWidth + Dimens.spacingXl + (DimensionTokens.Layout.setupContentMaxWidth * s).dp
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .then(if (frame.isTv) Modifier.widthIn(max = tvCap) else Modifier)
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingXl)
                .padding(bottom = Dimens.spacingLg + keyboardPadding),
        ) {
            AnimatedVisibility(
                visible = !frame.docked,
                enter = if (reduced) EnterTransition.None else fadeIn(compactionTween()) + expandHorizontally(compactionTween()),
                exit = if (reduced) ExitTransition.None else fadeOut(compactionTween()) + shrinkHorizontally(compactionTween()),
            ) {
                Row {
                    Column(modifier = Modifier.width(leadWidth).fillMaxHeight()) {
                        LeadBlock(lead = lead, rail = frame.rail, trailing = leadTrailing)
                        if (contextOnCompanion) {
                            CompanionContextNote()
                        } else if (context != null) {
                            ContextWell(context)
                        }
                    }
                    Spacer(modifier = Modifier.width(Dimens.spacingXl))
                }
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    content = content,
                )
                SetupActionRow(actions = actions, onAction = onAction, stacked = false)
            }
        }
    }
}

@Composable
private fun StackedBody(
    lead: SetupStepLead,
    frame: SetupStepFrame,
    reduced: Boolean,
    keyboardPadding: Dp,
    actions: List<IndexedValue<SetupAction>>,
    onAction: (Int) -> Unit,
    contextOnCompanion: Boolean,
    context: (@Composable () -> Unit)?,
    leadTrailing: (@Composable () -> Unit)?,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter = if (reduced) EnterTransition.None else fadeIn(compactionTween()) + expandVertically(compactionTween())
    val exit = if (reduced) ExitTransition.None else fadeOut(compactionTween()) + shrinkVertically(compactionTween())
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.spacingXl)
            .padding(bottom = Dimens.spacingLg + keyboardPadding),
    ) {
        AnimatedVisibility(visible = !frame.docked, enter = enter, exit = exit) {
            Column(modifier = Modifier.padding(bottom = Dimens.spacingMd)) {
                LeadBlock(lead = lead, rail = frame.rail, trailing = leadTrailing)
                if (contextOnCompanion) CompanionContextNote()
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            content = content,
        )
        if (context != null) {
            AnimatedVisibility(visible = !frame.docked, enter = enter, exit = exit) {
                ContextStrip(context)
            }
        }
        SetupActionRow(
            actions = actions,
            onAction = onAction,
            stacked = frame.layout == SetupLayout.TALL,
        )
    }
}

@Composable
private fun LeadBlock(lead: SetupStepLead, rail: SetupRailState, trailing: (@Composable () -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
        LeadText(lead = lead, rail = rail, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun LeadText(lead: SetupStepLead, rail: SetupRailState, modifier: Modifier) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
        if (rail.stepCount > 0) {
            Text(
                text = stringResource(R.string.setup_lead_eyebrow, rail.stepNumber, rail.stepCount)
                    .uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = ComponentDefaults.QuickPanel.sectionLabelTrackingSp.sp,
                ),
                color = theme.textDim,
            )
        }
        Text(
            text = stringResource(lead.title),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
        )
        if (lead.purpose != null) {
            Text(
                text = stringResource(lead.purpose),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
            )
        }
    }
}

@Composable
private fun CompanionContextNote() {
    Text(
        text = stringResource(R.string.setup_lead_context_on_other_screen),
        style = MaterialTheme.typography.bodySmall,
        color = LocalArgosyTheme.current.textDim,
        modifier = Modifier.padding(top = Dimens.spacingMd),
    )
}

@Composable
private fun ContextWell(context: @Composable () -> Unit) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    Box(
        modifier = Modifier
            .padding(top = Dimens.spacingMd)
            .fillMaxWidth()
            .clip(shape)
            .background(theme.surfaceRaised)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
    ) {
        ContextText(context)
    }
}

@Composable
private fun ContextStrip(context: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spacingSm),
    ) {
        ContextText(context)
    }
}

@Composable
private fun ContextText(context: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides LocalArgosyTheme.current.textDim) {
        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
            context()
        }
    }
}

@Composable
private fun SetupActionRow(
    actions: List<IndexedValue<SetupAction>>,
    onAction: (Int) -> Unit,
    stacked: Boolean,
) {
    if (actions.isEmpty()) return
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        ) {
            actions.asReversed().forEach { (index, action) ->
                SetupActionButton(action, onClick = { onAction(index) }, modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.End),
        ) {
            actions.forEach { (index, action) ->
                SetupActionButton(action, onClick = { onAction(index) })
            }
        }
    }
}

@Composable
private fun SetupActionButton(action: SetupAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val count = action.labelCount
    ActionButton(
        label = if (count != null) stringResource(action.label, count) else stringResource(action.label),
        onClick = onClick,
        modifier = modifier,
        focused = action.focused,
        primary = action.primary,
    )
}
