package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
import com.nendo.argosy.ui.quickmenu.GameRowUi
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.quickmenu.QuickMenuUiState
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun QuickMenuContent(
    uiState: QuickMenuUiState,
    isFocused: Boolean,
    onSearchQueryChange: (String) -> Unit,
    searchFieldFocus: FocusRequester,
    onSearchFieldFocused: () -> Unit,
    onSearchFieldTap: () -> Unit,
    onGameSelect: (Long) -> Unit,
    onRecentSearchSelect: (String) -> Unit,
    onRandomPlay: (Long) -> Unit,
    onRandomFavorite: () -> Unit,
    onRandomReroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (isFocused) 1f else 0.7f

    AnimatedContent(
        targetState = uiState.selectedOrb,
        transitionSpec = {
            fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(100))
        },
        modifier = modifier.alpha(contentAlpha),
        label = "quickMenuContent"
    ) { orb ->
        when (orb) {
            QuickMenuOrb.SEARCH -> SearchContent(
                query = uiState.searchQuery,
                results = uiState.searchResults,
                recentSearches = uiState.recentSearches,
                focusedIndex = uiState.focusedContentIndex,
                isInputFocused = isFocused && uiState.searchInputFocused,
                isListFocused = isFocused && !uiState.searchInputFocused,
                onQueryChange = onSearchQueryChange,
                searchFieldFocus = searchFieldFocus,
                onSearchFieldFocused = onSearchFieldFocused,
                onSearchFieldTap = onSearchFieldTap,
                onGameSelect = onGameSelect,
                onRecentSearchSelect = onRecentSearchSelect
            )
            QuickMenuOrb.RANDOM -> QuickMenuRandomContent(
                game = uiState.randomGame,
                isResolved = uiState.isRandomResolved,
                isFocused = isFocused,
                onPlay = onRandomPlay,
                onDetails = onGameSelect,
                onFavorite = onRandomFavorite,
                onReroll = onRandomReroll
            )
            QuickMenuOrb.MOST_PLAYED -> ListContent(
                games = uiState.mostPlayedGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_most_played),
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.TOP_UNPLAYED -> ListContent(
                games = uiState.topUnplayedGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_top_unplayed),
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.RECENT -> ListContent(
                games = uiState.recentGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_recent),
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.FAVORITES -> ListContent(
                games = uiState.favoriteGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = stringResource(R.string.ui_quick_menu_empty_favorites),
                onGameSelect = onGameSelect
            )
        }
    }
}

@Composable
private fun SearchContent(
    query: String,
    results: List<GameRowUi>,
    recentSearches: List<String>,
    focusedIndex: Int,
    isInputFocused: Boolean,
    isListFocused: Boolean,
    onQueryChange: (String) -> Unit,
    searchFieldFocus: FocusRequester,
    onSearchFieldFocused: () -> Unit,
    onSearchFieldTap: () -> Unit,
    onGameSelect: (Long) -> Unit,
    onRecentSearchSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputShape = RoundedCornerShape(Dimens.radiusLg)
    val inputBorderModifier = if (isInputFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, inputShape)
    } else Modifier

    Column(modifier = modifier.fillMaxSize()) {
        ControllerKeyboardTapTarget(onTap = onSearchFieldTap, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(inputBorderModifier)
                    .background(
                        if (isInputFocused) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                            .compositeOver(MaterialTheme.colorScheme.surface)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        inputShape
                    )
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
                Spacer(modifier = Modifier.width(Dimens.radiusLg))

                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_quick_menu_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(searchFieldFocus)
                            .onFocusChanged { if (it.isFocused) onSearchFieldFocused() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        if (query.length < 2) {
            if (recentSearches.isNotEmpty()) {
                RecentSearchesList(
                    searches = recentSearches,
                    focusedIndex = focusedIndex,
                    isFocused = isListFocused,
                    onRecentSearchSelect = onRecentSearchSelect,
                )
            } else {
                EmptyState(message = stringResource(R.string.ui_quick_menu_search_prompt))
            }
        } else if (results.isEmpty()) {
            EmptyState(
                message = stringResource(R.string.ui_quick_menu_search_no_results, query)
            )
        } else {
            GameList(
                games = results,
                focusedIndex = focusedIndex,
                isFocused = isListFocused,
                onGameSelect = onGameSelect
            )
        }
    }
}

@Composable
private fun ListContent(
    games: List<GameRowUi>,
    focusedIndex: Int,
    isFocused: Boolean,
    emptyMessage: String,
    onGameSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (games.isEmpty()) {
        EmptyState(message = emptyMessage)
        return
    }

    GameList(
        games = games,
        focusedIndex = focusedIndex,
        isFocused = isFocused,
        onGameSelect = onGameSelect,
        modifier = modifier
    )
}

@Composable
private fun GameList(
    games: List<GameRowUi>,
    focusedIndex: Int,
    isFocused: Boolean,
    onGameSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex in games.indices) {
            listState.animateScrollToItemCentered(focusedIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
            QuickMenuGameRow(
                game = game,
                isFocused = isFocused && index == focusedIndex,
                onClick = { onGameSelect(game.id) }
            )
        }
    }
}

@Composable
private fun RecentSearchesList(
    searches: List<String>,
    focusedIndex: Int,
    isFocused: Boolean,
    onRecentSearchSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex in searches.indices) {
            listState.animateScrollToItemCentered(focusedIndex)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.ui_quick_menu_recent_searches_heading),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Dimens.spacingSm)
        )

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(searches, key = { index, _ -> index }) { index, query ->
                RecentSearchRow(
                    query = query,
                    isFocused = isFocused && index == focusedIndex,
                    onRecentSearchSelect = onRecentSearchSelect
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    query: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    onRecentSearchSelect: (String) -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(
                if (isFocused) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                    .compositeOver(MaterialTheme.colorScheme.surface)
                else MaterialTheme.colorScheme.surface,
                shape
            )
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
            .clickableNoFocus(onClick = { onRecentSearchSelect(query) }),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
