package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalUiScale

@Composable
fun Modal(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    baseWidth: Dp = 350.dp,
    onDismiss: (() -> Unit)? = null,
    footerHints: List<Pair<InputButton, String>>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val scale = LocalUiScale.current.scale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .then(if (onDismiss != null) Modifier.clickableNoFocus(onClick = onDismiss) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)
        val modalMaxHeight = maxHeight * 0.85f

        Column(
            modifier = modifier
                .width(modalWidth)
                .heightIn(max = modalMaxHeight)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .then(if (onDismiss != null) Modifier.clickableNoFocus {} else Modifier)
                .padding(Dimens.spacingLg)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            content()

            if (footerHints != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                FooterBar(hints = footerHints)
            }
        }
    }
}

@Composable
fun CenteredModal(
    title: String,
    modifier: Modifier = Modifier,
    baseWidth: Dp = 400.dp,
    onDismiss: (() -> Unit)? = null,
    footerHints: List<Pair<InputButton, String>>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val scale = LocalUiScale.current.scale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .then(if (onDismiss != null) Modifier.clickableNoFocus(onClick = onDismiss) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)

        Column(
            modifier = modifier
                .width(modalWidth)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .then(if (onDismiss != null) Modifier.clickableNoFocus {} else Modifier)
                .padding(Dimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            content()

            if (footerHints != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                FooterBar(hints = footerHints)
            }
        }
    }
}

@Composable
fun NestedModal(
    title: String,
    modifier: Modifier = Modifier,
    baseWidth: Dp = 400.dp,
    onDismiss: (() -> Unit)? = null,
    footerHints: List<Pair<InputButton, String>>? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)
    val scale = LocalUiScale.current.scale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .then(if (onDismiss != null) Modifier.clickableNoFocus(onClick = onDismiss) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)

        Column(
            modifier = modifier
                .width(modalWidth)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusLg)
                )
                .then(if (onDismiss != null) Modifier.clickableNoFocus {} else Modifier)
                .padding(Dimens.spacingLg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimens.radiusLg))

            content()

            if (footerHints != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                FooterBar(hints = footerHints)
            }
        }
    }
}
