package com.nendo.argosy.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.nendo.argosy.data.preferences.DefaultView
import com.nendo.argosy.ui.screens.apps.AppsScreen
import com.nendo.argosy.ui.screens.collections.CollectionDetailScreen
import com.nendo.argosy.ui.screens.collections.CollectionsScreen
import com.nendo.argosy.ui.screens.collections.VirtualBrowserScreen
import com.nendo.argosy.ui.screens.collections.VirtualCategoryScreen
import com.nendo.argosy.ui.screens.downloads.DownloadsScreen
import com.nendo.argosy.ui.screens.firstrun.FirstRunScreen
import com.nendo.argosy.ui.screens.gamedetail.GameDetailScreen
import com.nendo.argosy.ui.screens.home.HomeScreen
import com.nendo.argosy.ui.screens.launch.LaunchScreen
import com.nendo.argosy.ui.screens.library.LibraryScreen
import com.nendo.argosy.ui.screens.doodle.DoodleScreen
import com.nendo.argosy.ui.screens.search.SearchScreen
import com.nendo.argosy.ui.screens.settings.ManagePinsScreen
import com.nendo.argosy.ui.screens.settings.SettingsScreen
import com.nendo.argosy.ui.screens.social.FeedEventDetailScreen
import com.nendo.argosy.ui.screens.social.PostEditorScreen
import com.nendo.argosy.ui.screens.social.SocialScreen
import com.nendo.argosy.ui.screens.social.UserProfileScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    defaultView: DefaultView,
    onDrawerToggle: () -> Unit,
    onSetReturningFromGame: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navigateToDefault = remember(defaultView) {
        {
            val route = when (defaultView) {
                DefaultView.HOME -> Screen.Home.route
                DefaultView.LIBRARY -> Screen.Library.route
            }
            navController.navigate(route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }
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
                isDefaultView = defaultView == DefaultView.HOME,
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onNavigateToLibrary = { platformId, sourceFilter ->
                    navController.navigate(Screen.Library.createRoute(platformId, sourceFilter))
                },
                onNavigateToLaunch = { gameId, channelName ->
                    navController.navigate(Screen.Launch.createRoute(gameId, channelName))
                },
                onNavigateToDefault = navigateToDefault,
                onDrawerToggle = onDrawerToggle,
                onChangelogAction = { action ->
                    val section = action.section.name
                    navController.navigate(Screen.Settings.createRoute(section, action.actionKey))
                }
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
            val platformId = backStackEntry.arguments?.getString("platformId")?.toLongOrNull()
            val source = backStackEntry.arguments?.getString("source")
            LibraryScreen(
                isDefaultView = defaultView == DefaultView.LIBRARY,
                initialPlatformId = platformId,
                initialSource = source,
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onNavigateToLaunch = { gameId, channelName ->
                    navController.navigate(Screen.Launch.createRoute(gameId, channelName))
                },
                onNavigateToDefault = navigateToDefault,
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(Screen.Collections.route) {
            CollectionsScreen(
                onBack = navigateToDefault,
                onCollectionClick = { collectionId ->
                    navController.navigate(Screen.CollectionDetail.createRoute(collectionId))
                },
                onVirtualBrowseClick = { type ->
                    navController.navigate(Screen.VirtualBrowser.createRoute(type))
                }
            )
        }

        composable(
            route = Screen.CollectionDetail.route,
            arguments = listOf(navArgument("collectionId") { type = NavType.LongType })
        ) {
            CollectionDetailScreen(
                onBack = { navController.popBackStack() },
                onGameClick = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                }
            )
        }

        composable(
            route = Screen.VirtualBrowser.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType })
        ) {
            VirtualBrowserScreen(
                onBack = { navController.popBackStack() },
                onCategoryClick = { category ->
                    val type = it.arguments?.getString("type") ?: "genres"
                    navController.navigate(Screen.VirtualCategory.createRoute(type, category))
                }
            )
        }

        composable(
            route = Screen.VirtualCategory.route,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType },
                navArgument("category") { type = NavType.StringType }
            )
        ) {
            VirtualCategoryScreen(
                onBack = { navController.popBackStack() },
                onGameClick = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                }
            )
        }

        composable(Screen.Downloads.route) {
            DownloadsScreen(
                onBack = navigateToDefault,
                onDrawerToggle = onDrawerToggle,
                onNavigateToGame = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                }
            )
        }

        composable(Screen.SaveSync.route) {
            com.nendo.argosy.ui.screens.savesync.SaveSyncScreen(
                onBack = navigateToDefault,
                onDrawerToggle = onDrawerToggle,
                onNavigateToGame = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                }
            )
        }

        composable(
            route = Screen.Apps.route,
            deepLinks = listOf(navDeepLink { uriPattern = "argosy://apps" })
        ) {
            AppsScreen(
                onBack = navigateToDefault,
                onDrawerToggle = onDrawerToggle
            )
        }

        composable(
            route = Screen.Settings.route,
            arguments = listOf(
                navArgument("section") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("action") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("platformId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString("section")
            val action = backStackEntry.arguments?.getString("action")
            val platformId = backStackEntry.arguments?.getLong("platformId")?.takeIf { it >= 0 }
            SettingsScreen(
                onBack = {
                    if (platformId != null) navController.popBackStack() else navigateToDefault()
                },
                initialSection = section,
                initialAction = action,
                initialPlatformId = platformId
            )
        }

        composable(
            route = Screen.GameDetail.route,
            arguments = listOf(navArgument("gameId") { type = NavType.LongType }),
            deepLinks = listOf(navDeepLink { uriPattern = "argosy://game/{gameId}" })
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            GameDetailScreen(
                gameId = gameId,
                onBack = { navController.popBackStack() },
                onNavigateToLaunch = { id, channelName, discId ->
                    navController.navigate(Screen.Launch.createRoute(id, channelName, discId))
                },
                onNavigateToPlatformSettings = { platformId ->
                    navController.navigate(
                        Screen.Settings.createRoute(section = "platform_detail", platformId = platformId)
                    )
                }
            )
        }

        composable(
            route = Screen.Launch.route,
            arguments = listOf(
                navArgument("gameId") { type = NavType.LongType },
                navArgument("channelName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("discId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getLong("gameId") ?: return@composable
            val channelName = backStackEntry.arguments?.getString("channelName")
            val discId = backStackEntry.arguments?.getLong("discId")?.takeIf { it != -1L }
            LaunchScreen(
                gameId = gameId,
                channelName = channelName,
                discId = discId,
                onLaunchComplete = {
                    onSetReturningFromGame()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                onGameSelect = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onBack = navigateToDefault
            )
        }

        composable(Screen.ManagePins.route) {
            ManagePinsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Social.route) {
            SocialScreen(
                onBack = navigateToDefault,
                onDrawerToggle = onDrawerToggle,
                onOpenEventDetail = { eventId ->
                    navController.navigate(Screen.SocialEventDetail.createRoute(eventId))
                },
                onCreatePost = {
                    navController.navigate(Screen.PostEditor.route)
                },
                onViewProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                },
                onNavigateToGameDetail = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId.toLong()))
                },
                onNavigateToSocialSettings = {
                    navController.navigate(Screen.Settings.createRoute(section = "social"))
                }
            )
        }

        composable(
            route = Screen.UserProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            UserProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                onNavigateToGameDetail = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId.toLong()))
                }
            )
        }

        composable(
            route = Screen.SocialEventDetail.route,
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            FeedEventDetailScreen(
                eventId = eventId,
                onBack = { navController.popBackStack() },
                onNavigateToGame = { gameId ->
                    navController.navigate(Screen.GameDetail.createRoute(gameId))
                },
                onViewProfile = { userId ->
                    navController.navigate(Screen.UserProfile.createRoute(userId))
                }
            )
        }

        composable(Screen.Doodle.route) {
            val prevHandle = navController.previousBackStackEntry?.savedStateHandle
            val initialGameId = prevHandle?.get<Int>("doodle_initial_game_id")
            val initialGameTitle = prevHandle?.get<String>("doodle_initial_game_title")
            val initialGameCoverPath = prevHandle?.get<String>("doodle_initial_game_cover_path")
            DoodleScreen(
                onBack = { navController.popBackStack() },
                onDone = { data, size, gameId, gameTitle, gameCoverPath ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("doodle_data", data)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("doodle_size", size)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("doodle_game_id", gameId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("doodle_game_title", gameTitle)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("doodle_game_cover_path", gameCoverPath)
                    navController.popBackStack()
                },
                initialGameId = initialGameId,
                initialGameTitle = initialGameTitle,
                initialGameCoverPath = initialGameCoverPath
            )
        }

        composable(Screen.PostEditor.route) { backStackEntry ->
            val doodleData = backStackEntry.savedStateHandle.get<String>("doodle_data")
            val doodleSize = backStackEntry.savedStateHandle.get<Int>("doodle_size")
            val doodleGameId = backStackEntry.savedStateHandle.get<Int>("doodle_game_id")
            val doodleGameTitle = backStackEntry.savedStateHandle.get<String>("doodle_game_title")
            val doodleGameCoverPath = backStackEntry.savedStateHandle.get<String>("doodle_game_cover_path")
            PostEditorScreen(
                onBack = { navController.popBackStack() },
                onPosted = { navController.popBackStack() },
                onNavigateToDoodle = { gameId, gameTitle, gameCoverPath ->
                    backStackEntry.savedStateHandle["doodle_initial_game_id"] = gameId
                    backStackEntry.savedStateHandle["doodle_initial_game_title"] = gameTitle
                    backStackEntry.savedStateHandle["doodle_initial_game_cover_path"] = gameCoverPath
                    navController.navigate(Screen.Doodle.route)
                },
                initialDoodleData = doodleData,
                initialDoodleSize = doodleSize,
                initialDoodleGameId = doodleGameId,
                initialDoodleGameTitle = doodleGameTitle,
                initialDoodleGameCoverPath = doodleGameCoverPath
            )
        }
    }
}
