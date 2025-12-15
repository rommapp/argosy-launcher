package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.screens.settings.PlatformEmulatorConfig
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun EmulatorsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val focusOffset = if (uiState.emulators.canAutoAssign) 1 else 0

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.emulators.showEmulatorPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "emulatorPickerBlur"
    )

    LaunchedEffect(uiState.focusedIndex) {
        val totalItems = uiState.emulators.platforms.size + focusOffset
        if (totalItems > 0 && uiState.focusedIndex in 0 until totalItems) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd).blur(modalBlur),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            if (uiState.emulators.canAutoAssign) {
                item {
                    ActionPreference(
                        title = "Auto-assign Emulators",
                        subtitle = "Set recommended emulators for all platforms",
                        isFocused = uiState.focusedIndex == 0,
                        onClick = { viewModel.autoAssignAllEmulators() }
                    )
                }
            }
            itemsIndexed(uiState.emulators.platforms) { index, config ->
                PlatformEmulatorItem(
                    config = config,
                    isFocused = uiState.focusedIndex == index + focusOffset,
                    onEmulatorClick = { viewModel.showEmulatorPicker(config) },
                    onCycleCore = { direction -> viewModel.cycleCoreForPlatform(config, direction) }
                )
            }
        }

        if (uiState.emulators.showEmulatorPicker && uiState.emulators.emulatorPickerInfo != null) {
            EmulatorPickerPopup(
                info = uiState.emulators.emulatorPickerInfo,
                focusIndex = uiState.emulators.emulatorPickerFocusIndex,
                onConfirm = { viewModel.confirmEmulatorPickerSelection() },
                onDismiss = { viewModel.dismissEmulatorPicker() }
            )
        }
    }
}

@Composable
private fun PlatformEmulatorItem(
    config: PlatformEmulatorConfig,
    isFocused: Boolean,
    onEmulatorClick: () -> Unit,
    onCycleCore: (Int) -> Unit
) {
    val disabledAlpha = 0.45f
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        !config.hasInstalledEmulators -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        !config.hasInstalledEmulators -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusLg))
            .clickable(onClick = onEmulatorClick)
            .padding(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = config.platform.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = if (config.hasInstalledEmulators) "${config.availableEmulators.size} emulators available" else "No emulators installed",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
            val emulatorDisplay = when {
                !config.hasInstalledEmulators -> "Download"
                config.selectedEmulator != null -> "< ${config.selectedEmulator} >"
                config.effectiveEmulatorName != null -> "< ${config.effectiveEmulatorName} >"
                else -> "< Auto >"
            }
            Text(
                text = emulatorDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    !config.hasInstalledEmulators -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        }

        if (config.showCoreSelection) {
            val selectedCoreId = config.selectedCore ?: config.availableCores.firstOrNull()?.id
            val selectedCoreName = config.availableCores.find { it.id == selectedCoreId }?.displayName
                ?: config.availableCores.firstOrNull()?.displayName ?: "Default"

            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Text(
                    text = "Core",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
                if (isFocused) {
                    config.availableCores.forEach { core ->
                        val isSelected = core.id == selectedCoreId
                        if (isSelected) {
                            OutlinedButton(
                                onClick = { },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = 0.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                Text(
                                    text = core.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    val currentIdx = config.availableCores.indexOfFirst { it.id == selectedCoreId }
                                    val targetIdx = config.availableCores.indexOf(core)
                                    onCycleCore(targetIdx - currentIdx)
                                },
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = Dimens.spacingSm, vertical = 0.dp)
                            ) {
                                Text(
                                    text = core.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = selectedCoreName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
