package com.nendo.argosy.ui.screens.settings

import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.usecase.storage.GameStorageBreakdown
import com.nendo.argosy.domain.usecase.storage.GameStorageFile
import com.nendo.argosy.domain.usecase.storage.GameStorageLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageLocationFilterTest {

    private fun game(id: Long, vararg locations: GameStorageLocation) = GameStorageBreakdown(
        gameId = id,
        title = "game $id",
        source = GameSource.LOCAL_ONLY,
        buckets = emptyList(),
        totalBytes = 0L,
        unsyncedSaves = 0,
        files = locations.mapIndexed { i, loc -> GameStorageFile("/f$id/$i", 1L, loc) }
    )

    private val internal = game(1, GameStorageLocation.INTERNAL)
    private val sdCard = game(2, GameStorageLocation.REMOVABLE)
    private val split = game(3, GameStorageLocation.INTERNAL, GameStorageLocation.REMOVABLE)
    private val unknown = game(4, GameStorageLocation.INTERNAL, GameStorageLocation.UNKNOWN)
    private val noFiles = game(5)
    private val games = listOf(internal, sdCard, split, unknown, noFiles)

    private fun visible(filter: StorageLocationFilter, localOnly: Boolean = true) =
        StoragePlatformGamesState(localOnly = localOnly, games = games, locationFilter = filter)
            .visibleGames.map { it.gameId }

    @Test
    fun `all shows every game`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), visible(StorageLocationFilter.ALL))
    }

    @Test
    fun `a game is listed under every drive it has files on`() {
        assertEquals(listOf(1L, 3L, 4L), visible(StorageLocationFilter.INTERNAL))
        assertEquals(listOf(2L, 3L), visible(StorageLocationFilter.REMOVABLE))
    }

    @Test
    fun `unknown lists games with a file on no detected drive or with no files`() {
        assertEquals(listOf(4L, 5L), visible(StorageLocationFilter.UNKNOWN))
    }

    @Test
    fun `the triggers wrap around the tabs in both directions`() {
        val delegate = com.nendo.argosy.ui.screens.settings.delegates.StoragePlatformGamesDelegate(
            io.mockk.mockk(relaxed = true), io.mockk.mockk(relaxed = true),
            io.mockk.mockk(relaxed = true), io.mockk.mockk(relaxed = true)
        )
        delegate.cycleLocationFilter(-1)
        assertEquals(StorageLocationFilter.UNKNOWN, delegate.state.value.locationFilter)
        delegate.cycleLocationFilter(1)
        assertEquals(StorageLocationFilter.ALL, delegate.state.value.locationFilter)
        delegate.cycleLocationFilter(1)
        assertEquals(StorageLocationFilter.INTERNAL, delegate.state.value.locationFilter)
    }

    @Test
    fun `a platform list ignores the location filter`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), visible(StorageLocationFilter.REMOVABLE, localOnly = false))
    }
}
