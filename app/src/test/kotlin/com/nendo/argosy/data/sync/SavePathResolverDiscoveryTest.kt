package com.nendo.argosy.data.sync

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.TitleIdExtractor
import com.nendo.argosy.data.emulator.TitleIdResult
import com.nendo.argosy.data.local.dao.EmulatorSaveConfigDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SwitchSaveHandler
import com.nendo.argosy.data.titledb.TitleDbRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class SavePathResolverDiscoveryTest {

    private lateinit var tempDir: File
    private lateinit var resolver: SavePathResolver

    private val context = mockk<Context>(relaxed = true)
    private val emulatorSaveConfigDao = mockk<EmulatorSaveConfigDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val retroArchConfigParser = mockk<RetroArchConfigParser>(relaxed = true)
    private val retroArchPathResolver = mockk<RetroArchPathResolver>(relaxed = true)
    private val titleIdExtractor = mockk<TitleIdExtractor>(relaxed = true)
    private val titleDbRepository = mockk<TitleDbRepository>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val switchSaveHandler = mockk<SwitchSaveHandler>(relaxed = true)
    private val gciSaveHandler = mockk<GciSaveHandler>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)

    private val titleId = "01007EF00011E000"
    private lateinit var basePath: File

    @Before
    fun setUp() {
        tempDir = createTempDirectory("save_path_resolver").toFile()
        mockkStatic(Environment::class)
        every { Environment.getExternalStorageDirectory() } returns tempDir
        basePath = File(tempDir, "Android/data/dev.eden.eden_emulator/files/nand/user/save").apply { mkdirs() }
        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        coEvery { emulatorSaveConfigDao.getByEmulator(any()) } returns null
        every { switchSaveHandler.isValidTitleId(any()) } answers {
            val s = firstArg<String>()
            s.length == 16 && s.uppercase().startsWith("01") && s.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
        }
        every { switchSaveHandler.findSaveFolderBySaveId(any(), any()) } returns null
        coEvery { titleDbRepository.getCachedCandidates(any()) } returns emptyList()
        coEvery { titleDbRepository.resolveTitleIdCandidates(any(), any(), any()) } returns emptyList()
        every { titleIdExtractor.extractTitleIdWithSource(any(), any(), any()) } returns null
        every { saveHandlerRegistry.getFolderHandler(any()) } returns null

        resolver = SavePathResolver(
            context, realFsFal(), emulatorSaveConfigDao, gameDao, retroArchConfigParser,
            retroArchPathResolver, titleIdExtractor, titleDbRepository, saveArchiver,
            switchSaveHandler, gciSaveHandler, saveHandlerRegistry,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Environment::class)
        tempDir.deleteRecursively()
    }

    @Test
    fun `cached titleId hit short-circuits before extraction`() = runTest {
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = "/roms/botw.nsp", cachedSaveId = titleId, emulatorPackage = "dev.eden.eden_emulator",
            gameId = 1L,
        )

        assertEquals("/path/found/$titleId", result)
        coVerify(exactly = 0) { titleIdExtractor.extractTitleIdWithSource(any(), any(), any()) }
    }

    @Test
    fun `invalid cached titleId is cleared and falls through to extraction`() = runTest {
        val invalidCached = "FF007EF00011E000"
        every { titleIdExtractor.extractTitleIdWithSource(any(), "switch", any()) } returns
            TitleIdResult(titleId = titleId, fromBinary = true)
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"
        val romFile = File(tempDir, "botw.nsp").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = romFile.absolutePath, cachedSaveId = invalidCached,
            emulatorPackage = "dev.eden.eden_emulator", gameId = 1L,
        )

        assertEquals("/path/found/$titleId", result)
        coVerify(exactly = 1) { gameDao.updateTitleId(1L, null) }
        coVerify(exactly = 1) { titleIdExtractor.extractTitleIdWithSource(any(), any(), any()) }
    }

    @Test
    fun `extraction success caches the titleId when no cached id existed`() = runTest {
        every { titleIdExtractor.extractTitleIdWithSource(any(), "switch", any()) } returns
            TitleIdResult(titleId = titleId, fromBinary = true)
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"
        val romFile = File(tempDir, "botw.nsp").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = romFile.absolutePath, cachedSaveId = null,
            emulatorPackage = "dev.eden.eden_emulator", gameId = 1L,
        )

        assertEquals("/path/found/$titleId", result)
        coVerify(exactly = 1) {
            gameDao.setTitleAndSaveIdWithLock(1L, titleId, titleId, true)
        }
    }

    @Test
    fun `extracted invalid switch titleId (does not start with 01) is not cached`() = runTest {
        val invalidExtracted = "FF007EF00011E000"
        every { titleIdExtractor.extractTitleIdWithSource(any(), "switch", any()) } returns
            TitleIdResult(titleId = invalidExtracted, fromBinary = true)
        val romFile = File(tempDir, "botw.nsp").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = romFile.absolutePath, cachedSaveId = null,
            emulatorPackage = "dev.eden.eden_emulator", gameId = 1L,
        )

        assertNull(result)
        coVerify(exactly = 0) {
            gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any())
        }
    }

    @Test
    fun `titleDb candidate fallback caches the winning titleId on best match`() = runTest {
        val candidateA = "0100ABC000DEF000"
        val candidateB = titleId
        every { titleIdExtractor.extractTitleIdWithSource(any(), "switch", any()) } returns null
        coEvery { titleDbRepository.getCachedCandidates(1L) } returns listOf(candidateA, candidateB)

        val baseUserDir = File(tempDir, "Android/data/dev.eden.eden_emulator/files/nand/user/save").apply { mkdirs() }
        val matchedA = File(baseUserDir, "0000000000000000/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$candidateA").apply { mkdirs() }
        File(matchedA, "older.bin").apply {
            writeBytes(byteArrayOf(1))
            setLastModified(1_000_000L)
        }
        val matchedB = File(baseUserDir, "0000000000000000/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$candidateB").apply { mkdirs() }
        File(matchedB, "newer.bin").apply {
            writeBytes(byteArrayOf(2))
            setLastModified(System.currentTimeMillis())
        }
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), candidateA)
        } returns matchedA.absolutePath
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), candidateB)
        } returns matchedB.absolutePath
        val romFile = File(tempDir, "botw.nsp").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = romFile.absolutePath, cachedSaveId = null,
            emulatorPackage = "dev.eden.eden_emulator", gameId = 1L,
        )

        assertEquals("Newer candidate must win", matchedB.absolutePath, result)
        coVerify(exactly = 1) { gameDao.updateTitleId(1L, candidateB) }
    }

    @Test
    fun `returns null when nothing in the cascade matches`() = runTest {
        every { titleIdExtractor.extractTitleIdWithSource(any(), "switch", any()) } returns null
        coEvery { titleDbRepository.getCachedCandidates(1L) } returns emptyList()
        val romFile = File(tempDir, "botw.nsp").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = romFile.absolutePath, cachedSaveId = null,
            emulatorPackage = "dev.eden.eden_emulator", gameId = 1L,
        )

        assertNull(result)
    }

    @Test
    fun `savesBesideRom discovers the save in the ROM folder`() = runTest {
        coEvery { emulatorSaveConfigDao.getByEmulator("builtin") } returns
            EmulatorSaveConfigEntity(emulatorId = "builtin", savePathPattern = "", isAutoDetected = true, savesBesideRom = true)
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }
        val saveFile = File(romDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }

        val result = resolver.discoverSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(saveFile.absolutePath, result)
    }

    @Test
    fun `savesBesideRom constructs the restore target beside the ROM with the ROM name`() = runTest {
        coEvery { emulatorSaveConfigDao.getByEmulator("builtin") } returns
            EmulatorSaveConfigEntity(emulatorId = "builtin", savePathPattern = "", isAutoDetected = true, savesBesideRom = true)
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.constructSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath,
        )

        assertEquals(File(romDir, "Zelda.srm").absolutePath, result)
    }

    private fun rommGame(): GameEntity = GameEntity(
        id = 1L, platformId = 10L, platformSlug = "switch",
        title = "BOTW", sortTitle = "botw",
        localPath = "/roms/botw.nsp", rommId = 100L,
        igdbId = null, source = GameSource.ROMM_SYNCED,
    )
}
