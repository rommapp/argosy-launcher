package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.PlatformFilterLogic

const val PLATFORM_HEADER_SEARCH = 0
const val PLATFORM_HEADER_SORT = 1
const val PLATFORM_HEADER_FILTER = 2
const val PLATFORM_HEADER_COUNT = 3

private const val WIDE_SCREEN_DP = 900

private val SORT_OPTIONS = listOf(
    PlatformFilterLogic.SortMode.DEFAULT to R.string.ui_platform_filter_sort_default,
    PlatformFilterLogic.SortMode.NAME_ASC to R.string.ui_platform_filter_sort_name_asc,
    PlatformFilterLogic.SortMode.NAME_DESC to R.string.ui_platform_filter_sort_name_desc,
    PlatformFilterLogic.SortMode.MOST_GAMES to R.string.ui_platform_filter_sort_most_games,
    PlatformFilterLogic.SortMode.LEAST_GAMES to R.string.ui_platform_filter_sort_least_games
)

@Composable
fun PlatformFilterHeader(
    platformCount: Int,
    filterMode: PlatformFilterLogic.FilterMode,
    searchQuery: String,
    headerFocused: Boolean,
    headerIndex: Int,
    searchActive: Boolean,
    sortMenuOpen: Boolean,
    sortMenuIndex: Int,
    onSearchQueryChange: (String) -> Unit,
    onSortModeChange: (PlatformFilterLogic.SortMode) -> Unit,
    onFilterModeChange: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onOpenSortMenu: () -> Unit,
    onCloseSortMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val searchFocusRequester = remember { FocusRequester() }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    LaunchedEffect(searchActive) {
        if (searchActive && screenWidthDp > WIDE_SCREEN_DP) {
            searchFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchActive) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Dimens.radiusMd))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .focusBorder(headerFocused && headerIndex == PLATFORM_HEADER_SEARCH, MaterialTheme.colorScheme.primary, Dimens.borderMedium, Dimens.radiusMd)
                    .padding(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSm)
                )
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        R.string.ui_platform_filter_search_placeholder
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(
                        R.string.ui_platform_filter_search_clear
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(Dimens.iconSm)
                        .clickableNoFocus {
                            onSearchQueryChange("")
                            onCloseSearch()
                        }
                )
            }
        } else {
            Text(
                text = pluralStringResource(
                    R.plurals.ui_platform_filter_count,
                    platformCount,
                    platformCount
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (!searchActive) {
                HeaderIconButton(
                    icon = Icons.Default.Search,
                    description = stringResource(R.string.ui_platform_filter_open_search),
                    isFocused = headerFocused && headerIndex == PLATFORM_HEADER_SEARCH,
                    onClick = onOpenSearch
                )
            }

            Box {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.Sort,
                    description = stringResource(R.string.ui_platform_filter_open_sort),
                    isFocused = headerFocused && headerIndex == PLATFORM_HEADER_SORT,
                    onClick = onOpenSortMenu
                )
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = onCloseSortMenu) {
                    SORT_OPTIONS.forEachIndexed { index, (mode, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes)) },
                            onClick = { onSortModeChange(mode) },
                            modifier = Modifier.background(
                                if (index == sortMenuIndex) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                        )
                    }
                }
            }

            val filterFocused = headerFocused && headerIndex == PLATFORM_HEADER_FILTER
            if (filterMode != PlatformFilterLogic.FilterMode.ALL) {
                val filterLabel = when (filterMode) {
                    PlatformFilterLogic.FilterMode.HAS_GAMES ->
                        stringResource(R.string.ui_platform_filter_mode_has_games)
                    PlatformFilterLogic.FilterMode.ENABLED ->
                        stringResource(R.string.ui_platform_filter_mode_enabled)
                    PlatformFilterLogic.FilterMode.ALL ->
                        stringResource(R.string.ui_platform_filter_mode_all)
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .focusBorder(filterFocused, MaterialTheme.colorScheme.primary, Dimens.borderMedium, Dimens.radiusSm)
                        .clickableNoFocus(onClick = onFilterModeChange)
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Text(
                        text = filterLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                HeaderIconButton(
                    icon = Icons.Default.FilterList,
                    description = stringResource(R.string.ui_platform_filter_open_filter),
                    isFocused = filterFocused,
                    onClick = onFilterModeChange
                )
            }
        }
    }
}

@Composable
fun platformFilterHints(
    searchActive: Boolean,
    sortMenuOpen: Boolean,
    headerFocused: Boolean
): List<Pair<InputButton, String>> = when {
    sortMenuOpen -> listOf(
        InputButton.DPAD to stringResource(R.string.ui_platform_filter_hint_sort_navigate),
        InputButton.A to stringResource(R.string.ui_platform_filter_hint_sort_select),
        InputButton.B to stringResource(R.string.ui_platform_filter_hint_sort_back)
    )
    searchActive -> listOf(
        InputButton.A to stringResource(R.string.ui_platform_filter_hint_search_done),
        InputButton.B to stringResource(R.string.ui_platform_filter_hint_search_close)
    )
    headerFocused -> listOf(
        InputButton.DPAD to stringResource(R.string.ui_platform_filter_hint_header_navigate),
        InputButton.A to stringResource(R.string.ui_platform_filter_hint_header_select),
        InputButton.B to stringResource(R.string.ui_platform_filter_hint_header_close)
    )
    else -> listOf(
        InputButton.DPAD to stringResource(R.string.ui_platform_filter_hint_list_navigate),
        InputButton.A to stringResource(R.string.ui_platform_filter_hint_list_toggle),
        InputButton.B to stringResource(R.string.ui_platform_filter_hint_list_close)
    )
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    description: String,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(if (isFocused) focusAccent.copy(alpha = 0.15f) else Color.Transparent)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused) lerp(focusAccent, Color.White, 0.45f)
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSm)
        )
    }
}

private fun Modifier.focusBorder(isFocused: Boolean, color: Color, width: Dp, radius: Dp): Modifier =
    if (isFocused) border(width, color, RoundedCornerShape(radius)) else this
