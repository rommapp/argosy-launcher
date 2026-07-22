package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsRow
import com.nendo.argosy.ui.screens.gamedetail.delegates.PerGameSettingsState
import com.nendo.argosy.ui.screens.settings.components.PathConfigItem
import com.nendo.argosy.ui.screens.settings.sections.formatStoragePath
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun PerGameSettingsModal(
    gameTitle: String,
    state: PerGameSettingsState,
    onEmulatorClick: () -> Unit,
    onCoreClick: () -> Unit,
    onChangeSavePath: () -> Unit,
    onResetSavePath: () -> Unit,
    onCycleDisplayTarget: (Int) -> Unit,
    onCycleExtension: (Int) -> Unit,
    onPlatformSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val rows = state.rows
    val focusedRow = state.focusedRow

    fun isFocused(row: PerGameSettingsRow) = focusedRow == row

    Modal(
        title = "Per-Game Settings",
        subtitle = gameTitle,
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss,
        footerHints = buildList {
            when (focusedRow) {
                PerGameSettingsRow.DISPLAY_TARGET, PerGameSettingsRow.EXTENSION ->
                    add(InputButton.DPAD_HORIZONTAL to "Adjust")
                PerGameSettingsRow.SAVE_PATH ->
                    if (state.isSavePathOverride) add(InputButton.DPAD_HORIZONTAL to "Change/Reset")
                else -> {}
            }
            add(
                InputButton.A to when (focusedRow) {
                    PerGameSettingsRow.EMULATOR, PerGameSettingsRow.CORE -> "Select"
                    PerGameSettingsRow.SAVE_PATH -> "Change"
                    PerGameSettingsRow.DISPLAY_TARGET, PerGameSettingsRow.EXTENSION -> "Cycle"
                    PerGameSettingsRow.PLATFORM_SETTINGS, null -> "Open"
                }
            )
            add(InputButton.B to "Back")
        }
    ) {
        rows.forEach { row ->
            when (row) {
                PerGameSettingsRow.EMULATOR -> ValueConfigItem(
                    label = "Emulator",
                    value = state.emulatorName ?: "None detected",
                    isOverride = state.isEmulatorOverride,
                    isFocused = isFocused(row),
                    onClick = onEmulatorClick
                )

                PerGameSettingsRow.CORE -> ValueConfigItem(
                    label = "Core",
                    value = state.coreName ?: "Default",
                    isOverride = state.isCoreOverride,
                    isFocused = isFocused(row),
                    onClick = onCoreClick
                )

                PerGameSettingsRow.SAVE_PATH -> PathConfigItem(
                    label = "Save Path",
                    path = state.savePath?.let { formatStoragePath(it) },
                    isCustom = state.isSavePathOverride,
                    isFocused = isFocused(row),
                    buttonFocusIndex = state.pathButtonIndex,
                    onChange = onChangeSavePath,
                    onReset = if (state.isSavePathOverride) onResetSavePath else null
                )

                PerGameSettingsRow.DISPLAY_TARGET -> ValueConfigItem(
                    label = "Display Target",
                    value = state.displayTarget?.displayName
                        ?: "Inherit (${state.inheritedDisplayTarget.displayName})",
                    isOverride = state.displayTarget != null,
                    isFocused = isFocused(row),
                    onClick = { onCycleDisplayTarget(1) }
                )

                PerGameSettingsRow.EXTENSION -> ValueConfigItem(
                    label = "File Extension",
                    value = state.preferredExtension?.let { ext ->
                        state.extensionOptions.find { it.extension == ext }?.label ?: ext
                    } ?: "Inherit (${inheritedExtensionLabel(state)})",
                    isOverride = state.preferredExtension != null,
                    isFocused = isFocused(row),
                    onClick = { onCycleExtension(1) }
                )

                PerGameSettingsRow.PLATFORM_SETTINGS -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = Dimens.spacingSm),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    OptionItem(
                        label = "Platform Settings",
                        isFocused = isFocused(row),
                        onClick = onPlatformSettings
                    )
                }
            }
        }
    }
}

private fun inheritedExtensionLabel(state: PerGameSettingsState): String {
    val inherited = state.inheritedExtension ?: return "Auto"
    return state.extensionOptions.find { it.extension == inherited }?.label ?: inherited
}

@Composable
private fun ValueConfigItem(
    label: String,
    value: String,
    isOverride: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }
    val valueColor = when {
        isOverride && isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        isOverride -> MaterialTheme.colorScheme.primary
        else -> secondaryColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (!isOverride) {
                Text(
                    text = "Inherited",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryColor
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            modifier = Modifier.padding(top = Dimens.spacingXs)
        )
    }
}
