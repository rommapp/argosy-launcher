package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.libretro.shader.ShaderRegistry
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun ShaderPickerModal(
    shaders: List<ShaderRegistry.ShaderEntry>,
    focusIndex: Int,
    installedIds: Set<String>,
    downloadingShaderId: String?,
    onSelect: (ShaderRegistry.ShaderEntry) -> Unit,
    onDismiss: () -> Unit,
    onItemTap: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    val grouped = remember(shaders) {
        shaders.groupBy { it.category }
    }

    val flatItems = remember(grouped) {
        buildList {
            for (category in ShaderRegistry.Category.entries) {
                val entries = grouped[category] ?: continue
                add(ShaderPickerItem.Header(category))
                entries.forEach { add(ShaderPickerItem.Shader(it)) }
            }
        }
    }

    val focusableItems = remember(flatItems) {
        flatItems.filterIsInstance<ShaderPickerItem.Shader>()
    }

    val shaderToFocusIndex = remember(focusableItems) {
        focusableItems.withIndex().associate { (idx, item) -> item.entry.id to idx }
    }

    val focusToListMap = remember(focusableItems, flatItems) {
        focusableItems.withIndex().associate { (focusIdx, item) ->
            focusIdx to flatItems.indexOf(item)
        }
    }

    fun focusToListIndex(focusIdx: Int): Int {
        return focusToListMap[focusIdx] ?: focusIdx
    }

    FocusedScroll(
        listState = listState,
        focusedIndex = focusToListIndex(focusIndex)
    )

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        val maxModalHeight = maxHeight * 0.9f

        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .heightIn(max = maxModalHeight)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "ADD SHADER",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                items(flatItems, key = { it.key }) { item ->
                    when (item) {
                        is ShaderPickerItem.Header -> {
                            Text(
                                text = item.category.name.replace('_', ' '),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    top = Dimens.spacingSm,
                                    bottom = Dimens.spacingXs
                                )
                            )
                        }
                        is ShaderPickerItem.Shader -> {
                            val itemFocusIndex = shaderToFocusIndex[item.entry.id] ?: -1
                            val isFocused = focusIndex == itemFocusIndex
                            val isInstalled = item.entry.source == ShaderRegistry.Source.CUSTOM
                                || item.entry.id in installedIds
                            val isDownloading = item.entry.id == downloadingShaderId
                            ShaderPickerEntry(
                                entry = item.entry,
                                isFocused = isFocused,
                                isInstalled = isInstalled,
                                isDownloading = isDownloading,
                                onClick = { onItemTap(itemFocusIndex) }
                            )
                        }
                    }
                }
            }

            FooterBar(
                hints = listOf(
                    InputButton.DPAD_HORIZONTAL to "Section",
                    InputButton.A to "Add",
                    InputButton.B to "Cancel"
                )
            )
        }
    }
}

@Composable
private fun ShaderPickerEntry(
    entry: ShaderRegistry.ShaderEntry,
    isFocused: Boolean,
    isInstalled: Boolean,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val nameAlpha = if (isInstalled || entry.source == ShaderRegistry.Source.CUSTOM) 1f else 0.6f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(bgColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = nameAlpha)
                )
                entry.description?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.7f * nameAlpha)
                    )
                }
            }
            when {
                isDownloading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
                entry.source == ShaderRegistry.Source.CUSTOM -> {
                    Text(
                        text = "Custom",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
                !isInstalled -> {
                    Text(
                        text = "Download",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

private sealed class ShaderPickerItem(val key: String) {
    class Header(val category: ShaderRegistry.Category) : ShaderPickerItem("header_${category.name}")
    class Shader(val entry: ShaderRegistry.ShaderEntry) : ShaderPickerItem("shader_${entry.id}")
}

fun shaderPickerMaxFocusIndex(shaders: List<ShaderRegistry.ShaderEntry>): Int =
    (shaders.size - 1).coerceAtLeast(0)
