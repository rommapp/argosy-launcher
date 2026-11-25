package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.RommMusicTrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RommMusicIndexDaoSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var dao: RommMusicIndexDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.rommMusicIndexDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun row(owner: Long, romFileId: Long, romId: Long) = RommMusicTrackEntity(
        ownerUserId = owner,
        romFileId = romFileId,
        romId = romId,
        title = "Track $romFileId",
        artist = null,
        album = null,
        genre = null,
        year = null,
        track = null,
        disc = null,
        durationSeconds = 60.0,
        gameName = "Game $romId",
        platformId = 1L,
        platformSlug = "snes",
        platformName = "SNES",
        streamUrl = "/api/raw/$romFileId",
        coverUrl = null,
        gameCoverUrl = null
    )

    @Test
    fun readsNeverReturnAnotherOwnersRows() = runBlocking {
        dao.replaceForOwner(1L, listOf(row(1L, 10L, 100L), row(1L, 11L, 100L)), 1_000L)
        dao.replaceForOwner(2L, listOf(row(2L, 10L, 100L)), 2_000L)

        assertEquals(listOf(10L, 11L), dao.tracks(1L).map { it.romFileId }.sorted())
        assertEquals(listOf(10L), dao.tracks(2L).map { it.romFileId })
        assertEquals(2, dao.tracksForRom(1L, 100L).size)
        assertEquals(1, dao.tracksForRom(2L, 100L).size)
        assertEquals(1_000L, dao.state(1L)?.fetchedAt)
        assertEquals(1, dao.state(2L)?.trackCount)
    }

    @Test
    fun replacingOneOwnerLeavesTheOtherIntact() = runBlocking {
        dao.replaceForOwner(1L, listOf(row(1L, 10L, 100L)), 1_000L)
        dao.replaceForOwner(2L, listOf(row(2L, 20L, 200L)), 1_000L)

        dao.replaceForOwner(1L, listOf(row(1L, 12L, 101L)), 3_000L)

        assertEquals(listOf(12L), dao.tracks(1L).map { it.romFileId })
        assertEquals(listOf(20L), dao.tracks(2L).map { it.romFileId })
        assertEquals(3_000L, dao.state(1L)?.fetchedAt)
    }

    @Test
    fun deleteByOwnerClearsOnlyThatOwner() = runBlocking {
        dao.replaceForOwner(1L, listOf(row(1L, 10L, 100L)), 1_000L)
        dao.replaceForOwner(2L, listOf(row(2L, 20L, 200L)), 1_000L)

        dao.deleteByOwner(1L)

        assertTrue(dao.tracks(1L).isEmpty())
        assertNull(dao.state(1L))
        assertEquals(1, dao.tracks(2L).size)
    }
}
