package com.nendo.argosy.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import android.content.Intent
import com.nendo.argosy.ui.util.doubleTapNoFocus
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.libretro.LibretroActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nendo.argosy.ui.components.BackgroundSyncConflictDialog
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.components.FloatingNavBar
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.revealOnBottomEdgeTouch
import com.nendo.argosy.ui.components.FooterHost
import com.nendo.argosy.ui.components.FooterHostController
import com.nendo.argosy.ui.components.LocalFooterHost
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.ui.components.MainDrawer
import com.nendo.argosy.ui.components.QuickSettingsInputRouter
import com.nendo.argosy.ui.components.QuickSettingsPage
import com.nendo.argosy.ui.components.QuickSettingsPanel
import com.nendo.argosy.ui.components.friends.QuickFriendsInputHandler
import com.nendo.argosy.ui.components.friends.QuickFriendsModals
import com.nendo.argosy.ui.components.friends.QuickSettingsFriendsPage
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerInputHandler
import com.nendo.argosy.ui.components.musicplayer.MusicPlayerViewModel
import com.nendo.argosy.ui.components.musicplayer.QuickSettingsMusicPage
import com.nendo.argosy.ui.components.NetplayInviteModal
import com.nendo.argosy.ui.components.NetplayJoinModal
import com.nendo.argosy.ui.input.NetplayJoinInputHandler
import com.nendo.argosy.data.netplay.NetplayJoinState
import com.nendo.argosy.data.netplay.VerifySubState
import com.nendo.argosy.ui.components.CoreCrashModal
import com.nendo.argosy.ui.components.SaveConflictModal
import com.nendo.argosy.ui.components.ScreenDimmerOverlay
import com.nendo.argosy.ui.input.BackgroundConflictInputHandler
import com.nendo.argosy.ui.input.CapturingInputHandler
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.HapticPattern
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.UiShortcut
import com.nendo.argosy.ui.input.UiShortcutGate
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.navigation.NavGraph
import com.nendo.argosy.ui.navigation.NavRing
import com.nendo.argosy.ui.navigation.RouteRestore
import com.nendo.argosy.ui.navigation.concreteRoute
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.player.PlayerActivity
import com.nendo.argosy.ui.screens.player.PlayerArgs
import com.nendo.argosy.core.notification.NotificationHost
import com.nendo.argosy.ui.quickmenu.QuickMenuInputHandler
import com.nendo.argosy.ui.quickmenu.QuickMenuOverlay
import com.nendo.argosy.ui.quickmenu.QuickMenuViewModel
import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.gripReserveBottomInset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val KEY_SINK_RECLAIM_GRACE_MS = 250L
private const val NAV_READY_TIMEOUT_MS = 45_000L

