package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.emulator.BuiltinSaveBase
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMSave
import com.nendo.argosy.data.repository.ConflictDetector
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncResult
import com.nendo.argosy.data.repository.SaveUploader
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import com.nendo.argosy.data.sync.SavePathResolver
import com.nendo.argosy.data.sync.SaveUnitResolver
import com.nendo.argosy.util.AppPaths
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import kotlin.io.path.createTempDirectory

class SigilSharedContainerRouteTest {

    private lateinit var tempDir: File
    private lateinit var memcards: File

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val saveUnitResolver = mockk<SaveUnitResolver>(relaxed = true)
    private val connectionManager = mockk<RomMConnectionManager>(relaxed = true)
    private val fal = mockk<FileAccessLayer>(relaxed = true)
    private val saveLocator = mockk<SigilSaveLocator>()

    private lateinit var handler: SigilSaveHandler

    @Before
    fun setUp() {
        tempDir = createTempDirectory("sigil_shared_container").toFile()
        val filesDir = File(tempDir, "files")
        memcards = File(SigilSaveHandler.pcsx2MemcardRoot(AppPaths.libretroSystemDir(filesDir))).apply { mkdirs() }
        File(memcards, "Mcd001.ps2").writeBytes(WHOLE_CARD)

        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
        every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
        every { fal.lastModified(any()) } answers { File(firstArg<String>()).lastModified() }
        every { connectionManager.snapshotsEnabled() } returns false
        coEvery { gameDao.getById(GAME_ID) } returns GAME
        coEvery { emulatorResolver.getEmulatorPackageForGame(any(), any(), any()) } returns null
        coEvery { saveUnitResolver.layoutFor(GAME, BUILTIN, null) } returns "armsx2"
        coEvery { saveUnitResolver.optionsFor(any(), any()) } returns emptyMap()

        handler = SigilSaveHandler(
            context = context,
            gameDao = gameDao,
            emulatorResolver = emulatorResolver,
            emulatorSaveConfigRepository = mockk(relaxed = true),
            switchProfileParser = mockk(relaxed = true),
            titleDbRepository = mockk(relaxed = true),
            emulatorConfigDao = mockk(relaxed = true),
            fal = fal,
            connectionManager = { connectionManager },
            saveUnitResolver = saveUnitResolver,
            builtinSaveBase = mockk<BuiltinSaveBase>(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            sigilSyncStateDao = mockk(relaxed = true),
            syncPreferencesRepository = mockk(relaxed = true),
            cartFeatureScanner = mockk(relaxed = true),
            saveLocator = saveLocator
        )
        every { saveLocator.accessPaths(any()) } answers { listOf(firstArg<SigilRoute>().root) }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun located(shared: List<String>, own: List<String>) {
        every { saveLocator.locate(any(), any(), any()) } returns SigilSaveLocation(shared, own)
    }

    @Test
    fun `a built-in ARMSX2 game whose saves sit on the shared card routes through Sigil on a server without Sigil units`() = runTest {
        located(shared = listOf("Mcd001.ps2"), own = emptyList())

        val route = handler.route(GAME_ID, BUILTIN)

        assertEquals("pcsx2", route?.layout)
        assertEquals(memcards.absolutePath, route?.root)
        assertEquals(true, route?.libretro)
    }

    @Test
    fun `routing prepares save access on the layout's folders, not the whole root`() = runTest {
        located(shared = listOf("Mcd001.ps2"), own = emptyList())
        val layoutFolder = "${memcards.absolutePath}/cards"
        every { saveLocator.accessPaths(any()) } returns listOf(layoutFolder)

        handler.route(GAME_ID, BUILTIN)

        verify { fal.prepareSaveAccess(layoutFolder) }
        verify(exactly = 0) { fal.prepareSaveAccess(memcards.absolutePath) }
    }

    @Test
    fun `the upload of a game on the shared card is its Sigil unit, not a missing save and not the whole card`() = runTest {
        located(shared = listOf("Mcd001.ps2"), own = emptyList())
        val sigil = spyk(handler)
        coEvery { sigil.collect(GAME_ID, BUILTIN, any()) } returns
            SigilCollect.Found(UNIT, "$ROM_STEM.ps2", "unit-hash", "unit-identity", root = memcards.absolutePath)
        val romMApi = mockk<RomMApi>(relaxed = true)
        var uploadedName: String? = null
        var uploadedSize = -1L
        coEvery {
            romMApi.uploadSaveWithDevice(any(), any(), any(), any(), any(), any(), any(), any<MultipartBody.Part>(), any())
        } answers {
            val part = arg<MultipartBody.Part>(7)
            uploadedName = part.headers?.get("Content-Disposition")
            uploadedSize = part.body.contentLength()
            Response.success(RomMSave(id = 5L, romId = ROMM_ID, userId = 1L, emulator = BUILTIN, fileName = "$ROM_STEM.ps2", updatedAt = "2026-10-12T00:00:00Z", contentHash = "unit-hash"))
        }

        val result = uploader(sigil, romMApi).uploadSave(GAME_ID, BUILTIN, channelName = null)

        assertTrue("expected an upload but got $result", result is SaveSyncResult.Success && !result.noOp)
        assertTrue("uploaded as $uploadedName", uploadedName?.contains("filename=\"$ROM_STEM.ps2\"") == true)
        assertEquals(UNIT.size.toLong(), uploadedSize)
        coVerify(exactly = 1) { sigil.collect(GAME_ID, BUILTIN, any()) }
    }

    @Test
    fun `a whole card downloaded for a game on the shared card restores through Sigil, never written raw`() = runTest {
        located(shared = listOf("Mcd001.ps2"), own = emptyList())
        val sigil = spyk(handler)
        coEvery { sigil.restoreInto(any(), any(), any()) } returns SigilRestore.Restored(hardcoreMarker = false)
        val download = File(tempDir, "download.ps2").apply { writeBytes(SERVER_CARD) }

        val restored = sigil.restore(GAME_ID, download, BUILTIN)

        assertEquals(SigilRestore.Restored(hardcoreMarker = false), restored)
        coVerify(exactly = 1) {
            sigil.restoreInto(
                match { it.layout == "pcsx2" && it.root == memcards.absolutePath },
                GAME_ID,
                match { it.contentEquals(SERVER_CARD) }
            )
        }
        assertTrue(File(memcards, "Mcd001.ps2").readBytes().contentEquals(WHOLE_CARD))
    }

    @Test
    fun `a game on its own card keeps the legacy path on a server without Sigil units, with or without a shared card beside it`() = runTest {
        for (shared in listOf(emptyList<String>(), listOf("Mcd001.ps2"))) {
            located(shared = shared, own = listOf("$ROM_STEM.ps2"))

            assertNull(handler.route(GAME_ID, BUILTIN))
            assertEquals(SigilCollect.NotRouted, handler.collect(GAME_ID, BUILTIN))
            assertEquals(SigilRestore.NotRouted, handler.restore(GAME_ID, File(tempDir, "download.ps2"), BUILTIN))
        }
    }

    @Test
    fun `a root holding no shared container keeps the legacy path on a server without Sigil units`() = runTest {
        located(shared = emptyList(), own = emptyList())

        assertNull(handler.route(GAME_ID, BUILTIN))
    }

    @Test
    fun `a server with Sigil units routes without reading the root`() = runTest {
        every { connectionManager.snapshotsEnabled() } returns true

        assertEquals("pcsx2", handler.route(GAME_ID, BUILTIN)?.layout)
        verify(exactly = 0) { saveLocator.locate(any(), any(), any()) }
    }

    @Test
    fun `a game's source paths are its own files under the root, never the shared card or the root`() = runTest {
        every { connectionManager.snapshotsEnabled() } returns true
        val context = SaveContext(
            config = com.nendo.argosy.data.emulator.SavePathRegistry.getConfig(BUILTIN)!!,
            romPath = null,
            saveId = null,
            emulatorPackage = null,
            gameId = GAME_ID,
            gameTitle = GAME.title,
            platformSlug = GAME.platformSlug,
            emulatorId = BUILTIN,
            localSavePath = memcards.absolutePath
        )

        located(shared = listOf("Mcd001.ps2"), own = listOf("$ROM_STEM.ps2"))
        assertEquals(listOf(File(memcards, "$ROM_STEM.ps2").path), handler.sourcePathsFor(memcards.absolutePath, context))

        located(shared = listOf("Mcd001.ps2"), own = emptyList())
        assertEquals(emptyList<String>(), handler.sourcePathsFor(memcards.absolutePath, context))
    }

    @Test
    fun `a folder card on disk is listed as its folder so the layout's shared card name finds it`() {
        val listing = listOf("memcards/Mcd001.ps2/_pcsx2_superblock", "memcards/Mcd001.ps2/BASLUS-20642SYS/icon.sys", "Mcd002.ps2")

        assertEquals(
            listing + listOf("memcards", "memcards/Mcd001.ps2", "memcards/Mcd001.ps2/BASLUS-20642SYS"),
            SigilSaveLocator.withFolders(listing)
        )
    }

    private fun uploader(sigil: SigilSaveHandler, romMApi: RomMApi): SaveUploader {
        val apiClient = mockk<SaveSyncApiClient>(relaxed = true)
        every { apiClient.getApi() } returns romMApi
        every { apiClient.getDeviceId() } returns "device-1"
        coEvery { apiClient.resolveCoreForGame(any<GameEntity>(), any()) } returns "armsx2"
        coEvery { apiClient.checkSavesForGame(any(), any()) } returns emptyList()
        val registry = mockk<PlatformSaveHandlerRegistry>(relaxed = true)
        every { registry.sigil } returns sigil
        every { registry.isValidCachedSavePath(any(), any()) } returns true
        val savePathResolver = mockk<SavePathResolver>(relaxed = true)
        coEvery { savePathResolver.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            memcards.absolutePath
        val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        val saveArchiver = mockk<SaveArchiver>(relaxed = true)
        every { saveArchiver.calculateContentHash(any()) } returns "unit-hash"
        val conflictDetector = mockk<ConflictDetector>(relaxed = true)
        every { conflictDetector.pickLatestServerSave(any(), any(), any(), any()) } returns null
        every { conflictDetector.detectUploadConflict(any(), any(), any(), any(), any(), any()) } returns null
        return SaveUploader(
            context = context,
            saveSyncDao = saveSyncDao,
            saveCacheDao = mockk(relaxed = true),
            emulatorResolver = emulatorResolver,
            gameDao = gameDao,
            titleDbRepository = mockk(relaxed = true),
            saveArchiver = saveArchiver,
            savePathResolver = savePathResolver,
            fal = fal,
            saveHandlerRegistry = registry,
            apiClient = { apiClient },
            conflictDetector = conflictDetector,
            saveCacheManager = { mockk<SaveCacheManager>(relaxed = true) },
            syncPreferencesRepository = mockk(relaxed = true),
            emulatorSaveConfigRepository = mockk(relaxed = true)
        )
    }

    private companion object {
        const val GAME_ID = 12L
        const val ROMM_ID = 44L
        const val BUILTIN = EmulatorRegistry.BUILTIN_ID
        const val ROM_STEM = "Champions of Norrath (USA)"
        val WHOLE_CARD = ByteArray(1024) { 0x11 }
        val SERVER_CARD = ByteArray(1024) { 0x22 }
        val UNIT = ByteArray(512) { 0x33 }
        val GAME = GameEntity(
            id = GAME_ID,
            title = "Champions of Norrath",
            sortTitle = "champions of norrath",
            platformId = 2L,
            platformSlug = "ps2",
            rommId = ROMM_ID,
            igdbId = null,
            localPath = "/roms/$ROM_STEM.chd",
            source = GameSource.ROMM_SYNCED
        )
    }
}
