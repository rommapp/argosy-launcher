package com.nendo.argosy.ui.screens.collections

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.collections.components.WideGameCard
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun VirtualCategoryScreen(
    onBack: () -> Unit,
    onGameClick: (Long) -> Unit,
    viewModel: VirtualCategoryViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack, onGameClick) {
        viewModel.createInputHandler(
            onBack = onBack,
            onGameClick = onGameClick
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_VIRTUAL_BROWSER)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_VIRTUAL_BROWSER)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.games.isNotEmpty() && uiState.focusedIndex in uiState.games.indices) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 120
            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            listState.animateScrollToItem(uiState.focusedIndex, -targetOffset)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            VirtualCategoryHeader(
                categoryName = uiState.categoryName,
                gameCount = uiState.games.size,
                onBack = onBack
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.games.isEmpty() -> {
                    EmptyVirtualCategory()
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = Dimens.spacingLg,
                            end = Dimens.spacingLg,
                            top = Dimens.spacingSm,
                            bottom = 80.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                    ) {
                        itemsIndexed(uiState.games, key = { _, g -> g.id }) { index, game ->
                            WideGameCard(
                                title = game.title,
                                platformDisplayName = game.platformDisplayName,
                                coverPath = game.coverPath,
                                developer = game.developer,
                                releaseYear = game.releaseYear,
                                genre = game.genre,
                                userRating = game.userRating,
                                userDifficulty = game.userDifficulty,
                                achievementCount = game.achievementCount,
                                playTimeMinutes = game.playTimeMinutes,
                                isFocused = uiState.focusedIndex == index,
                                onClick = { onGameClick(game.id) }
                            )
                        }
                    }
                }
            }
        }

        FooterBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hints = buildList {
                add(InputButton.DPAD to "Navigate")
                add(InputButton.A to "Open")
                add(InputButton.B to "Back")
                add(InputButton.Y to if (uiState.isPinned) "Unpin" else "Pin")
                add(InputButton.X to if (uiState.isRefreshing) "Refreshing..." else "Refresh")
                if (uiState.canDownloadAll) {
                    add(InputButton.SELECT to "Download All (${uiState.downloadableGamesCount})")
                }
            }
        )

        if (uiState.downloadAllProgress.isActive) {
            DownloadAllModal(
                currentIndex = uiState.downloadAllProgress.currentIndex,
                totalCount = uiState.downloadAllProgress.totalCount
            )
        }
    }
}

@Composable
private fun DownloadAllModal(
    currentIndex: Int,
    totalCount: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(Dimens.radiusXl),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = Dimens.elevationLg)
        ) {
            Column(
                modifier = Modifier
                    .padding(Dimens.spacingLg)
                    .width(Dimens.modalWidthLg - 170.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.iconXl),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                Text(
                    text = "Queuing Downloads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                Text(
                    text = "$currentIndex of $totalCount games",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                LinearProgressIndicator(
                    progress = { if (totalCount > 0) currentIndex.toFloat() / totalCount else 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun VirtualCategoryHeader(
    categoryName: String,
    gameCount: Int,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$gameCount games",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyVirtualCategory() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl + Dimens.spacingMd)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "No games in this category",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
