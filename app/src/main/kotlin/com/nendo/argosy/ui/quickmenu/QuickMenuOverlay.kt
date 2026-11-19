package com.nendo.argosy.ui.quickmenu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.flowWithLifecycle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.LocalRunningGames
import com.nendo.argosy.ui.components.EnumPickerModal
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.GameActionEntry
import com.nendo.argosy.ui.components.GameActionsMenu
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.quickmenu.components.QuickMenuContent
import com.nendo.argosy.ui.quickmenu.components.QuickMenuContentActions
import com.nendo.argosy.ui.quickmenu.components.QuickMenuTabStrip
import com.nendo.argosy.ui.quickmenu.components.TabStripPlacement
import com.nendo.argosy.ui.quickmenu.components.dismissOnSwipeDown
import com.nendo.argosy.ui.quickmenu.components.labelRes
import com.nendo.argosy.ui.quickmenu.components.sectionSwipe
import com.nendo.argosy.ui.screens.search.searchUsesPresentation
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.motionEnter
import com.nendo.argosy.ui.theme.motionExit
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.observeTouchDown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull

private const val QUICK_MENU_SLOT_OWNER = "quick_menu"

private val QuickMenuTokens = ComponentDefaults.QuickMenu

@Composable
fun QuickMenuOverlay(
    viewModel: QuickMenuViewModel,
    onGameSelect: (Long) -> Unit,
    onGamePlay: (Long) -> Unit,
    onOpenSearch: (String?) -> Unit,
    closeQuickMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val manager = DualScreenManagerHolder.instance
    val presentationUp by (manager?.presentationShowsHints ?: remember { MutableStateFlow(false) }).collectAsState()
    val shownSlot by (
        manager?.presentationSlot ?: remember { MutableStateFlow<PresentationSlot>(PresentationSlot.Fallback) }
        ).collectAsState()
    val usesPresentation = searchUsesPresentation(presentationUp, shownSlot)
    val aspectRatioClass = LocalUiScale.current.aspectRatioClass
    val formFactor = when {
        usesPresentation -> QuickMenuFormFactor.DUAL_SCREEN
        aspectRatioClass == AspectRatioClass.TALL || aspectRatioClass == AspectRatioClass.ULTRA_TALL -> QuickMenuFormFactor.PORTRAIT
        else -> QuickMenuFormFactor.LANDSCAPE
    }
    LaunchedEffect(formFactor) { viewModel.setFormFactor(formFactor) }
    val downloadIndicators by viewModel.downloadIndicators.collectAsState()

    val handler = remember(viewModel, onGameSelect, onGamePlay, onOpenSearch, closeQuickMenu) {
        QuickMenuInputHandler(viewModel, onGameSelect, onGamePlay, onOpenSearch, closeQuickMenu)
    }
    val primary: () -> Unit = {
        val state = viewModel.uiState.value
        state.focusedGame()?.let { game ->
            if (state.selectedOrb.confirmPlays) onGamePlay(game.id) else onGameSelect(game.id)
        }
    }

    val hints = quickMenuHints(uiState)
    val onHintClick: (InputButton) -> Unit = { button ->
        when (button) {
            InputButton.X -> handler.onContextMenu()
            InputButton.Y -> handler.onSecondaryAction()
            else -> Unit
        }
    }
    if (uiState.isVisible) {
        FooterHints(
            hints = if (formFactor == QuickMenuFormFactor.PORTRAIT) emptyList() else hints,
            onHintClick = onHintClick
        )
        if (manager != null && presentationUp) {
            QuickMenuOnPresentation(viewModel = viewModel, uiState = uiState, onPrimary = primary)
        }
    }

    val actions = remember(viewModel, handler) { contentActions(viewModel, handler, primary, onGameSelect) }
    val onTabClick: (QuickMenuOrb) -> Unit = { orb -> viewModel.selectOrb(orb) }
    val tabStrip: @Composable (TabStripPlacement) -> Unit = { placement ->
        QuickMenuTabStrip(
            selectedOrb = uiState.selectedOrb,
            placement = placement,
            showGlyphs = uiState.showsCursor,
            showRunningDot = uiState.hasRunningGame,
            onTabClick = onTabClick,
            onPrev = { viewModel.moveSection(-1) },
            onNext = { viewModel.moveSection(1) }
        )
    }
    val content: @Composable (Modifier) -> Unit = { contentModifier ->
        QuickMenuContent(
            uiState = uiState,
            downloadIndicators = downloadIndicators,
            actions = actions,
            modifier = contentModifier.sectionSwipe(viewModel::moveSection)
        )
    }
    val touchModifier = Modifier.observeTouchDown { viewModel.enterTouchMode() }

    Box(modifier = modifier.fillMaxSize()) {
        when (formFactor) {
            QuickMenuFormFactor.DUAL_SCREEN -> AnimatedVisibility(
                visible = uiState.isVisible,
                enter = fadeIn(tween(Motion.durationContent)),
                exit = fadeOut(tween(Motion.durationContent)),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(touchModifier)
                        .dismissOnSwipeDown(closeQuickMenu)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    tabStrip(TabStripPlacement.TOP)
                    content(Modifier.fillMaxWidth().weight(1f))
                }
            }
            QuickMenuFormFactor.LANDSCAPE -> QuickMenuSheet(
                isVisible = uiState.isVisible,
                onDismiss = closeQuickMenu,
                modifier = touchModifier
            ) {
                tabStrip(TabStripPlacement.TOP)
                content(Modifier.fillMaxWidth().weight(1f))
            }
            QuickMenuFormFactor.PORTRAIT -> AnimatedVisibility(
                visible = uiState.isVisible,
                enter = motionEnter(slideInVertically(tween(Motion.durationSlide)) { it }),
                exit = motionExit(slideOutVertically(tween(Motion.durationSlide)) { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .then(touchModifier)
                        .dismissOnSwipeDown(closeQuickMenu)
                        .clip(RoundedCornerShape(topStart = Dimens.radiusPanel, topEnd = Dimens.radiusPanel))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickableNoFocus {}
                        .navigationBarsPadding()
                ) {
                    DragHandle()
                    content(Modifier.fillMaxWidth().weight(1f))
                    if (hints.isNotEmpty()) FooterBar(hints = hints, onHintClick = onHintClick)
                    tabStrip(TabStripPlacement.BOTTOM)
                }
            }
        }
        if (uiState.isVisible) {
            QuickMenuPickers(uiState = uiState, viewModel = viewModel)
            uiState.actionsMenu?.let { menu ->
                QuickMenuActionsMenu(menu = menu, onAction = { action -> handler.performAction(menu, action) }, onDismiss = viewModel::closeActions)
            }
        }
    }
}

