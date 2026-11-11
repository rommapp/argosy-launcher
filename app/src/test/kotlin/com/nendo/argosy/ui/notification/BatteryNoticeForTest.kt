package com.nendo.argosy.ui.notification

import com.nendo.argosy.domain.model.BatteryEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryNoticeForTest {

    private val bothBooleans = listOf(true, false)

    @Test
    fun `critical says the game saved only when a built-in game is in front`() {
        bothBooleans.forEach { gameRunning ->
            assertEquals(
                BatteryNoticeKind.CRITICAL_GAME_SAVED,
                batteryNoticeFor(listOf(BatteryEvent.CRITICAL_5), builtInGameInFront = true, gameRunning = gameRunning)
            )
            assertEquals(
                BatteryNoticeKind.CRITICAL,
                batteryNoticeFor(listOf(BatteryEvent.CRITICAL_5), builtInGameInFront = false, gameRunning = gameRunning)
            )
        }
    }

    @Test
    fun `critical outranks low in the same reading`() {
        assertEquals(
            BatteryNoticeKind.CRITICAL,
            batteryNoticeFor(
                listOf(BatteryEvent.LOW_15, BatteryEvent.CRITICAL_5, BatteryEvent.CRITICAL_3),
                builtInGameInFront = false,
                gameRunning = false
            )
        )
    }

    @Test
    fun `low shows whether or not a game runs`() {
        bothBooleans.forEach { inFront ->
            bothBooleans.forEach { gameRunning ->
                assertEquals(
                    BatteryNoticeKind.LOW,
                    batteryNoticeFor(listOf(BatteryEvent.LOW_15), inFront, gameRunning)
                )
            }
        }
    }

    @Test
    fun `the three and one percent re-saves earn no card`() {
        bothBooleans.forEach { inFront ->
            bothBooleans.forEach { gameRunning ->
                assertNull(batteryNoticeFor(listOf(BatteryEvent.CRITICAL_3), inFront, gameRunning))
                assertNull(batteryNoticeFor(listOf(BatteryEvent.CRITICAL_1), inFront, gameRunning))
            }
        }
    }

    @Test
    fun `charger cards show only while a game runs`() {
        bothBooleans.forEach { inFront ->
            assertEquals(BatteryNoticeKind.CHARGING, batteryNoticeFor(listOf(BatteryEvent.PLUGGED), inFront, true))
            assertEquals(BatteryNoticeKind.UNPLUGGED, batteryNoticeFor(listOf(BatteryEvent.UNPLUGGED), inFront, true))
            assertNull(batteryNoticeFor(listOf(BatteryEvent.PLUGGED), inFront, false))
            assertNull(batteryNoticeFor(listOf(BatteryEvent.UNPLUGGED), inFront, false))
        }
    }
}
