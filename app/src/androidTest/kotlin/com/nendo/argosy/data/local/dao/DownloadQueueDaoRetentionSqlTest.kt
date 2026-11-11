package com.nendo.argosy.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadQueueDaoRetentionSqlTest {
    private lateinit var db: ALauncherDatabase
    private lateinit var dao: DownloadQueueDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            ALauncherDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.downloadQueueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun failedDownloadCountsForItsOwnerEvenWithZeroPersistedProgress() = runBlocking {
        val failed = DownloadQueueEntity(
            gameId = 1, rommId = 10, fileName = "fixture.bin", gameTitle = "Synthetic fixture",
            platformSlug = "nes", coverPath = null, bytesDownloaded = 0, totalBytes = 10240,
            state = "FAILED", errorReason = null, tempFilePath = null, ownerUserId = 99,
            createdAt = java.time.Instant.ofEpochMilli(1000)
        )
        val failedId = dao.insert(failed)
        dao.insert(failed.copy(gameId = 2, ownerUserId = 100))
        dao.insert(failed.copy(gameId = 3, state = "COMPLETED"))
        dao.insert(failed.copy(gameId = 4, ownerUserId = null))

        assertEquals(1, dao.countUnfinishedForOwner(99))
        assertEquals(1, dao.countUnfinishedForOwner(100))
        assertEquals(0, dao.countUnfinishedForOwner(101))

        dao.clearCompleted()
        assertEquals(failed.copy(id = failedId), dao.getById(failedId))
        assertEquals(1, dao.countUnfinishedForOwner(99))

        assertEquals(listOf(failed.copy(id = failedId)), dao.getByOwner(99))
        assertEquals(listOf(failed.copy(id = failedId)), dao.getAllByGameId(1))
    }
}
