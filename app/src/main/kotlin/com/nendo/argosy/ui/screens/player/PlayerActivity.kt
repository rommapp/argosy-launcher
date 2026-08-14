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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.ControllerDetector
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.ui.input.GamepadEvent
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalABIconsSwapped
import com.nendo.argosy.ui.input.LocalSwapStartSelect
import com.nendo.argosy.ui.input.LocalXYIconsSwapped
import com.nendo.argosy.ui.input.mapKeycodeToGamepadEvent
import com.nendo.argosy.ui.theme.ALauncherTheme
import com.nendo.argosy.util.DisplayAffinityHelper
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

    @Inject
    lateinit var displayAffinityHelper: DisplayAffinityHelper

    private val viewModel: PlayerViewModel by viewModels()
    private val inputHandler by lazy { PlayerInputHandler(viewModel) }

    private var swapAB by mutableStateOf(false)
    private var swapXY by mutableStateOf(false)
    private var swapStartSelect by mutableStateOf(false)
    private var isNintendoLayout by mutableStateOf(false)
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
                    LocalABIconsSwapped provides (isNintendoLayout xor swapAB),
                    LocalXYIconsSwapped provides (isNintendoLayout xor swapXY),
                    LocalSwapStartSelect provides swapStartSelect
                ) {
                    PlayerScreen(viewModel)
                }
            }
        }
    }

    /**
     * A different item arriving at a live player is a different playback in the same window. The
     * view model is handed the new item rather than the activity being recreated: recreation keeps
     * the same view model store, so the second title would arrive at a view model that already
     * considers itself initialized and would be dropped.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val args = intent.toPlayerArgs() ?: return
        setIntent(intent)
        viewModel.initialize(args)
    }

    override fun onStart() {
        super.onStart()
        reportDisplay()
        if (wasStopped) {
            wasStopped = false
            viewModel.onEnteredForeground()
        }
    }

    /**
     * Tells the launcher which screen this window landed on. The companion sends episodes to the
     * player and has to address the window where it actually is, which is not always where a
     * resolver would have put it.
     */
    private fun reportDisplay() {
        val displayId = window.decorView.display?.displayId ?: return
        DualScreenManagerHolder.instance?.mediaPlayerDisplayId = displayId
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
        if (hasFocus) {
            enterImmersiveMode()
            reportDisplay()
        }
    }

    /**
     * Claims first, like every other window that takes gamepad input. On a dual-screen device the
     * same physical press is delivered to this window and to the companion, and without the claim
     * both act on it - the transport moves and the panel behind it moves with it.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val dsm = DualScreenManagerHolder.instance
        if (dsm != null && !dsm.claimInput(event)) return true
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

    /**
     * The swap preferences say which intent each physical button carries, and that is what the key
     * dispatch is given. What the hints draw is a second question with a second answer: on a Nintendo
     * pad the physical positions are already the other way round, so the glyph for an intent is
     * swapped relative to the preference rather than by it.
     */
    private fun observeButtonSwaps() {
        lifecycleScope.launch {
            userPreferencesRepository.preferences.collectLatest { prefs ->
                swapAB = prefs.swapAB
                swapXY = prefs.swapXY
                swapStartSelect = prefs.swapStartSelect
                isNintendoLayout = when (prefs.controllerLayout) {
                    "nintendo" -> true
                    "xbox" -> false
                    else -> ControllerDetector.detectFromActiveGamepad().layout ==
                        DetectedLayout.NINTENDO
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    PlayerEvent.Finish -> finish()
                    is PlayerEvent.Relocate -> relocate(event)
                }
            }
        }
    }

    /**
     * Hands the viewing to a window on the other screen, and closes this one.
     *
     * A window cannot change display, so a move is a new window plus a departure - which is exactly
     * what the player is built to survive, since it opens at a stated position and holds nothing the
     * old window would have to pass over. On a single-screen device there is nowhere to go and the
     * paused film simply stays behind the game, which is what was asked for there.
     *
     * The move is one-way. When the game ends the film stays where it was put rather than being
     * relaunched a second time: a move costs a re-negotiation and a visible stall, and spending that
     * on a window the viewer is already watching would be worse than leaving it alone.
     */
    private fun relocate(event: PlayerEvent.Relocate) {
        val target = DualScreenManagerHolder.instance?.mediaPlayerRelocationDisplayId() ?: return
        if (window.decorView.display?.displayId == target) return
        val options = displayAffinityHelper.getActivityOptions(
            forEmulator = false,
            overrideDisplayId = target
        ) ?: return
        relocateTo(
            context = this,
            args = PlayerArgs(itemId = event.itemId, startPositionMs = event.positionMs),
            options = options
        )
        finish()
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
            startPositionMs = getLongExtra(EXTRA_START_POSITION_MS, RESOLVE_RESUME)
        )
    }

    companion object {
        private const val EXTRA_ITEM_ID = "com.nendo.argosy.player.ITEM_ID"
        private const val EXTRA_TITLE = "com.nendo.argosy.player.TITLE"
        private const val EXTRA_SUBTITLE = "com.nendo.argosy.player.SUBTITLE"
        private const val EXTRA_START_POSITION_MS = "com.nendo.argosy.player.START_POSITION_MS"

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

        /**
         * Opens an item in the player from something that is not the player's own screen - the
         * companion, whose context belongs to another display. A live window takes the item through
         * onNewIntent and keeps its place; there is no second window.
         */
        fun startOnDisplay(context: Context, args: PlayerArgs, options: Bundle?) {
            context.startActivity(
                intent(context, args).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                },
                options
            )
        }

        /**
         * Opens the window that replaces one on another display. It has to be a genuinely new
         * instance - a single-top match would hand the intent back to the window being left behind
         * and nothing would move - so this is the one launch that asks for a task of its own.
         */
        fun relocateTo(context: Context, args: PlayerArgs, options: Bundle) {
            context.startActivity(
                intent(context, args).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    )
                },
                options
            )
        }
    }
}
