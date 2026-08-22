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
        title = "${info.platformName} - ${info.emulatorName}",
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        Text(
            text = "Custom paths help fix save detection when Argosy can't find saves to sync. " +
                "This doesn't change where your emulator stores saves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.radiusLg)
        )

        SavePathOptionItem(
            label = "Save Path",
            path = info.savePath?.let { formatStoragePath(it) },
            isCustom = info.isUserOverride,
            isFocused = focusIndex == 0,
            buttonFocusIndex = buttonFocusIndex,
            onClick = onChangeSavePath,
            onReset = if (info.isUserOverride) onResetSavePath else null,
            note = when {
                info.savePath != null && !info.pathPresent ->
                    "This folder isn't on the device right now. Saves won't be found until it is."
                info.shapeWarning != null -> info.shapeWarning
                info.chosenPath != null ->
                    "Moved to where ${info.platformName} saves actually live, below the folder you picked."
                info.unresolvedShape != null ->
                    "Each game's save sits under ${info.unresolvedShape} below this folder."
                else -> null
            },
            noteIsWarning = (info.savePath != null && !info.pathPresent) || info.shapeWarning != null
        )

        if (info.besideRomSupported) {
            val besideRomSubtitle = if (info.emulatorId == "builtin") {
                "Store saves in each game's folder."
            } else {
                "Look for saves in each game's folder. Set your emulator to save next to the ROM as well."
            }
            SwitchPreference(
                title = "Save beside ROM",
                subtitle = besideRomSubtitle,
                isEnabled = info.savesBesideRom,
                isFocused = focusIndex == 1,
                onToggle = { onToggleBesideRom() }
            )
        }

        SavePathOptionItem(
            label = "State Path",
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
    noteIsWarning: Boolean = false
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
                if (isCustom) {
                    Text(
                        text = "(custom)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) focusContent else MaterialTheme.colorScheme.primary
                    )
                }
                if (!enabled) {
                    Text(
                        text = "(coming soon)",
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
                            label = "Reset",
                            onClick = onReset,
                            focused = resetFocused
                        )
                    }
                    ActionButton(
                        label = "Change",
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
                text = "Not configured",
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
