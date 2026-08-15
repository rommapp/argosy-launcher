package com.nendo.argosy.ui.screens.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.media.components.MediaBackdrop
import com.nendo.argosy.ui.screens.media.components.MediaCastRail
import com.nendo.argosy.ui.screens.media.components.MediaSimilarRail
import com.nendo.argosy.ui.screens.media.components.MediaDetailMenu
import com.nendo.argosy.ui.screens.media.components.MediaDetailSkeleton
import com.nendo.argosy.ui.screens.media.components.MediaEpisodeRow
import com.nendo.argosy.ui.screens.media.components.MediaErrorState
import com.nendo.argosy.ui.screens.media.components.MediaExpandedHeader
import com.nendo.argosy.ui.screens.media.components.MediaMessageState
import com.nendo.argosy.ui.screens.media.components.MediaSeasonTabs
import com.nendo.argosy.ui.screens.media.components.MediaStickyCollapsedHeader
import com.nendo.argosy.ui.screens.media.modals.MediaDetailMenuModalHost
import com.nendo.argosy.ui.screens.media.modals.MediaDownloadModalHost
import com.nendo.argosy.ui.screens.media.modals.MediaResumeModalHost
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens

/**
 * The detail view for a movie or a series.
 *
 * The screen is a permanent left rail beside a content column, the shape game detail uses. The rail
 * carries what acts on the title and, under a divider, the title's own sections; the column carries
 * what those sections hold. For a series that is the season tabs and the episode list, and they are
 * pinned rather than scrolled: the region keeps its place at the top of the column and the episode
 * list scrolls inside it, so walking an episode list never drags the tabs off screen. The expanded
 * header is what yields to the region, collapsing to its sticky form for as long as focus is out of
 * the rail. A movie has no sections, so its rail ends at Options and its column is the header alone.
 */
