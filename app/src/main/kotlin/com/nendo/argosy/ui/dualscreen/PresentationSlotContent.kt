package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.ScreenDimmerPreferences
import com.nendo.argosy.ui.toScreenDimmerPreferences
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.toRunningGamesUi
import com.nendo.argosy.ui.components.FooterBarWithState
import com.nendo.argosy.ui.components.FooterHintItem
import com.nendo.argosy.ui.components.LocalFrostedBackdrop
import com.nendo.argosy.ui.components.rememberFrostedBackdrop
import com.nendo.argosy.ui.components.frostedBackdropSource
import com.nendo.argosy.ui.components.HomeLayoutPreview
import com.nendo.argosy.ui.components.ScreenNumberBadge
import com.nendo.argosy.ui.components.ScreenDimmerOverlay
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TIMELINE_SCROLL_DELAY_MS = 1500L
private const val PLAY_SHARE_COLUMNS = 2
private const val PLAY_SHARE_TOP_GAMES = 2

@Composable
fun PresentationSlotContent(
    slot: PresentationSlot,
    showControlHints: Boolean = true,
    showsNotifications: Boolean = false
) {
    val manager = com.nendo.argosy.DualScreenManagerHolder.instance
    val dimmerPrefs = remember(manager) {
        manager?.preferencesRepository?.userPreferences
            ?.map { it.toScreenDimmerPreferences() }?.distinctUntilChanged()
    }?.collectAsState(ScreenDimmerPreferences())?.value ?: ScreenDimmerPreferences()
    val lastActivity = manager?.lastUserActivityAtMs?.collectAsState()?.value ?: 0L
    val gameActive = manager?.swappedIsGameActive?.collectAsState()?.value ?: false
    val mediaPlayback = manager?.mediaPlayback?.collectAsState()?.value
    val wizardActive = manager?.isWizardActive?.collectAsState()?.value ?: false
    val artSource = com.nendo.argosy.ui.common.LocalImageCacheManager.current
        ?: manager?.imageCacheManager
    val frostedBackdrop = rememberFrostedBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalFrostedBackdrop provides frostedBackdrop,
        com.nendo.argosy.ui.common.LocalImageCacheManager provides artSource
    ) {
        ScreenDimmerOverlay(
            enabled = dimmerPrefs.enabled && !gameActive && mediaPlayback == null && !wizardActive,
            timeoutMs = dimmerPrefs.timeoutMinutes * 60_000L,
            dimLevel = dimmerPrefs.level / 100f,
            lastActivityAtMs = lastActivity,
            onWake = { manager?.notifyUserActivity("presentationDimmerTap") }
        ) {
            PresentationSlotBody(slot, showControlHints, showsNotifications)
        }
    }
}

@Composable
private fun PresentationSlotBody(
    slot: PresentationSlot,
    showControlHints: Boolean,
    showsNotifications: Boolean
) {
    val density = LocalDensity.current
    val manager = com.nendo.argosy.DualScreenManagerHolder.instance
    val hints = manager?.controlHints?.collectAsState()?.value.orEmpty()
    val gameDisplayId = manager?.emulatorDisplay?.collectAsState()?.value
    val displayId = ContextCompat.getDisplayOrDefault(LocalContext.current).displayId
    var measuredHintsHeight by remember { mutableStateOf(0.dp) }
    val hintsHeight = if (!showControlHints || hints.isEmpty()) 0.dp else measuredHintsHeight
    val recapBlur = maxOf(
        com.nendo.argosy.ui.components.launchRecapBackdropBlur(),
        com.nendo.argosy.ui.components.recap.monthlyRecapBackdropBlur()
    ).takeIf { slot !is PresentationSlot.InGame } ?: 0.dp
    var videoPlaying by remember { mutableStateOf(false) }
    val chromeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (videoPlaying) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(
            com.nendo.argosy.ui.theme.generated.ComponentDefaults.GameShowcase.backdropCrossfadeMs
        ),
        label = "presentationChrome"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        PresentationSlotScene(
            slot = slot,
            hintsHeight = hintsHeight,
            onVideoPlayingChange = { videoPlaying = it },
            modifier = Modifier
                .fillMaxSize()
                .then(if (recapBlur > 0.dp) Modifier.blur(recapBlur) else Modifier)
        )
        if (slot !is PresentationSlot.InGame) {
            com.nendo.argosy.ui.components.SystemStatusBar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spacingLg)
                    .graphicsLayer { alpha = chromeAlpha }
            )
            if (showControlHints && hints.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = chromeAlpha }
                        .onSizeChanged { measuredHintsHeight = with(density) { it.height.toDp() } }
                ) {
                    PresentationControlHints(hints)
                }
            }
        }
        com.nendo.argosy.ui.components.LaunchVeilMirror(
            gameDisplayId = gameDisplayId,
            displayId = displayId
        )
        if (slot !is PresentationSlot.InGame) {
            com.nendo.argosy.ui.components.recap.MonthlyRecapMirror()
            com.nendo.argosy.ui.components.LaunchOverlayMirror()
        }
        if (showsNotifications && manager != null) {
            val mutedKeys by manager.mutedNotificationKeys.collectAsState()
            com.nendo.argosy.core.notification.NotificationHost(
                manager = manager.notificationManager,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = hintsHeight),
                mutedKeys = mutedKeys
            )
        }
    }
}

