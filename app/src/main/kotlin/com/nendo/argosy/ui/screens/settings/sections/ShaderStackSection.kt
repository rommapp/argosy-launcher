package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.libretro.shader.ShaderChainManager
import com.nendo.argosy.ui.screens.settings.ShaderParamDef
import com.nendo.argosy.ui.screens.settings.ShaderStackEntry
import com.nendo.argosy.ui.screens.settings.ShaderStackState
import com.nendo.argosy.ui.screens.settings.components.ShaderPickerModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun ShaderStackSection(
    manager: ShaderChainManager
) {
    val listState = rememberLazyListState()
    val shaderStack = manager.shaderStack
    val entries = shaderStack.entries
    val params = shaderStack.selectedShaderParams
    val previewBitmap = manager.previewBitmap

    FocusedScroll(
        listState = listState,
        focusedIndex = shaderStack.paramFocusIndex
    )

    val pickerBlur = if (shaderStack.showShaderPicker) Motion.blurRadiusModal else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .blur(pickerBlur)
                .padding(Dimens.spacingMd),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
            ) {
                if (entries.isEmpty()) {
                    ShaderStackEmptyState()
                } else {
                    ShaderTabBar(
                        entries = entries,
                        selectedIndex = shaderStack.selectedIndex,
                        onTabTap = { manager.selectShaderInStack(it) }
                    )

                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))

                    if (params.isEmpty()) {
                        ShaderNoParamsState(entries[shaderStack.selectedIndex].displayName)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            itemsIndexed(
                                params,
                                key = { _, param -> param.name }
                            ) { index, paramDef ->
                                val entry = shaderStack.selectedEntry
                                val currentValue = entry?.params?.get(paramDef.name)
                                    ?.toFloatOrNull() ?: paramDef.initial
                                ShaderParamItem(
                                    paramDef = paramDef,
                                    currentValue = currentValue,
                                    isFocused = shaderStack.paramFocusIndex == index,
                                    onClick = { manager.moveShaderParamFocus(index - shaderStack.paramFocusIndex) }
                                )
                            }
                        }
                    }
                }
            }

            ShaderPreviewPanel(
                previewBitmap = previewBitmap,
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = shaderStack.showShaderPicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val registry = manager.getShaderRegistry()
            val allShaders = remember { registry.getShadersForPicker() }
            val installedIds = remember(shaderStack.downloadingShaderId) {
                registry.getInstalledIds()
            }
            ShaderPickerModal(
                shaders = allShaders,
                focusIndex = shaderStack.shaderPickerFocusIndex,
                installedIds = installedIds,
                downloadingShaderId = shaderStack.downloadingShaderId,
                onSelect = { entry ->
                    manager.addShaderToStack(entry.id, entry.displayName)
                },
                onDismiss = { manager.dismissShaderPicker() },
                onItemTap = { index -> manager.setShaderPickerFocusIndex(index) }
            )
        }
    }
}

@Composable
private fun ShaderTabBar(
    entries: List<ShaderStackEntry>,
    selectedIndex: Int,
    onTabTap: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        entries.forEachIndexed { index, entry ->
            val isSelected = index == selectedIndex
            val bgColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
            val textColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(bgColor)
                    .clickableNoFocus { onTabTap(index) }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
            ) {
                Text(
                    text = "${index + 1}. ${entry.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun ShaderParamItem(
    paramDef: ShaderParamDef,
    currentValue: Float,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val valueColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    val displayValue = formatParamValue(currentValue, paramDef.step)
    val isSectionLabel = paramDef.min == paramDef.max
    val displayName = formatParamDescription(paramDef.description, isSectionLabel)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(bgColor)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = if (isSectionLabel) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.titleMedium,
            color = if (isSectionLabel) MaterialTheme.colorScheme.primary else textColor,
            modifier = Modifier.weight(1f)
        )
        if (!isSectionLabel) {
            Text(
                text = "< $displayValue >",
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
        }
    }
}

private val BRACKET_LABEL = Regex("""^\s*\[?\s*(.+?)\s*]?\s*$""")

private fun formatParamDescription(description: String, isSectionLabel: Boolean): String {
    if (!isSectionLabel) return description
    val inner = BRACKET_LABEL.find(description.trim())?.groupValues?.get(1)?.trim() ?: description.trim()
    return inner.ifEmpty { description }
}

private fun formatParamValue(value: Float, step: Float): String {
    return if (step >= 1f && value == value.toLong().toFloat()) {
        value.toLong().toString()
    } else {
        val decimals = step.toString().substringAfter('.', "").trimEnd('0').length
            .coerceIn(1, 3)
        String.format("%.${decimals}f", value)
    }
}

@Composable
private fun ShaderPreviewPanel(
    previewBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        if (previewBitmap != null) {
            Image(
                bitmap = previewBitmap,
                contentDescription = "Shader preview",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.radiusLg)),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "No preview",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ShaderStackEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingLg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No shaders in chain",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShaderNoParamsState(shaderName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Dimens.spacingLg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No adjustable parameters",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun shaderStackMaxFocusIndex(state: ShaderStackState): Int =
    (state.selectedShaderParams.size - 1).coerceAtLeast(0)
