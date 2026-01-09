package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import com.nendo.argosy.ui.quickmenu.GameCardUi
import com.nendo.argosy.ui.quickmenu.GameRowUi
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.quickmenu.QuickMenuUiState
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun QuickMenuContent(
    uiState: QuickMenuUiState,
    isFocused: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onGameSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
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
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.RANDOM -> RandomContent(
                game = uiState.randomGame,
                isFocused = isFocused,
                onClick = { uiState.randomGame?.id?.let { onGameSelect(it) } }
            )
            QuickMenuOrb.MOST_PLAYED -> ListContent(
                games = uiState.mostPlayedGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = "No games played yet",
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.TOP_UNPLAYED -> ListContent(
                games = uiState.topUnplayedGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = "No rated games found",
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.RECENT -> ListContent(
                games = uiState.recentGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = "No recent games",
                onGameSelect = onGameSelect
            )
            QuickMenuOrb.FAVORITES -> ListContent(
                games = uiState.favoriteGames,
                focusedIndex = uiState.focusedContentIndex,
                isFocused = isFocused,
                emptyMessage = "No favorites yet",
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
    onGameSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val inputShape = RoundedCornerShape(12.dp)
    val inputBorderModifier = if (isInputFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, inputShape)
    } else Modifier

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(inputBorderModifier)
                .background(
                    if (isInputFocused) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    inputShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "Search games...",
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        if (query.length < 2) {
            if (recentSearches.isNotEmpty()) {
                RecentSearchesList(
                    searches = recentSearches,
                    focusedIndex = focusedIndex,
                    isFocused = isListFocused
                )
            } else {
                EmptyState(message = "Type at least 2 characters to search")
            }
        } else if (results.isEmpty()) {
            EmptyState(message = "No games found for \"$query\"")
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
private fun RandomContent(
    game: GameCardUi?,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (game == null) {
        EmptyState(message = "No games available")
        return
    }

    val shape = RoundedCornerShape(16.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxSize()
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape
            )
            .clip(shape)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        val imageData = game.coverPath?.let { path ->
            if (path.startsWith("/")) File(path) else path
        }
        AsyncImage(
            model = imageData,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Text(
                text = game.platformName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Text(
                text = buildString {
                    game.year?.let { append(it) }
                    game.developer?.let {
                        if (isNotEmpty()) append(" | ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            game.genre?.let { genre ->
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                game.rating?.let { rating ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "${rating.toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (game.isDownloaded) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
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
        if (games.isNotEmpty() && focusedIndex in games.indices) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 80

            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            listState.animateScrollToItem(focusedIndex, -targetOffset)
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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (searches.isNotEmpty() && focusedIndex in searches.indices) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 56

            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            listState.animateScrollToItem(focusedIndex, -targetOffset)
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Recent Searches",
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
                    isFocused = isFocused && index == focusedIndex
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    query: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    val borderModifier = if (isFocused) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
    } else Modifier

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(borderModifier)
            .background(
                if (isFocused) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                shape
            )
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Icon(
            Icons.Default.History,
            contentDescription = null,
            tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
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