@Composable
fun ArgosyApp(
    viewModel: ArgosyViewModel = launcherViewModel(),
    quickMenuViewModel: QuickMenuViewModel = launcherViewModel(),
    musicPlayerViewModel: MusicPlayerViewModel = launcherViewModel(),
    onStartupComplete: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val drawerUiState by viewModel.drawerUiState.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val isQuickSettingsOpen by viewModel.quickSettings.isOpen.collectAsState()
    val quickSettingsFocusIndex by viewModel.quickSettings.focusIndex.collectAsState()
    val quickSettingsPage by viewModel.quickSettings.page.collectAsState()
    val quickSettingsState by viewModel.quickSettings.state.collectAsState()
    val quickFriendsState by viewModel.quickFriends.state.collectAsState()
    val musicPlayerUiState by musicPlayerViewModel.uiState.collectAsState()
    val screenDimmerPrefs by viewModel.screenDimmerPreferences.collectAsState()
    val isEmulatorRunning by viewModel.isEmulatorRunning.collectAsState()
    val quickMenuState by quickMenuViewModel.uiState.collectAsState()
    val saveConflictInfo by viewModel.saveConflicts.saveConflictInfo.collectAsState()
    val saveConflictButtonIndex by viewModel.saveConflicts.saveConflictButtonIndex.collectAsState()
    val backgroundConflictInfo by viewModel.saveConflicts.backgroundConflictInfo.collectAsState()
    val backgroundConflictButtonIndex by viewModel.saveConflicts.backgroundConflictButtonIndex.collectAsState()
    val backgroundConflictSnapshot by viewModel.saveConflicts.backgroundConflictSnapshot.collectAsState()
    val coreCrashPrompt by viewModel.coreCrashController.prompt.collectAsState()
    val coreCrashFocusIndex by viewModel.coreCrashController.focusIndex.collectAsState()
    val coreCrashDownloading by viewModel.coreCrashController.downloading.collectAsState()
    val netplayInvitePrompt by viewModel.netplayInvitePrompt.collectAsState()
    val netplayInviteFocusIndex by viewModel.netplayInviteFocusIndex.collectAsState()
    val netplayJoinState by viewModel.netplayJoinState.collectAsState()
    val navRingState by viewModel.navRingState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? com.nendo.argosy.MainActivity
    val dsm = remember { com.nendo.argosy.DualScreenManagerHolder.instance }

    val companionActive by dsm?.isCompanionActive?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isDualScreenDevice by dsm?.isDualScreenDevice?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isRolesSwapped by dsm?.isRolesSwapped?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isOnHomeScreen = currentRoute == Screen.Home.route

    val isDualActive = isDualScreenDevice && companionActive
    LaunchedEffect(isDualActive, isRolesSwapped) {
        viewModel.quickSettings.setDualScreen(active = isDualActive, rolesSwapped = isRolesSwapped)
    }

    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading) onStartupComplete()
    }

    val isOnWizard = currentRoute == Screen.FirstRun.route
    var wasOnWizard by remember { mutableStateOf(isOnWizard) }
    LaunchedEffect(uiState.isFirstRun, isOnWizard) {
        dsm?.broadcastWizardState(uiState.isFirstRun || isOnWizard)
        if (wasOnWizard && !isOnWizard) {
            viewModel.triggerPostWizardSync()
        }
        wasOnWizard = isOnWizard
    }

    LaunchedEffect(isOnHomeScreen) {
        activity?.isOnHomeScreen = isOnHomeScreen
        dsm?.setPrimaryOnHome(isOnHomeScreen)
    }

    val carriedRoute = remember { dsm?.primaryRoute?.value }
    LaunchedEffect(Unit) {
        val stack = carriedRoute?.let(RouteRestore::restoreStack).orEmpty()
        if (stack.isEmpty()) return@LaunchedEffect
        val entry = withTimeoutOrNull(NAV_READY_TIMEOUT_MS) {
            navController.currentBackStackEntryFlow.first()
        } ?: return@LaunchedEffect
        if (entry.destination.route != Screen.Home.route || navController.previousBackStackEntry != null) {
            return@LaunchedEffect
        }
        stack.forEach { navController.navigate(it) }
    }

    LaunchedEffect(navBackStackEntry) {
        navBackStackEntry?.let { dsm?.setPrimaryRoute(it.concreteRoute()) }
    }

    val handleDeepLink: suspend (android.net.Uri) -> Unit = { uri ->
        android.util.Log.d("ArgosyApp", "Handling deep link: $uri")
        val showDeepLinkNotice: (Int) -> Unit = { messageRes ->
            android.widget.Toast.makeText(context, messageRes, android.widget.Toast.LENGTH_LONG).show()
        }
        val awaitNavGraph: suspend () -> Boolean = {
            val ready = withTimeoutOrNull(NAV_READY_TIMEOUT_MS) {
                navController.currentBackStackEntryFlow.first()
            } != null
            if (!ready) {
                android.util.Log.w("ArgosyApp", "Nav graph not ready, dropping deep link $uri")
                showDeepLinkNotice(R.string.ui_deep_link_startup_timeout)
            }
            ready
        }
        if (uri.scheme == "argosy") {
            when (uri.host) {
                "game" -> {
                    val gameId = uri.lastPathSegment?.toLongOrNull()
                    if (gameId != null && awaitNavGraph()) {
                        navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                            launchSingleTop = true
                        }
                    }
                }
                "play" -> {
                    val gameId = uri.lastPathSegment?.toLongOrNull()
                    if (gameId != null && awaitNavGraph()) {
                        viewModel.initiateGameLaunch(gameId)
                        navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                            launchSingleTop = true
                        }
                    }
                }
                "apps" -> {
                    if (awaitNavGraph()) {
                        navController.navigate(Screen.Apps.route) {
                            launchSingleTop = true
                        }
                    }
                }
                "launch" -> {
                    val request = com.nendo.argosy.ui.deeplink.DeepLinkParser.parse(uri)
                    if (request == null) {
                        android.util.Log.w("ArgosyApp", "Deep link carried no target: $uri")
                    } else {
                        when (val outcome = viewModel.resolveDeepLinkLaunch(request)) {
                            is com.nendo.argosy.ui.deeplink.DeepLinkLaunch.Ready -> {
                                if (awaitNavGraph()) {
                                    navController.navigate(
                                        Screen.GameDetail.createRoute(outcome.gameId)
                                    ) {
                                        launchSingleTop = true
                                    }
                                    viewModel.awaitDeepLinkSyncReady()
                                    viewModel.initiateGameLaunch(
                                        outcome.gameId,
                                        outcome.channelName,
                                        origin = com.nendo.argosy.data.emulator.LaunchOrigin.EXTERNAL
                                    )
                                }
                            }
                            is com.nendo.argosy.ui.deeplink.DeepLinkLaunch.Failed -> {
                                showDeepLinkNotice(outcome.messageRes)
                            }
                        }
                    }
                }
            }
        }
    }
    val currentHandleDeepLink by rememberUpdatedState(handleDeepLink)
    LaunchedEffect(dsm) {
        val manager = dsm ?: return@LaunchedEffect
        manager.pendingDeepLink.filterNotNull().collect { uri ->
            if (manager.consumeDeepLink(uri)) currentHandleDeepLink(uri)
        }
    }

    // Drawer state - confirmStateChange handles swipe gestures synchronously
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val inputDispatcher = remember {
        InputDispatcher(
            hapticManager = viewModel.hapticManager,
            soundManager = viewModel.soundManager
        )
    }

    val unconfiguredScreenSet by (
        dsm?.unconfiguredScreenSet
            ?: remember { kotlinx.coroutines.flow.MutableStateFlow<String?>(null) }
        ).collectAsState()

    var screenSetPromptVisible by remember { mutableStateOf(false) }

    LaunchedEffect(unconfiguredScreenSet) {
        if (unconfiguredScreenSet == null) {
            screenSetPromptVisible = false
            return@LaunchedEffect
        }
        screenSetPromptVisible = true
        delay(com.nendo.argosy.core.notification.SCREEN_SET_PROMPT_MS)
        screenSetPromptVisible = false
        dsm?.clearUnconfiguredScreenSet()
    }

    val screenSetPromptHandler = remember(navController) {
        object : InputHandler {
            override fun onContextMenu(): InputResult {
                screenSetPromptVisible = false
                dsm?.clearUnconfiguredScreenSet()
                navController.navigate(
                    Screen.Settings.createRoute(section = SettingsSection.SCREENS.name)
                ) { launchSingleTop = true }
                return InputResult.HANDLED
            }
        }
    }

    LaunchedEffect(screenSetPromptVisible) {
        inputDispatcher.setInterceptHandler(
            if (screenSetPromptVisible) screenSetPromptHandler else null
        )
    }

    val presentationShowsHints by dsm?.presentationShowsHints?.collectAsState()
        ?: remember { mutableStateOf(false) }

    val footerHostController = remember { FooterHostController() }

    val rootFocusRequester = remember { FocusRequester() }
    var resumeCount by remember { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (dsm?.isOverlayFocused != true) {
            inputDispatcher.blockInputFor(200)
            inputDispatcher.resetToMainView()
            viewModel.resetAllModals()
            resumeCount++
        }
        viewModel.refreshControllerDetection()
        try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    // When dual-screen topology changes (role swap, companion attach/detach, game moves
    // between displays), drop any lingering modal/drawer state and clear the deferred
    // view subscription so the newly-active screen's input handler gets a clean slot.
    if (dsm != null) {
        val swappedGameActive by dsm.swappedIsGameActive.collectAsState()
        LaunchedEffect(isRolesSwapped, companionActive, swappedGameActive) {
            inputDispatcher.resetToMainView()
            inputDispatcher.clearPendingViewSubscription()
            if (dsm.isOverlayFocused) {
                dsm.isOverlayFocused = false
                dsm.controlCompanion?.onOverlayClosed()
            }
            viewModel.setDrawerOpen(false)
            viewModel.quickSettings.setOpen(false)
            quickMenuViewModel.hide()
        }
    }

    val startDestination = remember(uiState.isLoading) {
        when {
            uiState.isFirstRun -> Screen.FirstRun.route
            else -> Screen.Home.route
        }
    }

    val navigateFromDrawer: (String) -> Unit = remember {
        { route ->
            val current = navController.currentDestination?.route
            if (route != current) {
                navController.navigate(route) {
                    popUpTo(Screen.Home.route) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // Create drawer input handler
    val drawerInputHandler = remember {
        viewModel.createDrawerInputHandler(
            onNavigate = { route ->
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
                scope.launch { drawerState.close() }
                navigateFromDrawer(route)
            },
            onDismiss = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
            }
        )
    }

    // Synchronous drawer toggle - subscription must happen immediately, not via LaunchedEffect
    val openDrawer = remember(drawerInputHandler) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(drawerInputHandler)
            viewModel.setDrawerOpen(true)
            val parentRoute = navController.previousBackStackEntry?.destination?.route
            viewModel.initDrawerFocus(currentRoute, parentRoute)
            viewModel.onDrawerOpened()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val closeDrawer = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setDrawerOpen(false)
        }
    }

    // Quick settings input handler
    val openRommSignIn: () -> Unit = remember(viewModel, musicPlayerViewModel, inputDispatcher, navController) {
        {
            musicPlayerViewModel.closeBrowse()
            inputDispatcher.unsubscribeDrawer()
            viewModel.quickSettings.setOpen(false)
            navController.navigate(
                Screen.Settings.createRoute(section = SettingsSection.ROMM.name)
            ) { launchSingleTop = true }
        }
    }

    val openDeviceAccess: () -> Unit = remember(viewModel, inputDispatcher, navController) {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.quickSettings.setOpen(false)
            navController.navigate(
                Screen.Settings.createRoute(section = SettingsSection.PERMISSIONS.name)
            ) { launchSingleTop = true }
        }
    }

    val quickFriendsInputHandler = remember(viewModel, inputDispatcher) {
        QuickFriendsInputHandler(
            controller = viewModel.quickFriends,
            showQuayPass = { viewModel.quickSettings.state.value.isSocialLinked },
            quayPassEnabled = { viewModel.quickSettings.state.value.quayPassEnabled },
            wrapMode = { viewModel.uiState.value.menuWrapMode },
            onToggleQuayPass = { viewModel.quickSettings.toggleQuayPass() },
            onOpenProfile = { userId ->
                inputDispatcher.unsubscribeDrawer()
                viewModel.quickSettings.setOpen(false)
                navigateFromDrawer(Screen.UserProfile.createRoute(userId))
            },
            onEditAvatar = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.quickSettings.setOpen(false)
                navigateFromDrawer(Screen.AvatarDoodle.route)
            }
        )
    }

    val quickSettingsInputHandler = remember(
        viewModel, musicPlayerViewModel, inputDispatcher, openRommSignIn, openDeviceAccess, quickFriendsInputHandler
    ) {
        QuickSettingsInputRouter(
            panelHandler = viewModel.quickSettings.createInputHandler(
                onDismiss = {
                    inputDispatcher.unsubscribeDrawer()
                    viewModel.quickSettings.setOpen(false)
                },
                onOpenDeviceAccess = openDeviceAccess
            ),
            pageHandlers = mapOf(
                QuickSettingsPage.FRIENDS to quickFriendsInputHandler,
                QuickSettingsPage.MUSIC to MusicPlayerInputHandler(musicPlayerViewModel, openRommSignIn)
            ),
            activePage = { viewModel.quickSettings.activePage() }
        )
    }

    val musicPageVisible = isQuickSettingsOpen && quickSettingsPage == QuickSettingsPage.MUSIC
    LaunchedEffect(musicPageVisible) {
        if (!musicPageVisible) musicPlayerViewModel.onPageHidden()
    }

    val openQuickSettings = remember(quickSettingsInputHandler) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
            viewModel.quickSettings.setOpen(true)
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val closeQuickSettings = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.quickSettings.setOpen(false)
        }
    }

    val closeQuickMenu = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            quickMenuViewModel.hide()
        }
    }

    val playFromQuickMenu: (Long) -> Unit = remember(navController, closeQuickMenu) {
        { gameId ->
            closeQuickMenu()
            viewModel.initiateGameLaunch(gameId)
            navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                launchSingleTop = true
            }
        }
    }

    val quickMenuInputHandler = remember(quickMenuViewModel, navController, closeQuickMenu, playFromQuickMenu) {
        QuickMenuInputHandler(
            viewModel = quickMenuViewModel,
            onGameSelect = { gameId ->
                closeQuickMenu()
                navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                    launchSingleTop = true
                }
            },
            onGamePlay = playFromQuickMenu,
            onDismiss = { closeQuickMenu() }
        )
    }

    val openQuickMenu = remember(quickMenuInputHandler) {
        wizardGuard@{
            if (uiState.isFirstRun) return@wizardGuard
            if (isDrawerOpen) closeDrawer()
            if (isQuickSettingsOpen) closeQuickSettings()
            viewModel.hideNavBar()
            inputDispatcher.subscribeDrawer(quickMenuInputHandler)
            quickMenuViewModel.show()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val pendingOverlay by dsm?.pendingOverlayEvent?.collectAsState()
        ?: remember { mutableStateOf(null) }
    LaunchedEffect(pendingOverlay) {
        val eventName = pendingOverlay ?: return@LaunchedEffect
        when (eventName) {
            DualScreenManager.OVERLAY_QUICK_MENU -> openQuickMenu()
            DualScreenManager.OVERLAY_QUICK_SETTINGS -> openQuickSettings()
            else -> openDrawer()
        }
        dsm?.clearPendingOverlay()
    }

    val saveConflictInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onLeft(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.saveConflicts.moveSaveConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                viewModel.saveConflicts.confirmSaveConflict()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.saveConflicts.dismissSaveConflict()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onPrevSection() = InputResult.HANDLED
            override fun onNextSection() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onSecondaryAction() = InputResult.HANDLED
            override fun onContextMenu() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
        }
    }

    val backgroundConflictInputHandler = remember(viewModel) {
        BackgroundConflictInputHandler(
            moveFocus = viewModel.saveConflicts::moveBackgroundConflictFocus,
            confirm = viewModel.saveConflicts::confirmBackgroundConflict,
            skip = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.SKIP) }
        )
    }

    val steamDownloadPromptInputHandler = remember(viewModel) {
        object : CapturingInputHandler {
            override fun onLeft(): InputResult {
                viewModel.steamDownloadPromptController.moveFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.steamDownloadPromptController.moveFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.steamDownloadPromptController.moveFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.steamDownloadPromptController.moveFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                viewModel.steamDownloadPromptController.confirmFocused()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.steamDownloadPromptController.dismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val netplayInviteInputHandler = remember(viewModel) {
        object : CapturingInputHandler {
            override fun onLeft(): InputResult {
                viewModel.moveNetplayInviteFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.moveNetplayInviteFocus(1)
                return InputResult.HANDLED
            }
            override fun onUp(): InputResult {
                viewModel.moveNetplayInviteFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.moveNetplayInviteFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                if (viewModel.netplayInviteFocusIndex.value == 1) {
                    viewModel.acceptNetplayInvite()
                } else {
                    viewModel.dismissNetplayInvite()
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.dismissNetplayInvite()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val netplayJoinNeedsInput = netplayJoinState.let {
        it is NetplayJoinState.VerifyingGame &&
            (it.sub is VerifySubState.AmbiguousCandidates || it.sub is VerifySubState.HashMismatchVariants)
    }
    val netplayJoinModalActive = netplayJoinState !is NetplayJoinState.Idle &&
        netplayJoinState !is NetplayJoinState.Cancelled &&
        netplayJoinState !is NetplayJoinState.LaunchReady

    val netplayJoinInputHandler = remember(viewModel) {
        NetplayJoinInputHandler(
            service = viewModel.netplayJoinService(),
            onDismiss = { viewModel.cancelNetplayJoin() }
        )
    }

    val steamDownloadPrompt by viewModel.steamDownloadPromptController.prompt.collectAsState()

    val coreCrashInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onUp(): InputResult { viewModel.coreCrashController.moveFocus(-1); return InputResult.HANDLED }
            override fun onDown(): InputResult { viewModel.coreCrashController.moveFocus(1); return InputResult.HANDLED }
            override fun onLeft(): InputResult { viewModel.coreCrashController.moveFocus(-1); return InputResult.HANDLED }
            override fun onRight(): InputResult { viewModel.coreCrashController.moveFocus(1); return InputResult.HANDLED }
            override fun onConfirm(): InputResult {
                val dl = viewModel.coreCrashController.downloading.value
                if (dl?.done == true && !dl.failed) {
                    viewModel.launchFromCoreCrash()
                } else {
                    viewModel.coreCrashController.confirmFocused()
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.coreCrashController.dismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onMenu() = InputResult.HANDLED
            override fun onSelect() = InputResult.HANDLED
            override fun onPrevSection() = InputResult.HANDLED
            override fun onNextSection() = InputResult.HANDLED
            override fun onPrevTrigger() = InputResult.HANDLED
            override fun onNextTrigger() = InputResult.HANDLED
            override fun onSecondaryAction() = InputResult.HANDLED
            override fun onContextMenu() = InputResult.HANDLED
            override fun onLeftStickClick() = InputResult.HANDLED
            override fun onRightStickClick() = InputResult.HANDLED
        }
    }

    LaunchedEffect(coreCrashPrompt, saveConflictInfo, backgroundConflictInfo, netplayInvitePrompt, netplayJoinModalActive, netplayJoinNeedsInput, steamDownloadPrompt, resumeCount) {
        inputDispatcher.setCriticalHandler(
            when {
                coreCrashPrompt != null -> coreCrashInputHandler
                saveConflictInfo != null -> saveConflictInputHandler
                backgroundConflictInfo != null -> backgroundConflictInputHandler
                else -> null
            }
        )
        when {
            steamDownloadPrompt != null -> inputDispatcher.subscribeDrawer(steamDownloadPromptInputHandler)
            netplayJoinNeedsInput || netplayJoinModalActive -> inputDispatcher.subscribeDrawer(netplayJoinInputHandler)
            netplayInvitePrompt != null -> inputDispatcher.subscribeDrawer(netplayInviteInputHandler)
            else -> {
                val released = listOf(
                    steamDownloadPromptInputHandler,
                    netplayJoinInputHandler,
                    netplayInviteInputHandler
                ).any { inputDispatcher.releaseDrawer(it) }
                if (released) {
                    when {
                        viewModel.quickSettings.isOpen.value -> inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
                        viewModel.isDrawerOpen.value -> inputDispatcher.subscribeDrawer(drawerInputHandler)
                        quickMenuViewModel.uiState.value.isVisible -> inputDispatcher.subscribeDrawer(quickMenuInputHandler)
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.netplayInviteLaunch.collect { request ->
            context.startActivity(request.intent, request.options)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.coreCrashLaunch.collect { gameId ->
            navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                launchSingleTop = true
            }
            viewModel.initiateGameLaunch(gameId)
        }
    }

    LaunchedEffect(netplayJoinState) {
        val s = netplayJoinState
        if (s is NetplayJoinState.LaunchReady) {
            context.startActivity(s.intent, viewModel.launchOptionsFor(s.gameId))
            viewModel.resetNetplayJoin()
        }
    }


    // Block input during route transitions and sync route to dispatcher
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
        }
        inputDispatcher.setCurrentRoute(currentRoute)
    }

    // Gate Home button events - only emit when not on home screen
    LaunchedEffect(currentRoute) {
        val isHome = currentRoute?.startsWith(Screen.Home.route) == true
        viewModel.gamepadInputHandler.homeEventEnabled = !isHome
    }

    LaunchedEffect(currentRoute, uiState.isFirstRun, navRingState.destinations.isEmpty()) {
        if (!uiState.isFirstRun && viewModel.isNavRingRoute(currentRoute)) {
            viewModel.showNavBar()
        } else {
            viewModel.hideNavBar()
        }
    }

    // Sync ViewModel drawer state -> Compose drawer animation
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen && !drawerState.isOpen) {
            drawerState.open()
        } else if (!isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    // Notify companion when any upper overlay closes: stop forwarding + refocus lower screen
    val notifyOverlayClosed: () -> Unit = remember {
        {
            if (dsm != null && dsm.isOverlayFocused) {
                dsm.isOverlayFocused = false
                dsm.controlCompanion?.onOverlayClosed()
                dsm.controlCompanion?.refocusSelf()
            }
        }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasOpen = false
        viewModel.isDrawerOpen.collect { open ->
            if (wasOpen && !open) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasOpen = open
        }
    }

    // When returning to Home from Apps/Settings in dual-screen mode, refocus lower
    LaunchedEffect(isDualScreenDevice, companionActive) {
        if (!isDualScreenDevice || !companionActive) return@LaunchedEffect
        var wasOnHome = true
        snapshotFlow { navBackStackEntry?.destination?.route == Screen.Home.route }
            .collect { onHome ->
                if (!wasOnHome && onHome) {
                    notifyOverlayClosed()
                }
                wasOnHome = onHome
            }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasOpen = false
        viewModel.quickSettings.isOpen.collect { open ->
            if (wasOpen && !open) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasOpen = open
        }
    }

    LaunchedEffect(isDualScreenDevice) {
        if (!isDualScreenDevice) return@LaunchedEffect
        var wasVisible = false
        quickMenuViewModel.uiState.collect { state ->
            if (wasVisible && !state.isVisible) {
                val onHome = navController.currentDestination?.route == Screen.Home.route
                if (onHome) {
                    notifyOverlayClosed()
                } else {
                    dsm?.controlCompanion?.onBackgroundForward()
                }
            }
            wasVisible = state.isVisible
        }
    }

    // Sync Compose drawer state -> ViewModel (for scrim tap close)
    LaunchedEffect(drawerState.isOpen) {
        if (!drawerState.isOpen && isDrawerOpen) {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setDrawerOpen(false)
        }
    }

    // Block input during drawer transitions
    LaunchedEffect(isDrawerOpen) {
        inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
    }

    // Collect gamepad events (Menu toggles drawer, L3 toggles quick menu, R3 toggles quick settings)
    val inputLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(inputLifecycle) {
        viewModel.gamepadInputHandler.eventFlow().flowWithLifecycle(inputLifecycle).collect { input ->            val result = inputDispatcher.dispatch(input)
            val event = input.event
            val isBumper = event == GamepadEvent.PrevSection || event == GamepadEvent.NextSection
            if (!isBumper || inputDispatcher.hasActiveModal()) viewModel.hideNavBar()
            if (!result.handled && !inputDispatcher.hasCapturingOverlay()) {
                when (event) {
                    GamepadEvent.PrevSection, GamepadEvent.NextSection -> {
                        val delta = if (event == GamepadEvent.PrevSection) -1 else 1
                        val target = if (
                            !input.isRepeat &&
                            !uiState.isFirstRun &&
                            !isDrawerOpen &&
                            !isQuickSettingsOpen &&
                            !quickMenuState.isVisible
                        ) {
                            viewModel.navRingRouteFrom(navController.currentDestination?.route, delta)
                        } else {
                            null
                        }
                        if (target != null) {
                            viewModel.hapticManager.vibrate(HapticPattern.FOCUS_CHANGE)
                            viewModel.soundManager.play(SoundType.SECTION_CHANGE)
                            navigateFromDrawer(target)
                            viewModel.showNavBar()
                        }
                    }
                    GamepadEvent.Menu -> {
                        if (isDrawerOpen) {
                            closeDrawer()
                        } else {
                            if (isQuickSettingsOpen) closeQuickSettings()
                            if (quickMenuState.isVisible) closeQuickMenu()
                            openDrawer()
                        }
                    }
                    GamepadEvent.Left -> {
                        if (!input.isRepeat &&
                            !isDrawerOpen &&
                            !isQuickSettingsOpen &&
                            !quickMenuState.isVisible
                        ) {
                            openDrawer()
                        }
                    }
                    GamepadEvent.Select -> {
                        if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) dsm?.swapRoles()
                    }
                    GamepadEvent.LongSelect -> {
                        if (com.nendo.argosy.ui.dualscreen.selectHoldSwapsRoles()) dsm?.swapRoles()
                    }
                    GamepadEvent.LeftStickClick -> {
                        if (quickMenuState.isVisible) {
                            closeQuickMenu()
                        } else {
                            if (isDrawerOpen) closeDrawer()
                            if (isQuickSettingsOpen) closeQuickSettings()
                            openQuickMenu()
                        }
                    }
                    GamepadEvent.RightStickClick -> {
                        if (isQuickSettingsOpen) {
                            closeQuickSettings()
                        } else {
                            if (isDrawerOpen) closeDrawer()
                            if (quickMenuState.isVisible) closeQuickMenu()
                            openQuickSettings()
                        }
                    }
                    GamepadEvent.Home -> {
                        if (isDrawerOpen) closeDrawer()
                        if (isQuickSettingsOpen) closeQuickSettings()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        val homeRoute = Screen.Home.route
                        if (currentRoute != homeRoute) {
                            navController.navigate(homeRoute) {
                                popUpTo(homeRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    val shortcutGate = remember(inputDispatcher, navController) {
        object : UiShortcutGate {
            override fun hotkeysAllowed(): Boolean =
                !uiState.isFirstRun &&
                    !isEmulatorRunning &&
                    navController.currentDestination?.route != Screen.FirstRun.route &&
                    !inputDispatcher.hasCapturingOverlay()

            override fun longBackAllowed(): Boolean =
                hotkeysAllowed() &&
                    !isDrawerOpen &&
                    !isQuickSettingsOpen &&
                    !quickMenuState.isVisible
        }
    }

    DisposableEffect(shortcutGate) {
        val handler = viewModel.gamepadInputHandler
        handler.attachShortcutGate(shortcutGate)
        onDispose { handler.detachShortcutGate(shortcutGate) }
    }

    LaunchedEffect(inputLifecycle) {
        viewModel.gamepadInputHandler.shortcutEventFlow().flowWithLifecycle(inputLifecycle).collect { shortcut ->
            when (shortcut) {
                UiShortcut.OPEN_NAVIGATION -> {
                    if (isDrawerOpen) {
                        closeDrawer()
                    } else {
                        if (isQuickSettingsOpen) closeQuickSettings()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        openDrawer()
                    }
                }
                UiShortcut.OPEN_QUICK_PANEL -> {
                    if (isQuickSettingsOpen) {
                        closeQuickSettings()
                    } else {
                        if (isDrawerOpen) closeDrawer()
                        if (quickMenuState.isVisible) closeQuickMenu()
                        openQuickSettings()
                    }
                }
            }
        }
    }

    LaunchedEffect(inputLifecycle) {
        viewModel.gamepadInputHandler.homeEventFlow().flowWithLifecycle(inputLifecycle).collect {
            if (isEmulatorRunning) {
                // No-op: onUserLeaveHint in LibretroActivity handles HOME quit
            } else {
                // Navigate to home view (only if nav graph is ready)
                if (navController.currentDestination != null) {
                    if (isDrawerOpen) closeDrawer()
                    if (isQuickSettingsOpen) closeQuickSettings()
                    if (quickMenuState.isVisible) closeQuickMenu()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    val isScrapingArtwork by viewModel.imageCacheManager.progress
        .collectAsState()
        .let { state -> remember { derivedStateOf { state.value.isProcessing } } }

    val localAvatarInfo by remember {
        derivedStateOf {
            com.nendo.argosy.ui.components.friends.LocalUserAvatarInfo(
                userId = drawerUiState.localUser?.id,
                doodle = drawerUiState.localAvatarDoodle
            )
        }
    }

    CompositionLocalProvider(
        LocalInputDispatcher provides inputDispatcher,
        com.nendo.argosy.ui.input.LocalModalPresence provides inputDispatcher,
        LocalGamepadInputHandler provides viewModel.gamepadInputHandler,
        LocalABIconsSwapped provides uiState.abIconsSwapped,
        LocalXYIconsSwapped provides uiState.xyIconsSwapped,
        LocalSwapStartSelect provides uiState.swapStartSelect,
        LocalFooterHost provides footerHostController,
        com.nendo.argosy.ui.common.LocalImageCacheManager provides viewModel.imageCacheManager,
        com.nendo.argosy.ui.components.LocalArtworkScraping provides isScrapingArtwork,
        com.nendo.argosy.ui.components.LocalStatusBarItems provides
            com.nendo.argosy.ui.components.StatusBarItems(
                clock = uiState.showStatusClock,
                battery = uiState.showStatusBattery,
                network = uiState.showStatusNetwork
            ),
        com.nendo.argosy.ui.components.friends.LocalUserAvatarState provides localAvatarInfo
    ) {
        if (uiState.isLoading) {
            AppSplashScreen(statusRes = uiState.startupStatusRes)
            return@CompositionLocalProvider
        }

        val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
        val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)
        val lastUserActivityAtMs by (
            dsm?.lastUserActivityAtMs
                ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }
            ).collectAsState()
        val dimmerEnabled = dsm != null &&
            screenDimmerPrefs.enabled && !isEmulatorRunning && !uiState.isFirstRun
        val bottomReserved = gripReserveBottomInset()

        ScreenDimmerOverlay(
            enabled = dimmerEnabled,
            timeoutMs = screenDimmerPrefs.timeoutMinutes * 60_000L,
            dimLevel = screenDimmerPrefs.level / 100f,
            lastActivityAtMs = lastUserActivityAtMs,
            onWake = { dsm?.notifyUserActivity("mainDimmerTap") }
        ) {
            var keySinkFocused by remember { mutableStateOf(false) }
            val imeManager = remember(context) {
                context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = bottomReserved)
                    .onFocusChanged { keySinkFocused = it.isFocused }
                    .focusRequester(rootFocusRequester)
                    .focusable()
                    .doubleTapNoFocus { openQuickMenu() }
            ) {
                /**
                 * The key sink has to take focus back whenever something else has had it.
                 *
                 * Gamepad input reaches the app through this node, and on a dual-screen handheld it
                 * is also what forwards keys to the lower display, so a composable that takes focus
                 * and does not give it back leaves both screens deaf until a touch clears it.
                 *
                 * Every tap is a way to lose it: Compose makes a clickable node focusable and moves
                 * focus to it on press, so watching for a lost sink is the only reclaim that covers
                 * the real cause rather than the few state changes we thought to enumerate. Typing
                 * is the one time something else is meant to hold focus, and a raised keyboard is
                 * what says so.
                 *
                 * A text field takes focus before the keyboard it asks for is up, so the reclaim
                 * waits out that gap and then asks the input manager directly whether a field is
                 * connected. The window inset is not trusted for this: it reads zero here even
                 * with the keyboard on screen, which is what made every text field unusable.
                 */
                LaunchedEffect(keySinkFocused, drawerState.isOpen, uiState.isFirstRun) {
                    if (!keySinkFocused && !drawerState.isOpen) {
                        delay(KEY_SINK_RECLAIM_GRACE_MS)
                        if (imeManager?.isAcceptingText == true) return@LaunchedEffect
                        rootFocusRequester.requestFocus()
                    }
                }

                val keyboardToggle by dsm?.keyboardToggleEvent?.collectAsState()
                    ?: remember { mutableStateOf(0L) }
                LaunchedEffect(keyboardToggle) {
                    if (keyboardToggle == 0L) return@LaunchedEffect
                    delay(KEY_SINK_RECLAIM_GRACE_MS)
                    @Suppress("DEPRECATION")
                    imeManager?.toggleSoftInput(android.view.inputmethod.InputMethodManager.SHOW_FORCED, 0)
                }

                var drawerWidthPx by remember { mutableStateOf(0f) }

                ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !uiState.isFirstRun,
                scrimColor = scrimColor,
                drawerContent = {
                    MainDrawer(
                        items = viewModel.drawerItems,
                        currentRoute = currentRoute,
                        drawerState = drawerUiState,
                        isOpen = isDrawerOpen,
                        onNavigate = { route ->
                            inputDispatcher.unsubscribeDrawer()
                            viewModel.setDrawerOpen(false)
                            scope.launch { drawerState.close() }
                            navigateFromDrawer(route)
                        },
                        modifier = Modifier.onSizeChanged { drawerWidthPx = it.width.toFloat() }
                    )
                }
            ) {
                val drawerBlurProgress by remember(drawerState) {
                    derivedStateOf {
                        val offset = drawerState.currentOffset
                        val width = drawerWidthPx
                        if (offset.isNaN() || width <= 0f) {
                            0f
                        } else {
                            (1f + offset / width).coerceIn(0f, 1f)
                        }
                    }
                }
                val drawerBlur = (drawerBlurProgress * Motion.blurRadiusDrawer.value).dp
                val quickMenuBlur by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (quickMenuState.isVisible) Motion.blurRadiusDrawer else 0.dp,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "quickMenuBlur"
                )
                val contentBlur = maxOf(drawerBlur, quickMenuBlur)

                val onRingDestination = navRingState.destinations.any { NavRing.routeMatches(it.route, currentRoute) }
                val homeAppBarOwnsBottom = currentRoute == Screen.Home.route &&
                    presentationShowsHints && navRingState.homeAppBarConfigured
                val appPromptShowing = saveConflictInfo != null ||
                    backgroundConflictInfo != null ||
                    coreCrashPrompt != null ||
                    netplayInvitePrompt != null ||
                    netplayJoinModalActive ||
                    steamDownloadPrompt != null
                val navBarAllowed = onRingDestination &&
                    !homeAppBarOwnsBottom &&
                    !uiState.isFirstRun &&
                    !isDrawerOpen &&
                    !isQuickSettingsOpen &&
                    !quickMenuState.isVisible &&
                    !appPromptShowing
                val currentNavBarAllowed by rememberUpdatedState(navBarAllowed)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .revealOnBottomEdgeTouch(Dimens.footerHeight) {
                            if (currentNavBarAllowed) viewModel.showNavBar()
                        }
                ) {
                    NavGraph(
                        navController = navController,
                        startDestination = startDestination,
                        onDrawerToggle = { if (isDrawerOpen) closeDrawer() else openDrawer() },
                        argosyViewModel = viewModel,
                        videoPreviewBlocked = isDrawerOpen || isQuickSettingsOpen ||
                            quickMenuState.isVisible || appPromptShowing,
                        onPlayMedia = { itemId, startOver ->
                            dsm?.playMediaItem(itemId, startOver)
                                ?: PlayerActivity.start(
                                    context = context,
                                    args = PlayerArgs(
                                        itemId = itemId,
                                        startPositionMs = if (startOver) 0L else -1L
                                    )
                                )
                        },
                        modifier = Modifier.blur(contentBlur)
                    )

                    FloatingNavBar(
                        visible = navBarAllowed && navRingState.isBarVisible,
                        destinations = navRingState.destinations,
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            navigateFromDrawer(route)
                            viewModel.showNavBar()
                        },
                        onInteract = { viewModel.showNavBar() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = Dimens.spacingSm),
                        badgeFor = drawerUiState::badgeCountFor
                    )
                }
            }

            com.nendo.argosy.ui.components.LaunchOverlay()

            val mutedNotificationKeys = if (currentRoute == Screen.SyncMonitor.route) {
                com.nendo.argosy.domain.usecase.sync.SyncNotificationKeys.ALL
            } else {
                emptySet()
            }
            LaunchedEffect(mutedNotificationKeys) { dsm?.setMutedNotificationKeys(mutedNotificationKeys) }
            val notificationsOnPresentation by dsm?.companionHoldsPrimary?.collectAsState()
                ?: remember { mutableStateOf(false) }
            if (!notificationsOnPresentation) {
                NotificationHost(
                    manager = viewModel.notificationManager,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    mutedKeys = mutedNotificationKeys
                )
            }

            com.nendo.argosy.core.notification.ScreenSetPrompt(
                visible = screenSetPromptVisible,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Dimens.spacingMd, bottom = Dimens.headerHeight)
            )

            // Quick Menu Overlay (L3 triggered)
            QuickMenuOverlay(
                viewModel = quickMenuViewModel,
                onGameSelect = { gameId ->
                    closeQuickMenu()
                    navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                        launchSingleTop = true
                    }
                },
                onGamePlay = playFromQuickMenu,
                closeQuickMenu = closeQuickMenu
            )

            // Quick Settings Panel (right-side drawer)
            val musicNeedsSignIn = musicPageVisible &&
                musicPlayerUiState.browse?.notice ==
                com.nendo.argosy.ui.components.musicplayer.MusicBrowseNotice.SIGN_IN_FOR_PLAYLISTS
            val signInHint = stringResource(R.string.ui_quick_settings_music_hint_sign_in)
            QuickSettingsPanel(
                onHintClick = { button ->
                    if (button == com.nendo.argosy.ui.components.InputButton.Y && musicNeedsSignIn) {
                        openRommSignIn()
                    }
                },
                isVisible = isQuickSettingsOpen,
                state = quickSettingsState,
                page = quickSettingsPage,
                focusedIndex = quickSettingsFocusIndex,
                controller = viewModel.quickSettings,
                onOpenDeviceAccess = openDeviceAccess,
                friendsPage = {
                    QuickSettingsFriendsPage(
                        state = quickFriendsState,
                        showQuayPass = quickSettingsState.isSocialLinked,
                        quayPassEnabled = quickSettingsState.quayPassEnabled,
                        onRowClick = { quickFriendsInputHandler.tapRow(it) },
                        onRowLongClick = { quickFriendsInputHandler.longPressRow(it) },
                        onActionClick = { index, action -> quickFriendsInputHandler.tapAction(index, action) }
                    )
                },
                musicPage = {
                    QuickSettingsMusicPage(viewModel = musicPlayerViewModel, onOpenRommSignIn = openRommSignIn)
                },
                onDismiss = closeQuickSettings,
                footerHints = listOfNotNull(
                    (com.nendo.argosy.ui.components.InputButton.Y to signInHint).takeIf { musicNeedsSignIn }
                )
            )

            QuickFriendsModals(
                state = quickFriendsState,
                controller = viewModel.quickFriends,
                onOpenProfile = quickFriendsInputHandler::openProfile,
                onEditAvatar = quickFriendsInputHandler::editAvatar
            )

            saveConflictInfo?.let { info ->
                SaveConflictModal(
                    info = info,
                    focusedButton = saveConflictButtonIndex,
                    onKeepLocal = { viewModel.saveConflicts.dismissSaveConflict() },
                    onOverwrite = { viewModel.saveConflicts.forceUploadConflictSave() },
                    onSnapshotChoice = viewModel.saveConflicts::answerSnapshotConflict
                )
            }

            // Background Sync Conflict Dialog
            backgroundConflictInfo?.let { info ->
                BackgroundSyncConflictDialog(
                    conflictInfo = info,
                    focusIndex = backgroundConflictButtonIndex,
                    onKeepLocal = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.KEEP_LOCAL) },
                    onKeepServer = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.KEEP_SERVER) },
                    onSkip = { viewModel.saveConflicts.resolveBackgroundConflict(ConflictResolution.SKIP) },
                    snapshotConflict = backgroundConflictSnapshot,
                    onSnapshotChoice = viewModel.saveConflicts::resolveBackgroundSnapshotConflict
                )
            }

            val sessionEndOverlay by viewModel.sessionEndOverlay.collectAsState()
            SyncOverlay(
                syncProgress = sessionEndOverlay?.syncProgress,
                gameTitle = sessionEndOverlay?.gameTitle,
                onGrantPermission = sessionEndOverlay?.onGrantPermission,
                onDisableSync = sessionEndOverlay?.onDisableSync,
                onOpenSettings = sessionEndOverlay?.onOpenSettings,
                onSkip = sessionEndOverlay?.onSkip
            )

            coreCrashPrompt?.let { prompt ->
                CoreCrashModal(
                    prompt = prompt,
                    focusedIndex = coreCrashFocusIndex,
                    downloading = coreCrashDownloading,
                    onSelect = { index ->
                        viewModel.coreCrashController.setFocus(index)
                        viewModel.coreCrashController.confirmFocused()
                    },
                    onDismiss = { viewModel.coreCrashController.dismiss() }
                )
            }

            netplayInvitePrompt?.let { invite ->
                NetplayInviteModal(
                    invite = invite,
                    focusedButton = netplayInviteFocusIndex,
                    onJoin = { viewModel.acceptNetplayInvite() },
                    onDismiss = { viewModel.dismissNetplayInvite() }
                )
            }

            NetplayJoinModal(
                state = netplayJoinState,
                onDismiss = { viewModel.cancelNetplayJoin() }
            )

            val steamDownloadFocusIndex by viewModel.steamDownloadPromptController.focusIndex.collectAsState()
            val steamMarkOptions by viewModel.steamDownloadPromptController.markOptions.collectAsState()
            steamDownloadPrompt?.let { prompt ->
                com.nendo.argosy.ui.components.SteamDownloadLocationModal(
                    prompt = prompt,
                    focusIndex = steamDownloadFocusIndex,
                    markOptions = steamMarkOptions,
                    onDownloadToSd = { viewModel.steamDownloadPromptController.confirmDownloadToSd() },
                    onMarkAsInstalled = { pkg -> viewModel.steamDownloadPromptController.confirmMarkInstalled(pkg) },
                    onDismiss = { viewModel.steamDownloadPromptController.dismiss() }
                )
            }

            if (presentationShowsHints) {
                val topEntry = footerHostController.top
                val relayed = topEntry?.hints.orEmpty().map {
                    com.nendo.argosy.ui.dualscreen.CompanionHint(it.button, it.action)
                }
                LaunchedEffect(relayed) {
                    com.nendo.argosy.DualScreenManagerHolder.instance?.publishControlHints(relayed)
                }
            } else {
                AnimatedVisibility(
                    visible = !navRingState.isBarVisible,
                    enter = slideInVertically(tween(Motion.durationSlide)) { it },
                    exit = slideOutVertically(tween(Motion.durationSlide)) { it },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    FooterHost(controller = footerHostController)
                }
            }
            }
        }
    }
}

@Composable
private fun AppSplashScreen(@StringRes statusRes: Int?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.spacingLg)
        ) {
            androidx.compose.material3.Text(
                text = androidx.compose.ui.res.stringResource(R.string.argosyapp_splash_title),
                style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                letterSpacing = 8.sp
            )
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconLg),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                strokeWidth = Dimens.borderMedium
            )
            if (statusRes != null) {
                androidx.compose.material3.Text(
                    text = stringResource(statusRes),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}
