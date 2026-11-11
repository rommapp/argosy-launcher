package com.nendo.argosy.ui.screens.collections

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.AlphabetSidebar
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.components.gamelist.GameListRow
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
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_COLLECTION_DETAIL)
        viewModel.republishCompanionDetail()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex, uiState.isSearchStopFocused) {
        if (uiState.isSearchStopFocused) {
            listState.animateScrollToItem(0)
        } else if (uiState.games.isNotEmpty() && uiState.focusedIndex in uiState.games.indices) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val viewportHeight = listState.layoutInfo.viewportEndOffset
            val avgItemHeight = if (visibleItems.isNotEmpty()) {
                visibleItems.sumOf { it.size } / visibleItems.size
            } else 120
            val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
            listState.animateScrollToItem(uiState.focusedIndex, -targetOffset)
        }
    }

    val collectionName = uiState.collection?.name ?: ""
    val searchFieldFocus = remember { FocusRequester() }
    ControllerKeyboardSession(
        open = uiState.isSearchActive && uiState.isSearchKeyboardOpen,
        label = stringResource(R.string.collections_detail_search_placeholder, collectionName),
        text = uiState.searchQuery,
        onTextChange = { viewModel.setSearchQuery(it) },
        onSubmit = { viewModel.closeSearchKeyboard() },
        onClose = { viewModel.closeSearchKeyboard() },
        submitLabel = stringResource(R.string.collections_detail_keyboard_submit),
        onSystemKeyboard = {
            viewModel.closeSearchKeyboard()
            searchFieldFocus.requestFocus()
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollectionDetailHeader(
                collectionName = collectionName,
                gameCount = uiState.games.size,
                isSearchActive = uiState.isSearchActive,
                searchQuery = uiState.searchQuery,
                searchFieldFocus = searchFieldFocus,
                isSearchStopFocused = uiState.isSearchStopFocused,
                onBack = { if (uiState.isSearchActive) viewModel.closeSearch() else onBack() },
                onSearchOpen = { viewModel.openSearch() },
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSearchFieldFocused = { viewModel.closeSearchKeyboard() },
                onSearchClose = { viewModel.closeSearch() }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.collections_detail_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.games.isEmpty() -> {
                    EmptyCollectionDetail()
                }
                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Dimens.spacingLg,
                                end = if (uiState.showSectionSidebar) Dimens.spacingLg + 44.dp else Dimens.spacingLg,
                                top = Dimens.spacingSm,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
                        ) {
                            val hasDialogOpen = uiState.showEditDialog || uiState.showDeleteDialog || uiState.showRemoveGameDialog
                            itemsIndexed(uiState.games, key = { _, g -> g.id }) { index, game ->
                                GameListRow(
                                    title = game.title,
                                    platformSlug = game.platformSlug,
                                    platformDisplayName = game.platformDisplayName,
                                    coverPath = com.nendo.argosy.ui.common.rememberResolvedCoverPath(game.id, null),
                                    details = game.details,
                                    isDownloaded = game.isDownloaded,
                                    isFocused = !hasDialogOpen && !uiState.isSearchStopFocused &&
                                        uiState.focusedIndex == index,
                                    onClick = { onGameClick(game.id) },
                                    onLongClick = { viewModel.handleGameLongPress(index) },
                                    running = com.nendo.argosy.ui.common.LocalRunningGames.current.markFor(game.id)
                                )
                            }
                        }

                        if (uiState.showSectionSidebar) {
                            AlphabetSidebar(
                                availableLetters = uiState.sectionLabels,
                                currentLetter = uiState.currentSectionLabel,
                                onLetterClick = { viewModel.jumpToSection(it) },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                topPadding = Dimens.spacingSm,
                                bottomPadding = 80.dp
                            )
                        }
                    }
                }
            }
        }

        val selectSwapMode = com.nendo.argosy.ui.dualscreen.selectSwapModeState()
        val selectSwapsRoles = selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.TAP
        val selectHintSwaps = selectSwapsRoles ||
            (selectSwapMode == com.nendo.argosy.data.preferences.SelectSwapMode.HOLD && uiState.collection == null)
        val hints = if (uiState.isSearchActive) {
            listOf(
                InputButton.A to stringResource(R.string.collections_detail_hint_search_open),
                InputButton.B to stringResource(R.string.collections_detail_hint_search_close)
            )
        } else {
            val baseHints = listOf(
                InputButton.DPAD to stringResource(R.string.collections_detail_hint_navigate),
                InputButton.A to stringResource(
                    if (selectSwapsRoles && uiState.collection != null) {
                        R.string.collections_detail_hint_open_hold_options
                    } else {
                        R.string.collections_detail_hint_open
                    }
                ),
                InputButton.B to stringResource(R.string.collections_detail_hint_back),
                InputButton.X to stringResource(R.string.collections_detail_hint_search)
            )
            val pinHint = if (uiState.collection != null) {
                listOf(
                    InputButton.Y to stringResource(
                        if (uiState.isPinned) R.string.collections_detail_hint_unpin else R.string.collections_detail_hint_pin
                    )
                )
            } else {
                emptyList()
            }
            val jumpHint = if (uiState.sectionLabels.size > 1) {
                listOf(InputButton.LT_RT to stringResource(R.string.collections_detail_hint_jump))
            } else {
                emptyList()
            }
            val optionsHint = when {
                selectSwapsRoles -> listOf(InputButton.SELECT to stringResource(R.string.collections_detail_hint_swap_screens))
                selectHintSwaps -> listOf(InputButton.SELECT to stringResource(R.string.collections_detail_hint_hold_swap_screens))
                selectSwapMode != null && uiState.collection != null -> listOf(
                    InputButton.SELECT to stringResource(R.string.collections_detail_hint_options_hold_swap_screens)
                )
                uiState.collection != null -> listOf(InputButton.SELECT to stringResource(R.string.collections_detail_hint_options))
                else -> emptyList()
            }
            val refreshHint = listOf(
                InputButton.START to stringResource(
                    if (uiState.isRefreshing) R.string.collections_detail_hint_refreshing else R.string.collections_detail_hint_refresh
                )
            )
            baseHints + pinHint + jumpHint + optionsHint + refreshHint
        }
        FooterHints(
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { inputHandler.onConfirm() }
                    InputButton.B -> { inputHandler.onBack() }
                    InputButton.X -> { inputHandler.onContextMenu() }
                    InputButton.Y -> { inputHandler.onSecondaryAction() }
                    InputButton.SELECT -> if (selectHintSwaps) {
                        com.nendo.argosy.DualScreenManagerHolder.instance?.swapRoles()
                    } else {
                        inputHandler.onSelect()
                    }
                    InputButton.START -> { inputHandler.onMenu() }
                    else -> Unit
                }
            }
        )

        uiState.overlayLetter?.let { letter ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(Dimens.radiusLg))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter,
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
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
            onOptionSelect = { option -> viewModel.selectOption(option) },
            onDismiss = { viewModel.hideOptionsModal() },
            rows = uiState.optionRows,
            downloadableCount = uiState.downloadableGamesCount,
            gameTitle = uiState.focusedGame?.title,
            onMoveToDisplay = { target -> viewModel.moveRunningGame(target) }
        )
    }

    com.nendo.argosy.ui.screens.running.RunningGameCloseHost(viewModel.runningGameMenu)

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
    isSearchActive: Boolean,
    searchQuery: String,
    searchFieldFocus: FocusRequester,
    isSearchStopFocused: Boolean,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchFieldFocused: () -> Unit,
    onSearchClose: () -> Unit
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
                contentDescription = stringResource(R.string.collections_detail_back_description),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        if (isSearchActive) {
            CollectionSearchField(
                query = searchQuery,
                placeholder = stringResource(R.string.collections_detail_search_placeholder, collectionName),
                isFocused = isSearchStopFocused,
                focusRequester = searchFieldFocus,
                onQueryChange = onSearchChange,
                onFieldFocused = onSearchFieldFocused,
                onTap = onSearchOpen,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearchClose) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.collections_detail_close_search_description),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = collectionName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pluralStringResource(R.plurals.collections_detail_header_game_count, gameCount, gameCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CollectionSearchButton(
                isFocused = isSearchStopFocused,
                contentDescription = stringResource(R.string.collections_detail_search_description),
                onClick = onSearchOpen
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
                modifier = Modifier.size(Dimens.iconXl + Dimens.spacingMd)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.collections_detail_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.collections_detail_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
