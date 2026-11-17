package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.touchOnly

val AlphabetSidebarWidth: Dp = ComponentDefaults.AlphabetRail.widthDp.dp

/**
 * [focusedLetter] is the letter the gamepad cursor sits on while the rail holds focus, or null
 * while focus is elsewhere; [currentLetter] is the section the list is showing.
 */
@Composable
fun AlphabetSidebar(
    availableLetters: List<String>,
    currentLetter: String,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusedLetter: String? = null,
    topPadding: Dp = Dimens.headerHeightLg,
    bottomPadding: Dp = Dimens.footerHeight
) {
    val listState = rememberLazyListState()
    val anchorLetter = focusedLetter ?: currentLetter
    val anchorIndex = availableLetters.indexOf(anchorLetter).coerceAtLeast(0)

    LaunchedEffect(anchorLetter) {
        if (availableLetters.isNotEmpty() && anchorLetter.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 32
            val centerOffset = (viewportHeight - itemHeight) / 2
            val targetIndex = anchorIndex.coerceIn(0, availableLetters.lastIndex)
            listState.animateScrollToItem(targetIndex, -centerOffset)
        }
    }

    Box(
        modifier = modifier
            .width(AlphabetSidebarWidth)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = ComponentDefaults.AlphabetRail.backgroundAlpha)
            )
            .padding(top = topPadding, bottom = bottomPadding)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            itemsIndexed(availableLetters, key = { _, letter -> letter }) { _, letter ->
                val isActive = letter == currentLetter
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.5f else 1f,
                    animationSpec = tween(Motion.durationMicro),
                    label = "letterScale"
                )
                Text(
                    text = letter,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .argosyFocusIndicators(
                            focused = letter == focusedLetter,
                            indicators = FocusIndicators.Pill,
                            shape = RoundedCornerShape(Dimens.radiusSm)
                        )
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .touchOnly { onLetterClick(letter) }
                        .padding(vertical = Dimens.spacingXs, horizontal = Dimens.spacingSm)
                )
            }
        }
    }
}
