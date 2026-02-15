/**
 * DUAL-SCREEN COMPONENT - Lower display game carousel.
 * Runs in :companion process (SecondaryHomeActivity).
 * Communicates selection to upper display via broadcasts.
 * Uses custom InputHandler focus (selectedIndex from ViewModel).
 */
package com.nendo.argosy.ui.dualscreen.home

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.hardware.CompanionAppBar
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.util.touchOnly
import java.io.File

data class DualHomeGameUi(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val platformName: String,
    val platformSlug: String,
    val playTimeMinutes: Int,
    val lastPlayedAt: Long?,
    val status: String?,
    val communityRating: Float?,
    val userRating: Int,
    val userDifficulty: Int,
    val isPlayable: Boolean,
    val isFavorite: Boolean,
    val backgroundPath: String?,
    val description: String?,
    val developer: String?,
    val releaseYear: Int?,
    val titleId: String?,
    val genre: String? = null,
    val gameModes: String? = null,
    val franchises: String? = null
)

private val CARD_WIDTH = 100.dp
private val CARD_HEIGHT = 140.dp
private val FOCUSED_CARD_WIDTH = 140.dp
private val FOCUSED_CARD_HEIGHT = 196.dp
private val CARD_SPACING = 12.dp

private val GRID_CARD_SIZE = 100.dp

@Composable
fun DualHomeLowerScreen(
    games: List<DualHomeGameUi>,
    selectedIndex: Int,
    platformName: String,
    totalCount: Int,
    hasMoreGames: Boolean,
    isViewAllFocused: Boolean,
    homeApps: List<String>,
    appBarFocused: Boolean,
    appBarIndex: Int,
    viewMode: DualHomeViewMode,
    onGameTapped: (Int) -> Unit,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onLibraryToggle: () -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games) {
        if (games.isNotEmpty()) {
            if (selectedIndex in games.indices) {
                listState.animateScrollToItem(
                    index = selectedIndex,
                    scrollOffset = -200
                )
                broadcastGameSelection(context, games[selectedIndex])
            } else if (hasMoreGames && selectedIndex == games.size) {
                listState.animateScrollToItem(
                    index = games.size,
                    scrollOffset = -200
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (viewMode == DualHomeViewMode.CAROUSEL) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCollectionsClick) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Collections",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                IconButton(onClick = onLibraryToggle) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = "Library Grid",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "$platformName ($totalCount)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FOCUSED_CARD_HEIGHT + 16.dp),
            contentAlignment = Alignment.Center
        ) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(games, key = { _, game -> game.id }) { index, game ->
                    CarouselGameCard(
                        game = game,
                        isSelected = index == selectedIndex,
                        showBorder = index == selectedIndex && !appBarFocused,
                        onClick = {
                            onGameTapped(index)
                            onGameSelected(game.id)
                        }
                    )
                }
                if (hasMoreGames) {
                    item(key = "view_all") {
                        ViewAllCard(
                            remainingCount = totalCount - games.size,
                            isFocused = isViewAllFocused,
                            onClick = onViewAllClick
                        )
                    }
                }
            }
        }

        PositionIndicator(
            totalCount = games.size,
            currentIndex = if (isViewAllFocused) -1 else selectedIndex,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        if (homeApps.isNotEmpty()) {
            CompanionAppBar(
                apps = homeApps,
                onAppClick = onAppClick,
                focusedIndex = if (appBarFocused) appBarIndex else -1
            )
        }
    }
}

// --- Collection List ---

