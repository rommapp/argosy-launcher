package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.local.DatabaseFactory
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeScreen
import com.nendo.argosy.ui.screens.secondaryhome.SecondaryHomeViewModel
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Secondary display home activity running in the :companion process.
 * Registered as SECONDARY_HOME to be the default launcher on secondary displays.
 *
 * CRITICAL: This activity runs in a separate process and must NEVER:
 * - Launch MainActivity or any main process components
 * - Use Hilt-injected dependencies
 * - Reference any singletons from the main process
 */
class SecondaryHomeActivity : ComponentActivity() {

    companion object {
        const val ACTION_ARGOSY_FOREGROUND = "com.nendo.argosy.FOREGROUND"
        const val ACTION_ARGOSY_BACKGROUND = "com.nendo.argosy.BACKGROUND"
        const val ACTION_SAVE_STATE_CHANGED = "com.nendo.argosy.SAVE_STATE_CHANGED"
        const val ACTION_SESSION_CHANGED = "com.nendo.argosy.SESSION_CHANGED"
        const val ACTION_HOME_APPS_CHANGED = "com.nendo.argosy.HOME_APPS_CHANGED"
        const val ACTION_LIBRARY_REFRESH = "com.nendo.argosy.LIBRARY_REFRESH"
        const val ACTION_DOWNLOAD_COMPLETED = "com.nendo.argosy.DOWNLOAD_COMPLETED"
        const val EXTRA_IS_DIRTY = "is_dirty"
        const val EXTRA_GAME_ID = "game_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_IS_HARDCORE = "is_hardcore"
        const val EXTRA_HOME_APPS = "home_apps"
    }

    // Show splash until we receive first foreground state broadcast
    private var isInitialized by mutableStateOf(false)
    // Default to false - only show library when we positively know Argosy is in foreground
    private var isArgosyForeground by mutableStateOf(false)
    private var isGameActive by mutableStateOf(false)
    private var currentChannelName by mutableStateOf<String?>(null)
    private var isSaveDirty by mutableStateOf(false)
    private var homeApps by mutableStateOf<List<String>>(emptyList())
    private var primaryColor by mutableStateOf<Int?>(null)

    private lateinit var viewModel: SecondaryHomeViewModel
    private lateinit var sessionStateStore: SessionStateStore

    private fun loadInitialState() {
        sessionStateStore = SessionStateStore(applicationContext)

        // Read initial state from SharedPreferences
        isArgosyForeground = sessionStateStore.isArgosyForeground()
        isGameActive = sessionStateStore.hasActiveSession()
        currentChannelName = sessionStateStore.getChannelName()
        isSaveDirty = sessionStateStore.isSaveDirty()
        homeApps = sessionStateStore.getHomeApps().toList()
        primaryColor = sessionStateStore.getPrimaryColor()

        // Update ViewModel with home apps
        if (homeApps.isNotEmpty()) {
            viewModel.setHomeApps(homeApps)
        }

        isInitialized = true
    }

