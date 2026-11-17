package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.HomeTileDao
import com.nendo.argosy.data.local.entity.HomeTileEntity
import com.nendo.argosy.data.model.SourceFilter
import com.nendo.argosy.domain.model.CustomGridLayout
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.FOLDER_MEMBER_PAGE_INDEX
import com.nendo.argosy.domain.model.FolderAccent
import com.nendo.argosy.domain.model.FolderAppearance
import com.nendo.argosy.domain.model.FolderBackdropStyle
import com.nendo.argosy.domain.model.FolderCoverStyle
import com.nendo.argosy.domain.model.FolderMemberSize
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeTileTargetRef
import com.nendo.argosy.domain.model.LibraryLinkFilters
import com.nendo.argosy.domain.model.PlayerCountBucket
import com.nendo.argosy.domain.model.RaProgressFilter
import com.nendo.argosy.domain.model.StickerOutline
import com.nendo.argosy.domain.model.StickerOutlineColor
import com.nendo.argosy.domain.model.StickerOutlineWidth
import com.nendo.argosy.domain.model.TileArtSlot
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.domain.model.TileRect
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER = 1L
private const val FOLDER_ID = 50L
private const val PAGED = "PAGED"

class HomeTileFolderRepositoryTest {

    private val dao = mockk<HomeTileDao>(relaxed = true)
    private val repository = HomeTileRepository(dao)
    private val layout = CustomGridLayout(CustomGridShape(3, 2))

    private suspend fun roundTrip(target: HomeTileTargetRef): HomeTileTargetRef {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns 7L
        repository.place(OWNER, HomeGridKind.PAGED, 0, TileRect(0, 0), target)
        every { dao.observeTiles(OWNER, PAGED) } returns flowOf(listOf(written.captured.copy(id = 7L)))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return repository.observeTiles(OWNER, HomeGridKind.PAGED).first().single().target
    }

    private fun gameRow(id: Long, column: Int, row: Int, parent: Long? = null, page: Int = 0) = HomeTileEntity(
        id = id,
        ownerUserId = OWNER,
        gridKind = PAGED,
        pageIndex = page,
        columnIndex = column,
        rowIndex = row,
        targetType = "GAME",
        gameId = id,
        parentTileId = parent
    )

    private fun folderRow(rule: String? = null) = HomeTileEntity(
        id = FOLDER_ID,
        ownerUserId = OWNER,
        gridKind = PAGED,
        pageIndex = 0,
        columnIndex = 2,
        rowIndex = 1,
        targetType = "FOLDER",
        featureConfig = rule ?: "{\"name\":\"RPGs\"}"
    )

    @Test
    fun `an auto-folder keeps its name and every rule category across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Folder(
            name = "Recent RPGs",
            rule = LibraryLinkFilters(
                source = SourceFilter.FAVORITES,
                platformIds = setOf(2L, 5L),
                genres = setOf("Role-playing (RPG)"),
                series = setOf("Final Fantasy"),
                players = PlayerCountBucket.FOUR_PLUS,
                developers = setOf("Square", "Atlus"),
                releaseYearFrom = 1990,
                releaseYearTo = 1999,
                playStatuses = setOf("finished", "retired"),
                addedWithinDays = 30,
                raProgress = RaProgressFilter.IN_PROGRESS
            )
        )

        assertEquals(target, roundTrip(target))
    }

    @Test
    fun `a hand folder reads back with no rule`() = runTest {
        val read = roundTrip(HomeTileTargetRef.Folder(name = "Favourites"))

        assertEquals(HomeTileTargetRef.Folder(name = "Favourites"), read)
    }

    private suspend fun readStored(featureConfig: String?): HomeTileTargetRef {
        every { dao.observeTiles(OWNER, PAGED) } returns flowOf(listOf(folderRow().copy(featureConfig = featureConfig)))
        every { dao.observeAllEpisodes() } returns flowOf(emptyList())
        return repository.observeTiles(OWNER, HomeGridKind.PAGED).first().single().target
    }

    @Test
    fun `a folder keeps every appearance field across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Folder(
            name = "Shmups",
            rule = LibraryLinkFilters(genres = setOf("Shooter")),
            appearance = FolderAppearance(
                cover = FolderCoverStyle.STICKER,
                coverSource = TileImageSource.GameArt(gameId = 12L, slot = TileArtSlot.LOGO),
                coverOutline = StickerOutline(enabled = true, color = StickerOutlineColor.ACCENT),
                showName = false,
                backdrop = FolderBackdropStyle.IMAGE,
                backdropSource = TileImageSource.File("/sdcard/Pictures/stars.png"),
                accent = FolderAccent.VIOLET,
                memberSize = FolderMemberSize.LARGE
            )
        )

