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
import com.nendo.argosy.ui.input.LocalNintendoLayout
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.navigation.NavGraph
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.notification.NotificationHost
import com.nendo.argosy.ui.theme.Motion
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

    // Drawer state with synchronous ViewModel sync via confirmStateChange
    // This ensures _isDrawerOpen is ALWAYS correct before any input is processed
    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed,
        confirmStateChange = { newValue ->
            val newOpen = newValue == DrawerValue.Open
            viewModel.setDrawerOpen(newOpen)
            true
        }
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
            onDismiss = { viewModel.setDrawerOpen(false) }
        )
    }

    // Block input during route transitions
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
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

    // Handle drawer state change: subscribe/unsubscribe handler and side effects
    LaunchedEffect(isDrawerOpen) {
        inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
        if (isDrawerOpen) {
            inputDispatcher.subscribeDrawer(drawerInputHandler)
            val parentRoute = navController.previousBackStackEntry?.destination?.route
            viewModel.initDrawerFocus(currentRoute, parentRoute)
            viewModel.onDrawerOpened()
            viewModel.soundManager.play(SoundType.OPEN_MODAL)
        } else {
            inputDispatcher.unsubscribeDrawer()
        }
    }

    // Collect gamepad events (Menu opens drawer if unhandled)
    LaunchedEffect(Unit) {
        viewModel.gamepadInputHandler.eventFlow().collect { event ->
            val handled = inputDispatcher.dispatch(event)
            if (!handled && event == GamepadEvent.Menu) {
                viewModel.setDrawerOpen(true)
            }
        }
    }

    CompositionLocalProvider(
        LocalInputDispatcher provides inputDispatcher,
        LocalNintendoLayout provides uiState.nintendoButtonLayout,
        LocalSwapStartSelect provides uiState.swapStartSelect
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = !uiState.isFirstRun,
                drawerContent = {
                    MainDrawer(
                        items = viewModel.drawerItems,
                        currentRoute = currentRoute,
                        focusedIndex = drawerFocusIndex,
                        drawerState = drawerUiState,
                        onNavigate = { route ->
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
                    onDrawerToggle = { viewModel.setDrawerOpen(!isDrawerOpen) },
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
