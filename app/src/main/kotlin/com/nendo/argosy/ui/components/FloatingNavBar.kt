package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
        val currentIndex = destinations.indexOfFirst { NavRing.routeMatches(it.route, currentRoute) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentIndex.coerceAtLeast(0))
        val viewportSize by remember { derivedStateOf { listState.layoutInfo.viewportSize } }
        LaunchedEffect(currentIndex, destinations, viewportSize) {
            if (currentIndex < 0) return@LaunchedEffect
            val layoutInfo = listState.layoutInfo
            val currentItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            if (currentItem == null || currentItem.offset < layoutInfo.viewportStartOffset ||
                currentItem.offset + currentItem.size > layoutInfo.viewportEndOffset
            ) {
                listState.animateScrollToItem(currentIndex)
            }
        }
        LazyRow(
            state = listState,
            modifier = Modifier
                .observeTouchDowns { _, _ -> onInteract() }
                .frostedSurface(shape)
                .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            items(destinations, key = { it.route }) { item ->
                NavBarDestination(
                    item = item,
                    isCurrent = NavRing.routeMatches(item.route, currentRoute),
                    hasBadge = badgeFor(item.route) != null,
                    onClick = { onNavigate(item.route) }
                )
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
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
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
