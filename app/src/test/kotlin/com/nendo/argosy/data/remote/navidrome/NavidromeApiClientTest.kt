package com.nendo.argosy.data.remote.navidrome

import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.squareup.moshi.Moshi
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NavidromeApiClientTest {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(NavidromeEnvelope::class.java)

    private val failedBody = """
        {"subsonic-response":{"status":"failed","version":"1.16.1","type":"navidrome",
        "serverVersion":"0.64.2","openSubsonic":true,
        "error":{"code":40,"message":"Wrong username or password"}}}
    """.trimIndent()

    private fun client(): NavidromeApiClient {
        val certs = mockk<UserCertStore>()
        every { certs.trustManager() } returns null
        return NavidromeApiClient(moshi, certs)
    }

    @Test
    fun `a failed status throws the server code and message`() {
        try {
            NavidromeApiClient.parseResponse(failedBody, adapter::fromJson)
            fail("A failed status must throw")
        } catch (e: NavidromeApiException) {
            assertEquals(40, e.code)
            assertEquals("Wrong username or password", e.serverMessage)
        }
    }

    @Test
    fun `a failed status without a message keeps the code and no server message`() {
        val body = """{"subsonic-response":{"status":"failed","error":{"code":70}}}"""
        try {
            NavidromeApiClient.parseResponse(body, adapter::fromJson)
            fail("A failed status must throw")
        } catch (e: NavidromeApiException) {
            assertEquals(70, e.code)
            assertNull(e.serverMessage)
        }
    }

    @Test
    fun `an ok status returns the response`() {
        val body = """{"subsonic-response":{"status":"ok","version":"1.16.1","serverVersion":"0.64.2"}}"""
        val response = NavidromeApiClient.parseResponse(body, adapter::fromJson)
        assertEquals("0.64.2", response.serverVersion)
    }

    @Test
    fun `ping against a refusing server fails with its message and sends token auth`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody(failedBody))
            val credentials = NavidromeCredentials(server.url("/music").toString(), "alice", "sesame")

            val result = client().ping(credentials)

            val error = result.exceptionOrNull()
            assertTrue(error is NavidromeApiException)
            assertEquals("Wrong username or password", (error as NavidromeApiException).serverMessage)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/music/rest/ping", url.encodedPath)
            assertEquals("alice", url.queryParameter("u"))
            assertEquals("1.16.1", url.queryParameter("v"))
            assertEquals("argosy", url.queryParameter("c"))
            assertEquals("json", url.queryParameter("f"))
            val salt = url.queryParameter("s")!!
            assertEquals(NavidromeAuth.token("sesame", salt), url.queryParameter("t"))
            assertNull(url.queryParameter("p"))
        }
    }
}
