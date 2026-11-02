package com.nendo.argosy.data.scanner

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.LocalPlatformIds
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.repository.AppsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidGameScannerUnlinkTest {

    private val appsRepository: AppsRepository = mockk(relaxed = true)
    private val gameDao: GameDao = mockk(relaxed = true)
    private val platformDao: PlatformDao = mockk(relaxed = true)
    private val syncPreferences: SyncPreferencesRepository = mockk(relaxed = true)

    private val scanner = AndroidGameScanner(
        appsRepository = appsRepository,
        appCategoryDao = mockk(relaxed = true),
        gameDao = gameDao,
        platformDao = platformDao,
        syncPreferencesRepository = syncPreferences,
        metadataFetcher = mockk(relaxed = true),
        gameArtDao = mockk(relaxed = true)
    )

    private fun linkedGame(id: Long, pkg: String, rommId: Long?, localPath: String? = null) = GameEntity(
        id = id,
        platformId = LocalPlatformIds.ANDROID,
        platformSlug = "android",
        title = "Game $id",
        sortTitle = "game $id",
        localPath = localPath,
        rommId = rommId,
        igdbId = null,
        source = GameSource.ANDROID_APP,
        packageName = pkg,
        titleId = pkg
    )

    @Test
    fun `a RomM game whose app was uninstalled returns to remote and keeps its package id`() = runBlocking {
        coEvery { gameDao.getBySource(GameSource.ANDROID_APP) } returns listOf(linkedGame(1, "com.gone", rommId = 42))
        every { appsRepository.isPackageMissing("com.gone") } returns true
        val updated = slot<GameEntity>()
        coEvery { gameDao.update(capture(updated)) } returns Unit

        val count = scanner.unlinkUninstalledApps()

        assertEquals(1, count)
        assertNull(updated.captured.packageName)
        assertEquals(GameSource.ROMM_REMOTE, updated.captured.source)
        assertEquals("com.gone", updated.captured.titleId)
        coVerify(exactly = 0) { gameDao.delete(any<Long>()) }
        coVerify { platformDao.updateGameCount(LocalPlatformIds.ANDROID, any()) }
    }

    @Test
    fun `a RomM game that still has a local file becomes synced`() = runBlocking {
        coEvery { gameDao.getBySource(GameSource.ANDROID_APP) } returns
            listOf(linkedGame(1, "com.gone", rommId = 42, localPath = "/roms/android/game.apk"))
        every { appsRepository.isPackageMissing("com.gone") } returns true
        val updated = slot<GameEntity>()
        coEvery { gameDao.update(capture(updated)) } returns Unit

        scanner.unlinkUninstalledApps()

        assertEquals(GameSource.ROMM_SYNCED, updated.captured.source)
    }

    @Test
    fun `a local app row whose app was uninstalled is removed`() = runBlocking {
        coEvery { gameDao.getBySource(GameSource.ANDROID_APP) } returns listOf(
            linkedGame(1, "com.local", rommId = null),
            linkedGame(2, "com.orphan", rommId = -2)
        )
        every { appsRepository.isPackageMissing(any()) } returns true

        scanner.unlinkUninstalledApps()

        coVerify { gameDao.delete(1L) }
        coVerify { gameDao.delete(2L) }
        coVerify(exactly = 0) { gameDao.update(any()) }
    }

    @Test
    fun `installed apps are left linked`() = runBlocking {
        coEvery { gameDao.getBySource(GameSource.ANDROID_APP) } returns listOf(linkedGame(1, "com.here", rommId = 42))
        every { appsRepository.isPackageMissing("com.here") } returns false

        val count = scanner.unlinkUninstalledApps()

        assertEquals(0, count)
        coVerify(exactly = 0) { gameDao.update(any()) }
        coVerify(exactly = 0) { gameDao.delete(any<Long>()) }
        coVerify(exactly = 0) { platformDao.updateGameCount(any(), any()) }
    }

    @Test
    fun `a named package limits the pass to that app`() = runBlocking {
        coEvery { gameDao.getBySource(GameSource.ANDROID_APP) } returns listOf(
            linkedGame(1, "com.gone", rommId = 42),
            linkedGame(2, "com.other", rommId = 43)
        )
        every { appsRepository.isPackageMissing(any()) } returns true
        val updated = slot<GameEntity>()
        coEvery { gameDao.update(capture(updated)) } returns Unit

        val count = scanner.unlinkUninstalledApps("com.gone")

        assertEquals(1, count)
        assertEquals(1L, updated.captured.id)
    }
}
