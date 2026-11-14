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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    private val saveUnitResolver = mockk<SaveUnitResolver>(relaxed = true)
    private val duckStationCardFinder = mockk<DuckStationCardFinder>(relaxed = true)
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
        coEvery { saveHandlerRegistry.sigil.sharedLibretroRoot(any(), any()) } returns null
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
        coEvery { saveUnitResolver.resolve(any(), any(), any(), any(), any(), any()) } returns null

        val saveConfigRepository = com.nendo.argosy.data.repository.EmulatorSaveConfigRepository(emulatorSaveConfigDao)
        val libretroSavePaths = com.nendo.argosy.data.emulator.LibretroSavePathResolver(
            context, platformLibretroSettingsDao, builtinPreferences
        )
        resolver = SavePathResolver(
            context, realFsFal(), emulatorSaveConfigDao,
            saveConfigRepository,
            emulatorConfigDao, gameDao, retroArchConfigParser,
            retroArchPathResolver, titleIdExtractor, titleDbRepository, saveArchiver,
            switchSaveHandler, gciSaveHandler, saveHandlerRegistry,
            libretroSavePaths,
            saveUnitResolver,
            com.nendo.argosy.data.emulator.BuiltinSaveBase(libretroSavePaths, saveConfigRepository, emulatorConfigDao),
            duckStationCardFinder,
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
    fun `a Switch save with nothing on disk is placed in the emulator's title folder, not a literal package path`() = runTest {
        stubBuiltinSettings()
        val pkg = "dev.eden.eden_emulator"
        val expected = "${basePath.absolutePath}/0000000000000000/PROFILE/$titleId"
        every { switchSaveHandler.resolveBasePath(any(), null, pkg) } returns basePath.absolutePath
        every { switchSaveHandler.constructSavePath(basePath.absolutePath, titleId, pkg) } returns expected

        val path = resolver.constructSavePath(
            emulatorId = "eden", gameTitle = "Portal", platformSlug = "switch", romPath = null,
            cachedSaveId = titleId, gameId = 5L, emulatorPackage = pkg
        )

        assertEquals(expected, path)
    }

    private val duckStation = "com.github.stenzek.duckstation"

    private fun duckStationCards(): File = File(tempDir, "Android/data/$duckStation/files/memcards").apply { mkdirs() }

    private fun psxRom(name: String): File =
        File(tempDir, "roms/$name.chd").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(0)) }

    private suspend fun discoverWithRoot(gameTitle: String, gameId: Long): String? {
        io.mockk.mockkObject(com.nendo.argosy.util.RootShell)
        try {
            every { com.nendo.argosy.util.RootShell.isAvailable } returns true
            return resolver.discoverSavePath(
                emulatorId = "duckstation", gameTitle = gameTitle, platformSlug = "psx",
                romPath = psxRom(gameTitle).absolutePath, gameId = gameId, emulatorPackage = duckStation
            )
        } finally {
            io.mockk.unmockkObject(com.nendo.argosy.util.RootShell)
        }
    }

    @Test
    fun `a DuckStation card named after the database title is found by the serial its saves carry`() = runTest {
        stubBuiltinSettings()
        val card = File(duckStationCards(), "Crash Team Racing (USA)_1.mcd").apply { writeBytes(ByteArray(16)) }
        coEvery { duckStationCardFinder.ownedCard(9L, any()) } returns card.absolutePath

        assertEquals(card.absolutePath, discoverWithRoot("CTR - Crash Team Racing", 9L))
    }

    @Test
    fun `a DuckStation game never takes another game's card by a title substring`() = runTest {
        stubBuiltinSettings()
        File(duckStationCards(), "Digimon World 2 (USA)_1.mcd").writeBytes(ByteArray(16))
        coEvery { duckStationCardFinder.ownedCard(any(), any()) } returns null

        assertEquals(null, discoverWithRoot("Digimon World", 10L))
    }

    @Test
    fun `a package-template folder save with nothing on disk resolves the package in its base folder`() = runTest {
        stubBuiltinSettings()
        val pkg = "xyz.aethersx2.android"
        val cardDir = File(tempDir, "Android/data/$pkg/files/memcards").apply { mkdirs() }
        val folderHandler = mockk<com.nendo.argosy.data.sync.platform.FolderSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.getFolderHandler("ps2") } returns folderHandler
        every { folderHandler.constructSavePath(any(), any()) } answers { "${firstArg<String>()}/${secondArg<String>()}" }

        val path = resolver.constructSavePath(
            emulatorId = "nethersx2", gameTitle = "Norrath", platformSlug = "ps2", romPath = null,
            cachedSaveId = "SLUS-20642", gameId = 6L, emulatorPackage = pkg
        )

        assertEquals("${cardDir.absolutePath}/SLUS-20642", path)
    }

    @Test
    fun `a GameCube save with nothing on disk is placed in the region card folder Dolphin reads`() = runTest {
        stubBuiltinSettings()
        val rom = File(tempDir, "roms/Zelda.iso").apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(0)) }
        every { gciSaveHandler.parseRomHeader(rom.absolutePath) } returns
            com.nendo.argosy.data.emulator.GameCubeGameInfo(gameId = "GZLE", makerCode = "01", region = "USA", gameName = null)

        val path = resolver.constructSavePath(
            emulatorId = "dolphin", gameTitle = "Zelda", platformSlug = "ngc", romPath = rom.absolutePath, gameId = 1L
        )

        assertNotNull(path)
        assertTrue("Expected a USA/Card A target, got $path", path!!.endsWith("/USA/Card A/01-GZLE.gci"))
    }

    @Test
    fun `built-in ARMSX2 looks for its unit under the memory card root its Sigil route uses`() = runTest {
        stubBuiltinSettings()
        val memcards = File(tempDir, "libretro/system/pcsx2/memcards").apply { mkdirs() }
        val ownCard = File(memcards, "BASLUS-20642.ps2").apply { writeBytes(byteArrayOf(5)) }
        val rom = File(tempDir, "roms/Champions of Norrath (USA).chd").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val game = GameEntity(
            id = 1L, title = "Champions of Norrath", sortTitle = "champions of norrath", platformId = 2L,
            platformSlug = "ps2", rommId = 7L, igdbId = null, localPath = rom.absolutePath,
            source = GameSource.ROMM_SYNCED
        )
        coEvery { gameDao.getById(1L) } returns game
        val sigil = mockk<com.nendo.argosy.data.sync.platform.SigilSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.sigil } returns sigil
        coEvery { sigil.route(any(), any()) } returns null
        coEvery { sigil.sharedLibretroRoot(any(), any()) } returns null
        coEvery { sigil.localRoute(1L, "argosy") } returns
            com.nendo.argosy.data.sync.platform.SigilRoute("pcsx2", memcards.absolutePath, null, null, libretro = true)
        val unit = com.nendo.sigil.SigilSaveUnit(
            "key", com.nendo.sigil.SigilSaveUnit.Shape.Single.code,
            listOf(com.nendo.sigil.SigilSaveMember("BASLUS-20642.ps2", "BASLUS-20642.ps2", 0, true)),
            emptyList(), emptyList(), "BASLUS-20642.ps2", "", ""
        )
        coEvery {
            saveUnitResolver.resolve(memcards.absolutePath, "armsx2", "ps2", rom.name, game, false)
        } returns ResolvedSaveUnit(memcards.absolutePath, "pcsx2", unit)

        val result = resolver.discoverSavePath(
            emulatorId = "argosy",
            gameTitle = game.title,
            platformSlug = "ps2",
            romPath = rom.absolutePath,
            coreName = "armsx2",
            gameId = 1L,
        )

        assertEquals(ownCard.absolutePath, result)
        coVerify(exactly = 1) { saveUnitResolver.resolve(memcards.absolutePath, "armsx2", "ps2", rom.name, game, false) }
    }

    @Test
    fun `a built-in game whose saves sit on shared cards resolves to the card root, not a stale rom-named save`() = runTest {
        stubBuiltinSettings()
        val savesDir = File(tempDir, "libretro/saves").apply { mkdirs() }
        File(savesDir, "Champions of Norrath (USA).srm").writeBytes(ByteArray(64) { 4 })
        val memcards = File(tempDir, "libretro/system/pcsx2/memcards").apply { mkdirs() }
        File(memcards, "Mcd001.ps2").writeBytes(ByteArray(64) { 5 })
        coEvery { saveHandlerRegistry.sigil.sharedLibretroRoot(1L, "argosy") } returns memcards.absolutePath

        val result = resolver.discoverSavePath(
            emulatorId = "argosy",
            gameTitle = "Champions of Norrath",
            platformSlug = "ps2",
            romPath = "/roms/Champions of Norrath (USA).chd",
            coreName = "armsx2",
            gameId = 1L,
        )

        assertEquals(memcards.absolutePath, result)
    }

    @Test
    fun `a standalone emulator never asks for a shared libretro root`() = runTest {
        val sigil = saveHandlerRegistry.sigil
        resolver.discoverSavePath(
            emulatorId = "nethersx2",
            gameTitle = "Champions of Norrath",
            platformSlug = "ps2",
            romPath = "/roms/Champions of Norrath (USA).chd",
            gameId = 1L,
        )

        coVerify(exactly = 0) { sigil.sharedLibretroRoot(any(), any()) }
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
            emulatorId = "argosy",
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
            emulatorId = "argosy",
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
            emulatorId = "argosy",
            gameTitle = "Totally Unrelated Title",
            platformSlug = "gba",
            romPath = rom.absolutePath,
            gameId = 1L,
        )

        assertEquals(File(customDir, "Zelda.srm").absolutePath, result)
    }

    @Test
    fun `builtin global custom path is the construct target as well`() = runTest {
        val customDir = File(tempDir, "custom/saves").apply { mkdirs() }
        builtinSettings = com.nendo.argosy.data.preferences.BuiltinEmulatorSettings(
            customSavePath = customDir.absolutePath
        )
        stubBuiltinSettings()
        val rom = File(tempDir, "roms/Zelda.gba").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val result = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = rom.absolutePath, gameId = 1L,
        )

        assertEquals(File(customDir, "Zelda.srm").absolutePath, result)
    }

    /**
     * The launcher gives a per-platform libretro path precedence over the global custom path, so
     * discovery and the download target have to land in the same place or a restore is written
     * where the core never reads.
     */
    @Test
    fun `builtin per-platform path beats the global custom path for discover and construct`() = runTest {
        val customDir = File(tempDir, "custom/saves").apply { mkdirs() }
        val platformDir = File(tempDir, "custom/gba-only").apply { mkdirs() }
        builtinSettings = com.nendo.argosy.data.preferences.BuiltinEmulatorSettings(
            customSavePath = customDir.absolutePath
        )
        stubBuiltinSettings()
        coEvery { gameDao.getById(1L) } returns rommGame().copy(platformId = 10L, platformSlug = "gba")
        coEvery { platformLibretroSettingsDao.getByPlatformId(10L) } returns
            com.nendo.argosy.data.local.entity.PlatformLibretroSettingsEntity(
                platformId = 10L, savePath = platformDir.absolutePath
            )
        File(customDir, "Zelda.srm").writeBytes(byteArrayOf(2))
        val platformSave = File(platformDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }
        val rom = File(tempDir, "roms/Zelda.gba").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val discovered = resolver.discoverSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = rom.absolutePath, gameId = 1L,
        )
        val constructed = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = rom.absolutePath, gameId = 1L,
        )

        assertEquals(platformSave.absolutePath, discovered)
        assertEquals(platformSave.absolutePath, constructed)
    }

    /**
     * The Save Path modal used to write a `builtin` row the launcher never read, and discovery
     * then searched only that row. The row is inert now: the live directory decides.
     */
    @Test
    fun `a builtin override row is ignored in favour of the live save directory`() = runTest {
        stubBuiltinSettings()
        val staleDir = File(tempDir, "Documents/saves").apply { mkdirs() }
        coEvery { emulatorSaveConfigDao.getByEmulator("argosy") } returns
            EmulatorSaveConfigEntity(
                emulatorId = "argosy", savePathPattern = staleDir.absolutePath,
                isAutoDetected = false, isUserOverride = true,
            )
        val savesDir = File(tempDir, "libretro/saves").apply { mkdirs() }
        val liveSave = File(savesDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }
        val rom = File(tempDir, "roms/Zelda.gba").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }

        val discovered = resolver.discoverSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = rom.absolutePath, gameId = 1L,
        )
        val constructed = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = rom.absolutePath, gameId = 1L,
        )

        assertEquals(liveSave.absolutePath, discovered)
        assertEquals(liveSave.absolutePath, constructed)
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
    fun `a cached patch id is searched as its application and the stored row is rebased`() = runTest {
        val patchId = "01007EF00011E800"
        coEvery { gameDao.getById(1L) } returns
            rommGame().copy(titleId = patchId, saveId = patchId, titleIdLocked = true)
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = "/roms/botw.nsp", cachedSaveId = patchId, emulatorPackage = "dev.eden.eden_emulator",
            gameId = 1L,
        )

        assertEquals("/path/found/$titleId", result)
        coVerify(exactly = 1) { gameDao.setTitleAndSaveIdWithLock(1L, titleId, titleId, true) }
        coVerify(exactly = 0) { gameDao.updateTitleId(any(), any()) }
    }

    @Test
    fun `a cached application id leaves the stored row alone`() = runTest {
        coEvery { gameDao.getById(1L) } returns
            rommGame().copy(titleId = titleId, saveId = titleId, titleIdLocked = true)
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns "/path/found/$titleId"

        resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = "/roms/botw.nsp", cachedSaveId = titleId, emulatorPackage = "dev.eden.eden_emulator",
            gameId = 1L,
        )

        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `cached titledb candidates are searched as their application ids`() = runTest {
        val dlcCandidate = "01007EF00011F002"
        coEvery { titleDbRepository.getCachedCandidates(1L) } returns listOf(dlcCandidate)
        val matched = File(basePath, "0000000000000000/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$titleId").apply { mkdirs() }
        every {
            switchSaveHandler.findSaveFolderBySaveId(any(), titleId)
        } returns matched.absolutePath

        val result = resolver.discoverSavePath(
            emulatorId = "eden", gameTitle = "BOTW", platformSlug = "switch",
            romPath = "/roms/botw.nsp", cachedSaveId = null, emulatorPackage = "dev.eden.eden_emulator",
            gameId = 1L,
        )

        assertEquals(matched.absolutePath, result)
        coVerify(exactly = 1) { gameDao.updateTitleId(1L, titleId) }
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
        val candidateA = "0100ABC000DEE000"
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
    fun `SeedlessDS saves are found beside the ROM without a user setting`() = runTest {
        val romDir = File(tempDir, "roms/nds").apply { mkdirs() }
        val romFile = File(romDir, "Mario Kart DS.nds").apply { writeBytes(byteArrayOf(0)) }
        val saveFile = File(romDir, "Mario Kart DS.dsv").apply { writeBytes(byteArrayOf(1)) }

        val result = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = romFile.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 1L,
        )

        assertEquals(saveFile.absolutePath, result)
    }

    @Test
    fun `a SeedlessDS download is written beside the ROM`() = runTest {
        val romDir = File(tempDir, "roms/nds").apply { mkdirs() }
        val romFile = File(romDir, "Mario Kart DS.nds").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.constructSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(File(romDir, "Mario Kart DS.dsv").absolutePath, result)
    }

    private fun seedlessFolder(path: String, userChosen: Boolean) {
        coEvery { emulatorSaveConfigDao.getByEmulator("seedlessds") } returns
            com.nendo.argosy.data.local.entity.EmulatorSaveConfigEntity(
                emulatorId = "seedlessds",
                savePathPattern = path,
                isAutoDetected = !userChosen,
                isUserOverride = userChosen
            )
    }

    @Test
    fun `a SeedlessDS custom save folder the user chose is searched instead of beside the ROM`() = runTest {
        val romDir = File(tempDir, "Games/nds").apply { mkdirs() }
        val romFile = File(romDir, "Pokemon HeartGold (USA).nds").apply { writeBytes(byteArrayOf(0)) }
        File(romDir, "Pokemon HeartGold (USA).dsv").writeBytes(byteArrayOf(9))
        val custom = File(tempDir, "Saves/DS").apply { mkdirs() }
        val saveFile = File(custom, "Pokemon HeartGold (USA).dsv").apply { writeBytes(byteArrayOf(1)) }
        seedlessFolder(custom.absolutePath, userChosen = true)

        val found = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Pokemon HeartGold", platformSlug = "nds",
            romPath = romFile.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 1L,
        )
        val target = resolver.constructSavePath(
            emulatorId = "seedlessds", gameTitle = "Pokemon HeartGold", platformSlug = "nds",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(saveFile.absolutePath, found)
        assertEquals(saveFile.absolutePath, target)
    }

    @Test
    fun `an evaluated SeedlessDS folder never pulls saves away from beside the ROM`() = runTest {
        val romDir = File(tempDir, "Games/nds").apply { mkdirs() }
        val romFile = File(romDir, "Pokemon HeartGold (USA).nds").apply { writeBytes(byteArrayOf(0)) }
        val besideRom = File(romDir, "Pokemon HeartGold (USA).dsv").apply { writeBytes(byteArrayOf(1)) }
        val elsewhere = File(tempDir, "Saves/DS").apply { mkdirs() }
        File(elsewhere, "Pokemon HeartGold (USA).dsv").writeBytes(byteArrayOf(9))
        seedlessFolder(elsewhere.absolutePath, userChosen = false)

        val found = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Pokemon HeartGold", platformSlug = "nds",
            romPath = romFile.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 1L,
        )

        assertEquals(besideRom.absolutePath, found)
    }

    @Test
    fun `two SeedlessDS regional copies in one folder resolve only their own save`() = runTest {
        val romDir = File(tempDir, "roms/nds").apply { mkdirs() }
        val usaRom = File(romDir, "Mario Kart DS (USA).nds").apply { writeBytes(byteArrayOf(0)) }
        val europeRom = File(romDir, "Mario Kart DS (Europe).nds").apply { writeBytes(byteArrayOf(0)) }
        val usaSave = File(romDir, "Mario Kart DS (USA).dsv").apply { writeBytes(byteArrayOf(1)) }

        val usa = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = usaRom.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 1L,
        )
        val europeBeforeItsOwnSave = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = europeRom.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 2L,
        )
        val europeSave = File(romDir, "Mario Kart DS (Europe).dsv").apply { writeBytes(byteArrayOf(2)) }
        val europe = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = europeRom.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 2L,
        )

        assertEquals(usaSave.absolutePath, usa)
        assertNull(europeBeforeItsOwnSave)
        assertEquals(europeSave.absolutePath, europe)
    }

    @Test
    fun `a rom folder never matches a save by title`() = runTest {
        val romDir = File(tempDir, "roms/nds").apply { mkdirs() }
        val europeRom = File(romDir, "Mario Kart DS (Europe).nds").apply { writeBytes(byteArrayOf(0)) }
        File(romDir, "Mario Kart DS.dsv").writeBytes(byteArrayOf(1))

        val result = resolver.discoverSavePath(
            emulatorId = "seedlessds", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = europeRom.absolutePath, emulatorPackage = "com.seedlessds.app", gameId = 2L,
        )

        assertNull(result)
    }

    @Test
    fun `a per-game folder another copy also names never matches a save by title`() = runTest {
        val sharedDir = File(tempDir, "saves/nds").apply { mkdirs() }
        File(sharedDir, "Mario Kart DS (USA).dsv").writeBytes(byteArrayOf(1))
        coEvery { emulatorConfigDao.getSavePathForGame(2L) } returns sharedDir.absolutePath
        coEvery { emulatorConfigDao.countOtherGamesWithSavePath(2L, sharedDir.absolutePath) } returns 1
        val europeRom = File(tempDir, "roms/nds/Mario Kart DS (Europe).nds").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0))
        }

        val result = resolver.discoverSavePath(
            emulatorId = "drastic", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = europeRom.absolutePath, gameId = 2L,
        )

        assertNull(result)
    }

    @Test
    fun `a per-game folder only this game names still falls back to the title`() = runTest {
        val ownDir = File(tempDir, "saves/mkds").apply { mkdirs() }
        val titled = File(ownDir, "Mario Kart DS.dsv").apply { writeBytes(byteArrayOf(1)) }
        coEvery { emulatorConfigDao.getSavePathForGame(2L) } returns ownDir.absolutePath
        coEvery { emulatorConfigDao.countOtherGamesWithSavePath(2L, ownDir.absolutePath) } returns 0
        val europeRom = File(tempDir, "roms/nds/Mario Kart DS (Europe).nds").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0))
        }

        val result = resolver.discoverSavePath(
            emulatorId = "drastic", gameTitle = "Mario Kart DS", platformSlug = "nds",
            romPath = europeRom.absolutePath, gameId = 2L,
        )

        assertEquals(titled.absolutePath, result)
    }

    @Test
    fun `savesBesideRom discovers the save in the ROM folder`() = runTest {
        coEvery { emulatorSaveConfigDao.getByEmulator("argosy") } returns
            EmulatorSaveConfigEntity(emulatorId = "argosy", savePathPattern = "", isAutoDetected = true, savesBesideRom = true)
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }
        val saveFile = File(romDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }

        val result = resolver.discoverSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(saveFile.absolutePath, result)
    }

    @Test
    fun `savesBesideRom constructs the restore target beside the ROM with the ROM name`() = runTest {
        coEvery { emulatorSaveConfigDao.getByEmulator("argosy") } returns
            EmulatorSaveConfigEntity(emulatorId = "argosy", savePathPattern = "", isAutoDetected = true, savesBesideRom = true)
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath,
        )

        assertEquals(File(romDir, "Zelda.srm").absolutePath, result)
    }

    @Test
    fun `constructSavePath names the flat save after the ROM base name not the game title`() = runTest {
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda (USA).gba").apply { writeBytes(byteArrayOf(0)) }

        val result = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
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
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(File(perGameDir, "Zelda (USA).srm").absolutePath, constructed)

        File(constructed!!).writeBytes(byteArrayOf(1))
        val discovered = resolver.discoverSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )

        assertEquals(constructed, discovered)
    }

    @Test
    fun `per-game path beats the per-emulator user override in both discover and construct`() = runTest {
        val perGameDir = File(tempDir, "custom/per-game").apply { mkdirs() }
        val overrideDir = File(tempDir, "custom/emulator-override").apply { mkdirs() }
        coEvery { emulatorConfigDao.getSavePathForGame(1L) } returns perGameDir.absolutePath
        coEvery { emulatorSaveConfigDao.getByEmulator("argosy") } returns
            EmulatorSaveConfigEntity(
                emulatorId = "argosy", savePathPattern = overrideDir.absolutePath,
                isAutoDetected = false, isUserOverride = true,
            )
        val romDir = File(tempDir, "roms/gba").apply { mkdirs() }
        val romFile = File(romDir, "Zelda.gba").apply { writeBytes(byteArrayOf(0)) }
        val perGameSave = File(perGameDir, "Zelda.srm").apply { writeBytes(byteArrayOf(1)) }
        File(overrideDir, "Zelda.srm").writeBytes(byteArrayOf(2))

        val discovered = resolver.discoverSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
            romPath = romFile.absolutePath, gameId = 1L,
        )
        val constructed = resolver.constructSavePath(
            emulatorId = "argosy", gameTitle = "Zelda", platformSlug = "gba",
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

    @Test
    fun `a file shaped save on a folder based platform is not given the memcard directory`() = runTest {
        val memcardDir = File(tempDir, "memcards/Shared.ps2").absolutePath
        val folderHandler = mockk<com.nendo.argosy.data.sync.platform.FolderSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.getFolderHandler("ps2") } returns folderHandler
        every { folderHandler.constructSavePath(any(), any()) } returns memcardDir

        val folderShapedResult = resolver.constructSavePath(
            emulatorId = "nethersx2", gameTitle = "Shadow of the Colossus", platformSlug = "ps2",
            romPath = "/roms/sotc.iso", cachedSaveId = "SCUS-97472", gameId = 1L,
        )
        val fileShapedResult = resolver.constructSavePath(
            emulatorId = "nethersx2", gameTitle = "Shadow of the Colossus", platformSlug = "ps2",
            romPath = "/roms/sotc.iso", cachedSaveId = "SCUS-97472", gameId = 1L,
            folderShaped = false,
        )

        assertEquals(memcardDir, folderShapedResult)
        assertNotEquals(memcardDir, fileShapedResult)
    }

    @Test
    fun `a Sigil save root that cannot be read is reported unreadable, not absent`() = runTest {
        val root = File(tempDir, "Android/data/org.ppsspp.ppsspp/files").apply { mkdirs() }.absolutePath
        val sigil = mockk<com.nendo.argosy.data.sync.platform.SigilSaveHandler>()
        coEvery { saveHandlerRegistry.isSigilRoutedFor(1L, any(), "psp", "ppsspp") } returns true
        every { saveHandlerRegistry.sigil } returns sigil
        coEvery { sigil.route(1L, any()) } returns
            com.nendo.argosy.data.sync.platform.SigilRoute("ppsspp_standalone", root, null, null)
        coEvery { sigil.sharedLibretroRoot(any(), any()) } returns null
        coEvery { sigil.collect(1L, any(), any()) } returns
            com.nendo.argosy.data.sync.platform.SigilCollect.Unreadable("permission denied")

        val lookup = resolver.discoverSavePathChecked(
            emulatorId = "ppsspp", gameTitle = "Lunar", platformSlug = "psp",
            romPath = "/roms/lunar.iso", gameId = 1L,
        )

        assertEquals(SaveLookup.Unreadable(root), lookup)
    }

    private fun storePspGame(): GameEntity = rommGame().copy(
        platformSlug = "psp", localPath = "/roms/masou.chd",
        titleId = "NPJH50804", saveId = "NPJH50804", titleIdLocked = true
    )

    private fun pspHandlerFinding(saveId: String, path: String): com.nendo.argosy.data.sync.platform.FolderSaveHandler {
        val handler = mockk<com.nendo.argosy.data.sync.platform.FolderSaveHandler>(relaxed = true)
        every { saveHandlerRegistry.getFolderHandler("psp") } returns handler
        every { handler.findSaveFolderBySaveId(any(), any()) } returns null
        every { handler.findSaveFolderBySaveId(any(), saveId) } returns path
        return handler
    }

    @Test
    fun `a stored save id that finds nothing is replaced by the extracted one that finds the save`() = runTest {
        coEvery { gameDao.getById(1L) } returns storePspGame()
        val romFile = File(tempDir, "masou.chd").apply { writeBytes(byteArrayOf(0)) }
        every { titleIdExtractor.extractTitleIdWithSource(any(), "psp", any()) } returns
            TitleIdResult(titleId = "NPJH50804", fromBinary = true, saveId = "ULJS00445")
        pspHandlerFinding("ULJS00445", "/sd/PSP/SAVEDATA")

        val result = resolver.discoverSavePath(
            emulatorId = "ppsspp", gameTitle = "Masou Kishin", platformSlug = "psp",
            romPath = romFile.absolutePath, cachedSaveId = "NPJH50804", gameId = 1L,
        )

        assertEquals("/sd/PSP/SAVEDATA", result)
        coVerify(exactly = 1) { gameDao.setSaveId(1L, "ULJS00445") }
        coVerify(exactly = 0) { gameDao.setTitleAndSaveIdWithLock(any(), any(), any(), any()) }
    }

    @Test
    fun `a stored save id is kept when the extracted one finds nothing either`() = runTest {
        coEvery { gameDao.getById(1L) } returns storePspGame()
        val romFile = File(tempDir, "masou.chd").apply { writeBytes(byteArrayOf(0)) }
        every { titleIdExtractor.extractTitleIdWithSource(any(), "psp", any()) } returns
            TitleIdResult(titleId = "NPJH50804", fromBinary = true, saveId = "ULJS00445")
        pspHandlerFinding("ULUS99999", "/sd/PSP/SAVEDATA")

        resolver.discoverSavePath(
            emulatorId = "ppsspp", gameTitle = "Masou Kishin", platformSlug = "psp",
            romPath = romFile.absolutePath, cachedSaveId = "NPJH50804", gameId = 1L,
        )

        coVerify(exactly = 0) { gameDao.setSaveId(any(), any()) }
    }

    @Test
    fun `a stored save id that finds its save is never re-extracted`() = runTest {
        coEvery { gameDao.getById(1L) } returns storePspGame()
        pspHandlerFinding("NPJH50804", "/sd/PSP/SAVEDATA")

        resolver.discoverSavePath(
            emulatorId = "ppsspp", gameTitle = "Masou Kishin", platformSlug = "psp",
            romPath = "/roms/masou.chd", cachedSaveId = "NPJH50804", gameId = 1L,
        )

        coVerify(exactly = 0) { titleIdExtractor.extractTitleIdWithSource(any(), any(), any()) }
        coVerify(exactly = 0) { gameDao.setSaveId(any(), any()) }
    }

    private val wiiSaveId = "00010000/534f4d45"
    private val retroArch64 = "com.retroarch.aarch64"

    private fun retroArchWiiTitles(): File {
        val sortedSaves = File(tempDir, "Cocoon/Emulation/saves/retroarch/saves/wii").apply { mkdirs() }
        coEvery {
            retroArchPathResolver.resolveSaveDirectoriesForPlatform(
                match { it.emulatorId == "retroarch_64" }, "wii"
            )
        } returns listOf(sortedSaves.absolutePath)
        every { saveHandlerRegistry.getFolderHandler("wii") } returns
            com.nendo.argosy.data.sync.platform.CategorySplitFolderHandler(
                context, realFsFal(), saveArchiver, platformSlug = "wii",
                categoryDir = Regex("0001[0-9a-fA-F]{4}"),
                categoryParents = listOf("", "title", "Wii/title", "User/Wii/title", "dolphin-emu/User/Wii/title")
            )
        return File(sortedSaves, "User/Wii/title/00010000").apply { mkdirs() }
    }

    @Test
    fun `a RetroArch Wii save is found under the save directory retroarch cfg sorts it into`() = runTest {
        stubBuiltinSettings()
        val titleFolder = File(retroArchWiiTitles(), "534f4d45").apply { mkdirs() }
        File(titleFolder, "data.bin").writeBytes(byteArrayOf(1))

        val result = resolver.discoverSavePath(
            emulatorId = "retroarch_64", gameTitle = "Rhythm Heaven Fever", platformSlug = "wii",
            romPath = "/roms/wii/Rhythm Heaven Fever (USA).rvz", cachedSaveId = wiiSaveId,
            emulatorPackage = retroArch64, gameId = 1L,
        )

        assertEquals(titleFolder.absolutePath, result)
    }

    @Test
    fun `a RetroArch Wii download lands under the save directory retroarch cfg sorts it into`() = runTest {
        stubBuiltinSettings()
        val titles = retroArchWiiTitles()

        val target = resolver.constructFolderSavePathWithOverride(
            emulatorId = "retroarch_64", platformSlug = "wii",
            romPath = "/roms/wii/Rhythm Heaven Fever (USA).rvz", gameId = 1L,
            gameTitle = "Rhythm Heaven Fever", cachedSaveId = wiiSaveId, emulatorPackage = retroArch64,
        )

        assertEquals(File(titles, "534f4d45").absolutePath, target)
    }

    private fun rommGame(): GameEntity = GameEntity(
        id = 1L, platformId = 10L, platformSlug = "switch",
        title = "BOTW", sortTitle = "botw",
        localPath = "/roms/botw.nsp", rommId = 100L,
        igdbId = null, source = GameSource.ROMM_SYNCED,
    )
}
