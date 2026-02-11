package com.nendo.argosy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DrawerValue
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
import androidx.compose.foundation.focusable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.nendo.argosy.libretro.LibretroActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nendo.argosy.data.sync.BackgroundSyncConflictDialog
import com.nendo.argosy.data.sync.ConflictResolution
import com.nendo.argosy.ui.components.MainDrawer
import com.nendo.argosy.ui.components.QuickSettingsPanel
import com.nendo.argosy.ui.components.QuickSettingsState
import com.nendo.argosy.ui.components.SaveConflictModal
import com.nendo.argosy.ui.components.ScreenDimmerOverlay
import com.nendo.argosy.ui.components.rememberScreenDimmerState
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalGamepadInputHandler
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.NavGraph
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.notification.NotificationHost
import com.nendo.argosy.ui.quickmenu.QuickMenuInputHandler
import com.nendo.argosy.ui.quickmenu.QuickMenuOverlay
import com.nendo.argosy.ui.quickmenu.QuickMenuViewModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch

@Composable
fun ArgosyApp(
    viewModel: ArgosyViewModel = hiltViewModel(),
    quickMenuViewModel: QuickMenuViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val drawerFocusIndex by viewModel.drawerFocusIndex.collectAsState()
    val drawerUiState by viewModel.drawerUiState.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val isQuickSettingsOpen by viewModel.isQuickSettingsOpen.collectAsState()
    val quickSettingsFocusIndex by viewModel.quickSettingsFocusIndex.collectAsState()
    val quickSettingsUiState by viewModel.quickSettingsState.collectAsState()
    val screenDimmerPrefs by viewModel.screenDimmerPreferences.collectAsState()
    val isEmulatorRunning by viewModel.isEmulatorRunning.collectAsState()
    val quickMenuState by quickMenuViewModel.uiState.collectAsState()
    val saveConflictInfo by viewModel.saveConflictInfo.collectAsState()
    val saveConflictButtonIndex by viewModel.saveConflictButtonIndex.collectAsState()
    val backgroundConflictInfo by viewModel.backgroundConflictInfo.collectAsState()
    val backgroundConflictButtonIndex by viewModel.backgroundConflictButtonIndex.collectAsState()
    val screenDimmerState = rememberScreenDimmerState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Handle deep links from secondary home
    val activity = context as? com.nendo.argosy.MainActivity
    val pendingDeepLink by activity?.pendingDeepLink?.collectAsState() ?: remember { mutableStateOf(null) }
    LaunchedEffect(pendingDeepLink) {
        pendingDeepLink?.let { uri ->
            android.util.Log.d("ArgosyApp", "Handling deep link: $uri")
            if (uri.scheme == "argosy") {
                when (uri.host) {
                    "game" -> {
                        val gameId = uri.lastPathSegment?.toLongOrNull()
                        if (gameId != null) {
                            navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                    "play" -> {
                        val gameId = uri.lastPathSegment?.toLongOrNull()
                        if (gameId != null) {
                            navController.navigate(Screen.Launch.createRoute(gameId)) {
                                launchSingleTop = true
                            }
                        }
                    }
                    "apps" -> {
                        navController.navigate(Screen.Apps.route) {
                            launchSingleTop = true
                        }
                    }
                }
            }
            activity?.clearPendingDeepLink()
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

    val rootFocusRequester = remember { FocusRequester() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        inputDispatcher.blockInputFor(200)
        inputDispatcher.resetToMainView()
        viewModel.resetAllModals()
        viewModel.refreshControllerDetection()
        try { rootFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    val startDestination = when {
        uiState.isFirstRun -> Screen.FirstRun.route
        uiState.defaultView == DefaultView.LIBRARY -> Screen.Library.route
        else -> Screen.Home.route
    }

    // Create drawer input handler
    val drawerInputHandler = remember {
        viewModel.createDrawerInputHandler(
            onNavigate = { route ->
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
                scope.launch { drawerState.close() }
                val current = navController.currentDestination?.route
                if (route != current) {
                    navController.navigate(route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            onDismiss = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.setDrawerOpen(false)
            }
        )
    }

    // Synchronous drawer toggle - subscription must happen immediately, not via LaunchedEffect
    val openDrawer = remember(drawerInputHandler) {
        {
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
    val quickSettingsInputHandler = remember(viewModel, inputDispatcher) {
        viewModel.createQuickSettingsInputHandler(
            onDismiss = {
                inputDispatcher.unsubscribeDrawer()
                viewModel.setQuickSettingsOpen(false)
            }
        )
    }

    val openQuickSettings = remember(quickSettingsInputHandler) {
        {
            inputDispatcher.subscribeDrawer(quickSettingsInputHandler)
            viewModel.setQuickSettingsOpen(true)
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val closeQuickSettings = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            viewModel.setQuickSettingsOpen(false)
        }
    }

    val closeQuickMenu = remember {
        {
            inputDispatcher.unsubscribeDrawer()
            quickMenuViewModel.hide()
        }
    }

    // Quick menu input handler
    val quickMenuInputHandler = remember(quickMenuViewModel, navController, closeQuickMenu) {
        QuickMenuInputHandler(
            viewModel = quickMenuViewModel,
            onGameSelect = { gameId ->
                closeQuickMenu()
                navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                    launchSingleTop = true
                }
            },
            onDismiss = { closeQuickMenu() }
        )
    }

    val openQuickMenu = remember(quickMenuInputHandler) {
        {
            if (isDrawerOpen) closeDrawer()
            if (isQuickSettingsOpen) closeQuickSettings()
            inputDispatcher.subscribeDrawer(quickMenuInputHandler)
            quickMenuViewModel.show()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        }
    }

    val saveConflictInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onLeft(): InputResult {
                viewModel.moveSaveConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onRight(): InputResult {
                viewModel.moveSaveConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                val buttonIndex = viewModel.saveConflictButtonIndex.value
                if (buttonIndex == 0) {
                    viewModel.dismissSaveConflict()
                } else {
                    viewModel.forceUploadConflictSave()
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.dismissSaveConflict()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    val backgroundConflictInputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onUp(): InputResult {
                viewModel.moveBackgroundConflictFocus(-1)
                return InputResult.HANDLED
            }
            override fun onDown(): InputResult {
                viewModel.moveBackgroundConflictFocus(1)
                return InputResult.HANDLED
            }
            override fun onConfirm(): InputResult {
                when (viewModel.backgroundConflictButtonIndex.value) {
                    0 -> viewModel.resolveBackgroundConflict(ConflictResolution.KEEP_LOCAL)
                    1 -> viewModel.resolveBackgroundConflict(ConflictResolution.KEEP_SERVER)
                    2 -> viewModel.resolveBackgroundConflict(ConflictResolution.SKIP)
                }
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
            override fun onBack(): InputResult {
                viewModel.resolveBackgroundConflict(ConflictResolution.SKIP)
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }
        }
    }

    LaunchedEffect(saveConflictInfo, backgroundConflictInfo) {
        when {
            saveConflictInfo != null -> inputDispatcher.subscribeDrawer(saveConflictInputHandler)
            backgroundConflictInfo != null -> inputDispatcher.subscribeDrawer(backgroundConflictInputHandler)
            else -> inputDispatcher.unsubscribeDrawer()
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

    // Sync ViewModel drawer state -> Compose drawer animation
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen && !drawerState.isOpen) {
            drawerState.open()
        } else if (!isDrawerOpen && drawerState.isOpen) {
            drawerState.close()
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

    // Reset dim timer on any gamepad key press (including raw-intercepted events)
    LaunchedEffect(Unit) {
        viewModel.gamepadInputHandler.onActivity = { screenDimmerState.recordActivity() }
    }

    // Collect gamepad events (Menu toggles drawer, L3 toggles quick menu, R3 toggles quick settings)
    LaunchedEffect(Unit) {
        viewModel.gamepadInputHandler.eventFlow().collect { event ->
            val result = inputDispatcher.dispatch(event)
            if (!result.handled) {
                when (event) {
                    GamepadEvent.Menu -> {
                        if (isDrawerOpen) {
                            closeDrawer()
                        } else {
                            if (isQuickSettingsOpen) closeQuickSettings()
                            if (quickMenuState.isVisible) closeQuickMenu()
                            openDrawer()
                        }
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

    // Collect Home button events (from system Home button press)
    LaunchedEffect(Unit) {
        viewModel.gamepadInputHandler.homeEventFlow().collect {
            if (isEmulatorRunning) {
                // Bring emulator back and show its menu
                context.startActivity(
                    Intent(context, LibretroActivity::class.java).apply {
                        action = LibretroActivity.ACTION_SHOW_MENU
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    }
                )
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

    CompositionLocalProvider(
        LocalInputDispatcher provides inputDispatcher,
        LocalGamepadInputHandler provides viewModel.gamepadInputHandler,
        LocalABIconsSwapped provides uiState.abIconsSwapped,
        LocalXYIconsSwapped provides uiState.xyIconsSwapped,
        LocalSwapStartSelect provides uiState.swapStartSelect
    ) {
        if (uiState.isLoading) {
            AppSplashScreen()
            return@CompositionLocalProvider
        }

        val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
        val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)
        val dimmerEnabled = screenDimmerPrefs.enabled && !isEmulatorRunning && !uiState.isFirstRun

        ScreenDimmerOverlay(
            enabled = dimmerEnabled,
            timeoutMs = screenDimmerPrefs.timeoutMinutes * 60_000L,
            dimLevel = screenDimmerPrefs.level / 100f,
            dimmerState = screenDimmerState
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(rootFocusRequester)
                    .focusable()
            ) {
                LaunchedEffect(Unit) {
                    rootFocusRequester.requestFocus()
                }

                ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !uiState.isFirstRun,
                scrimColor = scrimColor,
                drawerContent = {
                    MainDrawer(
                        items = viewModel.drawerItems,
                        currentRoute = currentRoute,
                        focusedIndex = drawerFocusIndex,
                        drawerState = drawerUiState,
                        onNavigate = { route ->
                            inputDispatcher.unsubscribeDrawer()
                            viewModel.setDrawerOpen(false)
                            scope.launch { drawerState.close() }
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            ) {
                val density = LocalDensity.current
                val drawerWidthPx = remember { with(density) { 360.dp.toPx() } }
                val drawerBlurProgress by remember(drawerState) {
                    derivedStateOf {
                        val offset = drawerState.currentOffset
                        if (offset.isNaN()) 0f else (1f + offset / drawerWidthPx).coerceIn(0f, 1f)
                    }
                }
                val drawerBlur = (drawerBlurProgress * Motion.blurRadiusDrawer.value).dp
                val quickMenuBlur by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (quickMenuState.isVisible) Motion.blurRadiusDrawer else 0.dp,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "quickMenuBlur"
                )
                val contentBlur = maxOf(drawerBlur, quickMenuBlur)

                NavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    defaultView = uiState.defaultView,
                    onDrawerToggle = { if (isDrawerOpen) closeDrawer() else openDrawer() },
                    onSetReturningFromGame = { viewModel.setReturningFromGame() },
                    modifier = Modifier.blur(contentBlur)
                )
            }

            NotificationHost(
                manager = viewModel.notificationManager,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            // Quick Menu Overlay (L3 triggered)
            QuickMenuOverlay(
                viewModel = quickMenuViewModel,
                onGameSelect = { gameId ->
                    closeQuickMenu()
                    navController.navigate(Screen.GameDetail.createRoute(gameId)) {
                        launchSingleTop = true
                    }
                }
            )

            // Quick Settings Panel (right-side drawer)
            QuickSettingsPanel(
                isVisible = isQuickSettingsOpen,
                state = QuickSettingsState(
                    themeMode = quickSettingsUiState.themeMode,
                    soundEnabled = quickSettingsUiState.soundEnabled,
                    hapticEnabled = quickSettingsUiState.hapticEnabled,
                    vibrationStrength = quickSettingsUiState.vibrationStrength,
                    vibrationSupported = quickSettingsUiState.vibrationSupported,
                    ambientAudioEnabled = quickSettingsUiState.ambientAudioEnabled,
                    fanMode = quickSettingsUiState.fanMode,
                    fanSpeed = quickSettingsUiState.fanSpeed,
                    performanceMode = quickSettingsUiState.performanceMode,
                    deviceSettingsSupported = quickSettingsUiState.deviceSettingsSupported,
                    deviceSettingsEnabled = quickSettingsUiState.deviceSettingsEnabled,
                    systemVolume = quickSettingsUiState.systemVolume,
                    secondaryVolume = quickSettingsUiState.secondaryVolume,
                    screenBrightness = quickSettingsUiState.screenBrightness,
                    secondaryBrightness = quickSettingsUiState.secondaryBrightness
                ),
                focusedIndex = quickSettingsFocusIndex,
                onThemeCycle = { viewModel.cycleTheme() },
                onSoundToggle = { viewModel.toggleSound() },
                onHapticToggle = { viewModel.toggleHaptic() },
                onVibrationStrengthChange = { viewModel.setVibrationStrength(it) },
                onAmbientToggle = { viewModel.toggleAmbientAudio() },
                onFanModeCycle = { viewModel.cycleFanMode() },
                onFanSpeedChange = { viewModel.setFanSpeed(it) },
                onPerformanceModeCycle = { viewModel.cyclePerformanceMode() },
                onVolumeChange = { viewModel.setSystemVolume(it) },
                onSecondaryVolumeChange = { viewModel.setSecondaryVolume(it) },
                onBrightnessChange = { viewModel.setScreenBrightness(it) },
                onSecondaryBrightnessChange = { viewModel.setSecondaryBrightness(it) },
                onDismiss = closeQuickSettings
            )

            // Save Conflict Modal
            saveConflictInfo?.let { info ->
                SaveConflictModal(
                    info = info,
                    focusedButton = saveConflictButtonIndex,
                    onKeepLocal = { viewModel.dismissSaveConflict() },
                    onOverwrite = { viewModel.forceUploadConflictSave() }
                )
            }

            // Background Sync Conflict Dialog
            backgroundConflictInfo?.let { info ->
                BackgroundSyncConflictDialog(
                    conflictInfo = info,
                    focusIndex = backgroundConflictButtonIndex,
                    onKeepLocal = { viewModel.resolveBackgroundConflict(ConflictResolution.KEEP_LOCAL) },
                    onKeepServer = { viewModel.resolveBackgroundConflict(ConflictResolution.KEEP_SERVER) },
                    onSkip = { viewModel.resolveBackgroundConflict(ConflictResolution.SKIP) }
                )
            }
            }
        }
    }
}

@Composable
private fun AppSplashScreen() {
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
                text = "ARGOSY",
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
        }
    }
}
