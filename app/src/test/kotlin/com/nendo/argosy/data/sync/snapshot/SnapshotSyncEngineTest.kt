package com.nendo.argosy.data.sync.snapshot

import android.content.Context
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SnapshotChannelDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SnapshotChannelEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMRom
import com.nendo.argosy.data.remote.romm.RomMRomFile
import com.nendo.argosy.data.remote.romm.RomMChannel
import com.nendo.argosy.data.remote.romm.RomMSnapshot
import com.nendo.argosy.data.remote.romm.RomMSnapshotChannel
import com.nendo.argosy.data.remote.romm.RomMSnapshotSave
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilRoute
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SnapshotSyncEngineTest {

    private val api = mockk<RomMApi>()
    private val apiClient = mockk<SaveSyncApiClient>()
    private val gameDao = mockk<GameDao>()
    private val channelDao = mockk<SnapshotChannelDao>(relaxed = true)
    private val sigil = mockk<SigilSaveHandler>()
    private val prefs = mockk<SyncPreferencesRepository>()
    private val moshi = Moshi.Builder().build()
    private val snapshotJson = moshi.adapter(RomMSnapshot::class.java)

    private val game = GameEntity(
        id = GAME_ID,
        title = "Lunar",
        sortTitle = "lunar",
        platformId = 1L,
        platformSlug = "scd",
        rommId = ROMM_ID,
        igdbId = null,
        localPath = "/roms/scd/Lunar (USA).cue",
        source = GameSource.ROMM_SYNCED
    )
    private val local = SigilCollect.Found(byteArrayOf(1, 2, 3), "backup.ram", "content-a", "identity-a")
    private val stored = slot<SnapshotChannelEntity>()
    private val activeSaves = mockk<com.nendo.argosy.data.repository.ActiveSaveRepository>(relaxed = true)

    private lateinit var engine: SnapshotSyncEngine

    @Before
    fun setUp() {
        every { apiClient.getCapabilities() } returns RomMCapabilities.from("5.5.0", snapshotsEnabled = true)
        every { apiClient.getApi() } returns api
        every { apiClient.getDeviceId() } returns DEVICE
        coEvery { gameDao.getById(GAME_ID) } returns game
        coEvery { prefs.getRommUserId() } returns 3L
        coEvery { sigil.route(GAME_ID, EMULATOR) } returns SigilRoute("genesis_plus_gx", "/saves", null, null)
        coEvery { sigil.collect(GAME_ID, EMULATOR, any()) } returns local
        val rom = mockk<RomMRom>()
        every { rom.files } returns listOf(
            RomMRomFile(FILE_ID, ROMM_ID, "Lunar (USA).cue", "p", 1L, "p/Lunar (USA).cue"),
            RomMRomFile(FILE_ID + 1, ROMM_ID, "Lunar (USA) (Track 1).bin", "p", 1L, "p/t1")
        )
        coEvery { api.getRom(ROMM_ID) } returns Response.success(rom)
        coEvery { channelDao.upsert(capture(stored)) } returns Unit
        every { context.cacheDir } returns tempDir.root
        every { archiver.hasHardcoreTrailer(any()) } returns false
        every { archiver.readBytesWithoutTrailer(any()) } returns null
        every { archiver.calculateContentHash(any()) } answers { "md5-of-" + firstArg<java.io.File>().readBytes().size }
        engine = SnapshotSyncEngine(
            context = context,
            gameDao = gameDao,
            channelDao = channelDao,
            saveCacheDao = saveCacheDao,
            sigilSaveHandler = sigil,
            saveCacheManager = dagger.Lazy { cacheManager },
            saveDownloader = dagger.Lazy { downloader },
            apiClient = dagger.Lazy { apiClient },
            emulatorResolver = mockk(relaxed = true),
            saveArchiver = archiver,
            syncPreferencesRepository = prefs,
            pusher = SnapshotPusher(moshi),
            fileResolver = SnapshotFileResolver(),
            saveScreenshots = screenshots,
            builtinCoreResolver = coreResolver,
            statePaths = statePaths,
            emulatorStamper = stamper,
            activeSaveRepository = activeSaves
        )
        coEvery { stamper.stampFor(any(), any()) } returns SnapshotEmulatorStamp("libretro", null, "genesis_plus_gx", "2026-09-30")
        coEvery { coreResolver.resolveCoreId(any(), any(), any()) } returns null
        every { screenshots.recentFor(any(), any()) } returns null
        every { screenshots.keepForSnapshot(any(), any()) } returns true
    }

    @get:org.junit.Rule
    val tempDir = org.junit.rules.TemporaryFolder()
    private val context = mockk<Context>(relaxed = true)
    private val archiver = mockk<com.nendo.argosy.data.sync.SaveArchiver>()
    private val cacheManager = mockk<com.nendo.argosy.data.repository.SaveCacheManager>(relaxed = true)
    private val downloader = mockk<com.nendo.argosy.data.repository.SaveDownloader>()
    private val screenshots = mockk<com.nendo.argosy.hardware.SaveScreenshotCapture>()
    private val coreResolver = mockk<com.nendo.argosy.data.emulator.BuiltinCoreResolver>()
    private val statePaths = mockk<com.nendo.argosy.data.emulator.LibretroStatePathResolver>()
    private val stamper = mockk<SnapshotEmulatorStamper>()

    @Test
    fun `a push reports the emulator, core and core build that wrote the save`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)

        val manifest = manifestOf(parts.captured)
        assertEquals("libretro", manifest.getString("emulator"))
        assertEquals("genesis_plus_gx", manifest.getString("core"))
        assertEquals("2026-09-30", manifest.getString("core_version"))
        assertTrue(!manifest.has("emulator_version"))
    }
    private val saveCacheDao = mockk<com.nendo.argosy.data.local.dao.SaveCacheDao>(relaxed = true)

    @Test
    fun `a built-in push carries the auto state and its screenshot in the bank`() = runBlocking {
        val dir = tempDir.newFolder("states")
        val auto = java.io.File(dir, "Lunar (USA).state.auto").apply { writeBytes(ByteArray(64)) }
        val autoShot = java.io.File(dir, "Lunar (USA).state.auto.png").apply { writeBytes(byteArrayOf(7)) }
        coEvery { coreResolver.resolveCoreId(GAME_ID, any(), any()) } returns "genesis_plus_gx"
        coEvery { statePaths.liveStateBaseDir(GAME_ID) } returns dir
        every { statePaths.liveStateFile(dir, "Lunar (USA)", -1) } returns auto
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)

        val names = parts.captured.map { it.headers?.get("Content-Disposition")?.substringAfter("name=\"")?.substringBefore('"') }
        assertTrue(names.containsAll(listOf("state:genesis_plus_gx:auto", "state:genesis_plus_gx:auto:screenshot")))
        assertEquals("md5-of-64", manifestOf(parts.captured).getJSONObject("states").getJSONObject("genesis_plus_gx").getString("auto"))
        verify { screenshots.keepForSnapshot(41, autoShot) }
    }

    @Test
    fun `a hardcore push carries no states`() = runBlocking {
        coEvery { coreResolver.resolveCoreId(GAME_ID, any(), any()) } returns "genesis_plus_gx"
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null, isHardcore = true)

        assertTrue(!manifestOf(parts.captured).has("states"))
    }

    @Test
    fun `the screen captured at the save travels with the push and is dropped once it landed`() = runBlocking {
        val shot = tempDir.newFile("5.jpg").apply { writeBytes(byteArrayOf(1, 2)) }
        every { screenshots.recentFor(GAME_ID, any()) } returns shot
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)

        val names = parts.captured.map { it.headers?.get("Content-Disposition")?.substringAfter("name=\"")?.substringBefore('"') }
        assertEquals(listOf("manifest", "save", "save_screenshot"), names)
        verify { screenshots.keepForSnapshot(41, shot) }
        assertTrue(!shot.exists())
    }

    private fun snapshot(id: Long, save: String?) = RomMSnapshot(
        id = id,
        digest = "sha256:$id",
        createdAt = "2026-10-05T10:00:00Z",
        channel = RomMSnapshotChannel(CHANNEL, "default", romFileId = FILE_ID),
        save = save?.let { RomMSnapshotSave(id * 10, it, it) }
    )

    private fun manifestOf(parts: List<MultipartBody.Part>): JSONObject {
        val buffer = Buffer()
        parts.first().body.writeTo(buffer)
        return JSONObject(buffer.readUtf8())
    }

    @Test
    fun `a first save on a file with no channel pushes a new default channel expecting null`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        val result = engine.sync(GAME_ID, EMULATOR, null)

        assertEquals(SnapshotSyncResult.Pushed(41), result)
        val manifest = manifestOf(parts.captured)
        assertEquals(FILE_ID, manifest.getLong("rom_file_id"))
        assertEquals("default", manifest.getString("label"))
        assertTrue(manifest.isNull("expected_current_id"))
        assertEquals("content-a", manifest.getJSONObject("save").getString("hash"))
        assertEquals("neutral", manifest.getJSONObject("save").getString("format"))
        assertTrue("states carry from the parent", !manifest.has("states"))
        assertEquals(41L, stored.captured.heldSnapshotId)
        assertEquals("identity-a", stored.captured.heldSaveIdentityHash)
        assertEquals(manifest.getString("channel_id"), stored.captured.channelId)
    }

    private fun channelsOf(snapshots: List<RomMSnapshot>): Response<List<RomMChannel>> =
        Response.success(snapshots.map { snap ->
            RomMChannel(id = snap.channel!!.id, label = snap.channel!!.label, currentSnapshotId = snap.id, romFileId = FILE_ID, current = snap)
        })

    @Test
    fun `a first push joins the file's empty default channel instead of creating one`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(listOf(
            RomMChannel(id = "migrated", label = "default", romFileId = FILE_ID),
            RomMChannel(id = "speedrun", label = "Speedrun", romFileId = FILE_ID),
            RomMChannel(id = "someone-else", label = "default", isOwn = false, isPublic = true, romFileId = FILE_ID)
        ))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(41), engine.sync(GAME_ID, EMULATOR, null))
        val manifest = manifestOf(parts.captured)
        assertEquals("migrated", manifest.getString("channel_id"))
        assertTrue("an existing channel takes no label", !manifest.has("label"))
        assertTrue(manifest.isNull("expected_current_id"))
    }

    @Test
    fun `a dirty save on top of the held current pushes expecting it`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(42), engine.sync(GAME_ID, EMULATOR, null))
        val manifest = manifestOf(parts.captured)
        assertEquals(41L, manifest.getLong("expected_current_id"))
        assertEquals(CHANNEL, manifest.getString("channel_id"))
        assertTrue(!manifest.has("label"))
    }

    @Test
    fun `another device moving the channel first comes back as a conflict`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"current":{"id":43,"digest":"sha256:43"}}""".toResponseBody())

        val result = engine.sync(GAME_ID, EMULATOR, null) as SnapshotSyncResult.Conflict
        assertEquals(43L, result.currentId)
        coVerify(exactly = 0) { channelDao.upsert(any()) }
    }

    @Test
    fun `holding current with the same save does nothing`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "content-a")))

        assertEquals(SnapshotSyncResult.UpToDate, engine.sync(GAME_ID, EMULATOR, null))
        coVerify(exactly = 0) { api.pushSnapshot(any(), any()) }
    }

    @Test
    fun `a hardcore current refuses a softcore push until approved`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"hardcore_downgrade":true}""".toResponseBody())

        assertEquals(SnapshotSyncResult.HardcoreDowngrade(41), engine.sync(GAME_ID, EMULATOR, null))
    }

    @Test
    fun `an offline chain pushes each cached save on top of the one before`(): Unit = runBlocking {
        val heldAfterFirst = SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 44, "d", "c1", "c1", 0)
        coEvery { channelDao.get(3L, GAME_ID, "default") } returnsMany listOf(
            SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old", 0),
            heldAfterFirst
        )
        coEvery { api.listChannels(listOf(FILE_ID)) } returnsMany listOf(
            channelsOf(listOf(snapshot(43, "theirs"))),
            channelsOf(listOf(snapshot(44, "c1")))
        )
        val pushes = mutableListOf<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(pushes)) } returnsMany listOf(
            Response.success(201, snapshotJson.toJson(snapshot(44, "c1")).toResponseBody()),
            Response.success(201, snapshotJson.toJson(snapshot(45, "c2")).toResponseBody())
        )
        val unit = kotlin.io.path.createTempFile(suffix = ".ram").toFile().apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, unit, onTopOfCurrent = true, cacheId = null)
        engine.pushCached(GAME_ID, EMULATOR, null, unit, onTopOfCurrent = false, cacheId = null)

        assertEquals(43L, manifestOf(pushes[0]).getLong("expected_current_id"))
        assertEquals(44L, manifestOf(pushes[1]).getLong("expected_current_id"))
        unit.delete()
    }

    private fun cachedRow(id: Long, format: String?, hash: String = "content-a", channel: String? = "autosave") = SaveCacheEntity(
        id = id,
        gameId = GAME_ID,
        emulatorId = EMULATOR,
        cachedAt = java.time.Instant.parse("2026-10-05T09:00:00Z"),
        saveSize = 3,
        cachePath = "3/$GAME_ID/20261005_090000/backup.ram",
        contentHash = hash,
        identityHash = hash,
        channelName = channel,
        needsRemoteSync = true,
        ownerUserId = 3L,
        saveFormat = format
    )

    private fun stubDirtyDefaultChannel(): MutableList<List<MultipartBody.Part>> {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns
            SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        val pushes = mutableListOf<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(pushes)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "c1")).toResponseBody())
        return pushes
    }

    @Test
    fun `a cached save pushes in the format recorded on its row, not the live route`(): Unit = runBlocking {
        val pushes = stubDirtyDefaultChannel()
        coEvery { saveCacheDao.getById(60L) } returns cachedRow(60L, SaveCacheEntity.FORMAT_NATIVE)
        val file = tempDir.newFile("Lunar.srm").apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, file, onTopOfCurrent = false, cacheId = 60L)

        assertEquals("native", manifestOf(pushes.single()).getJSONObject("save").getString("format"))
    }

    @Test
    fun `a cached Sigil unit pushes as neutral even once the route is gone`(): Unit = runBlocking {
        coEvery { sigil.route(GAME_ID, EMULATOR) } returns null
        val pushes = stubDirtyDefaultChannel()
        coEvery { saveCacheDao.getById(61L) } returns cachedRow(61L, SaveCacheEntity.FORMAT_NEUTRAL)
        val file = tempDir.newFile("backup.ram").apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, file, onTopOfCurrent = false, cacheId = 61L)

        assertEquals("neutral", manifestOf(pushes.single()).getJSONObject("save").getString("format"))
    }

    @Test
    fun `a cached row written before formats were recorded pushes as native`(): Unit = runBlocking {
        val pushes = stubDirtyDefaultChannel()
        coEvery { saveCacheDao.getById(62L) } returns cachedRow(62L, format = null)
        val file = tempDir.newFile("old.srm").apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, file, onTopOfCurrent = false, cacheId = 62L)

        assertEquals("native", manifestOf(pushes.single()).getJSONObject("save").getString("format"))
    }

    @Test
    fun `a cached save that lands marks its row synced`(): Unit = runBlocking {
        stubDirtyDefaultChannel()
        coEvery { saveCacheDao.getById(60L) } returns cachedRow(60L, SaveCacheEntity.FORMAT_NATIVE)
        val file = tempDir.newFile("landed.srm").apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, file, onTopOfCurrent = false, cacheId = 60L)

        coVerify { saveCacheDao.markSynced(60L, any()) }
    }

    @Test
    fun `a cached save the server refuses stays unsynced`(): Unit = runBlocking {
        stubDirtyDefaultChannel()
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.error(409, """{"current":{"id":43,"digest":"sha256:43"}}""".toResponseBody())
        coEvery { saveCacheDao.getById(60L) } returns cachedRow(60L, SaveCacheEntity.FORMAT_NATIVE)
        val file = tempDir.newFile("refused.srm").apply { writeBytes(byteArrayOf(9)) }

        engine.pushCached(GAME_ID, EMULATOR, null, file, onTopOfCurrent = false, cacheId = 60L)

        coVerify(exactly = 0) { saveCacheDao.markSynced(any(), any()) }
    }

    @Test
    fun `a Sigil push marks the cached row holding the pushed unit synced`(): Unit = runBlocking {
        stubDirtyDefaultChannel()
        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, 3L, "autosave", "content-a") } returns
            listOf(cachedRow(40L, SaveCacheEntity.FORMAT_NEUTRAL))

        assertEquals(SnapshotSyncResult.Pushed(42), engine.keepLocal(GAME_ID, EMULATOR, null))

        coVerify { saveCacheDao.markSynced(40L, any()) }
    }

    @Test
    fun `a native push marks the row it packed synced`(): Unit = runBlocking {
        stubNativeSave(ByteArray(64))
        stubDirtyDefaultChannel()

        engine.keepLocal(GAME_ID, EMULATOR, null)

        coVerify { saveCacheDao.markSynced(7L, any()) }
    }

    @Test
    fun `a native push of the save on disk makes its row the active save`(): Unit = runBlocking {
        stubNativeSave(ByteArray(64))
        stubDirtyDefaultChannel()

        engine.keepLocal(GAME_ID, EMULATOR, null)

        coVerify { activeSaves.activateCache(GAME_ID, 7L) }
    }

    @Test
    fun `a save the server already holds marks its cached row synced`(): Unit = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns
            SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "content-a")))
        coEvery { saveCacheDao.getAllByGameChannelAndHash(GAME_ID, 3L, "autosave", "content-a") } returns
            listOf(cachedRow(40L, SaveCacheEntity.FORMAT_NEUTRAL))

        assertEquals(SnapshotSyncResult.UpToDate, engine.sync(GAME_ID, EMULATOR, null))

        coVerify { saveCacheDao.markSynced(40L, any()) }
    }

    @Test
    fun `a cached unit's shape is read from its bytes`() {
        fun zip(vararg names: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(out).use { zip -> names.forEach { zip.putNextEntry(java.util.zip.ZipEntry(it)); zip.write(1); zip.closeEntry() } }
            return out.toByteArray()
        }
        assertEquals("SINGLE", SigilSaveHandler.unitShape(null, byteArrayOf(1, 2, 3)))
        assertEquals("MULTI", SigilSaveHandler.unitShape(null, zip("backup.ram", "cart.ram")))
        assertEquals("FOLDER", SigilSaveHandler.unitShape(null, zip("0100A/a.bin", "0100A/b.bin")))
        assertEquals("FOLDER", SigilSaveHandler.unitShape(null, zip("0100A/a.bin", "device/0100A/b.bin")))
    }

    private fun stubNativeSave(bytes: ByteArray) {
        coEvery { sigil.collect(GAME_ID, EMULATOR, any()) } returns SigilCollect.NotRouted
        coEvery { sigil.route(GAME_ID, EMULATOR) } returns null
        coEvery { apiClient.discoverSavePath(any(), any(), any(), any(), any(), any(), any(), any()) } returns "/saves/Emerald.srm"
        coEvery { apiClient.resolveCoreForGame(any<GameEntity>(), any()) } returns "mgba"
        coEvery { cacheManager.calculateLocalSaveHash("/saves/Emerald.srm", GAME_ID, EMULATOR) } returns "disk-hash"
        coEvery {
            cacheManager.cacheCurrentSave(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns com.nendo.argosy.data.repository.SaveCacheManager.CacheResult.Created(1L, 7L)
        val entity = mockk<com.nendo.argosy.data.local.entity.SaveCacheEntity>()
        coEvery { cacheManager.getCacheById(7L) } returns entity
        val cached = tempDir.newFile("Emerald.srm").apply { writeBytes(bytes) }
        every { cacheManager.getCacheFile(entity) } returns cached
    }

    @Test
    fun `a save Sigil doesn't route pushes as the emulator wrote it, labelled native`() = runBlocking {
        stubNativeSave(ByteArray(131072))
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "md5-of-131072")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(41), engine.sync(GAME_ID, EMULATOR, null))
        val save = manifestOf(parts.captured).getJSONObject("save")
        assertEquals("native", save.getString("format"))
        assertEquals("SINGLE", save.getString("shape"))
        assertEquals("md5-of-131072", save.getString("hash"))
        assertEquals("Emerald.srm", parts.captured[1].headers?.get("Content-Disposition")?.substringAfter("filename=\"")?.substringBefore('"'))
    }

    @Test
    fun `a native save matching current by content is adopted, whatever the server's identity hash`() = runBlocking {
        stubNativeSave(ByteArray(8))
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        val current = snapshot(41, null).copy(
            save = RomMSnapshotSave(410, contentHash = "disk-hash", identityHash = "single-entry-hash", format = "native")
        )
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(current))
        coEvery { api.getSnapshot(41, DEVICE, true) } returns Response.success(current)

        val result = engine.sync(GAME_ID, EMULATOR, null)

        assertTrue("adopted, not a conflict: $result", result !is SnapshotSyncResult.Conflict)
        assertEquals("disk-hash", stored.captured.heldSaveIdentityHash)
        coVerify(exactly = 1) { api.getSnapshot(41, DEVICE, true) }
    }

    @Test
    fun `progress on a restored older snapshot pushes over current naming it as parent`() = runBlocking {
        stubNativeSave(ByteArray(8))
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(
            3L, GAME_ID, "default", CHANNEL, FILE_ID, 39, "d", "content-a", "content-a", 0, heldByChoice = true
        )
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(43, "disk-hash")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(43), engine.sync(GAME_ID, EMULATOR, null))
        val manifest = manifestOf(parts.captured)
        assertEquals(42L, manifest.getLong("expected_current_id"))
        assertEquals(39L, manifest.getLong("parent_snapshot_id"))
        assertTrue("the new tip is held normally", !stored.captured.heldByChoice)
    }

    @Test
    fun `a named Argosy channel syncs as the spec channel with its label`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "Speedrun") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "other")))
        coEvery { api.getSavesByRom(ROMM_ID) } returns Response.success(emptyList())
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(50, "content-a")).toResponseBody())

        assertEquals(SnapshotSyncResult.Pushed(50), engine.sync(GAME_ID, EMULATOR, "Speedrun"))
        val manifest = manifestOf(parts.captured)
        assertEquals("Speedrun", manifest.getString("label"))
        assertTrue("the default channel's current is not this channel's", manifest.isNull("expected_current_id"))
        assertEquals("Speedrun", stored.captured.label)
    }

    @Test
    fun `a newer current on a clean device is placed by the legacy downloader and held in the same fetch`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(42, any(), any()) } returns Response.success(snapshot(42, "theirs"))
        coEvery {
            downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true)
        } returns com.nendo.argosy.data.repository.SaveSyncResult.Success()

        assertEquals(SnapshotSyncResult.Applied(42), engine.sync(GAME_ID, EMULATOR, null))
        assertEquals(42L, stored.captured.heldSnapshotId)
        coVerify(exactly = 1) { api.getSnapshot(42, DEVICE, true) }
    }

    @Test
    fun `keep both forks a new channel from the held snapshot, numbered past a label the server already has`() = runBlocking {
        val base = SnapshotChannels.branchLabel("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", java.time.LocalDate.now(), emptyList())
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(listOf(
            RomMChannel(id = CHANNEL, label = "default", currentSnapshotId = 42, romFileId = FILE_ID, current = snapshot(42, "theirs")),
            RomMChannel(id = "other-device", label = base, romFileId = FILE_ID)
        ))
        val parts = slot<List<MultipartBody.Part>>()
        coEvery { api.pushSnapshot(DEVICE, capture(parts)) } returns
            Response.success(201, snapshotJson.toJson(snapshot(50, "content-a")).toResponseBody())

        val result = engine.branch(GAME_ID, EMULATOR, null)

        assertEquals(SnapshotSyncResult.Branched(50, "$base (2)"), result)
        val manifest = manifestOf(parts.captured)
        assertEquals("$base (2)", manifest.getString("label"))
        assertTrue(manifest.isNull("expected_current_id"))
        assertEquals(41L, manifest.getLong("parent_snapshot_id"))
    }

    @Test
    fun `revert places the held snapshot over a newer current and keeps it by choice`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 39, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(39, any(), any()) } returns Response.success(snapshot(39, "old"))
        coEvery { downloader.downloadSave(GAME_ID, EMULATOR, null, false, 390L, true) } returns
            com.nendo.argosy.data.repository.SaveSyncResult.Success()

        assertEquals(SnapshotSyncResult.Applied(39), engine.revert(GAME_ID, EMULATOR, null))
        assertEquals(39L, stored.captured.heldSnapshotId)
        assertTrue(stored.captured.heldByChoice)
    }

    @Test
    fun `revert with nothing held places current without holding it by choice`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(42, any(), any()) } returns Response.success(snapshot(42, "theirs"))
        coEvery { downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true) } returns
            com.nendo.argosy.data.repository.SaveSyncResult.Success()

        assertEquals(SnapshotSyncResult.Applied(42), engine.revert(GAME_ID, EMULATOR, null))
        assertEquals(42L, stored.captured.heldSnapshotId)
        assertTrue(!stored.captured.heldByChoice)
    }

    @Test
    fun `restoring an older snapshot on this device holds it by choice`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 42, "d", "theirs", "theirs", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(37, any(), any()) } returns Response.success(snapshot(37, "older"))
        coEvery { downloader.downloadSave(GAME_ID, EMULATOR, null, false, 370L, true) } returns
            com.nendo.argosy.data.repository.SaveSyncResult.Success()

        assertEquals(SnapshotSyncResult.Applied(37), engine.restoreLocally(GAME_ID, EMULATOR, null, 37))
        assertEquals(37L, stored.captured.heldSnapshotId)
        assertTrue(stored.captured.heldByChoice)
    }

    @Test
    fun `an offline chain push leaves the active save alone`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "content-a")).toResponseBody())
        val cacheFile = tempDir.newFile("chain-1.srm").apply { writeBytes(byteArrayOf(4, 5, 6)) }

        engine.pushCached(GAME_ID, EMULATOR, null, cacheFile, onTopOfCurrent = true, cacheId = 77L)

        coVerify(exactly = 0) { activeSaves.activateCache(any(), any()) }
    }

    @Test
    fun `the cache row holding an applied snapshot's save becomes the active save`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(42, "theirs")))
        coEvery { api.getSnapshot(42, any(), any()) } returns Response.success(snapshot(42, "theirs"))
        coEvery {
            downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true)
        } returns com.nendo.argosy.data.repository.SaveSyncResult.Success()
        val older =mockk<SaveCacheEntity>(relaxed = true) { every { id } returns 326L; every { rommSaveId } returns null }
        val placed = mockk<SaveCacheEntity>(relaxed = true) { every { id } returns 328L; every { rommSaveId } returns 420L }
        coEvery { saveCacheDao.getByGameAndOwner(GAME_ID, 3L) } returns listOf(older, placed)

        engine.sync(GAME_ID, EMULATOR, null)

        coVerify { activeSaves.activateCache(GAME_ID, 328L) }
    }

    @Test
    fun `applying a snapshot clears the emulator's held states and places its bank`() = runBlocking {
        val dir = tempDir.newFolder("applied")
        val auto = java.io.File(dir, "Lunar (USA).state.auto").apply { writeBytes(byteArrayOf(1)) }
        val stray = java.io.File(dir, "Lunar (USA).state3").apply { writeBytes(byteArrayOf(2)) }
        val strayShot = java.io.File(dir, "Lunar (USA).state3.png").apply { writeBytes(byteArrayOf(3)) }
        every { archiver.calculateContentHash(stray) } returns "stray"
        coEvery { coreResolver.resolveCoreId(GAME_ID, any(), any()) } returns "genesis_plus_gx"
        coEvery { statePaths.liveStateBaseDir(GAME_ID) } returns dir
        every { statePaths.liveStateFile(dir, "Lunar (USA)", -1) } returns auto
        every { archiver.calculateContentHash(auto) } returns "old-state"
        val banked = snapshot(42, "theirs").copy(
            states = mapOf(
                "genesis_plus_gx" to mapOf(
                    "auto" to com.nendo.argosy.data.remote.romm.RomMSnapshotState(
                        id = 7,
                        contentHash = "new-state",
                        downloadPath = "/api/states/7/content",
                        screenshot = com.nendo.argosy.data.remote.romm.RomMScreenshotRef(9, "/api/screenshots/9/content")
                    )
                )
            )
        )
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(banked))
        coEvery { api.getSnapshot(42, any(), any()) } returns Response.success(banked)
        coEvery {
            downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true)
        } returns com.nendo.argosy.data.repository.SaveSyncResult.Success()
        coEvery { api.downloadRaw("api/states/7/content") } returns Response.success(byteArrayOf(5, 5, 5).toResponseBody())
        coEvery { api.downloadRaw("api/screenshots/9/content") } returns Response.success(byteArrayOf(8).toResponseBody())

        assertEquals(SnapshotSyncResult.Applied(42), engine.sync(GAME_ID, EMULATOR, null))
        assertTrue(byteArrayOf(5, 5, 5).contentEquals(auto.readBytes()))
        assertTrue(byteArrayOf(8).contentEquals(java.io.File(dir, "Lunar (USA).state.auto.png").readBytes()))
        assertTrue("a slot the bank lacks is removed", !stray.exists() && !strayShot.exists())
    }

    private class LiveStates(val dir: java.io.File, val auto: java.io.File, val stray: java.io.File, val strayShot: java.io.File)

    private fun stubBankedApply(): LiveStates {
        val dir = tempDir.newFolder("bank")
        val live = LiveStates(
            dir,
            java.io.File(dir, "Lunar (USA).state.auto").apply { writeBytes(byteArrayOf(1)) },
            java.io.File(dir, "Lunar (USA).state3").apply { writeBytes(byteArrayOf(2)) },
            java.io.File(dir, "Lunar (USA).state3.png").apply { writeBytes(byteArrayOf(3)) }
        )
        every { archiver.calculateContentHash(live.stray) } returns "stray"
        every { archiver.calculateContentHash(live.auto) } returns "old-state"
        coEvery { coreResolver.resolveCoreId(GAME_ID, any(), any()) } returns "genesis_plus_gx"
        coEvery { statePaths.liveStateBaseDir(GAME_ID) } returns dir
        every { statePaths.liveStateFile(dir, "Lunar (USA)", -1) } returns live.auto
        val banked = snapshot(42, "theirs").copy(
            states = mapOf(
                "genesis_plus_gx" to mapOf(
                    "auto" to com.nendo.argosy.data.remote.romm.RomMSnapshotState(
                        id = 7,
                        contentHash = "new-state",
                        downloadPath = "/api/states/7/content",
                        screenshot = com.nendo.argosy.data.remote.romm.RomMScreenshotRef(9, "/api/screenshots/9/content")
                    )
                )
            )
        )
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "content-a", "identity-a", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(banked))
        coEvery { api.getSnapshot(42, any(), any()) } returns Response.success(banked)
        coEvery {
            downloader.downloadSave(GAME_ID, EMULATOR, null, false, 420L, true)
        } returns com.nendo.argosy.data.repository.SaveSyncResult.Success()
        coEvery { api.downloadRaw("api/screenshots/9/content") } returns Response.success(byteArrayOf(8).toResponseBody())
        return live
    }

    @Test
    fun `a state that fails to download leaves the live states and the held snapshot alone`() = runBlocking {
        val live = stubBankedApply()
        coEvery { api.downloadRaw("api/states/7/content") } returns Response.error(500, "down".toResponseBody())

        val result = engine.sync(GAME_ID, EMULATOR, null)

        assertTrue("apply fails: $result", result is SnapshotSyncResult.Failed)
        assertTrue(byteArrayOf(1).contentEquals(live.auto.readBytes()))
        assertTrue(live.stray.exists() && live.strayShot.exists())
        coVerify(exactly = 0) { channelDao.upsert(any()) }
        coVerify(exactly = 0) { downloader.downloadSave(any(), any(), any(), any(), any(), any()) }
        assertEquals(listOf("Lunar (USA).state.auto", "Lunar (USA).state3", "Lunar (USA).state3.png"), live.dir.list()!!.sorted())
    }

    @Test
    fun `a bank screenshot that fails to download also leaves the live states alone`() = runBlocking {
        val live = stubBankedApply()
        coEvery { api.downloadRaw("api/states/7/content") } returns Response.success(byteArrayOf(5, 5, 5).toResponseBody())
        coEvery { api.downloadRaw("api/screenshots/9/content") } throws java.io.IOException("reset")

        assertTrue(engine.sync(GAME_ID, EMULATOR, null) is SnapshotSyncResult.Failed)
        assertTrue(byteArrayOf(1).contentEquals(live.auto.readBytes()))
        assertTrue(live.stray.exists())
        coVerify(exactly = 0) { channelDao.upsert(any()) }
    }

    @Test
    fun `a bank applied in full replaces the held states, clears untapped slots and leaves no staging behind`() = runBlocking {
        val live = stubBankedApply()
        coEvery { api.downloadRaw("api/states/7/content") } returns Response.success(byteArrayOf(5, 5, 5).toResponseBody())

        assertEquals(SnapshotSyncResult.Applied(42), engine.sync(GAME_ID, EMULATOR, null))
        assertTrue(byteArrayOf(5, 5, 5).contentEquals(live.auto.readBytes()))
        assertTrue(!live.stray.exists() && !live.strayShot.exists())
        assertEquals(listOf("Lunar (USA).state.auto", "Lunar (USA).state.auto.png"), live.dir.list()!!.sorted())
        assertEquals(42L, stored.captured.heldSnapshotId)
    }

    @Test
    fun `applying a snapshot leaves unsynced cached saves flagged`() = runBlocking {
        stubBankedApply()
        coEvery { api.downloadRaw("api/states/7/content") } returns Response.success(byteArrayOf(5).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)

        coVerify(exactly = 0) { saveCacheDao.clearDirtyFlagForNoChannel(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.clearDirtyFlagForChannel(any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.clearAllDirtyFlags(any(), any()) }
    }

    @Test
    fun `a push to the default channel clears only the default channel's dirty flags`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.success(201, snapshotJson.toJson(snapshot(41, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)

        coVerify { saveCacheDao.clearDirtyFlagForNoChannel(GAME_ID, 3L) }
        coVerify { saveCacheDao.clearDirtyFlagForChannel(GAME_ID, 3L, "autosave", any()) }
        coVerify(exactly = 0) { saveCacheDao.clearAllDirtyFlags(any(), any()) }
    }

    @Test
    fun `a push to a named channel clears only that channel's dirty flags`() = runBlocking {
        coEvery { channelDao.get(3L, GAME_ID, "Speedrun") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns Response.success(emptyList())
        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.success(201, snapshotJson.toJson(snapshot(50, "content-a")).toResponseBody())

        engine.sync(GAME_ID, EMULATOR, "Speedrun")

        coVerify { saveCacheDao.clearDirtyFlagForChannel(GAME_ID, 3L, "Speedrun", any()) }
        coVerify(exactly = 0) { saveCacheDao.clearDirtyFlagForNoChannel(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.clearAllDirtyFlags(any(), any()) }
    }

    @Test
    fun `a conflict carries the save's path so keep-local can cache it`() = runBlocking {
        stubNativeSave(ByteArray(131072))
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns null
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "theirs")))

        val result = engine.sync(GAME_ID, EMULATOR, null)

        assertEquals("/saves/Emerald.srm", (result as SnapshotSyncResult.Conflict).localSavePath)
    }

    @Test
    fun `a Sigil unit's state is stored only once the push landed`() = runBlocking {
        val pending = com.nendo.argosy.data.sync.platform.SigilPendingState(
            com.nendo.argosy.data.local.entity.SigilSyncStateEntity(3L, "scd", "genesis_plus_gx", "/saves", byteArrayOf(4), "", 0L)
        )
        coEvery { sigil.collect(GAME_ID, EMULATOR, any()) } returns local.copy(root = "/saves", pending = pending)
        coEvery { sigil.commit(pending) } returns Unit
        coEvery { channelDao.get(3L, GAME_ID, "default") } returns SnapshotChannelEntity(3L, GAME_ID, "default", CHANNEL, FILE_ID, 41, "d", "old", "old-identity", 0)
        coEvery { api.listChannels(listOf(FILE_ID)) } returns channelsOf(listOf(snapshot(41, "old")))
        coEvery { api.pushSnapshot(DEVICE, any()) } returns Response.error(500, "down".toResponseBody())

        engine.sync(GAME_ID, EMULATOR, null)
        coVerify(exactly = 0) { sigil.commit(any()) }

        coEvery { api.pushSnapshot(DEVICE, any()) } returns
            Response.success(201, snapshotJson.toJson(snapshot(42, "content-a")).toResponseBody())
        engine.sync(GAME_ID, EMULATOR, null)
        coVerify(exactly = 1) { sigil.commit(pending) }
    }

    @Test
    fun `a server without the snapshot flag gets nothing through the snapshot API`() = runBlocking {
        every { apiClient.getCapabilities() } returns RomMCapabilities.from("5.5.0", snapshotsEnabled = false)

        assertEquals(SnapshotSyncResult.NotEligible, engine.sync(GAME_ID, EMULATOR, null))
        coVerify(exactly = 0) { api.listChannels(any()) }
    }

    private companion object {
        const val GAME_ID = 5L
        const val ROMM_ID = 70L
        const val FILE_ID = 99L
        const val DEVICE = "d-7f3a"
        const val EMULATOR = "argosy"
        const val CHANNEL = "0192f1c4-0000-7000-8000-000000000000"
    }
}
