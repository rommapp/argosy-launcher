package com.nendo.argosy.data.sync

import android.content.Context
import android.os.Environment
import com.nendo.argosy.data.emulator.RetroArchConfigParser
import com.nendo.argosy.data.emulator.RetroArchPathResolver
import com.nendo.argosy.data.emulator.TitleIdExtractor
import com.nendo.argosy.data.emulator.TitleIdResult
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
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
    private val emulatorConfigDao = mockk<EmulatorConfigDao>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val retroArchConfigParser = mockk<RetroArchConfigParser>(relaxed = true)
    private val retroArchPathResolver = mockk<RetroArchPathResolver>(relaxed = true)
    private val titleIdExtractor = mockk<TitleIdExtractor>(relaxed = true)
    private val titleDbRepository = mockk<TitleDbRepository>(relaxed = true)
    private val saveArchiver = mockk<SaveArchiver>(relaxed = true)
    private val switchSaveHandler = mockk<SwitchSaveHandler>(relaxed = true)
    private val gciSaveHandler = mockk<GciSaveHandler>(relaxed = true)
    private val saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)
    private val builtinPreferences =
        mockk<com.nendo.argosy.data.preferences.BuiltinEmulatorPreferencesRepository>(relaxed = true)
    private val platformLibretroSettingsDao =
        mockk<com.nendo.argosy.data.local.dao.PlatformLibretroSettingsDao>(relaxed = true)

    private var builtinSettings =
        com.nendo.argosy.data.preferences.BuiltinEmulatorSettings()

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
        coEvery { emulatorConfigDao.getSavePathForGame(any()) } returns null
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
            context, realFsFal(), emulatorSaveConfigDao, emulatorConfigDao, gameDao, retroArchConfigParser,
            retroArchPathResolver, titleIdExtractor, titleDbRepository, saveArchiver,
            switchSaveHandler, gciSaveHandler, saveHandlerRegistry,
            builtinPreferences, platformLibretroSettingsDao,
        )
    }

    private fun zipContaining(archive: File, entryName: String): File {
        archive.parentFile?.mkdirs()
        java.util.zip.ZipOutputStream(archive.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry(entryName))
            out.write(byteArrayOf(1, 2, 3))
            out.closeEntry()
        }
        return archive
    }

    private fun stubBuiltinSettings() {
        every { builtinPreferences.getBuiltinEmulatorSettings() } returns
            kotlinx.coroutines.flow.flowOf(builtinSettings)
        coEvery { platformLibretroSettingsDao.getByPlatformId(any()) } returns null
    }

    @Test
    fun `zipped rom save is found under the extracted entry name`() = runTest {
        stubBuiltinSettings()
        val savesDir = File(tempDir, "libretro/saves").apply { mkdirs() }
        File(savesDir, "Pokemon - Emerald Version (U).srm").writeBytes(byteArrayOf(9))
        val rom = zipContaining(
            File(tempDir, "roms/Pokemon Emerald (USA).zip"),
            "Pokemon - Emerald Version (U).gba"
        )

        val result = resolver.discoverSavePath(
            emulatorId = "builtin",
            gameTitle = "Totally Unrelated Title",
            platformSlug = "gba",
            romPath = rom.absolutePath,
            gameId = 1L,
        )

        assertEquals(File(savesDir, "Pokemon - Emerald Version (U).srm").absolutePath, result)
    }

    @Test
    fun `unzipped rom save is still found under the rom name`() = runTest {
        stubBuiltinSettings()
        val savesDir = File(tempDir, "libretro/saves").apply { mkdirs() }
        File(savesDir, "Sonic.srm").writeBytes(byteArrayOf(9))
        val rom = File(tempDir, "roms/Sonic.md").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val result = resolver.discoverSavePath(
            emulatorId = "builtin",
            gameTitle = "Totally Unrelated Title",
            platformSlug = "genesis",
            romPath = rom.absolutePath,
            gameId = 1L,
        )

        assertEquals(File(savesDir, "Sonic.srm").absolutePath, result)
    }

    @Test
    fun `builtin custom save path is searched`() = runTest {
        val customDir = File(tempDir, "custom/saves").apply { mkdirs() }
        builtinSettings = com.nendo.argosy.data.preferences.BuiltinEmulatorSettings(
            customSavePath = customDir.absolutePath
        )
        stubBuiltinSettings()
        File(customDir, "Zelda.srm").writeBytes(byteArrayOf(9))
        val rom = File(tempDir, "roms/Zelda.gba").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val result = resolver.discoverSavePath(
            emulatorId = "builtin",
            gameTitle = "Totally Unrelated Title",
            platformSlug = "gba",
            romPath = rom.absolutePath,
            gameId = 1L,
        )

        assertEquals(File(customDir, "Zelda.srm").absolutePath, result)
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

    @Test
    fun `constructSavePath names the flat save after the ROM base name not the game title`() = runTest {
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda (USA).gba").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.constructSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath,
        )

        assertEquals("Zelda (USA).srm", result?.let { File(it).name })
    }

    @Test
    fun `per-game path yields identical discover and construct results for a file-based config`() = runTest {
        val perGameDir = File(tempDir, "custom/gba-saves").apply { mkdirs() }
        coEvery { emulatorConfigDao.getSavePathForGame(1L) } returns perGameDir.absolutePath
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda (USA).gba").apply { writeBytes(byteArrayOf(0)) }

        val constructed = resolver.constructSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(File(perGameDir, "Zelda (USA).srm").absolutePath, constructed)

        File(constructed!!).writeBytes(byteArrayOf(1))
        val discovered = resolver.discoverSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(constructed, discovered)
    }

    @Test
    fun `per-game path beats the per-emulator user override in both discover and construct`() = runTest {
        val perGameDir = File(tempDir, "custom/per-game").apply { mkdirs() }
        val overrideDir = File(tempDir, "custom/emulator-override").apply { mkdirs() }
        coEvery { emulatorConfigDao.getSavePathForGame(1L) } returns perGameDir.absolutePath
        coEvery { emulatorSaveConfigDao.getByEmulator("builtin") } returns
            EmulatorSaveConfigEntity(
                emulatorId = "builtin", savePathPattern = overrideDir.absolutePath,
                isAutoDetected = false, isUserOverride = true,
            )
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }
        val perGameSave = File(perGameDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }
        File(overrideDir, "Zelda.srm").writeBytes(byteArrayOf(2))

        val discovered = resolver.discoverSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )
        val constructed = resolver.constructSavePath(
            emulatorId = "builtin", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(perGameSave.absolutePath, discovered)
        assertEquals(perGameSave.absolutePath, constructed)
    }

    @Test
    fun `per-game path is ignored for a folder-based config`() = runTest {
        val perGameDir = File(tempDir, "custom/per-game").apply { mkdirs() }
        coEvery { emulatorConfigDao.getSavePathForGame(1L) } returns perGameDir.absolutePath
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = "/roms/botw.nsp", cachedSaveId = titleId, emulatorPackage = "dev.eden.eden_emulator",
            gameId = 1L,
        )

        assertEquals("/path/found/$titleId", result)
    }

    private fun rommGame(): GameEntity = GameEntity(
        id = 1L, platformId = 10L, platformSlug = "switch",
        title = "BOTW", sortTitle = "botw",
        localPath = "/roms/botw.nsp", rommId = 100L,
        igdbId = null, source = GameSource.ROMM_SYNCED,
    )
}
