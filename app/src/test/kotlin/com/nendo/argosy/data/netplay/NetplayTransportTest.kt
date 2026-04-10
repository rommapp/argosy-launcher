package com.nendo.argosy.data.netplay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.time.Duration.Companion.seconds

class NetplayTransportTest {

    private lateinit var hostSocket: DatagramSocket
    private lateinit var guestSocket: DatagramSocket
    private lateinit var scope: CoroutineScope
    private val masterKey = ByteArray(32) { (it * 7).toByte() }

    @Before
    fun setUp() {
        val loopback = InetAddress.getByName("127.0.0.1")
        hostSocket = DatagramSocket(0, loopback)
        guestSocket = DatagramSocket(0, loopback)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    @After
    fun tearDown() {
        runCatching { hostSocket.close() }
        runCatching { guestSocket.close() }
        scope.cancel()
    }

    @Test
    fun loopback_roundTrip_hostToGuest() = runBlocking {
        val hostCrypto = PacketCrypto.fromMasterKey(masterKey.copyOf())
        val guestCrypto = PacketCrypto.fromMasterKey(masterKey.copyOf())
        val hostTransport = NetplayTransport(hostCrypto, hostSocket, PacketCrypto.Direction.HostToGuest, scope)
        val guestTransport = NetplayTransport(guestCrypto, guestSocket, PacketCrypto.Direction.GuestToHost, scope)
        try {
            val hostAddr = InetSocketAddress(InetAddress.getByName("127.0.0.1"), hostSocket.localPort)
            val guestAddr = InetSocketAddress(InetAddress.getByName("127.0.0.1"), guestSocket.localPort)

            val receivedDeferred = async {
                withTimeoutOrNull(3.seconds) { guestTransport.incomingPackets.first() }
            }
            delay(50)
            hostTransport.send(guestAddr, NetplayPacket.Ping(timestampNanos = 1234567L))
            val received = receivedDeferred.await()
            assertTrue(received != null && received.packet is NetplayPacket.Ping)
            assertEquals(1234567L, (received!!.packet as NetplayPacket.Ping).timestampNanos)
            assertEquals(hostAddr.port, received.source.port)
        } finally {
            hostTransport.close()
            guestTransport.close()
        }
    }

    @Test
    fun replayedDatagram_dropped() = runBlocking {
        val hostCrypto = PacketCrypto.fromMasterKey(masterKey.copyOf())
        val guestCrypto = PacketCrypto.fromMasterKey(masterKey.copyOf())
        val guestTransport = NetplayTransport(guestCrypto, guestSocket, PacketCrypto.Direction.GuestToHost, scope)
        try {
            val packet = NetplayPacket.Ping(timestampNanos = 999L)
            val wire = hostCrypto.encrypt(packet.serialize(), PacketCrypto.Direction.HostToGuest)
            val dest = InetSocketAddress(InetAddress.getByName("127.0.0.1"), guestSocket.localPort)

            val firstDeferred = async {
                withTimeoutOrNull(3.seconds) { guestTransport.incomingPackets.first() }
            }
            delay(50)
            hostSocket.send(DatagramPacket(wire, wire.size, dest))
            val first = firstDeferred.await()
            assertTrue(first != null)

            val secondDeferred = async {
                withTimeoutOrNull(500.milliseconds) { guestTransport.incomingPackets.first() }
            }
            delay(50)
            hostSocket.send(DatagramPacket(wire, wire.size, dest))
            val second = secondDeferred.await()
            assertNull("replay should have been dropped", second)
        } finally {
            hostCrypto.close()
            guestTransport.close()
        }
    }

    @Test
    fun malformedCiphertext_dropped() = runBlocking {
        val guestCrypto = PacketCrypto.fromMasterKey(masterKey.copyOf())
        val guestTransport = NetplayTransport(guestCrypto, guestSocket, PacketCrypto.Direction.GuestToHost, scope)
        try {
            val garbage = ByteArray(64) { 0xAB.toByte() }
            val dest = InetSocketAddress(InetAddress.getByName("127.0.0.1"), guestSocket.localPort)
            val receivedDeferred = async {
                withTimeoutOrNull(500.milliseconds) { guestTransport.incomingPackets.first() }
            }
            delay(50)
            hostSocket.send(DatagramPacket(garbage, garbage.size, dest))
            val received = receivedDeferred.await()
            assertNull(received)
        } finally {
            guestTransport.close()
        }
    }

}
