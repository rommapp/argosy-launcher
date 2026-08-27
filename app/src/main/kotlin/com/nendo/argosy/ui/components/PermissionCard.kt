package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun PermissionCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val backgroundColor = when {
        isFocused -> focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        isFocused -> lerp(focusAccent, Color.White, 0.45f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = when {
        isFocused -> lerp(focusAccent, Color.White, 0.45f).copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val leadingIcon = if (isGranted) Icons.Default.CheckCircle else icon
    val leadingTint = if (isGranted) MaterialTheme.colorScheme.primary else contentColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(shape)
            .clickableNoFocus(onClick = onClick)
            .background(backgroundColor, shape)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = if (isGranted) {
                stringResource(R.string.ui_permission_card_granted)
            } else {
                null
            },
            tint = leadingTint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor
            )
        }
    }
}
