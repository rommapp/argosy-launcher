package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.PlatformFilterHeader
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.components.platformFilterHints
import com.nendo.argosy.ui.screens.settings.PlatformFilterItem
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.util.PlatformFilterLogic

@Composable
fun PlatformFiltersModal(
    platforms: List<PlatformFilterItem>,
    filterMode: PlatformFilterLogic.FilterMode,
    searchQuery: String,
    focusIndex: Int,
    isLoading: Boolean,
    headerFocused: Boolean,
    headerIndex: Int,
    searchActive: Boolean,
    sortMenuOpen: Boolean,
    sortMenuIndex: Int,
    onTogglePlatform: (Long) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (PlatformFilterLogic.SortMode) -> Unit,
    onFilterModeChange: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSortMenu: () -> Unit,
    onCloseSortMenu: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    FocusedScroll(listState = listState, focusedIndex = focusIndex)

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
                text = stringResource(R.string.settings_platform_filters_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.settings_platform_filters_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            PlatformFilterHeader(
                platformCount = platforms.size,
                filterMode = filterMode,
                searchQuery = searchQuery,
                headerFocused = headerFocused,
                headerIndex = headerIndex,
                searchActive = searchActive,
                sortMenuOpen = sortMenuOpen,
                sortMenuIndex = sortMenuIndex,
                onSearchQueryChange = onSearchQueryChange,
                onSortModeChange = onSortModeChange,
                onFilterModeChange = onFilterModeChange,
                onOpenSearch = onOpenSearch,
                onCloseSearch = onCloseSearch,
                onOpenSortMenu = onOpenSortMenu,
                onCloseSortMenu = onCloseSortMenu
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
                            text = stringResource(R.string.settings_platform_filters_loading),
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
                        text = stringResource(R.string.settings_platform_filters_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = Dimens.headerHeightLg + Dimens.headerHeightLg + Dimens.iconSm),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(
                        items = platforms,
                        key = { _, item -> item.id }
                    ) { index, platform ->
                        val subtitle = if (platform.romCount > 0) {
                            pluralStringResource(
                                R.plurals.settings_platform_filters_rom_count,
                                platform.romCount,
                                platform.romCount
                            )
                        } else {
                            stringResource(R.string.settings_platform_filters_no_games)
                        }
                        SwitchPreference(
                            title = platform.name,
                            subtitle = subtitle,
                            isEnabled = platform.syncEnabled,
                            isFocused = !headerFocused && focusIndex == index,
                            onToggle = { onTogglePlatform(platform.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterHints(
                hints = platformFilterHints(searchActive, sortMenuOpen, headerFocused),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> if (searchActive) onCloseSearch() else Unit
                        InputButton.B -> when {
                            sortMenuOpen -> onCloseSortMenu()
                            searchActive -> onCloseSearch()
                            else -> onDismiss()
                        }
                        else -> Unit
                    }
                }
            )
        }
    }
}
