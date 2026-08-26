package com.nendo.argosy.ui.screens.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.media.components.MediaEmptyState
import com.nendo.argosy.ui.screens.media.components.MediaErrorState
import com.nendo.argosy.ui.screens.media.components.MediaLibrarySkeleton
import com.nendo.argosy.ui.screens.media.components.MediaLibraryTabs
import com.nendo.argosy.ui.screens.media.components.MediaPosterGrid
import com.nendo.argosy.ui.screens.media.components.MediaSignedOutState
import com.nendo.argosy.ui.screens.media.modals.MediaResumeModalHost
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * Browses one media library at a time. The libraries themselves are a tab row rather than a screen
 * of their own: a handheld user reaches them with the shoulder buttons without leaving the grid, and
 * a TV user has the same path with no touch involved.
 */
@Composable
fun MediaLibraryScreen(
    onBack: () -> Unit,
    onItemSelect: (String) -> Unit,
    libraryId: String? = null,
    onPlay: (itemId: String, startOver: Boolean) -> Unit = { _, _ -> },
    viewModel: MediaLibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(libraryId, uiState.libraries) {
        if (libraryId != null) viewModel.selectLibraryById(libraryId)
    }
    val inputDispatcher = LocalInputDispatcher.current
    val gridState = rememberLazyGridState()
    val theme = LocalArgosyTheme.current

    val inputHandler = remember(viewModel, onBack, onItemSelect, onPlay) {
        viewModel.createInputHandler(
            onBack = onBack,
            onItemSelect = onItemSelect,
            onPlay = { itemId -> onPlay(itemId, false) }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MEDIA_LIBRARY)
                viewModel.republishCompanionDetail()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MEDIA_LIBRARY)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.clearCompanionDetail()
        }
    }

    LaunchedEffect(uiState.focusedIndex, uiState.selectedLibraryIndex) {
        if (uiState.items.isNotEmpty()) {
            gridState.animateScrollToItemCentered(
                uiState.focusedIndex.coerceIn(0, uiState.items.lastIndex)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "MEDIA",
                style = MaterialTheme.typography.labelLarge,
                color = theme.focusAccent,
                modifier = Modifier.padding(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg,
                    top = Dimens.spacingLg
                )
            )
            if (uiState.libraries.isNotEmpty()) {
                MediaLibraryTabs(
                    libraries = uiState.libraries,
                    selectedIndex = uiState.selectedLibraryIndex,
                    onSelect = viewModel::selectLibrary,
                    modifier = Modifier.padding(
                        start = Dimens.spacingLg,
                        end = Dimens.spacingLg,
                        top = Dimens.spacingSm
                    )
                )
            }
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !uiState.isSignedIn -> MediaSignedOutState()
                    uiState.isLoading -> MediaLibrarySkeleton()
                    uiState.errorMessage != null && uiState.items.isEmpty() ->
                        MediaErrorState(message = uiState.errorMessage.orEmpty())
                    uiState.isEmpty -> MediaEmptyState()
                    else -> MediaPosterGrid(
                        items = uiState.items,
                        focusedIndex = uiState.focusedIndex,
                        gridState = gridState,
                        contentPadding = PaddingValues(
                            start = Dimens.spacingLg,
                            end = Dimens.spacingLg,
                            top = Dimens.spacingMd,
                            bottom = Dimens.footerHeight + Dimens.spacingXl
                        ),
                        onColumnsChanged = viewModel::setColumnsCount,
                        onItemClick = { index ->
                            viewModel.setFocusedIndex(index)
                            uiState.items.getOrNull(index)?.let { onItemSelect(it.itemId) }
                        },
                        onItemLongClick = { index ->
                            val item = uiState.items.getOrNull(index)
                            when {
                                item == null || !item.isPlayable -> Unit
                                viewModel.openResumePrompt(index) -> Unit
                                else -> onPlay(item.itemId, false)
                            }
                        },
                        onPosterLoaded = viewModel::onPosterLoaded
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterHints(
                hints = buildLibraryHints(uiState),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> inputHandler.onConfirm()
                        InputButton.B -> inputHandler.onBack()
                        InputButton.X -> inputHandler.onContextMenu()
                        InputButton.Y -> inputHandler.onSecondaryAction()
                        InputButton.LB_RB -> inputHandler.onNextSection()
                        else -> Unit
                    }
                }
            )
        }
    }

    MediaResumeModalHost(
        prompt = uiState.resumePrompt,
        onResume = { itemId ->
            viewModel.dismissResumePrompt()
            onPlay(itemId, false)
        },
        onStartOver = { itemId ->
            viewModel.dismissResumePrompt()
            onPlay(itemId, true)
        },
        onDismiss = viewModel::dismissResumePrompt
    )
}

private fun buildLibraryHints(uiState: MediaLibraryUiState): List<Pair<InputButton, String>> = buildList {
    if (uiState.libraries.size > 1) add(InputButton.LB_RB to "Library")
    val focused = uiState.focusedItem
    if (focused?.isPlayable == true) {
        add(InputButton.Y to if (focused.hasResumePosition) "Resume" else "Play")
    }
    add(InputButton.X to if (uiState.isRefreshing) "Refreshing" else "Refresh")
    add(InputButton.A to "Open")
    add(InputButton.B to "Back")
}
