package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun PlatformFiltersModal(
    platforms: List<PlatformFilterItem>,
    focusIndex: Int,
    isLoading: Boolean,
    onTogglePlatform: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthXl)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "PLATFORM FILTERS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Select which platforms to include during library sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .height(Dimens.headerHeightLg + Dimens.spacingXxl + Dimens.spacingSm)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(Dimens.iconLg))
                        Text(
                            text = "Fetching platforms...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (platforms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .height(Dimens.headerHeightLg - Dimens.iconXl)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No platforms available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.height(Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(platforms) { index, platform ->
                        val subtitle = if (platform.romCount > 0) {
                            "${platform.romCount} games"
                        } else {
                            "No games"
                        }
                        SwitchPreference(
                            title = platform.name,
                            subtitle = subtitle,
                            isEnabled = platform.syncEnabled,
                            isFocused = focusIndex == index,
                            onToggle = { onTogglePlatform(platform.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.A to "Toggle",
                    InputButton.B to "Close"
                )
            )
        }
    }
}
