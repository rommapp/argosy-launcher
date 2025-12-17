package com.nendo.argosy.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nendo.argosy.ui.screens.apps.AppsScreen
import com.nendo.argosy.ui.screens.downloads.DownloadsScreen
import com.nendo.argosy.ui.screens.firstrun.FirstRunScreen
import com.nendo.argosy.ui.screens.gamedetail.GameDetailScreen
import com.nendo.argosy.ui.screens.home.HomeScreen
import com.nendo.argosy.ui.screens.library.LibraryScreen
import com.nendo.argosy.ui.screens.search.SearchScreen
import com.nendo.argosy.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onDrawerToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(animationSpec = tween(150)) },
        exitTransition = { fadeOut(animationSpec = tween(150)) },
        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
        popExitTransition = { fadeOut(animationSpec = tween(150)) }
    ) {
        composable(Screen.FirstRun.route) {
            FirstRunScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.FirstRun.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onNavigateToLibrary = { platformId, sourceFilter ->
                    navController.navigate(Screen.Library.createRoute(platformId, sourceFilter))
                },
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(
            route = Screen.Library.route,
            arguments = listOf(
                navArgument("platformId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("source") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val platformId = backStackEntry.arguments?.getString("platformId")
            val source = backStackEntry.arguments?.getString("source")
            LibraryScreen(
                initialPlatformId = platformId,
                initialSource = source,
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onBack = { navController.popBackStack() },
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(Screen.Apps.route) {
            AppsScreen(
                onBack = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            GameDetailScreen(
                gameId = gameId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
