package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.platform.LaunchableApp
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.screens.settings.AppPickerModalState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun AppPickerModal(
    state: AppPickerModalState,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AppPickerModal(
        title = stringResource(R.string.settings_app_picker_title, state.platformName),
        intro = stringResource(R.string.settings_app_picker_intro),
        apps = state.apps,
        focusIndex = state.focusIndex,
        onItemTap = onItemTap,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun AppPickerModal(
    title: String,
    intro: String,
    apps: List<LaunchableApp>,
    focusIndex: Int,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val reduced = reducedMotion()

    LaunchedEffect(focusIndex) {
        if (apps.isNotEmpty()) {
            listState.animateScrollToItemCentered(focusIndex.coerceIn(0, apps.size - 1), reduced)
        }
    }

    Modal(
        title = title,
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        Text(
            text = intro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )

        if (apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spacingXl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_app_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                items(
                    count = apps.size,
                    key = { idx -> apps[idx].packageName }
                ) { index ->
                    AppRow(
                        app = apps[index],
                        isFocused = index == focusIndex,
                        onClick = {
                            onItemTap(index)
                            onConfirm()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: LaunchableApp,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val background = if (isFocused) theme.focusAccent.copy(alpha = 0.15f) else Color.Transparent
    val labelColor = if (isFocused) lerp(theme.focusAccent, Color.White, 0.45f) else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isFocused) {
        lerp(theme.focusAccent, Color.White, 0.45f).copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        AppIcon(app = app, modifier = Modifier.size(40.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = subtitleColor
            )
        }
    }
}

@Composable
private fun AppIcon(app: LaunchableApp, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = AppIconData(app.packageName),
        contentDescription = app.displayName,
        modifier = modifier,
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    )
}
