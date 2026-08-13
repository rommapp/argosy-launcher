package com.nendo.argosy.ui.screens.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.media.components.MediaActionRow
import com.nendo.argosy.ui.screens.media.components.MediaDetailSkeleton
import com.nendo.argosy.ui.screens.media.components.MediaEpisodeRow
import com.nendo.argosy.ui.screens.media.components.MediaErrorState
import com.nendo.argosy.ui.screens.media.components.MediaExpandedHeader
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaSeasonTabs
import com.nendo.argosy.ui.screens.media.components.MediaStickyCollapsedHeader
import com.nendo.argosy.ui.screens.media.modals.MediaDownloadModalHost
import com.nendo.argosy.ui.screens.media.modals.MediaResumeModalHost
import com.nendo.argosy.ui.theme.ArgosyThemeTokens
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The detail view for a movie or a series.
 *
 * Movie mode is the action row and the metadata. Series mode adds the season tabs and the episode
 * list under them, both as real lists: focus moves between sections vertically and within a section
 * horizontally, so the episode list keeps the whole vertical axis to itself.
 */
@Composable
fun MediaDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (itemId: String, startOver: Boolean) -> Unit,
    viewModel: MediaDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val listState = rememberLazyListState()

    LaunchedEffect(itemId) { viewModel.load(itemId) }

    val inputHandler = remember(viewModel, onBack, onPlay) {
        viewModel.createInputHandler(onBack = onBack, onPlay = onPlay)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MEDIA_DETAIL)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MEDIA_DETAIL)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val headerItemCount = if (uiState.hasSeasons) SERIES_HEADER_ITEMS else MOVIE_HEADER_ITEMS
    val isCollapsed by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    LaunchedEffect(uiState.section, uiState.episodeIndex, uiState.seasonIndex) {
        when (uiState.section) {
            MediaDetailSection.ACTIONS -> listState.animateScrollToItem(0)
            MediaDetailSection.SEASONS -> listState.animateScrollToItem(headerItemCount - 1)
            MediaDetailSection.EPISODES -> if (uiState.episodes.isNotEmpty()) {
                listState.animateScrollToItem(headerItemCount + uiState.episodeIndex)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> MediaDetailSkeleton()
            uiState.item == null -> MediaErrorState(
                message = uiState.errorMessage ?: "This title could not be opened."
            )
            else -> MediaDetailContent(
                uiState = uiState,
                listState = listState,
                isCollapsed = isCollapsed,
                viewModel = viewModel,
                onPlay = onPlay
            )
        }

        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            FooterHints(
                hints = buildDetailHints(uiState),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> inputHandler.onConfirm()
                        InputButton.B -> inputHandler.onBack()
                        InputButton.Y -> inputHandler.onSecondaryAction()
                        InputButton.X -> inputHandler.onContextMenu()
                        InputButton.LB_RB -> inputHandler.onNextSection()
                        else -> Unit
                    }
                }
            )
        }
    }

    MediaResumeModalHost(
        prompt = uiState.resumePrompt,
        onResume = { id ->
            viewModel.dismissResumePrompt()
            onPlay(id, false)
        },
        onStartOver = { id ->
            viewModel.dismissResumePrompt()
            onPlay(id, true)
        },
        onDismiss = viewModel::dismissResumePrompt
    )

    MediaDownloadModalHost(
        prompt = uiState.downloadPrompt,
        onMove = viewModel::moveDownloadFocus,
        onFocus = viewModel::focusDownloadOption,
        onConfirm = viewModel::confirmDownloadOption,
        onDismiss = viewModel::dismissDownloadPrompt
    )
}

@Composable
private fun MediaDetailContent(
    uiState: MediaDetailUiState,
    listState: LazyListState,
    isCollapsed: Boolean,
    viewModel: MediaDetailViewModel,
    onPlay: (itemId: String, startOver: Boolean) -> Unit
) {
    val detail = uiState.item ?: return
    val theme = LocalArgosyTheme.current

    Column(modifier = Modifier.fillMaxSize()) {
        MediaStickyCollapsedHeader(item = detail, isVisible = isCollapsed)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Dimens.footerHeight + Dimens.spacingXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item(key = "header") {
                MediaExpandedHeader(item = detail)
            }
            item(key = "actions") {
                MediaActionRow(
                    uiState = uiState,
                    isSectionFocused = uiState.section == MediaDetailSection.ACTIONS,
                    onAction = { index ->
                        viewModel.setActionIndex(index)
                        when (uiState.actions.getOrNull(index)) {
                            MediaDetailAction.PLAY -> uiState.playTarget?.let { onPlay(it.itemId, false) }
                            MediaDetailAction.DOWNLOAD -> viewModel.openDownloadPrompt()
                            MediaDetailAction.FAVORITE -> viewModel.toggleFavorite()
                            MediaDetailAction.WATCHED -> viewModel.toggleWatched()
                            null -> Unit
                        }
                    },
                    onPlayLongPress = {
                        val target = uiState.playTarget
                        if (target != null && !viewModel.openResumePrompt(target)) {
                            onPlay(target.itemId, false)
                        }
                    },
                    modifier = Modifier.padding(horizontal = Dimens.spacingLg)
                )
            }
            if (uiState.hasSeasons) {
                item(key = "seasons") {
                    MediaSeasonTabs(
                        seasons = uiState.seasons,
                        selectedIndex = uiState.seasonIndex,
                        isSectionFocused = uiState.section == MediaDetailSection.SEASONS,
                        onSelect = viewModel::selectSeason,
                        modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
                    )
                }
            }
            if (uiState.mode == MediaDetailMode.SERIES) {
                episodeSection(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlay = onPlay,
                    theme = theme
                )
            }
        }
    }
}

