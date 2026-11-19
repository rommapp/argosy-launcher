package com.nendo.argosy.ui.components.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.LaunchRecap
import com.nendo.argosy.domain.model.RecapLastSession
import com.nendo.argosy.ui.common.recapTierColor
import com.nendo.argosy.ui.common.recapTierLabelRes
import com.nendo.argosy.ui.common.recapTypeLabelRes
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.LaunchCancelButton
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.GlassPanel
import com.nendo.argosy.ui.primitives.RuledSectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.motionDuration
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import java.time.Instant

private const val PRESENCE_LINES = 2
private const val DESCRIPTION_LINES = 2
private const val SHORT_LINES = 1

@Composable
fun LaunchRecapCard(
    recap: LaunchRecap,
    cursor: RecapCursor,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onCurrent: (Int) -> Unit
) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val short = RecapLayout.isShort(maxHeight.value)
        val spotlight = RecapSpotlight.of(recap.unlocks, cursor.currentIndex)
        RecapCardFrame(
            maxWidth = ComponentDefaults.UnlockRecap.cardMaxWidthDp.dp,
            cursor = cursor,
            header = {
                RecapTitle(recap.gameTitle)
                recap.lastSession?.let { session ->
                    MetaLine(session, unlockCount = recap.unlocks.size.takeIf { short })
                }
            },
            actions = { RecapActions(onStart = onStart, onCancel = onCancel) }
        ) {
            recap.lastSession?.let { session -> PresenceBlock(session, short) }
            if (spotlight != null) {
                if (!short) {
                    Spacer(modifier = Modifier.height(Dimens.spacingLg))
                    RuledSectionHeader(
                        title = stringResource(R.string.ui_launch_recap_unlocks_section),
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg),
                        trailing = { SectionCount(recap.unlocks.size) }
                    )
                }
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                UnlockRevealRow(
                    unlocks = recap.unlocks,
                    currentIndex = spotlight.position - 1,
                    scrubs = cursor.scrubs,
                    onCurrent = onCurrent,
                    short = short
                )
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                UnlockDetailPanel(spotlight, short, showGame = false)
            }
        }
    }
}

/**
 * The card the surface not holding [LaunchRecapCard] shows: the title, the last session and the
 * two actions.
 */
@Composable
fun LaunchRecapCompactCard(
    recap: LaunchRecap,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    GlassPanel(
        modifier = Modifier
            .padding(Dimens.spacingLg)
            .widthIn(max = ComponentDefaults.UnlockRecap.cardMaxWidthDp.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            RecapTitle(recap.gameTitle)
            recap.lastSession?.let { session ->
                MetaLine(session, unlockCount = null)
                PresenceBlock(session, short = false, horizontalInset = false)
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                RecapActions(onStart = onStart, onCancel = onCancel)
            }
        }
    }
}

@Composable
private fun RecapTitle(gameTitle: String) {
    GameTitle(
        title = gameTitle,
        titleStyle = MaterialTheme.typography.headlineSmall,
        titleColor = LocalArgosyTheme.current.textPrimary,
        maxLines = 2
    )
}

@Composable
private fun MetaLine(session: RecapLastSession, unlockCount: Int?) {
    val context = LocalContext.current
    val text = remember(session, unlockCount, context) {
        RecapMetaLine.of(context, session, Instant.now(), unlockCount)
    }
    Spacer(modifier = Modifier.height(Dimens.spacingXs))
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun PresenceBlock(session: RecapLastSession, short: Boolean, horizontalInset: Boolean = true) {
    val presence = session.richPresence?.takeIf { it.isNotBlank() } ?: return
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spacingMd)
            .padding(horizontal = if (horizontalInset) Dimens.spacingLg else 0.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_launch_recap_presence_eyebrow),
            style = MaterialTheme.typography.labelMedium,
            color = theme.textMute
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXs))
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(ComponentDefaults.UnlockRecap.presenceRuleDp.dp)
                    .fillMaxHeight()
                    .background(theme.hairlineHigh)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = presence,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textPrimary,
                maxLines = if (short) SHORT_LINES else PRESENCE_LINES,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionCount(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.titleSmall,
        color = LocalArgosyTheme.current.textDim,
        maxLines = 1
    )
}

@Composable
private fun RecapActions(onStart: () -> Unit, onCancel: () -> Unit) {
    LaunchCancelButton(onClick = onCancel)
    ActionButton(
        label = stringResource(R.string.ui_launch_recap_start),
        onClick = onStart,
        primary = true,
        glyph = InputButton.A
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnlockDetailPanel(spotlight: RecapSpotlight, short: Boolean, showGame: Boolean) {
    val theme = LocalArgosyTheme.current
    val fadeMs = motionDuration(Motion.durationContent)
    val descriptionLines = if (short) SHORT_LINES else DESCRIPTION_LINES
    AnimatedContent(
        targetState = spotlight,
        transitionSpec = { fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs)) },
        contentKey = { it.position },
        label = "recapUnlockDetail",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingLg)
    ) { shown ->
        val unlock = shown.unlock
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            shown.gameLine(showGame)?.let { gameTitle ->
                Text(
                    text = gameTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }
            Text(
                text = unlock.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = unlock.description.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                textAlign = TextAlign.Center,
                minLines = descriptionLines,
                maxLines = descriptionLines,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.fillMaxWidth()
            ) {
                unlock.points?.let { points ->
                    RecapChip(pluralStringResource(R.plurals.ui_launch_recap_points, points, points))
                }
                RecapChip(stringResource(unlock.recapTierLabelRes), color = unlock.recapTierColor)
                unlock.recapTypeLabelRes?.let { RecapChip(stringResource(it)) }
            }
        }
    }
}
