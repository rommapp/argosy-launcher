package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.FeatureTileKind
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.TileArtSlot
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.domain.model.TileRect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER = 1L
private const val TILE_ID = 7L
private const val KEY_PICKED_GAME_ID = "pickedGameId"
private const val KEY_COVER_SOURCE = "coverSource"
private const val KEY_PLATFORM_IDS = "platformIds"
private const val PAGED = "PAGED"
private const val SCROLL = "SCROLL"

class HomeTileRepositoryTest {

    private val dao = mockk<HomeTileDao>(relaxed = true)
    private val repository = HomeTileRepository(dao)

    private suspend fun roundTrip(
        target: HomeTileTargetRef
    ): Pair<HomeTileEntity, HomeTileTargetRef> {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns TILE_ID
        repository.place(
            ownerUserId = OWNER,
            kind = HomeGridKind.PAGED,
            pageIndex = 0,
            rect = TileRect(0, 0),
            target = target
        )
        val row = written.captured.copy(id = TILE_ID)
        every { dao.observeTiles(OWNER, PAGED) } returns flowOf(listOf(row))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return row to repository.observeTiles(OWNER, HomeGridKind.PAGED).first().single().target
    }

    @Test
    fun `an RA tile keeps its tracked game across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY, pickedGameId = 42L)

        val (row, read) = roundTrip(target)

        assertEquals(FeatureTileKind.RA_SUMMARY.name, row.featureKind)
        assertEquals(42L, JSONObject(row.featureConfig.orEmpty()).getLong(KEY_PICKED_GAME_ID))
        assertEquals(target, read)
    }

    @Test
    fun `an RA tile with no tracked game stores no pick and reads back null`() = runTest {
        val target = HomeTileTargetRef.Feature(FeatureTileKind.RA_SUMMARY)

        val (row, read) = roundTrip(target)

        assertFalse(JSONObject(row.featureConfig.orEmpty()).has(KEY_PICKED_GAME_ID))
        assertTrue(read is HomeTileTargetRef.Feature)
        assertEquals(null, (read as HomeTileTargetRef.Feature).pickedGameId)
        assertEquals(target, read)
    }

    /**
     * A stored link carries every category the library can apply. One dropped in the round trip
     * is a filter the user set and the opened library never sees.
     */
    @Test
    fun `a library link keeps every filter across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(
            kind = FeatureTileKind.LIBRARY_LINK,
            libraryLink = com.nendo.argosy.domain.model.LibraryLinkFilters(
                source = com.nendo.argosy.data.model.SourceFilter.FAVORITES,
                platformIds = setOf(3L, 9L),
                genres = setOf("Role-playing (RPG)"),
                series = setOf("Mega Man"),
                players = com.nendo.argosy.domain.model.PlayerCountBucket.FOUR_PLUS,
                sort = com.nendo.argosy.data.model.ActiveSort(
                    com.nendo.argosy.data.model.SortOption.RELEASE_YEAR,
                    descending = false
                )
            )
        )

        val (row, read) = roundTrip(target)