@Composable
fun MediaDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onPlay: (itemId: String, startOver: Boolean) -> Unit,
    onNavigateToLibrary: (String) -> Unit = {},
    viewModel: MediaDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val backdropSettings by viewModel.backdropSettings.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current

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

    Box(modifier = Modifier.fillMaxSize()) {
        MediaBackdrop(
            imageUrl = uiState.item?.backdropUrl.orEmpty(),
            settings = backdropSettings,
            modifier = Modifier.fillMaxSize()
        )

        when {
            uiState.isLoading -> MediaDetailSkeleton()
            uiState.item == null -> MediaErrorState(
                message = uiState.errorMessage ?: "This title could not be opened."
            )
            else -> MediaDetailContent(
                uiState = uiState,
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

    MediaDetailMenuModalHost(
        menu = uiState.menu,
        onMove = viewModel::moveMenuFocus,
        onFocus = viewModel::focusMenuOption,
        onConfirm = { viewModel.confirmMenuOption(onNavigateToLibrary) },
        onDismiss = viewModel::dismissMenu
    )
}

@Composable
private fun MediaDetailContent(
    uiState: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    onPlay: (itemId: String, startOver: Boolean) -> Unit
) {
    val detail = uiState.item ?: return
    val theme = LocalArgosyTheme.current
    val configuration = LocalConfiguration.current
    val displayAspectRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp
    val isCompactMenu = displayAspectRatio <= COMPACT_MENU_ASPECT_RATIO

    Column(modifier = Modifier.fillMaxSize()) {
        MediaStickyCollapsedHeader(item = detail, isVisible = uiState.isHeaderCollapsed)

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .then(
                        if (isCompactMenu) {
                            Modifier.width(Dimens.mediaMenuRailWidth)
                        } else {
                            Modifier.fillMaxWidth(MENU_WIDTH_FRACTION)
                        }
                    )
                    .fillMaxHeight()
                    .background(
                        theme.surfaceRaised.copy(
                            alpha = ComponentDefaults.MediaBackdrop.surfaceAlpha
                        )
                    )
            ) {
                MediaDetailMenu(
                    uiState = uiState,
                    isCompact = isCompactMenu,
                    onRow = { index -> viewModel.activateRow(index, onPlay) },
                    onPlayLongPress = {
                        val target = uiState.playTarget
                        if (target != null && !viewModel.openResumePrompt(target)) {
                            onPlay(target.itemId, false)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (isCompactMenu) Dimens.spacingXs else Dimens.spacingLg,
                            end = if (isCompactMenu) Dimens.spacingXs else Dimens.spacingMd,
                            top = Dimens.spacingMd
                        )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = !uiState.isHeaderCollapsed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    MediaExpandedHeader(item = detail)
                }

                when {
                    uiState.mode == MediaDetailMode.SERIES &&
                        uiState.section != MediaDetailSection.CAST &&
                        uiState.section != MediaDetailSection.SIMILAR ->
                        MediaSeriesPane(
                            uiState = uiState,
                            viewModel = viewModel,
                            onPlay = onPlay,
                            modifier = Modifier.weight(1f)
                        )

                    else -> MediaExtraRails(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * Who is in this title and what is like it, both on the page rather than behind a door.
 *
 * A title with no episode list has the column to spare, so hiding these behind the rail only made
 * opening one feel like leaving the page. The rail rows still lead here; they move focus to a
 * region that was already in front of the viewer.
 */
@Composable
private fun MediaExtraRails(
    uiState: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(uiState.section) {
        when (uiState.section) {
            MediaDetailSection.CAST -> scrollState.animateScrollTo(0)
            MediaDetailSection.SIMILAR -> scrollState.animateScrollTo(scrollState.maxValue)
            else -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = Dimens.spacingSm, bottom = Dimens.footerHeight + Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (uiState.cast.isNotEmpty()) {
            MediaRailHeading(
                text = "Cast",
                isFocused = uiState.section == MediaDetailSection.CAST
            )
            MediaCastRail(
                cast = uiState.cast,
                focusedIndex = uiState.castIndex,
                isSectionFocused = uiState.section == MediaDetailSection.CAST,
                onSelect = viewModel::setCastIndex
            )
        }

        if (uiState.similar.isNotEmpty()) {
            MediaRailHeading(
                text = "More Like This",
                isFocused = uiState.section == MediaDetailSection.SIMILAR
            )
            MediaSimilarRail(
                titles = uiState.similar,
                focusedIndex = uiState.similarIndex,
                isSectionFocused = uiState.section == MediaDetailSection.SIMILAR,
                onSelect = viewModel::setSimilarIndex,
                onOpen = { viewModel.openSimilarTitle() }
            )
        }
    }
}

@Composable
private fun MediaRailHeading(text: String, isFocused: Boolean) {
    val theme = LocalArgosyTheme.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = if (isFocused) theme.textPrimary else theme.textDim,
        modifier = Modifier.padding(horizontal = Dimens.spacingLg)
    )
}

/**
 * The pinned half of a series: the season tabs, then the episodes under them.
 *
 * The tabs are laid out once at the top of this pane and stay there; only the episode list scrolls,
 * and it scrolls within the height this pane was given rather than within the page.
 */
@Composable
private fun MediaSeriesPane(
    uiState: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    onPlay: (itemId: String, startOver: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val episodesError = uiState.episodesErrorMessage

    Column(modifier = modifier.fillMaxWidth()) {
        if (uiState.hasSeasons) {
            MediaSeasonTabs(
                seasons = uiState.seasons,
                selectedIndex = uiState.seasonIndex,
                isSectionFocused = uiState.section == MediaDetailSection.SEASONS,
                onSelect = viewModel::focusSeason,
                modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm)
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.episodes.isNotEmpty() -> MediaEpisodeList(
                    uiState = uiState,
                    viewModel = viewModel,
                    onPlay = onPlay
                )

                uiState.isLoadingEpisodes -> Text(
                    text = "Loading episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textMute,
                    modifier = Modifier.fillMaxWidth().padding(Dimens.spacingLg)
                )

                episodesError != null -> MediaMessageState(
                    icon = Icons.Outlined.Inbox,
                    title = "Episodes are unavailable",
                    message = episodesError
                )

                uiState.hasSeasons -> MediaMessageState(
                    icon = Icons.Outlined.Inbox,
                    title = "No episodes in this season",
                    message = null
                )

                else -> MediaMessageState(
                    icon = Icons.Outlined.Inbox,
                    title = "No seasons yet",
                    message = "This series has no seasons on the server, or the last refresh has not reached it."
                )
            }
        }
    }
}

/**
 * The episode list and the centring that keeps the focused row in view.
 *
 * Focus here is a plain index into one list, so the shared scroller is handed a single section
 * spanning the whole of it: the first episode clamps to the top of the pane, the last clamps to the
 * bottom, and everything between rides the centre.
 */
@Composable
private fun MediaEpisodeList(
    uiState: MediaDetailUiState,
    viewModel: MediaDetailViewModel,
    onPlay: (itemId: String, startOver: Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val sections = remember(uiState.episodes.size) {
        listOf(
            ListSection(
                listStartIndex = 0,
                listEndIndex = uiState.episodes.lastIndex,
                focusStartIndex = 0,
                focusEndIndex = uiState.episodes.lastIndex
            )
        )
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.episodeIndex,
        focusToListIndex = { it },
        sections = sections
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = Dimens.footerHeight + Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(
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
    }
}

private fun buildDetailHints(uiState: MediaDetailUiState): List<Pair<InputButton, String>> = buildList {
    if (uiState.hasSiblingTitles) add(InputButton.LB_RB to "Prev/Next Title")
    add(InputButton.X to "Options")
    if (uiState.section == MediaDetailSection.EPISODES) {
        add(InputButton.Y to if (uiState.focusedEpisode?.played == true) "Mark Unwatched" else "Mark Watched")
    } else {
        add(InputButton.Y to if (uiState.item?.isFavorite == true) "Unfavorite" else "Favorite")
    }
    add(InputButton.A to confirmHint(uiState))
    add(InputButton.B to "Back")
}

private fun confirmHint(uiState: MediaDetailUiState): String {
    val resumeTarget = when (uiState.section) {
        MediaDetailSection.EPISODES -> uiState.focusedEpisode
        MediaDetailSection.SEASONS -> null
        MediaDetailSection.CAST -> null
        MediaDetailSection.SIMILAR -> null
        MediaDetailSection.MENU ->
            if (uiState.focusedRow == MediaDetailRow.PLAY) uiState.playTarget else null
    }
    if (resumeTarget?.hasResumePosition == true) return "Resume"
    if (uiState.section == MediaDetailSection.SEASONS) return "Open Season"
    if (uiState.section == MediaDetailSection.SIMILAR) return "Open Title"
    if (uiState.section != MediaDetailSection.MENU) return "Play"
    return when (uiState.focusedRow) {
        MediaDetailRow.DOWNLOAD -> "Downloads"
        MediaDetailRow.FAVORITE -> "Favorite"
        MediaDetailRow.WATCHED -> "Mark Watched"
        MediaDetailRow.OPTIONS -> "Options"
        MediaDetailRow.SEASONS -> "Open Seasons"
        MediaDetailRow.EPISODES -> "Open Episodes"
        else -> "Play"
    }
}

private const val COMPACT_MENU_ASPECT_RATIO = 1.3f
private val MENU_WIDTH_FRACTION = DimensionTokens.Layout.mediaMenuWidthPct / 100f