private fun LazyListScope.episodeSection(
    uiState: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    onPlay: (itemId: String, startOver: Boolean) -> Unit,
    theme: ArgosyThemeTokens
) {
    val episodesError = uiState.episodesErrorMessage
    when {
        uiState.episodes.isNotEmpty() -> itemsIndexed(
            items = uiState.episodes,
            key = { _, episode -> episode.itemId }
        ) { index, episode ->
            MediaEpisodeRow(
                episode = episode,
                isFocused = uiState.section == MediaDetailSection.EPISODES && index == uiState.episodeIndex,
                onClick = {
                    viewModel.setEpisodeIndex(index)
                    onPlay(episode.itemId, false)
                },
                onLongClick = {
                    viewModel.setEpisodeIndex(index)
                    if (!viewModel.openResumePrompt(episode)) onPlay(episode.itemId, false)
                },
                modifier = Modifier.padding(horizontal = Dimens.spacingLg)
            )
        }
        uiState.isLoadingEpisodes -> item(key = "episodes-loading") {
            Text(
                text = "Loading episodes",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textMute,
                modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg)
            )
        }
        episodesError != null -> item(key = "episodes-error") {
            MediaMessageState(
                icon = Icons.Outlined.Inbox,
                title = "Episodes are unavailable",
                message = episodesError
            )
        }
        else -> item(key = "episodes-empty") {
            MediaMessageState(
                icon = Icons.Outlined.Inbox,
                title = "No episodes in this season",
                message = null
            )
        }
    }
}

private fun buildDetailHints(uiState: MediaDetailUiState): List<Pair<InputButton, String>> = buildList {
    if (uiState.seasons.size > 1) add(InputButton.LB_RB to "Season")
    add(InputButton.X to downloadHint(uiState))
    if (uiState.section == MediaDetailSection.EPISODES) {
        add(InputButton.Y to if (uiState.focusedEpisode?.played == true) "Mark Unwatched" else "Mark Watched")
    } else {
        add(InputButton.Y to if (uiState.item?.isFavorite == true) "Unfavourite" else "Favourite")
    }
    add(InputButton.A to confirmHint(uiState))
    add(InputButton.B to "Back")
}

private fun downloadHint(uiState: MediaDetailUiState): String = when {
    uiState.section == MediaDetailSection.EPISODES && uiState.focusedEpisode?.isDownloaded == true ->
        "Downloaded"
    uiState.section == MediaDetailSection.EPISODES -> "Download Episode"
    else -> "Downloads"
}

private fun confirmHint(uiState: MediaDetailUiState): String {
    val resumeTarget = when (uiState.section) {
        MediaDetailSection.EPISODES -> uiState.focusedEpisode
        MediaDetailSection.SEASONS -> null
        MediaDetailSection.ACTIONS ->
            if (uiState.focusedAction == MediaDetailAction.PLAY) uiState.playTarget else null
    }
    return when {
        resumeTarget?.hasResumePosition == true -> "Resume (hold: Start Over)"
        uiState.section == MediaDetailSection.SEASONS -> "Open Season"
        uiState.section == MediaDetailSection.ACTIONS &&
            uiState.focusedAction == MediaDetailAction.DOWNLOAD -> "Downloads"
        uiState.section == MediaDetailSection.ACTIONS &&
            uiState.focusedAction == MediaDetailAction.FAVORITE -> "Favourite"
        uiState.section == MediaDetailSection.ACTIONS &&
            uiState.focusedAction == MediaDetailAction.WATCHED -> "Mark Watched"
        else -> "Play"
    }
}

private const val MOVIE_HEADER_ITEMS = 2
private const val SERIES_HEADER_ITEMS = 3
