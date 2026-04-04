package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.nendo.argosy.data.platform.InstalledApp
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.settings.AppPickerModalState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * App picker for ad-hoc emulator bindings on platforms with no known installed emulator. Lists
 * launchable user-installed apps alphabetically, excluding system apps and known emulators.
 *
 * Input:
 * - UP/DOWN move focus
 * - LB/RB skip 5 items (shoulder jump)
 * - A confirms selection
 * - B dismisses
 */
@Composable
fun AppPickerModal(
    state: AppPickerModalState,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // Keep focused item in view as focus moves.
    LaunchedEffect(state.focusIndex) {
        if (state.apps.isNotEmpty()) {
            listState.animateScrollToItem(state.focusIndex.coerceIn(0, state.apps.size - 1))
        }
    }

    Modal(
        title = "Select App  -  ${state.platformName}",
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss
    ) {
        Text(
            text = "Argosy has no known emulator installed for this platform. Pick any app to " +
                "launch games with; you can tune how Argosy calls it via Launch Args afterwards.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )

        if (state.apps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Dimens.spacingXl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No launchable apps found.",
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
                    count = state.apps.size,
                    key = { idx -> state.apps[idx].packageName }
                ) { index ->
                    AppRow(
                        app = state.apps[index],
                        isFocused = index == state.focusIndex,
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
    app: InstalledApp,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val background = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val labelColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
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
private fun AppIcon(app: InstalledApp, modifier: Modifier = Modifier) {
    val icon = app.icon
    if (icon != null) {
        val density = LocalDensity.current
        val sizePx = with(density) { 40.dp.roundToPx() }
        val bitmap = runCatching { icon.toBitmap(width = sizePx, height = sizePx) }.getOrNull()
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = app.displayName,
                modifier = modifier
            )
            return
        }
    }
    // Fallback: colored square placeholder
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
