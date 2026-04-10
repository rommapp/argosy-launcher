package com.nendo.argosy.data.netplay

import android.util.Log
import com.goterl.lazysodium.interfaces.AEAD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException

class NetplayTransport(
    private val crypto: PacketCrypto,
    private val socket: DatagramSocket,
    private val localDirection: PacketCrypto.Direction,
    scope: CoroutineScope
) : AutoCloseable {

    private val _incomingPackets = MutableSharedFlow<Incoming>(
        replay = 0,
        extraBufferCapacity = 256
    )
    val incomingPackets: SharedFlow<Incoming> = _incomingPackets.asSharedFlow()

    private val replayWindow = ReplayWindow()
    private val peerDirection: PacketCrypto.Direction = when (localDirection) {
        PacketCrypto.Direction.HostToGuest -> PacketCrypto.Direction.GuestToHost
        PacketCrypto.Direction.GuestToHost -> PacketCrypto.Direction.HostToGuest
    }

    private val receiveJob: Job = scope.launch(Dispatchers.IO) {
        receiveLoop()
    }

    data class Incoming(val source: InetSocketAddress, val packet: NetplayPacket)

    suspend fun send(peer: InetSocketAddress, packet: NetplayPacket) {
        val plaintext = packet.serialize()
        val wire = crypto.encrypt(plaintext, localDirection)
        withContext(Dispatchers.IO) {
            try {
                socket.send(DatagramPacket(wire, wire.size, peer))
            } catch (se: SocketException) {
                if (!socket.isClosed) throw se
            }
        }
    }

    private suspend fun receiveLoop() {
        val buffer = ByteArray(MAX_DATAGRAM_SIZE)
        val dgram = DatagramPacket(buffer, buffer.size)
        while (scopeActive()) {
            try {
                socket.receive(dgram)
            } catch (_: SocketException) {
                return
            } catch (t: Throwable) {
                if (!socket.isClosed) Log.d(TAG, "receive error: ${t.message}")
                return
            }
            val wire = dgram.data.copyOfRange(dgram.offset, dgram.offset + dgram.length)
            handleDatagram(wire, InetSocketAddress(dgram.address, dgram.port))
        }
    }

    private suspend fun handleDatagram(wire: ByteArray, source: InetSocketAddress) {
        if (wire.size < AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES + AEAD.XCHACHA20POLY1305_IETF_ABYTES) return
        val nonce = wire.copyOfRange(0, AEAD.XCHACHA20POLY1305_IETF_NPUBBYTES)
        if (!replayWindow.checkAndRecord(nonce)) {
            Log.d(TAG, "dropped replayed datagram from $source")
            return
        }
        val plaintext = crypto.decrypt(wire, peerDirection) ?: run {
            Log.d(TAG, "dropped datagram with bad AEAD tag from $source")
            return
        }
        val packet = NetplayPacket.deserialize(plaintext) ?: run {
            Log.d(TAG, "dropped datagram with malformed plaintext from $source")
            return
        }
        _incomingPackets.emit(Incoming(source, packet))
    }

    private fun scopeActive(): Boolean = receiveJob.isActive

    override fun close() {
        try {
            socket.close()
        } catch (_: Throwable) {
        }
        receiveJob.cancel()
        crypto.close()
    }

    companion object {
        private const val TAG = "NetplayTransport"
        private const val MAX_DATAGRAM_SIZE = 2048
    }
}
