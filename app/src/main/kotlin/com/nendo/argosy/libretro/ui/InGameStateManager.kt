package com.nendo.argosy.libretro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.libretro.SaveStateManager
import com.nendo.argosy.ui.common.savechannel.formatSize
import com.nendo.argosy.ui.common.savechannel.formatTimestamp
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.NestedModal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.util.clickableNoFocus

enum class StateManagerViewMode { SPLIT, CAROUSEL }

@Composable
fun InGameStateManager(
    slots: List<SaveStateManager.SlotInfo>,
    channelName: String?,
    focusedIndex: Int,
    viewMode: StateManagerViewMode,
    showDeleteConfirmation: Boolean,
    onFocusChange: (Int) -> Unit,
    onViewModeToggle: () -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit,
    onDeleteRequest: (Int) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onDismiss: () -> Unit
): InputHandler {
    val currentFocusedIndex = rememberUpdatedState(focusedIndex)
    val currentSlots = rememberUpdatedState(slots)
    val currentShowDelete = rememberUpdatedState(showDeleteConfirmation)
    val currentOnFocusChange = rememberUpdatedState(onFocusChange)
    val currentOnViewModeToggle = rememberUpdatedState(onViewModeToggle)
    val currentOnSave = rememberUpdatedState(onSave)
    val currentOnLoad = rememberUpdatedState(onLoad)
    val currentOnDeleteRequest = rememberUpdatedState(onDeleteRequest)
    val currentOnDeleteConfirm = rememberUpdatedState(onDeleteConfirm)
    val currentOnDeleteCancel = rememberUpdatedState(onDeleteCancel)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val currentViewMode = rememberUpdatedState(viewMode)

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val mode = currentViewMode.value
                if (mode == StateManagerViewMode.SPLIT) {
                    val idx = currentFocusedIndex.value
                    val newIndex = (idx - 3).coerceAtLeast(0)
                    if (newIndex != idx) currentOnFocusChange.value(newIndex)
                } else {
                    // Carousel: no vertical nav
                }
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val mode = currentViewMode.value
                if (mode == StateManagerViewMode.SPLIT) {
                    val idx = currentFocusedIndex.value
                    val lastIndex = currentSlots.value.lastIndex
                    val newIndex = (idx + 3).coerceAtMost(lastIndex)
                    if (newIndex != idx) currentOnFocusChange.value(newIndex)
                }
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val idx = currentFocusedIndex.value
                val newIndex = (idx - 1).coerceAtLeast(0)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val idx = currentFocusedIndex.value
                val lastIndex = currentSlots.value.lastIndex
                val newIndex = (idx + 1).coerceAtMost(lastIndex)
                if (newIndex != idx) currentOnFocusChange.value(newIndex)
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                if (currentShowDelete.value) {
                    currentOnDeleteConfirm.value()
                    return InputResult.HANDLED
                }
                val slot = currentSlots.value.getOrNull(currentFocusedIndex.value) ?: return InputResult.HANDLED
                if (slot.file != null) {
                    currentOnLoad.value(slot.slotNumber)
                }
                return InputResult.HANDLED
            }

            override fun onContextMenu(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val slot = currentSlots.value.getOrNull(currentFocusedIndex.value) ?: return InputResult.HANDLED
                currentOnSave.value(slot.slotNumber)
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                val slot = currentSlots.value.getOrNull(currentFocusedIndex.value) ?: return InputResult.HANDLED
                if (slot.file != null) {
                    currentOnDeleteRequest.value(slot.slotNumber)
                }
                return InputResult.HANDLED
            }

            override fun onSelect(): InputResult {
                if (currentShowDelete.value) return InputResult.HANDLED
                currentOnViewModeToggle.value()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (currentShowDelete.value) {
                    currentOnDeleteCancel.value()
                    return InputResult.HANDLED
                }
                currentOnDismiss.value()
                return InputResult.HANDLED
            }
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val focusedSlot = slots.getOrNull(focusedIndex)
    val isOccupied = focusedSlot?.file != null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .focusProperties { canFocus = false }
    ) {
        val isSquarish = maxWidth < 500.dp || (maxWidth / maxHeight < 1.4f)
        val effectiveMode = if (isSquarish && viewMode == StateManagerViewMode.SPLIT) {
            StateManagerViewMode.CAROUSEL
        } else {
            viewMode
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Manage States",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (channelName != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[$channelName]",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Content area
            Box(modifier = Modifier.weight(1f)) {
                when (effectiveMode) {
                    StateManagerViewMode.SPLIT -> SplitLayout(
                        slots = slots,
                        focusedIndex = focusedIndex,
                        onFocusChange = onFocusChange,
                        onSave = onSave,
                        onLoad = onLoad
                    )
                    StateManagerViewMode.CAROUSEL -> CarouselLayout(
                        slots = slots,
                        focusedIndex = focusedIndex,
                        onFocusChange = onFocusChange,
                        onSave = onSave,
                        onLoad = onLoad
                    )
                }
            }

            // Footer
            val footerHints = buildFooterHints(isOccupied, effectiveMode)
            FooterBarWithState(
                hints = footerHints,
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> if (isOccupied) focusedSlot?.let { onLoad(it.slotNumber) }
                        InputButton.X -> focusedSlot?.let { onSave(it.slotNumber) }
                        InputButton.Y -> if (isOccupied) focusedSlot?.let { onDeleteRequest(it.slotNumber) }
                        InputButton.SELECT -> onViewModeToggle()
                        InputButton.B -> onDismiss()
                        else -> {}
                    }
                }
            )
        }

        // Delete confirmation overlay
        if (showDeleteConfirmation) {
            NestedModal(
                title = "Delete save state?",
                onDismiss = onDeleteCancel,
                footerHints = listOf(
                    InputButton.A to "Confirm",
                    InputButton.B to "Cancel"
                )
            ) {
                Text(
                    text = "This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    return inputHandler
}

@Composable
private fun SplitLayout(
    slots: List<SaveStateManager.SlotInfo>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Left panel: screenshot preview (40%)
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val focusedSlot = slots.getOrNull(focusedIndex)
            val screenshotFile = focusedSlot?.screenshotFile
            if (screenshotFile != null && screenshotFile.exists()) {
                AsyncImage(
                    model = screenshotFile,
                    contentDescription = slotLabel(focusedSlot.slotNumber),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (focusedSlot?.file != null) "No Screenshot" else "Empty Slot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Slot info at bottom of preview
            if (focusedSlot != null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = slotLabel(focusedSlot.slotNumber),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (focusedSlot.timestamp != null) {
                        Text(
                            text = formatTimestamp(focusedSlot.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatSize(focusedSlot.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Right panel: slot grid (60%)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(slots) { index, slot ->
                SlotCard(
                    slot = slot,
                    isFocused = index == focusedIndex,
                    onClick = { onFocusChange(index) },
                    onDoubleAction = {
                        if (slot.file != null) onLoad(slot.slotNumber)
                        else onSave(slot.slotNumber)
                    }
                )
            }
        }
    }
}

@Composable
private fun CarouselLayout(
    slots: List<SaveStateManager.SlotInfo>,
    focusedIndex: Int,
    onFocusChange: (Int) -> Unit,
    onSave: (Int) -> Unit,
    onLoad: (Int) -> Unit
) {
    val focusedSlot = slots.getOrNull(focusedIndex)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Large screenshot
        val screenshotFile = focusedSlot?.screenshotFile
        if (screenshotFile != null && screenshotFile.exists()) {
            AsyncImage(
                model = screenshotFile,
                contentDescription = focusedSlot.let { slotLabel(it.slotNumber) },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            )
        } else {
            Text(
                text = if (focusedSlot?.file != null) "No Screenshot" else "No State Saved",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Slot indicator at top
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slots.forEachIndexed { index, slot ->
                val dotColor = when {
                    index == focusedIndex && slot.file != null -> MaterialTheme.colorScheme.primary
                    index == focusedIndex -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    slot.file != null -> Color.White.copy(alpha = 0.7f)
                    else -> Color.White.copy(alpha = 0.2f)
                }
                Box(
                    modifier = Modifier
                        .size(if (index == focusedIndex) 10.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(dotColor)
                        .clickableNoFocus { onFocusChange(index) }
                )
            }
        }

        // Info bar at bottom
        if (focusedSlot != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = slotLabel(focusedSlot.slotNumber),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (focusedSlot.timestamp != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = formatTimestamp(focusedSlot.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatSize(focusedSlot.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    Text(
                        text = "Empty",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotCard(
    slot: SaveStateManager.SlotInfo,
    isFocused: Boolean,
    onClick: () -> Unit,
    onDoubleAction: () -> Unit
) {
    val isEmpty = slot.file == null
    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        isEmpty -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (isFocused) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickableNoFocus(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (isEmpty) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else Color.Black
                ),
            contentAlignment = Alignment.Center
        ) {
            val screenshotFile = slot.screenshotFile
            if (screenshotFile != null && screenshotFile.exists()) {
                AsyncImage(
                    model = screenshotFile,
                    contentDescription = slotLabel(slot.slotNumber),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (isEmpty) "--" else "?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = slotLabel(slot.slotNumber),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (isFocused) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!isEmpty && slot.timestamp != null) {
            Text(
                text = formatTimestamp(slot.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSize(slot.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 1
            )
        }
    }
}

private fun slotLabel(slotNumber: Int): String {
    return when (slotNumber) {
        SaveStateManager.AUTO_SLOT -> "Auto"
        else -> "Slot $slotNumber"
    }
}

private fun buildFooterHints(
    isOccupied: Boolean,
    viewMode: StateManagerViewMode
): List<FooterHintItem> {
    val modeLabel = when (viewMode) {
        StateManagerViewMode.SPLIT -> "Carousel"
        StateManagerViewMode.CAROUSEL -> "Grid"
    }
    return buildList {
        if (isOccupied) {
            add(FooterHintItem(InputButton.A, "Load"))
            add(FooterHintItem(InputButton.X, "Save Over"))
            add(FooterHintItem(InputButton.Y, "Delete"))
        } else {
            add(FooterHintItem(InputButton.X, "Save"))
        }
        add(FooterHintItem(InputButton.SELECT, modeLabel))
        add(FooterHintItem(InputButton.B, "Back"))
    }
}
