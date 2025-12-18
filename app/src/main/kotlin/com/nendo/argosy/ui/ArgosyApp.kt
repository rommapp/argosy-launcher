package com.nendo.argosy.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nendo.argosy.ui.components.MainDrawer
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputDispatcher
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.NavGraph
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.notification.NotificationHost
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun ArgosyApp(
    viewModel: ArgosyViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val drawerFocusIndex by viewModel.drawerFocusIndex.collectAsState()
    val drawerUiState by viewModel.drawerUiState.collectAsState()
    val isDrawerOpen by viewModel.isDrawerOpen.collectAsState()
    val scope = rememberCoroutineScope()

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

    val startDestination = if (uiState.isFirstRun) {
        Screen.FirstRun.route
    } else {
        Screen.Home.route
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

    // Block input during route transitions and sync route to dispatcher
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
        }
        inputDispatcher.setCurrentRoute(currentRoute)
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

    // Collect gamepad events (Menu opens drawer if unhandled)
    LaunchedEffect(Unit) {
        viewModel.gamepadInputHandler.eventFlow().collect { event ->
            val result = inputDispatcher.dispatch(event)
            if (!result.handled && event == GamepadEvent.Menu) {
                openDrawer()
            }
        }
    }

    CompositionLocalProvider(
        LocalInputDispatcher provides inputDispatcher,
        LocalABIconsSwapped provides uiState.abIconsSwapped,
        LocalSwapStartSelect provides uiState.swapStartSelect
    ) {
        val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
        val scrimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)

        Box(modifier = Modifier.fillMaxSize()) {
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
                val contentBlur = (drawerBlurProgress * Motion.blurRadiusDrawer.value).dp

                NavGraph(
                    navController = navController,
                    startDestination = startDestination,
                    onDrawerToggle = { if (isDrawerOpen) closeDrawer() else openDrawer() },
                    modifier = Modifier.blur(contentBlur)
                )
            }

            NotificationHost(
                manager = viewModel.notificationManager,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
