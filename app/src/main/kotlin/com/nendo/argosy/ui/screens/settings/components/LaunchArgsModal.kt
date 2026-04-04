package com.nendo.argosy.ui.screens.settings.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.emulator.LaunchMethod
import com.nendo.argosy.data.emulator.RomPathFormat
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.settings.LaunchArgsModalState
import com.nendo.argosy.ui.screens.settings.LaunchArgsRow
import com.nendo.argosy.ui.screens.settings.launchArgsModalRows
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Launch Args modal -- per-(platform, emulator) override of launch method, ROM path format,
 * intent flags, and MIME type. Rows are dynamic based on which knobs apply to the current
 * emulator's LaunchConfig; visibility is computed in [launchArgsModalRows].
 *
 * Input behavior:
 * - UP/DOWN moves focus between rows
 * - A toggles / cycles the focused row
 * - Y resets the focused field to default (clear override)
 * - X resets all fields (delete override row entirely)
 * - B dismisses
 *
 * Resume-mode flags are never overridden; documented in subtext.
 */
@Composable
fun LaunchArgsModal(
    state: LaunchArgsModalState,
    onCycleMethod: () -> Unit,
    onCycleRomPathFormat: () -> Unit,
    onToggleFlag: (Int) -> Unit,
    onCycleMimeType: () -> Unit,
    onDismiss: () -> Unit
) {
    val rows = launchArgsModalRows(state)

    Modal(
        title = "Launch Args  -  ${state.platformName} / ${state.emulatorName}",
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        Text(
            text = "Override how Argosy launches this emulator on this platform. Resume-mode " +
                "flags are fixed regardless of overrides.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )

        rows.forEachIndexed { index, row ->
            val focused = index == state.focusIndex
            when (row) {
                is LaunchArgsRow.LaunchMethod -> LaunchArgsOptionRow(
                    label = "Launch method",
                    value = methodLabel(state.override?.launchMethod, state.defaultLaunchMethod),
                    subtitle = "Shell launches via am start and bypass caller-side URI grants. " +
                        "More reliable on scoped storage.",
                    isOverridden = state.override?.launchMethod != null,
                    isFocused = focused,
                    onClick = onCycleMethod
                )
                is LaunchArgsRow.RomPathFormat -> LaunchArgsOptionRow(
                    label = "ROM path format",
                    value = romPathFormatLabel(state.override?.romPathFormat),
                    subtitle = "How the ROM location is expressed to the emulator. Try a different " +
                        "format if the emulator can't find the ROM.",
                    isOverridden = state.override?.romPathFormat != null,
                    isFocused = focused,
                    onClick = onCycleRomPathFormat
                )
                is LaunchArgsRow.Flag -> {
                    val mask = state.override?.intentFlagsMask ?: state.defaultFlagsMask
                    val isOn = (mask and row.bit) != 0
                    val isOverridden = state.override?.intentFlagsMask != null
                    LaunchArgsOptionRow(
                        label = row.label,
                        value = if (isOn) "On" else "Off",
                        subtitle = flagSubtext(row.bit),
                        isOverridden = isOverridden,
                        isFocused = focused,
                        onClick = { onToggleFlag(row.bit) }
                    )
                }
                is LaunchArgsRow.MimeType -> LaunchArgsOptionRow(
                    label = "MIME type",
                    value = state.override?.mimeType ?: "Default (${state.defaultMimeType ?: "*/*"})",
                    subtitle = "MIME type sent with the ROM URI. Most emulators ignore this and " +
                        "filter by extension.",
                    isOverridden = state.override?.mimeType != null,
                    isFocused = focused,
                    onClick = onCycleMimeType
                )
            }
            if (index < rows.size - 1) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
            }
        }
    }
}

@Composable
private fun LaunchArgsOptionRow(
    label: String,
    value: String,
    subtitle: String,
    isOverridden: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val background = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val labelColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val valueColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitleColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
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
                    color = labelColor
                )
                if (isOverridden) {
                    Text(
                        text = "(custom)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

private fun methodLabel(override: String?, defaultName: String): String = when (override) {
    null -> "Default (${defaultName.lowercase().replaceFirstChar { it.uppercase() }})"
    LaunchMethod.INTENT.name -> "Intent"
    LaunchMethod.SHELL.name -> "Shell (am start)"
    else -> "Default"
}

private fun romPathFormatLabel(override: String?): String = when (override) {
    null -> "Default"
    RomPathFormat.ABSOLUTE_PATH.name -> "Absolute path"
    RomPathFormat.FILE_PROVIDER.name -> "FileProvider URI"
    RomPathFormat.DOCUMENT_URI.name -> "Document URI (SAF)"
    RomPathFormat.AUTO.name -> "Default"
    else -> "Default"
}

private fun flagSubtext(bit: Int): String = when (bit) {
    Intent.FLAG_ACTIVITY_NEW_TASK -> "Launches in a separate Android task. Required by almost every emulator."
    Intent.FLAG_ACTIVITY_CLEAR_TASK -> "Clears any existing instance of the emulator before launching."
    Intent.FLAG_ACTIVITY_NO_HISTORY -> "Hides the emulator from the recent apps list."
    Intent.FLAG_ACTIVITY_SINGLE_TOP -> "Reuses an existing emulator instance if it is at the top of its task."
    Intent.FLAG_GRANT_READ_URI_PERMISSION -> "Delegates read access to the ROM URI. Required when passing content:// URIs."
    Intent.FLAG_ACTIVITY_CLEAR_TOP -> "Clears activities above the target when reusing a task."
    else -> ""
}

