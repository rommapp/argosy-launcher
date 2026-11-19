package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nendo.argosy.R
import com.nendo.argosy.libretro.SaveStateManager
import com.nendo.argosy.ui.common.ScreenshotImage
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.horizontalEdgeFade
import com.nendo.argosy.util.formatSaveTimestamp

@Composable
internal fun QuickLoadHistoryStrip(
    entries: List<SaveStateManager.SlotInfo>,
    focusedIndex: Int?,
    onLoad: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val reduced = reducedMotion()

    LaunchedEffect(focusedIndex, entries.size) {
        if (focusedIndex != null && focusedIndex in entries.indices) {
            listState.animateScrollToItemCentered(focusedIndex, reduced)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .horizontalEdgeFade(listState, fadeWidth = Dimens.spacingXl)
            .heightIn(min = DimensionTokens.Layout.inGameQuickHistoryThumbHeight.dp + Dimens.spacingXs * 2),
        contentPadding = PaddingValues(Dimens.spacingXs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.slotNumber }) { index, entry ->
            QuickLoadHistoryThumb(
                slot = entry,
                label = if (index == 0) {
                    stringResource(R.string.ingame_quickload_row_latest)
                } else {
                    stringResource(R.string.ingame_quickload_row_entry)
                },
                isFocused = index == focusedIndex,
                onClick = { onLoad(entry.slotNumber) }
            )
        }
    }
}

@Composable
private fun QuickLoadHistoryThumb(
    slot: SaveStateManager.SlotInfo,
    label: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Box(
        modifier = Modifier
            .size(
                width = DimensionTokens.Layout.inGameQuickHistoryThumbWidth.dp,
                height = DimensionTokens.Layout.inGameQuickHistoryThumbHeight.dp
            )
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Ring,
                shape = shape
            )
            .clip(shape)
            .background(Color.Black)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val screenshotFile = slot.screenshotFile
        if (screenshotFile != null) {
            ScreenshotImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(screenshotFile)
                    .memoryCacheKey("${screenshotFile.absolutePath}_${slot.timestamp}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = stringResource(R.string.ingame_quickload_preview_no_screenshot),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = PLACEHOLDER_TEXT_ALPHA),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Dimens.spacingXs)
            )
        }
        if (slot.timestamp != null) {
            Text(
                text = formatSaveTimestamp(LocalContext.current, slot.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = CAPTION_SCRIM_ALPHA))
                    .padding(horizontal = Dimens.spacingXs)
            )
        }
    }
}

private const val PLACEHOLDER_TEXT_ALPHA = 0.5f
private const val CAPTION_SCRIM_ALPHA = 0.6f
