package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

enum class ArgosyCheckState { CHECKED, UNCHECKED, PARTIAL }

/** Standard boxed check control per CONTROL-FOUNDATIONS: checkbox + mark, toggled by A/tap. */
@Composable
fun ArgosyCheckbox(
    state: ArgosyCheckState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val theme = LocalArgosyTheme.current
    val alpha = if (enabled) 1f else 0.4f
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Box(
        modifier = modifier
            .size(Dimens.iconMd)
            .clip(shape)
            .then(
                if (state == ArgosyCheckState.UNCHECKED) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), shape)
                } else {
                    Modifier.background(theme.focusAccent.copy(alpha = alpha))
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            ArgosyCheckState.CHECKED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(Dimens.iconSm)
            )
            ArgosyCheckState.PARTIAL -> Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(Dimens.iconSm)
            )
            ArgosyCheckState.UNCHECKED -> {}
        }
    }
}
