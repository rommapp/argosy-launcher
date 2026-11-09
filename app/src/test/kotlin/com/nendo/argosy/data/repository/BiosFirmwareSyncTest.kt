package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.local.dao.FirmwareDao
import com.nendo.argosy.data.local.entity.FirmwareEntity
import com.nendo.argosy.data.preferences.UserPreferences
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMFirmware
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.time.Instant

class BiosFirmwareSyncTest {

    private val rows = mutableListOf<FirmwareEntity>()
    private val filesDir = File("build/tmp/bios-sync")
    private var afterSyncRead: suspend () -> Unit = {}
    private var duringTransfer: () -> Unit = {}
    private lateinit var repository: BiosRepository

    @Before
    fun setUp() {
        rows.clear()
        filesDir.deleteRecursively()
        val dao = mockk<FirmwareDao>(relaxed = true)
        coEvery { dao.getByRommId(any()) } coAnswers {
            val read = rows.firstOrNull { it.rommId == firstArg() }
            val hook = afterSyncRead
            afterSyncRead = {}
            hook()
            read
        }
        coEvery { dao.updateLocalPath(any(), any(), any()) } answers {
            val id = firstArg<Long>()
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = rows[index].copy(localPath = secondArg(), downloadedAt = thirdArg())
        }
        coEvery { dao.upsertAll(any()) } answers {
            val incoming = firstArg<List<FirmwareEntity>>()
            rows.removeAll { row -> incoming.any { it.rommId == row.rommId } }
            rows.addAll(incoming)
        }
        coEvery { dao.deleteRemovedFirmware(any(), any()) } answers {
            val platformId = firstArg<Long>()
            val keep = secondArg<List<Long>>()
            rows.removeAll { it.platformId == platformId && it.rommId !in keep }
        }
        coEvery { dao.getAll() } answers { rows.toList() }
        coEvery { dao.deleteByIds(any()) } answers {
            val ids = firstArg<List<Long>>()
            rows.removeAll { it.id in ids }
        }
        coEvery { dao.deleteByPlatform(any()) } answers {
            val platformId = firstArg<Long>()
            rows.removeAll { it.platformId == platformId }
        }

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns filesDir
        val prefs = mockk<UserPreferencesRepository>(relaxed = true)
        val values = mockk<UserPreferences>(relaxed = true)
        every { values.customBiosPath } returns null
        every { prefs.preferences } returns flowOf(values)
        val api = mockk<RomMApi>()
        coEvery { api.downloadFirmware(any(), any(), any()) } answers {
            duringTransfer()
            Response.success(ByteArray(1024).toResponseBody())
        }
        repository = BiosRepository(
            context = context,
            firmwareDao = dao,
            platformDao = mockk(relaxed = true),
            userPreferencesRepository = prefs,
            switchKeyManager = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            fal = mockk(relaxed = true)
        )
        repository.setApi(api)
    }

    private fun biosFile(name: String) = File(filesDir, "bios/3ds/$name")

    private fun remote(id: Long, name: String, missing: Boolean = false) = RomMFirmware(
        id = id,
        fileName = name,
        filePath = "bios/3ds/$name",
        fullPath = "/romm/library/bios/3ds/$name",
        fileSizeBytes = 1024,
        md5Hash = null,
        sha1Hash = null,
        missingFromFs = missing
    )

    private fun row(rommId: Long, platformId: Long, name: String, localPath: String? = null) = FirmwareEntity(
        id = rommId,
        platformId = platformId,
        platformSlug = if (platformId == 3L) "3ds" else "ps1",
        rommId = rommId,
        fileName = name,
        filePath = "bios/$name",
        fileSizeBytes = 1024,
        md5Hash = null,
        sha1Hash = null,
        localPath = localPath,
        downloadedAt = localPath?.let { Instant.EPOCH },
        lastVerifiedAt = null
    )

    @Test
    fun `an empty server list clears only that platform's rows`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        rows += row(20, 1, "scph5501.bin")

        repository.syncPlatformFirmware(3, "3ds", emptyList())

