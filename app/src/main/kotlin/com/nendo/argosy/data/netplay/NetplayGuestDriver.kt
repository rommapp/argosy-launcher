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
import kotlin.collections.ArrayDeque
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

class NetplayGuestDriver(
    private val retroView: GLRetroView,
    private val transport: NetplayTransport,
    initialPeerAddress: InetSocketAddress,
    val peerUserId: String,
    private val localPort: Int,
    private val hostPort: Int,
    private val scope: CoroutineScope,
    private val onSessionEnd: (reason: String) -> Unit,
    private val catchupThresholdFrames: Int = DEFAULT_CATCHUP_THRESHOLD,
    private val libretroOps: LibretroNetplayOps = RealLibretroNetplayOps,
    private val framePeriodNanos: Long = FRAME_PERIOD_NANOS
) : NetplayDriver {

    @Volatile private var peerAddress: InetSocketAddress = initialPeerAddress
    @Volatile private var currentFrame: Long = 0L
    @Volatile private var lastKnownHostFrame: Long = 0L
    @Volatile private var lastLocalBitmask: Int = 0
    @Volatile private var lastGuestInputFrame: Long = -GUEST_REASSERT_INTERVAL
    @Volatile private var lastHeartbeatNanos: Long = 0L
    @Volatile override var lastRttNanos: Long = 0L
        private set
    @Volatile override var lastIncomingNanos: Long = System.nanoTime()
        private set
    @Volatile private var catchingUp: Boolean = false
    @Volatile private var stopped = false
    @Volatile private var nextTickTargetNanos: Long = System.nanoTime()

    private val latestHostInputs = mutableMapOf<Int, Int>()

    @Volatile var shouldSkipRender: Boolean = false

    private data class RollbackEntry(
        val frame: Long,
        val state: ByteArray,
        val p1Bitmask: Int,
        val p2Bitmask: Int,
        val wasSpeculative: Boolean
    )

    private val rollbackBuffer = ArrayDeque<RollbackEntry>(ROLLBACK_DEPTH)
    private var lastConfirmedP1: Int = 0

    val framesBehindHost: Long
        get() = (lastKnownHostFrame - currentFrame).coerceAtLeast(0L)

    private val incomingRing = Channel<NetplayTransport.Incoming>(capacity = 256)
    private val pendingBundles = TreeMap<Long, NetplayPacket.InputBundle>()
    private val reassembly = ConcurrentHashMap<Int, ReassemblyBuffer>()
    @Volatile private var activeReassemblyId: Int? = null
    @Volatile private var lastAppliedSnapshotId: Int = -1
    private var lastAckEmitNanos = 0L

    private val receiveJob: Job = scope.launch {
        transport.incomingPackets.collect { incoming ->
            peerAddress = incoming.source
            incomingRing.trySend(incoming)
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

        val now = System.nanoTime()
        if (now < nextTickTargetNanos) {
            libretroOps.renderFrameOnly()
            return
        }
        nextTickTargetNanos += framePeriodNanos
        if (nextTickTargetNanos < now - framePeriodNanos) {
            nextTickTargetNanos = now
        }

        drainIncoming()
        sampleAndSendLocalInput()

        if (!catchingUp) {
            while (pendingBundles.isNotEmpty() && pendingBundles.firstKey() < rollbackOldestFrame()) {
                val staleBundle = pendingBundles.pollFirstEntry().value
                updateLastConfirmedP1(staleBundle)
            }

            checkAndRollback()

            val p1 = lastConfirmedP1
            val p2 = lastLocalBitmask

            saveRollbackState(currentFrame, p1, p2, speculative = true)

            libretroOps.setInputPortState(hostPort, p1)
            libretroOps.setInputPortState(localPort, p2)
            libretroOps.stepForNetplay(retroView)
            currentFrame++
        }

        if (framesBehindHost > catchupThresholdFrames && !catchingUp) {
            catchingUp = true
            dispatchSend(NetplayPacket.SnapshotRequest(reasonCode = REASON_CATCHUP))
            Log.d(TAG, "requesting catchup snapshot (behind=$framesBehindHost)")
        }

        val nowNanos = System.nanoTime()
        if (nowNanos - lastHeartbeatNanos >= HEARTBEAT_INTERVAL_NANOS) {
            lastHeartbeatNanos = nowNanos
            dispatchSend(NetplayPacket.Ping(nowNanos))
        }
    }


    private fun extractPortBitmask(bundle: NetplayPacket.InputBundle, port: Int): Int {
        return bundle.ports.firstOrNull { it.port == port }?.bitmask ?: 0
    }

    private fun updateLastConfirmedP1(bundle: NetplayPacket.InputBundle) {
        lastConfirmedP1 = extractPortBitmask(bundle, hostPort)
    }

    private fun saveRollbackState(frame: Long, p1: Int, p2: Int, speculative: Boolean) {
        if (rollbackBuffer.size >= ROLLBACK_DEPTH) {
            rollbackBuffer.removeFirst()
        }
        val state = try {
            retroView.serializeState()
        } catch (t: Throwable) {
            Log.w(TAG, "serializeState failed for frame $frame: ${t.message}")
            return
        }
        rollbackBuffer.addLast(RollbackEntry(frame, state, p1, p2, speculative))
    }

    private fun checkAndRollback() {
        var earliestMispredictIdx = -1
        for (i in rollbackBuffer.indices) {
            val entry = rollbackBuffer[i]
            if (!entry.wasSpeculative) continue
            val bundle = pendingBundles[entry.frame] ?: continue
            val realP1 = extractPortBitmask(bundle, hostPort)
            val realP2 = extractPortBitmask(bundle, localPort)
            if (realP1 != entry.p1Bitmask || realP2 != entry.p2Bitmask) {
                earliestMispredictIdx = i
                break
            }
        }
        if (earliestMispredictIdx < 0) {
            confirmMatchedEntries()
            return
        }

        val rewindEntry = rollbackBuffer[earliestMispredictIdx]
        try {
            retroView.unserializeState(rewindEntry.state)
        } catch (t: Throwable) {
            Log.w(TAG, "rollback unserializeState failed: ${t.message}")
            return
        }

        val savedCurrentFrame = currentFrame
        currentFrame = rewindEntry.frame

        val replayRange = rollbackBuffer.size
        val entriesToReplay = ArrayList<RollbackEntry>(replayRange - earliestMispredictIdx)
        for (i in earliestMispredictIdx until replayRange) {
            entriesToReplay.add(rollbackBuffer[i])
        }
        while (rollbackBuffer.size > earliestMispredictIdx) {
            rollbackBuffer.removeLast()
        }

        for (entry in entriesToReplay) {
            val bundle = pendingBundles.remove(entry.frame)
            val p1: Int
            val p2: Int
            val speculative: Boolean

            if (bundle != null) {
                p1 = extractPortBitmask(bundle, hostPort)
                p2 = extractPortBitmask(bundle, localPort)
                lastConfirmedP1 = p1
                speculative = false
            } else {
                p1 = lastConfirmedP1
                p2 = entry.p2Bitmask
                speculative = true
            }

            if (rollbackBuffer.size >= ROLLBACK_DEPTH) {
                rollbackBuffer.removeFirst()
            }
            rollbackBuffer.addLast(RollbackEntry(entry.frame, entry.state, p1, p2, speculative))

            libretroOps.setInputPortState(hostPort, p1)
            libretroOps.setInputPortState(localPort, p2)
            libretroOps.stepForNetplay(retroView)
            currentFrame++
        }

        currentFrame = savedCurrentFrame
    }

    private fun rollbackOldestFrame(): Long {
        return if (rollbackBuffer.isNotEmpty()) rollbackBuffer.first().frame else currentFrame
    }

    private fun confirmMatchedEntries() {
        val iter = rollbackBuffer.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (!entry.wasSpeculative) continue
            val bundle = pendingBundles[entry.frame] ?: break
            val realP1 = extractPortBitmask(bundle, hostPort)
            val realP2 = extractPortBitmask(bundle, localPort)
            if (realP1 == entry.p1Bitmask && realP2 == entry.p2Bitmask) {
                pendingBundles.remove(entry.frame)
            } else {
                break
            }
        }
    }

    private fun sampleAndSendLocalInput() {
        val current = libretroOps.getInputPortBitmask(0)
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
        rollbackBuffer.clear()
        reassembly.clear()
        scope.launch {
            runCatching { transport.send(peerAddress, NetplayPacket.SessionControl.Goodbye) }
            runCatching { transport.close() }
        }
    }

    private var drainLogCounter = 0
    private fun drainIncoming() {
        var count = 0
        while (true) {
            val incoming = incomingRing.tryReceive().getOrNull() ?: break
            count++
            lastIncomingNanos = System.nanoTime()
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
                is NetplayPacket.SessionControl.SnapshotAck,
                is NetplayPacket.FrameInput,
                is NetplayPacket.DesyncCheck -> {
                }
            }
        }
        if (count > 0 && drainLogCounter++ % 60 == 0) {
            Log.d(TAG, "drain: $count packets, frame=$currentFrame hostFrame=$lastKnownHostFrame behind=$framesBehindHost catchingUp=$catchingUp pendingBundles=${pendingBundles.size}")
        }
    }

    private fun handleInputBundle(bundle: NetplayPacket.InputBundle) {
        if (bundle.frameIndex > lastKnownHostFrame + NetplaySecurityBounds.MAX_FRAME_LOOKAHEAD) return
        if (pendingBundles.size >= NetplaySecurityBounds.MAX_INPUT_MAP_ENTRIES) return
        if (bundle.frameIndex > lastKnownHostFrame) lastKnownHostFrame = bundle.frameIndex
        val oldestRollbackFrame = if (rollbackBuffer.isEmpty()) currentFrame
            else rollbackBuffer[0].frame
        if (bundle.frameIndex < oldestRollbackFrame) return
        pendingBundles[bundle.frameIndex] = bundle
    }

    private fun handleSnapshotChunk(chunk: NetplayPacket.SnapshotChunk) {
        if (chunk.chunkTotal <= 0 || chunk.chunkTotal > NetplaySecurityBounds.MAX_CHUNKS_PER_SNAPSHOT) return
        if (chunk.chunkIndex < 0 || chunk.chunkIndex >= chunk.chunkTotal) return
        val nowNanos = System.nanoTime()
        reassembly.entries.removeAll { (_, buf) -> nowNanos - buf.createdNanos > NetplaySecurityBounds.REASSEMBLY_TTL_NANOS }
        if (!reassembly.containsKey(chunk.snapshotId) && reassembly.size >= NetplaySecurityBounds.MAX_CONCURRENT_SNAPSHOTS) return
        if (chunk.snapshotId <= lastAppliedSnapshotId) return
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
            val snapshotFrame = buffer.snapshotFrame
            val reassembled = buffer.assemble()
            reassembly.remove(chunk.snapshotId)
            if (activeReassemblyId == chunk.snapshotId) activeReassemblyId = null
            if (reassembled != null) {
                applySnapshot(chunk.snapshotId, reassembled, snapshotFrame)
            }
        }
    }

    private fun applySnapshot(snapshotId: Int, bytes: ByteArray, snapshotFrame: Long) {
        Log.d(TAG, "applySnapshot: id=$snapshotId snapshotFrame=$snapshotFrame size=${bytes.size} currentFrame=$currentFrame hostFrame=$lastKnownHostFrame")
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
        lastAppliedSnapshotId = snapshotId
        currentFrame = if (snapshotFrame == 0L) 0L else snapshotFrame + 1
        pendingBundles.clear()
        rollbackBuffer.clear()
        lastConfirmedP1 = 0
        catchingUp = false
        Log.d(TAG, "applySnapshot: done, currentFrame now=$currentFrame")
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
        @Volatile var snapshotFrame: Long = 0L
        val createdNanos: Long = System.nanoTime()

        @Synchronized
        fun addChunk(chunk: NetplayPacket.SnapshotChunk) {
            if (chunk.chunkIndex !in received) {
                received[chunk.chunkIndex] = chunk.payload
                if (chunk.chunkIndex == 0) snapshotFrame = chunk.frameIndex
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
        private const val FRAME_PERIOD_NANOS = 16_666_667L
        private const val MAX_STEPS_PER_TICK = 4
        private const val BURST_THRESHOLD = 10
        private const val ROLLBACK_DEPTH = 8
        const val DEFAULT_CATCHUP_THRESHOLD = 30
    }
}
