package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.DualScreenManager
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.preferences.SelectSwapMode
import com.nendo.argosy.domain.model.MusicAlbum
import com.nendo.argosy.domain.model.MusicColumnKind
import com.nendo.argosy.domain.model.MusicColumnsPage
import com.nendo.argosy.domain.model.MusicGrouping
import com.nendo.argosy.domain.model.MusicSourceKind
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.musicstats.MusicStatsInputHandler
import com.nendo.argosy.ui.screens.musicstats.MusicStatsUiState
import com.nendo.argosy.ui.screens.musicstats.MusicStatsViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDualScreenTest {

    private val artist = fixtureColumn(kind = MusicColumnKind.ARTIST, key = "artist:1")
    private val playlist = fixtureColumn()
    private val page = MusicColumnsPage(columns = listOf(playlist, artist), offline = false)
    private val browsing = MusicUiState(sources = setOf(MusicSourceKind.NAVIDROME), page = page, isLoading = false)

    private fun album(id: String) = MusicAlbum(
        key = "navidrome:album:$id",
        source = MusicSourceKind.NAVIDROME,
        id = id,
        title = "Album $id",
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

    @After
    fun tearDown() {
        DualScreenManagerHolder.instance = null
    }

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
    fun `a position recorded before a swap reaches the browser composed after it`() {
        val carrier = MusicBrowserCarrier()
        val position = MusicBrowserPosition(MusicGrouping.ALBUMS, "artist:1", "artist:1", "navidrome:album:2", 3)
        carrier.record(Any(), swapped = false, position = position)
        assertEquals(position, carrier.take(swapped = true))
    }

    @Test
    fun `reopening without a swap starts fresh`() {
        val carrier = MusicBrowserCarrier()
        carrier.record(Any(), swapped = false, position = MusicBrowserPosition(MusicGrouping.ALBUMS, "artist:1"))
        assertNull(carrier.take(swapped = false))
    }

    @Test
    fun `a browser that leaves clears only its own position`() {
        val carrier = MusicBrowserCarrier()
        val owner = Any()
        carrier.record(owner, swapped = false, position = MusicBrowserPosition(MusicGrouping.ALBUMS, "artist:1"))
        carrier.release(Any())
        assertTrue(carrier.take(swapped = true) != null)
        carrier.release(owner)
        assertNull(carrier.take(swapped = true))
    }

    @Test
    fun `the carried position covers grouping, column, open column, album and track`() {
        val state = browsing.copy(
            grouping = MusicGrouping.ARTISTS,
            position = 1,
            contents = MusicContentsUiState(
                column = artist,
                albums = listOf(album("1"), album("2")),
                albumIndex = 1,
                trackIndex = 4,
                isLoading = false
            )
        )
        assertEquals(
            MusicBrowserPosition(MusicGrouping.ARTISTS, "artist:1", "artist:1", "navidrome:album:2", 4),
            MusicBrowserPosition.of(state)
        )
    }

    @Test
    fun `a carried column is found again or dropped`() {
        assertEquals(1, restoredColumnIndex(page.columns, "artist:1"))
        assertNull(restoredColumnIndex(page.columns, "artist:gone"))
        assertNull(restoredColumnIndex(page.columns, null))
    }

    @Test
    fun `the strip showcases the focused column`() {
        val slot = browsing.copy(position = 1).showcaseSlot()
        val state = slot?.state as? MusicShowcaseState.Column
        assertEquals(artist, state?.column)
    }

    @Test
    fun `nothing to describe publishes nothing`() {
        assertNull(MusicUiState(sources = emptySet()).showcaseSlot())
        assertNull(MusicUiState(sources = setOf(MusicSourceKind.NAVIDROME), loadFailed = true).showcaseSlot())
        assertNull(browsing.copy(page = MusicColumnsPage(emptyList(), false)).showcaseSlot())
    }

    @Test
    fun `a loading column keeps its column showcase and a failed one releases`() {
        val loading = browsing.copy(contents = MusicContentsUiState(column = artist))
        assertTrue(loading.showcaseSlot()?.state is MusicShowcaseState.Column)
        val failed = browsing.copy(contents = MusicContentsUiState(column = artist, isLoading = false, loadFailed = true))
        assertNull(failed.showcaseSlot())
    }

    @Test
    fun `an opened album column shows the album in front with its position`() {
        val opened = browsing.copy(
            contents = MusicContentsUiState(
                column = artist,
                albums = listOf(album("1"), album("2"), album("3")),
                albumIndex = 1,
                isLoading = false
            )
        )
        val state = opened.showcaseSlot()?.state as MusicShowcaseState.Contents
        assertEquals("navidrome:album:2", state.front.first().key)
        assertEquals(1, state.albumIndex)
        assertEquals(3, state.albumCount)
    }

    @Test
    fun `the focused square leaves each neighbour a usable width`() {
        val geometry = squareFocusGeometry(width = 620f, height = 500f, slots = 3, gap = 8f, minNeighbour = 48f)
        assertTrue(geometry.columnWidth >= 48f - 0.01f)
    }

    @Test
    fun `a roomy strip keeps the square focus`() {
        val geometry = squareFocusGeometry(width = 1920f, height = 400f, slots = 5, gap = 8f, minNeighbour = 48f)
        assertEquals(400f, geometry.columnWidth * geometry.focusScale, 0.5f)
    }

    @Test
    fun `tap mode select on the browser falls through to the swap and hold A opens options`() {
        installSwap(SelectSwapMode.TAP)
        val viewModel = mockk<MusicViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(browsing)
        val handler = MusicInputHandler(viewModel = viewModel, onExit = {}, onOpenSettings = {})
        assertEquals(InputResult.UNHANDLED, handler.onSelect())
        verify(exactly = 0) { viewModel.openFocusedColumnOptions() }
        assertEquals(InputResult.HANDLED, handler.onLongConfirm())
        verify(exactly = 1) { viewModel.openFocusedColumnOptions() }
    }

    @Test
    fun `hold mode keeps select for options on the browser`() {
        installSwap(SelectSwapMode.HOLD)
        val viewModel = mockk<MusicViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(browsing)
        val handler = MusicInputHandler(viewModel = viewModel, onExit = {}, onOpenSettings = {})
        assertEquals(InputResult.HANDLED, handler.onSelect())
        verify(exactly = 1) { viewModel.openFocusedColumnOptions() }
        assertEquals(InputResult.UNHANDLED, handler.onLongSelect())
    }

    @Test
    fun `move mode blocks both select gestures`() {
        installSwap(SelectSwapMode.TAP)
        val viewModel = mockk<MusicViewModel>(relaxed = true)
        val moving = browsing.copy(
            contents = MusicContentsUiState(column = playlist, isLoading = false, moveGroup = fixtureGroup("Road"))
        )
        every { viewModel.uiState } returns MutableStateFlow(moving)
        val handler = MusicInputHandler(viewModel = viewModel, onExit = {}, onOpenSettings = {})
        assertEquals(InputResult.handled(SoundType.BOUNDARY), handler.onSelect())
        assertEquals(InputResult.HANDLED, handler.onLongSelect())
    }

    @Test
    fun `music modals swallow the hold select swap`() {
        val modal = MusicModalInput(move = {}, confirm = {}, back = {})
        assertEquals(InputResult.HANDLED, modal.onLongSelect())
    }

    @Test
    fun `tap mode select on your listening falls through to the swap`() {
        installSwap(SelectSwapMode.TAP)
        val viewModel = mockk<MusicStatsViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(MusicStatsUiState())
        val handler = MusicStatsInputHandler(viewModel = viewModel, onExit = {})
        assertEquals(InputResult.UNHANDLED, handler.onSelect())
        verify(exactly = 0) { viewModel.openOptions() }
        handler.onLongConfirm()
        verify(exactly = 1) { viewModel.openOptions() }
    }
}
