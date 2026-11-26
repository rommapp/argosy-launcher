package com.nendo.argosy.ui.screens.nowplaying

import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.components.musicplayer.MusicTransportButton
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingQueueFocusTest {

    private val playback = MutableStateFlow(queue(index = 2))
    private lateinit var viewModel: NowPlayingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val audio = mockk<AmbientAudioManager>(relaxed = true)
        every { audio.playback } returns playback
        val controls = mockk<ControlsPreferencesRepository>(relaxed = true)
        every { controls.preferences } returns flowOf(ControlsPreferences())
        val nowPlaying = mockk<NowPlayingPreferencesRepository>(relaxed = true)
        every { nowPlaying.preferences } returns flowOf(NowPlayingPreferences())
        val playlists = mockk<MusicPlaylists>(relaxed = true)
        every { playlists.changes } returns MutableStateFlow(0)
        val analysis = mockk<TrackAnalysisStore>(relaxed = true)
        every { analysis.analysis(any()) } returns MutableStateFlow(null)
        viewModel = NowPlayingViewModel(
            ambientAudioManager = audio,
            ambientMusicSwitch = mockk(relaxed = true),
            nowPlayingPreferences = nowPlaying,
            controlsPreferences = controls,
            musicPlaylists = playlists,
            notifications = mockk(relaxed = true),
            analysisStore = analysis,
            quickNavigation = mockk { every { enabled } returns MutableStateFlow(true) }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun queue(index: Int): AmbientPlaybackState {
        val tracks = (0 until 5).map { MusicQueueTrack(id = "t$it", source = MusicTrackSource.Local("/t$it"), title = "T$it") }
        return AmbientPlaybackState(index = index, count = tracks.size, tracks = tracks, sourceId = "album:a")
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `first entry lands on the playing track`() {
        assertTrue(viewModel.focusQueue())
        assertEquals(NowPlayingFocus.QUEUE, state.focus)
        assertEquals(2, state.queueFocus)
    }

    @Test
    fun `left or right exits to the transport keeping its button`() {
        viewModel.moveTransport(1)
        val button = state.navigation.transport
        viewModel.focusQueue()
        assertTrue(viewModel.moveTransport(-1))
        assertEquals(NowPlayingFocus.TRANSPORT, state.focus)
        assertEquals(button, state.navigation.transport)
        assertEquals(MusicTransportButton.NEXT, button)
    }

    @Test
    fun `re-entry restores the cursor the user left`() {
        viewModel.focusQueue()
        viewModel.moveVertical(1)
        viewModel.moveVertical(1)
        viewModel.leaveQueue()
        viewModel.moveVertical(1)
        assertEquals(NowPlayingFocus.QUEUE, state.focus)
        assertEquals(4, state.queueFocus)
    }

    @Test
    fun `re-entry after a skip lands on the playing track`() {
        viewModel.focusQueue()
        viewModel.moveVertical(1)
        viewModel.moveVertical(1)
        viewModel.moveTransport(1)
        playback.value = queue(index = 3)
        viewModel.focusQueue()
        assertEquals(3, state.queueFocus)
    }

    @Test
    fun `focus on the playing track follows a skip`() {
        viewModel.focusQueue()
        playback.value = queue(index = 3)
        assertEquals(3, state.queueFocus)
    }

    @Test
    fun `focus on another track stays put across a skip`() {
        viewModel.focusQueue()
        viewModel.moveVertical(1)
        viewModel.moveVertical(1)
        playback.value = queue(index = 3)
        assertEquals(4, state.queueFocus)
    }

    @Test
    fun `an unfocused queue does not follow`() {
        viewModel.focusQueue()
        viewModel.leaveQueue()
        playback.value = queue(index = 3)
        assertEquals(NowPlayingFocus.TRANSPORT, state.focus)
        assertEquals(2, state.navigation.queueFocus)
    }
}
