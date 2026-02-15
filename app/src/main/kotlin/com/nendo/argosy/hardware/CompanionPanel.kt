package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.time.Duration
import java.time.Instant

enum class CompanionPanel(val label: String) {
    DASHBOARD("Dashboard")
}

data class CompanionInGameState(
    val gameId: Long = -1,
    val title: String = "",
    val coverPath: String? = null,
    val platformName: String = "",
    val developer: String? = null,
    val releaseYear: Int? = null,
    val playTimeMinutes: Int = 0,
    val playCount: Int = 0,
    val achievementCount: Int = 0,
    val earnedAchievementCount: Int = 0,
    val sessionStartTimeMillis: Long = 0,
    val channelName: String? = null,
    val isHardcore: Boolean = false,
    val isDirty: Boolean = false,
    val currentPanel: CompanionPanel = CompanionPanel.DASHBOARD,
    val isLoaded: Boolean = false
)

class CompanionSessionTimer {
    private var screenOnDuration: Duration = Duration.ZERO
    private var lastScreenOnTime: Instant? = null
    private var isScreenOn = true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> onScreenOn()
            }
        }
    }

    fun start(context: Context) {
        screenOnDuration = Duration.ZERO
        lastScreenOnTime = Instant.now()
        isScreenOn = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        context.registerReceiver(receiver, filter)
    }

    fun stop(context: Context) {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) { }
    }

    fun getActiveMillis(): Long {
        var total = screenOnDuration
        if (isScreenOn && lastScreenOnTime != null) {
            total = total.plus(Duration.between(lastScreenOnTime, Instant.now()))
        }
        return total.toMillis()
    }

    private fun onScreenOn() {
        if (!isScreenOn) {
            isScreenOn = true
            lastScreenOnTime = Instant.now()
        }
    }

    private fun onScreenOff() {
        if (isScreenOn && lastScreenOnTime != null) {
            isScreenOn = false
            val elapsed = Duration.between(lastScreenOnTime, Instant.now())
            screenOnDuration = screenOnDuration.plus(elapsed)
            lastScreenOnTime = null
        }
    }
}