private fun contentActions(
    viewModel: QuickMenuViewModel,
    handler: QuickMenuInputHandler,
    primary: () -> Unit,
    onGameSelect: (Long) -> Unit
): QuickMenuContentActions = QuickMenuContentActions(
    onItemTap = { index ->
        viewModel.focusAt(index)
        val state = viewModel.uiState.value
        if (state.selectedOrb == QuickMenuOrb.SEARCH) handler.confirmSearch(state, index) else primary()
    },
    onItemLongPress = { index ->
        viewModel.focusAt(index)
        viewModel.uiState.value.focusedGame()?.let(viewModel::openActions)
    },
    onHeaderCycle = { delta ->
        viewModel.focusHeader()
        when (viewModel.uiState.value.selectedOrb) {
            QuickMenuOrb.MOST_PLAYED -> viewModel.cycleMostPlayedRange(delta)
            QuickMenuOrb.FAVORITES -> viewModel.cycleFavoritesSort(delta)
            else -> Unit
        }
    },
    onHeaderOpen = {
        viewModel.focusHeader()
        when (viewModel.uiState.value.selectedOrb) {
            QuickMenuOrb.MOST_PLAYED -> viewModel.openPicker(QuickMenuPicker.RANKING)
            QuickMenuOrb.FAVORITES -> viewModel.openPicker(QuickMenuPicker.SORT)
            else -> Unit
        }
    },
    onPrimary = primary,
    onRandomStop = { stop ->
        viewModel.focusRandom(stop)
        handler.confirmRandom(viewModel.uiState.value, stop)
    },
    onRandomHistoryTap = viewModel::pickRandom,
    onRandomCyclePlatform = { delta ->
        viewModel.focusRandom(RandomStop.PLATFORM)
        viewModel.cycleRandomPlatform(delta)
    },
    onRandomCoverTap = { viewModel.uiState.value.random.current?.let { onGameSelect(it.id) } },
    onSearchResultTap = onGameSelect
)

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = QuickMenuTokens.dragHandleWidthDp.dp, height = QuickMenuTokens.dragHandleHeightDp.dp)
                .clip(RoundedCornerShape(Dimens.radiusPill))
                .background(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
private fun QuickMenuSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrimBase = if (LocalLauncherTheme.current.isDarkTheme) Color.Black else Color.White
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(Motion.durationSlide)),
            exit = fadeOut(tween(Motion.durationSlide)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimBase.copy(alpha = QuickMenuTokens.sheetScrimAlpha))
                    .clickableNoFocus(onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible = isVisible,
            enter = motionEnter(slideInVertically(tween(Motion.durationSlide)) { it }),
            exit = motionExit(slideOutVertically(tween(Motion.durationSlide)) { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(QuickMenuTokens.sheetHeightRatio)
        ) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .dismissOnSwipeDown(onDismiss)
                    .clip(RoundedCornerShape(topStart = Dimens.radiusPanel, topEnd = Dimens.radiusPanel))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickableNoFocus {}
                    .padding(top = Dimens.spacingSm, bottom = Dimens.footerClearance),
                content = content
            )
        }
    }
}