        assertEquals(target, roundTrip(target))
    }

    @Test
    fun `a default appearance writes the rule-only shape`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.insert(capture(written)) } returns 7L

        repository.place(OWNER, HomeGridKind.PAGED, 0, TileRect(0, 0), HomeTileTargetRef.Folder(name = "Plain"))

        assertFalse(written.captured.featureConfig.orEmpty().contains("appearance"))
    }

    @Test
    fun `a stored rule-only folder reads back with the default appearance`() = runTest {
        val read = readStored("{\"name\":\"Auto\",\"rule\":{\"source\":\"FAVORITES\"}}")

        assertEquals(
            HomeTileTargetRef.Folder(name = "Auto", rule = LibraryLinkFilters(source = SourceFilter.FAVORITES)),
            read
        )
    }

    @Test
    fun `a folder row with no config reads as an unnamed hand folder`() = runTest {
        assertEquals(HomeTileTargetRef.Folder(name = ""), readStored(null))
    }

    @Test
    fun `appearance tokens this build cannot read fall back to their defaults`() = runTest {
        val read = readStored(
            "{\"name\":\"Odd\",\"appearance\":{\"cover\":\"HOLOGRAM\",\"accent\":\"PLAID\",\"memberSize\":\"HUGE\"," +
                "\"coverSource\":{\"imageSource\":\"FILE\",\"path\":\"\"}}}"
        ) as HomeTileTargetRef.Folder

        assertEquals(FolderAppearance(), read.appearance)
    }

    @Test
    fun `a sticker keeps its source and outline across a write and a read`() = runTest {
        val target = HomeTileTargetRef.Sticker(
            source = TileImageSource.GameArt(gameId = 12L, slot = TileArtSlot.LOGO),
            outline = StickerOutline(enabled = true, width = StickerOutlineWidth.THICK, color = StickerOutlineColor.ACCENT)
        )

        assertEquals(target, roundTrip(target))
    }

    @Test
    fun `an image file reads back as the same file`() = runTest {
        val target = HomeTileTargetRef.Image(TileImageSource.File("/sdcard/Pictures/wall.png"))

        assertEquals(target, roundTrip(target))
    }

    @Test
    fun `moving a tile into a folder parks it off every page after the last member`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(FOLDER_ID) } returns folderRow()
        coEvery { dao.getById(3L) } returns gameRow(3L, 0, 0)
        coEvery { dao.getFolderMembers(FOLDER_ID) } returns listOf(gameRow(4L, 0, 0, FOLDER_ID, FOLDER_MEMBER_PAGE_INDEX))
        coEvery { dao.update(capture(written)) } returns Unit

        assertTrue(repository.moveIntoFolder(3L, FOLDER_ID))

        assertEquals(FOLDER_ID, written.captured.parentTileId)
        assertEquals(FOLDER_MEMBER_PAGE_INDEX, written.captured.pageIndex)
        assertEquals(1, written.captured.columnIndex)
    }

    @Test
    fun `a folder never goes inside another folder`() = runTest {
        coEvery { dao.getById(FOLDER_ID) } returns folderRow()
        coEvery { dao.getById(60L) } returns folderRow().copy(id = 60L)

        assertFalse(repository.moveIntoFolder(60L, FOLDER_ID))
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `nothing is moved into an auto-folder`() = runTest {
        coEvery { dao.getById(FOLDER_ID) } returns folderRow("{\"name\":\"Auto\",\"rule\":{\"source\":\"ALL\"}}")
        coEvery { dao.getById(3L) } returns gameRow(3L, 0, 0)

        assertFalse(repository.moveIntoFolder(3L, FOLDER_ID))
        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `deleting a folder returns its members to free cells before the folder row goes`() = runTest {
        val members = listOf(
            gameRow(4L, 0, 0, FOLDER_ID, FOLDER_MEMBER_PAGE_INDEX),
            gameRow(5L, 1, 0, FOLDER_ID, FOLDER_MEMBER_PAGE_INDEX)
        )
        val updates = mutableListOf<HomeTileEntity>()
        coEvery { dao.getById(FOLDER_ID) } returns folderRow()
        coEvery { dao.getFolderMembers(FOLDER_ID) } returns members
        coEvery { dao.getPage(OWNER, PAGED, 0) } returnsMany listOf(
            listOf(gameRow(1L, 0, 0), folderRow()),
            listOf(gameRow(1L, 0, 0), folderRow(), gameRow(4L, 1, 0))
        )
        coEvery { dao.update(capture(updates)) } returns Unit

        repository.deleteFolder(FOLDER_ID, layout)

        assertEquals(listOf(4L, 5L), updates.map { it.id })
        assertTrue(updates.all { it.parentTileId == null && it.pageIndex == 0 })
        assertEquals(listOf(1 to 0, 2 to 0), updates.map { it.columnIndex to it.rowIndex })
        coVerifyOrder {
            dao.update(match { it.id == 5L })
            dao.deleteTileWithEpisodes(FOLDER_ID)
        }
    }

    @Test
    fun `a member released from a full page goes to a new page`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(4L) } returns gameRow(4L, 0, 0, FOLDER_ID, FOLDER_MEMBER_PAGE_INDEX)
        coEvery { dao.getById(FOLDER_ID) } returns folderRow()
        coEvery { dao.getMaxPageIndex(OWNER, PAGED) } returns 0
        coEvery { dao.getPage(OWNER, PAGED, 0) } returns (0 until 3).flatMap { column ->
            (0 until 2).map { row -> gameRow(100L + column * 2 + row, column, row) }
        }
        coEvery { dao.update(capture(written)) } returns Unit

        repository.removeFromFolder(4L, layout)

        assertNull(written.captured.parentTileId)
        assertEquals(1, written.captured.pageIndex)
        assertEquals(0 to 0, written.captured.columnIndex to written.captured.rowIndex)
    }

    @Test
    fun `retargeting a folder member keeps it inside its folder`() = runTest {
        val written = slot<HomeTileEntity>()
        coEvery { dao.getById(4L) } returns gameRow(4L, 2, 0, FOLDER_ID, FOLDER_MEMBER_PAGE_INDEX)
        coEvery { dao.update(capture(written)) } returns Unit

        repository.retarget(4L, HomeTileTargetRef.Game(9L), emptyList())

        assertEquals(FOLDER_ID, written.captured.parentTileId)
        assertEquals(FOLDER_MEMBER_PAGE_INDEX, written.captured.pageIndex)
    }

    @Test
    fun `removing a page removes the members of folders on it`() = runTest {
        repository.removePage(OWNER, 1)

        coVerify { dao.deleteFolderMemberEpisodesForPage(OWNER, PAGED, 1) }
        coVerify { dao.deleteFolderMembersForPage(OWNER, PAGED, 1) }
    }
}
