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
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
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
    onMemcardClick: () -> Unit,
    onCycleDisplayTarget: (Int) -> Unit,
    onCycleExtension: (Int) -> Unit,
    onPlatformSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val rows = state.rows
    val focusedRow = state.focusedRow

    fun isFocused(row: PerGameSettingsRow) = focusedRow == row

    val adjustHint = stringResource(R.string.gamedetail_per_game_footer_adjust)
    val changeResetHint = stringResource(R.string.gamedetail_per_game_footer_change_reset)
    val selectHint = stringResource(R.string.gamedetail_per_game_footer_select)
    val changeSavePathHint = stringResource(R.string.gamedetail_per_game_footer_change_save_path)
    val openSaveLocationHint =
        stringResource(R.string.gamedetail_per_game_footer_open_save_location)
    val changeMemcardHint = stringResource(R.string.gamedetail_per_game_footer_change_memcard)
    val cycleHint = stringResource(R.string.gamedetail_per_game_footer_cycle)
    val openHint = stringResource(R.string.gamedetail_per_game_footer_open)
    val backHint = stringResource(R.string.gamedetail_per_game_footer_back)

    Modal(
        title = stringResource(R.string.gamedetail_per_game_title),
        subtitle = gameTitle,
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss,
        footerHints = buildList {
            when (focusedRow) {
                PerGameSettingsRow.DISPLAY_TARGET, PerGameSettingsRow.EXTENSION ->
                    add(InputButton.DPAD_HORIZONTAL to adjustHint)
                PerGameSettingsRow.SAVE_PATH ->
                    if (state.isSavePathOverride) {
                        add(InputButton.DPAD_HORIZONTAL to changeResetHint)
                    }
                else -> {}
            }
            add(
                InputButton.A to when (focusedRow) {
                    PerGameSettingsRow.EMULATOR, PerGameSettingsRow.CORE -> selectHint
                    PerGameSettingsRow.SAVE_PATH -> changeSavePathHint
                    PerGameSettingsRow.SAVE_BASE_PATH -> openSaveLocationHint
                    PerGameSettingsRow.MEMCARD -> changeMemcardHint
                    PerGameSettingsRow.DISPLAY_TARGET, PerGameSettingsRow.EXTENSION -> cycleHint
                    PerGameSettingsRow.PLATFORM_SETTINGS, null -> openHint
                }
            )
            add(InputButton.B to backHint)
        }
    ) {
        rows.forEach { row ->
            when (row) {
                PerGameSettingsRow.EMULATOR -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_emulator_label),
                    value = state.emulatorName
                        ?: stringResource(R.string.gamedetail_per_game_emulator_none),
                    isOverride = state.isEmulatorOverride,
                    isFocused = isFocused(row),
                    onClick = onEmulatorClick
                )

                PerGameSettingsRow.CORE -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_core_label),
                    value = state.coreName
                        ?: stringResource(R.string.gamedetail_per_game_core_default),
                    isOverride = state.isCoreOverride,
                    isFocused = isFocused(row),
                    onClick = onCoreClick
                )

                PerGameSettingsRow.SAVE_PATH -> PathConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_save_path_label),
                    path = state.savePath?.let { formatStoragePath(it) },
                    isCustom = state.isSavePathOverride,
                    isFocused = isFocused(row),
                    buttonFocusIndex = state.pathButtonIndex,
                    onChange = onChangeSavePath,
                    onReset = if (state.isSavePathOverride) onResetSavePath else null
                )

                PerGameSettingsRow.SAVE_BASE_PATH -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_save_location_label),
                    value = state.saveBasePath?.let { formatStoragePath(it) }
                        ?: stringResource(R.string.gamedetail_per_game_save_location_unset),
                    isOverride = !state.saveBasePathIsInherited && state.saveBasePath != null,
                    isFocused = isFocused(row),
                    onClick = onPlatformSettings
                )

                PerGameSettingsRow.MEMCARD -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_memcard_label),
                    value = state.selectedMemcardPath
                        ?.let { path ->
                            state.memcardCards.find { it.path == path }?.name
                                ?: java.io.File(path).name
                        }
                        ?: stringResource(
                            R.string.gamedetail_per_game_memcard_default,
                            state.inheritedMemcardName
                                ?: stringResource(
                                    R.string.gamedetail_per_game_memcard_default_auto
                                )
                        ),
                    isOverride = state.selectedMemcardPath != null,
                    isFocused = isFocused(row),
                    onClick = onMemcardClick
                )

                PerGameSettingsRow.DISPLAY_TARGET -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_display_target_label),
                    value = state.displayTarget?.displayName
                        ?: stringResource(
                            R.string.gamedetail_per_game_display_target_inherit,
                            state.inheritedDisplayTarget.displayName
                        ),
                    isOverride = state.displayTarget != null,
                    isFocused = isFocused(row),
                    onClick = { onCycleDisplayTarget(1) }
                )

                PerGameSettingsRow.EXTENSION -> ValueConfigItem(
                    label = stringResource(R.string.gamedetail_per_game_extension_label),
                    value = state.preferredExtension?.let { ext ->
                        state.extensionOptions.find { it.extension == ext }?.label ?: ext
                    } ?: stringResource(
                        R.string.gamedetail_per_game_extension_inherit,
                        state.inheritedExtension?.let { inherited ->
                            state.extensionOptions.find { it.extension == inherited }?.label
                                ?: inherited
                        } ?: stringResource(
                            R.string.gamedetail_per_game_extension_inherit_auto
                        )
                    ),
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
                        label = stringResource(R.string.gamedetail_per_game_platform_settings),
                        isFocused = isFocused(row),
                        onClick = onPlatformSettings
                    )
                }
            }
        }
    }
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
                    text = stringResource(R.string.gamedetail_per_game_inherited_tag),
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