@Composable
private fun PresentationSlotScene(
    slot: PresentationSlot,
    hintsHeight: Dp,
    onVideoPlayingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val frostedBackdrop = LocalFrostedBackdrop.current
    Box(modifier = modifier) {
        if (slot !is PresentationSlot.Detail) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .then(if (frostedBackdrop != null) Modifier.frostedBackdropSource(frostedBackdrop) else Modifier)
                    .surfaceBackdrop(BackdropRole.WALLPAPER)
            )
        }
        when (slot) {
            PresentationSlot.Fallback -> Unit
            is PresentationSlot.HomeLayoutPreview -> Box(
                modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
                contentAlignment = Alignment.Center
            ) {
                HomeLayoutPreview(settings = slot.settings, modifier = Modifier.fillMaxWidth())
            }
            is PresentationSlot.Setup ->
                com.nendo.argosy.ui.screens.setup.components.SetupCompanion(state = slot.state)
            is PresentationSlot.PlayTime -> PlayTimeSlot(slot)
            is PresentationSlot.PlayTimeline -> PlayTimelineSlot(slot)
            is PresentationSlot.PlayShare -> PlayShareSlot(slot)
            is PresentationSlot.Breakdown -> BreakdownSlot(slot)
            is PresentationSlot.Detail -> {
                val style = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.presentationStyle?.collectAsState()?.value
                    ?: com.nendo.argosy.domain.model.PresentationStyle()
                val backgroundBlur = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.presentationBackgroundBlur?.collectAsState()?.value ?: 0
                val runningSnapshot = com.nendo.argosy.DualScreenManagerHolder.instance
                    ?.runningGames?.state?.collectAsState()?.value
                val running = remember(runningSnapshot) {
                    runningSnapshot?.toRunningGamesUi() ?: com.nendo.argosy.ui.common.RunningGamesUi()
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    com.nendo.argosy.ui.common.LocalRunningGames provides running
                ) {
                    CompanionDetailScreen(
                        detail = slot.detail,
                        style = style,
                        backgroundBlur = backgroundBlur,
                        bottomInset = hintsHeight,
                        onVideoPlayingChange = onVideoPlayingChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            is PresentationSlot.PlatformShowcase -> PlatformShowcaseContent(slot)
            is PresentationSlot.SaveGame -> SaveGameSlot(slot, bottomInset = hintsHeight)
            is PresentationSlot.MediaPlayback ->
                com.nendo.argosy.ui.dualscreen.media.CompanionMediaPanel(slot.itemId, bottomInset = hintsHeight)
            is PresentationSlot.InGame -> {
                val manager = com.nendo.argosy.DualScreenManagerHolder.instance ?: return@Box
                val bar = rememberInGameAppBarState()
                val controls by manager.sessionControls.collectAsState()
                val actions = remember(manager) { dashboardActions(manager) }
                com.nendo.argosy.ui.dualscreen.dashboard.InGameDashboard(
                    state = slot.state,
                    controls = controls,
                    achievements = slot.achievements,
                    sessionTimer = manager.swappedSessionTimer,
                    reader = manager.dashboardReader,
                    actions = actions,
                    appBar = { modifier -> if (bar != null) InGameAppBar(bar, modifier) }
                )
            }
        }
    }
}

@Composable
internal fun PresentationControlHints(hints: List<CompanionHint>) {
    val items = remember(hints) { hints.map { FooterHintItem(it.button, it.label) } }
    val forced = remember(hints) { hints.any { it.forced } }
    FooterBarWithState(hints = items, forceVisible = forced)
}

private fun dashboardActions(manager: com.nendo.argosy.DualScreenManager) =
    com.nendo.argosy.ui.dualscreen.dashboard.DashboardActions(
        onQuickSave = { manager.sessionQuickActions?.quickSave() },
        onLoadState = { manager.sessionQuickActions?.loadState(it.slotNumber, it.savedAtMillis) },
        onScreenshot = { manager.sessionQuickActions?.screenshot() },
        onOpenCheats = { manager.sessionQuickActions?.openCheats() },
        onOpenSettings = { manager.sessionQuickActions?.openGameSettings() },
        onQuit = { manager.quitSession() },
        onOpenDocument = { manager.openDashboardDocument(it) },
        onNextDisc = { manager.streamDashboardActions?.nextDisc() }
    )

private data class InGameAppBarState(
    val manager: com.nendo.argosy.DualScreenManager,
    val apps: List<String>,
    val displays: List<com.nendo.argosy.ui.components.DisplayFocusTarget>,
    val pickerOpen: Boolean,
    val pickerIndex: Int,
    val canSwapRoles: Boolean
)

@Composable
private fun rememberInGameAppBarState(): InGameAppBarState? {
    val manager = com.nendo.argosy.DualScreenManagerHolder.instance ?: return null
    val apps by manager.homeAppsFlow.collectAsState(initial = manager.homeAppsList)
    val pickerOpen by manager.focusPickerOpen.collectAsState()
    val pickerIndex by manager.focusPickerIndex.collectAsState()
    val canSwapRoles by manager.liveSwapAvailable.collectAsState()
    val displays = remember(pickerOpen) {
        manager.focusableDisplays().map { screen ->
            com.nendo.argosy.ui.components.DisplayFocusTarget(screen.displayId, screen.number)
        }
    }
    if (apps.isEmpty() && displays.size <= 1 && !canSwapRoles) return null
    return InGameAppBarState(manager, apps, displays, pickerOpen, pickerIndex, canSwapRoles)
}

@Composable
private fun InGameAppBar(state: InGameAppBarState, modifier: Modifier = Modifier) {
    val manager = state.manager
    val reorder by manager.dockReorder.collectAsState()
    var menuPackage by remember { mutableStateOf<String?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var toolIndex by remember { mutableStateOf(1) }
    var drawerApps by remember {
        mutableStateOf(emptyList<com.nendo.argosy.ui.components.AppDrawerEntry>())
    }

    LaunchedEffect(drawerOpen) {
        if (!drawerOpen || drawerApps.isNotEmpty()) return@LaunchedEffect
        drawerApps = manager.installedAppLabels().map { (packageName, label) ->
            com.nendo.argosy.ui.components.AppDrawerEntry(packageName, label)
        }
    }

    com.nendo.argosy.ui.components.CompanionAppBar(
        apps = state.apps,
        onAppClick = { manager.launchCompanionApp(it) },
        reorder = reorder,
        onReorderStart = { manager.beginDockReorder(it, false) },
        onReorderTarget = manager::targetDockReorder,
        onReorderSave = manager::saveDockReorder,
        onReorderCancel = manager::cancelDockReorder,
        onAppHold = { packageName -> if (state.displays.size > 1) menuPackage = packageName },
        focusedIndex = com.nendo.argosy.ui.components.APP_BAR_NOTHING_FOCUSED,
        onOpenDrawer = { drawerOpen = true },
        focusDisplays = state.displays,
        focusPickerOpen = state.pickerOpen,
        focusPickerIndex = state.pickerIndex,
        onFocusPickerToggle = {
            if (state.pickerOpen) manager.closeFocusPicker() else manager.openFocusPicker()
        },
        onFocusPickerMove = manager::moveFocusPicker,
        onFocusDisplay = { displayId ->
            manager.focusDisplay(displayId)
            manager.closeFocusPicker()
        },
        onSwapRoles = if (state.canSwapRoles) {
            { manager.swapRoles() }
        } else {
            null
        },
        toolsOpen = toolsOpen,
        toolIndex = toolIndex,
        onToolsToggle = { toolsOpen = !toolsOpen; toolIndex = 1 },
        onToolFocus = { toolIndex = it },
        onToolsDismiss = { toolsOpen = false },
        controllerInputEnabled = false,
        modifier = modifier.padding(
            start = Dimens.spacingMd,
            top = Dimens.spacingMd,
            end = Dimens.spacingMd,
            bottom = ComponentDefaults.Carousel.headerEdgePaddingDp.dp
        )
    )

    if (drawerOpen) {
        com.nendo.argosy.ui.components.CompanionAppDrawer(
            apps = drawerApps,
            onLaunch = { packageName ->
                drawerOpen = false
                manager.launchCompanionApp(packageName)
            },
            onLongPress = if (state.displays.size > 1) {
                { packageName ->
                    drawerOpen = false
                    menuPackage = packageName
                }
            } else {
                null
            },
            onDismiss = { drawerOpen = false }
        )
    }

    menuPackage?.let { packageName ->
        val rows = remember(packageName, state.displays) {
            manager.appMenuRowsFor(manager.focusableDisplays())
        }
        var pinned by remember(packageName) { mutableStateOf(false) }
        var hidden by remember(packageName) { mutableStateOf(false) }
        LaunchedEffect(packageName) {
            pinned = manager.isAppPinned(packageName)
            hidden = manager.isAppHidden(packageName)
        }
        com.nendo.argosy.ui.components.AppLaunchMenu(
            appLabel = manager.appLabel(packageName),
            rows = rows,
            focusIndex = -1,
            isAppHidden = hidden,
            isOnSecondaryHome = pinned,
            onSelect = { index ->
                val row = rows.getOrNull(index)
                menuPackage = null
                if (row != null) manager.runAppMenuRow(packageName, row)
            },
            onDismiss = { menuPackage = null }
        )
    }
}

@Composable
private fun PlayShareSlot(slot: PresentationSlot.PlayShare) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = slot.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = theme.textPrimary
                )
                Text(
                    text = slot.subtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textDim
                )
            }
            Text(
                text = slot.totalLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = theme.focusAccent
            )
        }
        slot.rows.chunked(PLAY_SHARE_COLUMNS).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)) {
                pair.forEach { row ->
                    PlayShareCard(row = row, modifier = Modifier.weight(1f))
                }
                repeat(PLAY_SHARE_COLUMNS - pair.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayShareCard(row: PlayShareRow, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusPanel))
            .background(theme.surfaceRaised)
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            row.iconModel?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(Dimens.iconLg)
                )
            }
            if (row.showsCover) {
                Box(
                    modifier = Modifier
                        .height(Dimens.timelineCoverHeight)
                        .aspectRatio(COVER_ASPECT)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(theme.surfaceBase)
                ) {
                    AsyncImage(
                        model = rememberFileImageModel(row.coverPath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = row.valueLabel,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary
            )
            Text(
                text = row.shareLabel,
                style = MaterialTheme.typography.bodySmall,
                color = theme.textDim
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ComponentDefaults.VolumeMeter.height.dp)
                .clip(RoundedCornerShape(ComponentDefaults.VolumeMeter.radius.dp))
                .background(theme.surfaceBase)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(ComponentDefaults.VolumeMeter.radius.dp))
                    .background(row.color)
            )
        }
        if (row.topGames.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                row.topGames.forEach { game ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .height(Dimens.timelineCoverHeight)
                                .aspectRatio(COVER_ASPECT)
                                .clip(RoundedCornerShape(Dimens.radiusSm))
                                .background(theme.surfaceBase)
                        ) {
                            AsyncImage(
                                model = rememberFileImageModel(game.coverPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = game.detail,
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.textDim
                            )
                        }
                    }
                }
                repeat(PLAY_SHARE_TOP_GAMES - row.topGames.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayTimelineSlot(slot: PresentationSlot.PlayTimeline) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()
    LaunchedEffect(slot.selectedIndex, slot.dots.size) {
        if (slot.selectedIndex in slot.dots.indices) {
            listState.animateScrollToItemCentered(slot.selectedIndex)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = slot.dayLabel,
                style = MaterialTheme.typography.displaySmall,
                color = theme.textPrimary
            )
            slot.dayTotal?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    color = theme.focusAccent
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(Dimens.timelineTrackHeight)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.timelineDotSelected / 2)
                    .height(Dimens.borderMedium)
                    .background(theme.surfaceRaised)
            )
            LazyRow(
                state = listState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
            ) {
                itemsIndexed(slot.dots) { index, dot ->
                    val selected = index == slot.selectedIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                    ) {
                        Box(
                            modifier = Modifier.size(Dimens.timelineDotSelected),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(theme.surfaceBase)
                                        .border(Dimens.borderMedium, theme.focusAccent, CircleShape)
                                )
                            }
                            if (dot.hasActivity) {
                                Box(
                                    modifier = Modifier
                                        .size(Dimens.timelineDotActive)
                                        .clip(CircleShape)
                                        .background(dot.color)
                                )
                            }
                        }
                        Text(
                            text = dot.label.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim
                        )
                    }
                }
            }
        }
        val gamesState = rememberLazyListState()
        LaunchedEffect(slot.games, slot.selectedIndex) {
            gamesState.scrollToItem(0)
            while (true) {
                delay(TIMELINE_SCROLL_DELAY_MS)
                if (!gamesState.canScrollForward) {
                    if (gamesState.firstVisibleItemIndex == 0) continue
                    gamesState.animateScrollToItem(0)
                } else {
                    gamesState.animateScrollToItem(gamesState.firstVisibleItemIndex + 1)
                }
            }
        }
        val selectedDotCenter by remember(slot.selectedIndex) {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == slot.selectedIndex }
                    ?.let { it.offset + it.size / 2 }
            }
        }
        if (slot.games.isEmpty()) return@Column
        val cardWidth = Dimens.modalWidthXl
        val stemWidth = Dimens.borderMedium
        val stemHeight = Dimens.spacingLg
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val trackWidthPx = with(density) { maxWidth.roundToPx() }
            val cardWidthPx = with(density) { cardWidth.roundToPx() }
            val dotCenterPx = selectedDotCenter ?: (trackWidthPx / 2)
            val cardStartPx = (dotCenterPx - cardWidthPx / 2)
                .coerceIn(0, (trackWidthPx - cardWidthPx).coerceAtLeast(0))
            val stemStartPx = dotCenterPx - with(density) { stemWidth.roundToPx() } / 2
            val stemRisePx = with(density) {
                (Dimens.spacingLg + Dimens.timelineTrackHeight - Dimens.timelineDotSelected).roundToPx()
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(stemStartPx, -stemRisePx) }
                    .width(stemWidth)
                    .height(stemHeight + with(density) { stemRisePx.toDp() })
                    .background(theme.surfaceRaised)
            )
            LazyColumn(
                state = gamesState,
                userScrollEnabled = false,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                contentPadding = PaddingValues(Dimens.spacingLg),
                modifier = Modifier
                    .offset { IntOffset(cardStartPx, with(density) { stemHeight.roundToPx() }) }
                    .width(cardWidth)
                    .clip(RoundedCornerShape(Dimens.radiusPanel))
                    .background(theme.surfaceRaised)
            ) {
                items(slot.games, key = { it.gameId }) { game ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = rememberFileImageModel(game.coverPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .height(Dimens.timelineCoverHeight)
                                .aspectRatio(COVER_ASPECT)
                                .clip(RoundedCornerShape(Dimens.radiusSm))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary
                            )
                            game.subtitle?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = theme.textDim
                                )
                            }
                        }
                        Text(
                            text = game.detail,
                            style = MaterialTheme.typography.titleMedium,
                            color = theme.textDim
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownSlot(slot: PresentationSlot.Breakdown) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Text(
            text = slot.title,
            style = MaterialTheme.typography.headlineMedium,
            color = theme.textPrimary
        )
        slot.subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textDim
            )
        }
        slot.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.textPrimary
                    )
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.textDim
                    )
                }
                val barShape = RoundedCornerShape(ComponentDefaults.VolumeMeter.radius.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ComponentDefaults.VolumeMeter.height.dp)
                        .clip(barShape)
                        .background(theme.surfaceRaised)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.fraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(barShape)
                            .background(row.color)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayTimeSlot(slot: PresentationSlot.PlayTime) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingLg),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = slot.dateLabel ?: slot.sectionLabel,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            items(slot.games, key = { it.gameId }) { game ->
                Column {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textPrimary
                    )
                    Text(
                        text = game.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.textDim
                    )
                }
            }
        }
    }
}
