package com.nendo.argosy.ui.screens.setup.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboard
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.MotionTokens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * What a page body renders against: the rail as it stood for this page, whether the controller
 * keyboard is docked and how tall it is, the layout arrangement, and the TV cap flag.
 */
data class SetupStepFrame(
    val rail: SetupRailState,
    val docked: Boolean,
    val dockedHeight: Dp,
    val layout: SetupLayout,
    val isTv: Boolean,
)

val LocalSetupStepFrame = staticCompositionLocalOf {
    SetupStepFrame(
        rail = SetupRailState(segments = emptyList(), currentIndex = 0),
        docked = false,
        dockedHeight = 0.dp,
        layout = SetupLayout.STANDARD,
        isTv = false,
    )
}

private data class SetupPageFrame<P : Any>(val page: P, val rail: SetupRailState)

@Composable
fun <P : Any> SetupStepScaffold(
    page: P,
    direction: SetupDirection,
    rail: SetupRailState,
    @StringRes pageTitle: Int,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isTv: Boolean = false,
    renderPage: @Composable (P) -> Unit,
) {
    val dockedPx by ControllerKeyboard.dockedHeightPx.collectAsState()
    val density = LocalDensity.current
    val docked = dockedPx > 0
    val dockedHeight = with(density) { dockedPx.toDp() }
    val tier = LocalMotionTier.current
    val drift = if (tier == MotionTier.Snappy) Dimens.spacingMd else Dimens.spacingLg
    val driftPx = with(density) { drift.roundToPx() }
    val layout = setupLayoutFor(LocalUiScale.current.aspectRatioClass)
    Column(modifier = modifier.fillMaxSize()) {
        SetupHeaderBand(
            rail = rail,
            onBack = onBack,
            pageTitle = pageTitle,
            showTitle = docked,
        )
        AnimatedContent(
            targetState = SetupPageFrame(page, rail),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            transitionSpec = { setupPageTransform(direction, tier, driftPx) },
            contentKey = { it.page },
            label = "setup-page",
        ) { frame ->
            CompositionLocalProvider(
                LocalSetupStepFrame provides SetupStepFrame(
                    rail = frame.rail,
                    docked = docked,
                    dockedHeight = dockedHeight,
                    layout = layout,
                    isTv = isTv,
                ),
            ) {
                renderPage(frame.page)
            }
        }
    }
}

@Composable
private fun SetupHeaderBand(
    rail: SetupRailState,
    onBack: (() -> Unit)?,
    @StringRes pageTitle: Int,
    showTitle: Boolean,
) {
    val theme = LocalArgosyTheme.current
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.quickPanelHeaderHeight)
            .padding(horizontal = Dimens.spacingXl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(Dimens.buttonHeight)
                    .clip(RoundedCornerShape(Dimens.radiusControl))
                    .clickableNoFocus(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.setup_header_back_desc),
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconMd),
                )
            }
        }
        AnimatedVisibility(
            visible = showTitle,
            enter = if (reduced) EnterTransition.None else fadeIn(compactionTween()) + expandHorizontally(compactionTween()),
            exit = if (reduced) ExitTransition.None else fadeOut(compactionTween()) + shrinkHorizontally(compactionTween()),
        ) {
            Text(
                text = stringResource(pageTitle),
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SetupRail(state = rail, modifier = Modifier.weight(1f))
        SystemStatusBar(contentColor = theme.textDim, scrim = false, showsRunningSlots = false)
    }
}

internal fun <T> compactionTween() = tween<T>(Motion.durationSlide, easing = Motion.argosyEase)

private fun setupPageTransform(direction: SetupDirection, tier: MotionTier, driftPx: Int): ContentTransform {
    if (tier == MotionTier.Reduced) {
        return fadeIn(tween(MotionTokens.Tween.fastMs)) togetherWith fadeOut(tween(MotionTokens.Tween.fastMs))
    }
    val enterMs = if (tier == MotionTier.Snappy) Motion.durationSlide else Motion.durationPage
    val exitMs = if (tier == MotionTier.Snappy) Motion.durationMicro else Motion.durationContent
    val sign = if (direction == SetupDirection.FORWARD) 1 else -1
    val enter = fadeIn(tween(enterMs, easing = Motion.argosyEase)) +
        slideInHorizontally(tween(enterMs, easing = Motion.argosyEase)) { sign * driftPx }
    val exit = fadeOut(tween(exitMs)) +
        slideOutHorizontally(tween(exitMs)) { -sign * driftPx }
    return ContentTransform(enter, exit, sizeTransform = SizeTransform(clip = false))
}

private fun setupLayoutFor(aspect: AspectRatioClass): SetupLayout = when (aspect) {
    AspectRatioClass.ULTRA_WIDE, AspectRatioClass.WIDE -> SetupLayout.WIDE
    AspectRatioClass.STANDARD -> SetupLayout.STANDARD
    AspectRatioClass.TALL, AspectRatioClass.ULTRA_TALL -> SetupLayout.TALL
}
