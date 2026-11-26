package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.data.music.library.MusicLibrary
import com.nendo.argosy.data.music.library.MusicPlaylists
import com.nendo.argosy.domain.model.MusicPlaylistResult
import com.nendo.argosy.domain.model.MusicSourceFilter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlaylistActionsDelegateTest {

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)
    private val playlists = mockk<MusicPlaylists>()
    private val group = fixtureGroup("Road")
    private val column = fixtureColumn()
    private val tracks = listOf(fixtureTrack("a"), fixtureTrack("b"), fixtureTrack("c"))
    private val state = MutableStateFlow(MusicUiState())
    private var reloads = 0

    private val delegate = MusicPlaylistActionsDelegate(
        playlists = playlists,
        notifications = mockk(relaxed = true),
        resolver = MusicTrackResolver(mockk<MusicLibrary>(relaxed = true)),
        scope = scope,
        state = state,
        onOpenForReorder = { _, _ -> },
        onReloadContents = { reloads++ },
        io = dispatcher
    )

    private fun moving(at: Int) {
        state.value = MusicUiState(
            contents = MusicContentsUiState(
                column = column,
                tracks = tracks,
                trackIndex = at,
                isLoading = false,
                moveGroup = group
            )
        )
    }

    private val contents get() = requireNotNull(state.value.contents)

    @Test
    fun `moving up from the first track is a boundary`() {
        moving(at = 0)
        assertFalse(delegate.moveFocused(-1))
        assertEquals(tracks, contents.tracks)
        assertEquals(0, contents.trackIndex)
    }

    @Test
    fun `moving down from the last track is a boundary`() {
        moving(at = 2)
        assertFalse(delegate.moveFocused(1))
        assertEquals(tracks, contents.tracks)
    }

    @Test
    fun `moving down swaps the track and keeps it focused`() {
        coEvery { playlists.move(group, 0, 1) } returns MusicPlaylistResult.Done(group.copy(hasCustomOrder = true))
        moving(at = 0)
        assertTrue(delegate.moveFocused(1))
        assertEquals(listOf(tracks[1], tracks[0], tracks[2]), contents.tracks)
        assertEquals(1, contents.trackIndex)
        scope.advanceUntilIdle()
        coVerify { playlists.move(group, 0, 1) }
        assertTrue(contents.moveGroup?.hasCustomOrder == true)
        assertTrue(contents.isMoving)
    }

    @Test
    fun `moving up swaps with the track above`() {
        coEvery { playlists.move(group, 2, 1) } returns MusicPlaylistResult.Done(group)
        moving(at = 2)
        assertTrue(delegate.moveFocused(-1))
        assertEquals(listOf(tracks[0], tracks[2], tracks[1]), contents.tracks)
        assertEquals(1, contents.trackIndex)
    }

    @Test
    fun `a failed move ends move mode and reloads the column`() {
        coEvery { playlists.move(group, 0, 1) } returns MusicPlaylistResult.Failed
        moving(at = 0)
        delegate.moveFocused(1)
        scope.advanceUntilIdle()
        assertFalse(contents.isMoving)
        assertEquals(1, reloads)
    }

    @Test
    fun `ending move mode keeps the track focused`() {
        moving(at = 1)
        assertTrue(delegate.endMove())
        assertFalse(contents.isMoving)
        assertEquals(1, contents.trackIndex)
        assertFalse(delegate.endMove())
    }

    @Test
    fun `moves are ignored outside move mode`() {
        state.value = MusicUiState(contents = MusicContentsUiState(column = column, tracks = tracks, isLoading = false))
        assertFalse(delegate.moveFocused(1))
    }

    @Test
    fun `missing RomM permission from the picker opens the permission prompt`() {
        val track = tracks[0]
        coEvery { playlists.groups(MusicSourceFilter.ALL) } returns emptyList()
        coEvery { playlists.create("Road", listOf(track)) } returns MusicPlaylistResult.NeedsRommPermission
        state.value = MusicUiState(
            options = MusicOptionsState(
                target = MusicOptionsTarget.Track(track, column, 0),
                title = track.title,
                actions = listOf(MusicOptionAction.ADD_TO_PLAYLIST)
            )
        )
        delegate.picker.open()
        scope.advanceUntilIdle()
        delegate.picker.confirm()
        delegate.picker.submitName("Road")
        scope.advanceUntilIdle()
        assertTrue(state.value.showsPlaylistPermission)
        assertNull(state.value.options)
        delegate.dismissPermission()
        assertFalse(state.value.showsPlaylistPermission)
    }
}
