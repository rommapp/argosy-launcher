package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PendingSyncQueueDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.PendingSyncQueueEntity
import com.nendo.argosy.data.local.entity.SyncPriority
import com.nendo.argosy.data.local.entity.SyncType
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferences
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import com.nendo.argosy.data.sync.ScreenshotPayload
import com.nendo.argosy.data.sync.ScreenshotUploadLedger
import com.nendo.argosy.data.sync.SyncPayloadCodec
import com.nendo.argosy.util.PermissionHelper
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.time.Instant

class SessionScreenshotScannerTest {

    private val gameDao: GameDao = mockk()
    private val pendingSyncQueueDao: PendingSyncQueueDao = mockk(relaxed = true)
    private val fileAccessLayer: FileAccessLayer = mockk()
    private val folderRegistry: ScreenshotFolderRegistry = mockk()
    private val ledger: ScreenshotUploadLedger = mockk()
    private val connectionManager: RomMConnectionManager = mockk()
    private val permissionHelper: PermissionHelper = mockk()
    private val codec = SyncPayloadCodec(Moshi.Builder().build())
    private val prefs = MutableStateFlow(SyncPreferences(uploadScreenshotsEnabled = true, rommUserId = OWNER))
    private val tempFilesDir: File = Files.createTempDirectory("scanner").toFile()
    private val inserted = mutableListOf<PendingSyncQueueEntity>()

    private val game = GameEntity(
        id = GAME_ID,
        title = "Blue",
        sortTitle = "blue",
        platformId = 1L,
        platformSlug = "gb",
        rommId = ROMM_ID,
        igdbId = null,
        localPath = "/storage/roms/blue.gb",
        source = GameSource.ROMM_SYNCED
    )

    @Before
    fun setup() {
        coEvery { gameDao.getById(GAME_ID) } returns game
        every { folderRegistry.folders() } returns listOf(SYSTEM_DIR, BUILTIN_DIR)
        every { folderRegistry.builtinFolder() } returns BUILTIN_DIR
        every { permissionHelper.foregroundSpans(any(), any(), any(), any()) } returns listOf(START..END)
        every { fileAccessLayer.listFiles(any()) } returns emptyList()
        every { fileAccessLayer.getInputStream(any()) } answers { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        every { ledger.contains(any(), any(), any(), any()) } returns false
        every { connectionManager.isConnected() } returns false
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns emptyList()
        coEvery { pendingSyncQueueDao.insert(any()) } answers {
            inserted += firstArg<PendingSyncQueueEntity>()
            inserted.size.toLong()
        }
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `queues images modified inside the session window and skips the rest`() = runTest {
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(
            image(SYSTEM_DIR, "before.png", START - 1),
            image(SYSTEM_DIR, "at_start.png", START),
            image(SYSTEM_DIR, "during.jpg", START + 5_000),
            image(SYSTEM_DIR, "after.png", END + 1),
            image(SYSTEM_DIR, "notes.txt", START + 10)
        )
        every { fileAccessLayer.listFiles(BUILTIN_DIR) } returns listOf(
            image(BUILTIN_DIR, "argosy_1.png", END)
        )

        val queued = scan()

        assertEquals(3, queued)
        val sources = inserted.map { codec.decodeScreenshot(it.payloadJson)!!.sourcePath }
        assertEquals(
            listOf("$SYSTEM_DIR/at_start.png", "$SYSTEM_DIR/during.jpg", "$BUILTIN_DIR/argosy_1.png"),
            sources
        )
        inserted.forEach { row ->
            assertEquals(SyncType.SCREENSHOT, row.syncType)
            assertEquals(SyncPriority.PROPERTY, row.priority)
            assertEquals(ROMM_ID, row.rommId)
            assertEquals(OWNER, row.ownerUserId)
        }
    }

    @Test
    fun `the queued copy keeps the source file's own name`() = runTest {
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(
            image(SYSTEM_DIR, "Screenshot_20261109-101500.png", START + 1)
        )

        scan()

        val copy = File(codec.decodeScreenshot(inserted.single().payloadJson)!!.localPath)
        assertEquals("Screenshot_20261109-101500.png", copy.name)
        assertTrue(copy.exists())
        assertTrue(copy.absolutePath.startsWith(tempFilesDir.absolutePath))
    }

    @Test
    fun `a source already queued for the game is not queued again`() = runTest {
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))
        coEvery { pendingSyncQueueDao.getByGameId(GAME_ID) } returns listOf(
            PendingSyncQueueEntity(
                id = 9L,
                gameId = GAME_ID,
                rommId = ROMM_ID,
                syncType = SyncType.SCREENSHOT,
                priority = SyncPriority.PROPERTY,
                payloadJson = codec.encode(ScreenshotPayload("/x/a.png", "$SYSTEM_DIR/a.png", START + 1))
            )
        )

        val queued = scan()

        assertEquals(0, queued)
        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
    }

