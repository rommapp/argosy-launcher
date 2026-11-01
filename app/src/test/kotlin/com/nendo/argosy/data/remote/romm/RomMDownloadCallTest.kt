package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.PlatformDao
import com.nendo.argosy.data.remote.ssl.UserCertStore
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RomMDownloadCallTest {
    private fun client(server: MockWebServer): RomMApiClient {
        val certs = mockk<UserCertStore>()
        every { certs.trustManager() } returns null
        val api = RomMApiFactory(certs, mockk()).create(
            server.url("/romm/").toString(), "synthetic-test-token", probeTimeoutSeconds = 5
        )
        val connection = mockk<RomMConnectionManager>()
        every { connection.getApi() } returns api
        return RomMApiClient(connection, mockk<PlatformDao>())
    }

    @Test
    fun `selected ROM preserves encoding headers and factory authentication`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(206).setHeader("ETag", "\"v1\"").setBody("part"))
            val client = client(server)
            val call = client.newDownloadCall(42, "Game%20%23%25.zip", "bytes=4-", "9,3", "\"v1\"")
            val response = call.execute()
            response.body()!!.use { assertEquals("part", it.string()) }
            assertEquals(206, response.code())
            assertEquals("\"v1\"", response.headers()["ETag"])
            val request = server.takeRequest()
            assertEquals("/romm/api/roms/42/content/Game%20%23%25.zip?file_ids=9%2C3", request.path)
            assertEquals("bytes=4-", request.getHeader("Range"))
            assertEquals("\"v1\"", request.getHeader("If-Range"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
            assertEquals("Bearer synthetic-test-token", request.getHeader("Authorization"))
            val key = client.downloadRequestKey(42, "Game%20%23%25.zip", "9,3")
            assertEquals(call.request().url.toString(), key)
            assertFalse(key.contains("synthetic-test-token"))
            assertNotEquals(key, client.downloadRequestKey(42, "Game%20%23%25.zip", "3,9"))
        }
    }

    @Test
    fun `game file uses file endpoint and fresh cancellable calls`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("file"))
            val client = client(server)
            val first = client.newDownloadCall(7, "Disc%201.bin", isGameFile = true)
            first.cancel()
            assertTrue(first.isCanceled)
            val second = client.newDownloadCall(7, "Disc%201.bin", isGameFile = true)
            assertFalse(second.isCanceled)
            second.execute().body()!!.close()
            val request = server.takeRequest()
            assertEquals("/romm/api/roms/7/files/content/Disc%201.bin", request.path)
            assertNull(request.getHeader("Range"))
            assertNull(request.getHeader("If-Range"))
            assertEquals("identity", request.getHeader("Accept-Encoding"))
            assertNotEquals(client.downloadRequestKey(7, "Disc%201.bin"), client.downloadRequestKey(7, "Disc%201.bin", isGameFile = true))
        }
    }

    @Test(expected = IOException::class)
    fun `disconnected factory is recoverable IO failure`() {
        val connection = mockk<RomMConnectionManager>()
        every { connection.getApi() } returns null
        RomMApiClient(connection, mockk()).newDownloadCall(1, "test.bin")
    }
}
