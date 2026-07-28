package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun SteamLauncherPreference(
    displayName: String,
    subtitle: String? = null,
    isSyncing: Boolean,
    isFocused: Boolean,
    isEnabled: Boolean,
    onAdd: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val backgroundColor = if (isFocused) {
        theme.focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f).copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusControl))
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
            ActionButton(
                label = "Add",
                onClick = onAdd,
                focused = isFocused,
                enabled = isEnabled
            )
        }
    }
}
