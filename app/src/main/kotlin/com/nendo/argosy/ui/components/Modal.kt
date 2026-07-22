package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
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
    subtitleColor: Color? = null,
    titleContent: (@Composable () -> Unit)? = null,
    baseWidth: Dp = 350.dp,
    fillHeight: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    footerHints: List<Pair<InputButton, String>>? = null,
    inlineFooterHints: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val scale = LocalUiScale.current.scale

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss ?: {}),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)
        val modalMaxHeight = maxHeight * 0.85f

        Column(
            modifier = modifier
                .width(modalWidth)
                .then(
                    if (fillHeight) Modifier.fillMaxHeight()
                    else Modifier.heightIn(max = modalMaxHeight)
                )
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus {}
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(Dimens.spacingLg)
            ) {
                if (titleContent != null) {
                    titleContent()
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                content()
            }

            if (footerHints != null) {
                if (inlineFooterHints) {
                    val shown = footerHints.filterNot { isObviousHint(it.first, it.second) }
                    if (shown.isNotEmpty()) {
                        FooterBarWithState(
                            hints = shown.map { FooterHintItem(it.first, it.second) },
                            forceVisible = true
                        )
                    }
                } else {
                    FooterHints(hints = footerHints)
                }
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
            .clickableNoFocus(onClick = onDismiss ?: {}),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)

        Column(
            modifier = modifier
                .width(modalWidth)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusPanel)
                )
                .clickableNoFocus {}
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
                FooterHints(hints = footerHints)
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
            .clickableNoFocus(onClick = onDismiss ?: {}),
        contentAlignment = Alignment.Center
    ) {
        val modalWidth = (baseWidth * scale).coerceIn(280.dp, maxWidth * 0.9f)

        Column(
            modifier = modifier
                .width(modalWidth)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(Dimens.radiusPanel)
                )
                .clickableNoFocus {}
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
                FooterHints(hints = footerHints)
            }
        }
    }
}
