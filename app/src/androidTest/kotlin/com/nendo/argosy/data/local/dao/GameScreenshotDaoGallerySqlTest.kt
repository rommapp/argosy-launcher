package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameScreenshotEntity
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.data.local.entity.ScreenshotSource
import com.nendo.argosy.data.model.GameSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameScreenshotDaoGallerySqlTest {

    private lateinit var db: ALauncherDatabase
    private lateinit var dao: GameScreenshotDao

    private val ownShot = GameScreenshotEntity(
        gameId = 7L,
        position = 0,
        sourceUrl = "https://romm/api/screenshots/9/content",
        cachedPath = "/img/snes/screenshots/uss_42_9_abc.png",
        cachedFromUrl = "https://romm/api/screenshots/9/content",
        source = ScreenshotSource.OWN,
        rommScreenshotId = 9L,
        ownerUsername = "me"
    )

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.gameScreenshotDao()
        db.platformDao().insert(
            PlatformEntity(id = 1L, slug = "snes", name = "SNES", shortName = "SNES", romExtensions = "sfc")
        )
        db.gameDao().insert(
            GameEntity(
                id = 7L,
                platformId = 1L,
                platformSlug = "snes",
                title = "Game",
                sortTitle = "game",
                localPath = null,
                rommId = 42L,
                igdbId = null,
                source = GameSource.ROMM_REMOTE
            )
        )
        dao.replaceSources(7L, listOf("https://cdn/a.jpg", "https://cdn/b.jpg"))
        dao.replaceGallery(7L, listOf(ownShot))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun a_library_sync_rewrite_of_official_rows_keeps_gallery_rows() = runBlocking {
        dao.replaceSources(7L, listOf("https://cdn/c.jpg"))

        assertEquals(listOf(ownShot), dao.getGalleryForGame(7L))
        assertEquals(listOf("https://cdn/c.jpg"), dao.getForGame(7L).map { it.sourceUrl })
    }

    @Test
    fun an_empty_official_list_keeps_gallery_rows() = runBlocking {
        dao.replaceSources(7L, emptyList())

        assertTrue(dao.getForGame(7L).isEmpty())
        assertEquals(listOf(ownShot), dao.getGalleryForGame(7L))
    }

    @Test
    fun official_readers_and_the_pending_pass_see_official_rows_only() = runBlocking {
        assertEquals(2, dao.getForGame(7L).size)
        assertEquals(2, dao.countWithSource())
        assertTrue(dao.getPending().none { it.sourceUrl == ownShot.sourceUrl })
        assertEquals(3, dao.getAllForGame(7L).size)
        assertTrue(dao.getCached().any { it.cachedPath == ownShot.cachedPath })
    }

    @Test
    fun forgetting_official_art_leaves_the_gallery_cache_in_place() = runBlocking {
        dao.clearCachedForGame(7L)

        assertEquals(ownShot.cachedPath, dao.getGalleryForGame(7L).single().cachedPath)
    }

    @Test
    fun replacing_the_gallery_leaves_official_rows_alone() = runBlocking {
        dao.replaceGallery(7L, emptyList())

        assertTrue(dao.getGalleryForGame(7L).isEmpty())
        assertEquals(2, dao.getForGame(7L).size)
    }
}
