package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun OptionItem(
    label: String,
    icon: ImageVector? = null,
    iconTint: Color? = null,
    value: String? = null,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val contentColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.onErrorContainer
        isDangerous -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .then(if (onClick != null) Modifier.clickableNoFocus(onClick = onClick) else Modifier)
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
            Text(
                text = "[Current]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
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
