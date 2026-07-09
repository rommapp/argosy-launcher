package com.nendo.argosy.libretro

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.repository.SaveCacheManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class SaveStateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun manager(gameDao: GameDao, cache: SaveCacheManager): SaveStateManager {
        val saves = tempFolder.newFolder("saves")
        val states = tempFolder.newFolder("states")
        return SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            gameDao = gameDao,
            saveCacheManager = cache
        )
    }

    private fun game(activeSaveApplied: Boolean = false) = GameEntity(
        id = GAME_ID,
        title = "Sonic Advance",
        sortTitle = "sonic advance",
        platformId = 1L,
        platformSlug = "gba",
        rommId = null,
        igdbId = null,
        localPath = null,
        source = GameSource.ROMM_SYNCED,
        activeSaveApplied = activeSaveApplied
    )

    private fun cacheEntity(hardcore: Boolean) = SaveCacheEntity(
        gameId = GAME_ID,
        emulatorId = "builtin",
        cachedAt = Instant.ofEpochMilli(1000),
        saveSize = 3,
        cachePath = "cache/x.srm",
        isHardcore = hardcore
    )

    // RetroAchievements permits SRAM in hardcore, so a hardcore resume must not start
    // fresh just because no save carries the hardcore tag -- it should reuse the active save.
    @Test
    fun `RESUME_HARDCORE reuses the casual save when no hardcore save exists`() = runBlocking {
        val gameDao = mockk<GameDao>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val casual = cacheEntity(hardcore = false)
        val casualBytes = byteArrayOf(1, 2, 3)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns null
        coEvery { gameDao.getById(GAME_ID) } returns game() // no active timestamp/channel -> most recent
        coEvery { cache.getMostRecentSave(GAME_ID) } returns casual
        coEvery { cache.getSaveBytesFromEntity(casual) } returns casualBytes

        val result = manager(gameDao, cache).restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertNotNull("hardcore should reuse the active SRAM instead of starting fresh", result.sramData)
        assertArrayEquals(casualBytes, result.sramData)
    }

    @Test
    fun `RESUME_HARDCORE still prefers an existing hardcore save`() = runBlocking {
        val gameDao = mockk<GameDao>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val hardcore = cacheEntity(hardcore = true)
        val hardcoreBytes = byteArrayOf(9, 9)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns hardcore
        coEvery { cache.isValidHardcoreSave(hardcore) } returns true
        coEvery { cache.getSaveBytesFromEntity(hardcore) } returns hardcoreBytes

        val result = manager(gameDao, cache).restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(hardcoreBytes, result.sramData)
    }

    @Test
    fun `activeSaveApplied honors the on-disk SRAM over the default resume pick`() = runBlocking {
        val saves = tempFolder.newFolder("applied-saves")
        val states = tempFolder.newFolder("applied-states")
        val restoredBytes = byteArrayOf(7, 7, 7)
        File(saves, "Sonic Advance.srm").writeBytes(restoredBytes)

        val gameDao = mockk<GameDao>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        coEvery { gameDao.getById(GAME_ID) } returns game(activeSaveApplied = true)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns cacheEntity(hardcore = true)

        val mgr = SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            gameDao = gameDao,
            saveCacheManager = cache
        )
        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(
            "activeSaveApplied must load the on-disk .srm, not the cache's hardcore save",
            restoredBytes,
            result.sramData
        )
    }

    // --- Flat live layout (refactor: channels are cache-only) ---

    @Test
    fun `getSlotFile is flat, never under a channel subdir`() {
        val saves = tempFolder.newFolder("flat-saves")
        val states = tempFolder.newFolder("flat-states")
        val mgr = SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            gameDao = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            channelName = "some-channel"
        )
        val auto = mgr.getSlotFile(SaveStateManager.AUTO_SLOT)
        assertEquals(File(states, "Sonic Advance.state.auto"), auto)
        assertEquals("state file must sit directly in statesDir, not a channel subdir", states, auto.parentFile)
    }

    @Test
    fun `initialize lifts legacy channel states up to flat`() {
        val saves = tempFolder.newFolder("mig-saves")
        val states = tempFolder.newFolder("mig-states")
        File(states, "default").mkdirs()
        File(states, "default/Sonic Advance.state.auto").writeBytes(byteArrayOf(1, 2))
        val mgr = SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            gameDao = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            channelName = null
        )
        mgr.initializeFromExistingSave(null)
        assertTrue(
            "legacy default/ state should be migrated up to the flat states dir",
            File(states, "Sonic Advance.state.auto").exists()
        )
    }

    companion object {
        private const val GAME_ID = 1L
    }
}
