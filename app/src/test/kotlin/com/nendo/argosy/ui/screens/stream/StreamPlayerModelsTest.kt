package com.nendo.argosy.ui.screens.stream

import com.nendo.argosy.data.remote.romm.RomMStreamSlotCapabilities
import com.nendo.argosy.data.streaming.StreamDisc
import com.nendo.argosy.data.streaming.StreamPhase
import com.nendo.argosy.data.streaming.StreamSession
import com.nendo.argosy.data.streaming.StreamTarget
import com.nendo.argosy.ui.dualscreen.dashboard.DashboardQuickState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamPlayerModelsTest {

    private val autosaveOnly = RomMStreamSlotCapabilities(maxSlots = 0, hasAutosave = true, autosaveSlot = 10)

    private fun session(
        capabilities: RomMStreamSlotCapabilities = autosaveOnly,
        supportsLiveStates: Boolean = false,
        discs: List<StreamDisc> = emptyList(),
        isHost: Boolean = true,
        lastStateSavedAtMillis: Long? = 1_000L
    ) = StreamSession(
        target = StreamTarget(
            gameId = 11,
            rommId = 42,
            title = "Chrono Cross",
            platform = "psx",
            containerLabel = "Living room PC",
            capabilities = capabilities,
            supportsLiveStates = supportsLiveStates,
            discs = discs
        ),
        containerKey = "box-1",
        claimedAt = "2026-11-13T09:00:00+00:00",
        isHost = isHost,
        phase = StreamPhase.PLAYING,
        roomUrl = "https://romm.example/stream/psx/room",
        extractionPhase = null,
        startedAtMillis = 0L,
        taskId = null,
        currentDiscId = null,
        lastStateSavedAtMillis = lastStateSavedAtMillis,
        endReason = null
    )

    private val twoDiscs = listOf(StreamDisc(1, "Disc 1.chd"), StreamDisc(2, "Disc 2.chd"))

    @Test
    fun `a container without live states offers no states, disc or achievements on the rail`() {
        val controls = StreamPlayerUiState.sessionControlsFor(session())

        assertFalse(controls.statesAvailable)
        assertTrue(controls.quickStates.isEmpty())
        assertFalse(controls.discSwapAvailable)
        assertFalse(controls.achievementsAvailable)
        assertFalse(controls.cheatsAvailable)
        assertFalse(controls.screenshotAvailable)
        assertTrue(controls.settingsAvailable)
    }

    @Test
    fun `a container with live states offers states and the last saved one`() {
        val controls = StreamPlayerUiState.sessionControlsFor(session(supportsLiveStates = true))

        assertTrue(controls.statesAvailable)
        assertEquals(listOf(DashboardQuickState(10, 1_000L, null)), controls.quickStates)
        assertFalse(controls.achievementsAvailable)
    }

    @Test
    fun `live states with neither slots nor autosave offer no states`() {
        val controls = StreamPlayerUiState.sessionControlsFor(
            session(capabilities = RomMStreamSlotCapabilities(), supportsLiveStates = true)
        )

        assertFalse(controls.statesAvailable)
        assertTrue(controls.quickStates.isEmpty())
    }

    @Test
    fun `the disc row needs disc swap support and more than one disc`() {
        val swappable = autosaveOnly.copy(supportsDiscSwap = true)

        assertTrue(StreamPlayerUiState.sessionControlsFor(session(capabilities = swappable, discs = twoDiscs)).discSwapAvailable)
        assertFalse(StreamPlayerUiState.sessionControlsFor(session(capabilities = swappable, discs = twoDiscs.take(1))).discSwapAvailable)
        assertFalse(StreamPlayerUiState.sessionControlsFor(session(discs = twoDiscs)).discSwapAvailable)
    }

    @Test
    fun `the controls menu has no state rows without live states and no mute row`() {
        val rows = StreamPlayerUiState.rowsFor(session())

        assertEquals(listOf(StreamControlRow.RESUME, StreamControlRow.SAVE_AND_EXIT), rows)
    }

    @Test
    fun `a joined stream offers leave instead of save and exit`() {
        val rows = StreamPlayerUiState.rowsFor(session(isHost = false, supportsLiveStates = true))

        assertEquals(listOf(StreamControlRow.RESUME, StreamControlRow.LEAVE), rows)
    }

    @Test
    fun `a playing stream stays on the starting stage until the page is revealed`() {
        assertEquals(StreamPlayerStage.LAUNCHING, StreamPlayerUiState.stageOf(StreamPhase.PLAYING, pageRevealed = false))
        assertEquals(StreamPlayerStage.PLAYING, StreamPlayerUiState.stageOf(StreamPhase.PLAYING, pageRevealed = true))
        assertEquals(StreamPlayerStage.LAUNCHING, StreamPlayerUiState.stageOf(StreamPhase.LAUNCHING, pageRevealed = true))
        assertEquals(StreamPlayerStage.ENDED, StreamPlayerUiState.stageOf(StreamPhase.ENDED, pageRevealed = false))
    }

    @Test
    fun `the page stays hidden while no video has been seen`() {
        assertFalse(StreamPageReveal.shouldReveal(loadStartedAtMillis = 0L, videoSinceMillis = null, nowMillis = 5_000L))
    }

    @Test
    fun `the page is revealed once video has been showing for the settle time`() {
        val since = 3_000L

        assertFalse(StreamPageReveal.shouldReveal(0L, since, since + StreamPageReveal.SETTLE_MS - 1))
        assertTrue(StreamPageReveal.shouldReveal(0L, since, since + StreamPageReveal.SETTLE_MS))
    }

    @Test
    fun `the page is revealed at the timeout even without video`() {
        val started = 1_000L

        assertFalse(StreamPageReveal.shouldReveal(started, null, started + StreamPageReveal.TIMEOUT_MS - 1))
        assertTrue(StreamPageReveal.shouldReveal(started, null, started + StreamPageReveal.TIMEOUT_MS))
    }
}
