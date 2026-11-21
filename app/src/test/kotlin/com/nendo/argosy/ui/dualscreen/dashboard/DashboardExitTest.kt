package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardExitTest {

    private val loaded = CompanionInGameState(gameId = 1L, isLoaded = true)

    @Test
    fun `a built-in game offers return or close`() {
        assertEquals(DashboardExit.RETURN_OR_CLOSE, dashboardExitOf(loaded.copy(quickActionsAvailable = true)))
    }

    @Test
    fun `a built-in game in a game slot offers return or close without quick actions`() {
        assertEquals(DashboardExit.RETURN_OR_CLOSE, dashboardExitOf(loaded.copy(builtInGame = true)))
    }

    @Test
    fun `an external game offers return or close where root can stop it`() {
        assertEquals(
            DashboardExit.RETURN_OR_CLOSE,
            dashboardExitOf(loaded.copy(externalEmulator = true, canForceClose = true))
        )
    }

    @Test
    fun `an external game without root only returns to Argosy`() {
        assertEquals(DashboardExit.RETURN_ONLY, dashboardExitOf(loaded.copy(externalEmulator = true)))
    }

    @Test
    fun `a session with neither shows no exit`() {
        assertEquals(DashboardExit.NONE, dashboardExitOf(loaded))
    }
}
