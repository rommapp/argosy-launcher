package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.reducedMotion

private const val COLLAGE_ALPHA = 0.35f
private const val SCROLL_PIXELS_PER_SECOND = 14f
internal val COVER_ASPECT = 3f / 4f

@Composable
fun PlatformShowcaseContent(slot: PresentationSlot.PlatformShowcase) {
    val theme = LocalArgosyTheme.current
    Box(modifier = Modifier.fillMaxSize()) {
        if (slot.coverPaths.isNotEmpty()) {
            CoverCollage(
                coverPaths = slot.coverPaths,
                modifier = Modifier.fillMaxSize().alpha(COLLAGE_ALPHA)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            theme.surfaceBase.copy(alpha = 0.55f),
                            theme.surfaceBase.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(Dimens.spacingXl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = slot.name,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = theme.textPrimary
            )
            slot.yearSpan?.let { span ->
                Text(
                    text = span,
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.focusAccent
                )
            }
            if (slot.facts.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = Dimens.spacingLg),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
                ) {
                    slot.facts.forEach { fact ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = fact.value,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Medium,
                                color = theme.textPrimary
                            )
                            Text(
                                text = fact.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textDim
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverCollage(coverPaths: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        val rows = remember(coverPaths) { collageRows(coverPaths) }
        rows.forEachIndexed { index, row ->
            CollageRow(
                coverPaths = row,
                reverse = index % 2 == 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CollageRow(coverPaths: List<String>, reverse: Boolean, modifier: Modifier = Modifier) {
    val listState = key(coverPaths) {
        val middle = COLLAGE_ITEM_COUNT / 2
        rememberLazyListState(initialFirstVisibleItemIndex = middle - middle.mod(coverPaths.size))
    }

    val reduced = reducedMotion()
    LaunchedEffect(coverPaths, reverse, reduced) {
        if (coverPaths.isEmpty() || reduced) return@LaunchedEffect
        val step = 4000f
        val durationMs = (step / SCROLL_PIXELS_PER_SECOND * 1000f).toInt()
        while (true) {
            listState.animateScrollBy(
                if (reverse) -step else step,
                tween(durationMs, easing = LinearEasing)
            )
        }
    }

    LazyRow(
        state = listState,
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        modifier = modifier.fillMaxWidth()
    ) {
        items(COLLAGE_ITEM_COUNT) { index ->
            AsyncImage(
                model = rememberFileImageModel(coverPaths[index.mod(coverPaths.size)]),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(COVER_ASPECT)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )
        }
    }
}

private fun collageRows(coverPaths: List<String>): List<List<String>> {
    val perRow = (coverPaths.size + COLLAGE_ROWS - 1) / COLLAGE_ROWS
    val chunks = coverPaths.chunked(perRow)
    return List(COLLAGE_ROWS) { row ->
        chunks.getOrNull(row) ?: run {
            val shift = row.mod(coverPaths.size)
            coverPaths.drop(shift) + coverPaths.take(shift)
        }
    }
}

private const val COLLAGE_ROWS = 4
private const val COLLAGE_ITEM_COUNT = Int.MAX_VALUE