    private val foregroundReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ARGOSY_FOREGROUND -> {
                    isArgosyForeground = true
                    isInitialized = true
                }
                ACTION_ARGOSY_BACKGROUND -> {
                    isArgosyForeground = false
                    isInitialized = true
                }
            }
        }
    }

    private val saveStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SAVE_STATE_CHANGED) {
                isSaveDirty = intent.getBooleanExtra(EXTRA_IS_DIRTY, false)
            }
        }
    }

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SESSION_CHANGED) {
                val gameId = intent.getLongExtra(EXTRA_GAME_ID, -1)
                if (gameId > 0) {
                    isGameActive = true
                    currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME)
                    isSaveDirty = false
                } else {
                    isGameActive = false
                    currentChannelName = null
                    isSaveDirty = false
                }
                isInitialized = true
            }
        }
    }

    private val homeAppsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HOME_APPS_CHANGED) {
                val apps = intent.getStringArrayListExtra(EXTRA_HOME_APPS)?.toList() ?: emptyList()
                homeApps = apps
                // Also update the ViewModel for library mode
                viewModel.setHomeApps(apps)
            }
        }
    }

    private val libraryRefreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_LIBRARY_REFRESH, ACTION_DOWNLOAD_COMPLETED -> {
                    viewModel.refresh()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeDependencies()
        loadInitialState()
        registerReceivers()

        setContent {
            SecondaryHomeTheme(primaryColor = primaryColor) {
                SecondaryHomeContent(
                    isInitialized = isInitialized,
                    isArgosyForeground = isArgosyForeground,
                    isGameActive = isGameActive,
                    channelName = currentChannelName,
                    isSaveDirty = isSaveDirty,
                    homeApps = homeApps,
                    viewModel = viewModel,
                    onAppClick = { packageName -> launchApp(packageName) }
                )
            }
        }
    }

    private fun initializeDependencies() {
        val database = DatabaseFactory.getDatabase(applicationContext)
        val gameDao = database.gameDao()
        val platformDao = database.platformDao()
        val appsRepository = AppsRepository(applicationContext)
        val displayAffinityHelper = DisplayAffinityHelper(applicationContext)

        viewModel = SecondaryHomeViewModel(
            gameDao = gameDao,
            platformDao = platformDao,
            appsRepository = appsRepository,
            preferencesRepository = null, // Don't use DataStore in companion process - data comes via broadcasts
            displayAffinityHelper = displayAffinityHelper,
            downloadManager = null,
            context = applicationContext
        )
    }

    private fun registerReceivers() {
        val foregroundFilter = IntentFilter().apply {
            addAction(ACTION_ARGOSY_FOREGROUND)
            addAction(ACTION_ARGOSY_BACKGROUND)
        }
        val saveStateFilter = IntentFilter(ACTION_SAVE_STATE_CHANGED)
        val sessionFilter = IntentFilter(ACTION_SESSION_CHANGED)
        val homeAppsFilter = IntentFilter(ACTION_HOME_APPS_CHANGED)
        val libraryRefreshFilter = IntentFilter().apply {
            addAction(ACTION_LIBRARY_REFRESH)
            addAction(ACTION_DOWNLOAD_COMPLETED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(foregroundReceiver, foregroundFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(saveStateReceiver, saveStateFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(sessionReceiver, sessionFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(homeAppsReceiver, homeAppsFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(libraryRefreshReceiver, libraryRefreshFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(foregroundReceiver, foregroundFilter)
            registerReceiver(saveStateReceiver, saveStateFilter)
            registerReceiver(sessionReceiver, sessionFilter)
            registerReceiver(homeAppsReceiver, homeAppsFilter)
            registerReceiver(libraryRefreshReceiver, libraryRefreshFilter)
        }
    }

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            }
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            when (keyCode) {
                android.view.KeyEvent.KEYCODE_DPAD_UP -> {
                    viewModel.moveFocusUp()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> {
                    viewModel.moveFocusDown()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.moveFocusLeft()
                    return true
                }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.moveFocusRight()
                    return true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_L1 -> {
                    viewModel.previousSection()
                    return true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_R1 -> {
                    viewModel.nextSection()
                    return true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_A,
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_ENTER -> {
                    selectFocusedGame()
                    return true
                }
                android.view.KeyEvent.KEYCODE_BUTTON_X -> {
                    launchFocusedGame()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun selectFocusedGame() {
        val (intent, options) = viewModel.selectFocusedGame() ?: return
        if (options != null) {
            startActivity(intent, options)
        } else {
            startActivity(intent)
        }
    }

    private fun launchFocusedGame() {
        val result = viewModel.launchFocusedGame() ?: return
        val (intent, options) = result
        intent?.let {
            if (options != null) {
                startActivity(it, options)
            } else {
                startActivity(it)
            }
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(foregroundReceiver)
            unregisterReceiver(saveStateReceiver)
            unregisterReceiver(sessionReceiver)
            unregisterReceiver(homeAppsReceiver)
            unregisterReceiver(libraryRefreshReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
        super.onDestroy()
    }
}

/**
 * Unified container for secondary display content.
 *
 * Uses a single root Box to prevent back navigation issues.
 * Content visibility is controlled via AnimatedVisibility rather than
 * switching between different root composables.
 */
@Composable
private fun SecondaryHomeContent(
    isInitialized: Boolean,
    isArgosyForeground: Boolean,
    isGameActive: Boolean,
    channelName: String?,
    isSaveDirty: Boolean,
    homeApps: List<String>,
    viewModel: SecondaryHomeViewModel,
    onAppClick: (String) -> Unit
) {
    // Consume all back presses - SECONDARY_HOME should be a navigation dead-end
    BackHandler(enabled = true) {
        // Do nothing - prevent back navigation from affecting this screen
    }

    val showLibrary = isInitialized && isArgosyForeground && !isGameActive
    val showCompanion = isInitialized && !showLibrary
    val showSplash = !isInitialized

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Splash screen - shown until we receive foreground state
        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_helm),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                    alpha = 0.6f
                )
            }
        }

        // Library mode
        AnimatedVisibility(
            visible = showLibrary,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            SecondaryHomeScreen(viewModel = viewModel)
        }

        // Companion mode
        AnimatedVisibility(
            visible = showCompanion,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CompanionContent(
                channelName = channelName,
                isHardcore = false, // TODO: Get from session
                gameName = null,
                isDirty = isSaveDirty,
                homeApps = homeApps,
                onAppClick = onAppClick
            )
        }
    }
}
