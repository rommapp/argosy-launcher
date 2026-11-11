package com.nendo.argosy.libretro

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.VideogameAssetOff
import com.nendo.argosy.R
import com.nendo.argosy.core.input.ControllerChange
import com.nendo.argosy.core.input.ControllerEvent
import com.nendo.argosy.core.input.ConnectedPad
import com.nendo.argosy.core.notification.CONTROLLERS_NOTICE_KEY
import com.nendo.argosy.core.notification.ControllerBurst
import com.nendo.argosy.core.notification.ControllerBurstCard
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.PadBatteryWatch
import com.nendo.argosy.core.notification.PadNotice
import com.nendo.argosy.core.notification.PadNoticeKind
import com.nendo.argosy.core.notification.padBatteryNoticeKey
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.notification.InGameNotice
import com.nendo.argosy.ui.notification.InGameNoticeIcon
import com.nendo.argosy.ui.notification.NOTICE_GLYPH_MARKER
import com.nendo.argosy.ui.notification.NotificationSeverity
import java.util.TreeMap

const val CONTROLLER_PAUSE_NOTICE_KEY = "controller-paused"

enum class PadIntent { CONFIRM, BACK, OTHER }

/**
 * What the built-in game gives [InGameControllerNotices]: its seats, its pause, its notice host.
 */
interface InGameControllerHost {
    val isNetplay: Boolean
    val isMenuOpen: Boolean
    fun seatOf(controllerId: String): Int?
    fun takeSeat(controllerId: String, port: Int)
    fun holdPause(held: Boolean)
    fun openMenu()
    fun show(notice: InGameNotice, onDismissed: () -> Unit = {})
}

/**
 * Controller notices inside a built-in game, and the pause that holds a player's seat while its pad
 * is away. Pads present when the session started never announce themselves; built-in pads never
 * produce a connect, disconnect or battery card. With [informational] off only the pause and seat
 * cards show. Every call runs on the main thread.
 */