        assertEquals(listOf(20L), rows.map { it.rommId })
    }

    @Test
    fun `firmware missing from the server filesystem is dropped and never offered`() = runBlocking {
        rows += row(10, 3, "firmware.bin")

        repository.syncPlatformFirmware(
            3,
            "3ds",
            listOf(remote(10, "firmware.bin", missing = true), remote(11, "aes_keys.txt"))
        )

        assertEquals(listOf("aes_keys.txt"), rows.map { it.fileName })
    }

    @Test
    fun `a platform whose only firmware is missing ends up with no rows`() = runBlocking {
        rows += row(10, 3, "firmware.bin")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(10, "firmware.bin", missing = true)))

        assertEquals(emptyList<FirmwareEntity>(), rows)
    }

    @Test
    fun `a normal list keeps download state and prunes removed entries`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        rows += row(11, 3, "aes_keys.txt", localPath = "/bios/3ds/aes_keys.txt")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(11, "aes_keys.txt"), remote(12, "seeddb.bin")))

        assertEquals(listOf(11L, 12L), rows.map { it.rommId }.sorted())
        assertEquals("/bios/3ds/aes_keys.txt", rows.first { it.rommId == 11L }.localPath)
        assertNull(rows.first { it.rommId == 12L }.localPath)
    }

    @Test
    fun `a downloaded row flagged missing from the server filesystem is kept with its local path`() = runBlocking {
        rows += row(10, 3, "firmware.bin", localPath = "/bios/3ds/firmware.bin")

        repository.syncPlatformFirmware(
            3,
            "3ds",
            listOf(remote(10, "firmware.bin", missing = true), remote(11, "aes_keys.txt"))
        )

        assertEquals(listOf(10L, 11L), rows.map { it.rommId }.sorted())
        assertEquals("/bios/3ds/firmware.bin", rows.first { it.rommId == 10L }.localPath)
    }

    @Test
    fun `a platform whose only firmware is missing keeps the downloaded rows`() = runBlocking {
        rows += row(10, 3, "firmware.bin", localPath = "/bios/3ds/firmware.bin")
        rows += row(11, 3, "aes_keys.txt")

        repository.syncPlatformFirmware(
            3,
            "3ds",
            listOf(remote(10, "firmware.bin", missing = true), remote(11, "aes_keys.txt", missing = true))
        )

        assertEquals(listOf(10L), rows.map { it.rommId })
        assertEquals("/bios/3ds/firmware.bin", rows.single().localPath)
    }

    @Test
    fun `a downloaded row the server no longer lists is pruned`() = runBlocking {
        rows += row(10, 3, "firmware.bin", localPath = "/bios/3ds/firmware.bin")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(11, "aes_keys.txt")))

        assertEquals(listOf(11L), rows.map { it.rommId })
    }

    @Test
    fun `pruning removed entries leaves another platform's rows untouched`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        rows += row(20, 1, "scph5501.bin", localPath = "/bios/psx/scph5501.bin")
        rows += row(21, 1, "scph1001.bin")

        repository.syncPlatformFirmware(3, "3ds", listOf(remote(10, "firmware.bin", missing = true)))

        assertEquals(listOf(20L, 21L), rows.map { it.rommId }.sorted())
    }

    @Test
    fun `retired firmware the server still lists gets no row`() = runBlocking {
        repository.syncPlatformFirmware(3, "3ds", listOf(remote(30, "xbox_hdd.qcow2"), remote(11, "aes_keys.txt")))

        assertEquals(listOf(11L), rows.map { it.rommId })
    }

    @Test
    fun `the startup purge deletes retired firmware rows and files without asking the server`() = runBlocking {
        val disk = biosFile("xbox_hdd.qcow2").apply { parentFile.mkdirs(); writeBytes(ByteArray(16)) }
        val part = biosFile("xbox_hdd.qcow2.part").apply { writeBytes(ByteArray(8)) }
        val kept = biosFile("aes_keys.txt").apply { writeBytes(ByteArray(4)) }
        rows += row(30, 3, "xbox_hdd.qcow2", localPath = disk.absolutePath)
        rows += row(11, 3, "aes_keys.txt", localPath = kept.absolutePath)

        repository.purgeRetiredFirmware()

        assertEquals(listOf(11L), rows.map { it.rommId })
        assertFalse(disk.exists())
        assertFalse(part.exists())
        assertTrue(kept.exists())
    }

    @Test
    fun `a download whose row is pruned while it transfers leaves no file behind`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        duringTransfer = { rows.removeAll { it.rommId == 10L } }

        val result = repository.downloadFirmware(10)

        assertTrue(result is BiosDownloadResult.Error)
        assertFalse(biosFile("firmware.bin").exists())
        assertFalse(biosFile("firmware.bin.part").exists())
    }

    @Test
    fun `a download whose row is re-created under a new id while it transfers records on the new row`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        duringTransfer = {
            rows.removeAll { it.rommId == 10L }
            rows += row(10, 3, "firmware.bin").copy(id = 99)
        }

        val result = repository.downloadFirmware(10)

        assertTrue(result is BiosDownloadResult.Success)
        assertTrue(biosFile("firmware.bin").exists())
        assertEquals(biosFile("firmware.bin").absolutePath, rows.single().localPath)
    }

    @Test
    fun `a sync running while a download finishes never leaves a file without its row`() = runBlocking {
        rows += row(10, 3, "firmware.bin")
        val syncReading = CompletableDeferred<Unit>()
        val releaseSync = CompletableDeferred<Unit>()
        afterSyncRead = {
            syncReading.complete(Unit)
            releaseSync.await()
        }

        val sync = async {
            repository.syncPlatformFirmware(3, "3ds", listOf(remote(10, "firmware.bin", missing = true)))
        }
        syncReading.await()
        val download = async(Dispatchers.IO) { repository.downloadFirmware(10) }
        withTimeoutOrNull(500) { download.await() }
        releaseSync.complete(Unit)
        sync.await()
        download.await()

        val row = rows.firstOrNull { it.rommId == 10L }
        assertEquals(biosFile("firmware.bin").exists(), row?.localPath != null)
    }
}
