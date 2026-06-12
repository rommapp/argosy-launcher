package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMDeviceIdRequest
import com.nendo.argosy.data.remote.romm.RomMSave
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Response

// SaveDownloader is heavily coupled to Android (Context, FileAccessLayer, platform handlers) and
// real file I/O, making full unit coverage of downloadSave() impractical without a significant
// refactor or instrumented tests. The tests here cover the parts that can be isolated cleanly.
//
// Gaps requiring instrumented or integration tests:
//   - confirmDeviceSynced() is called after a successful downloadSave() (the download loop fix)
//   - saveSyncDao.upsert() is called with lastUploadedHash == serverSave.contentHash post-download
//   - folder-based saves use the correct path extraction logic

class SaveDownloaderTest {

    private val api: RomMApi = mockk()
    private val mockApiClient: SaveSyncApiClient = mockk {
        every { getApi() } returns api
        every { getDeviceId() } returns "device-abc"
    }
    private val downloader = SaveDownloader(
        context = mockk(relaxed = true),
        saveSyncDao = mockk(relaxed = true),
        saveCacheDao = mockk(relaxed = true),
        emulatorResolver = mockk(relaxed = true),
        gameDao = mockk(relaxed = true),
        titleDbRepository = mockk(relaxed = true),
        titleIdExtractor = mockk(relaxed = true),
        saveArchiver = mockk(relaxed = true),
        savePathResolver = mockk(relaxed = true),
        saveCacheManager = dagger.Lazy { mockk(relaxed = true) },
        fal = mockk(relaxed = true),
        switchSaveHandler = mockk(relaxed = true),
        gciSaveHandler = mockk(relaxed = true),
        apiClient = dagger.Lazy { mockApiClient },
        saveUploader = dagger.Lazy { mockk(relaxed = true) }
    )

    @Test
    fun `confirmDeviceSynced calls confirmSaveDownloaded with correct saveId and deviceId`() = runTest {
        coEvery { api.confirmSaveDownloaded(42L, RomMDeviceIdRequest("device-abc")) } returns
            Response.success(RomMSave(id = 42L, romId = 1L, userId = 1L, emulator = null, fileName = "save.sav", updatedAt = ""))

        downloader.confirmDeviceSynced(42L)

        coVerify { api.confirmSaveDownloaded(42L, RomMDeviceIdRequest("device-abc")) }
    }

    @Test
    fun `confirmDeviceSynced does not throw on HTTP error`() = runTest {
        coEvery { api.confirmSaveDownloaded(any(), any()) } returns
            Response.error(500, ResponseBody.create(null, ""))

        downloader.confirmDeviceSynced(99L)

        coVerify { api.confirmSaveDownloaded(99L, any()) }
    }

    @Test
    fun `confirmDeviceSynced does not throw on network exception`() = runTest {
        coEvery { api.confirmSaveDownloaded(any(), any()) } throws RuntimeException("timeout")

        downloader.confirmDeviceSynced(7L)
    }

    @Test
    fun `confirmDeviceSynced skips api call when api is null`() = runTest {
        every { mockApiClient.getApi() } returns null

        downloader.confirmDeviceSynced(1L)

        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }

    @Test
    fun `confirmDeviceSynced skips api call when deviceId is null`() = runTest {
        every { mockApiClient.getDeviceId() } returns null

        downloader.confirmDeviceSynced(1L)

        coVerify(exactly = 0) { api.confirmSaveDownloaded(any(), any()) }
    }
}
