package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.DatabaseCallbacks
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangeSeqTriggersSqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var gameDao: GameDao
    private lateinit var changeDao: GameChangeDao
    private lateinit var artDao: GameArtDao

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).addCallback(DatabaseCallbacks).allowMainThreadQueries().build()
        gameDao = db.gameDao()
        changeDao = db.gameChangeDao()
        artDao = db.gameArtDao()
        db.platformDao().insert(
            PlatformEntity(id = 1L, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        db.platformDao().insert(
            PlatformEntity(id = 2L, slug = "gba", name = "GBA", shortName = "GBA", romExtensions = "gba")
        )
        Unit
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun game(
        id: Long,
        platformId: Long = 1L,
        title: String = "Game $id",
        changeSeq: Long = 0
    ) = GameEntity(
        id = id,
        platformId = platformId,
        platformSlug = if (platformId == 1L) "snes" else "gba",
        title = title,
        sortTitle = title.lowercase(),
        localPath = null,
        rommId = null,
        igdbId = null,
        source = GameSource.LOCAL_ONLY,
        changeSeq = changeSeq
    )

    private suspend fun seqOf(id: Long): Long = gameDao.getById(id)!!.changeSeq

    private suspend fun artSeqOf(gameId: Long, slot: String): Long = artDao.get(gameId, slot)!!.changeSeq

    private suspend fun artMaxSeq(): Long = artDao.getAll().maxOf { it.changeSeq }

    @Test
    fun inserts_stamp_strictly_increasing_sequences() = runBlocking {
        gameDao.insert(game(1L))
        gameDao.insert(game(2L))

        assertTrue(seqOf(1L) > 0)
        assertTrue(seqOf(2L) > seqOf(1L))
        assertEquals(seqOf(2L), changeDao.maxChangeSeq())
    }

    @Test
    fun an_update_restamps_the_row_above_the_table_max() = runBlocking {
        gameDao.insert(game(1L))
        gameDao.insert(game(2L))
        val maxBefore = changeDao.maxChangeSeq()

        gameDao.update(game(1L, title = "Renamed", changeSeq = seqOf(1L)))

        assertTrue(seqOf(1L) > maxBefore)
        assertEquals(seqOf(1L), changeDao.maxChangeSeq())
    }

    @Test
    fun an_update_writing_a_lower_sequence_is_restamped() = runBlocking {
        gameDao.insert(game(1L))
        gameDao.insert(game(2L))
        gameDao.update(game(2L, title = "Renamed", changeSeq = seqOf(2L)))
        val maxBefore = changeDao.maxChangeSeq()

        gameDao.update(game(2L, title = "Renamed again", changeSeq = 1L))

        assertTrue(seqOf(2L) > maxBefore)
    }

    @Test
    fun deleting_the_top_row_never_lowers_the_max_and_later_updates_stamp_above_every_handed_out_seq() =
        runBlocking {
            gameDao.insert(game(1L))
            gameDao.insert(game(2L))
            val handedOut = mutableListOf(seqOf(1L), seqOf(2L))
            val maxBefore = changeDao.maxChangeSeq()

            gameDao.delete(2L)

            assertTrue(changeDao.maxChangeSeq() >= maxBefore)
            handedOut += seqOf(1L)

            gameDao.update(game(1L, title = "Renamed", changeSeq = seqOf(1L)))

            assertTrue(seqOf(1L) > handedOut.max())
        }

    @Test
    fun platform_changes_return_only_rows_past_the_cursor_and_flag_rows_that_left_the_scope() = runBlocking {
        gameDao.insert(game(1L))
        gameDao.insert(game(2L))
        gameDao.insert(game(3L))
        val cursor = changeDao.maxChangeSeq()

        gameDao.update(game(2L, title = "Renamed", changeSeq = seqOf(2L)))
        gameDao.update(game(3L, platformId = 2L, changeSeq = seqOf(3L)))

        val changes = changeDao.platformListChangedSince(cursor, 1L, null)

        assertEquals(mapOf(2L to true, 3L to false), changes.associate { it.item.id to it.inScope })
    }

    @Test
    fun art_inserts_and_updates_stamp_above_the_table_max() = runBlocking {
        gameDao.insert(game(7L))
        artDao.insertSlotIfAbsent(7L, "COVER")
        artDao.insertSlotIfAbsent(7L, "LOGO")

        assertTrue(artSeqOf(7L, "LOGO") > artSeqOf(7L, "COVER"))
        val maxBefore = artMaxSeq()

        artDao.updateSourceUrl(7L, "COVER", "https://romm/covers/7.png")

        assertTrue(artSeqOf(7L, "COVER") > maxBefore)
    }

    @Test
    fun deleting_one_art_slot_restamps_the_game_s_remaining_slots() = runBlocking {
        gameDao.insert(game(7L))
        gameDao.insert(game(8L))
        artDao.insertSlotIfAbsent(7L, "COVER")
        artDao.insertSlotIfAbsent(7L, "LOGO")
        artDao.insertSlotIfAbsent(8L, "COVER")
        val maxBefore = artMaxSeq()

        db.openHelper.writableDatabase.execSQL("DELETE FROM game_art WHERE gameId = 7 AND slot = 'LOGO'")

        assertTrue(artSeqOf(7L, "COVER") > maxBefore)
        assertEquals(listOf(7L), artDao.changedSince(maxBefore).map { it.gameId }.distinct())
    }

    @Test
    fun deleting_all_of_one_game_s_art_keeps_the_next_stamp_above_every_previous_one() = runBlocking {
        gameDao.insert(game(7L))
        gameDao.insert(game(8L))
        artDao.insertSlotIfAbsent(7L, "COVER")
        artDao.insertSlotIfAbsent(7L, "LOGO")
        artDao.insertSlotIfAbsent(8L, "COVER")
        artDao.insertSlotIfAbsent(8L, "LOGO")
        val handedOut = artDao.getAll().map { it.changeSeq }.toMutableList()

        db.openHelper.writableDatabase.execSQL("DELETE FROM game_art WHERE gameId = 8")

        assertTrue(artMaxSeq() >= handedOut.max())
        handedOut += artDao.getAll().map { it.changeSeq }

        artDao.updateSourceUrl(7L, "COVER", "https://romm/covers/7.png")

        assertTrue(artSeqOf(7L, "COVER") > handedOut.max())
    }
}
