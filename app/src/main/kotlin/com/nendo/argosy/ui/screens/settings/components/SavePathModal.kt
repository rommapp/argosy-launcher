package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.screens.settings.SavePathModalInfo
import com.nendo.argosy.ui.screens.settings.sections.formatStoragePath
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun SavePathModal(
    info: SavePathModalInfo,
    focusIndex: Int,
    buttonFocusIndex: Int,
    onDismiss: () -> Unit,
    onChangeSavePath: () -> Unit,
    onResetSavePath: () -> Unit,
    onToggleBesideRom: () -> Unit = {}
) {
    Modal(
        title = stringResource(R.string.settings_save_path_modal_title, info.platformName, info.emulatorName),
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        Text(
            text = stringResource(R.string.settings_save_path_modal_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.radiusLg)
        )

        SavePathOptionItem(
            label = stringResource(R.string.settings_save_path_modal_save_path_label),
            path = info.savePath?.let { formatStoragePath(it) },
            isCustom = info.isUserOverride,
            badge = when {
                info.isUserOverride -> stringResource(R.string.settings_save_path_option_custom_badge)
                info.isEvaluatedDefault -> stringResource(R.string.settings_save_path_option_detected_badge)
                else -> null
            },
            isFocused = focusIndex == 0,
            buttonFocusIndex = buttonFocusIndex,
            onClick = onChangeSavePath,
            onReset = if (info.canReset) onResetSavePath else null,
            note = when {
                info.savePath != null && !info.pathPresent ->
                    stringResource(R.string.settings_save_path_modal_note_missing)
                info.shapeWarning != null -> info.shapeWarning
                info.isFallbackDefault && !info.isPreferredReadable ->
                    stringResource(R.string.settings_save_path_modal_note_fallback, info.emulatorName)
                info.isFallbackDefault ->
                    stringResource(R.string.settings_save_path_modal_note_found_elsewhere, info.emulatorName)
                info.chosenPath != null ->
                    stringResource(R.string.settings_save_path_modal_note_moved, info.platformName)
                info.unresolvedShape != null ->
                    stringResource(R.string.settings_save_path_modal_note_shape, info.unresolvedShape)
                else -> null
            },
            noteIsWarning = (info.savePath != null && !info.pathPresent) || info.shapeWarning != null
        )

        if (info.besideRomSupported) {
            val besideRomSubtitle = if (info.emulatorId == "builtin") {
                stringResource(R.string.settings_save_path_modal_beside_rom_subtitle_builtin)
            } else {
                stringResource(R.string.settings_save_path_modal_beside_rom_subtitle_external)
            }
            SwitchPreference(
                title = stringResource(R.string.settings_save_path_modal_beside_rom_title),
                subtitle = besideRomSubtitle,
                isEnabled = info.savesBesideRom,
                isFocused = focusIndex == 1,
                onToggle = { onToggleBesideRom() }
            )
        }

        SavePathOptionItem(
            label = stringResource(R.string.settings_save_path_modal_state_path_label),
            path = null,
            isCustom = false,
            isFocused = false,
            buttonFocusIndex = 0,
            onClick = { },
            enabled = false
        )
    }
}

@Composable
private fun SavePathOptionItem(
    label: String,
    path: String?,
    isCustom: Boolean,
    isFocused: Boolean,
    buttonFocusIndex: Int,
    onClick: () -> Unit,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
    note: String? = null,
    noteIsWarning: Boolean = false,
    badge: String? = if (isCustom) stringResource(R.string.settings_save_path_option_custom_badge) else null
) {
    val focusContent = lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> focusContent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isFocused -> focusContent.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = if (isFocused && enabled) {
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    val changeFocused = isFocused && buttonFocusIndex == 0
    val resetFocused = isFocused && buttonFocusIndex == 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .then(
                if (enabled) {
                    Modifier.clickableNoFocus(onClick = onClick)
                } else Modifier
            )
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) focusContent else MaterialTheme.colorScheme.primary
                    )
                }
                if (!enabled) {
                    Text(
                        text = stringResource(R.string.settings_save_path_option_coming_soon),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor
                    )
                }
            }
            if (enabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onReset != null) {
                        ActionButton(
                            label = stringResource(R.string.settings_save_path_option_reset_button),
                            onClick = onReset,
                            focused = resetFocused
                        )
                    }
                    ActionButton(
                        label = stringResource(R.string.settings_save_path_option_change_button),
                        onClick = onClick,
                        focused = changeFocused,
                        primary = true
                    )
                }
            }
        }
        if (path != null) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCustom && isFocused) focusContent else if (isCustom) MaterialTheme.colorScheme.primary else secondaryColor,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        } else if (enabled) {
            Text(
                text = stringResource(R.string.settings_save_path_option_not_configured),
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = if (noteIsWarning) MaterialTheme.colorScheme.error else secondaryColor,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}