@Composable
fun DualHomeCollectionList(
    items: List<DualCollectionListItem>,
    selectedIndex: Int,
    onCollectionTapped: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (items.isNotEmpty() && selectedIndex in items.indices) {
            listState.animateScrollToItem(selectedIndex, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items) { index, item ->
            when (item) {
                is DualCollectionListItem.Header -> {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            top = if (index > 0) 16.dp else 0.dp,
                            bottom = 4.dp
                        )
                    )
                }
                is DualCollectionListItem.Collection -> {
                    DualCollectionRow(
                        item = item,
                        isSelected = index == selectedIndex,
                        onClick = { onCollectionTapped(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DualCollectionRow(
    item: DualCollectionListItem.Collection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                } else {
                    Modifier.background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(8.dp)
                    )
                }
            )
            .touchOnly(onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CollectionCoverMosaic(
            coverPaths = item.coverPaths,
            modifier = Modifier.size(56.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.platformSummary.isNotBlank()) {
                Text(
                    text = item.platformSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Text(
            text = "${item.gameCount} games",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CollectionCoverMosaic(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        when {
            coverPaths.isEmpty() -> {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
            coverPaths.size == 1 -> {
                val imageData = coverPaths[0].let { path ->
                    if (path.startsWith("/")) File(path) else path
                }
                AsyncImage(
                    model = imageData,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val displayed = coverPaths.take(4)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        displayed.getOrNull(0)?.let { path ->
                            AsyncImage(
                                model = if (path.startsWith("/")) File(path) else path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        displayed.getOrNull(1)?.let { path ->
                            AsyncImage(
                                model = if (path.startsWith("/")) File(path) else path,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                    if (displayed.size > 2) {
                        Row(modifier = Modifier.weight(1f)) {
                            displayed.getOrNull(2)?.let { path ->
                                AsyncImage(
                                    model = if (path.startsWith("/")) File(path) else path,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                            displayed.getOrNull(3)?.let { path ->
                                AsyncImage(
                                    model = if (path.startsWith("/")) File(path) else path,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            } ?: Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// --- Library Grid ---

@Composable
fun DualHomeLibraryGrid(
    games: List<DualHomeGameUi>,
    focusedIndex: Int,
    columns: Int,
    availableLetters: List<String>,
    currentLetter: String,
    showLetterOverlay: Boolean = false,
    overlayLetter: String = "",
    onGameTapped: (Int) -> Unit,
    onLetterClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(focusedIndex) {
        if (games.isNotEmpty() && focusedIndex in games.indices) {
            val viewportHeight = gridState.layoutInfo.viewportSize.height
            val itemHeight = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            gridState.animateScrollToItem(focusedIndex, -centerOffset)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                    LibraryGridCard(
                        game = game,
                        isFocused = index == focusedIndex,
                        onClick = { onGameTapped(index) }
                    )
                }
            }

            if (availableLetters.size >= 9) {
                AlphabetSidebar(
                    availableLetters = availableLetters,
                    currentLetter = currentLetter,
                    onLetterClick = onLetterClick,
                    modifier = Modifier.fillMaxHeight(),
                    topPadding = 0.dp,
                    bottomPadding = 0.dp
                )
            }
        }

        LetterJumpOverlay(
            letter = overlayLetter,
            visible = showLetterOverlay
        )
    }
}

@Composable
private fun LetterJumpOverlay(
    letter: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(100)),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(400)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                style = MaterialTheme.typography.displayLarge,
                fontSize = 120.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LibraryGridCard(
    game: DualHomeGameUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(GRID_CARD_SIZE)
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    )
                } else Modifier
            )
            .touchOnly(onClick)
    ) {
        if (game.coverPath != null) {
            AsyncImage(
                model = File(game.coverPath),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.title.take(2).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// --- Filter Overlay ---

@Composable
fun DualFilterOverlay(
    category: DualFilterCategory,
    options: List<DualFilterOption>,
    focusedIndex: Int,
    onOptionTapped: (Int) -> Unit,
    onCategoryTapped: (DualFilterCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (options.isNotEmpty() && focusedIndex in options.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val itemHeight = visibleItems.firstOrNull()?.size ?: 0
            if (itemHeight > 0 && viewportHeight > 0) {
                val centerOffset = (viewportHeight - itemHeight) / 2
                val paddingBuffer = (itemHeight * 0.2f).toInt()
                listState.animateScrollToItem(
                    index = focusedIndex,
                    scrollOffset = -centerOffset + paddingBuffer
                )
            } else {
                listState.animateScrollToItem(focusedIndex)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DualFilterCategory.entries.forEach { cat ->
                val isActive = cat == category
                TextButton(onClick = { onCategoryTapped(cat) }) {
                    Text(
                        text = cat.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(options) { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index == focusedIndex) {
                                Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(6.dp)
                                    )
                            } else {
                                Modifier.background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp)
                                )
                            }
                        )
                        .touchOnly { onOptionTapped(index) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (option.isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

// --- Shared Composables ---

@Composable
private fun CarouselGameCard(
    game: DualHomeGameUi,
    isSelected: Boolean,
    showBorder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.5f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "card_alpha"
    )

    val cardWidth = if (isSelected) FOCUSED_CARD_WIDTH else CARD_WIDTH
    val cardHeight = if (isSelected) FOCUSED_CARD_HEIGHT else CARD_HEIGHT

    Box(
        modifier = modifier
            .size(width = cardWidth, height = cardHeight)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .touchOnly(onClick)
    ) {
        if (game.coverPath != null) {
            AsyncImage(
                model = File(game.coverPath),
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.title.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ViewAllCard(
    remainingCount: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = CARD_WIDTH, height = CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .touchOnly(onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "+$remainingCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PositionIndicator(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        repeat(totalCount) { index ->
            val isActive = index == (currentIndex % totalCount)
            Box(
                modifier = Modifier
                    .size(if (isActive) 10.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

fun broadcastGameSelection(context: Context, game: DualHomeGameUi) {
    val intent = Intent("com.nendo.argosy.DUAL_GAME_SELECTED").apply {
        setPackage(context.packageName)
        putExtra("game_id", game.id)
        putExtra("title", game.title)
        putExtra("cover_path", game.coverPath)
        putExtra("background_path", game.backgroundPath)
        putExtra("platform_name", game.platformName)
        putExtra("platform_slug", game.platformSlug)
        putExtra("play_time_minutes", game.playTimeMinutes)
        putExtra("last_played_at", game.lastPlayedAt ?: 0L)
        putExtra("status", game.status)
        putExtra("community_rating", game.communityRating ?: 0f)
        putExtra("user_rating", game.userRating)
        putExtra("user_difficulty", game.userDifficulty)
        putExtra("description", game.description)
        putExtra("developer", game.developer)
        putExtra("release_year", game.releaseYear ?: 0)
        putExtra("title_id", game.titleId)
        putExtra("is_favorite", game.isFavorite)
        putExtra("is_downloaded", game.isPlayable)
    }
    context.sendBroadcast(intent)
}