class InGameControllerNotices(
    private val host: InGameControllerHost,
    private val informational: Boolean,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class LostSeat(val controllerId: String, val name: String)
    private data class Offer(val controllerId: String, val name: String, val port: Int)

    private val arrivals = mutableMapOf<String, String>()
    private val releasedSeats = mutableMapOf<String, Int>()
    private val lostSeats = TreeMap<Int, LostSeat>()
    private val swallowedKeys = mutableSetOf<Pair<String, Int>>()
    private val burst = ControllerBurst()
    private val batteryWatch = PadBatteryWatch()
    private var offer: Offer? = null

    var holdsPause: Boolean = false
        private set

    fun onEvent(event: ControllerEvent) {
        when (event.change) {
            ControllerChange.CONNECTED -> onConnected(event.controller)
            ControllerChange.DISCONNECTED -> onDisconnected(event.controller)
        }
    }

    fun onSeatsReleased(seats: Map<String, Int>) {
        releasedSeats.putAll(seats)
    }

    fun onSeatClaimed(controllerId: String, port: Int) {
        val name = arrivals.remove(controllerId) ?: return
        if (holdsPause) return
        showBurst(PadNotice(controllerId, name, PadNoticeKind.JOINED, port), NotificationDuration.SHORT.ms)
    }

    fun onControllers(controllers: Collection<ConnectedPad>) {
        batteryWatch.newlyLow(controllers).forEach { pad ->
            if (!informational) return@forEach
            host.show(
                InGameNotice(
                    severity = NotificationSeverity.WARNING,
                    title = NotificationText.Res(R.string.ingame_controller_battery_low_title),
                    body = NotificationText.Raw(pad.name),
                    durationMs = NotificationDuration.MEDIUM.ms,
                    key = padBatteryNoticeKey(pad.controllerId),
                    icon = InGameNoticeIcon.Glyph(Icons.Filled.BatteryAlert),
                    badge = host.seatOf(pad.controllerId)?.let(::playerBadge)
                )
            )
        }
    }

    /**
     * Whether a key from this pad is held back from the game and menus. While a seat is lost and no
     * menu is open, an unseated pad's press offers it the seat, its confirm takes the seat, its back
     * withdraws the offer, and a back from a pad with no offer opens the menu.
     */
    fun consumesKey(controllerId: String, name: String, keyCode: Int, isDown: Boolean, intent: PadIntent): Boolean {
        val pressed = controllerId to keyCode
        if (!isDown) return swallowedKeys.remove(pressed)
        if (pressed in swallowedKeys) return true
        if (!interceptsPad(controllerId)) return false
        swallowedKeys += pressed
        val current = offer
        when {
            current != null && current.controllerId == controllerId -> when (intent) {
                PadIntent.CONFIRM -> takeOfferedSeat(current)
                PadIntent.BACK -> withdrawOffer()
                PadIntent.OTHER -> Unit
            }
            intent == PadIntent.BACK -> host.openMenu()
            else -> {
                offer = Offer(controllerId, name, lostSeats.firstKey())
                showPaused()
            }
        }
        return true
    }

    fun consumesMotion(controllerId: String): Boolean = interceptsPad(controllerId)

    private fun interceptsPad(controllerId: String): Boolean =
        lostSeats.isNotEmpty() && !host.isMenuOpen && host.seatOf(controllerId) == null

    private fun onConnected(pad: ConnectedPad) {
        releasedSeats.remove(pad.controllerId)
        val returningPort = lostSeats.entries.firstOrNull { it.value.controllerId == pad.controllerId }?.key
        if (returningPort != null) {
            if (offer?.port == returningPort) offer = null
            host.takeSeat(pad.controllerId, returningPort)
            lostSeats.remove(returningPort)
            onSeatFilled(returningPort, pad.name)
            return
        }
        if (pad.builtIn) return
        val seat = host.seatOf(pad.controllerId)
        if (seat == null) arrivals[pad.controllerId] = pad.name
        if (holdsPause) return
        if (seat != null) {
            showBurst(PadNotice(pad.controllerId, pad.name, PadNoticeKind.JOINED, seat), NotificationDuration.SHORT.ms)
        } else {
            showBurst(PadNotice(pad.controllerId, pad.name, PadNoticeKind.WAITING), NotificationDuration.MEDIUM.ms)
        }
    }

    private fun onDisconnected(pad: ConnectedPad) {
        val seat = releasedSeats.remove(pad.controllerId) ?: host.seatOf(pad.controllerId)
        arrivals.remove(pad.controllerId)
        swallowedKeys.removeAll { it.first == pad.controllerId }
        if (offer?.controllerId == pad.controllerId) {
            offer = null
            if (lostSeats.isNotEmpty()) showPaused()
        }
        if (pad.builtIn) return
        if (seat == null || host.isNetplay) {
            if (!holdsPause) {
                showBurst(PadNotice(pad.controllerId, pad.name, PadNoticeKind.DISCONNECTED, seat), NotificationDuration.MEDIUM.ms)
            }
            return
        }
        lostSeats[seat] = LostSeat(pad.controllerId, pad.name)
        if (!holdsPause) {
            holdsPause = true
            host.holdPause(true)
        }
        showPaused()
    }

    private fun takeOfferedSeat(taken: Offer) {
        offer = null
        host.takeSeat(taken.controllerId, taken.port)
        lostSeats.remove(taken.port)
        onSeatFilled(taken.port, taken.name)
    }

    private fun withdrawOffer() {
        offer = null
        showPaused()
    }

    private fun onSeatFilled(port: Int, name: String) {
        if (lostSeats.isNotEmpty()) {
            showPaused()
            return
        }
        host.show(
            InGameNotice(
                severity = NotificationSeverity.SUCCESS,
                title = NotificationText.Res(R.string.ingame_controller_joined_title, listOf(port + 1)),
                body = NotificationText.Raw(name),
                durationMs = NotificationDuration.SHORT.ms,
                key = CONTROLLER_PAUSE_NOTICE_KEY,
                icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports),
                badge = playerBadge(port)
            ),
            onDismissed = ::releasePauseIfResolved
        )
    }

    private fun releasePauseIfResolved() {
        if (lostSeats.isNotEmpty() || !holdsPause) return
        holdsPause = false
        host.holdPause(false)
    }

    private fun showPaused() {
        val current = offer
        val notice = if (current != null) {
            InGameNotice(
                severity = NotificationSeverity.WARNING,
                title = NotificationText.Res(R.string.ingame_controller_offer_title, listOf(current.port + 1)),
                body = NotificationText.Res(R.string.ingame_controller_offer_body, listOf(NOTICE_GLYPH_MARKER)),
                bodyGlyph = InputButton.A,
                durationMs = null,
                key = CONTROLLER_PAUSE_NOTICE_KEY,
                icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports),
                badge = playerBadge(current.port)
            )
        } else {
            InGameNotice(
                severity = NotificationSeverity.WARNING,
                title = NotificationText.Res(R.string.ingame_controller_paused_title),
                body = NotificationText.Res(R.string.ingame_controller_paused_body),
                durationMs = null,
                key = CONTROLLER_PAUSE_NOTICE_KEY,
                icon = InGameNoticeIcon.Glyph(Icons.Filled.Pause),
                badge = if (lostSeats.size == 1) playerBadge(lostSeats.firstKey()) else countBadge(lostSeats.size)
            )
        }
        host.show(notice)
    }

    private fun showBurst(pad: PadNotice, baseMs: Long) {
        val card = burst.add(pad, baseMs, clock())
        if (informational) host.show(card.toNotice())
    }

    private fun ControllerBurstCard.toNotice(): InGameNotice {
        val single = pads.singleOrNull()
        val notice = if (single != null) singleNotice(single) else burstNotice(pads)
        return notice.copy(durationMs = durationMs, key = CONTROLLERS_NOTICE_KEY)
    }

    private fun singleNotice(pad: PadNotice): InGameNotice = when (pad.kind) {
        PadNoticeKind.DISCONNECTED -> InGameNotice(
            severity = NotificationSeverity.WARNING,
            title = NotificationText.Res(R.string.ingame_controller_disconnected_title),
            body = NotificationText.Raw(pad.name),
            icon = InGameNoticeIcon.Glyph(Icons.Filled.VideogameAssetOff),
            badge = pad.seat?.let(::playerBadge)
        )
        PadNoticeKind.JOINED -> InGameNotice(
            severity = NotificationSeverity.SUCCESS,
            title = NotificationText.Res(R.string.ingame_controller_joined_title, listOf((pad.seat ?: 0) + 1)),
            body = NotificationText.Raw(pad.name),
            icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports),
            badge = pad.seat?.let(::playerBadge)
        )
        PadNoticeKind.WAITING, PadNoticeKind.CONNECTED -> InGameNotice(
            severity = NotificationSeverity.INFO,
            title = NotificationText.Res(R.string.ingame_controller_join_prompt_title),
            body = NotificationText.Raw(pad.name),
            icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports)
        )
    }

    private fun burstNotice(pads: List<PadNotice>): InGameNotice {
        val count = pads.size
        if (pads.first().kind.isDisconnect) {
            return InGameNotice(
                severity = NotificationSeverity.WARNING,
                title = NotificationText.Plural(R.plurals.ingame_controller_burst_disconnected_title, count, listOf(count)),
                body = playersBody(pads),
                icon = InGameNoticeIcon.Glyph(Icons.Filled.VideogameAssetOff),
                badge = countBadge(count)
            )
        }
        val waiting = pads.count { it.kind != PadNoticeKind.JOINED }
        if (waiting == 0) {
            return InGameNotice(
                severity = NotificationSeverity.SUCCESS,
                title = NotificationText.Plural(R.plurals.ingame_controller_burst_joined_title, count, listOf(count)),
                body = playersBody(pads),
                icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports),
                badge = countBadge(count)
            )
        }
        return InGameNotice(
            severity = NotificationSeverity.INFO,
            title = NotificationText.Plural(R.plurals.ingame_controller_burst_connected_title, count, listOf(count)),
            body = NotificationText.Plural(R.plurals.ingame_controller_burst_waiting_body, waiting, listOf(waiting)),
            icon = InGameNoticeIcon.Glyph(Icons.Filled.SportsEsports),
            badge = countBadge(count)
        )
    }

    private fun playersBody(pads: List<PadNotice>): NotificationText? {
        val players = pads.mapNotNull { it.seat?.plus(1) }.sorted()
        if (players.size != pads.size) return null
        return when (players.size) {
            2 -> NotificationText.Res(R.string.ingame_controller_players_two, players)
            3 -> NotificationText.Res(R.string.ingame_controller_players_three, players)
            else -> null
        }
    }

    private fun playerBadge(port: Int): NotificationText =
        NotificationText.Res(R.string.ingame_controller_badge_player, listOf(port + 1))

    private fun countBadge(count: Int): NotificationText =
        NotificationText.Res(R.string.ingame_controller_badge_count, listOf(count))
}
