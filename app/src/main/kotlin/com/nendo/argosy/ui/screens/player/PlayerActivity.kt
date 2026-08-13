package com.nendo.argosy.ui.screens.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.theme.ALauncherTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The video player, in a window of its own.
 *
 * It is an activity rather than a route because of what it has to survive and where it has to be
 * able to appear: it keeps playing across a rotation, it opens straight from a Home tile without
 * the launcher's navigation stack underneath it, and it can be placed on a chosen display. Moving
 * it between displays is a plain relaunch there - the position is already persisted, so the new
 * window picks the film up where the old one left it and there is no state to hand across.
 *
 * It takes its own keys rather than joining the launcher's dispatcher, which belongs to the
 * launcher's window. UI sound is deliberately absent here, matching the emulator window: a click on
 * every press of the transport is noise over a film.
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val viewModel: PlayerViewModel by viewModels()
    private val inputHandler by lazy { PlayerInputHandler(viewModel) }

    private var swapAB = false
    private var swapXY = false
    private var swapStartSelect = false
    private var wasStopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        val args = intent.toPlayerArgs()
        if (args == null) {
            finish()
            return
        }
        viewModel.initialize(args)
        observeButtonSwaps()
        observeEvents()

        setContent {
            ALauncherTheme {
                CompositionLocalProvider(
                    LocalABIconsSwapped provides swapAB,
                    LocalXYIconsSwapped provides swapXY,
                    LocalSwapStartSelect provides swapStartSelect
                ) {
                    PlayerScreen(viewModel)
                }
            }
        }
    }

    /**
     * A different item arriving at a live player is a different playback, so the window is rebuilt
     * rather than re-pointed. Recreating tears the old view model down through its normal path,
     * which is what reports the previous stream stopped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val args = intent.toPlayerArgs() ?: return
        if (args.itemId == viewModel.uiState.value.itemId) return
        setIntent(intent)
        recreate()
    }

    override fun onStart() {
        super.onStart()
        if (wasStopped) {
            wasStopped = false
            viewModel.onEnteredForeground()
        }
    }

    /**
     * Whatever took the screen - a game launching, the launcher coming forward, the window being
     * dismissed - the viewing is over as far as the server is concerned. Holding the stream open
     * against a possible return would leave an encoder running for a session nobody is watching.
     */
    override fun onStop() {
        wasStopped = true
        viewModel.onEnteredBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gamepadEvent = mapKeycodeToGamepadEvent(event.keyCode, swapAB, swapXY, swapStartSelect)
            ?: return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (route(gamepadEvent).handled) return true
        }
        return event.action == KeyEvent.ACTION_UP || super.dispatchKeyEvent(event)
    }

    private fun route(event: GamepadEvent): InputResult = when (event) {
        GamepadEvent.Up -> inputHandler.onUp()
        GamepadEvent.Down -> inputHandler.onDown()
        GamepadEvent.Left -> inputHandler.onLeft()
        GamepadEvent.Right -> inputHandler.onRight()
        GamepadEvent.Confirm -> inputHandler.onConfirm()
        GamepadEvent.Back -> inputHandler.onBack()
        GamepadEvent.Menu -> inputHandler.onMenu()
        GamepadEvent.SecondaryAction -> inputHandler.onSecondaryAction()
        GamepadEvent.ContextMenu -> inputHandler.onContextMenu()
        GamepadEvent.PrevSection -> inputHandler.onPrevSection()
        GamepadEvent.NextSection -> inputHandler.onNextSection()
        GamepadEvent.PrevTrigger -> inputHandler.onPrevTrigger()
        GamepadEvent.NextTrigger -> inputHandler.onNextTrigger()
        GamepadEvent.Select -> inputHandler.onSelect()
        GamepadEvent.LeftStickClick -> inputHandler.onLeftStickClick()
        GamepadEvent.RightStickClick -> inputHandler.onRightStickClick()
        GamepadEvent.LongConfirm -> inputHandler.onLongConfirm()
        GamepadEvent.Home -> InputResult.UNHANDLED
    }

    private fun observeButtonSwaps() {
        lifecycleScope.launch {
            userPreferencesRepository.preferences.collectLatest { prefs ->
                swapAB = prefs.swapAB
                swapXY = prefs.swapXY
                swapStartSelect = prefs.swapStartSelect
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    PlayerEvent.Finish -> finish()
                }
            }
        }
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun Intent.toPlayerArgs(): PlayerArgs? {
        val itemId = getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return null
        return PlayerArgs(
            itemId = itemId,
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            subtitle = getStringExtra(EXTRA_SUBTITLE).orEmpty(),
            startPositionMs = getLongExtra(EXTRA_START_POSITION_MS, RESOLVE_RESUME),
            promptResume = getBooleanExtra(EXTRA_PROMPT_RESUME, false)
        )
    }

    companion object {
        private const val EXTRA_ITEM_ID = "com.nendo.argosy.player.ITEM_ID"
        private const val EXTRA_TITLE = "com.nendo.argosy.player.TITLE"
        private const val EXTRA_SUBTITLE = "com.nendo.argosy.player.SUBTITLE"
        private const val EXTRA_START_POSITION_MS = "com.nendo.argosy.player.START_POSITION_MS"
        private const val EXTRA_PROMPT_RESUME = "com.nendo.argosy.player.PROMPT_RESUME"

        /**
         * Sent as the start position when the caller has no opinion. The player then resolves resume
         * itself from the later of the local and server positions, which is what a relaunch on
         * another display sends.
         */
        const val RESOLVE_RESUME = -1L

        fun intent(context: Context, args: PlayerArgs): Intent =
            Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_ITEM_ID, args.itemId)
                putExtra(EXTRA_TITLE, args.title)
                putExtra(EXTRA_SUBTITLE, args.subtitle)
                putExtra(EXTRA_START_POSITION_MS, args.startPositionMs)
                putExtra(EXTRA_PROMPT_RESUME, args.promptResume)
            }

        /**
         * Opens the player, optionally on a chosen display. [options] is the bundle
         * `DisplayAffinityHelper.getActivityOptions` produces; passing none leaves the placement to
         * the system, which is what a single-screen device wants.
         */
        fun start(context: Context, args: PlayerArgs, options: Bundle? = null) {
            val launchIntent = intent(context, args).apply {
                if (options != null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(launchIntent, options)
        }
    }
}
