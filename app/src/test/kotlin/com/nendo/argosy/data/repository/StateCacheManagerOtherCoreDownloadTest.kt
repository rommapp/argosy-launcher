package com.nendo.argosy.data.repository

import android.content.Context
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File

/**
 * A server copy the running core cannot read is refused without touching the state already
 * cached under the same name, so a readable slot is never emptied by a refused download.
 */
class StateCacheManagerOtherCoreDownloadTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = mockk<RomMApi>()
    private val syncPreferences = mockk<SyncPreferencesRepository>(relaxed = true)
    private lateinit var manager: StateCacheManager

    private val fceumm = "FCS-readable".toByteArray()
    private val nestopia = byteArrayOf('N'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte(), 0x1A, 9, 9)

    @Before
    fun setUp() {
        coEvery { syncPreferences.getRommUserId() } returns 1L
        val context = mockk<Context>(relaxed = true) {
            every { filesDir } returns folder.root
        }
        manager = StateCacheManager(
            context = context,
            gameDao = mockk(relaxed = true),
            stateCacheDao = mockk(relaxed = true),
            stateTombstoneDao = mockk(relaxed = true),
            saveCacheDao = mockk(relaxed = true),
            saveSyncDao = mockk(relaxed = true),
            pendingSyncQueueDao = mockk(relaxed = true),
            emulatorSaveConfigDao = mockk(relaxed = true),
            preferencesRepository = mockk(relaxed = true),
            syncPreferencesRepository = syncPreferences,
            coreVersionExtractor = mockk(relaxed = true),
            retroArchConfigParser = mockk(relaxed = true),
            retroArchPathResolver = mockk(relaxed = true),
            libretroStatePathResolver = mockk(relaxed = true),
            saveSyncApiClient = mockk(relaxed = true),
            payloadCodec = mockk(relaxed = true),
            attributionRepository = mockk(relaxed = true),
            stateOwnershipTracker = mockk(relaxed = true)
        )
    }

    @Test
    fun `a refused download leaves the cached state in place`() = runTest {
        serverServes(fceumm)
        assertEquals(StateCacheManager.StateCloudResult.Success, download())
        val cached = cachedFiles().single()

        serverServes(nestopia)
        val result = download()

        assertEquals(StateCacheManager.StateCloudResult.OtherCoreFormat("nestopia"), result)
        assertArrayEquals(fceumm, cached.readBytes())
        assertEquals(listOf(cached), cachedFiles())
    }

    @Test
    fun `a readable download replaces the cached state`() = runTest {
        serverServes(fceumm)
        download()
        val updated = "FCS-newer".toByteArray()

        serverServes(updated)
        assertEquals(StateCacheManager.StateCloudResult.Success, download())

        assertArrayEquals(updated, cachedFiles().single().readBytes())
        assertTrue(cachedFiles().none { it.name.endsWith(".download") })
    }

    private fun serverServes(bytes: ByteArray) {
        coEvery { api.downloadRaw(DOWNLOAD_PATH) } returns Response.success(bytes.toResponseBody())
    }

    private suspend fun download() = manager.downloadStateFromRomM(
        rommStateId = 5L,
        fileName = FILE_NAME,
        api = api,
        gameId = 3L,
        platformSlug = "nes",
        emulatorId = EmulatorRegistry.BUILTIN_ID,
        coreId = EmulatorRegistry.BUILTIN_ID,
        serverState = RomMState(
            id = 5L,
            romId = 30L,
            userId = 1L,
            fileName = FILE_NAME,
            downloadPath = DOWNLOAD_PATH,
            updatedAt = "2026-11-14T00:00:00Z"
        ),
        readableByCore = "fceumm"
    )

    private fun cachedFiles(): List<File> =
        folder.root.walkTopDown().filter { it.isFile && it.name.startsWith("smb3") }.toList()

    private companion object {
        const val FILE_NAME = "smb3.state1"
        const val DOWNLOAD_PATH = "/api/states/5/content"
    }
}
