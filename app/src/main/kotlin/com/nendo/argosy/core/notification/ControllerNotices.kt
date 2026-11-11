package com.nendo.argosy.core.notification

import com.nendo.argosy.core.input.ConnectedPad
import com.nendo.argosy.domain.model.BatteryThresholds

const val CONTROLLERS_NOTICE_KEY = "controllers"
const val CONTROLLER_BURST_WINDOW_MS = 2000L

fun padBatteryNoticeKey(controllerId: String): String = "pad-battery-$controllerId"

enum class PadNoticeKind(val isDisconnect: Boolean) {
    CONNECTED(isDisconnect = false),
    WAITING(isDisconnect = false),
    JOINED(isDisconnect = false),
    DISCONNECTED(isDisconnect = true)
}

/**
 * One pad's part in a controller card. [seat] is the zero-based player seat, null for a pad that
 * holds none.
 */
data class PadNotice(
    val controllerId: String,
    val name: String,
    val kind: PadNoticeKind,
    val seat: Int? = null
)

/**
 * What the shared controller card shows after the latest notice. [durationMs] counts from that
 * notice.
 */
data class ControllerBurstCard(val pads: List<PadNotice>, val durationMs: Long) {
    val tier: NotificationDuration
        get() = NotificationDuration.entries.lastOrNull { it.ms <= durationMs } ?: NotificationDuration.SHORT
}

/**
 * Folds controller notices that arrive within [windowMs] of the previous one into one card, one
 * entry per pad. Each notice restarts the card's time, which never runs past [capMs] from the
 * notice that opened the burst. A disconnect and a connect never share a burst.
 */
class ControllerBurst(
    private val windowMs: Long = CONTROLLER_BURST_WINDOW_MS,
    private val capMs: Long = NotificationDuration.LONG.ms
) {
    private val pads = LinkedHashMap<String, PadNotice>()
    private var openedAt = 0L
    private var lastAt: Long? = null
    private var disconnects = false

    fun add(notice: PadNotice, baseMs: Long, now: Long): ControllerBurstCard {
        val last = lastAt
        val expired = last == null || now - last > windowMs || now - openedAt >= capMs
        if (expired || notice.kind.isDisconnect != disconnects) {
            pads.clear()
            openedAt = now
            disconnects = notice.kind.isDisconnect
        }
        lastAt = now
        pads[notice.controllerId] = notice
        return ControllerBurstCard(
            pads = pads.values.toList(),
            durationMs = minOf(baseMs, capMs - (now - openedAt))
        )
    }
}

/**
 * Picks the pads that have just reached [lowPercent], each once for the life of the watch. Built-in
 * pads and pads that report no battery are never picked.
 */
class PadBatteryWatch(private val lowPercent: Int = BatteryThresholds.LOW_PERCENT) {
    private val warned = mutableSetOf<String>()

    fun newlyLow(controllers: Collection<ConnectedPad>): List<ConnectedPad> =
        controllers.filter { pad ->
            val level = pad.batteryPercent
            !pad.builtIn && level != null && level <= lowPercent && warned.add(pad.controllerId)
        }
}