@Composable
private fun QuickMenuPickers(uiState: QuickMenuUiState, viewModel: QuickMenuViewModel) {
    val random = uiState.random
    val allPlatforms = stringResource(R.string.ui_quick_menu_random_platform_all)
    EnumPickerModal(
        title = stringResource(R.string.ui_quick_menu_random_platform_picker_title),
        options = remember(random.platforms, allPlatforms) { listOf(allPlatforms) + random.platforms.map { it.name } },
        selectedIndex = random.platformIndex,
        onSelect = { index ->
            viewModel.selectRandomPlatform(index)
            viewModel.closePicker()
        },
        onDismiss = viewModel::closePicker,
        visible = uiState.picker == QuickMenuPicker.PLATFORM
    )
    val rangeLabels = MostPlayedRange.entries.map { stringResource(it.labelRes) }
    EnumPickerModal(
        title = stringResource(R.string.ui_quick_menu_most_played_picker_title),
        options = rangeLabels,
        selectedIndex = uiState.mostPlayed.range.ordinal,
        onSelect = { index ->
            viewModel.selectMostPlayedRange(MostPlayedRange.entries[index])
            viewModel.closePicker()
        },
        onDismiss = viewModel::closePicker,
        visible = uiState.picker == QuickMenuPicker.RANKING
    )
    val sortLabels = FavoritesSort.entries.map { stringResource(it.labelRes) }
    EnumPickerModal(
        title = stringResource(R.string.ui_quick_menu_favorites_picker_title),
        options = sortLabels,
        selectedIndex = uiState.favorites.sort.ordinal,
        onSelect = { index ->
            viewModel.selectFavoritesSort(FavoritesSort.entries[index])
            viewModel.closePicker()
        },
        onDismiss = viewModel::closePicker,
        visible = uiState.picker == QuickMenuPicker.SORT
    )
}

