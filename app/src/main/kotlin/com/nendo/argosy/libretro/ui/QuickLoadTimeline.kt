package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.nendo.argosy.libretro.SaveStateManager
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatSaveSize
import com.nendo.argosy.util.formatSaveTimestamp

@Composable
fun QuickLoadTimeline(
    entries: List<SaveStateManager.SlotInfo>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentEntries = rememberUpdatedState(entries)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnLoad = rememberUpdatedState(onLoad)
    val currentOnDismiss = rememberUpdatedState(onDismiss)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = (idx - 1).coerceAtLeast(0)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                val idx = currentFocusedIndex.value
                val newIndex = (idx + 1).coerceAtMost(currentEntries.value.lastIndex)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                currentEntries.value.getOrNull(currentFocusedIndex.value)?.let { currentOnLoad.value(it.slotNumber) }
                return InputResult.HANDLED
            }
            override fun onBack(): InputResult {
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val focused = entries.getOrNull(focusedIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        if (entries.isEmpty()) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(12.dp)
                    .focusProperties { canFocus = false },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No quick saves",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Use Quick Save to build a history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val maxHeightDp = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
            Surface(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .heightIn(max = maxHeightDp)
                    .padding(12.dp)
                    .focusProperties { canFocus = false },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxHeight().padding(20.dp)) {
                    Text(
                        text = "Quick Load",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TimelinePreview(
                            slot = focused,
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                        )
                        TimelineList(
                            entries = entries,
                            focusedIndex = focusedIndex,
                            onFocusChange = onFocusChange,
                            onLoad = onLoad,
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FooterBarWithState(
                        hints = listOf(
                            FooterHintItem(InputButton.A, "Load"),
                            FooterHintItem(InputButton.B, "Back")
                        ),
                        onHintClick = { button ->
                            when (button) {
                                InputButton.A -> focused?.let { onLoad(it.slotNumber) }
                                InputButton.B -> onDismiss()
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }

    return inputHandler
}

@Composable
private fun TimelinePreview(
    slot: SaveStateManager.SlotInfo?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val screenshotFile = slot?.screenshotFile
        if (screenshotFile != null && screenshotFile.exists()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(screenshotFile)
                    .memoryCacheKey("${screenshotFile.absolutePath}_${slot?.timestamp}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .build(),
                contentDescription = "Quick save preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            Text(
                text = "No Screenshot",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        if (slot?.timestamp != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = formatSaveTimestamp(slot.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                Text(
                    text = formatSaveSize(slot.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun TimelineList(
    entries: List<SaveStateManager.SlotInfo>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(focusedIndex) {
        if (entries.isNotEmpty() && focusedIndex in entries.indices) {
            listState.animateScrollToItem(focusedIndex)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.slotNumber }) { index, entry ->
            TimelineRow(
                label = if (index == 0) "Latest" else "Quick save",
                slot = entry,
                isFocused = index == focusedIndex,
                onClick = { onFocusChange(index) },
                onLoad = { onLoad(entry.slotNumber) }
            )
        }
    }
}

@Composable
private fun TimelineRow(
    label: String,
    slot: SaveStateManager.SlotInfo,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLoad: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val screenshotFile = slot.screenshotFile
            if (screenshotFile != null && screenshotFile.exists()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(screenshotFile)
                        .memoryCacheKey("${screenshotFile.absolutePath}_${slot.timestamp}")
                        .diskCachePolicy(CachePolicy.DISABLED)
                        .build(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (slot.timestamp != null) {
                Text(
                    text = formatSaveTimestamp(slot.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