        assertEquals(FeatureTileKind.LIBRARY_LINK.name, row.featureKind)
        assertEquals(target, read)
    }

    @Test
    fun `a collection keeps its cover picture beside its typed columns across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Collection(
            collectionId = 4L,
            focusGameId = 9L,
            coverSource = TileImageSource.GameArt(12L, TileArtSlot.BACKGROUND)
        )

        val (row, read) = roundTrip(target)

        assertEquals(4L, row.collectionId)
        assertEquals(9L, row.gameId)
        assertTrue(JSONObject(row.featureConfig.orEmpty()).has(KEY_COVER_SOURCE))
        assertEquals(target, read)
    }

    @Test
    fun `a collection with no cover picture leaves the feature config empty`() = runTest {
        val target = HomeTileTargetRef.Collection(collectionId = 4L)

        val (row, read) = roundTrip(target)

        assertNull(row.featureConfig)
        assertEquals(target, read)
    }

    @Test
    fun `a library link keeps its cover picture alongside its filters across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Feature(
            kind = FeatureTileKind.LIBRARY_LINK,
            libraryLink = com.nendo.argosy.domain.model.LibraryLinkFilters(
                platformIds = setOf(3L),
                genres = setOf("Platform")
            ),
            coverSource = TileImageSource.File("/sdcard/Pictures/link.png")
        )

        val (row, read) = roundTrip(target)

        val config = JSONObject(row.featureConfig.orEmpty())
        assertTrue(config.has(KEY_COVER_SOURCE))
        assertTrue(config.has(KEY_PLATFORM_IDS))
        assertEquals(target, read)
    }

    @Test
    fun `a library link with no cover picture stores no cover key`() = runTest {
        val target = HomeTileTargetRef.Feature(
            kind = FeatureTileKind.LIBRARY_LINK,
            libraryLink = com.nendo.argosy.domain.model.LibraryLinkFilters()
        )

        val (row, read) = roundTrip(target)

        assertFalse(JSONObject(row.featureConfig.orEmpty()).has(KEY_COVER_SOURCE))
        assertEquals(target, read)
    }

    @Test
    fun `retargeting a collection with a cover picture keeps its collection and focus game`() = runTest {
        val stored = HomeTileEntity(
            id = TILE_ID,
            ownerUserId = OWNER,
            pageIndex = 0,
            columnIndex = 0,
            rowIndex = 0,
            targetType = "COLLECTION",
            collectionId = 4L,
            gameId = 9L
        )
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(TILE_ID) } returns stored
        coEvery { dao.update(capture(written)) } returns Unit

        repository.retarget(
            TILE_ID,
            HomeTileTargetRef.Collection(4L, focusGameId = 9L, coverSource = TileImageSource.File("/p.png")),
            emptyList()
        )

        val row = written.captured
        assertEquals(4L, row.collectionId)
        assertEquals(9L, row.gameId)
        assertTrue(JSONObject(row.featureConfig.orEmpty()).has(KEY_COVER_SOURCE))
    }

    @Test
    fun `retargeting keeps the stored page, rectangle and cover scale`() = runTest {
        val stored = HomeTileEntity(
            id = TILE_ID,
            ownerUserId = OWNER,
            pageIndex = 2,
            columnIndex = 5,
            rowIndex = 1,
            columnSpan = 3,
            rowSpan = 2,
            coverScale = "FIT",
            targetType = "COLLECTION",
            collectionId = 4L
        )
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(TILE_ID) } returns stored
        coEvery { dao.update(capture(written)) } returns Unit

        repository.retarget(TILE_ID, HomeTileTargetRef.Collection(4L, focusGameId = 9L), emptyList())

        val row = written.captured
        assertEquals(listOf(2, 5, 1, 3, 2), listOf(row.pageIndex, row.columnIndex, row.rowIndex, row.columnSpan, row.rowSpan))
        assertEquals("FIT", row.coverScale)
        assertEquals(OWNER, row.ownerUserId)
        assertEquals(9L, row.gameId)
    }

    private fun pageRow(
        id: Long,
        column: Int,
        row: Int,
        columnSpan: Int = 1,
        rowSpan: Int = 1,
        kind: String = PAGED
    ) = HomeTileEntity(
        id = id,
        ownerUserId = OWNER,
        gridKind = kind,
        pageIndex = 0,
        columnIndex = column,
        rowIndex = row,
        columnSpan = columnSpan,
        rowSpan = rowSpan,
        targetType = "GAME",
        gameId = id
    )

    private fun paged(columns: Int, rows: Int) = CustomGridLayout(CustomGridShape(columns, rows))

    private fun scrolling(lanes: Int, axis: HomeScrollAxis) = CustomGridLayout(
        shape = if (axis == HomeScrollAxis.VERTICAL) {
            CustomGridShape(columns = lanes, rows = 3)
        } else {
            CustomGridShape(columns = 3, rows = lanes)
        },
        scrollAxis = axis
    )

    @Test
    fun `an auto-added tile takes the first free cell inside the resolved shape`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns listOf(pageRow(1, 0, 0, columnSpan = 5))
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(3L), paged(columns = 6, rows = 3))

        assertEquals(0, written.captured.pageIndex)
        assertEquals(PAGED, written.captured.gridKind)
        assertEquals(5 to 0, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `an auto-added tile never lands past the resolved columns`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns listOf(pageRow(1, 0, 0), pageRow(2, 1, 0))
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(3L), paged(columns = 2, rows = 3))

        assertEquals(0 to 1, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `a full last page sends the auto-added tile to a new page`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns listOf(pageRow(1, 0, 0), pageRow(2, 1, 0))
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(3L), paged(columns = 2, rows = 1))

        assertEquals(1, written.captured.pageIndex)
        assertEquals(0 to 0, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `an auto-added media tile needs room for its minimum span`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns listOf(pageRow(1, 0, 0))
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.LocalMedia("/v.mp4"), paged(columns = 3, rows = 2))

        assertEquals(1 to 0, written.captured.columnIndex to written.captured.rowIndex)
        assertEquals(2 to 2, written.captured.columnSpan to written.captured.rowSpan)
    }

    @Test
    fun `a scroll append reads only scroll tiles and never pages`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getPage(OWNER, SCROLL, 0) } returns listOf(
            pageRow(1, 0, 0, kind = SCROLL),
            pageRow(2, 1, 0, kind = SCROLL)
        )
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(3L), scrolling(lanes = 2, axis = HomeScrollAxis.VERTICAL))

        assertEquals(SCROLL, written.captured.gridKind)
        assertEquals(0, written.captured.pageIndex)
        assertEquals(0 to 1, written.captured.columnIndex to written.captured.rowIndex)
        coVerify(exactly = 0) { dao.getMaxPageIndex(any(), any()) }
        coVerify(exactly = 0) { dao.getPage(any(), PAGED, any()) }
    }

    @Test
    fun `a paged append reads only paged tiles`() = runTest {
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns emptyList()
        coEvery { dao.insert(any()) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(3L), paged(columns = 2, rows = 2))

        coVerify(exactly = 0) { dao.getMaxPageIndex(any(), SCROLL) }
        coVerify(exactly = 0) { dao.getPage(any(), SCROLL, any()) }
    }

    @Test
    fun `a scroll append past a full screen keeps going down the canvas`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getPage(OWNER, SCROLL, 0) } returns (0 until 5).flatMap { row ->
            listOf(pageRow(row * 2L + 1, 0, row, kind = SCROLL), pageRow(row * 2L + 2, 1, row, kind = SCROLL))
        }
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(99L), scrolling(lanes = 2, axis = HomeScrollAxis.VERTICAL))

        assertEquals(0 to 5, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `a horizontal scroll append fills down a column before moving right`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getPage(OWNER, SCROLL, 0) } returns listOf(
            pageRow(1, 0, 0, kind = SCROLL),
            pageRow(2, 0, 1, kind = SCROLL),
            pageRow(3, 0, 2, kind = SCROLL),
            pageRow(4, 1, 0, kind = SCROLL)
        )
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(OWNER, HomeTileTargetRef.Game(9L), scrolling(lanes = 3, axis = HomeScrollAxis.HORIZONTAL))

        assertEquals(1 to 1, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `a scroll media append takes a clear block of its minimum span`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getPage(OWNER, SCROLL, 0) } returns listOf(pageRow(1, 1, 0, kind = SCROLL))
        coEvery { dao.insert(capture(written)) } returns TILE_ID

        repository.append(
            OWNER,
            HomeTileTargetRef.LocalMedia("/v.mp4"),
            scrolling(lanes = 3, axis = HomeScrollAxis.VERTICAL)
        )

        assertEquals(0 to 1, written.captured.columnIndex to written.captured.rowIndex)
        assertEquals(2 to 2, written.captured.columnSpan to written.captured.rowSpan)
    }

    @Test
    fun `observing one kind asks only for that kind and the tiles carry it`() = runTest {
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        every { dao.observeTiles(OWNER, SCROLL) } returns flowOf(listOf(pageRow(5, 0, 7, kind = SCROLL)))
        every { dao.observeTiles(OWNER, PAGED) } returns flowOf(listOf(pageRow(6, 0, 0)))

        val scrollTiles = repository.observeTiles(OWNER, HomeGridKind.SCROLL).first()
        val pagedTiles = repository.observeTiles(OWNER, HomeGridKind.PAGED).first()

        assertEquals(listOf(5L), scrollTiles.map { it.id })
        assertEquals(listOf(HomeGridKind.SCROLL), scrollTiles.map { it.kind })
        assertEquals(listOf(6L), pagedTiles.map { it.id })
        assertEquals(listOf(HomeGridKind.PAGED), pagedTiles.map { it.kind })
    }

    @Test
    fun `moving a scroll tile keeps it on the scroll grid at page zero`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(TILE_ID) } returns pageRow(TILE_ID, 0, 4, kind = SCROLL)
        coEvery { dao.update(capture(written)) } returns Unit
        val tile = com.nendo.argosy.domain.model.HomeTile(
            id = TILE_ID,
            pageIndex = 0,
            rect = TileRect(0, 4),
            target = HomeTileTargetRef.Game(3L),
            kind = HomeGridKind.SCROLL
        )

        repository.move(tile, TileRect(1, 40), pageIndex = 3)

        assertEquals(SCROLL, written.captured.gridKind)
        assertEquals(0, written.captured.pageIndex)
        assertEquals(1 to 40, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `moving a tile rewrites only its page and rectangle`() = runTest {
        val stored = HomeTileEntity(
            id = TILE_ID,
            ownerUserId = null,
            gridKind = PAGED,
            pageIndex = 1,
            pageId = 11L,
            artStyle = "logo",
            coverScale = "FIT",
            columnIndex = 0,
            rowIndex = 0,
            targetType = "GAME",
            gameId = 3L,
            createdAt = 1_000L
        )
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(TILE_ID) } returns stored
        coEvery { dao.update(capture(written)) } returns Unit
        val tile = com.nendo.argosy.domain.model.HomeTile(
            id = TILE_ID,
            pageIndex = 1,
            rect = TileRect(0, 0),
            target = HomeTileTargetRef.Game(3L)
        )

        repository.move(tile, TileRect(2, 1, columnSpan = 2, rowSpan = 1), pageIndex = 2)

        assertEquals(
            stored.copy(pageIndex = 2, columnIndex = 2, rowIndex = 1, columnSpan = 2, rowSpan = 1),
            written.captured
        )
    }

    @Test
    fun `moving a tile whose row is gone writes nothing`() = runTest {
        coEvery { dao.getById(TILE_ID) } returns null
        val tile = com.nendo.argosy.domain.model.HomeTile(
            id = TILE_ID,
            pageIndex = 0,
            rect = TileRect(0, 0),
            target = HomeTileTargetRef.Game(3L)
        )

        repository.move(tile, TileRect(1, 1))

        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `a scroll reflow writes every moved scroll row in one update and keeps their other columns`() = runTest {
        val first = pageRow(1, 2, 0, kind = SCROLL).copy(pageId = 5L, artStyle = "logo", createdAt = 7L)
        val second = pageRow(2, 3, 0, kind = SCROLL).copy(coverScale = "FIT", createdAt = 8L)
        val third = pageRow(3, 0, 1, kind = SCROLL)
        coEvery { dao.getPage(OWNER, SCROLL, 0) } returns listOf(first, second, third)
        val written = slot<List<HomeTileEntity>>()
        coEvery { dao.updateAll(capture(written)) } returns Unit

        repository.reflowScroll(
            OWNER,
            scrolling(lanes = 4, axis = HomeScrollAxis.VERTICAL),
            scrolling(lanes = 2, axis = HomeScrollAxis.VERTICAL)
        )

        coVerify(exactly = 1) { dao.updateAll(any()) }
        coVerify(exactly = 0) { dao.update(any()) }
        coVerify(exactly = 0) { dao.getPage(any(), PAGED, any()) }
        assertEquals(
            listOf(first.copy(columnIndex = 0), second.copy(columnIndex = 1)),
            written.captured
        )
    }

    @Test
    fun `entering or leaving scroll mode never reads or writes tiles`() = runTest {
        repository.reflowScroll(OWNER, paged(columns = 4, rows = 3), scrolling(lanes = 2, axis = HomeScrollAxis.VERTICAL))
        repository.reflowScroll(OWNER, scrolling(lanes = 2, axis = HomeScrollAxis.VERTICAL), paged(columns = 4, rows = 3))

        coVerify(exactly = 0) { dao.getPage(any(), any(), any()) }
        coVerify(exactly = 0) { dao.updateAll(any()) }
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `retargeting keeps the stored grid kind`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(TILE_ID) } returns pageRow(TILE_ID, 0, 9, kind = SCROLL)
        coEvery { dao.update(capture(written)) } returns Unit

        repository.retarget(TILE_ID, HomeTileTargetRef.Game(4L), emptyList())

        assertEquals(SCROLL, written.captured.gridKind)
    }

    @Test
    fun `removing a page only touches paged tiles`() = runTest {
        repository.removePage(OWNER, 1)

        coVerify { dao.deleteEpisodesForPage(OWNER, PAGED, 1) }
        coVerify { dao.deletePage(OWNER, PAGED, 1) }
        coVerify { dao.shiftPagesDown(OWNER, PAGED, 1) }
        coVerify(exactly = 0) { dao.deletePage(any(), SCROLL, any()) }
    }

    @Test
    fun `a link with no stored config reads back unfiltered`() = runTest {
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        every { dao.observeTiles(OWNER, PAGED) } returns flowOf(
            listOf(
                HomeTileEntity(
                    id = TILE_ID,
                    ownerUserId = OWNER,
                    pageIndex = 0,
                    columnIndex = 0,
                    rowIndex = 0,
                    targetType = "FEATURE",
                    featureKind = FeatureTileKind.LIBRARY_LINK.name
                )
            )
        )

        val read = repository.observeTiles(OWNER, HomeGridKind.PAGED).first().single().target

        assertTrue(
            (read as HomeTileTargetRef.Feature).libraryLink?.isUnfiltered == true
        )
    }
}
