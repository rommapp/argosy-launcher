package com.nendo.argosy.ui.screens.musicstats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.dualscreen.presentationTakesSlotsState
import com.nendo.argosy.ui.dualscreen.selectSwapModeState
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.withNavBarClearance
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.music.MusicLoadingState
import com.nendo.argosy.ui.screens.music.MusicMessageState
import com.nendo.argosy.ui.screens.music.MusicModalInput
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus

private const val MUSIC_STATS_SLOT_OWNER = "music_stats"

@Composable
fun MusicStatsScreen(
    onBack: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    viewModel: MusicStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(viewModel, onBack) { MusicStatsInputHandler(viewModel = viewModel, onExit = onBack) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MUSIC_STATS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MUSIC_STATS)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, onOpenNowPlaying) {
        viewModel.playbackStarted.collect { onOpenNowPlaying() }
    }

    val split = presentationTakesSlotsState()
    val slotOwner = remember(viewModel) { SlotOwner.of(MUSIC_STATS_SLOT_OWNER, viewModel) }
    if (split) MusicStatsOnPresentation(uiState = uiState, owner = slotOwner)
    val selectSwaps = selectSwapModeState() == SelectSwapMode.TAP

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (split) Modifier.surfaceBackdrop(BackdropRole.CONTENT) else Modifier)
    ) {
        StatsHeader(
            selected = uiState.tab,
            onBack = { inputHandler.onBack() },
            onSelectTab = viewModel::selectTab
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            StatsBody(uiState = uiState, viewModel = viewModel)
        }
    }

    FooterHints(
        hints = statsFooterHints(uiState, selectSwaps),
        onHintClick = { button ->
            when (button) {
                InputButton.LT_RT -> inputHandler.onNextTrigger()
                InputButton.Y -> inputHandler.onSecondaryAction()
                InputButton.SELECT -> if (selectSwaps) {
                    DualScreenManagerHolder.instance?.swapRoles()
                } else {
                    inputHandler.onSelect()
                }
                else -> Unit
            }
        }
    )

    StatsOptionsModalHost(
        uiState = uiState,
        onMove = viewModel::moveOptionsFocus,
        onFocus = viewModel::focusOption,
        onConfirm = viewModel::confirmOption,
        onDismiss = viewModel::dismissOptions
    )
}

@Composable
private fun StatsHeader(selected: MusicStatsTab, onBack: () -> Unit, onSelectTab: (MusicStatsTab) -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.spacingSm, end = Dimens.spacingLg, top = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp)
                .clickableNoFocus(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.music_stats_back_desc),
                tint = theme.textDim,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
        Text(
            text = stringResource(R.string.music_stats_title),
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.spacingXs)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
            MusicStatsTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val shape = RoundedCornerShape(Dimens.radiusPill)
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .argosyFocusIndicators(focused = isSelected, indicators = FocusIndicators.Pill, shape = shape)
                        .clickableNoFocus { onSelectTab(tab) }
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(tab.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) theme.textPrimary else theme.textDim
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsBody(uiState: MusicStatsUiState, viewModel: MusicStatsViewModel) {
    val page = uiState.page
    val showsList = uiState.tab == MusicStatsTab.MONTH || page.shelves.isNotEmpty()
    when {
        page.loadFailed -> MusicMessageState(
            icon = Icons.Outlined.CloudOff,
            title = stringResource(R.string.music_stats_error_title),
            message = stringResource(R.string.music_stats_error_message),
            actionLabel = stringResource(R.string.music_stats_error_retry),
            onAction = viewModel::retry
        )
        page.isLoading && page.shelves.isEmpty() -> MusicLoadingState()
        showsList -> ShelfList(uiState = uiState, viewModel = viewModel)
        else -> EmptyTab(uiState.tab)
    }
}

