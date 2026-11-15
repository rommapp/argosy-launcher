package com.nendo.argosy.data.remote.romm

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RomMReachabilityTest {

    private val root = "https://romm.example/"

    private fun answering(code: Int): Call.Factory {
        val request = slot<Request>()
        val factory = mockk<Call.Factory>()
        every { factory.newCall(capture(request)) } answers {
            mockk<Call> {
                every { execute() } returns Response.Builder()
                    .request(request.captured)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("status $code")
                    .body("".toResponseBody())
                    .build()
            }
        }
        return factory
    }

    private fun failingWith(error: IOException): Call.Factory {
        val factory = mockk<Call.Factory>()
        every { factory.newCall(any()) } returns mockk<Call> {
            every { execute() } throws error
        }
        return factory
    }

    @Test
    fun `a 200 heartbeat reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(200), root))
    }

    @Test
    fun `a 401 heartbeat reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(401), root))
    }

    @Test
    fun `a proxy redirect reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(302), root))
    }

    @Test
    fun `an error from the server itself reads as reachable`() {
        assertTrue(heartbeatGotAnyResponse(answering(500), root))
    }

    @Test
    fun `a proxy with no server behind it reads as unreachable`() {
        listOf(502, 503, 504).forEach { code ->
            assertFalse("status $code", heartbeatGotAnyResponse(answering(code), root))
        }
    }

    private fun onlineReachability(): RomMReachability {
        val capabilities = mockk<NetworkCapabilities> {
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
        }
        val network = mockk<Network>()
        val connectivity = mockk<ConnectivityManager> {
            every { activeNetwork } returns network
            every { getNetworkCapabilities(network) } returns capabilities
        }
        val context = mockk<Context> {
            every { getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivity
        }
        return RomMReachability(context, mockk(relaxed = true), mockk(relaxed = true))
    }

    private fun chainAnswering(code: Int, path: String = "api/saves"): Interceptor.Chain {
        val request = Request.Builder().url(root + path).build()
        return mockk {
            every { request() } returns request
            every { call() } returns mockk { every { isCanceled() } returns false }
            every { proceed(any()) } returns Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("status $code")
                .body("".toResponseBody())
                .build()
        }
    }

    @Test
    fun `a gateway failure on an api call fails the next call fast`() {
        val reachability = onlineReachability()
        reachability.recordReachable(root)

        reachability.interceptor.intercept(chainAnswering(502))

        val next = chainAnswering(200)
        assertThrows(RomMUnreachableException::class.java) { reachability.interceptor.intercept(next) }
        verify(exactly = 0) { next.proceed(any()) }
    }

    @Test
    fun `an error from the server itself on an api call keeps it reachable`() {
        val reachability = onlineReachability()
        reachability.recordReachable(root)
        val mark = reachability.mark()

        reachability.interceptor.intercept(chainAnswering(500))

        assertTrue(reachability.answeredSince(root, mark))
    }

    @Test
    fun `a transport failure reads as unreachable`() {
        assertFalse(heartbeatGotAnyResponse(failingWith(IOException("connection refused")), root))
    }

    @Test
    fun `the probe targets the heartbeat path under the server root`() {
        val request = slot<Request>()
        val factory = mockk<Call.Factory>()
        every { factory.newCall(capture(request)) } returns mockk<Call> {
            every { execute() } throws IOException("timeout")
        }

        heartbeatGotAnyResponse(factory, root)

        assertEquals("https://romm.example/api/heartbeat", request.captured.url.toString())
    }

    private class Harness(var serverUp: Boolean) {
        var clock = 0L
        var probes = 0
        val queued = mutableListOf<Runnable>()
        val ledger = ReachabilityLedger(
            now = { clock },
            probe = { probes++; serverUp },
            runInBackground = { queued += it }
        )

        fun runQueued() {
            val pending = queued.toList()
            queued.clear()
            pending.forEach { it.run() }
        }
    }

    @Test
    fun `a dead server answers every later launch at once and re-probes off the caller`() {
        val h = Harness(serverUp = false)

        assertFalse(h.ledger.isReachable(root))
        assertEquals(1, h.probes)

        h.clock = 60_000L
        assertFalse(h.ledger.isReachable(root))
        assertFalse(h.ledger.isReachable(root))
        assertEquals("no launch waits on a probe", 1, h.probes)
        assertEquals("one background re-probe at a time", 1, h.queued.size)

        h.runQueued()
        assertEquals(2, h.probes)
        assertFalse(h.ledger.isReachable(root))
    }

    @Test
    fun `a background re-probe that answers lets the next launch through`() {
        val h = Harness(serverUp = false)
        h.ledger.isReachable(root)
        h.clock = 60_000L
        h.ledger.isReachable(root)

        h.serverUp = true
        h.runQueued()

        assertTrue(h.ledger.isReachable(root))
        assertEquals(2, h.probes)
    }

    @Test
    fun `a failure inside the fresh window schedules nothing`() {
        val h = Harness(serverUp = false)
        h.ledger.isReachable(root)
        h.clock = 5_000L

        assertFalse(h.ledger.isReachable(root))
        assertTrue(h.queued.isEmpty())
    }

    @Test
    fun `only an answer after the mark counts as the server answering`() {
        val h = Harness(serverUp = true)
        h.clock = 1_000L
        h.ledger.recordReachable(root)
        h.clock = 2_000L
        val mark = h.clock

        assertFalse(h.ledger.answeredSince(root, mark))

        h.clock = 3_000L
        h.ledger.recordReachable(root)
        assertTrue(h.ledger.answeredSince(root, mark))

        h.ledger.recordUnreachable(root)
        assertFalse(h.ledger.answeredSince(root, mark))
    }

    @Test
    fun `a successful call clears a recorded failure`() {
        val h = Harness(serverUp = false)
        h.ledger.isReachable(root)

        h.ledger.recordReachable(root)

        assertTrue(h.ledger.isReachable(root))
        assertEquals(1, h.probes)
    }
}
