package com.nendo.argosy.data.sync

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.data.emulator.EmulatorResolver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.SaveCacheEntity
import com.nendo.argosy.data.local.entity.SaveSyncEntity
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncOrchestrator
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.sync.fixtures.realFsFal
import com.nendo.argosy.data.sync.platform.GciSaveHandler
import com.nendo.argosy.data.sync.platform.PlatformSaveHandlerRegistry
import com.nendo.argosy.data.sync.platform.SigilCollect
import com.nendo.argosy.data.sync.platform.SigilProbe
import com.nendo.argosy.data.sync.platform.SigilRestore
import com.nendo.argosy.data.sync.platform.SigilRoute
import com.nendo.argosy.data.sync.platform.SigilSaveHandler
import com.nendo.sigil.SigilException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class CrossEmulatorSaveMigratorTest {

    private lateinit var tempDir: File
    private lateinit var standaloneRoot: File
    private lateinit var builtinRoot: File
    private lateinit var archiver: SaveArchiver
    private lateinit var migrator: CrossEmulatorSaveMigrator

    private val context = mockk<Context>(relaxed = true)
    private val gameDao = mockk<GameDao>(relaxed = true)
    private val saveCacheDao = mockk<SaveCacheDao>(relaxed = true)
    private val saveSyncDao = mockk<SaveSyncDao>(relaxed = true)
    private val activeSaveRepository = mockk<ActiveSaveRepository>(relaxed = true)
    private val emulatorResolver = mockk<EmulatorResolver>(relaxed = true)
    private val sigil = mockk<SigilSaveHandler>(relaxed = true)
    private val orchestrator = mockk<SaveSyncOrchestrator>(relaxed = true)
    private val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferencesRepository>(relaxed = true)
    private val savePathResolver = mockk<SavePathResolver>(relaxed = true)
    private val ownership = mockk<SaveOwnershipTracker>(relaxed = true)
    private val notifications = mockk<NotificationManager>(relaxed = true)
    private val inserted = mutableListOf<SaveCacheEntity>()

    private lateinit var standaloneRoute: SigilRoute
    private lateinit var builtinRoute: SigilRoute

    @Before
    fun setUp() {
        tempDir = createTempDirectory("cross_emulator_migration").toFile()
        standaloneRoot = File(tempDir, "storage/PSP-root").apply { mkdirs() }
        builtinRoot = File(tempDir, "files/libretro/saves").apply { mkdirs() }
        standaloneRoute = SigilRoute("ppsspp_standalone", standaloneRoot.absolutePath, STANDALONE_PACKAGE, null)
        builtinRoute = SigilRoute("ppsspp", builtinRoot.absolutePath, null, null, libretro = true)

        every { context.filesDir } returns tempDir
        every { context.cacheDir } returns File(tempDir, "cache").apply { mkdirs() }
        every { userPreferences.userPreferences } returns flowOf(UserPreferences())
        coEvery { gameDao.getById(GAME_ID) } returns GAME
        coEvery { syncPreferences.getRommUserId() } returns OWNER
        coEvery { syncPreferences.isSecureSaves() } returns true
        coEvery { saveCacheDao.getMostRecent(GAME_ID, OWNER) } returns null
        coEvery { saveCacheDao.getAllByGameChannelAndHash(any(), any(), any(), any()) } returns emptyList()
        coEvery { saveCacheDao.getLatestByGameChannelAndIdentity(any(), any(), any(), any()) } returns null
        coEvery { saveCacheDao.insert(capture(inserted)) } answers { 100L + inserted.size }
        coEvery { saveSyncDao.getByGameEmulatorAndChannel(any(), any(), any(), any()) } returns null
        coEvery { ownership.recordedHash(any(), any()) } returns null
        coEvery { ownership.claim(any(), any()) } returns SaveClaim.Unowned
        coEvery { sigil.restoreInto(any(), any(), any()) } returns SigilRestore.Restored(hardcoreMarker = false)
        coEvery { orchestrator.lookupSystemSave(any(), any(), any()) } answers {
            SaveLookup.Found(if (secondArg<String>() == BUILTIN) builtinRoot.absolutePath else standaloneRoot.absolutePath)
        }
        every { savePathResolver.findNewestFileTime(any()) } returns 1_000L
        coEvery { orchestrator.reconcileDisk(any(), any(), any(), any(), any(), any(), any()) } returns
            SaveSyncOrchestrator.DiskCheck.Untouched
        coEvery { syncPreferences.isSaveMigrationNoticeShown(any()) } returns false

        val fal = realFsFal()
        archiver = SaveArchiver(mockk<AndroidDataAccessor>(relaxed = true), fal)
        val cacheManager = SaveCacheManager(
            context = context,
            saveCacheDao = saveCacheDao,
            saveSyncDao = saveSyncDao,
            pendingSyncQueueDao = mockk(relaxed = true),
            gameDao = gameDao,
            preferencesRepository = userPreferences,
            syncPreferencesRepository = syncPreferences,
            savePathResolver = savePathResolver,
            saveArchiver = archiver,
            fal = fal,
            saveHandlerRegistry = mockk<PlatformSaveHandlerRegistry>(relaxed = true) {
                every { getFolderHandler(any()) } returns null
            },
            saveOwnershipTracker = ownership,
            saveOwnershipDao = mockk(relaxed = true),
            saveUnitResolver = mockk(relaxed = true),
            gciSaveHandler = GciSaveHandler(context, fal, archiver),
            sigilSaveHandler = sigil,
        )
        migrator = CrossEmulatorSaveMigrator(
            gameDao = gameDao,
            activeSaveRepository = activeSaveRepository,
            saveCacheDao = saveCacheDao,
            emulatorResolver = emulatorResolver,
            sigilSaveHandler = sigil,
            saveCacheManager = cacheManager,
            orchestrator = orchestrator,
            syncPreferencesRepository = syncPreferences,
            saveOwnershipTracker = ownership,
            notificationManager = notifications
        )
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun launching(target: String) {
        val pkg = if (target == BUILTIN) BUILTIN_PACKAGE else STANDALONE_PACKAGE
        coEvery { emulatorResolver.getEmulatorPackageForGame(GAME_ID, GAME.platformId, GAME.platformSlug) } returns pkg
        every { emulatorResolver.resolveEmulatorId(pkg) } returns target
        coEvery { sigil.localRoute(GAME_ID, STANDALONE) } returns standaloneRoute
        coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns builtinRoute
    }

    private fun routeOf(emulatorId: String) = if (emulatorId == BUILTIN) builtinRoute else standaloneRoute

    private fun activeRow(
        from: String,
        format: String,
        bytes: ByteArray = NEUTRAL_UNIT,
        name: String = "ULUS10336.zip",
        isHardcore: Boolean = false,
        channel: String = "autosave"
    ): SaveCacheEntity {
        val path = "u3/4/20261012_000000/$name"
        File(tempDir, "save_cache/$path").apply { parentFile?.mkdirs(); writeBytes(bytes) }
        val row = SaveCacheEntity(
            id = ACTIVE_ID,
            gameId = GAME_ID,
            emulatorId = from,
            cachedAt = Instant.EPOCH,
            saveSize = bytes.size.toLong(),
            cachePath = path,
            contentHash = "active-hash",
            identityHash = "active-identity",
            channelName = channel,
            ownerUserId = OWNER,
            isHardcore = isHardcore,
            saveFormat = format,
            isActive = true
        )
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns row
        return row
    }

    private fun targetHolds(emulatorId: String, anchored: Boolean, changed: Boolean) {
        val unit = SigilCollect.Found(TARGET_UNIT, "ULUS10336.zip", "target-hash", "target-identity", root = routeOf(emulatorId).root)
        coEvery { sigil.probe(routeOf(emulatorId), GAME_ID) } returns SigilProbe.Found(unit, anchored, changed)
        coEvery { sigil.collect(GAME_ID, emulatorId, any()) } returns unit
    }

    private fun targetEmpty(emulatorId: String) {
        coEvery { sigil.probe(routeOf(emulatorId), GAME_ID) } returns SigilProbe.Absent
    }

    private fun nativeZip(entries: Map<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun rollbacks() = inserted.filter { it.isRollback }

    private fun refusedWith(message: Int) = verify {
        notifications.show(
            title = NotificationText.Res(R.string.sync_migration_refused_title, listOf(GAME.title)),
            subtitle = NotificationText.Res(message),
            type = any(),
            imagePath = any(),
            platformSlug = any(),
            duration = any(),
            key = "save-migration-$GAME_ID",
            immediate = any(),
            accentColor = any()
        )
    }

    @Test
    fun `a neutral save from the standalone emulator restores through Sigil into the built-in route`() = runTest {
        launching(BUILTIN)
        val active = activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetEmpty(BUILTIN)
        val unit = slot<ByteArray>()
        coEvery { sigil.restoreInto(builtinRoute, GAME_ID, capture(unit)) } returns SigilRestore.Restored(false)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertArrayEquals(NEUTRAL_UNIT, unit.captured)
        coVerify(exactly = 1) { ownership.record(builtinRoot.absolutePath, BUILTIN, active.contentHash, GAME_ID, "autosave") }
        coVerify(exactly = 0) { ownership.record(any(), STANDALONE, any(), any(), any()) }
        coVerify(exactly = 0) { sigil.convertNative(any(), any(), any()) }
    }

    @Test
    fun `with no active row and no chosen slot, the newest save that is not a rollback is carried`() = runTest {
        launching(BUILTIN)
        val newest = activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL).copy(isActive = false)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns null
        coEvery { saveCacheDao.getMostRecentNonRollback(GAME_ID, OWNER) } returns newest
        targetEmpty(BUILTIN)
        coEvery { sigil.restoreInto(builtinRoute, GAME_ID, any()) } returns SigilRestore.Restored(false)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))
    }

    @Test
    fun `with no active row and an empty chosen slot, nothing is carried`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        coEvery { activeSaveRepository.getActiveRow(GAME_ID) } returns null
        coEvery { activeSaveRepository.getActiveChannel(GAME_ID) } returns "quick"
        coEvery { saveCacheDao.getMostRecentInChannel(GAME_ID, OWNER, "quick") } returns null

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Untouched, migrator.migrateBeforeLaunch(GAME_ID))
        coVerify(exactly = 0) { saveCacheDao.getMostRecentNonRollback(any(), any()) }
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
    }

    @Test
    fun `a native save from the built-in core is collected under the built-in layout, then restored into the standalone route`() = runTest {
        launching(STANDALONE)
        val folder = mapOf("ULUS10336DATA00/DATA.BIN" to byteArrayOf(1, 2, 3), "ULUS10336DATA00/ICON0.PNG" to byteArrayOf(4))
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = nativeZip(folder), name = "save.zip")
        targetEmpty(STANDALONE)
        val files = slot<Map<String, ByteArray>>()
        coEvery { sigil.convertNative(builtinRoute, GAME_ID, capture(files)) } returns
            SigilCollect.Found(CONVERTED_UNIT, "ULUS10336.zip", "converted-hash", "converted-identity")
        val unit = slot<ByteArray>()
        coEvery { sigil.restoreInto(standaloneRoute, GAME_ID, capture(unit)) } returns SigilRestore.Restored(false)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertEquals(folder.keys, files.captured.keys)
        folder.forEach { (name, bytes) -> assertArrayEquals(bytes, files.captured[name]) }
        assertArrayEquals(CONVERTED_UNIT, unit.captured)
        coVerify(exactly = 1) { ownership.record(standaloneRoot.absolutePath, STANDALONE, any(), GAME_ID, any()) }
    }

    @Test
    fun `a single-file native save reaches the converter under its own name`() = runTest {
        launching(STANDALONE)
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = byteArrayOf(9, 8, 7), name = "Game.srm")
        targetEmpty(STANDALONE)
        val files = slot<Map<String, ByteArray>>()
        coEvery { sigil.convertNative(builtinRoute, GAME_ID, capture(files)) } returns
            SigilCollect.Found(CONVERTED_UNIT, "Game.srm", "converted-hash", "converted-identity")

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertEquals(setOf("Game.srm"), files.captured.keys)
        assertArrayEquals(byteArrayOf(9, 8, 7), files.captured.getValue("Game.srm"))
    }

    @Test
    fun `a native conversion records nothing for its scratch root`() = runTest {
        launching(STANDALONE)
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = byteArrayOf(9), name = "Game.srm")
        targetEmpty(STANDALONE)
        coEvery { sigil.convertNative(any(), any(), any()) } returns
            SigilCollect.Found(CONVERTED_UNIT, "Game.srm", "converted-hash", "converted-identity")
        val ownedPaths = mutableListOf<String>()
        coEvery { ownership.record(capture(ownedPaths), any(), any(), any(), any()) } returns Unit
        val syncRows = mutableListOf<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(syncRows)) } returns 1L

        migrator.migrateBeforeLaunch(GAME_ID)

        assertEquals(listOf(standaloneRoot.absolutePath), ownedPaths)
        assertTrue(syncRows.none { it.localSavePath?.startsWith(File(tempDir, "cache").absolutePath) == true })
        coVerify(exactly = 0) { sigil.collect(any(), any(), any()) }
        coVerify(exactly = 0) { sigil.commit(any()) }
    }

    @Test
    fun `a native cache whose entries leave its folder is refused before any conversion`() = runTest {
        launching(STANDALONE)
        activeRow(
            from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE,
            bytes = nativeZip(mapOf("../../outside.bin" to byteArrayOf(1))), name = "save.zip"
        )
        targetEmpty(STANDALONE)

        assertEquals(
            CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.UNSUPPORTED_FORMAT),
            migrator.migrateBeforeLaunch(GAME_ID)
        )
        coVerify(exactly = 0) { sigil.convertNative(any(), any(), any()) }
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
    }

    @Test
    fun `Secure Saves on backs up the target save as a rollback before placing the active save`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = true, changed = true)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertEquals(1, rollbacks().size)
        assertEquals(BUILTIN, rollbacks().single().emulatorId)
        coVerifyOrder {
            saveCacheDao.insert(match { it.isRollback })
            sigil.restoreInto(builtinRoute, GAME_ID, any())
        }
    }

    @Test
    fun `Secure Saves on writes nothing when the target save cannot be backed up`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = false, changed = true)
        coEvery { sigil.collect(GAME_ID, BUILTIN, any()) } returns SigilCollect.Unreadable("locked")

        assertEquals(
            CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.BACKUP_FAILED),
            migrator.migrateBeforeLaunch(GAME_ID)
        )
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        refusedWith(R.string.sync_migration_refused_backup_failed)
    }

    @Test
    fun `Secure Saves off places the active save when the target is unchanged since Argosy's anchor`() = runTest {
        coEvery { syncPreferences.isSecureSaves() } returns false
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = true, changed = false)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertEquals(1, rollbacks().size)
        coVerify(exactly = 1) { sigil.restoreInto(builtinRoute, GAME_ID, any()) }
    }

    @Test
    fun `Secure Saves off places the active save into an empty target`() = runTest {
        coEvery { syncPreferences.isSecureSaves() } returns false
        launching(STANDALONE)
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetEmpty(STANDALONE)
        val anchor = slot<SaveSyncEntity>()
        coEvery { saveSyncDao.upsert(capture(anchor)) } returns 1L

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertTrue(rollbacks().isEmpty())
        assertEquals(STANDALONE, anchor.captured.emulatorId)
        assertEquals(standaloneRoot.absolutePath, anchor.captured.localSavePath)
    }

    @Test
    fun `Secure Saves off adopts a target changed outside Argosy and writes nothing`() = runTest {
        coEvery { syncPreferences.isSecureSaves() } returns false
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = true, changed = true)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Adopted, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        assertTrue(rollbacks().isEmpty())
        val adopted = inserted.single()
        assertEquals(BUILTIN, adopted.emulatorId)
        assertTrue(adopted.needsRemoteSync)
        assertEquals("autosave", adopted.channelName)
        coVerify { saveCacheDao.setActiveRow(GAME_ID, OWNER, 101L) }
    }

    @Test
    fun `Secure Saves off adopts a target save Argosy holds no anchor for`() = runTest {
        coEvery { syncPreferences.isSecureSaves() } returns false
        launching(STANDALONE)
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(STANDALONE, anchored = false, changed = true)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Adopted, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        assertEquals(STANDALONE, inserted.single().emulatorId)
    }

    @Test
    fun `Secure Saves off reads the ownership hash as the anchor when Sigil holds none`() = runTest {
        coEvery { syncPreferences.isSecureSaves() } returns false
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = false, changed = true)
        coEvery { ownership.recordedHash(builtinRoot.absolutePath, BUILTIN) } returns "target-hash"

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))
        assertEquals(1, rollbacks().size)

        inserted.clear()
        coEvery { ownership.recordedHash(builtinRoot.absolutePath, BUILTIN) } returns "older-hash"

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Adopted, migrator.migrateBeforeLaunch(GAME_ID))
    }

    @Test
    fun `an unreadable target writes nothing, keeps the active save and tells the user`() = runTest {
        for (secure in listOf(true, false)) {
            coEvery { syncPreferences.isSecureSaves() } returns secure
            launching(BUILTIN)
            activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
            coEvery { sigil.probe(builtinRoute, GAME_ID) } returns SigilProbe.Unreadable("permission denied")

            assertEquals(
                CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.UNREADABLE),
                migrator.migrateBeforeLaunch(GAME_ID)
            )
        }
        assertTrue(inserted.isEmpty())
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        coVerify(exactly = 0) { activeSaveRepository.activateCache(any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.setActiveRow(any(), any(), any()) }
        refusedWith(R.string.sync_migration_refused_unreadable)
    }

    @Test
    fun `each Sigil refusal maps to its own notice and leaves the active save`() = runTest {
        val cases = mapOf(
            SigilException.CONFLICT to (CrossEmulatorSaveMigrator.Refusal.CONFLICT to R.string.sync_migration_refused_conflict),
            SigilException.NO_SPACE to (CrossEmulatorSaveMigrator.Refusal.NO_SPACE to R.string.sync_migration_refused_no_space),
            SigilException.NO_TARGET to (CrossEmulatorSaveMigrator.Refusal.NO_TARGET to R.string.sync_migration_refused_no_target),
            SigilException.AMBIGUOUS to (CrossEmulatorSaveMigrator.Refusal.AMBIGUOUS to R.string.sync_migration_refused_ambiguous),
            SigilException.DAMAGED to (CrossEmulatorSaveMigrator.Refusal.DAMAGED to R.string.sync_migration_refused_damaged),
            SigilException.UNSUPPORTED_FORMAT to
                (CrossEmulatorSaveMigrator.Refusal.UNSUPPORTED_FORMAT to R.string.sync_migration_refused_unsupported_format),
            SigilException.EXISTS to (CrossEmulatorSaveMigrator.Refusal.EXISTS to R.string.sync_migration_refused_exists),
            SigilException.IO to (CrossEmulatorSaveMigrator.Refusal.IO to R.string.sync_migration_refused_io)
        )
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetEmpty(BUILTIN)
        for ((code, expected) in cases) {
            coEvery { sigil.restoreInto(builtinRoute, GAME_ID, any()) } returns SigilRestore.Refused("refused", code)

            assertEquals(CrossEmulatorSaveMigrator.Outcome.Refused(expected.first), migrator.migrateBeforeLaunch(GAME_ID))
            refusedWith(expected.second)
        }
        coVerify(exactly = 0) { ownership.record(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { saveCacheDao.setActiveRow(any(), any(), any()) }
    }

    @Test
    fun `a restore into a shared container goes through Sigil and leaves other games' saves alone`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetHolds(BUILTIN, anchored = true, changed = false)
        val otherGame = File(builtinRoot, "PSP/SAVEDATA/NPJH50000DATA/DATA.BIN").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(42))
        }
        val before = builtinRoot.walkTopDown().filter { it.isFile }.map { it.path }.toSet()

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 1) { sigil.restoreInto(builtinRoute, GAME_ID, any()) }
        assertArrayEquals(byteArrayOf(42), otherGame.readBytes())
        assertEquals(before, builtinRoot.walkTopDown().filter { it.isFile }.map { it.path }.toSet())
    }

    private fun noNotice() =
        verify(exactly = 0) { notifications.show(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }

    private fun legacyPlacement(result: SaveSyncOrchestrator.DiskCheck) {
        coEvery { orchestrator.reconcileDisk(any(), any(), any(), any(), any(), any(), any()) } returns result
    }

    @Test
    fun `a pair Sigil does not route is placed the way a launch always placed it, under each Secure Saves setting`() = runTest {
        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Restored)
        for (secure in listOf(true, false)) {
            coEvery { syncPreferences.isSecureSaves() } returns secure
            launching(BUILTIN)
            val active = activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NATIVE)
            coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns null

            assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

            coVerify(exactly = 1) { orchestrator.reconcileDisk(GAME, BUILTIN, "autosave", active, secure, OWNER, null) }
        }
        coVerify(exactly = 0) { sigil.probe(any(), any()) }
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        noNotice()
    }

    @Test
    fun `a pair without Sigil routes whose save is already there, or adopted, stays silent`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NATIVE)
        coEvery { sigil.localRoute(GAME_ID, STANDALONE) } returns null
        coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns null

        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Matches)
        assertEquals(CrossEmulatorSaveMigrator.Outcome.AlreadyPlaced, migrator.migrateBeforeLaunch(GAME_ID))
        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Adopted)
        assertEquals(CrossEmulatorSaveMigrator.Outcome.Adopted, migrator.migrateBeforeLaunch(GAME_ID))

        noNotice()
    }

    @Test
    fun `a save that reached the target by no path is reported once per active save`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NATIVE)
        coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns null
        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Failed)

        assertEquals(
            CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.IO),
            migrator.migrateBeforeLaunch(GAME_ID)
        )
        refusedWith(R.string.sync_migration_refused_io)
        coVerify(exactly = 1) { syncPreferences.markSaveMigrationNoticeShown(ACTIVE_ID) }

        coEvery { syncPreferences.isSaveMigrationNoticeShown(ACTIVE_ID) } returns true
        assertEquals(
            CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.IO),
            migrator.migrateBeforeLaunch(GAME_ID)
        )
        verify(exactly = 1) { notifications.show(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a generic libretro route on both sides carries the save through Sigil`() = runTest {
        val builtinGeneric = SigilRoute("libretro", builtinRoot.absolutePath, null, null, libretro = true)
        val retroArchGeneric = SigilRoute("libretro", standaloneRoot.absolutePath, null, null, libretro = true)
        launching(STANDALONE)
        coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns builtinGeneric
        coEvery { sigil.localRoute(GAME_ID, STANDALONE) } returns retroArchGeneric
        coEvery { sigil.probe(retroArchGeneric, GAME_ID) } returns SigilProbe.Absent
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = byteArrayOf(9, 8, 7), name = "Game.srm")
        coEvery { sigil.convertNative(builtinGeneric, GAME_ID, any()) } returns
            SigilCollect.Found(byteArrayOf(9, 8, 7), "Game.srm", "converted-hash", "converted-identity")
        val unit = slot<ByteArray>()
        coEvery { sigil.restoreInto(retroArchGeneric, GAME_ID, capture(unit)) } returns SigilRestore.Restored(false)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        assertArrayEquals(byteArrayOf(9, 8, 7), unit.captured)
        coVerify(exactly = 0) { orchestrator.reconcileDisk(any(), any(), any(), any(), any(), any(), any()) }
        noNotice()
    }

    @Test
    fun `a generic route paired with a layout of its own is placed without Sigil`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NATIVE)
        coEvery { sigil.localRoute(GAME_ID, BUILTIN) } returns
            SigilRoute("libretro", builtinRoot.absolutePath, null, null, libretro = true)
        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Restored)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 0) { sigil.probe(any(), any()) }
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        coVerify(exactly = 1) { orchestrator.reconcileDisk(GAME, BUILTIN, any(), any(), any(), any(), null) }
    }

    @Test
    fun `a single-file native save Sigil cannot convert is placed as that file without a notice`() = runTest {
        launching(STANDALONE)
        val active = activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = byteArrayOf(9), name = "Game.srm")
        targetEmpty(STANDALONE)
        coEvery { sigil.convertNative(any(), any(), any()) } returns
            SigilCollect.Found(CONVERTED_UNIT, "Game.srm", "converted-hash", "converted-identity")
        coEvery { sigil.restoreInto(standaloneRoute, GAME_ID, any()) } returns
            SigilRestore.Refused("unsupported", SigilException.UNSUPPORTED_FORMAT)
        legacyPlacement(SaveSyncOrchestrator.DiskCheck.Restored)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 1) { orchestrator.reconcileDisk(GAME, STANDALONE, "autosave", active, true, OWNER, "srm") }
        noNotice()
    }

    @Test
    fun `a multi-file native save Sigil cannot convert is refused with a notice and nothing else written`() = runTest {
        launching(STANDALONE)
        val folder = mapOf("ULUS10336DATA00/DATA.BIN" to byteArrayOf(1), "ULUS10336DATA00/ICON0.PNG" to byteArrayOf(4))
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NATIVE, bytes = nativeZip(folder), name = "save.zip")
        targetEmpty(STANDALONE)
        coEvery { sigil.convertNative(any(), any(), any()) } returns SigilCollect.Absent

        assertEquals(
            CrossEmulatorSaveMigrator.Outcome.Refused(CrossEmulatorSaveMigrator.Refusal.UNSUPPORTED_FORMAT),
            migrator.migrateBeforeLaunch(GAME_ID)
        )
        coVerify(exactly = 0) { orchestrator.reconcileDisk(any(), any(), any(), any(), any(), any(), any()) }
        refusedWith(R.string.sync_migration_refused_unsupported_format)
    }

    @Test
    fun `a placement through Sigil shows no notice`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        targetEmpty(BUILTIN)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Placed, migrator.migrateBeforeLaunch(GAME_ID))
        noNotice()
        coVerify(exactly = 0) { syncPreferences.markSaveMigrationNoticeShown(any()) }
    }

    @Test
    fun `a hardcore active or most recent save is left alone`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL, isHardcore = true)
        assertEquals(CrossEmulatorSaveMigrator.Outcome.Untouched, migrator.migrateBeforeLaunch(GAME_ID))

        val casual = activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        coEvery { saveCacheDao.getMostRecent(GAME_ID, OWNER) } returns casual.copy(id = 77L, isHardcore = true)
        assertEquals(CrossEmulatorSaveMigrator.Outcome.Untouched, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 0) { sigil.localRoute(any(), any()) }
        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
    }

    @Test
    fun `a state channel is left alone`() = runTest {
        launching(BUILTIN)
        activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL, channel = "state_1")

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Untouched, migrator.migrateBeforeLaunch(GAME_ID))
        coVerify(exactly = 0) { sigil.localRoute(any(), any()) }
    }

    @Test
    fun `a launch under the emulator that wrote the active save is untouched`() = runTest {
        launching(BUILTIN)
        activeRow(from = BUILTIN, format = SaveCacheEntity.FORMAT_NEUTRAL)

        assertEquals(CrossEmulatorSaveMigrator.Outcome.Untouched, migrator.migrateBeforeLaunch(GAME_ID))
        coVerify(exactly = 0) { sigil.localRoute(any(), any()) }
        coVerify(exactly = 0) { sigil.probe(any(), any()) }
        verify(exactly = 0) { notifications.show(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a target already holding the active save is not written again`() = runTest {
        launching(BUILTIN)
        val active = activeRow(from = STANDALONE, format = SaveCacheEntity.FORMAT_NEUTRAL)
        val unit = SigilCollect.Found(TARGET_UNIT, "ULUS10336.zip", "target-hash", "active-identity")
        coEvery { sigil.probe(builtinRoute, GAME_ID) } returns SigilProbe.Found(unit, anchored = false, changed = true)
        assertEquals(CrossEmulatorSaveMigrator.Outcome.AlreadyPlaced, migrator.migrateBeforeLaunch(GAME_ID))

        targetHolds(BUILTIN, anchored = true, changed = false)
        coEvery { ownership.recordedHash(builtinRoot.absolutePath, BUILTIN) } returns active.contentHash
        assertEquals(CrossEmulatorSaveMigrator.Outcome.AlreadyPlaced, migrator.migrateBeforeLaunch(GAME_ID))

        coVerify(exactly = 0) { sigil.restoreInto(any(), any(), any()) }
        assertTrue(inserted.isEmpty())
    }

    private companion object {
        const val GAME_ID = 4L
        const val ACTIVE_ID = 31L
        const val OWNER = 3L
        const val STANDALONE = "ppsspp"
        const val BUILTIN = "builtin"
        const val STANDALONE_PACKAGE = "org.ppsspp.ppsspp"
        const val BUILTIN_PACKAGE = "com.nendo.argosy.builtin"
        val NEUTRAL_UNIT = byteArrayOf(0x50, 0x4B, 1, 2)
        val TARGET_UNIT = byteArrayOf(0x50, 0x4B, 7, 7)
        val CONVERTED_UNIT = byteArrayOf(0x50, 0x4B, 5, 5)
        val GAME = GameEntity(
            id = GAME_ID,
            title = "Lunar",
            sortTitle = "lunar",
            platformId = 1L,
            platformSlug = "psp",
            rommId = 77L,
            igdbId = null,
            localPath = "/roms/Lunar.iso",
            source = GameSource.ROMM_SYNCED
        )
    }
}
