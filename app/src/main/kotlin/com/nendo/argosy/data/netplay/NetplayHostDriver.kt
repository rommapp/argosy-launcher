package com.nendo.argosy.data.netplay

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class NetplayHostDriver(
    private val retroView: GLRetroView,
    private val transport: NetplayTransport,
    private val peerAddress: InetSocketAddress,
    val peerUserId: String,
    private val localPort: Int,
    private val guestPort: Int,
    val inputShadow: NetplayInputShadow,
    private val scope: CoroutineScope,
    private val onSessionEnd: (reason: String) -> Unit
) : NetplayDriver {

    @Volatile private var currentFrame: Long = 0L
    @Volatile private var guestCurrentBitmask: Int = 0
    @Volatile private var lastHeartbeatNanos: Long = 0L
    @Volatile var lastRttNanos: Long = 0L
        private set
    @Volatile private var stopped = false

    private val incomingRing = Channel<NetplayTransport.Incoming>(capacity = 256)
    private val nextSnapshotId = AtomicInteger(1)
    private val pendingSnapshots = ConcurrentHashMap<Int, OutboundSnapshot>()

    private val receiveJob: Job = scope.launch {
        transport.incomingPackets.collect { incoming ->
            if (incoming.source.address == peerAddress.address &&
                incoming.source.port == peerAddress.port) {
                incomingRing.trySend(incoming)
            }
        }
    }

    private val snapshotRetransmitJob: Job = scope.launch(Dispatchers.IO) {
        while (isActive && !stopped) {
            try {
                kotlinx.coroutines.delay(SNAPSHOT_RETRANSMIT_INTERVAL_MS)
                retransmitUnackedChunks()
            } catch (_: Throwable) {
            }
        }
    }

    override fun tick() {
        if (stopped) return

        drainIncoming()

        val localBitmask = inputShadow.sample(localPort)
        LibretroDroid.setInputPortState(localPort, localBitmask)
        LibretroDroid.setInputPortState(guestPort, guestCurrentBitmask)

        LibretroDroid.stepForNetplay(retroView)

        val bundle = NetplayPacket.InputBundle(
            frameIndex = currentFrame,
            ports = listOf(
                NetplayPacket.PortInputState(localPort, localBitmask),
                NetplayPacket.PortInputState(guestPort, guestCurrentBitmask)
            )
        )
        dispatchSend(bundle)

        currentFrame++

        val nowNanos = System.nanoTime()
        if (nowNanos - lastHeartbeatNanos >= HEARTBEAT_INTERVAL_NANOS) {
            lastHeartbeatNanos = nowNanos
            dispatchSend(NetplayPacket.Ping(nowNanos))
        }
    }

    suspend fun sendInitialSnapshot(state: ByteArray): Int {
        val snapshotId = 0
        enqueueSnapshot(snapshotId, state)
        return snapshotId
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        receiveJob.cancel()
        snapshotRetransmitJob.cancel()
        pendingSnapshots.clear()
        scope.launch {
            runCatching { transport.send(peerAddress, NetplayPacket.SessionControl.Goodbye) }
            runCatching { transport.close() }
        }
    }

    private fun drainIncoming() {
        while (true) {
            val incoming = incomingRing.tryReceive().getOrNull() ?: break
            when (val packet = incoming.packet) {
                is NetplayPacket.GuestInput -> handleGuestInput(packet)
                is NetplayPacket.SnapshotRequest -> handleSnapshotRequest(packet)
                is NetplayPacket.Ping -> dispatchSend(NetplayPacket.Pong(packet.timestampNanos))
                is NetplayPacket.Pong -> {
                    val rtt = System.nanoTime() - packet.timestampNanos
                    if (rtt > 0) lastRttNanos = rtt
                }
                is NetplayPacket.SessionControl.Goodbye -> {
                    onSessionEnd("peer_goodbye")
                    return
                }
                is NetplayPacket.SessionControl.Kick -> {
                    onSessionEnd("peer_kick")
                    return
                }
                is NetplayPacket.SessionControl.SnapshotAck -> handleSnapshotAck(packet)
                is NetplayPacket.InputBundle,
                is NetplayPacket.SnapshotChunk -> {
                }
            }
        }
    }

    private fun handleGuestInput(packet: NetplayPacket.GuestInput) {
        if (packet.portNumber != guestPort) {
            Log.d(TAG, "dropping guest input for wrong port ${packet.portNumber} (expected $guestPort)")
            return
        }
        guestCurrentBitmask = packet.bitmask
    }

    private fun handleSnapshotRequest(packet: NetplayPacket.SnapshotRequest) {
        scope.launch {
            try {
                val state = retroView.serializeState()
                val snapshotId = nextSnapshotId.getAndIncrement()
                enqueueSnapshot(snapshotId, state)
            } catch (t: Throwable) {
                Log.w(TAG, "serializeState failed: ${t.message}")
            }
        }
    }

    private fun enqueueSnapshot(snapshotId: Int, payload: ByteArray) {
        val total = ((payload.size + SNAPSHOT_CHUNK_BYTES - 1) / SNAPSHOT_CHUNK_BYTES).coerceAtLeast(1)
        val chunks = ArrayList<NetplayPacket.SnapshotChunk>(total)
        for (i in 0 until total) {
            val start = i * SNAPSHOT_CHUNK_BYTES
            val end = (start + SNAPSHOT_CHUNK_BYTES).coerceAtMost(payload.size)
            val slice = payload.copyOfRange(start, end)
            chunks.add(
                NetplayPacket.SnapshotChunk(
                    snapshotId = snapshotId,
                    chunkIndex = i,
                    chunkTotal = total,
                    payload = slice
                )
            )
        }
        val outstanding = (0 until total).toMutableSet()
        val attempts = IntArray(total)
        pendingSnapshots[snapshotId] = OutboundSnapshot(chunks, outstanding, attempts, startNanos = System.nanoTime())
        chunks.forEach { dispatchSend(it) }
    }

    private fun handleSnapshotAck(ack: NetplayPacket.SessionControl.SnapshotAck) {
        val snapshot = pendingSnapshots[ack.snapshotId] ?: return
        synchronized(snapshot) {
            ack.acknowledgedChunks.forEach { snapshot.outstanding.remove(it) }
            if (snapshot.outstanding.isEmpty()) {
                pendingSnapshots.remove(ack.snapshotId)
            }
        }
    }

    private fun retransmitUnackedChunks() {
        val ids = pendingSnapshots.keys.toList()
        for (id in ids) {
            val snapshot = pendingSnapshots[id] ?: continue
            val (toResend, giveUp) = synchronized(snapshot) {
                if (snapshot.outstanding.isEmpty()) {
                    return@synchronized emptyList<NetplayPacket.SnapshotChunk>() to false
                }
                val bumpedOut = ArrayList<NetplayPacket.SnapshotChunk>()
                var failed = false
                for (idx in snapshot.outstanding.toList()) {
                    snapshot.attempts[idx] += 1
                    if (snapshot.attempts[idx] > SNAPSHOT_MAX_ATTEMPTS) {
                        failed = true
                        break
                    }
                    bumpedOut.add(snapshot.chunks[idx])
                }
                bumpedOut to failed
            }
            if (giveUp) {
                pendingSnapshots.remove(id)
                continue
            }
            if (toResend.isEmpty()) {
                pendingSnapshots.remove(id)
                continue
            }
            toResend.forEach { dispatchSend(it) }
        }
    }

    private fun dispatchSend(packet: NetplayPacket) {
        scope.launch {
            runCatching { transport.send(peerAddress, packet) }
        }
    }

    private class OutboundSnapshot(
        val chunks: List<NetplayPacket.SnapshotChunk>,
        val outstanding: MutableSet<Int>,
        val attempts: IntArray,
        val startNanos: Long
    )

    companion object {
        private const val TAG = "NetplayHostDriver"
        private const val HEARTBEAT_INTERVAL_NANOS = 250_000_000L
        private const val SNAPSHOT_CHUNK_BYTES = 1280
        private const val SNAPSHOT_RETRANSMIT_INTERVAL_MS = 100L
        private const val SNAPSHOT_MAX_ATTEMPTS = 10
    }
}
