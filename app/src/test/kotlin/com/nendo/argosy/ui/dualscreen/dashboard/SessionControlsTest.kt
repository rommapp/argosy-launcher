package com.nendo.argosy.ui.dualscreen.dashboard

import com.nendo.argosy.hardware.CompanionInGameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControlsTest {

    private val running = CompanionInGameState(gameId = 1L, isLoaded = true, quickActionsAvailable = true, achievementCount = 40)

    @Test
    fun `a game with states offers them when it publishes quick actions`() {
        assertTrue(SessionControls().offersStates(running))
    }

    @Test
    fun `a game without states never offers them`() {
        assertFalse(SessionControls(statesAvailable = false).offersStates(running))
    }

    @Test
    fun `hardcore and a game without quick actions offer no states`() {
        assertFalse(SessionControls().offersStates(running.copy(isHardcore = true)))
        assertFalse(SessionControls().offersStates(running.copy(quickActionsAvailable = false)))
    }

    @Test
    fun `a game with achievements counts the game's set`() {
        assertEquals(40, SessionControls().achievementTotal(running, emptyList()))
    }

    @Test
    fun `a game without achievements counts none even when the game has a set`() {
        assertEquals(0, SessionControls(achievementsAvailable = false).achievementTotal(running, emptyList()))
    }
}