@Composable
private fun ShelfList(uiState: MusicStatsUiState, viewModel: MusicStatsViewModel) {
    val shelves = uiState.page.shelves
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    LaunchedEffect(uiState.tab, uiState.shelfIndex) {
        if (uiState.shelfIndex in shelves.indices) listState.animateScrollToItemCentered(uiState.shelfIndex, reduced)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            top = Dimens.spacingSm,
            bottom = withNavBarClearance(Dimens.footerHeight + Dimens.spacingLg)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(shelves, key = { _, shelf -> shelf.key }) { index, shelf ->
            val isFocused = index == uiState.shelfIndex
            when (shelf.kind) {
                MusicStatsShelfKind.MONTH -> Column {
                    MusicStatsMonthHeader(
                        yearMonth = uiState.yearMonth,
                        totals = uiState.totals,
                        isFocused = isFocused,
                        onStep = { delta ->
                            viewModel.focus(index, 0)
                            viewModel.stepMonth(delta)
                        }
                    )
                    if (!uiState.month.isLoading && !uiState.month.hasHistory) {
                        EmptyNote(
                            title = stringResource(R.string.music_stats_month_empty_title),
                            message = stringResource(R.string.music_stats_empty_message)
                        )
                    }
                }
                else -> MusicStatsShelfRow(
                    shelf = shelf,
                    yearMonth = uiState.yearMonth,
                    isFocused = isFocused,
                    focusedItem = if (isFocused) uiState.itemIndex else 0,
                    onTap = { item -> viewModel.tapItem(index, item) },
                    onLongPress = { item -> viewModel.openOptionsAt(index, item) }
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(title: String, message: String) {
    val theme = LocalArgosyTheme.current
    Column(modifier = Modifier.padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingXl)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = theme.textPrimary)
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = theme.textDim)
    }
}

@Composable
private fun EmptyTab(tab: MusicStatsTab) {
    when (tab) {
        MusicStatsTab.FLASHBACK -> MusicMessageState(
            icon = Icons.Outlined.History,
            title = stringResource(R.string.music_stats_flashback_empty_title),
            message = stringResource(R.string.music_stats_flashback_empty_message)
        )
        MusicStatsTab.GAMES -> MusicMessageState(
            icon = Icons.Outlined.SportsEsports,
            title = stringResource(R.string.music_stats_games_empty_title),
            message = stringResource(R.string.music_stats_games_empty_message)
        )
        MusicStatsTab.MONTH -> Unit
    }
}

@Composable
private fun statsFooterHints(uiState: MusicStatsUiState, selectSwaps: Boolean): List<Pair<InputButton, String>> {
    val views = stringResource(R.string.music_stats_footer_views)
    val play = stringResource(R.string.music_stats_footer_play)
    val options = stringResource(R.string.music_stats_footer_options)
    val swapScreens = stringResource(R.string.music_stats_footer_swap_screens)
    return buildList {
        add(InputButton.LT_RT to views)
        if (uiState.canPlayFocused && !uiState.page.loadFailed) {
            add(InputButton.Y to play)
            if (!selectSwaps) add(InputButton.SELECT to options)
        }
        if (selectSwaps) add(InputButton.SELECT to swapScreens)
    }
}

@Composable
private fun StatsOptionsModalHost(
    uiState: MusicStatsUiState,
    onMove: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentMove by rememberUpdatedState(onMove)
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val handler = remember {
        MusicModalInput(move = { currentMove(it) }, confirm = { currentConfirm() }, back = { currentDismiss() })
    }
    val options = uiState.options
    ModalInputEffect(active = options != null, handler = handler)
    if (options == null) return
    val shelf = uiState.optionsShelf ?: return
    val theme = LocalArgosyTheme.current
    ModalScaffold(visible = true, onDismiss = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.spacingLg)) {
            Text(
                text = optionsTitle(shelf, options.itemIndex, uiState.yearMonth),
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Dimens.spacingLg))
            options.actions.forEachIndexed { index, action ->
                OptionItem(
                    icon = if (action == MusicStatsOptionAction.SHUFFLE) Icons.Filled.Shuffle else Icons.Filled.PlayArrow,
                    label = stringResource(action.labelRes),
                    isFocused = index == options.focusIndex,
                    onClick = {
                        onFocus(index)
                        onConfirm()
                    }
                )
            }
        }
    }
}

@Composable
private fun optionsTitle(shelf: MusicStatsShelf, itemIndex: Int, yearMonth: Int): String = when (shelf.kind) {
    MusicStatsShelfKind.ALBUMS -> shelf.albums.getOrNull(itemIndex)?.let { it.album?.title ?: it.stat.title }
    MusicStatsShelfKind.ARTISTS -> shelf.artists.getOrNull(itemIndex)?.name
    else -> null
} ?: musicStatsShelfTitle(shelf.title, yearMonth)
