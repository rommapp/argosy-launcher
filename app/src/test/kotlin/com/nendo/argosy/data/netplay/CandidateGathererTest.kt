package com.nendo.argosy.data.netplay

import com.nendo.argosy.data.social.NetplayCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class CandidateGathererTest {

    private class FakeStunClient(
        private val result: InetSocketAddress?
    ) : StunClient() {
        var called = 0
            private set

        override suspend fun discoverReflexiveAddress(
            server: InetSocketAddress,
            localSocket: DatagramSocket?,
            timeout: kotlin.time.Duration
        ): InetSocketAddress? {
            called++
            return result
        }
    }

    private class FakeUpnpProbe(
        private val result: PortMapping?
    ) : UpnpProbe() {
        var called = 0
            private set

        override suspend fun requestPortMapping(
            localPort: Int,
            protocol: String,
            description: String,
            duration: kotlin.time.Duration
        ): PortMapping? {
            called++
            return result
        }
    }

    @Test
    fun gather_withWorkingStunAndUpnp_includesAll() = runBlocking {
        val stunAddr = InetSocketAddress(InetAddress.getByName("203.0.113.5"), 44444)
        val fakeStun = FakeStunClient(result = stunAddr)
        val fakeUpnp = FakeUpnpProbe(
            result = UpnpProbe.PortMapping(
                externalIp = "198.51.100.7",
                externalPort = 55555,
                internalPort = 55555,
                protocol = "UDP",
                gatewayDevice = org.bitlet.weupnp.GatewayDevice()
            )
        )
        val gatherer = CandidateGatherer(
            stunClient = fakeStun,
            upnpProbe = fakeUpnp,
            stunServers = listOf(InetSocketAddress("stun.example.com", 19302))
        )

        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val candidates = gatherer.gatherCandidates(socket)
            val stun = candidates.firstOrNull { it.type == "stun" }
            val upnp = candidates.firstOrNull { it.type == "upnp" }
            assertNotNull(stun)
            assertEquals("203.0.113.5", stun!!.address)
            assertEquals(44444, stun.port)
            assertNotNull(upnp)
            assertEquals("198.51.100.7", upnp!!.address)
            assertEquals(55555, upnp.port)
        } finally {
            socket.close()
        }
    }

    @Test
    fun gather_whenStunFails_stillReturnsOthers() = runBlocking {
        val fakeStun = FakeStunClient(result = null)
        val fakeUpnp = FakeUpnpProbe(result = null)
        val gatherer = CandidateGatherer(
            stunClient = fakeStun,
            upnpProbe = fakeUpnp,
            stunServers = listOf(InetSocketAddress("stun.example.com", 19302))
        )
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val candidates = gatherer.gatherCandidates(socket)
            assertTrue(candidates.none { it.type == "stun" })
            assertTrue(candidates.none { it.type == "upnp" })
        } finally {
            socket.close()
        }
    }

    @Test
    fun gather_neverThrows() = runBlocking {
        val throwingStun = object : StunClient() {
            override suspend fun discoverReflexiveAddress(
                server: InetSocketAddress,
                localSocket: DatagramSocket?,
                timeout: kotlin.time.Duration
            ): InetSocketAddress? = throw RuntimeException("stun unreachable")
        }
        val throwingUpnp = object : UpnpProbe() {
            override suspend fun requestPortMapping(
                localPort: Int,
                protocol: String,
                description: String,
                duration: kotlin.time.Duration
            ): PortMapping? = throw RuntimeException("upnp exploded")
        }
        val gatherer = CandidateGatherer(
            stunClient = throwingStun,
            upnpProbe = throwingUpnp,
            stunServers = listOf(InetSocketAddress("stun.example.com", 19302))
        )
        val socket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        try {
            val candidates = gatherer.gatherCandidates(socket)
            assertNull(candidates.firstOrNull { it.type == "stun" })
            assertNull(candidates.firstOrNull { it.type == "upnp" })
        } finally {
            socket.close()
        }
    }
}
