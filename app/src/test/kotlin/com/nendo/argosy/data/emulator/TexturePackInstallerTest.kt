package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.GameDiscDao
import com.nendo.argosy.data.local.dao.GameFileDao
import com.nendo.argosy.data.local.entity.GameDiscEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.GameFileEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.platform.PlatformFolderResolver
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TexturePackInstallerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var saveDir: File
    private lateinit var systemDir: File
    private lateinit var builtinSaveBase: BuiltinSaveBase
    private lateinit var builtinCoreResolver: BuiltinCoreResolver
    private lateinit var emulatorResolver: EmulatorResolver
    private lateinit var titleIdExtractor: TitleIdExtractor
    private lateinit var baseRomFileResolver: BaseRomFileResolver
    private lateinit var platformFolderResolver: PlatformFolderResolver
    private lateinit var gameDao: GameDao
    private lateinit var gameDiscDao: GameDiscDao
    private lateinit var gameFileDao: GameFileDao
    private lateinit var installer: TexturePackInstaller

    @Before
    fun setup() {
        saveDir = tempFolder.newFolder("saves")
        systemDir = tempFolder.newFolder("system")
        builtinSaveBase = mockk(relaxed = true)
        builtinCoreResolver = mockk(relaxed = true)
        emulatorResolver = mockk(relaxed = true)
        titleIdExtractor = mockk(relaxed = true)
        baseRomFileResolver = mockk(relaxed = true)
        platformFolderResolver = mockk(relaxed = true)
        gameDao = mockk(relaxed = true)
        gameDiscDao = mockk(relaxed = true)
        gameFileDao = mockk(relaxed = true)

        coEvery { builtinSaveBase.forGame(any(), any()) } returns saveDir.absolutePath
        coEvery { builtinCoreResolver.resolveCoreId(any(), any(), "ngc") } returns "dolphin"
        coEvery { builtinCoreResolver.resolveCoreId(any(), any(), "n64") } returns "mupen64plus_next_gles3"
        coEvery { baseRomFileResolver.resolve(any(), any()) } answers { secondArg() }
        coEvery {
            emulatorResolver.getEmulatorPackageForGame(any(), any(), any())
        } returns EmulatorRegistry.BUILTIN_PACKAGE
        every {
            titleIdExtractor.resolveTextureFolderKey(any(), "ngc", any(), any(), any(), any())
        } answers {
            val game = thirdArg<GameEntity?>()
            game?.saveId ?: game?.titleId
        }

        installer = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir
        )
    }

    @Test
    fun `extracting flat texture zip places files inside Load Textures GZLE01 without mutating perGameSettingsEnabled`() = runTest {
        val zipFile = createZip(
            "flat_pack.zip",
            mapOf(
                "tex1.png" to "png_data_1",
                "sub/tex2.dds" to "dds_data_2"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 42L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)

        assertNotNull(manifest)
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        assertTrue(File(targetDir, "tex1.png").isFile)
        assertEquals("png_data_1", File(targetDir, "tex1.png").readText())
        assertTrue(File(targetDir, "sub/tex2.dds").isFile)
        assertEquals("dds_data_2", File(targetDir, "sub/tex2.dds").readText())
        assertFalse("Staging zip should be deleted after extraction", zipFile.exists())
        coVerify { gameFileDao.updateLocalPath(42L, manifest!!.absolutePath, any()) }
        coVerify(exactly = 0) { gameDao.setPerGameSettingsEnabled(any(), any()) }

        val usage = TexturePackInstaller.measureManifestUsage(manifest!!)
        assertEquals(
            "png_data_1".length.toLong() + "dds_data_2".length.toLong(),
            usage.first
        )
        assertEquals(2, usage.second)
    }

    @Test
    fun `extracting pre-nested texture zip strips duplicate GZLE01 prefix`() = runTest {
        val zipFile = createZip(
            "nested_pack.zip",
            mapOf(
                "GZLE01/tex1.png" to "nested_1",
                "GZLE01/ui/hud.png" to "nested_2"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 43L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)

        assertNotNull(manifest)
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        assertTrue(File(targetDir, "tex1.png").isFile)
        assertTrue(File(targetDir, "ui/hud.png").isFile)
        assertFalse(File(targetDir, "GZLE01").exists())
    }

    @Test
    fun `extracting N64 zip containing hts file routes to cache dir and resolves gliden64 plus hts options`() = runTest {
        val zipFile = createZip(
            "oot_reloaded.zip",
            mapOf("THE LEGEND OF ZELDA_HIRESTEXTURES.hts" to "hts_binary_payload")
        )
        val game = testGame(titleId = "CZLE", platformSlug = "n64")
        val modFile = testModFile(id = 45L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)

        assertNotNull(manifest)
        val cacheDir = File(systemDir, "Mupen64plus/cache")
        assertTrue(File(cacheDir, "THE LEGEND OF ZELDA_HIRESTEXTURES.hts").isFile)
        assertFalse(zipFile.exists())

        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(
            modFile.copy(localPath = manifest!!.absolutePath)
        )
        val resolvedOptions = installer.resolveAutoCoreOptionsForGame(
            gameId = game.id,
            coreId = "mupen64plus_next_gles3",
            platformSlug = "n64"
        )
        assertEquals(
            mapOf(
                "mupen64plus-rdp-plugin" to "gliden64",
                "mupen64plus-txHiresEnable" to "True",
                "mupen64plus-txHiresFullAlphaChannel" to "True",
                "mupen64plus-EnableEnhancedHighResStorage" to "True"
            ),
            resolvedOptions
        )

        installer.uninstallModFile(modFile.copy(localPath = manifest.absolutePath))
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(
            modFile.copy(localPath = null)
        )
        assertTrue(
            installer.resolveAutoCoreOptionsForGame(
                gameId = game.id,
                coreId = "mupen64plus_next_gles3",
                platformSlug = "n64"
            ).isEmpty()
        )
    }

    @Test
    fun `a second launch leaves an installed N64 cache pack in the cache folder`() = runTest {
        every {
            titleIdExtractor.resolveTextureFolderKey(any(), "n64", any(), any(), any(), any())
        } returns "THE LEGEND OF ZELDA"
        val zipFile = createZip(
            "oot_htc.zip",
            mapOf("THE LEGEND OF ZELDA_HIRESTEXTURES.htc" to "htc_binary_payload")
        )
        val game = testGame(titleId = "CZLE", platformSlug = "n64")
        val modFile = testModFile(id = 46L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)
        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)
        assertNotNull(manifest)
        val cachedPack = File(systemDir, "Mupen64plus/cache/THE LEGEND OF ZELDA_HIRESTEXTURES.htc")
        assertTrue(cachedPack.isFile)

        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(
            modFile.copy(localPath = manifest!!.absolutePath)
        )
        val installed = installer.installPendingModFilesForGame(game.id)

        assertEquals(0, installed)
        assertTrue(cachedPack.isFile)
        assertTrue(manifest.isFile)
        assertFalse(File(systemDir, "Mupen64plus/hires_texture/THE LEGEND OF ZELDA").exists())
        coVerify(exactly = 1) { gameFileDao.updateLocalPath(46L, any(), any()) }
    }

    @Test
    fun `parallel_n64 rejects hts texture packs without deleting source archive`() = runTest {
        coEvery { builtinCoreResolver.resolveCoreId(any(), any(), "n64") } returns "parallel_n64"
        val zipFile = createZip(
            "oot_hts.zip",
            mapOf("THE LEGEND OF ZELDA_HIRESTEXTURES.hts" to "hts_binary_payload")
        )
        val game = testGame(titleId = "CZLE", platformSlug = "n64")
        val modFile = testModFile(id = 47L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)

        assertNull(manifest)
        assertTrue("Unsupported .hts zip must not be deleted", zipFile.exists())
        assertFalse(File(systemDir, "Mupen64plus/cache/THE LEGEND OF ZELDA_HIRESTEXTURES.hts").exists())
    }

    @Test
    fun `rejects zipped ROM hack in mods without unpacking or deleting it`() = runTest {
        val romZip = createZip(
            "zelda_master_quest_hack.zip",
            mapOf("zelda_mq.z64" to "rom_binary_payload")
        )
        val game = testGame(titleId = "CZLE", platformSlug = "n64")
        val modFile = testModFile(id = 48L, gameId = game.id, fileName = romZip.name, localPath = romZip.absolutePath)

        assertFalse(TexturePackInstaller.looksLikeTextureZip(romZip))
        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = romZip)

        assertNull(manifest)
        assertTrue("Zipped ROM hack in mods/ must not be deleted", romZip.exists())
    }

    @Test
    fun `looksLikeTextureZip accepts texture archive containing binary palette data file`() {
        val packWithBin = createZip(
            "pack_with_bin.zip",
            mapOf(
                "textures/tex1.png" to "png_bytes",
                "palette.bin" to "binary_palette_data"
            )
        )
        assertTrue(TexturePackInstaller.looksLikeTextureZip(packWithBin))
    }

    @Test
    fun `defers texture pack installation until base ROM is on disk`() = runTest {
        val zipFile = createZip(
            "flat_pack.zip",
            mapOf("GZLE01/tex1.png" to "png_data_1")
        )
        val gameWithoutRom = testGame(titleId = "GZLE01").copy(localPath = null)
        val modFile = testModFile(id = 49L, gameId = gameWithoutRom.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = gameWithoutRom, gameFile = modFile, sourceFile = zipFile)

        assertNull("Installation must defer when base ROM is missing", manifest)
        assertTrue("Source archive must not be deleted when deferred", zipFile.exists())
    }

    @Test
    fun `defers texture pack installation when base ROM path is non-null but file does not exist on disk`() = runTest {
        val zipFile = createZip(
            "flat_pack.zip",
            mapOf("GZLE01/tex1.png" to "png_data_1")
        )
        val gameWithMissingRom = testGame(titleId = "GZLE01").copy(
            localPath = File(tempFolder.root, "missing_game.iso").absolutePath
        )
        val modFile = testModFile(id = 50L, gameId = gameWithMissingRom.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = gameWithMissingRom, gameFile = modFile, sourceFile = zipFile)

        assertNull("Installation must defer when base ROM does not physically exist on disk", manifest)
        assertTrue("Source archive must not be deleted when deferred", zipFile.exists())
    }

    @Test
    fun `GameCube texture pack with GZLE01 prefix strips prefix when folder key is 4-char or 6-char`() {
        val entryPaths = listOf(
            "GZLE01/textures/tex1.png",
            "GZLE01/textures/tex2.png"
        )
        val prefixWith4Char = TexturePackInstaller.computeRedundantPrefix(entryPaths, "GZLE")
        assertEquals("GZLE01/", prefixWith4Char)

        val prefixWith6Char = TexturePackInstaller.computeRedundantPrefix(entryPaths, "GZLE01")
        assertEquals("GZLE01/", prefixWith6Char)

        val prefixWith3Char = TexturePackInstaller.computeRedundantPrefix(entryPaths, "GZL")
        assertEquals("GZLE01/", prefixWith3Char)
    }

    @Test
    fun `N64 texture pack with descriptive folder name strips redundant prefix`() {
        val entryPaths = listOf(
            "SUPER MARIO 64 (USA)/textures/tex1.png",
            "SUPER MARIO 64 (USA)/textures/tex2.png"
        )
        val prefix = TexturePackInstaller.computeRedundantPrefix(entryPaths, "SUPER MARIO 64")
        assertEquals("SUPER MARIO 64 (USA)/", prefix)
    }

    @Test
    fun `computeRedundantPrefix strips container folder names textures, load, and hires_texture`() {
        val dolphinEntries = listOf("textures/GALE01/characters/mario.png")
        assertEquals("textures/GALE01/", TexturePackInstaller.computeRedundantPrefix(dolphinEntries, "GALE01"))

        val loadEntries = listOf("Load/Textures/GALE01/characters/mario.png")
        assertEquals("Load/Textures/GALE01/", TexturePackInstaller.computeRedundantPrefix(loadEntries, "GALE01"))

        val flatTextures = listOf("textures/characters/mario.png")
        assertEquals("textures/", TexturePackInstaller.computeRedundantPrefix(flatTextures, "GALE01"))

        val n64Entries = listOf("hires_texture/SUPER MARIO 64/mario.png")
        assertEquals("hires_texture/SUPER MARIO 64/", TexturePackInstaller.computeRedundantPrefix(n64Entries, "SUPER MARIO 64"))
    }

    @Test
    fun `installPendingModFilesForGame tracks failed installs and can be invalidated`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val textureZipWithUnresolvableKey = createZip(
            "unresolvable_pack.zip",
            mapOf("unknown_folder/tex1.png" to "png_bytes")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 999L, gameId = game.id, fileName = textureZipWithUnresolvableKey.name, localPath = textureZipWithUnresolvableKey.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)
        every {
            titleIdExtractor.resolveTextureFolderKey(any(), "ngc", any(), any(), any(), any())
        } returns null

        val installedCountFirst = installer.installPendingModFilesForGame(game.id)
        assertEquals(0, installedCountFirst)
        assertTrue(TexturePackInstaller.hasFailedInstall(999L))

        val installedCountSecond = installer.installPendingModFilesForGame(game.id)
        assertEquals(0, installedCountSecond)

        installer.invalidateFailedInstall(999L)
        assertFalse(TexturePackInstaller.hasFailedInstall(999L))
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `non-texture mods do not poison failedInstalls`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val romPatchZip = createZip(
            "translation.zip",
            mapOf("patch.ips" to "ips_data")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 888L, gameId = game.id, fileName = romPatchZip.name, localPath = romPatchZip.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        val installedCount = installer.installPendingModFilesForGame(game.id)
        assertEquals(0, installedCount)
        assertFalse(TexturePackInstaller.hasFailedInstall(888L))
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `installPendingModFilesForGame reports progress starting at 0 and finishing at 1`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val textureZip = createZip(
            "progress_pack.zip",
            mapOf("tex.png" to "png_bytes")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 555L, gameId = game.id, fileName = textureZip.name, localPath = textureZip.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        val progressReports = mutableListOf<Float?>()
        val installedCount = installer.installPendingModFilesForGame(game.id) { fraction ->
            progressReports.add(fraction)
        }

        assertEquals(1, installedCount)
        assertTrue("Expected progress reports", progressReports.isNotEmpty())
        assertEquals(0f, progressReports.first() ?: -1f, 0.001f)
        assertEquals(1f, progressReports.last() ?: -1f, 0.001f)
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `installPendingModFilesForGame with no textures does not report progress`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val game = testGame(titleId = "GZLE01")
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns emptyList()

        var progressReported = false
        val installedCount = installer.installPendingModFilesForGame(game.id) {
            progressReported = true
        }

        assertEquals(0, installedCount)
        assertFalse("Progress should not be reported when there are no textures", progressReported)
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `installPendingModFilesForGame stops when isCancelled is true`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val textureZip = createZip(
            "cancel_pack.zip",
            mapOf("tex.png" to "png_bytes")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 556L, gameId = game.id, fileName = textureZip.name, localPath = textureZip.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        var threwCancellation = false
        try {
            installer.installPendingModFilesForGame(
                gameId = game.id,
                isCancelled = { true }
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            threwCancellation = true
        }

        assertTrue("Expected CancellationException when cancelled", threwCancellation)
        assertFalse("Cancelled install should not be marked failed", TexturePackInstaller.hasFailedInstall(556L))
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `installPendingModFilesForGame cancelled mid-extraction cleans up staging and leaves destination untouched`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val textureZip = createZip(
            "cancel_mid_pack.zip",
            mapOf(
                "tex1.png" to "png_bytes_1",
                "tex2.png" to "png_bytes_2",
                "tex3.png" to "png_bytes_3"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 557L, gameId = game.id, fileName = textureZip.name, localPath = textureZip.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        var cancelled = false
        var progressCount = 0
        var threwCancellation = false
        try {
            installer.installPendingModFilesForGame(
                gameId = game.id,
                isCancelled = { cancelled },
                onProgress = { fraction ->
                    if (fraction != null && fraction > 0f) {
                        progressCount++
                        cancelled = true
                    }
                }
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            threwCancellation = true
        }

        assertTrue("Expected CancellationException on mid-extraction cancel", threwCancellation)
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        val targetFiles = targetDir.listFiles() ?: emptyArray()
        assertTrue(
            "Destination directory should contain zero files, got: ${targetFiles.map { it.name }}",
            targetFiles.isEmpty()
        )
        val manifest = File(targetDir, ".argosy_mod_${modFile.id}.manifest")
        assertFalse("Manifest file must not be created", manifest.exists())
        coVerify(exactly = 0) { gameFileDao.updateLocalPath(modFile.id, any(), any()) }
        assertFalse("Cancelled install should not be marked failed", TexturePackInstaller.hasFailedInstall(557L))
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `extractZipRootSerialFallback normalizes PSP serials by stripping hyphens and underscores`() {
        val zipWithHyphen = createZip(
            "psp_pack_hyphen.zip",
            mapOf("ULUS-10041/textures/ui.png" to "tex_data")
        )
        assertEquals("ULUS10041", TitleIdExtractor.extractZipRootSerialFallback(zipWithHyphen, "psp"))

        val zipWithUnderscore = createZip(
            "psp_pack_underscore.zip",
            mapOf("ULUS_10041/textures/ui.png" to "tex_data")
        )
        assertEquals("ULUS10041", TitleIdExtractor.extractZipRootSerialFallback(zipWithUnderscore, "psp"))

        val zipClean = createZip(
            "psp_pack_clean.zip",
            mapOf("ULUS10041/textures/ui.png" to "tex_data")
        )
        assertEquals("ULUS10041", TitleIdExtractor.extractZipRootSerialFallback(zipClean, "psp"))
    }

    @Test
    fun `ROM disc header takes precedence over zip archive folder root for NGC and Wii`() {
        val extractor = TitleIdExtractor(
            switchKeyManager = mockk(relaxed = true)
        )
        val usRom = File(tempFolder.root, "GALE01.iso").apply {
            writeBytes("GALE01".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        }
        val euZip = createZip(
            "galp01_pack.zip",
            mapOf("GALP01/textures/tex1.png" to "tex_data")
        )
        val key = extractor.resolveTextureFolderKey(
            strategy = TextureFolderKeyStrategy.GAME_ID,
            platformSlug = "ngc",
            game = null,
            romFile = usRom,
            zipArchive = euZip
        )
        assertEquals("GALE01", key)
    }

    @Test
    fun `readNintendoDisc6CharGameId reads 6-char GameID from ISO, WBFS, and RVZ headers`() {
        val isoFile = File(tempFolder.root, "test.iso").apply {
            writeBytes("GZLE01".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        }
        assertEquals("GZLE01", TitleIdExtractor.readNintendoDisc6CharGameId(isoFile))

        val wbfsFile = File(tempFolder.root, "test.wbfs").apply {
            val bytes = ByteArray(0x210)
            System.arraycopy("WBFS".toByteArray(Charsets.US_ASCII), 0, bytes, 0, 4)
            System.arraycopy("RMGE01".toByteArray(Charsets.US_ASCII), 0, bytes, 0x200, 6)
            writeBytes(bytes)
        }
        assertEquals("RMGE01", TitleIdExtractor.readNintendoDisc6CharGameId(wbfsFile))

        val wbfs4kFile = File(tempFolder.root, "test_4k.wbfs").apply {
            val bytes = ByteArray(0x1010)
            System.arraycopy("WBFS".toByteArray(Charsets.US_ASCII), 0, bytes, 0, 4)
            bytes[8] = 12.toByte()
            System.arraycopy("SB4E01".toByteArray(Charsets.US_ASCII), 0, bytes, 0x1000, 6)
            writeBytes(bytes)
        }
        assertEquals("SB4E01", TitleIdExtractor.readNintendoDisc6CharGameId(wbfs4kFile))

        val rvzFile = File(tempFolder.root, "test.rvz").apply {
            val bytes = ByteArray(0x60)
            System.arraycopy("RVZ\u0001".toByteArray(Charsets.US_ASCII), 0, bytes, 0, 4)
            System.arraycopy("SMNE01".toByteArray(Charsets.US_ASCII), 0, bytes, 0x58, 6)
            writeBytes(bytes)
        }
        assertEquals("SMNE01", TitleIdExtractor.readNintendoDisc6CharGameId(rvzFile))
    }

    @Test
    fun `resolves first disc when base rom is an m3u playlist`() = runTest {
        val disc1 = File(tempFolder.root, "Resident Evil (Disc 1).iso").apply {
            writeBytes("GZLE01".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        }
        val m3u = File(tempFolder.root, "Resident Evil.m3u").apply {
            writeText("Resident Evil (Disc 1).iso\nResident Evil (Disc 2).iso\n")
        }
        val game = testGame(titleId = "GZLE01").copy(localPath = m3u.absolutePath)
        val zipFile = createZip(
            "re_pack.zip",
            mapOf("GZLE01/tex.png" to "tex_data")
        )
        val modFile = testModFile(id = 88L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)
        assertNotNull(manifest)
        assertTrue(File(saveDir, "User/Load/Textures/GZLE01/tex.png").isFile)
    }

    @Test
    fun `resolves multi-disc game when first disc entry is an m3u playlist`() = runTest {
        val disc1 = File(tempFolder.root, "RE Disc 1.iso").apply {
            writeBytes("GZLE01".toByteArray(Charsets.US_ASCII) + ByteArray(100))
        }
        val m3u = File(tempFolder.root, "RE Multi.m3u").apply {
            writeText("RE Disc 1.iso\nRE Disc 2.iso\n")
        }
        val game = testGame(titleId = "GZLE01").copy(isMultiDisc = true)
        coEvery { gameDiscDao.getDiscsForGame(game.id) } returns listOf(
            GameDiscEntity(
                id = 1L,
                gameId = game.id,
                discNumber = 1,
                rommId = 1L,
                fileName = m3u.name,
                localPath = m3u.absolutePath,
                fileSize = m3u.length()
            )
        )
        val zipFile = createZip(
            "re_multi_pack.zip",
            mapOf("GZLE01/tex.png" to "tex_data")
        )
        val modFile = testModFile(id = 89L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)
        assertNotNull(manifest)
        assertTrue(File(saveDir, "User/Load/Textures/GZLE01/tex.png").isFile)
    }

    @Test
    fun `does not extract or delete mod zip when game resolves to unsupported external emulator`() = runTest {
        coEvery {
            emulatorResolver.getEmulatorPackageForGame(any(), any(), any())
        } returns "org.unsupported.emulator"

        val zipFile = createZip(
            "external_pack.zip",
            mapOf("GZLE01/tex1.png" to "png_data")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 46L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val manifest = installer.installForModFile(game = game, gameFile = modFile, sourceFile = zipFile)

        assertNull(manifest)
        assertTrue("Unsupported external emulator mod zip must not be deleted", zipFile.exists())
    }

    @Test
    fun `rejects zip-slip path traversal entries`() = runTest {
        val zipFile = createZip(
            "evil.zip",
            mapOf(
                "valid.png" to "png",
                "../../evil.png" to "malicious"
            )
        )
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")

        var threwSecurity = false
        try {
            installer.extractTextureZip(
                zipFile = zipFile,
                targetDir = targetDir,
                folderKey = "GZLE01",
                gameFileId = 99L
            )
        } catch (_: SecurityException) {
            threwSecurity = true
        }

        assertTrue("Expected SecurityException on Zip-Slip entry", threwSecurity)
        assertFalse(File(saveDir, "User/evil.png").exists())
    }

    @Test
    fun `uninstall cleanup preserves files still claimed by sibling manifest`() = runTest {
        val zip1 = createZip(
            "pack1.zip",
            mapOf(
                "GZLE01/shared.png" to "shared_data",
                "GZLE01/only1.png" to "only_1"
            )
        )
        val zip2 = createZip(
            "pack2.zip",
            mapOf(
                "GZLE01/shared.png" to "shared_data",
                "GZLE01/only2.png" to "only_2"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val mod1 = testModFile(id = 51L, gameId = game.id, fileName = zip1.name, localPath = zip1.absolutePath)
        val mod2 = testModFile(id = 52L, gameId = game.id, fileName = zip2.name, localPath = zip2.absolutePath)

        val manifest1 = installer.installForModFile(game = game, gameFile = mod1, sourceFile = zip1)
        val manifest2 = installer.installForModFile(game = game, gameFile = mod2, sourceFile = zip2)
        assertNotNull(manifest1)
        assertNotNull(manifest2)

        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        installer.uninstallModFile(mod1.copy(localPath = manifest1!!.absolutePath))

        assertFalse(File(targetDir, "only1.png").exists())
        assertTrue("Shared file claimed by pack2 must remain", File(targetDir, "shared.png").exists())
        assertTrue(File(targetDir, "only2.png").exists())
        assertFalse(manifest1.exists())
        assertTrue(manifest2!!.exists())

        installer.uninstallModFile(mod2.copy(localPath = manifest2.absolutePath))
        assertFalse(File(targetDir, "shared.png").exists())
        assertFalse(File(targetDir, "only2.png").exists())
        assertFalse(manifest2.exists())
    }

    @Test
    fun `missing base ROM defers texture install without poisoning failedInstalls`() = runTest {
        TexturePackInstaller.clearFailedInstalls()
        val zipFile = createZip(
            "defer_pack.zip",
            mapOf("tex1.png" to "data")
        )
        val game = testGame(titleId = "GZLE01").copy(localPath = null)
        val modFile = testModFile(id = 77L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        val outcome = installer.installForModFileOutcome(
            game = game,
            gameFile = modFile,
            sourceFile = zipFile
        )

        assertTrue(outcome is TexturePackInstaller.ModInstallOutcome.Deferred)
        assertFalse("Deferred install must NOT be marked in failedInstalls", TexturePackInstaller.hasFailedInstall(77L))
        assertTrue("Source zip must be preserved", zipFile.exists())
    }

    @Test
    fun `database update failure rolls back extracted files and preserves source zip`() = runTest {
        val zipFile = createZip(
            "pack_db_fail.zip",
            mapOf(
                "tex1.png" to "data1",
                "tex2.png" to "data2"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 88L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        coEvery { gameFileDao.updateLocalPath(88L, any(), any()) } throws RuntimeException("SQLite disk error")

        var threw = false
        try {
            installer.installForModFileOutcome(
                game = game,
                gameFile = modFile,
                sourceFile = zipFile
            )
        } catch (_: RuntimeException) {
            threw = true
        }

        assertTrue("Expected exception to propagate", threw)
        assertTrue("Source zip must NOT be deleted when database update fails", zipFile.exists())
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        assertFalse("Extracted files must be rolled back", File(targetDir, "tex1.png").exists())
        assertFalse("Extracted files must be rolled back", File(targetDir, "tex2.png").exists())
        assertFalse("Manifest must be deleted", TexturePackInstaller.manifestFileFor(targetDir, 88L).exists())
    }

    @Test
    fun `a pack cancelled mid-commit restores the files it replaced from an earlier pack`() = runTest {
        val game = testGame(titleId = "GZLE01")
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        val firstZip = createZip("first_pack.zip", mapOf("shared.png" to "first", "own1.png" to "one"))
        val firstMod = testModFile(id = 61L, gameId = game.id, fileName = firstZip.name, localPath = firstZip.absolutePath)
        val firstManifest = installer.installForModFile(game = game, gameFile = firstMod, sourceFile = firstZip)
        assertNotNull(firstManifest)

        val secondZip = createZip("second_pack.zip", mapOf("shared.png" to "second", "own2.png" to "two"))
        val secondMod = testModFile(id = 62L, gameId = game.id, fileName = secondZip.name, localPath = secondZip.absolutePath)
        val shared = File(targetDir, "shared.png")
        var threwCancellation = false
        try {
            installer.installForModFileOutcome(
                game = game,
                gameFile = secondMod,
                sourceFile = secondZip,
                isCancelled = { shared.isFile && shared.readText() == "second" }
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            threwCancellation = true
        }

        assertTrue("Expected CancellationException", threwCancellation)
        assertEquals("first", shared.readText())
        assertEquals("one", File(targetDir, "own1.png").readText())
        assertFalse(File(targetDir, "own2.png").exists())
        assertFalse(TexturePackInstaller.manifestFileFor(targetDir, 62L).exists())
        assertEquals(setOf("shared.png", "own1.png"), firstManifest!!.readLines().toSet())
        assertFalse(File(targetDir, ".staging_62.replaced").exists())
        assertTrue(secondZip.exists())
    }

    @Test
    fun `cancellation during zip extraction leaves no partial files in target dir`() = runTest {
        val zipFile = createZip(
            "cancel_pack.zip",
            mapOf(
                "tex1.png" to "data1",
                "tex2.png" to "data2"
            )
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 99L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        var threwCancellation = false
        try {
            installer.installForModFileOutcome(
                game = game,
                gameFile = modFile,
                sourceFile = zipFile,
                isCancelled = { true }
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            threwCancellation = true
        }

        assertTrue("Expected CancellationException", threwCancellation)
        assertTrue("Source zip must remain intact", zipFile.exists())
        val targetDir = File(saveDir, "User/Load/Textures/GZLE01")
        assertFalse("Target file should not exist", File(targetDir, "tex1.png").exists())
        assertFalse("Target staging dir should not exist", File(targetDir, ".staging_99").exists())
    }

    @Test
    fun `archive with colliding relative paths extracts without NoSuchFileException`() = runTest {
        val zipFile = createZip(
            "collide_cache.zip",
            mapOf(
                "main/game.htc" to "content_main",
                "backup/game.htc" to "content_backup"
            )
        )
        val game = testGame(titleId = "CZLE", platformSlug = "n64")
        val modFile = testModFile(id = 101L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val outcome = installer.installForModFileOutcome(
            game = game,
            gameFile = modFile,
            sourceFile = zipFile
        )

        assertTrue("Expected successful install despite duplicate entry names, got $outcome", outcome is TexturePackInstaller.ModInstallOutcome.Success)
        val cacheDir = File(systemDir, "Mupen64plus/cache")
        assertTrue("Destination file should exist", File(cacheDir, "game.htc").exists())
    }

    @Test
    fun `installs texture pack for external Dolphin and enables GFX ini`() = runTest {
        val extStorage = tempFolder.newFolder("mock_external")
        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { externalStorageRoots() } returns listOf(extStorage.absolutePath)
            every { isRestrictedPath(any()) } returns false
            every { exists(any()) } answers { File(firstArg<String>()).exists() }
            every { mkdirs(any()) } answers { File(firstArg<String>()).mkdirs() }
            every { writeBytes(any(), any()) } answers {
                val f = File(firstArg<String>())
                f.parentFile?.mkdirs()
                f.writeBytes(secondArg<ByteArray>())
                true
            }
        }

        val customInstaller = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir,
            fileAccessLayer = fal,
            cacheDir = tempFolder.newFolder("cache")
        )

        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "org.dolphinemu.dolphinemu"

        val zipFile = createZip(
            "dolphin_pack.zip",
            mapOf("tex.png" to "tex_data")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 55L, gameId = game.id, fileName = zipFile.name, localPath = zipFile.absolutePath)

        val outcome = customInstaller.installForModFileOutcome(
            game = game,
            gameFile = modFile,
            sourceFile = zipFile
        )

        assertTrue(outcome is TexturePackInstaller.ModInstallOutcome.Success)
        val targetDir = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01")
        assertTrue(File(targetDir, "tex.png").isFile)
        val gfxFile = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Config/GFX.ini")
        assertTrue(gfxFile.isFile)
        assertTrue(gfxFile.readText().contains("HiresTextures = True"))
    }

    @Test
    fun `installPendingModFilesForGame migrates textures when switching between built-in and external emulator without data loss`() = runTest {
        val extStorage = tempFolder.newFolder("mock_external_mig")
        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { externalStorageRoots() } returns listOf(extStorage.absolutePath)
            every { isRestrictedPath(any()) } returns false
            every { exists(any()) } answers { File(firstArg<String>()).exists() }
            every { mkdirs(any()) } answers { File(firstArg<String>()).mkdirs() }
            every { writeBytes(any(), any()) } answers {
                val f = File(firstArg<String>())
                f.parentFile?.mkdirs()
                f.writeBytes(secondArg<ByteArray>())
                true
            }
        }

        val customInstaller = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir,
            fileAccessLayer = fal,
            cacheDir = tempFolder.newFolder("cache_mig")
        )

        val game = testGame(titleId = "GZLE01")
        val builtinTargetDir = File(saveDir, "User/Load/Textures/GZLE01").apply { mkdirs() }
        val oldManifest = File(builtinTargetDir, ".argosy_mod_77.manifest").apply {
            writeText("tex_valuable.png")
        }
        val texFile = File(builtinTargetDir, "tex_valuable.png").apply { writeText("original_texture_data") }

        val modFile = testModFile(id = 77L, gameId = game.id, fileName = "pack.zip", localPath = oldManifest.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)

        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "org.dolphinemu.dolphinemu"

        val count = customInstaller.installPendingModFilesForGame(game.id)
        assertEquals(1, count)

        val externalTargetDir = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01")
        val migratedTex = File(externalTargetDir, "tex_valuable.png")
        assertTrue("Migrated texture file must exist in external directory", migratedTex.isFile)
        assertEquals("Texture data must remain 100% intact", "original_texture_data", migratedTex.readText())

        val newManifest = File(externalTargetDir, ".argosy_mod_77.manifest")
        assertTrue("New manifest must exist in external directory", newManifest.isFile)
        coVerify { gameFileDao.updateLocalPath(77L, newManifest.absolutePath, any()) }

        val gfxFile = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Config/GFX.ini")
        assertTrue("Dolphin GFX.ini must be created", gfxFile.isFile)
        assertTrue(gfxFile.readText().contains("HiresTextures = True"))

        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns EmulatorRegistry.BUILTIN_PACKAGE
        val updatedModFile = testModFile(id = 77L, gameId = game.id, fileName = "pack.zip", localPath = newManifest.absolutePath)
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(updatedModFile)

        val reverseCount = customInstaller.installPendingModFilesForGame(game.id)
        assertEquals(1, reverseCount)

        val restoredTex = File(builtinTargetDir, "tex_valuable.png")
        assertTrue("Restored texture file must exist back in builtin directory", restoredTex.isFile)
        assertEquals("Restored texture data must remain 100% intact", "original_texture_data", restoredTex.readText())
    }

    @Test
    fun `a migration cancelled partway restores a file it replaced in the new emulator folder`() = runTest {
        val extStorage = tempFolder.newFolder("mock_external_overwrite")
        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { externalStorageRoots() } returns listOf(extStorage.absolutePath)
            every { isRestrictedPath(any()) } returns false
            every { exists(any()) } answers { File(firstArg<String>()).exists() }
        }
        val customInstaller = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir,
            fileAccessLayer = fal,
            cacheDir = tempFolder.newFolder("cache_overwrite")
        )
        val game = testGame(titleId = "GZLE01")
        val builtinTargetDir = File(saveDir, "User/Load/Textures/GZLE01").apply { mkdirs() }
        val oldManifest = File(builtinTargetDir, ".argosy_mod_78.manifest").apply { writeText("tex_a.png\ntex_b.png") }
        File(builtinTargetDir, "tex_a.png").writeText("pack_a")
        File(builtinTargetDir, "tex_b.png").writeText("pack_b")
        val externalTargetDir = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01")
            .apply { mkdirs() }
        val playersOwn = File(externalTargetDir, "tex_a.png").apply { writeText("player_original") }

        val modFile = testModFile(id = 78L, gameId = game.id, fileName = "pack.zip", localPath = oldManifest.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "org.dolphinemu.dolphinemu"

        var threwCancellation = false
        try {
            customInstaller.installPendingModFilesForGame(
                game.id,
                isCancelled = { playersOwn.isFile && playersOwn.readText() == "pack_a" }
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
            threwCancellation = true
        }

        assertTrue("Expected CancellationException", threwCancellation)
        assertEquals("player_original", playersOwn.readText())
        assertFalse(File(externalTargetDir, "tex_b.png").exists())
        assertFalse(File(externalTargetDir, ".argosy_mod_78.manifest").exists())
        assertFalse(File(externalTargetDir, ".argosy_mod_78.manifest.replaced").exists())
        assertEquals("pack_a", File(builtinTargetDir, "tex_a.png").readText())
        assertEquals("pack_b", File(builtinTargetDir, "tex_b.png").readText())
        assertTrue(oldManifest.isFile)
    }

    @Test
    fun `a migration that cannot write keeps the source pack and its manifest`() = runTest {
        val sourceDir = tempFolder.newFolder("builtin_source")
        val manifest = File(sourceDir, ".argosy_mod_77.manifest").apply { writeText("a.png\nb.png") }
        val textureA = File(sourceDir, "a.png").apply { writeText("aaa") }
        val textureB = File(sourceDir, "b.png").apply { writeText("bbb") }
        val extStorage = tempFolder.newFolder("external_unwritable")

        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { externalStorageRoots() } returns listOf(extStorage.absolutePath)
            every { isRestrictedPath(any()) } answers {
                firstArg<String>().replace('\\', '/').contains("Android/data")
            }
            every { exists(any()) } returns false
            every { mkdirs(any()) } returns true
            var opened = 0
            every { getOutputStream(any()) } answers {
                opened++
                if (opened == 1) {
                    val file = File(firstArg<String>())
                    file.parentFile?.mkdirs()
                    FileOutputStream(file)
                } else {
                    null
                }
            }
            every { delete(any()) } answers { File(firstArg<String>()).delete() }
        }
        val customInstaller = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir,
            fileAccessLayer = fal,
            cacheDir = tempFolder.newFolder("cache_unwritable")
        )
        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 77L, gameId = game.id, fileName = "pack.zip", localPath = manifest.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns "org.dolphinemu.dolphinemu"

        val installed = customInstaller.installPendingModFilesForGame(game.id)

        assertEquals(0, installed)
        assertTrue("Source manifest must survive", manifest.isFile)
        assertTrue("Source textures must survive", textureA.isFile && textureB.isFile)
        coVerify(exactly = 0) { gameFileDao.updateLocalPath(77L, any(), any()) }
        assertEquals(listOf("pack.zip"), customInstaller.failedInstallNames(game.id))
        val strayCopies = extStorage.walkTopDown().filter { it.isFile }.toList()
        assertTrue("Partial copies must be rolled back: $strayCopies", strayCopies.isEmpty())
        TexturePackInstaller.clearFailedInstalls()
    }

    @Test
    fun `installPendingModFilesForGame migrates textures from restricted Scoped Storage path via FileAccessLayer`() = runTest {
        val extStorage = tempFolder.newFolder("mock_external_restricted")
        val oldRestrictedDir = File(extStorage, "Android/data/org.dolphinemu.dolphinemu/files/Load/Textures/GZLE01").apply { mkdirs() }
        val oldManifest = File(oldRestrictedDir, ".argosy_mod_99.manifest").apply {
            writeText("tex_scoped.png")
        }
        val oldTex = File(oldRestrictedDir, "tex_scoped.png").apply {
            writeText("scoped_texture_data")
        }

        val fal = mockk<com.nendo.argosy.data.storage.FileAccessLayer>(relaxed = true) {
            every { externalStorageRoots() } returns listOf(extStorage.absolutePath)
            every { isRestrictedPath(any()) } answers {
                firstArg<String>().contains("Android/data") || firstArg<String>().contains("Android\\data")
            }
            every { exists(any()) } answers { File(firstArg<String>()).exists() }
            every { readBytes(any()) } answers { File(firstArg<String>()).readBytes() }
            every { getInputStream(any()) } answers { File(firstArg<String>()).inputStream() }
            every { getOutputStream(any()) } answers {
                val f = File(firstArg<String>())
                f.parentFile?.mkdirs()
                FileOutputStream(f)
            }
            every { writeBytes(any(), any()) } answers {
                val f = File(firstArg<String>())
                f.parentFile?.mkdirs()
                f.writeBytes(secondArg<ByteArray>())
                true
            }
            every { delete(any()) } answers {
                File(firstArg<String>()).delete()
            }
            every { mkdirs(any()) } answers {
                File(firstArg<String>()).mkdirs()
            }
            every { listFiles(any()) } answers {
                val dir = File(firstArg<String>())
                dir.listFiles()?.map { f ->
                    com.nendo.argosy.data.storage.FileInfo(
                        path = f.absolutePath,
                        name = f.name,
                        isDirectory = f.isDirectory,
                        isFile = f.isFile,
                        size = f.length(),
                        lastModified = f.lastModified()
                    )
                } ?: emptyList()
            }
        }

        val customInstaller = TexturePackInstaller(
            builtinSaveBase = builtinSaveBase,
            builtinCoreResolver = builtinCoreResolver,
            emulatorResolver = emulatorResolver,
            titleIdExtractor = titleIdExtractor,
            baseRomFileResolver = baseRomFileResolver,
            platformFolderResolver = platformFolderResolver,
            gameDao = gameDao,
            gameDiscDao = gameDiscDao,
            gameFileDao = gameFileDao,
            systemDir = systemDir,
            fileAccessLayer = fal,
            cacheDir = tempFolder.newFolder("cache_restricted_mig")
        )

        val game = testGame(titleId = "GZLE01")
        val modFile = testModFile(id = 99L, gameId = game.id, fileName = "scoped_pack.zip", localPath = oldManifest.absolutePath)
        coEvery { gameDao.getById(game.id) } returns game
        coEvery { gameFileDao.getModFilesForGame(game.id) } returns listOf(modFile)
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns EmulatorRegistry.BUILTIN_PACKAGE

        val count = customInstaller.installPendingModFilesForGame(game.id)
        assertEquals(1, count)

        io.mockk.verify { fal.readBytes(oldManifest.absolutePath) }
        io.mockk.verify { fal.getInputStream(oldTex.absolutePath) }
        io.mockk.verify { fal.delete(oldManifest.absolutePath) }
        io.mockk.verify { fal.delete(oldTex.absolutePath) }
        io.mockk.verify { fal.delete(oldRestrictedDir.absolutePath) }

        val builtinTargetDir = File(saveDir, "User/Load/Textures/GZLE01")
        val restoredTex = File(builtinTargetDir, "tex_scoped.png")
        assertTrue("Restored texture file must exist in builtin target dir", restoredTex.isFile)
        assertEquals("scoped_texture_data", restoredTex.readText())

        val restoredManifest = File(builtinTargetDir, ".argosy_mod_99.manifest")
        assertTrue("Restored manifest must exist in builtin target dir", restoredManifest.isFile)
        coVerify { gameFileDao.updateLocalPath(99L, restoredManifest.absolutePath, any()) }
    }

    private fun createZip(name: String, entries: Map<String, String>): File {
        val file = tempFolder.newFile(name)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((entryName, content) in entries) {
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }

    private fun testGame(titleId: String, platformSlug: String = "ngc"): GameEntity {
        val rom = File(tempFolder.root, "test_rom.iso").apply {
            if (!exists()) writeText("dummy_rom_data")
        }
        return GameEntity(
            id = 1L,
            platformId = 10L,
            platformSlug = platformSlug,
            title = "The Legend of Zelda: The Wind Waker",
            sortTitle = "legend of zelda",
            localPath = rom.absolutePath,
            rommId = 100L,
            igdbId = null,
            source = GameSource.ROMM_SYNCED,
            titleId = titleId,
            saveId = titleId
        )
    }

    private fun testModFile(
        id: Long,
        gameId: Long,
        fileName: String,
        localPath: String?
    ): GameFileEntity = GameFileEntity(
        id = id,
        gameId = gameId,
        rommFileId = 500L + id,
        romId = 100L,
        fileName = fileName,
        filePath = "mods/$fileName",
        category = "mod",
        fileSize = 1024L,
        localPath = localPath,
        isLaunchTarget = false
    )
}
