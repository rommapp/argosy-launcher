package com.nendo.argosy.ui.components.musicplayer

import com.nendo.argosy.data.music.analysis.TrackAnalysisStore
import com.nendo.argosy.data.preferences.ControlsPreferences
import com.nendo.argosy.data.preferences.ControlsPreferencesRepository
import com.nendo.argosy.data.preferences.NowPlayingPreferences
import com.nendo.argosy.data.preferences.NowPlayingPreferencesRepository
import com.nendo.argosy.domain.model.MusicQueueTrack
import com.nendo.argosy.domain.model.MusicTrackSource
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientPlaybackState
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerTrackFocusTest {

    private val playback = MutableStateFlow(queue(index = 2))
    private lateinit var viewModel: MusicPlayerViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val audio = mockk<AmbientAudioManager>(relaxed = true)
        every { audio.playback } returns playback
        val controls = mockk<ControlsPreferencesRepository>(relaxed = true)
        every { controls.preferences } returns flowOf(ControlsPreferences(ambientAudioEnabled = true))
        val nowPlaying = mockk<NowPlayingPreferencesRepository>(relaxed = true)
        every { nowPlaying.preferences } returns flowOf(NowPlayingPreferences())
        val analysis = mockk<TrackAnalysisStore>(relaxed = true)
        every { analysis.analysis(any()) } returns MutableStateFlow(null)
        viewModel = MusicPlayerViewModel(
            ambientAudioManager = audio,
            controlsPreferences = controls,
            nowPlayingPreferences = nowPlaying,
            ambientMusicSwitch = mockk(relaxed = true),
            getMusicPlaylists = mockk(relaxed = true),
            searchSoundtrackGames = mockk(relaxed = true),
            resolveMusicQueue = mockk(relaxed = true),
            analysisStore = analysis
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun queue(index: Int, sourceId: String = "album:a"): AmbientPlaybackState {
        val tracks = (0 until 5).map { MusicQueueTrack(id = "t$it", source = MusicTrackSource.Local("/t$it"), title = "T$it") }
        return AmbientPlaybackState(index = index, count = tracks.size, tracks = tracks, sourceId = sourceId)
    }

    private fun enterTracks() {
        viewModel.focusSource(MusicBrowseKind.PLAYLISTS)
        viewModel.moveRow(1)
    }

    private val state get() = viewModel.uiState.value

    @Test
    fun `first entry lands on the playing track`() {
        enterTracks()
        assertEquals(MusicPlayerRow.TRACKS, state.focusedRow)
        assertEquals(2, state.trackFocus)
    }

    @Test
    fun `re-entry restores the cursor the user left`() {
        enterTracks()
        viewModel.moveRow(1)
        viewModel.moveRow(1)
        viewModel.leaveTrackList()
        enterTracks()
        assertEquals(4, state.trackFocus)
    }

    @Test
    fun `re-entry after a skip lands on the playing track`() {
        enterTracks()
        viewModel.moveRow(1)
        viewModel.moveRow(1)
        viewModel.leaveTrackList()
        playback.value = queue(index = 3)
        enterTracks()
        assertEquals(3, state.trackFocus)
    }

    @Test
    fun `left or right exits to the transport keeping its button`() {
        viewModel.focusTransport(MusicTransportButton.NEXT)
        enterTracks()
        viewModel.moveHorizontal(1)
        assertEquals(MusicPlayerRow.TRANSPORT, state.focusedRow)
        assertEquals(MusicTransportButton.NEXT, state.transportButton)
    }

    @Test
    fun `focus on the playing track follows a skip`() {
        enterTracks()
        playback.value = queue(index = 3)
        assertEquals(3, state.trackFocus)
    }

    @Test
    fun `focus on another track stays put across a skip`() {
        enterTracks()
        viewModel.moveRow(1)
        viewModel.moveRow(1)
        playback.value = queue(index = 3)
        assertEquals(4, state.trackFocus)
    }

    @Test
    fun `an unfocused track list does not follow`() {
        viewModel.focusSource(MusicBrowseKind.PLAYLISTS)
        val before = state.trackFocus
        playback.value = queue(index = 3)
        assertEquals(MusicPlayerRow.SOURCES, state.focusedRow)
        assertEquals(before, state.trackFocus)
    }
}
