package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.nendo.argosy.util.PlatformFilterLogic

@Composable
fun PlatformFiltersModal(
    platforms: List<PlatformFilterItem>,
    hasGames: Boolean,
    searchQuery: String,
    focusIndex: Int,
    isLoading: Boolean,
    onTogglePlatform: (Long) -> Unit,
    onSortModeChange: (PlatformFilterLogic.SortMode) -> Unit,
    onHasGamesChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    // Scroll to top when the filtered list changes (e.g. search, sort, filter)
    LaunchedEffect(platforms) {
        if (platforms.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickable(
                onClick = onDismiss,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthXl)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showSearch by remember { mutableStateOf(searchQuery.isNotEmpty()) }
                var showSortMenu by remember { mutableStateOf(false) }

                if (showSearch) {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search platforms...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, "Search")
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                onSearchQueryChange("")
                                showSearch = false
                            }) {
                                Icon(Icons.Default.Close, "Clear")
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                } else {
                    Text(
                        text = "${platforms.size} platforms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                    }

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default") },
                                onClick = {
                                    onSortModeChange(PlatformFilterLogic.SortMode.DEFAULT)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)") },
                                onClick = {
                                    onSortModeChange(PlatformFilterLogic.SortMode.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z-A)") },
                                onClick = {
                                    onSortModeChange(PlatformFilterLogic.SortMode.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Most Games") },
                                onClick = {
                                    onSortModeChange(PlatformFilterLogic.SortMode.MOST_GAMES)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Least Games") },
                                onClick = {
                                    onSortModeChange(PlatformFilterLogic.SortMode.LEAST_GAMES)
                                    showSortMenu = false
                                }
                            )
                        }
                    }

                    if (hasGames) {
                        FilterChip(
                            selected = true,
                            onClick = { onHasGamesChange(false) },
                            label = { Text("Has Games") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimens.iconXs)
                                )
                            }
                        )
                    } else {
                        IconButton(
                            onClick = { onHasGamesChange(true) }
                        ) {
                            Icon(
                                Icons.Default.FilterList,
                                "Show platforms with games"
                            )
                        }
                    }
                }
            }

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
                    InputButton.SOUTH to "Toggle",
                    InputButton.EAST to "Close"
                )
            )
        }
    }
}
