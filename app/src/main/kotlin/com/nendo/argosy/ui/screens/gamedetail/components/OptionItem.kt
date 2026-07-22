package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

@Composable
fun OptionItem(
    label: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    value: String? = null,
    trailingIcon: ImageVector? = null,
    trailingTint: Color? = null,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val disabledAlpha = 0.38f
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
        isDangerous && isFocused -> lerp(LocalArgosyTheme.current.destructive, Color.White, 0.45f)
        isDangerous -> LocalArgosyTheme.current.destructive
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        !isEnabled -> Color.Transparent
        isDangerous && isFocused -> LocalArgosyTheme.current.destructive.copy(alpha = 0.15f)
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .then(if (onClick != null && isEnabled) Modifier.clickableNoFocus(onClick = onClick) else Modifier)
            .padding(horizontal = Dimens.radiusLg, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint ?: contentColor,
                modifier = Modifier.width(Dimens.iconSm)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Current",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(Dimens.iconSm)
            )
        } else if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = trailingTint ?: contentColor,
                modifier = Modifier.width(Dimens.iconSm)
            )
        } else if (value != null) {
            Text(
                text = "[$value]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
