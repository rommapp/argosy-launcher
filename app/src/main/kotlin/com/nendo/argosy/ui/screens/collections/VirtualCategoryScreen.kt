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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        if (uiState.games.isNotEmpty()) {
            listState.animateScrollToItem(uiState.focusedIndex)
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
                                platformShortName = game.platformShortName,
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
            hints = listOf(
                InputButton.DPAD to "Navigate",
                InputButton.SOUTH to "Open",
                InputButton.EAST to "Back"
            )
        )
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
                modifier = Modifier.size(64.dp)
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
