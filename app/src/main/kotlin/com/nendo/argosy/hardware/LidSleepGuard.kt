package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.util.AynThor
import com.nendo.argosy.util.DisplayAffinityHelper
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts an AYN Thor back to sleep when its screen comes on while the lid is shut, which a
 * controller button pressed during the lid-close sleep does. The lid state is the kernel lid
 * switch, read through the root route, and the sleep is a root sleep key, so the keyguard follows
 * the user's own auto-lock delay.
 */
@Singleton
class LidSleepGuard @Inject constructor(
    @ApplicationContext private val context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val displayPrefs: DisplayPreferencesRepository
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val isSupported: Boolean
        get() = AynThor.matches() && RootShell.isAvailable

    private var running = false
    private var enabled = false
    private var listening = false
    private var attemptWindowStart = 0L
    private var attemptsInWindow = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_ON) return
            handler.removeCallbacks(sleepCheck)
            handler.postDelayed(sleepCheck, LidSleepDecision.WAKE_GRACE_MS)
        }
    }

    private val sleepCheck = Runnable { scope.launch { sleepIfLidClosed() } }

    fun start() {
        if (running || !isSupported) return
        running = true
        scope.launch {
            displayPrefs.preferences.map { it.lidSleepEnabled }.distinctUntilChanged().collect { on ->
                enabled = on
                if (on) listen() else unlisten()
            }
        }
    }

    private fun listen() {
        if (listening) return
        context.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        listening = true
    }

    private fun unlisten() {
        if (!listening) return
        handler.removeCallbacks(sleepCheck)
        context.unregisterReceiver(screenReceiver)
        listening = false
    }

    private suspend fun sleepIfLidClosed() {
        val now = SystemClock.elapsedRealtime()
        if (now - attemptWindowStart > LidSleepDecision.ATTEMPT_WINDOW_MS) {
            attemptWindowStart = now
            attemptsInWindow = 0
        }
        val lidClosed = withContext(Dispatchers.IO) { readLidClosed() } ?: return
        val state = LidSleepState(
            enabled = enabled,
            lidClosed = lidClosed,
            interactive = powerManager.isInteractive,
            externalDisplayInUse = displayAffinityHelper.isDesktopMode ||
                displayAffinityHelper.isDockedDark ||
                displayAffinityHelper.hasLitExternalDisplay,
            attemptsInWindow = attemptsInWindow
        )
        when (LidSleepDecision.decide(state)) {
            LidSleepVerdict.IDLE -> Unit
            LidSleepVerdict.EXTERNAL_DISPLAY ->
                Logger.info(TAG, "Lid closed with an external display in use, leaving the device awake")
            LidSleepVerdict.RATE_LIMITED ->
                Logger.warn(TAG, "Sleep attempts exhausted for now, leaving the device awake")
            LidSleepVerdict.SLEEP -> {
                attemptsInWindow++
                val slept = withContext(Dispatchers.IO) {
                    RootShell.run(context, SLEEP_SCRIPT)?.succeeded == true
                }
                Logger.info(TAG, "Screen on with the lid closed, sleep requested=$slept")
            }
        }
    }

    private fun readLidClosed(): Boolean? {
        val result = RootShell.run(context, LID_SCRIPT) ?: return null
        return LidSleepDecision.parseLidClosed(result.output)
    }

    companion object {
        private const val TAG = "LidSleepGuard"
        private const val LID_SCRIPT = "dumpsys input"
        const val SLEEP_SCRIPT = "input keyevent KEYCODE_SLEEP"
    }
}
