package com.nendo.argosy.ui.notification

import androidx.compose.ui.graphics.toArgb
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationCategory
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.domain.model.BatteryEvent
import com.nendo.argosy.domain.model.BatteryStep
import com.nendo.argosy.hardware.BatteryMonitor
import com.nendo.argosy.libretro.slot.ResumedGameRouter
import com.nendo.argosy.ui.theme.generated.ColorTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

private const val LEVEL_KEY = "battery-level"
private const val CHARGER_KEY = "battery"
private const val MINUTES_PER_HOUR = 60L

enum class BatteryNoticeKind {
    LOW,
    CRITICAL,
    CRITICAL_GAME_SAVED,
    CHARGING,
    UNPLUGGED
}

/**
 * The one card a battery reading earns, or null. A critical reading outranks a low one in the same
 * reading; the 3 and 1 percent readings earn none. Charger cards need a game running.
 */
fun batteryNoticeFor(
    events: List<BatteryEvent>,
    builtInGameInFront: Boolean,
    gameRunning: Boolean
): BatteryNoticeKind? = when {
    BatteryEvent.CRITICAL_5 in events ->
        if (builtInGameInFront) BatteryNoticeKind.CRITICAL_GAME_SAVED else BatteryNoticeKind.CRITICAL
    BatteryEvent.LOW_15 in events -> BatteryNoticeKind.LOW
    !gameRunning -> null
    BatteryEvent.PLUGGED in events -> BatteryNoticeKind.CHARGING
    BatteryEvent.UNPLUGGED in events -> BatteryNoticeKind.UNPLUGGED
    else -> null
}

/**
 * Posts the main process's battery cards from [BatteryMonitor]. The built-in game's own save at
 * the critical levels is [com.nendo.argosy.libretro.BatterySaveGuard]'s job, in the game's process.
 */
@Singleton
class BatteryNoticePoster @Inject constructor(
    private val batteryMonitor: BatteryMonitor,
    private val notificationManager: NotificationManager,
    private val playSessionTracker: PlaySessionTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null

    fun start() {
        if (collector != null) return
        collector = scope.launch {
            batteryMonitor.events.collect { step -> post(step) }
        }
        batteryMonitor.start()
    }

    private fun post(step: BatteryStep) {
        val level = step.sample.level
        val kind = batteryNoticeFor(
            events = step.events,
            builtInGameInFront = ResumedGameRouter.hasGameInFront,
            gameRunning = playSessionTracker.activeSession.value != null
        ) ?: return
        when (kind) {
            BatteryNoticeKind.LOW -> notificationManager.show(
                title = NotificationText.Res(R.string.notif_battery_low_title, listOf(level)),
                type = NotificationType.WARNING,
                duration = NotificationDuration.MEDIUM,
                key = LEVEL_KEY,
                immediate = true,
                category = NotificationCategory.BATTERY
            )
            BatteryNoticeKind.CRITICAL, BatteryNoticeKind.CRITICAL_GAME_SAVED -> notificationManager.show(
                title = NotificationText.Res(R.string.notif_battery_critical_title, listOf(level)),
                subtitle = if (kind == BatteryNoticeKind.CRITICAL_GAME_SAVED) {
                    NotificationText.Res(R.string.notif_battery_critical_saved)
                } else {
                    null
                },
                type = NotificationType.ERROR,
                duration = NotificationDuration.LONG,
                key = LEVEL_KEY,
                immediate = true,
                category = NotificationCategory.BATTERY
            )
            BatteryNoticeKind.CHARGING -> notificationManager.show(
                title = NotificationText.Res(R.string.notif_battery_charging_title, listOf(level)),
                subtitle = remainingText(
                    batteryMonitor.chargeTimeRemaining(),
                    R.string.notif_battery_charging_full_in_hours_minutes,
                    R.string.notif_battery_charging_full_in_minutes
                ),
                type = NotificationType.INFO,
                duration = NotificationDuration.SHORT,
                key = CHARGER_KEY,
                accentColor = ColorTokens.Domain.Battery.charging.toArgb(),
                category = NotificationCategory.CHARGING
            )
            BatteryNoticeKind.UNPLUGGED -> notificationManager.show(
                title = NotificationText.Res(R.string.notif_battery_unplugged_title, listOf(level)),
                subtitle = remainingText(
                    batteryMonitor.dischargeTimeRemaining(),
                    R.string.notif_battery_unplugged_left_hours_minutes,
                    R.string.notif_battery_unplugged_left_minutes
                ),
                type = NotificationType.INFO,
                duration = NotificationDuration.SHORT,
                key = CHARGER_KEY,
                category = NotificationCategory.CHARGING
            )
        }
    }

    private fun remainingText(remaining: Duration?, hoursMinutesRes: Int, minutesRes: Int): NotificationText? {
        val totalMinutes = remaining?.toMinutes()?.takeIf { it > 0 } ?: return null
        val hours = (totalMinutes / MINUTES_PER_HOUR).toInt()
        val minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
        return if (hours > 0) {
            NotificationText.Res(hoursMinutesRes, listOf(hours, minutes))
        } else {
            NotificationText.Res(minutesRes, listOf(minutes))
        }
    }
}
