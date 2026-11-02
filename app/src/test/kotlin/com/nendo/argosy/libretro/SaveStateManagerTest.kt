package com.nendo.argosy.libretro

import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.repository.ActiveSaveDiskJudge
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.sync.SaveClaim
import com.nendo.argosy.data.sync.SaveOwnershipTracker
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.swordfish.libretrodroid.GLRetroView
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

class SaveStateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val saveCacheDao: SaveCacheDao = mockk(relaxed = true)
    private val saveSyncDao: SaveSyncDao = mockk(relaxed = true)
    private val ownership: SaveOwnershipTracker = mockk(relaxed = true)
    private val judge = ActiveSaveDiskJudge(saveCacheDao, saveSyncDao, ownership)

    @Before
    fun stubJudgeInputs() {
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { ownership.claim(any(), any()) } returns SaveClaim.Unowned
    }

    private fun manager(
        activeSaveRepository: ActiveSaveRepository,
        cache: SaveCacheManager
    ): SaveStateManager {
        val saves = tempFolder.newFolder("saves")
        val states = tempFolder.newFolder("states")
        coEvery { cache.restoreThroughSigil(any()) } returns SigilRestore.NotRouted
        coEvery { cache.probeLocalSave(any(), any(), any()) } returns SaveCacheManager.LocalSaveProbe.Absent
        return SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            activeSaveRepository = activeSaveRepository,
            saveCacheManager = cache,
            diskJudge = judge,
            secureSaves = true,
            ownerUserId = OWNER
        )
    }

    private fun cacheEntity(hardcore: Boolean, applied: Boolean = false) = SaveCacheEntity(
        gameId = GAME_ID,
        emulatorId = "argosy",
        cachedAt = Instant.ofEpochMilli(1000),
        saveSize = 3,
        cachePath = "cache/x.srm",
        isHardcore = hardcore,
        activeSaveApplied = applied
    )

    // RetroAchievements permits SRAM in hardcore, so a hardcore resume must not start
    // fresh just because no save carries the hardcore tag -- it should reuse the active save.
    @Test
    fun `RESUME_HARDCORE reuses the casual save when no hardcore save exists`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val casual = cacheEntity(hardcore = false)
        val casualBytes = byteArrayOf(1, 2, 3)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns null
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentSave(GAME_ID) } returns casual
        coEvery { cache.getSaveBytesFromEntity(casual) } returns casualBytes

        val result = manager(activeSaveRepository, cache)
            .restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertNotNull("hardcore should reuse the active SRAM instead of starting fresh", result.sramData)
        assertArrayEquals(casualBytes, result.sramData)
    }

    @Test
    fun `RESUME_HARDCORE still prefers an existing hardcore save`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val hardcore = cacheEntity(hardcore = true)
        val hardcoreBytes = byteArrayOf(9, 9)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns hardcore
        coEvery { cache.isValidHardcoreSave(hardcore) } returns true
        coEvery { cache.getSaveBytesFromEntity(hardcore) } returns hardcoreBytes

        val result = manager(activeSaveRepository, cache)
            .restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(hardcoreBytes, result.sramData)
    }

    @Test
    fun `activeSaveApplied honors the on-disk SRAM over the default resume pick`() = runBlocking {
        val saves = tempFolder.newFolder("applied-saves")
        val states = tempFolder.newFolder("applied-states")
        val restoredBytes = byteArrayOf(7, 7, 7)
        File(saves, "Sonic Advance.srm").writeBytes(restoredBytes)

        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns
            cacheEntity(hardcore = false, applied = true)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns cacheEntity(hardcore = true)

        val mgr = SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            activeSaveRepository = activeSaveRepository,
            saveCacheManager = cache,
            diskJudge = judge,
            secureSaves = true,
            ownerUserId = OWNER
        )
        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(
            "activeSaveApplied must load the on-disk .srm, not the cache's hardcore save",
            restoredBytes,
            result.sramData
        )
    }

    private fun managerInSlot(
        name: String,
        channelName: String?,
        activeSaveRepository: ActiveSaveRepository,
        cache: SaveCacheManager,
        secureSaves: Boolean = true
    ): Pair<SaveStateManager, File> {
        val saves = tempFolder.newFolder("$name-saves")
        val states = tempFolder.newFolder("$name-states")
        coEvery { cache.restoreThroughSigil(any()) } returns SigilRestore.NotRouted
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Duplicate(1, "h")
        coEvery { cache.probeLocalSave(any(), any(), any()) } returns SaveCacheManager.LocalSaveProbe.Absent
        val mgr = SaveStateManager(
            savesDir = saves,
            statesDir = states,
            romPath = File(saves, "Sonic Advance.gba").absolutePath,
            gameId = GAME_ID,
            activeSaveRepository = activeSaveRepository,
            saveCacheManager = cache,
            diskJudge = judge,
            secureSaves = secureSaves,
            ownerUserId = OWNER,
            channelName = channelName
        )
        return mgr to File(saves, "Sonic Advance.srm")
    }

    private fun diskHolds(cache: SaveCacheManager, primary: File, bytes: ByteArray) {
        primary.writeBytes(bytes)
        coEvery { cache.probeLocalSave(primary.absolutePath, GAME_ID, EmulatorRegistry.BUILTIN_ID) } returns
            SaveCacheManager.LocalSaveProbe.Present(DISK_HASH)
    }

    private fun builtinRow(id: Long, hash: String, hardcore: Boolean = false) = cacheEntity(hardcore).copy(
        id = id,
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        contentHash = hash,
        identityHash = hash,
        channelName = "autosave"
    )

    @Test
    fun `RESUME with a disk that matches the active version loads it and writes nothing`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, DISK_HASH)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        val (mgr, primary) = managerInSlot("disk-matches", null, activeSaveRepository, cache)
        val onDisk = byteArrayOf(1, 1, 1)
        diskHolds(cache, primary, onDisk)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(onDisk, result.sramData)
        assertArrayEquals(onDisk, primary.readBytes())
        io.mockk.coVerify(exactly = 0) { cache.cacheAsRollback(any(), any(), any()) }
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
    }

    @Test
    fun `RESUME with no save on disk writes the active version`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        val cachedBytes = byteArrayOf(4, 4)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns cachedBytes
        val (mgr, primary) = managerInSlot("disk-absent", null, activeSaveRepository, cache)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(cachedBytes, result.sramData)
        assertArrayEquals(cachedBytes, primary.readBytes())
        io.mockk.coVerify(exactly = 0) { cache.cacheAsRollback(any(), any(), any()) }
    }

    @Test
    fun `RESUME with Secure Saves off adopts a differing disk save and never overwrites it`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns byteArrayOf(4, 4)
        coEvery {
            cache.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns SaveCacheManager.CacheResult.Created(0L, 11L)
        val (mgr, primary) = managerInSlot("adopt", null, activeSaveRepository, cache, secureSaves = false)
        val handPlaced = byteArrayOf(7, 3, 7)
        diskHolds(cache, primary, handPlaced)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(handPlaced, result.sramData)
        assertArrayEquals(handPlaced, primary.readBytes())
        io.mockk.coVerify {
            cache.cacheCurrentSave(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath, null, any(), any(), any(), any(), any(), true, any(), any())
        }
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
        io.mockk.coVerify(exactly = 0) { cache.cacheAsRollback(any(), any(), any()) }
    }

    @Test
    fun `RESUME with Secure Saves on keeps a differing disk save as a rollback before writing the active version`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        val cachedBytes = byteArrayOf(4, 4)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns cachedBytes
        val (mgr, primary) = managerInSlot("rollback", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, 12L)
        diskHolds(cache, primary, byteArrayOf(7, 3, 7))

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(cachedBytes, result.sramData)
        assertArrayEquals(cachedBytes, primary.readBytes())
        io.mockk.coVerifyOrder {
            cache.cacheAsRollback(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath)
            cache.getSaveBytesFromEntity(active)
        }
    }

    @Test
    fun `RESUME never overwrites a differing disk save whose rollback failed`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns byteArrayOf(4, 4)
        val (mgr, primary) = managerInSlot("rollback-failed", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Failed
        val handPlaced = byteArrayOf(7, 3, 7)
        diskHolds(cache, primary, handPlaced)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(handPlaced, result.sramData)
        assertArrayEquals(handPlaced, primary.readBytes())
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
        io.mockk.coVerify(exactly = 0) { cache.restoreThroughSigil(any()) }
    }

    @Test
    fun `RESUME with a disk save it cannot read writes nothing over it`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        val (mgr, primary) = managerInSlot("unreadable", null, activeSaveRepository, cache)
        val onDisk = byteArrayOf(2, 2)
        primary.writeBytes(onDisk)
        coEvery { cache.probeLocalSave(any(), any(), any()) } returns SaveCacheManager.LocalSaveProbe.Unreadable

        mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(onDisk, primary.readBytes())
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
        io.mockk.coVerify(exactly = 0) { cache.cacheAsRollback(any(), any(), any()) }
    }

    @Test
    fun `RESUME with a disk matching a newer unsynced version activates it and writes nothing`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        val unsynced = builtinRow(13L, DISK_HASH).copy(cachedAt = Instant.ofEpochMilli(2000), needsRemoteSync = true)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, OWNER, "autosave", DISK_HASH) } returns listOf(unsynced)
        val (mgr, primary) = managerInSlot("progress", null, activeSaveRepository, cache)
        val onDisk = byteArrayOf(5, 6)
        diskHolds(cache, primary, onDisk)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(onDisk, result.sramData)
        io.mockk.coVerify { activeSaveRepository.activateCache(GAME_ID, 13L) }
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
        io.mockk.coVerify(exactly = 0) { cache.cacheAsRollback(any(), any(), any()) }
    }

    @Test
    fun `the most recent save fallback adopts a differing disk save with Secure Saves off`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val newest = builtinRow(10L, "cached")
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentSave(GAME_ID) } returns newest
        coEvery { cache.getSaveBytesFromEntity(newest) } returns byteArrayOf(4, 4)
        val (mgr, primary) = managerInSlot("most-recent-adopt", null, activeSaveRepository, cache, secureSaves = false)
        val handPlaced = byteArrayOf(7, 3, 7)
        diskHolds(cache, primary, handPlaced)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(handPlaced, result.sramData)
        assertArrayEquals(handPlaced, primary.readBytes())
        io.mockk.coVerify {
            cache.cacheCurrentSave(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath, null, any(), any(), any(), any(), any(), true, any(), any())
        }
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
    }

    @Test
    fun `the most recent save fallback keeps a rollback before writing with Secure Saves on`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val newest = builtinRow(10L, "cached")
        val cachedBytes = byteArrayOf(4, 4)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentSave(GAME_ID) } returns newest
        coEvery { cache.getSaveBytesFromEntity(newest) } returns cachedBytes
        val (mgr, primary) = managerInSlot("most-recent-rollback", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, 12L)
        diskHolds(cache, primary, byteArrayOf(7, 3, 7))

        mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(cachedBytes, primary.readBytes())
        io.mockk.coVerifyOrder {
            cache.cacheAsRollback(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath)
            cache.getSaveBytesFromEntity(newest)
        }
    }

    @Test
    fun `the hardcore fallback to the active save never overwrites a disk save whose rollback failed`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns null
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns byteArrayOf(4, 4)
        val (mgr, primary) = managerInSlot("hardcore-fallback-failed", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Failed
        val handPlaced = byteArrayOf(7, 3, 7)
        diskHolds(cache, primary, handPlaced)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(handPlaced, primary.readBytes())
        assertArrayEquals(handPlaced, result.sramData)
        assertTrue(result.casualSaveInHardcore)
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
    }

    @Test
    fun `the hardcore fallback to the active save keeps a rollback before writing`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val active = builtinRow(10L, "cached")
        val cachedBytes = byteArrayOf(4, 4)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns null
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns active
        coEvery { cache.getSaveBytesFromEntity(active) } returns cachedBytes
        val (mgr, primary) = managerInSlot("hardcore-fallback-rollback", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, 12L)
        diskHolds(cache, primary, byteArrayOf(7, 3, 7))

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(cachedBytes, primary.readBytes())
        assertTrue(result.casualSaveInHardcore)
        io.mockk.coVerifyOrder {
            cache.cacheAsRollback(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath)
            cache.getSaveBytesFromEntity(active)
        }
    }

    @Test
    fun `the hardcore save keeps a rollback of a differing disk before it is written`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val hardcore = builtinRow(20L, "hardcore-cached", hardcore = true)
        val hardcoreBytes = byteArrayOf(9, 9)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns hardcore
        coEvery { cache.isValidHardcoreSave(hardcore) } returns true
        coEvery { cache.getSaveBytesFromEntity(hardcore) } returns hardcoreBytes
        val (mgr, primary) = managerInSlot("hardcore-rollback", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, 12L)
        diskHolds(cache, primary, byteArrayOf(7, 3, 7))

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(hardcoreBytes, result.sramData)
        assertFalse(result.casualSaveInHardcore)
        io.mockk.coVerifyOrder {
            cache.cacheAsRollback(GAME_ID, EmulatorRegistry.BUILTIN_ID, primary.absolutePath)
            cache.getSaveBytesFromEntity(hardcore)
        }
    }

    @Test
    fun `the hardcore save never overwrites a disk save whose rollback failed`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val hardcore = builtinRow(20L, "hardcore-cached", hardcore = true)
        coEvery { cache.getLatestHardcoreSave(GAME_ID) } returns hardcore
        coEvery { cache.isValidHardcoreSave(hardcore) } returns true
        coEvery { cache.getSaveBytesFromEntity(hardcore) } returns byteArrayOf(9, 9)
        val (mgr, primary) = managerInSlot("hardcore-rollback-failed", null, activeSaveRepository, cache)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Failed
        val handPlaced = byteArrayOf(7, 3, 7)
        diskHolds(cache, primary, handPlaced)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME_HARDCORE)

        assertArrayEquals(handPlaced, primary.readBytes())
        assertTrue(result.casualSaveInHardcore)
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
    }

    @Test
    fun `RESUME into a chosen slot with no saves starts blank and clears the previous slot's save`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentInChannel(GAME_ID, "quick") } returns null
        coEvery { cache.getMostRecentSave(GAME_ID) } returns cacheEntity(hardcore = false)
        val (mgr, primary) = managerInSlot("empty-slot", "quick", activeSaveRepository, cache)
        primary.writeBytes(byteArrayOf(5, 5, 5))

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertEquals(null, result.sramData)
        assertFalse("the other slot's save must not stay on disk", primary.exists())
    }

    @Test
    fun `RESUME into a chosen slot without an active row loads that slot's latest save`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val inSlot = cacheEntity(hardcore = false)
        val slotBytes = byteArrayOf(4, 2)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentInChannel(GAME_ID, "quick") } returns inSlot
        coEvery { cache.getSaveBytesFromEntity(inSlot) } returns slotBytes
        val (mgr, _) = managerInSlot("slot-latest", "quick", activeSaveRepository, cache)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(slotBytes, result.sramData)
    }

    @Test
    fun `RESUME with no chosen slot still falls back to the most recent save`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val newest = cacheEntity(hardcore = false)
        val newestBytes = byteArrayOf(8)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { cache.getMostRecentSave(GAME_ID) } returns newest
        coEvery { cache.getSaveBytesFromEntity(newest) } returns newestBytes
        val (mgr, _) = managerInSlot("no-slot", null, activeSaveRepository, cache)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(newestBytes, result.sramData)
    }

    @Test
    fun `RESUME with an active save from another emulator loads the save on disk and never writes the cache`() = runBlocking {
        val activeSaveRepository = mockk<ActiveSaveRepository>()
        val cache = mockk<SaveCacheManager>(relaxed = true)
        val foreign = cacheEntity(hardcore = false).copy(emulatorId = "duckstation")
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns foreign
        coEvery { cache.getSaveBytesFromEntity(foreign) } returns byteArrayOf(6, 6)
        val (mgr, primary) = managerInSlot("foreign-active", null, activeSaveRepository, cache)
        val placed = byteArrayOf(3, 1, 4)
        primary.writeBytes(placed)

        val result = mgr.restoreSaveForLaunchMode(LaunchMode.RESUME)

        assertArrayEquals(placed, result.sramData)
        assertArrayEquals(placed, primary.readBytes())
        io.mockk.coVerify(exactly = 0) { cache.restoreThroughSigil(any()) }
        io.mockk.coVerify(exactly = 0) { cache.getSaveBytesFromEntity(any()) }
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
            activeSaveRepository = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            diskJudge = judge,
            secureSaves = true,
            ownerUserId = OWNER,
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
            activeSaveRepository = mockk(relaxed = true),
            saveCacheManager = mockk(relaxed = true),
            diskJudge = judge,
            secureSaves = true,
            ownerUserId = OWNER,
            channelName = null
        )
        mgr.initializeFromExistingSave(null)
        assertTrue(
            "legacy default/ state should be migrated up to the flat states dir",
            File(states, "Sonic Advance.state.auto").exists()
        )
    }

    @Test
    fun `performSlotLoad returns true when core accepts persisted state`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        val state = byteArrayOf(1, 2, 3, 4)
        mgr.getSlotFile(1).writeBytes(state)
        val retroView = mockk<GLRetroView>()
        every { retroView.unserializePersistedState(match { it.contentEquals(state) }) } returns true

        assertTrue(mgr.performSlotLoad(retroView, 1))
        verify(exactly = 1) {
            retroView.unserializePersistedState(match { it.contentEquals(state) })
        }
    }

    @Test
    fun `performSlotLoad returns false when core rejects persisted state`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        val state = byteArrayOf(5, 6, 7)
        mgr.getSlotFile(2).writeBytes(state)
        val retroView = mockk<GLRetroView>()
        every { retroView.unserializePersistedState(any()) } returns false

        assertFalse(mgr.performSlotLoad(retroView, 2))
        verify(exactly = 1) { retroView.unserializePersistedState(any()) }
    }

    @Test
    fun `performSlotLoad returns false for missing state without calling core`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        val retroView = mockk<GLRetroView>(relaxed = true)

        assertFalse(mgr.performSlotLoad(retroView, 3))
        verify(exactly = 0) { retroView.unserializePersistedState(any()) }
    }

    @Test
    fun `performSlotLoad returns false when state cannot be read`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        mgr.getSlotFile(4).mkdirs()
        val retroView = mockk<GLRetroView>(relaxed = true)

        assertFalse(mgr.performSlotLoad(retroView, 4))
        verify(exactly = 0) { retroView.unserializePersistedState(any()) }
    }

    @Test
    fun `performQuickLoad propagates core rejection`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        mgr.getSlotFile(SaveStateManager.QUICK_SLOT_BASE).writeBytes(byteArrayOf(8, 9))
        val retroView = mockk<GLRetroView>()
        every { retroView.unserializePersistedState(any()) } returns false

        assertFalse(mgr.performQuickLoad(retroView))
    }

    @Test
    fun `quick save into a full ring overwrites the oldest slot`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        fillRing(mgr)
        val oldest = SaveStateManager.QUICK_SLOT_BASE + 3
        mgr.getSlotFile(oldest).setLastModified(1_000L)

        assertTrue(mgr.performQuickSave(byteArrayOf(42)))

        val ring = mgr.getQuickRingInfoList()
        assertEquals(SaveStateManager.QUICK_RING_SIZE, ring.size)
        assertEquals(oldest, ring.first().slotNumber)
        assertArrayEquals(byteArrayOf(42), mgr.getSlotFile(oldest).readBytes())
    }

    @Test
    fun `overwriting a slot without a frame drops its old thumbnail`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        fillRing(mgr)
        val oldest = SaveStateManager.QUICK_SLOT_BASE + 5
        mgr.getSlotFile(oldest).setLastModified(1_000L)
        mgr.getSlotScreenshotFile(oldest).writeBytes(byteArrayOf(1))

        mgr.performQuickSave(byteArrayOf(7), screenshot = null)

        assertFalse(mgr.getSlotScreenshotFile(oldest).exists())
        assertEquals(null, mgr.getQuickRingInfoList().first().screenshotFile)
    }

    @Test
    fun `saveSram on a core with no RTC memory writes no rtc file and still writes SRAM`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        val sram = byteArrayOf(1, 2, 3, 4)
        val retroView = mockk<GLRetroView>()
        every { retroView.getRtcData() } returns null
        every { retroView.serializeSRAM() } returns sram

        mgr.saveSram(retroView)

        assertFalse(mgr.getRtcFile().exists())
        assertArrayEquals(sram, mgr.getSramFile().readBytes())
    }

    @Test
    fun `saveSram writes the core's RTC memory beside the SRAM`() {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        val rtc = byteArrayOf(9, 8, 7)
        val retroView = mockk<GLRetroView>()
        every { retroView.getRtcData() } returns rtc
        every { retroView.serializeSRAM() } returns byteArrayOf(1)

        mgr.saveSram(retroView)

        assertArrayEquals(rtc, mgr.getRtcFile().readBytes())
    }

    private fun autoSlotManager(saveOnDisk: ByteArray?): SaveStateManager {
        val mgr = manager(mockk(relaxed = true), mockk(relaxed = true))
        mgr.initializeFromExistingSave(saveOnDisk)
        mgr.getSlotFile(SaveStateManager.AUTO_SLOT).apply { parentFile?.mkdirs() }.writeBytes(byteArrayOf(5, 5))
        return mgr
    }

    private fun retroView(sramAfterLoad: ByteArray, bootState: ByteArray = byteArrayOf(8)): GLRetroView =
        mockk(relaxed = true) {
            every { serializeState() } returns bootState
            every { unserializePersistedState(any()) } returns true
            every { unserializeState(any()) } returns true
            every { serializeSRAM() } returns sramAfterLoad
        }

    @Test
    fun `an auto state carrying the save on disk loads`() {
        val save = byteArrayOf(1, 2, 3)
        val view = retroView(sramAfterLoad = save)

        val outcome = autoSlotManager(save).loadAutoSlotMatchingSave(view)

        assertEquals(SaveStateManager.AutoLoadOutcome.LOADED, outcome)
        verify(exactly = 0) { view.unserializeState(any()) }
    }

    @Test
    fun `an auto state carrying different save memory gives way to the save on disk`() {
        val bootState = byteArrayOf(8, 8)
        val view = retroView(sramAfterLoad = byteArrayOf(9, 9, 9), bootState = bootState)

        val outcome = autoSlotManager(byteArrayOf(1, 2, 3)).loadAutoSlotMatchingSave(view)

        assertEquals(SaveStateManager.AutoLoadOutcome.KEPT_SAVE, outcome)
        verify(exactly = 1) { view.unserializeState(bootState) }
    }

    @Test
    fun `a mismatched auto state is replaced by the save on disk and a fresh boot`() {
        val save = byteArrayOf(1, 2, 3)
        val mgr = autoSlotManager(save)
        mgr.getSramFile().apply { parentFile?.mkdirs() }.writeBytes(save)
        val view = retroView(sramAfterLoad = byteArrayOf(9, 9, 9))
        every { view.unserializeSRAM(any()) } returns true

        val outcome = mgr.loadAutoSlotMatchingSave(view)

        assertEquals(SaveStateManager.AutoLoadOutcome.KEPT_SAVE, outcome)
        verify(exactly = 1) { view.unserializeSRAM(match { it.contentEquals(save) }) }
        verify(exactly = 1) { view.reset() }
        verify(exactly = 0) { view.unserializeState(any()) }
    }

    @Test
    fun `a save the core refuses falls back to the booted game without a reset`() {
        val bootState = byteArrayOf(8, 8)
        val save = byteArrayOf(1, 2, 3)
        val mgr = autoSlotManager(save)
        mgr.getSramFile().apply { parentFile?.mkdirs() }.writeBytes(save)
        val view = retroView(sramAfterLoad = byteArrayOf(9, 9, 9), bootState = bootState)
        every { view.unserializeSRAM(any()) } returns false

        mgr.loadAutoSlotMatchingSave(view)

        verify(exactly = 0) { view.reset() }
        verify(exactly = 1) { view.unserializeState(bootState) }
    }

    @Test
    fun `with no save on disk the auto state loads without a comparison`() {
        val view = retroView(sramAfterLoad = byteArrayOf(9))

        val outcome = autoSlotManager(saveOnDisk = null).loadAutoSlotMatchingSave(view)

        assertEquals(SaveStateManager.AutoLoadOutcome.LOADED, outcome)
        verify(exactly = 0) { view.serializeState() }
        verify(exactly = 0) { view.unserializeState(any()) }
    }

    private fun refusedSaveManager(cache: SaveCacheManager, refused: ByteArray): SaveStateManager {
        val mgr = manager(mockk(relaxed = true), cache)
        mgr.getSramFile().apply { parentFile?.mkdirs() }.writeBytes(refused)
        mgr.initializeFromExistingSave(null)
        return mgr
    }

    private fun coreSram(bytes: ByteArray): GLRetroView = mockk(relaxed = true) {
        every { serializeSRAM() } returns bytes
    }

    @Test
    fun `a refused save is not overwritten before its copy exists`() {
        val refused = byteArrayOf(4, 4, 4)
        val mgr = refusedSaveManager(mockk(relaxed = true), refused)

        mgr.holdWritesForRefusedSave()
        mgr.saveSram(coreSram(byteArrayOf(0, 0, 0)))

        assertArrayEquals(refused, mgr.getSramFile().readBytes())
    }

    @Test
    fun `a refused save that could not be copied stays untouched`() = runBlocking {
        val refused = byteArrayOf(4, 4, 4)
        val cache = mockk<SaveCacheManager>(relaxed = true)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Failed
        val mgr = refusedSaveManager(cache, refused)

        mgr.holdWritesForRefusedSave()
        assertFalse(mgr.preserveUnloadedSave())
        mgr.saveSram(coreSram(byteArrayOf(0, 0, 0)))

        assertArrayEquals(refused, mgr.getSramFile().readBytes())
    }

    @Test
    fun `writes resume once the refused save is copied`() = runBlocking {
        val cache = mockk<SaveCacheManager>(relaxed = true)
        coEvery { cache.cacheAsRollback(any(), any(), any()) } returns SaveCacheManager.CacheResult.Created(0L, 1L)
        val mgr = refusedSaveManager(cache, byteArrayOf(4, 4, 4))
        val fresh = byteArrayOf(7, 7, 7)

        mgr.holdWritesForRefusedSave()
        assertTrue(mgr.preserveUnloadedSave())
        mgr.saveSram(coreSram(fresh))

        assertArrayEquals(fresh, mgr.getSramFile().readBytes())
    }

    private fun fillRing(mgr: SaveStateManager) {
        repeat(SaveStateManager.QUICK_RING_SIZE) { index ->
            val file = mgr.getSlotFile(SaveStateManager.QUICK_SLOT_BASE + index)
            file.parentFile?.mkdirs()
            file.writeBytes(byteArrayOf(index.toByte()))
            file.setLastModified(10_000L + index * 1_000L)
        }
    }

    companion object {
        private const val GAME_ID = 1L
        private const val OWNER = 3L
        private const val DISK_HASH = "disk"
    }
}
