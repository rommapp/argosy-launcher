package com.nendo.argosy.libretro

import android.view.KeyEvent
import com.nendo.argosy.R
import com.nendo.argosy.core.input.ConnectedPad
import com.nendo.argosy.core.input.ControllerChange
import com.nendo.argosy.core.input.ControllerEvent
import com.nendo.argosy.core.notification.CONTROLLERS_NOTICE_KEY
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.padBatteryNoticeKey
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.notification.InGameNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InGameControllerNoticesTest {

    private class FakeHost : InGameControllerHost {
        override var isNetplay: Boolean = false
        override var isMenuOpen: Boolean = false
        val seats = mutableMapOf<String, Int>()
        val shown = mutableListOf<Pair<InGameNotice, () -> Unit>>()
        val pauses = mutableListOf<Boolean>()
        var menusOpened = 0

        override fun seatOf(controllerId: String): Int? = seats[controllerId]
        override fun takeSeat(controllerId: String, port: Int) {
            seats.entries.removeAll { it.value == port }
            seats[controllerId] = port
        }
        override fun holdPause(held: Boolean) {
            pauses += held
        }
        override fun openMenu() {
            menusOpened++
        }
        override fun show(notice: InGameNotice, onDismissed: () -> Unit) {
            shown += notice to onDismissed
        }

        val last: InGameNotice get() = shown.last().first

        fun dismissLast() = shown.last().second()
    }

    private val host = FakeHost()
    private var now = 0L
    private val notices = InGameControllerNotices(host, informational = true, clock = { now })

    private val playerOne = pad("p1", "Player One Pad")
    private val playerTwo = pad("p2", "8BitDo Pro 2")
    private val playerThree = pad("p3", "Xbox Wireless Controller")
    private val spare = pad("spare", "DualSense Wireless Controller")
    private val grip = pad("grip", "Odin Controller", builtIn = true)

    private fun pad(id: String, name: String, builtIn: Boolean = false, battery: Int? = null) =
        ConnectedPad(id, name, builtIn, battery)

    private fun connect(pad: ConnectedPad) = notices.onEvent(ControllerEvent(ControllerChange.CONNECTED, pad))

    private fun disconnectSeated(pad: ConnectedPad) {
        val seat = host.seats.remove(pad.controllerId)
        if (seat != null) notices.onSeatsReleased(mapOf(pad.controllerId to seat))
        notices.onEvent(ControllerEvent(ControllerChange.DISCONNECTED, pad))
    }

    private fun press(pad: ConnectedPad, intent: PadIntent, keyCode: Int = KeyEvent.KEYCODE_BUTTON_A): Boolean =
        notices.consumesKey(pad.controllerId, pad.name, keyCode, isDown = true, intent = intent)

    private fun release(pad: ConnectedPad, keyCode: Int = KeyEvent.KEYCODE_BUTTON_A): Boolean =
        notices.consumesKey(pad.controllerId, pad.name, keyCode, isDown = false, intent = PadIntent.OTHER)

    private fun titleOf(notice: InGameNotice) = notice.title

    private fun seatedPlayers() {
        host.seats[playerOne.controllerId] = 0
        host.seats[playerTwo.controllerId] = 1
        host.seats[playerThree.controllerId] = 2
    }

    @Test
    fun `losing a seated pad pauses the game behind a card that stays`() {
        seatedPlayers()

        disconnectSeated(playerTwo)

        assertEquals(listOf(true), host.pauses)
        assertTrue(notices.holdsPause)
        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_title), titleOf(host.last))
        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_body), host.last.body)
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_player, listOf(2)), host.last.badge)
        assertEquals(CONTROLLER_PAUSE_NOTICE_KEY, host.last.key)
        assertNull(host.last.durationMs)
    }

    @Test
    fun `a press on an unseated pad offers it the seat and never reaches the game`() {
        seatedPlayers()
        disconnectSeated(playerTwo)

        assertTrue(press(spare, PadIntent.OTHER))

        assertEquals(NotificationText.Res(R.string.ingame_controller_offer_title, listOf(2)), titleOf(host.last))
        assertEquals(InputButton.A, host.last.bodyGlyph)
        assertNull(host.last.durationMs)
        assertTrue(release(spare))
    }

    @Test
    fun `confirm on the offering pad takes the seat and the game resumes once the card leaves`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        press(spare, PadIntent.OTHER)
        release(spare)

        assertTrue(press(spare, PadIntent.CONFIRM))

        assertEquals(1, host.seats[spare.controllerId])
        assertEquals(NotificationText.Res(R.string.ingame_controller_joined_title, listOf(2)), titleOf(host.last))
        assertEquals(NotificationDuration.SHORT.ms, host.last.durationMs)
        assertEquals(CONTROLLER_PAUSE_NOTICE_KEY, host.last.key)
        assertEquals(listOf(true), host.pauses)

        host.dismissLast()

        assertEquals(listOf(true, false), host.pauses)
        assertFalse(notices.holdsPause)
    }

    @Test
    fun `confirm on another pad never takes the offered seat`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        press(spare, PadIntent.OTHER)
        val other = pad("other", "Another Pad")

        assertTrue(press(other, PadIntent.CONFIRM))

        assertNull(host.seats[other.controllerId])
        assertNull(host.seats[spare.controllerId])
        assertEquals(NotificationText.Res(R.string.ingame_controller_offer_title, listOf(2)), titleOf(host.last))
        assertTrue(press(spare, PadIntent.CONFIRM, KeyEvent.KEYCODE_BUTTON_B))
        assertNull(host.seats[spare.controllerId])
    }

    @Test
    fun `back on the offering pad withdraws the offer`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        press(spare, PadIntent.OTHER)

        assertTrue(press(spare, PadIntent.BACK, KeyEvent.KEYCODE_BUTTON_B))

        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_title), titleOf(host.last))
        assertNull(host.seats[spare.controllerId])
        assertTrue(notices.holdsPause)
    }

    @Test
    fun `the original pad reconnecting withdraws the offer and gets its seat back`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        press(spare, PadIntent.OTHER)
        release(spare)

        connect(playerTwo)

        assertEquals(1, host.seats[playerTwo.controllerId])
        assertEquals(NotificationText.Res(R.string.ingame_controller_joined_title, listOf(2)), titleOf(host.last))
        host.dismissLast()
        assertEquals(listOf(true, false), host.pauses)
        assertFalse(press(spare, PadIntent.CONFIRM))
        assertNull(host.seats[spare.controllerId])
    }

    @Test
    fun `in netplay a lost seat only shows a card`() {
        seatedPlayers()
        host.isNetplay = true

        disconnectSeated(playerTwo)

        assertEquals(emptyList<Boolean>(), host.pauses)
        assertFalse(notices.holdsPause)
        assertEquals(NotificationText.Res(R.string.ingame_controller_disconnected_title), titleOf(host.last))
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_player, listOf(2)), host.last.badge)
        assertEquals(CONTROLLERS_NOTICE_KEY, host.last.key)
        assertFalse(press(spare, PadIntent.OTHER))
    }

    @Test
    fun `two lost seats share one card and the game waits for both`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        disconnectSeated(playerThree)

        assertEquals(listOf(true), host.pauses)
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_count, listOf(2)), host.last.badge)

        press(spare, PadIntent.OTHER)
        assertEquals(NotificationText.Res(R.string.ingame_controller_offer_title, listOf(2)), titleOf(host.last))
        release(spare)
        press(spare, PadIntent.CONFIRM)

        assertEquals(1, host.seats[spare.controllerId])
        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_title), titleOf(host.last))
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_player, listOf(3)), host.last.badge)
        assertTrue(notices.holdsPause)

        connect(playerThree)
        host.dismissLast()

        assertEquals(2, host.seats[playerThree.controllerId])
        assertEquals(listOf(true, false), host.pauses)
    }

    @Test
    fun `a new loss before the joined card leaves keeps the game paused`() {
        seatedPlayers()
        disconnectSeated(playerTwo)
        connect(playerTwo)
        val joinedDismissal = host.shown.last().second

        disconnectSeated(playerThree)
        joinedDismissal()

        assertEquals(listOf(true), host.pauses)
        assertTrue(notices.holdsPause)
    }

    @Test
    fun `seated pads and any pad while a menu is open pass through`() {
        seatedPlayers()
        disconnectSeated(playerTwo)

        assertFalse(press(playerOne, PadIntent.OTHER))
        assertFalse(notices.consumesMotion(playerOne.controllerId))
        assertTrue(notices.consumesMotion(spare.controllerId))

        host.isMenuOpen = true

        assertFalse(press(spare, PadIntent.OTHER))
        assertFalse(notices.consumesMotion(spare.controllerId))
    }

    @Test
    fun `back on an unseated pad with no offer opens the menu`() {
        seatedPlayers()
        disconnectSeated(playerTwo)

        assertTrue(press(spare, PadIntent.BACK, KeyEvent.KEYCODE_BUTTON_B))

        assertEquals(1, host.menusOpened)
        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_title), titleOf(host.last))
    }

    @Test
    fun `nothing is held back while every seat is filled`() {
        seatedPlayers()

        assertFalse(press(spare, PadIntent.OTHER))
        assertFalse(release(spare))
        assertFalse(notices.consumesMotion(spare.controllerId))
    }

    @Test
    fun `an unseated arrival asks for a press and the press turns the card into a join`() {
        seatedPlayers()

        connect(spare)
        assertEquals(NotificationText.Res(R.string.ingame_controller_join_prompt_title), titleOf(host.last))
        assertEquals(NotificationText.Raw(spare.name), host.last.body)
        assertEquals(CONTROLLERS_NOTICE_KEY, host.last.key)

        now = 900
        notices.onSeatClaimed(spare.controllerId, 3)

        assertEquals(NotificationText.Res(R.string.ingame_controller_joined_title, listOf(4)), titleOf(host.last))
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_player, listOf(4)), host.last.badge)
        assertEquals(CONTROLLERS_NOTICE_KEY, host.last.key)
    }

    @Test
    fun `an arrival with a saved seat joins at once`() {
        host.seats[spare.controllerId] = 1

        connect(spare)

        assertEquals(NotificationText.Res(R.string.ingame_controller_joined_title, listOf(2)), titleOf(host.last))
    }

    @Test
    fun `arrivals within the window collapse into one counted card`() {
        host.seats[playerTwo.controllerId] = 1
        host.seats[playerThree.controllerId] = 2

        connect(playerTwo)
        now = 900
        connect(playerThree)

        assertEquals(
            NotificationText.Plural(R.plurals.ingame_controller_burst_joined_title, 2, listOf(2)),
            titleOf(host.last)
        )
        assertEquals(NotificationText.Res(R.string.ingame_controller_players_two, listOf(2, 3)), host.last.body)
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_count, listOf(2)), host.last.badge)

        now = 1_700
        connect(spare)

        assertEquals(
            NotificationText.Plural(R.plurals.ingame_controller_burst_connected_title, 3, listOf(3)),
            titleOf(host.last)
        )
        assertEquals(NotificationText.Plural(R.plurals.ingame_controller_burst_waiting_body, 1, listOf(1)), host.last.body)
    }

    @Test
    fun `a pad present at session start claiming a seat says nothing`() {
        notices.onSeatClaimed(playerOne.controllerId, 0)

        assertTrue(host.shown.isEmpty())
    }

    @Test
    fun `built-in pads never connect, disconnect or warn of battery`() {
        host.seats[grip.controllerId] = 0

        connect(grip)
        disconnectSeated(grip)
        notices.onControllers(listOf(grip.copy(batteryPercent = 5)))

        assertTrue(host.shown.isEmpty())
        assertEquals(emptyList<Boolean>(), host.pauses)
    }

    @Test
    fun `a low pad warns once for the session`() {
        host.seats[playerTwo.controllerId] = 1
        val low = playerTwo.copy(batteryPercent = 10)

        notices.onControllers(listOf(low))
        notices.onControllers(listOf(low.copy(batteryPercent = 8)))

        assertEquals(1, host.shown.size)
        assertEquals(NotificationText.Res(R.string.ingame_controller_battery_low_title), titleOf(host.last))
        assertEquals(padBatteryNoticeKey(playerTwo.controllerId), host.last.key)
        assertEquals(NotificationText.Res(R.string.ingame_controller_badge_player, listOf(2)), host.last.badge)
    }

    @Test
    fun `with controller notices off only the pause and seat cards show`() {
        val quiet = InGameControllerNotices(host, informational = false, clock = { now })
        seatedPlayers()

        quiet.onEvent(ControllerEvent(ControllerChange.CONNECTED, spare))
        quiet.onControllers(listOf(spare.copy(batteryPercent = 5)))
        assertTrue(host.shown.isEmpty())

        host.seats.remove(playerTwo.controllerId)
        quiet.onSeatsReleased(mapOf(playerTwo.controllerId to 1))
        quiet.onEvent(ControllerEvent(ControllerChange.DISCONNECTED, playerTwo))

        assertEquals(NotificationText.Res(R.string.ingame_controller_paused_title), titleOf(host.last))
        assertEquals(listOf(true), host.pauses)
    }
}
