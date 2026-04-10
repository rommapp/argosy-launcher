package com.nendo.argosy.data.netplay

import android.util.Log
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.util.ArrayDeque
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

class NetplayGuestDriver(
    private val retroView: GLRetroView,
    private val transport: NetplayTransport,
    private val peerAddress: InetSocketAddress,
    val peerUserId: String,
    private val localPort: Int,
    private val hostPort: Int,
    val inputShadow: NetplayInputShadow,
    private val scope: CoroutineScope,
    private val onSessionEnd: (reason: String) -> Unit,
    private val catchupThresholdFrames: Int = DEFAULT_CATCHUP_THRESHOLD,
    private val libretroOps: LibretroNetplayOps = RealLibretroNetplayOps
) : NetplayDriver {

    @Volatile private var currentFrame: Long = 0L
    @Volatile private var lastKnownHostFrame: Long = 0L
    @Volatile private var lastLocalBitmask: Int = 0
    @Volatile private var lastGuestInputFrame: Long = -GUEST_REASSERT_INTERVAL
    @Volatile private var lastHeartbeatNanos: Long = 0L
    @Volatile var lastRttNanos: Long = 0L
        private set
    @Volatile private var catchingUp: Boolean = false
    @Volatile private var stopped = false

    @Volatile var shouldSkipRender: Boolean = false

    val framesBehindHost: Long
        get() = (lastKnownHostFrame - currentFrame).coerceAtLeast(0L)

    private val incomingRing = Channel<NetplayTransport.Incoming>(capacity = 256)
    private val pendingBundles = TreeMap<Long, NetplayPacket.InputBundle>()
    private val reassembly = ConcurrentHashMap<Int, ReassemblyBuffer>()
    private var activeReassemblyId: Int? = null
    private var lastAckEmitNanos = 0L

    private val receiveJob: Job = scope.launch {
        transport.incomingPackets.collect { incoming ->
            if (incoming.source.address == peerAddress.address &&
                incoming.source.port == peerAddress.port) {
                incomingRing.trySend(incoming)
            }
        }
    }

    private val reassemblyTimerJob: Job = scope.launch(Dispatchers.IO) {
        while (isActive && !stopped) {
            try {
                kotlinx.coroutines.delay(REASSEMBLY_TICK_MS)
                maybeEmitAck()
                maybeTimeoutReassembly()
            } catch (_: Throwable) {
            }
        }
    }

    fun applyInitialSnapshot(state: ByteArray): Boolean {
        return try {
            retroView.unserializeState(state)
        } catch (t: Throwable) {
            Log.w(TAG, "unserializeState initial failed: ${t.message}")
            false
        }
    }

    override fun tick() {
        if (stopped) return

        drainIncoming()

        if (!catchingUp) {
            while (true) {
                val bundle = pendingBundles.firstEntry() ?: break
                if (bundle.key != currentFrame) {
                    if (bundle.key < currentFrame) {
                        pendingBundles.pollFirstEntry()
                        continue
                    }
                    break
                }
                val entry = pendingBundles.pollFirstEntry() ?: break
                applyBundle(entry.value)
                currentFrame++
            }
        }

        if (framesBehindHost > catchupThresholdFrames && !catchingUp) {
            catchingUp = true
            dispatchSend(NetplayPacket.SnapshotRequest(reasonCode = REASON_CATCHUP))
            Log.d(TAG, "requesting catchup snapshot (behind=$framesBehindHost)")
        }

        sampleAndSendLocalInput()

        val nowNanos = System.nanoTime()
        if (nowNanos - lastHeartbeatNanos >= HEARTBEAT_INTERVAL_NANOS) {
            lastHeartbeatNanos = nowNanos
            dispatchSend(NetplayPacket.Ping(nowNanos))
        }
    }

    private fun applyBundle(bundle: NetplayPacket.InputBundle) {
        bundle.ports.forEach { port ->
            libretroOps.setInputPortState(port.port, port.bitmask)
        }
        libretroOps.stepForNetplay(retroView)
    }

    private fun sampleAndSendLocalInput() {
        val current = inputShadow.sample(localPort)
        val reassertDue = currentFrame - lastGuestInputFrame >= GUEST_REASSERT_INTERVAL
        if (current != lastLocalBitmask || reassertDue) {
            lastLocalBitmask = current
            lastGuestInputFrame = currentFrame
            dispatchSend(
                NetplayPacket.GuestInput(
                    guestFrameIndex = currentFrame,
                    portNumber = localPort,
                    bitmask = current
                )
            )
        }
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        receiveJob.cancel()
        reassemblyTimerJob.cancel()
        pendingBundles.clear()
        reassembly.clear()
        scope.launch {
            runCatching { transport.send(peerAddress, NetplayPacket.SessionControl.Goodbye) }
            runCatching { transport.close() }
        }
    }

    private fun drainIncoming() {
        while (true) {
            val incoming = incomingRing.tryReceive().getOrNull() ?: break
            when (val packet = incoming.packet) {
                is NetplayPacket.InputBundle -> handleInputBundle(packet)
                is NetplayPacket.SnapshotChunk -> handleSnapshotChunk(packet)
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
                is NetplayPacket.GuestInput,
                is NetplayPacket.SnapshotRequest,
                is NetplayPacket.SessionControl.SnapshotAck -> {
                }
            }
        }
    }

    private fun handleInputBundle(bundle: NetplayPacket.InputBundle) {
        if (bundle.frameIndex > lastKnownHostFrame) lastKnownHostFrame = bundle.frameIndex
        if (bundle.frameIndex < currentFrame) return
        pendingBundles[bundle.frameIndex] = bundle
    }

    private fun handleSnapshotChunk(chunk: NetplayPacket.SnapshotChunk) {
        val activeId = activeReassemblyId
        if (activeId != null && chunk.snapshotId != activeId && chunk.snapshotId > activeId) {
            reassembly.remove(activeId)
            activeReassemblyId = null
        }
        val buffer = reassembly.getOrPut(chunk.snapshotId) {
            ReassemblyBuffer(chunk.snapshotId, chunk.chunkTotal)
        }
        if (activeReassemblyId == null || chunk.snapshotId > (activeReassemblyId ?: Int.MIN_VALUE)) {
            activeReassemblyId = chunk.snapshotId
        }
        buffer.addChunk(chunk)
        if (buffer.isComplete()) {
            val reassembled = buffer.assemble()
            reassembly.remove(chunk.snapshotId)
            if (activeReassemblyId == chunk.snapshotId) activeReassemblyId = null
            if (reassembled != null) {
                applySnapshot(chunk.snapshotId, reassembled)
            }
        }
    }

    private fun applySnapshot(snapshotId: Int, bytes: ByteArray) {
        try {
            val ok = retroView.unserializeState(bytes)
            if (!ok) {
                Log.w(TAG, "unserializeState returned false for snapshot $snapshotId")
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "unserializeState threw: ${t.message}")
            return
        }
        currentFrame = lastKnownHostFrame
        pendingBundles.clear()
        catchingUp = false
    }

    private fun maybeEmitAck() {
        val now = System.nanoTime()
        if (now - lastAckEmitNanos < ACK_INTERVAL_NANOS) return
        val buffer = reassembly[activeReassemblyId ?: return] ?: return
        val acknowledged = buffer.receivedIndices()
        if (acknowledged.isEmpty()) return
        lastAckEmitNanos = now
        dispatchSend(
            NetplayPacket.SessionControl.SnapshotAck(
                snapshotId = buffer.snapshotId,
                acknowledgedChunks = acknowledged
            )
        )
    }

    private fun maybeTimeoutReassembly() {
        val now = System.nanoTime()
        val stale = reassembly.values.filter { now - it.lastProgressNanos > REASSEMBLY_TIMEOUT_NANOS }
        stale.forEach { buffer ->
            reassembly.remove(buffer.snapshotId)
            if (activeReassemblyId == buffer.snapshotId) activeReassemblyId = null
            Log.d(TAG, "reassembly timeout on snapshot ${buffer.snapshotId}")
            if (catchingUp) {
                dispatchSend(NetplayPacket.SnapshotRequest(reasonCode = REASON_CATCHUP))
            }
        }
    }

    private fun dispatchSend(packet: NetplayPacket) {
        scope.launch {
            runCatching { transport.send(peerAddress, packet) }
        }
    }

    internal class ReassemblyBuffer(
        val snapshotId: Int,
        val total: Int
    ) {
        val received = TreeMap<Int, ByteArray>()
        @Volatile var lastProgressNanos: Long = System.nanoTime()

        @Synchronized
        fun addChunk(chunk: NetplayPacket.SnapshotChunk) {
            if (chunk.chunkIndex !in received) {
                received[chunk.chunkIndex] = chunk.payload
                lastProgressNanos = System.nanoTime()
            }
        }

        @Synchronized
        fun isComplete(): Boolean = received.size >= total

        @Synchronized
        fun receivedIndices(): List<Int> = received.keys.toList()

        @Synchronized
        fun assemble(): ByteArray? {
            if (received.size < total) return null
            val totalBytes = received.values.sumOf { it.size }
            val out = ByteArray(totalBytes)
            var offset = 0
            for (idx in 0 until total) {
                val piece = received[idx] ?: return null
                System.arraycopy(piece, 0, out, offset, piece.size)
                offset += piece.size
            }
            return out
        }
    }

    companion object {
        private const val TAG = "NetplayGuestDriver"
        private const val HEARTBEAT_INTERVAL_NANOS = 250_000_000L
        private const val ACK_INTERVAL_NANOS = 50_000_000L
        private const val REASSEMBLY_TIMEOUT_NANOS = 1_000_000_000L
        private const val REASSEMBLY_TICK_MS = 50L
        private const val GUEST_REASSERT_INTERVAL = 10L
        private const val REASON_CATCHUP = 1
        const val DEFAULT_CATCHUP_THRESHOLD = 30
    }
}
