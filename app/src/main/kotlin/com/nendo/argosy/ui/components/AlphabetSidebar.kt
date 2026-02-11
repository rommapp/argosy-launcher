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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun AlphabetSidebar(
    availableLetters: List<String>,
    currentLetter: String,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val currentIndex = availableLetters.indexOf(currentLetter).coerceAtLeast(0)

    LaunchedEffect(currentLetter) {
        if (availableLetters.isNotEmpty() && currentLetter.isNotEmpty()) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 32
            val centerOffset = (viewportHeight - itemHeight) / 2
            val targetIndex = currentIndex.coerceIn(0, availableLetters.lastIndex)
            listState.animateScrollToItem(targetIndex, -centerOffset)
        }
    }

    Box(
        modifier = modifier
            .width(40.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .padding(top = Dimens.headerHeightLg, bottom = Dimens.footerHeight)
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
                    animationSpec = tween(150),
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
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clickableNoFocus { onLetterClick(letter) }
                        .padding(vertical = 4.dp, horizontal = 8.dp)
                )
            }
        }
    }
}
