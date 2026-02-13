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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.hardware.CompanionAppBar
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
    val titleId: String?
)

private val CARD_WIDTH = 100.dp
private val CARD_HEIGHT = 140.dp
private val FOCUSED_CARD_WIDTH = 140.dp
private val FOCUSED_CARD_HEIGHT = 196.dp
private val CARD_SPACING = 12.dp

@Composable
fun DualHomeLowerScreen(
    games: List<DualHomeGameUi>,
    selectedIndex: Int,
    platformName: String,
    totalCount: Int,
    homeApps: List<String>,
    appBarFocused: Boolean,
    appBarIndex: Int,
    onGameTapped: (Int) -> Unit,
    onGameSelected: (Long) -> Unit,
    onAppClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Auto-scroll to center the selected item and broadcast selection
    LaunchedEffect(selectedIndex, games) {
        if (games.isNotEmpty() && selectedIndex in games.indices) {
            // Scroll to center the item
            listState.animateScrollToItem(
                index = selectedIndex,
                scrollOffset = -200 // Offset to center (approximate)
            )
            broadcastGameSelection(context, games[selectedIndex])
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Game carousel - focus controlled by ViewModel selectedIndex
        // Extra height to accommodate larger focused card
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Position indicator and platform info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Position dots
            PositionIndicator(
                totalCount = minOf(games.size, 12),
                currentIndex = selectedIndex,
                modifier = Modifier.weight(1f)
            )

            // Platform and count
            Text(
                text = "$platformName ($totalCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (homeApps.isNotEmpty()) {
            CompanionAppBar(
                apps = homeApps,
                onAppClick = onAppClick,
                focusedIndex = if (appBarFocused) appBarIndex else -1
            )
        }
    }
}

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
private fun PositionIndicator(
    totalCount: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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

private fun broadcastGameSelection(context: Context, game: DualHomeGameUi) {
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
    }
    context.sendBroadcast(intent)
}
