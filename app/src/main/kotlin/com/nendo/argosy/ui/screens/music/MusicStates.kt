package com.nendo.argosy.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Composable
fun MusicMessageState(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Dimens.spacingMd)
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
        }
        if (actionLabel != null && onAction != null) {
            ActionButton(
                label = actionLabel,
                onClick = onAction,
                focused = true,
                primary = true,
                glyph = InputButton.A,
                modifier = Modifier.padding(top = Dimens.spacingLg)
            )
        }
    }
}

@Composable
fun MusicLoadingState(modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier.fillMaxSize().padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        repeat(ComponentDefaults.MusicColumns.skeletonColumns) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Dimens.radiusPanel))
                    .background(theme.surfaceRaised)
            )
        }
    }
}
