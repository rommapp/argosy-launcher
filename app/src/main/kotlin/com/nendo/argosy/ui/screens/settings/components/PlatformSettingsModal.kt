package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.settings.PlatformStorageConfig
import com.nendo.argosy.ui.screens.settings.sections.formatStoragePath
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun PlatformSettingsModal(
    config: PlatformStorageConfig,
    focusIndex: Int,
    buttonFocusIndex: Int,
    onDismiss: () -> Unit,
    onToggleSync: () -> Unit,
    onChangeRomPath: () -> Unit,
    onResetRomPath: () -> Unit,
    onChangeSavePath: () -> Unit,
    onResetSavePath: () -> Unit,
    onChangeStatePath: () -> Unit,
    onResetStatePath: () -> Unit,
    onResync: () -> Unit,
    onPurge: () -> Unit
) {
    Modal(
        title = config.platformName,
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        ToggleOptionItem(
            label = "Sync with Server",
            checked = config.syncEnabled,
            isFocused = focusIndex == 0,
            onToggle = onToggleSync
        )

        PathConfigItem(
            label = "ROM Path",
            path = config.customRomPath?.let { formatStoragePath(it) },
            isCustom = config.customRomPath != null,
            isFocused = focusIndex == 1,
            buttonFocusIndex = buttonFocusIndex,
            onChange = onChangeRomPath,
            onReset = if (config.customRomPath != null) onResetRomPath else null
        )

        PathConfigItem(
            label = "Save Path",
            path = config.effectiveSavePath?.let { formatStoragePath(it) },
            isCustom = config.isUserSavePathOverride,
            isFocused = focusIndex == 2,
            buttonFocusIndex = buttonFocusIndex,
            onChange = onChangeSavePath,
            onReset = if (config.isUserSavePathOverride) onResetSavePath else null
        )

        var nextIndex = 3
        val statePathIndex = if (config.supportsStatePath) nextIndex++ else -1
        val resyncIndex = nextIndex++
        val purgeIndex = nextIndex

        if (config.supportsStatePath) {
            PathConfigItem(
                label = "State Path",
                path = config.effectiveStatePath?.let { formatStoragePath(it) },
                isCustom = config.isUserStatePathOverride,
                isFocused = focusIndex == statePathIndex,
                buttonFocusIndex = buttonFocusIndex,
                onChange = onChangeStatePath,
                onReset = if (config.isUserStatePathOverride) onResetStatePath else null
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = Dimens.spacingSm),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        OptionItem(
            label = "Resync Platform",
            isFocused = focusIndex == resyncIndex,
            onClick = onResync
        )

        OptionItem(
            label = "Purge All Data",
            isFocused = focusIndex == purgeIndex,
            isDangerous = true,
            onClick = onPurge
        )
    }
}

@Composable
private fun ToggleOptionItem(
    label: String,
    checked: Boolean,
    isFocused: Boolean,
    onToggle: () -> Unit
) {
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onToggle)
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.focusProperties { canFocus = false },
            interactionSource = remember { MutableInteractionSource() }
        )
    }
}

@Composable
private fun PathConfigItem(
    label: String,
    path: String?,
    isCustom: Boolean,
    isFocused: Boolean,
    buttonFocusIndex: Int,
    onChange: () -> Unit,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val backgroundColor = if (isFocused && enabled) {
        MaterialTheme.colorScheme.primaryContainer
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
                    Modifier.clickableNoFocus(onClick = onChange)
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
                if (!enabled) {
                    Text(
                        text = "(coming soon)",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryColor
                    )
                }
            }
            if (enabled && isFocused) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onReset != null) {
                        Button(
                            onClick = onReset,
                            modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                            contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (resetFocused) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                                },
                                contentColor = if (resetFocused) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        ) {
                            Text(text = "Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = onChange,
                        modifier = Modifier.height(Dimens.iconLg - Dimens.spacingXs),
                        contentPadding = PaddingValues(horizontal = Dimens.spacingMd, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (changeFocused) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                            },
                            contentColor = if (changeFocused) {
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
        if (path != null) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    !enabled -> secondaryColor
                    isCustom && isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
                    isCustom -> MaterialTheme.colorScheme.primary
                    else -> secondaryColor
                },
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        } else if (enabled) {
            Text(
                text = "(auto)",
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

