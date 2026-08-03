package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * Dot strip marking the focused slot of a rail. A [currentIndex] outside 0 until [totalCount]
 * leaves every dot inactive, which is how callers show that focus has moved off the games.
 */
@Composable
fun PositionIndicator(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            ComponentDefaults.Carousel.dotGap.dp,
            Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(totalCount) { index ->
            val isActive = index == (currentIndex % totalCount)
            Box(
                modifier = Modifier
                    .size(
                        if (isActive) {
                            ComponentDefaults.Carousel.dotSizeActive.dp
                        } else {
                            ComponentDefaults.Carousel.dotSize.dp
                        }
                    )
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            theme.focusAccent
                        } else {
                            theme.textDim.copy(alpha = ComponentDefaults.Carousel.dotInactiveAlpha)
                        }
                    )
            )
        }
    }
}
