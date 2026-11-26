package com.nendo.argosy.ui.components.resumeplayer

import com.nendo.argosy.data.music.analysis.TrackAnalysis
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.audio.NowPlayingPillPolicy
import com.nendo.argosy.ui.audio.PlaybackPosition
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResumePlayerControllerTest {

    private class FakeTransport(initial: AmbientPlaybackState) : ResumePlayerTransport {
        override val playback = MutableStateFlow(initial)
        override val analysis: Flow<TrackAnalysis?> = flowOf(null)
        override val stage: Flow<ResumeStagePreferences> = flowOf(ResumeStagePreferences())
        val played = mutableListOf<Int>()
        var skips = 0

        override fun positionSnapshot(): PlaybackPosition? = null
        override suspend fun togglePlayPause() = Unit
        override suspend fun skipNext() {
            skips++
        }

        override suspend fun skipPrevious() {
            skips--
        }

        override suspend fun playAt(position: Int) {
            played += position
        }
    }

    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(UnconfinedTestDispatcher(scheduler) + Job())
    private val pill = mockk<NowPlayingPillPolicy>(relaxed = true)
    private lateinit var transport: FakeTransport
    private lateinit var controller: ResumePlayerController

    private val idleMs = ComponentDefaults.ResumePlayer.panelIdleCollapseMs.toLong()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun queue(index: Int, sourceId: String = "album:a"): AmbientPlaybackState {
        val tracks = (0 until 5).map {
            MusicQueueTrack(id = "t$it", source = MusicTrackSource.Local("/t$it"), title = "T$it")
        }
        return AmbientPlaybackState(index = index, count = tracks.size, tracks = tracks, sourceId = sourceId)
    }

    private fun build(playback: AmbientPlaybackState = queue(index = 2), hasGames: Boolean = true) {
        transport = FakeTransport(playback)
        controller = ResumePlayerController(scope, transport, pill)
        controller.setHasGames(hasGames)
        controller.onVisible()
    }

    private val state get() = controller.uiState.value

    private fun openPanel() {
        assertTrue(controller.enterCard())
        assertTrue(controller.openPanel())
    }

    @Test
    fun `no music shows no card and leaves the page alone`() {
        build(playback = AmbientPlaybackState())
        assertFalse(state.showsCard)
        assertFalse(controller.enterCard())
        assertFalse(controller.skip(1))
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
        verify(exactly = 0) { pill.setSuppressed(any(), true) }
    }

    @Test
    fun `focus enters on the games and up reaches Play`() {
        build()
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
        assertTrue(controller.enterCard())
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        assertEquals(ResumePlayerButton.PLAY_PAUSE, state.button)
    }

    @Test
    fun `down from the card returns to the games`() {
        build()
        controller.enterCard()
        assertTrue(controller.leaveCard())
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
    }

    @Test
    fun `no games starts focus on Play and down stays on the card`() {
        build(hasGames = false)
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        assertEquals(ResumePlayerButton.PLAY_PAUSE, state.button)
        assertFalse(controller.leaveCard())
        assertEquals(ResumePlayerFocus.CARD, state.focus)
    }

    @Test
    fun `games arriving hand focus back to the games`() {
        build(hasGames = false)
        controller.setHasGames(true)
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
    }

    @Test
    fun `card buttons stop at both ends`() {
        build()
        controller.enterCard()
        assertTrue(controller.moveButton(1))
        assertTrue(controller.moveButton(1))
        assertEquals(ResumePlayerButton.QUEUE, state.button)
        assertFalse(controller.moveButton(1))
        assertTrue(controller.moveButton(-1))
        assertTrue(controller.moveButton(-1))
        assertTrue(controller.moveButton(-1))
        assertEquals(ResumePlayerButton.PREVIOUS, state.button)
        assertFalse(controller.moveButton(-1))
    }

    @Test
    fun `the last card button opens the running order panel`() {
        build()
        controller.enterCard()
        controller.moveButton(1)
        assertTrue(controller.moveButton(1))
        assertEquals(ResumePlayerButton.QUEUE, state.button)
        controller.confirmButton()
        assertTrue(state.panelOpen)
    }

    @Test
    fun `X opens the panel on the playing track`() {
        build()
        openPanel()
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
        assertEquals(2, state.listFocus)
    }

    @Test
    fun `the panel captures every direction`() {
        build()
        openPanel()
        val handler = controller.createPanelInputHandler()
        assertTrue(handler.onLeft().handled)
        assertTrue(handler.onRight().handled)
        assertTrue(handler.onSecondaryAction().handled)
        assertTrue(handler.onUp().handled)
        assertTrue(handler.onUp().handled)
        assertEquals(0, state.listFocus)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `up from the first row moves to the controls with the panel still open`() {
        build()
        openPanel()
        val handler = controller.createPanelInputHandler()
        handler.onUp()
        handler.onUp()
        assertTrue(handler.onUp().handled)
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        assertTrue(state.panelOpen)
        assertTrue(controller.leaveCard())
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `B closes the panel and restores the opening button`() {
        build()
        controller.enterCard()
        controller.moveButton(1)
        controller.openPanel()
        assertTrue(controller.createPanelInputHandler().onBack().handled)
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        assertEquals(ResumePlayerButton.NEXT, state.button)
    }

    @Test
    fun `a tap outside closes the panel like B`() {
        build()
        controller.togglePanelFromTouch()
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
        assertTrue(controller.dismissPanel())
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        assertEquals(ResumePlayerButton.PLAY_PAUSE, state.button)
    }

    @Test
    fun `A plays the focused row and the panel stays open`() {
        build()
        openPanel()
        val handler = controller.createPanelInputHandler()
        handler.onDown()
        handler.onConfirm()
        assertEquals(listOf(3), transport.played)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `a tapped row plays and the panel stays open`() {
        build()
        openPanel()
        controller.playRow(0)
        assertEquals(listOf(0), transport.played)
        assertEquals(0, state.listFocus)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `reopening restores the cursor while the same track plays`() {
        build()
        openPanel()
        controller.moveList(1)
        controller.moveList(1)
        controller.dismissPanel()
        controller.openPanel()
        assertEquals(4, state.listFocus)
    }

    @Test
    fun `reopening after a skip lands on the playing track`() {
        build()
        openPanel()
        controller.moveList(1)
        controller.moveList(1)
        controller.dismissPanel()
        transport.playback.value = queue(index = 3)
        controller.openPanel()
        assertEquals(3, state.listFocus)
    }

    @Test
    fun `the cursor follows the playing track only while it sits on it`() {
        build()
        openPanel()
        transport.playback.value = queue(index = 3)
        assertEquals(3, state.listFocus)
        controller.moveList(1)
        transport.playback.value = queue(index = 0)
        assertEquals(4, state.listFocus)
    }

    private fun openPanelThenFocusControls() {
        openPanel()
        controller.createPanelInputHandler().run {
            onUp()
            onUp()
            onUp()
        }
    }

    @Test
    fun `the panel never collapses while the list holds focus`() {
        build()
        openPanel()
        scheduler.advanceTimeBy(idleMs * 3)
        scheduler.runCurrent()
        assertTrue(state.panelOpen)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `the open panel collapses after the idle time with the controls focused`() {
        build()
        openPanelThenFocusControls()
        scheduler.advanceTimeBy(idleMs - 1)
        scheduler.runCurrent()
        assertTrue(state.panelOpen)
        scheduler.advanceTimeBy(2)
        scheduler.runCurrent()
        assertFalse(state.panelOpen)
        assertEquals(ResumePlayerFocus.CARD, state.focus)
    }

    @Test
    fun `input on the controls restarts the idle time`() {
        build()
        openPanelThenFocusControls()
        scheduler.advanceTimeBy(idleMs - 1)
        controller.moveButton(1)
        scheduler.advanceTimeBy(idleMs - 1)
        scheduler.runCurrent()
        assertTrue(state.panelOpen)
        scheduler.advanceTimeBy(2)
        scheduler.runCurrent()
        assertFalse(state.panelOpen)
    }

    @Test
    fun `returning to the list stops the idle time`() {
        build()
        openPanelThenFocusControls()
        scheduler.advanceTimeBy(idleMs - 1)
        controller.leaveCard()
        scheduler.advanceTimeBy(idleMs * 2)
        scheduler.runCurrent()
        assertTrue(state.panelOpen)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `triggers change track from anywhere on the page`() {
        build()
        assertTrue(controller.skip(1))
        assertTrue(controller.skip(1))
        assertTrue(controller.skip(-1))
        assertEquals(1, transport.skips)
        openPanel()
        assertTrue(controller.createPanelInputHandler().onNextTrigger().handled)
        assertEquals(2, transport.skips)
        assertEquals(ResumePlayerFocus.PANEL, state.focus)
    }

    @Test
    fun `up from the first list row or the carousel enters the card`() {
        assertTrue(upFromGamesEntersCard(alongCards = false, focusedIndex = 0))
        assertFalse(upFromGamesEntersCard(alongCards = false, focusedIndex = 1))
        assertTrue(upFromGamesEntersCard(alongCards = true, focusedIndex = 3))
    }

    @Test
    fun `the pill segment is suppressed while the card shows`() {
        build()
        verify(exactly = 1) { pill.setSuppressed("resume_card", true) }
        controller.onHidden()
        verify(exactly = 1) { pill.setSuppressed("resume_card", false) }
    }

    @Test
    fun `the queue stopping releases the pill and focus`() {
        build()
        openPanel()
        transport.playback.value = AmbientPlaybackState()
        assertFalse(state.showsCard)
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
        verify(exactly = 1) { pill.setSuppressed("resume_card", false) }
    }

    @Test
    fun `hiding the page closes the panel`() {
        build()
        openPanel()
        controller.onHidden()
        assertEquals(ResumePlayerFocus.CARD, state.focus)
        controller.onVisible()
        assertEquals(ResumePlayerFocus.PAGE, state.focus)
    }
}