@Composable
private fun QuickMenuActionsMenu(
    menu: GameActionsMenuState,
    onAction: (QuickMenuGameAction) -> Unit,
    onDismiss: () -> Unit
) {
    val game = menu.game
    val isRunning = LocalRunningGames.current.isRunning(game.id)
    val primaryLabel = stringResource(
        when {
            isRunning -> R.string.library_quickmenu_resume_game
            game.needsInstall -> R.string.library_quickmenu_install
            game.isDownloaded -> R.string.library_quickmenu_play
            else -> R.string.library_quickmenu_download
        }
    )
    val primaryIcon = when {
        game.needsInstall -> Icons.Default.InstallMobile
        game.isDownloaded || isRunning -> Icons.Default.PlayArrow
        else -> Icons.Default.Download
    }
    val favoriteLabel = stringResource(
        if (game.isFavorite) R.string.library_quickmenu_unfavorite else R.string.library_quickmenu_favorite
    )
    val detailsLabel = stringResource(R.string.library_quickmenu_details)
    val notInterestedLabel = stringResource(R.string.ui_quick_menu_actions_not_interested)
    val entries = menu.actions.map { action ->
        when (action) {
            QuickMenuGameAction.PRIMARY -> GameActionEntry(primaryIcon, primaryLabel) { onAction(action) }
            QuickMenuGameAction.FAVORITE -> GameActionEntry(
                if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                favoriteLabel
            ) { onAction(action) }
            QuickMenuGameAction.DETAILS -> GameActionEntry(Icons.Default.Info, detailsLabel) { onAction(action) }
            QuickMenuGameAction.NOT_INTERESTED -> GameActionEntry(Icons.Default.VisibilityOff, notInterestedLabel) { onAction(action) }
        }
    }
    GameActionsMenu(
        options = entries,
        dangerousOptions = emptyList(),
        focusIndex = menu.focusIndex,
        onDismiss = onDismiss
    )
}

@Composable
private fun QuickMenuOnPresentation(
    viewModel: QuickMenuViewModel,
    uiState: QuickMenuUiState,
    onPrimary: () -> Unit
) {
    val manager = DualScreenManagerHolder.instance ?: return
    val owner = remember(viewModel) { SlotOwner.of(QUICK_MENU_SLOT_OWNER, viewModel) }
    val presentationState = remember(uiState) { uiState.toPresentationState() }
    PresentOnCompanion(owner = owner, slot = PresentationSlot.QuickMenu(owner, presentationState))

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnPrimary by rememberUpdatedState(onPrimary)
    LaunchedEffect(manager, owner, lifecycle) {
        manager.presentationTap
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .filter { it.owner == owner }
            .collect { tap -> if (manager.consumePresentationTap(tap)) currentOnPrimary() }
    }
}

@Composable
private fun quickMenuHints(uiState: QuickMenuUiState): List<Pair<InputButton, String>> {
    val favoriteHint = stringResource(R.string.ui_quick_menu_hint_favorite)
    val unfavoriteHint = stringResource(R.string.ui_quick_menu_hint_unfavorite)
    val notInterestedHint = stringResource(R.string.ui_quick_menu_top_new_hint_not_interested)
    val restoreHint = stringResource(R.string.ui_quick_menu_top_new_hint_restore)
    val removeHint = stringResource(R.string.ui_quick_menu_favorites_hint_remove)
    val restoreFavoriteHint = stringResource(R.string.ui_quick_menu_favorites_hint_restore)
    val detailsHint = stringResource(R.string.ui_quick_menu_recent_hint_details)

    return buildList {
        if (uiState.isTouchMode || uiState.picker != null || uiState.actionsMenu != null) return@buildList
        val game = uiState.actionGame() ?: return@buildList
        val favorite = InputButton.Y to if (game.isFavorite) unfavoriteHint else favoriteHint
        when (uiState.selectedOrb) {
            QuickMenuOrb.SEARCH, QuickMenuOrb.RANDOM -> Unit
            QuickMenuOrb.MOST_PLAYED -> add(favorite)
            QuickMenuOrb.TOP_UNPLAYED -> {
                add(favorite)
                add(InputButton.X to if (game.id in uiState.topNew.dismissedIds) restoreHint else notInterestedHint)
            }
            QuickMenuOrb.RECENT -> {
                add(favorite)
                add(InputButton.X to detailsHint)
            }
            QuickMenuOrb.FAVORITES ->
                add(InputButton.Y to if (game.id in uiState.favorites.removedIds) restoreFavoriteHint else removeHint)
        }
    }
}
