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
import androidx.compose.material.icons.filled.Folder
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
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOption
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionsModal
import com.nendo.argosy.ui.screens.collections.dialogs.DeleteCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.EditCollectionDialog
import com.nendo.argosy.ui.screens.collections.dialogs.RemoveGameDialog
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun CollectionDetailScreen(
    onBack: () -> Unit,
    onGameClick: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = hiltViewModel()
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
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTION_DETAIL)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTION_DETAIL)
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
            CollectionDetailHeader(
                collectionName = uiState.collection?.name ?: "",
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
                    EmptyCollectionDetail()
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
                        val hasDialogOpen = uiState.showEditDialog || uiState.showDeleteDialog || uiState.showRemoveGameDialog
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
                                isFocused = !hasDialogOpen && uiState.focusedIndex == index,
                                onClick = { onGameClick(game.id) }
                            )
                        }
                    }
                }
            }
        }

        val baseHints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.SOUTH to "Open",
            InputButton.EAST to "Back",
            InputButton.WEST to if (uiState.isRefreshing) "Refreshing..." else "Refresh"
        )
        val pinHint = if (uiState.collection != null) {
            listOf(InputButton.NORTH to if (uiState.isPinned) "Unpin" else "Pin")
        } else {
            emptyList()
        }
        val optionsHint = if (uiState.collection != null) {
            listOf(InputButton.SELECT to "Options")
        } else {
            emptyList()
        }
        FooterBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            hints = baseHints + pinHint + optionsHint
        )
    }

    if (uiState.showEditDialog && uiState.collection != null) {
        EditCollectionDialog(
            currentName = uiState.collection!!.name,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { name -> viewModel.updateCollectionName(name) }
        )
    }

    if (uiState.showDeleteDialog && uiState.collection != null) {
        DeleteCollectionDialog(
            collectionName = uiState.collection!!.name,
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteCollection(onDeleted = onBack) }
        )
    }

    if (uiState.showOptionsModal && uiState.collection != null) {
        CollectionOptionsModal(
            collectionName = uiState.collection!!.name,
            focusIndex = uiState.optionsModalFocusIndex,
            onOptionSelect = { option ->
                when (option) {
                    CollectionOption.RENAME -> {
                        viewModel.hideOptionsModal()
                        viewModel.showEditDialog()
                    }
                    CollectionOption.DELETE -> {
                        viewModel.hideOptionsModal()
                        viewModel.showDeleteDialog()
                    }
                    CollectionOption.REMOVE_GAME -> {
                        viewModel.hideOptionsModal()
                        viewModel.showRemoveGameDialog()
                    }
                }
            },
            onDismiss = { viewModel.hideOptionsModal() },
            showRemoveGame = uiState.focusedGame != null,
            gameTitle = uiState.focusedGame?.title
        )
    }

    if (uiState.showRemoveGameDialog && uiState.gameToRemove != null) {
        RemoveGameDialog(
            gameTitle = uiState.gameToRemove!!.title,
            collectionName = uiState.collection?.name ?: "",
            onDismiss = { viewModel.hideRemoveGameDialog() },
            onConfirm = { viewModel.confirmRemoveGame() }
        )
    }
}

@Composable
private fun CollectionDetailHeader(
    collectionName: String,
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
                text = collectionName,
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
private fun EmptyCollectionDetail() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "No games in this collection",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = "Add games from the library or game details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
