package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.DrawerItem
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingNavBar(
    visible: Boolean,
    destinations: List<DrawerItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onInteract: () -> Unit,
    modifier: Modifier = Modifier,
    badgeFor: (String) -> Int? = { null }
) {
    AnimatedVisibility(
        visible = visible && destinations.size > 1,
        modifier = modifier,
        enter = slideInVertically(tween(Motion.durationSlide)) { it } +
            fadeIn(tween(Motion.durationSlide)),
        exit = slideOutVertically(tween(Motion.durationSlide)) { it } +
            fadeOut(tween(Motion.durationSlide))
    ) {
        val shape = RoundedCornerShape(Dimens.radiusPill)
        val scrollState = rememberScrollState()
        val currentDestination = remember { BringIntoViewRequester() }
        LaunchedEffect(currentRoute, destinations, scrollState.viewportSize, scrollState.maxValue) {
            if (scrollState.viewportSize > 0) currentDestination.bringIntoView()
        }
        Row(
            modifier = Modifier
                .observeTouchDowns { _, _ -> onInteract() }
                .frostedSurface(shape)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            destinations.forEach { item ->
                key(item.route) {
                    val isCurrent = NavRing.routeMatches(item.route, currentRoute)
                    NavBarDestination(
                        item = item,
                        isCurrent = isCurrent,
                        hasBadge = badgeFor(item.route) != null,
                        onClick = { onNavigate(item.route) },
                        modifier = if (isCurrent) {
                            Modifier.bringIntoViewRequester(currentDestination)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

fun Modifier.revealOnBottomEdgeTouch(edgeHeight: Dp, onReveal: () -> Unit): Modifier = composed {
    val edgePx = with(LocalDensity.current) { edgeHeight.toPx() }
    observeTouchDowns { y, height ->
        if (y >= height - edgePx) onReveal()
    }
}

private fun Modifier.observeTouchDowns(onDown: (y: Float, height: Int) -> Unit): Modifier = composed {
    val currentOnDown by rememberUpdatedState(onDown)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            currentOnDown(down.position.y, size.height)
        }
    }
}

@Composable
private fun NavBarDestination(
    item: DrawerItem,
    isCurrent: Boolean,
    hasBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .argosyFocusIndicators(
                focused = isCurrent,
                indicators = FocusIndicators.Pill,
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickableNoFocus(onClick = onClick)
            .sizeIn(minWidth = minimumTouchTarget, minHeight = minimumTouchTarget)
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = getIconForRoute(item.route),
            contentDescription = stringResource(item.labelRes),
            tint = if (isCurrent) theme.focusAccent else theme.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
        if (hasBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(Dimens.spacingSm)
                    .background(theme.focusAccent, CircleShape)
            )
        }
    }
}
