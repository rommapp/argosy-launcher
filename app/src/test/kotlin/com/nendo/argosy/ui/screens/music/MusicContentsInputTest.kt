package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.ui.input.InputResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicContentsInputTest {

    private val viewModel = mockk<MusicViewModel>(relaxed = true)
    private val state = MutableStateFlow(MusicUiState(sources = setOf(MusicSourceKind.NAVIDROME)))
    private val handler = MusicInputHandler(viewModel = viewModel, onExit = {}, onOpenSettings = {})
    private val boundary = InputResult.handled(SoundType.BOUNDARY)

    init {
        every { viewModel.uiState } returns state
        every { viewModel.playContentsColumn() } returns true
    }

    private val album = MusicAlbum(
        key = "navidrome:album:1",
        source = MusicSourceKind.NAVIDROME,
        id = "1",
        title = "Album",
        artist = null,
        artistId = null,
        year = null,
        genre = null,
        trackCount = 2,
        durationSec = 0,
        art = null,
        playCount = 0,
        downloaded = false
    )

    private val tracks = listOf(fixtureTrack("a"), fixtureTrack("b"), fixtureTrack("c"))

    @Test
    fun `X in a grouped column plays the whole column`() {
        state.value = state.value.copy(
            contents = MusicContentsUiState(
                column = fixtureColumn(kind = MusicColumnKind.ARTIST, key = "artist:1"),
                albums = listOf(album),
                isLoading = false
            )
        )
        assertEquals(InputResult.HANDLED, handler.onContextMenu())
        verify(exactly = 1) { viewModel.playContentsColumn() }
    }

    @Test
    fun `X in a playlist column does nothing`() {
        state.value = state.value.copy(
            contents = MusicContentsUiState(column = fixtureColumn(), tracks = tracks, isLoading = false)
        )
        assertEquals(boundary, handler.onContextMenu())
        verify(exactly = 0) { viewModel.playContentsColumn() }
    }

    @Test
    fun `X with no column open still opens the filter`() {
        assertEquals(InputResult.HANDLED, handler.onContextMenu())
        verify(exactly = 0) { viewModel.playContentsColumn() }
        verify(exactly = 1) { viewModel.actions.openFilter() }
    }

    @Test
    fun `focus on the playing track follows the next one`() {
        val contents = MusicContentsUiState(column = fixtureColumn(), tracks = tracks, trackIndex = 0, isLoading = false)
        val followed = contents.followingPlaying(tracks[0].key, tracks[1].key, listFocused = true)
        assertEquals(1, followed.trackIndex)
    }

    @Test
    fun `focus on another track stays put`() {
        val contents = MusicContentsUiState(column = fixtureColumn(), tracks = tracks, trackIndex = 2, isLoading = false)
        val followed = contents.followingPlaying(tracks[0].key, tracks[1].key, listFocused = true)
        assertEquals(2, followed.trackIndex)
    }

    @Test
    fun `an unfocused column does not follow`() {
        val contents = MusicContentsUiState(column = fixtureColumn(), tracks = tracks, trackIndex = 0, isLoading = false)
        val followed = contents.followingPlaying(tracks[0].key, tracks[1].key, listFocused = false)
        assertEquals(0, followed.trackIndex)
    }

    @Test
    fun `a new playing track outside the column leaves focus alone`() {
        val contents = MusicContentsUiState(column = fixtureColumn(), tracks = tracks, trackIndex = 0, isLoading = false)
        val followed = contents.followingPlaying(tracks[0].key, "navidrome:track:zzz", listFocused = true)
        assertEquals(0, followed.trackIndex)
    }
}
