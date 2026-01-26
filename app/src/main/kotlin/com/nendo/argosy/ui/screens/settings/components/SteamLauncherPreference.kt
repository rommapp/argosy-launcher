package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SteamLauncherPreference(
    displayName: String,
    subtitle: String? = null,
    supportsScanning: Boolean,
    isSyncing: Boolean,
    isFocused: Boolean,
    isEnabled: Boolean,
    actionIndex: Int,
    onScan: () -> Unit,
    onAdd: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusLg))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSyncing) Icons.Default.Sync else Icons.Default.Cloud,
            contentDescription = null,
            tint = if (isEnabled) contentColor else contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
            val subtitleText = when {
                isSyncing -> "Scanning..."
                subtitle != null -> subtitle
                else -> null
            }
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (supportsScanning) {
                val scanSelected = isFocused && actionIndex == 0
                val scanBgColor = when {
                    scanSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val scanTextColor = when {
                    scanSelected -> MaterialTheme.colorScheme.onPrimary
                    !isEnabled -> contentColor.copy(alpha = 0.5f)
                    else -> contentColor
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(scanBgColor)
                        .clickableNoFocus(enabled = isEnabled, onClick = onScan)
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                ) {
                    Text(
                        text = "Scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = scanTextColor
                    )
                }
            }

            val addSelected = isFocused && if (supportsScanning) actionIndex == 1 else true
            val addBgColor = when {
                addSelected -> MaterialTheme.colorScheme.primary
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val addTextColor = when {
                addSelected -> MaterialTheme.colorScheme.onPrimary
                !isEnabled -> contentColor.copy(alpha = 0.5f)
                else -> contentColor
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(addBgColor)
                    .clickableNoFocus(enabled = isEnabled, onClick = onAdd)
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelMedium,
                    color = addTextColor
                )
            }
        }
    }
}
