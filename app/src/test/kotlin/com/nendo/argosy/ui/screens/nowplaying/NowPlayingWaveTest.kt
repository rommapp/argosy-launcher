package com.nendo.argosy.ui.screens.nowplaying

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.NowPlayingLayout
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.input.InputResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingWaveTest {

    private val playback = MutableStateFlow(queue())
    private val quickNavigation = MutableStateFlow(false)
    private val audio = mockk<AmbientAudioManager>(relaxed = true)
    private lateinit var viewModel: NowPlayingViewModel
    private lateinit var handler: NowPlayingInputHandler

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { audio.playback } returns playback
        viewModel = viewModel(NowPlayingPreferences())
        handler = NowPlayingInputHandler(viewModel) {}
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        DualScreenManagerHolder.instance = null
    }

    private fun viewModel(prefs: NowPlayingPreferences): NowPlayingViewModel {
        val controls = mockk<ControlsPreferencesRepository>(relaxed = true)
        every { controls.preferences } returns flowOf(ControlsPreferences())
        val nowPlaying = mockk<NowPlayingPreferencesRepository>(relaxed = true)
        every { nowPlaying.preferences } returns flowOf(prefs)
        val playlists = mockk<MusicPlaylists>(relaxed = true)
        every { playlists.changes } returns MutableStateFlow(0)
        val analysis = mockk<TrackAnalysisStore>(relaxed = true)
        every { analysis.analysis(any()) } returns MutableStateFlow(null)
        return NowPlayingViewModel(
            ambientAudioManager = audio,
            ambientMusicSwitch = mockk(relaxed = true),
            nowPlayingPreferences = nowPlaying,
            controlsPreferences = controls,
            musicPlaylists = playlists,
            notifications = mockk(relaxed = true),
            analysisStore = analysis,
            quickNavigation = mockk { every { enabled } returns quickNavigation }
        )
    }

    private fun queue(): AmbientPlaybackState {
        val tracks = (0 until 4).map { MusicQueueTrack(id = "t$it", source = MusicTrackSource.Local("/t$it"), title = "T$it") }
        return AmbientPlaybackState(index = 1, count = tracks.size, tracks = tracks, sourceId = "album:a")
    }

    private val state get() = viewModel.uiState.value

    private fun installSwap(mode: SelectSwapMode) {
        DualScreenManagerHolder.instance = mockk<DualScreenManager> {
            every { isDualScreenDevice } returns MutableStateFlow(true)
            every { hasPresentationScreen } returns MutableStateFlow(true)
            every { swappedIsGameActive } returns MutableStateFlow(false)
            every { liveSwapAvailable } returns MutableStateFlow(false)
            every { selectSwapMode } returns MutableStateFlow(mode)
        }
    }

    @Test
    fun `single screen keeps the wave out of reach`() {
        assertFalse(viewModel.moveVertical(-1))
        assertEquals(NowPlayingFocus.TRANSPORT, state.focus)
    }

    @Test
    fun `up from the transport rings the wave on the control screen and down returns`() {
        viewModel.setSplit(true)
        assertTrue(viewModel.moveVertical(-1))
        assertEquals(NowPlayingFocus.WAVE, state.focus)
        assertFalse(viewModel.moveVertical(-1))
        assertTrue(viewModel.moveVertical(1))
        assertEquals(NowPlayingFocus.TRANSPORT, state.focus)
    }

    @Test
    fun `left and right on the wave seek ten seconds`() {
        viewModel.setSplit(true)
        viewModel.focusWave()
        assertEquals(InputResult.HANDLED, handler.onLeft())
        assertEquals(InputResult.HANDLED, handler.onRight())
        verify(exactly = 1) { audio.seekBy(-10_000L) }
        verify(exactly = 1) { audio.seekBy(10_000L) }
        assertEquals(NowPlayingFocus.WAVE, state.focus)
    }

    @Test
    fun `the control screen lists transport and queue whatever the layout`() {
        viewModel = viewModel(NowPlayingPreferences(layout = NowPlayingLayout.SIMPLE))
        assertFalse(state.showsQueue)
        viewModel.setSplit(true)
        assertTrue(state.showsTransport)
        assertTrue(state.showsQueue)
        assertTrue(viewModel.moveVertical(1))
        assertEquals(NowPlayingFocus.QUEUE, state.focus)
    }

    @Test
    fun `losing the second screen drops wave focus`() {
        viewModel.setSplit(true)
        viewModel.focusWave()
        viewModel.setSplit(false)
        assertEquals(NowPlayingFocus.TRANSPORT, state.focus)
    }

    @Test
    fun `bumpers seek when quick navigation is off`() {
        assertEquals(InputResult.HANDLED, handler.onNextSection())
        assertEquals(InputResult.HANDLED, handler.onPrevSection())
        verify(exactly = 1) { audio.seekBy(10_000L) }
        verify(exactly = 1) { audio.seekBy(-10_000L) }
    }

    @Test
    fun `bumpers stay with quick navigation and the right stick seeks`() {
        quickNavigation.value = true
        assertEquals(InputResult.UNHANDLED, handler.onNextSection())
        assertEquals(InputResult.HANDLED, handler.onRightStickRight())
        verify(exactly = 1) { audio.seekBy(10_000L) }
    }

    @Test
    fun `tap mode select falls through to the swap and hold A opens options`() {
        installSwap(SelectSwapMode.TAP)
        assertEquals(InputResult.UNHANDLED, handler.onSelect())
        assertNull(state.navigation.options)
        handler.onLongConfirm()
        assertNotNull(state.navigation.options)
    }

    @Test
    fun `hold mode keeps select for options`() {
        installSwap(SelectSwapMode.HOLD)
        handler.onSelect()
        assertNotNull(state.navigation.options)
    }

    @Test
    fun `the stage is published only on the control screen`() {
        assertNull(state.stageSlot(bumpersSeek = true, selectSwaps = false))
        viewModel.setSplit(true)
        val slot = state.stageSlot(bumpersSeek = true, selectSwaps = false)
        assertTrue(slot is PresentationSlot.NowPlayingStage)
    }

    @Test
    fun `nothing queued publishes nothing`() {
        viewModel.setSplit(true)
        playback.value = AmbientPlaybackState()
        assertNull(state.stageSlot(bumpersSeek = true, selectSwaps = false))
    }

    @Test
    fun `the hint line follows the bumper and select modes`() {
        assertEquals(
            listOf(
                NowPlayingStageHint.SKIP_SECONDS,
                NowPlayingStageHint.TRACK,
                NowPlayingStageHint.QUEUE,
                NowPlayingStageHint.STYLE
            ),
            nowPlayingStageHints(hasQueue = true, showsQueue = true, bumpersSeek = true, selectSwaps = false)
        )
        assertEquals(
            listOf(NowPlayingStageHint.TRACK, NowPlayingStageHint.QUEUE, NowPlayingStageHint.SWAP_SCREENS),
            nowPlayingStageHints(hasQueue = true, showsQueue = true, bumpersSeek = false, selectSwaps = true)
        )
    }
}
