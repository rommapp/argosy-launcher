package com.nendo.argosy.ui.notification

import com.nendo.argosy.R
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.ControllerChange
import com.nendo.argosy.core.input.ControllerEvent
import com.nendo.argosy.core.input.ConnectedPad
import com.nendo.argosy.core.notification.CONTROLLERS_NOTICE_KEY
import com.nendo.argosy.core.notification.ControllerBurst
import com.nendo.argosy.core.notification.ControllerBurstCard
import com.nendo.argosy.core.notification.NotificationCategory
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.PadBatteryWatch
import com.nendo.argosy.core.notification.PadNotice
import com.nendo.argosy.core.notification.PadNoticeKind
import com.nendo.argosy.core.notification.padBatteryNoticeKey
import com.nendo.argosy.libretro.slot.ResumedGameRouter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

fun controllerNoticeFor(card: ControllerBurstCard): ControllerNoticeContent? {
    val pads = card.pads
    val single = pads.singleOrNull()
    val disconnect = pads.firstOrNull()?.kind?.isDisconnect ?: return null
    val type = if (disconnect) NotificationType.WARNING else NotificationType.INFO
    val title = when {
        single != null && disconnect -> NotificationText.Res(R.string.notif_controller_disconnected_title)
        single != null -> NotificationText.Res(R.string.notif_controller_connected_title)
        disconnect -> NotificationText.Plural(R.plurals.notif_controller_burst_disconnected_title, pads.size, listOf(pads.size))
        else -> NotificationText.Plural(R.plurals.notif_controller_burst_connected_title, pads.size, listOf(pads.size))
    }
    return ControllerNoticeContent(
        type = type,
        title = title,
        subtitle = single?.let { NotificationText.Raw(it.name) },
        duration = card.tier
    )
}

data class ControllerNoticeContent(
    val type: NotificationType,
    val title: NotificationText,
    val subtitle: NotificationText?,
    val duration: NotificationDuration
)

/**
 * Posts the main process's controller cards from [ConnectedControllerTracker]: plain connects and
 * disconnects with the pad's name, and a pad's low battery. Silent while a built-in game is in
 * front, which draws its own controller cards.
 */
@Singleton
class ControllerNoticePoster @Inject constructor(
    private val controllerTracker: ConnectedControllerTracker,
    private val notificationManager: NotificationManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val burst = ControllerBurst()
    private val batteryWatch = PadBatteryWatch()
    private var collectors: List<Job> = emptyList()

    fun start() {
        if (collectors.isNotEmpty()) return
        collectors = listOf(
            scope.launch { controllerTracker.controllerEvents.collect { event -> post(event) } },
            scope.launch { controllerTracker.controllers.collect { pads -> postBattery(pads) } }
        )
    }

    private fun post(event: ControllerEvent) {
        val pad = event.controller
        if (pad.builtIn || ResumedGameRouter.hasGameInFront) return
        val kind = if (event.change == ControllerChange.CONNECTED) PadNoticeKind.CONNECTED else PadNoticeKind.DISCONNECTED
        val card = burst.add(
            PadNotice(pad.controllerId, pad.name, kind),
            baseMs = NotificationDuration.MEDIUM.ms,
            now = System.currentTimeMillis()
        )
        val content = controllerNoticeFor(card) ?: return
        notificationManager.show(
            title = content.title,
            subtitle = content.subtitle,
            type = content.type,
            duration = content.duration,
            key = CONTROLLERS_NOTICE_KEY,
            immediate = true,
            category = NotificationCategory.CONTROLLER
        )
    }

    private fun postBattery(pads: List<ConnectedPad>) {
        val low = batteryWatch.newlyLow(pads)
        if (ResumedGameRouter.hasGameInFront) return
        low.forEach { pad ->
            notificationManager.show(
                title = NotificationText.Res(R.string.notif_controller_battery_low_title),
                subtitle = NotificationText.Raw(pad.name),
                type = NotificationType.WARNING,
                duration = NotificationDuration.MEDIUM,
                key = padBatteryNoticeKey(pad.controllerId),
                immediate = true,
                category = NotificationCategory.CONTROLLER
            )
        }
    }
}