    @Test
    fun `a source an earlier scan already uploaded is not copied again`() = runTest {
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))
        every { ledger.contains(OWNER, ROMM_ID, "$SYSTEM_DIR/a.png", START + 1) } returns true

        val queued = scan()

        assertEquals(0, queued)
        io.mockk.verify(exactly = 0) { fileAccessLayer.getInputStream(any()) }
    }

    @Test
    fun `a source that cannot be read is not queued, so the next scan tries it again`() = runTest {
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))
        every { fileAccessLayer.getInputStream(any()) } returns null

        val queued = scan()

        assertEquals(0, queued)
        coVerify(exactly = 0) { pendingSyncQueueDao.insert(any()) }
    }

    @Test
    fun `nothing is scanned with the upload toggle off`() = runTest {
        prefs.value = prefs.value.copy(uploadScreenshotsEnabled = false)
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))

        val queued = scan()

        assertEquals(0, queued)
        io.mockk.verify(exactly = 0) { fileAccessLayer.listFiles(any()) }
    }

    @Test
    fun `a screenshot taken while the emulator was in the background is not queued`() = runTest {
        every { permissionHelper.foregroundSpans(any(), EMULATOR, START, END) } returns
            listOf(START..START + 10_000, START + 40_000..END)
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(
            image(SYSTEM_DIR, "in_game.png", START + 5_000),
            image(SYSTEM_DIR, "other_app.png", START + 20_000),
            image(SYSTEM_DIR, "back_in_game.png", START + 50_000)
        )

        scan()

        assertEquals(
            listOf("$SYSTEM_DIR/in_game.png", "$SYSTEM_DIR/back_in_game.png"),
            inserted.map { codec.decodeScreenshot(it.payloadJson)!!.sourcePath }
        )
    }

    @Test
    fun `without usage access an external session queues nothing`() = runTest {
        every { permissionHelper.foregroundSpans(any(), any(), any(), any()) } returns null
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))

        assertEquals(0, scan())
    }

    @Test
    fun `without usage access a built-in session reads only Argosy's folder`() = runTest {
        every { permissionHelper.foregroundSpans(any(), any(), any(), any()) } returns null
        every { fileAccessLayer.listFiles(SYSTEM_DIR) } returns listOf(image(SYSTEM_DIR, "a.png", START + 1))
        every { fileAccessLayer.listFiles(BUILTIN_DIR) } returns listOf(image(BUILTIN_DIR, "argosy_1.png", START + 1))

        assertEquals(1, scan(EmulatorRegistry.BUILTIN_PACKAGE))
        assertEquals(
            "$BUILTIN_DIR/argosy_1.png",
            codec.decodeScreenshot(inserted.single().payloadJson)!!.sourcePath
        )
    }

    private suspend fun scan(emulatorPackage: String = EMULATOR) =
        scanner().queueSessionScreenshots(GAME_ID, emulatorPackage, Instant.ofEpochMilli(START), Instant.ofEpochMilli(END))

    private fun image(dir: String, name: String, modifiedAt: Long) = FileInfo(
        path = "$dir/$name",
        name = name,
        isDirectory = false,
        isFile = true,
        size = 3L,
        lastModified = modifiedAt
    )

    private fun scanner() = SessionScreenshotScanner(
        context = mockk(relaxed = true) { every { filesDir } returns tempFilesDir },
        gameDao = gameDao,
        pendingSyncQueueDao = pendingSyncQueueDao,
        payloadCodec = codec,
        syncPreferencesRepository = mockk { every { preferences } returns prefs },
        connectionManager = dagger.Lazy { connectionManager },
        fileAccessLayer = fileAccessLayer,
        folderRegistry = folderRegistry,
        ledger = ledger,
        permissionHelper = permissionHelper
    )

    private companion object {
        const val GAME_ID = 4L
        const val ROMM_ID = 400L
        const val OWNER = 9L
        const val EMULATOR = "com.flycast.emulator"
        const val START = 1_700_000_000_000L
        const val END = START + 60_000L
        const val SYSTEM_DIR = "/storage/emulated/0/DCIM/Screenshots"
        const val BUILTIN_DIR = "/storage/emulated/0/Pictures/Argosy"
    }
}
