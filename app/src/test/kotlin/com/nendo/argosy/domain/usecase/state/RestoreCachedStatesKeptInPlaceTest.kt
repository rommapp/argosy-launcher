package com.nendo.argosy.domain.usecase.state

import com.nendo.argosy.data.emulator.ArchiveRomNaming
import com.nendo.argosy.data.emulator.EmulatorDetector
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LibretroStatePathResolver
import com.nendo.argosy.libretro.LibretroStateSlots
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.StateCacheEntity
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.StateClaim
import com.nendo.argosy.data.sync.StateOwnershipTracker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RestoreCachedStatesKeptInPlaceTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `a live state that could not be moved aside is never overwritten by the restore`() = runTest {
        val liveDir = temp.newFolder("live")
        val romFile = File("/roms/snes/Game.sfc")
        val stateName = LibretroStateSlots.fileName(ArchiveRomNaming.liveBaseName(romFile, "snes"), 1)
        val live = File(liveDir, stateName).apply { writeText("live progress") }
        val cached = File(temp.newFolder("cache"), stateName).apply { writeText("older cache") }

        val game = mockk<GameEntity>(relaxed = true) {
            every { localPath } returns romFile.absolutePath
            every { platformSlug } returns "snes"
        }
        val state = mockk<StateCacheEntity>(relaxed = true) { every { slotNumber } returns 1 }
        val gameDao = mockk<GameDao> { coEvery { getById(1L) } returns game }
        val detector = mockk<EmulatorDetector> {
            every { getByPackage(EmulatorRegistry.BUILTIN_PACKAGE) } returns EmulatorRegistry.getById(EmulatorRegistry.BUILTIN_ID)
        }
        val stateCacheManager = mockk<StateCacheManager>(relaxed = true) {
            coEvery { getStatesForChannelAndCore(1L, null, any()) } returns listOf(state)
            every { getCacheFile(state) } returns cached
            every { displaceLiveState(any(), any()) } returns false
        }
        val tracker = mockk<StateOwnershipTracker>(relaxed = true) {
            coEvery { claim(any(), any()) } returns StateClaim.Mine
        }
        val pathResolver = mockk<LibretroStatePathResolver> {
            coEvery { liveStateBaseDir(1L) } returns liveDir
        }

        val useCase = RestoreCachedStatesUseCase(
            stateCacheManager = stateCacheManager,
            gameDao = gameDao,
            emulatorSaveConfigDao = mockk(relaxed = true),
            emulatorDetector = detector,
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = pathResolver,
            stateOwnershipTracker = tracker,
            stateSerialResolver = mockk { coEvery { serialsFor(any(), any()) } returns emptySet() },
            fileAccessLayer = mockk(relaxed = true)
        )

        useCase(gameId = 1L, channelName = null, emulatorPackage = EmulatorRegistry.BUILTIN_PACKAGE, coreId = "snes9x")

        assertEquals("live progress", live.readText())
    }

    @Test
    fun `restore never displaces or writes another game's duckstation state`() = runTest {
        val liveDir = temp.newFolder("savestates")
        val cacheDir = temp.newFolder("duck-cache")
        val ownLive = File(liveDir, "SLUS-00001_1.sav").apply { writeText("own live") }
        val foreignLive = File(liveDir, "SCUS-94163_1.sav").apply { writeText("other game") }
        val ownCached = File(cacheDir, "SLUS-00001_1.sav").apply { writeText("own cached") }
        val foreignCached = File(cacheDir, "SCUS-94163_2.sav").apply { writeText("stale foreign copy") }
        val duckstation = EmulatorRegistry.getById("duckstation")!!

        val game = mockk<GameEntity>(relaxed = true) {
            every { localPath } returns "/roms/psx/Game (USA).chd"
            every { platformSlug } returns "psx"
        }
        val ownState = mockk<StateCacheEntity>(relaxed = true) { every { slotNumber } returns 1 }
        val foreignState = mockk<StateCacheEntity>(relaxed = true) { every { slotNumber } returns 2 }
        val stateCacheManager = mockk<StateCacheManager>(relaxed = true) {
            coEvery { getStatesForChannelAndCore(1L, null, any()) } returns listOf(ownState, foreignState)
            every { getCacheFile(ownState) } returns ownCached
            every { getCacheFile(foreignState) } returns foreignCached
            every { displaceLiveState(any(), any()) } returns true
        }
        val saveConfigDao = mockk<com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao> {
            coEvery { getByEmulator("duckstation") } returns com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity(
                emulatorId = "duckstation",
                savePathPattern = "",
                isAutoDetected = false,
                statePathPattern = liveDir.absolutePath,
                isUserStateOverride = true
            )
        }

        val useCase = RestoreCachedStatesUseCase(
            stateCacheManager = stateCacheManager,
            gameDao = mockk { coEvery { getById(1L) } returns game },
            emulatorSaveConfigDao = saveConfigDao,
            emulatorDetector = mockk { every { getByPackage(duckstation.packageName) } returns duckstation },
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = mockk(relaxed = true),
            stateOwnershipTracker = mockk(relaxed = true) { coEvery { claim(any(), any()) } returns StateClaim.Mine },
            stateSerialResolver = mockk { coEvery { serialsFor(1L, "duckstation") } returns setOf("SLUS-00001") },
            fileAccessLayer = mockk(relaxed = true)
        )

        useCase(gameId = 1L, channelName = null, emulatorPackage = duckstation.packageName)

        io.mockk.verify(exactly = 1) { stateCacheManager.displaceLiveState(ownLive, "duckstation") }
        io.mockk.verify(exactly = 0) { stateCacheManager.displaceLiveState(foreignLive, any()) }
        io.mockk.coVerify { stateCacheManager.detachForeignSerialStates(1L, setOf("SLUS-00001")) }
        assertEquals("other game", foreignLive.readText())
        assertEquals("own cached", ownLive.readText())
        assertEquals(false, File(liveDir, "SCUS-94163_2.sav").exists())
        assertEquals(false, File(liveDir, "SLUS-00001_2.sav").exists())
    }
}
