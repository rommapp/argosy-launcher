package com.nendo.argosy.data.repository

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMCapabilities
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMUserScreenshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ScreenshotUploaderTest {

    private val api: RomMApi = mockk()
    private val connectionManager: RomMConnectionManager = mockk()
    private val capabilities: RomMCapabilities = mockk()
    private val dir: File = Files.createTempDirectory("uploader").toFile()
    private val uploader = ScreenshotUploader(connectionManager)

    @Before
    fun setup() {
        every { connectionManager.getApi() } returns api
        every { connectionManager.isConnected() } returns true
        every { connectionManager.getCapabilities() } returns capabilities
        every { capabilities.supportsScreenshotUpload } returns true
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `the upload carries the copy's original file name`() = runTest {
        val part = slot<MultipartBody.Part>()
        coEvery { api.uploadScreenshot(ROMM_ID, capture(part)) } returns Response.success(mockk<RomMUserScreenshot>(relaxed = true))

        val result = uploader.upload(copy("Screenshot_20261109-101500.png"), ROMM_ID)

        assertEquals(ScreenshotUploader.Result.Success, result)
        val disposition = part.captured.headers?.get("Content-Disposition").orEmpty()
        assertTrue(disposition, disposition.contains("filename=\"Screenshot_20261109-101500.png\""))
    }

    @Test
    fun `permanent HTTP refusals are rejections`() = runTest {
        for (code in listOf(400, 404, 413, 415)) {
            coEvery { api.uploadScreenshot(any(), any()) } returns Response.error(code, "".toResponseBody())

            val result = uploader.upload(copy("a.png"), ROMM_ID)

            assertTrue("HTTP $code gave $result", result is ScreenshotUploader.Result.Rejected)
        }
    }

    @Test
    fun `server errors, missing scope and network failures are transient`() = runTest {
        for (code in listOf(500, 502, 503, 401, 403, 408, 429)) {
            coEvery { api.uploadScreenshot(any(), any()) } returns Response.error(code, "".toResponseBody())

            val result = uploader.upload(copy("a.png"), ROMM_ID)

            assertTrue("HTTP $code gave $result", result is ScreenshotUploader.Result.Failed)
        }
        coEvery { api.uploadScreenshot(any(), any()) } throws IOException("timeout")

        assertTrue(uploader.upload(copy("a.png"), ROMM_ID) is ScreenshotUploader.Result.Failed)
    }

    @Test
    fun `an extension the server does not take is rejected without a request`() = runTest {
        val result = uploader.upload(copy("clip.heic"), ROMM_ID)

        assertTrue(result is ScreenshotUploader.Result.Rejected)
        coVerify(exactly = 0) { api.uploadScreenshot(any(), any()) }
    }

    private fun copy(name: String): File = File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    private companion object {
        const val ROMM_ID = 77L
    }
}
