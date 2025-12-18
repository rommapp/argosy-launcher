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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.nendo.argosy.ui.screens.settings.components.SavePathModal
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun EmulatorsSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    onLaunchSavePathPicker: () -> Unit
) {
    val listState = rememberLazyListState()
    val focusOffset = if (uiState.emulators.canAutoAssign) 1 else 0

    val modalBlur by animateDpAsState(
        targetValue = if (uiState.emulators.showEmulatorPicker || uiState.emulators.showSavePathModal) Motion.blurRadiusModal else 0.dp,
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
                        onClick = { viewModel.handlePlatformItemTap(-1) }
                    )
                }
            }
            itemsIndexed(uiState.emulators.platforms) { index, config ->
                val itemFocused = uiState.focusedIndex == index + focusOffset
                PlatformEmulatorItem(
                    config = config,
                    isFocused = itemFocused,
                    subFocusIndex = if (itemFocused) uiState.emulators.platformSubFocusIndex else 0,
                    onEmulatorClick = { viewModel.handlePlatformItemTap(index) },
                    onCycleCore = { direction -> viewModel.cycleCoreForPlatform(config, direction) },
                    onSavePathClick = { viewModel.showSavePathModal(config) }
                )
            }
        }

        if (uiState.emulators.showEmulatorPicker && uiState.emulators.emulatorPickerInfo != null) {
            EmulatorPickerPopup(
                info = uiState.emulators.emulatorPickerInfo,
                focusIndex = uiState.emulators.emulatorPickerFocusIndex,
                selectedIndex = uiState.emulators.emulatorPickerSelectedIndex,
                onItemTap = { index -> viewModel.handleEmulatorPickerItemTap(index) },
                onConfirm = { viewModel.confirmEmulatorPickerSelection() },
                onDismiss = { viewModel.dismissEmulatorPicker() }
            )
        }

        if (uiState.emulators.showSavePathModal && uiState.emulators.savePathModalInfo != null) {
            SavePathModal(
                info = uiState.emulators.savePathModalInfo,
                focusIndex = uiState.emulators.savePathModalFocusIndex,
                buttonFocusIndex = uiState.emulators.savePathModalButtonIndex,
                onDismiss = { viewModel.dismissSavePathModal() },
                onChangeSavePath = onLaunchSavePathPicker,
                onResetSavePath = {
                    viewModel.resetEmulatorSavePath(uiState.emulators.savePathModalInfo.emulatorId)
                }
            )
        }
    }
}

@Composable
private fun PlatformEmulatorItem(
    config: PlatformEmulatorConfig,
    isFocused: Boolean,
    subFocusIndex: Int,
    onEmulatorClick: () -> Unit,
    onCycleCore: (Int) -> Unit,
    onSavePathClick: () -> Unit
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

    val emulatorSubFocused = isFocused && subFocusIndex == 0
    val savesSubFocused = isFocused && subFocusIndex == 1

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
                config.selectedEmulator != null -> config.selectedEmulator
                config.effectiveEmulatorName != null -> config.effectiveEmulatorName
                else -> "Auto"
            }
            if (config.hasInstalledEmulators) {
                when {
                    emulatorSubFocused -> {
                        Button(
                            onClick = onEmulatorClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(text = emulatorDisplay, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    isFocused -> {
                        Button(
                            onClick = onEmulatorClick,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(text = emulatorDisplay, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    else -> {
                        Text(
                            text = emulatorDisplay,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    text = emulatorDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }
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

        if (config.showSavePath && config.hasInstalledEmulators) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .clickable(onClick = onSavePathClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Text(
                            text = "Saves",
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryColor
                        )
                        if (config.isUserSavePathOverride) {
                            Text(
                                text = "(custom)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = config.effectiveSavePath?.let { formatStoragePath(it) } ?: "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            config.effectiveSavePath == null -> secondaryColor.copy(alpha = 0.6f)
                            isFocused -> secondaryColor
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                if (isFocused) {
                    Button(
                        onClick = onSavePathClick,
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (savesSubFocused) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                            },
                            contentColor = if (savesSubFocused) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                    ) {
                        Text(text = "Change", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
