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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32

class NetplayPeerDriver(
    private val retroView: GLRetroView,
    private val transport: NetplayTransport,
    initialPeerAddress: InetSocketAddress,
    val peerUserId: String,
    private val localPort: Int,
    private val remotePort: Int,
    private val scope: CoroutineScope,
    private val onSessionEnd: (reason: String) -> Unit,
    private val inputDelay: Int = DEFAULT_INPUT_DELAY,
    private val rollbackWindow: Int = DEFAULT_ROLLBACK_WINDOW,
    private val desyncCheckInterval: Int = DEFAULT_DESYNC_CHECK_INTERVAL,
    private val libretroOps: LibretroNetplayOps = RealLibretroNetplayOps,
    private val framePeriodNanos: Long = FRAME_PERIOD_NANOS
) : NetplayDriver {

    @Volatile private var peerAddress: InetSocketAddress = initialPeerAddress
    @Volatile var currentFrame: Long = 0L
        private set
    @Volatile override var lastRttNanos: Long = 0L
        private set
    @Volatile override var lastIncomingNanos: Long = System.nanoTime()
        private set
    @Volatile private var lastHeartbeatNanos: Long = 0L
    @Volatile private var nextTickTargetNanos: Long = System.nanoTime()
    @Volatile private var stopped = false
    @Volatile private var ready = false

    private val localInputHistory = HashMap<Long, Int>(256)
    private val confirmedRemoteInputs = HashMap<Long, Int>(256)
    private var lastConfirmedRemoteInput: Int = 0
    private var lastConfirmedRemoteFrame: Long = -1L
    @Volatile var remotePeerFrame: Long = -1L
        private set

    private class StateEntry(val frame: Long, val state: ByteArray, val predictedRemoteInput: Int)
    private val stateRing = arrayOfNulls<StateEntry>(rollbackWindow)
    private var stateRingHead = 0

    private val incomingRing = Channel<NetplayTransport.Incoming>(capacity = 256)

    private val nextSnapshotId = AtomicInteger(1)
    private val pendingSnapshots = ConcurrentHashMap<Int, OutboundSnapshot>()
    private val reassembly = ConcurrentHashMap<Int, ReassemblyBuffer>()
    @Volatile private var activeReassemblyId: Int? = null
    @Volatile private var lastAppliedSnapshotId: Int = -1
    private var lastAckEmitNanos = 0L
    @Volatile private var catchingUp = false

    private var catchupObservationStartNanos: Long = 0L
    private var catchupObservationGap: Long = 0L
    private var catchupActive = false

    private val receiveJob: Job = scope.launch {
        transport.incomingPackets.collect { incoming ->
            peerAddress = incoming.source
            incomingRing.trySend(incoming)
        }
    }

    private val snapshotRetransmitJob: Job = scope.launch(Dispatchers.IO) {
        while (isActive && !stopped) {
            try {
                kotlinx.coroutines.delay(SNAPSHOT_RETRANSMIT_INTERVAL_MS)
                retransmitUnackedChunks()
                maybeEmitAck()
                maybeTimeoutReassembly()
            } catch (_: Throwable) {
            }
        }
    }

    private var tickCount = 0L
    private var lastTickLogNanos = 0L
    private var rollbackCount = 0L
    private var lastDesyncCheckFrame = -1L

    override fun tick() {
        if (stopped) return
        if (!ready) {
            drainIncoming()
            libretroOps.renderFrameOnly()
            return
        }

        val now = System.nanoTime()
        if (now < nextTickTargetNanos) {
            libretroOps.renderFrameOnly()
            return
        }
        nextTickTargetNanos += framePeriodNanos
        if (nextTickTargetNanos < now - framePeriodNanos) {
            nextTickTargetNanos = now
        }

        tickCount++
        if (now - lastTickLogNanos >= 1_000_000_000L) {
            Log.d(TAG, "ticks/sec=$tickCount frame=$currentFrame rollbacks=$rollbackCount rtt=${lastRttNanos / 1_000_000}ms")
            tickCount = 0
            rollbackCount = 0
            lastTickLogNanos = now
        }

        drainIncoming()

        if (catchingUp) {
            heartbeat()
            return
        }

        evaluateCatchup(now)

        if (catchupActive) {
            var extraFrames = 0
            while (extraFrames < MAX_CATCHUP_FRAMES_PER_TICK) {
                drainIncoming()
                val gap = remotePeerFrame - currentFrame
                if (gap < CATCHUP_EXIT_THRESHOLD) {
                    exitCatchup()
                    break
                }
                executeFrame()
                extraFrames++
            }
        }

        if (remotePeerFrame >= 0 && currentFrame > remotePeerFrame + HARD_STALL_FRAMES) {
            libretroOps.renderFrameOnly()
            heartbeat()
            return
        }

        executeFrame()
        heartbeat()
    }

    fun setStartFrame(frame: Long) {
        currentFrame = frame
    }

    private fun executeFrame() {
        val sampledInput = libretroOps.getInputPortBitmask(0)
        val delayedFrame = currentFrame + inputDelay
        localInputHistory[delayedFrame] = sampledInput
        sendFrameInput(delayedFrame, sampledInput)

        val localInput = localInputHistory[currentFrame] ?: 0
        val remoteInput = resolveRemoteInput(currentFrame)

        saveState(currentFrame, remoteInput)

        libretroOps.setInputPortState(localPort, localInput)
        libretroOps.setInputPortState(remotePort, remoteInput)
        libretroOps.stepForNetplay(retroView)

        currentFrame++

        checkRollback()

        if (desyncCheckInterval > 0 && currentFrame - lastDesyncCheckFrame >= desyncCheckInterval) {
            sendDesyncCheck()
        }

        trimHistory()
    }

    private fun evaluateCatchup(now: Long) {
        if (remotePeerFrame < 0) return
        val gap = remotePeerFrame - currentFrame
        if (gap < CATCHUP_THRESHOLD) {
            if (catchupActive) exitCatchup()
            catchupObservationStartNanos = 0L
            return
        }
        if (catchupActive) return
        if (catchupObservationStartNanos == 0L) {
            catchupObservationStartNanos = now
            catchupObservationGap = gap
            return
        }
        if (now - catchupObservationStartNanos < CATCHUP_OBSERVATION_NANOS) return
        if (gap >= catchupObservationGap) {
            catchupActive = true
            Log.d(TAG, "catch-up engaged: gap=$gap (was $catchupObservationGap)")
        }
        catchupObservationStartNanos = 0L
    }

    private fun exitCatchup() {
        catchupActive = false
        catchupObservationStartNanos = 0L
        nextTickTargetNanos = System.nanoTime()
        Log.d(TAG, "catch-up exited: frame=$currentFrame remotePeerFrame=$remotePeerFrame")
    }

    suspend fun sendInitialSnapshot(state: ByteArray, frameIndex: Long): Int {
        val snapshotId = 0
        enqueueSnapshot(snapshotId, state, frameIndex)
        return snapshotId
    }

    fun resetFrameCounter() {
        currentFrame = 0
        localInputHistory.clear()
        confirmedRemoteInputs.clear()
        lastConfirmedRemoteInput = 0
        lastConfirmedRemoteFrame = -1L
        remotePeerFrame = -1L
        for (i in stateRing.indices) stateRing[i] = null
        stateRingHead = 0
        catchupActive = false
        catchupObservationStartNanos = 0L
    }

    override fun stop() {
        if (stopped) return
        stopped = true
        receiveJob.cancel()
        snapshotRetransmitJob.cancel()
        pendingSnapshots.clear()
        reassembly.clear()
        scope.launch {
            runCatching { transport.send(peerAddress, NetplayPacket.SessionControl.Goodbye) }
            runCatching { transport.close() }
        }
    }

    private fun sendFrameInput(frame: Long, bitmask: Int) {
        val redundantCount = REDUNDANT_INPUT_COUNT.coerceAtMost((frame - 0).toInt().coerceAtLeast(0))
        val redundant = ArrayList<Pair<Long, Int>>(redundantCount)
        for (i in 1..redundantCount) {
            val priorFrame = frame - i
            val priorBitmask = localInputHistory[priorFrame] ?: continue
            redundant.add(priorFrame to priorBitmask)
        }
        dispatchSend(
            NetplayPacket.FrameInput(
                frameIndex = frame,
                playerPort = localPort,
                bitmask = bitmask,
                senderFrame = currentFrame,
                redundant = redundant
            )
        )
    }

    private fun resolveRemoteInput(frame: Long): Int {
        val confirmed = confirmedRemoteInputs[frame]
        if (confirmed != null) return confirmed
        return lastConfirmedRemoteInput
    }

    private fun saveState(frame: Long, predictedRemoteInput: Int) {
        val state = try {
            retroView.serializeState()
        } catch (t: Throwable) {
            Log.w(TAG, "serializeState failed for frame $frame: ${t.message}")
            return
        }
        val idx = (stateRingHead) % rollbackWindow
        stateRing[idx] = StateEntry(frame, state, predictedRemoteInput)
        stateRingHead = (stateRingHead + 1) % rollbackWindow
    }

    private fun findStateEntry(frame: Long): StateEntry? {
        for (entry in stateRing) {
            if (entry != null && entry.frame == frame) return entry
        }
        return null
    }

    private fun checkRollback() {
        var earliestMispredictedFrame = Long.MAX_VALUE

        for (entry in stateRing) {
            if (entry == null) continue
            if (entry.frame >= currentFrame) continue
            val confirmed = confirmedRemoteInputs[entry.frame] ?: continue
            if (confirmed != entry.predictedRemoteInput && entry.frame < earliestMispredictedFrame) {
                earliestMispredictedFrame = entry.frame
            }
        }

        if (earliestMispredictedFrame == Long.MAX_VALUE) return

        val entry = findStateEntry(earliestMispredictedFrame) ?: return

        rollbackCount++
        Log.d(TAG, "rollback from frame $earliestMispredictedFrame to ${currentFrame - 1}")

        try {
            retroView.unserializeState(entry.state)
        } catch (t: Throwable) {
            Log.w(TAG, "rollback unserializeState failed: ${t.message}")
            return
        }

        val savedCurrent = currentFrame
        var replayFrame = earliestMispredictedFrame

        while (replayFrame < savedCurrent) {
            val localInput = localInputHistory[replayFrame] ?: 0
            val remoteInput = resolveRemoteInput(replayFrame)

            saveState(replayFrame, remoteInput)

            libretroOps.setInputPortState(localPort, localInput)
            libretroOps.setInputPortState(remotePort, remoteInput)
            libretroOps.stepForNetplay(retroView)

            replayFrame++
        }

        currentFrame = savedCurrent
    }

    private fun sendDesyncCheck() {
        val state = try {
            retroView.serializeState()
        } catch (_: Throwable) {
            return
        }
        val crc = CRC32()
        crc.update(state)
        lastDesyncCheckFrame = currentFrame
        dispatchSend(NetplayPacket.DesyncCheck(frameIndex = currentFrame, stateHash = crc.value))
    }

    private fun trimHistory() {
        val oldestNeeded = (currentFrame - rollbackWindow - inputDelay - REDUNDANT_INPUT_COUNT).coerceAtLeast(0L)
        localInputHistory.keys.removeAll { it < oldestNeeded }
        confirmedRemoteInputs.keys.removeAll { it < oldestNeeded }
    }

    private fun heartbeat() {
        val nowNanos = System.nanoTime()
        if (nowNanos - lastHeartbeatNanos >= HEARTBEAT_INTERVAL_NANOS) {
            lastHeartbeatNanos = nowNanos
            dispatchSend(NetplayPacket.Ping(nowNanos))
        }
    }

    private fun drainIncoming() {
        while (true) {
            val incoming = incomingRing.tryReceive().getOrNull() ?: break
            lastIncomingNanos = System.nanoTime()
            when (val packet = incoming.packet) {
                is NetplayPacket.FrameInput -> handleFrameInput(packet)
                is NetplayPacket.DesyncCheck -> handleDesyncCheck(packet)
                is NetplayPacket.SnapshotRequest -> handleSnapshotRequest(packet)
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
                is NetplayPacket.SessionControl.SnapshotAck -> handleSnapshotAck(packet)
                is NetplayPacket.InputBundle,
                is NetplayPacket.GuestInput -> { }
            }
        }
    }

    private fun handleFrameInput(packet: NetplayPacket.FrameInput) {
        if (packet.senderFrame > remotePeerFrame) {
            remotePeerFrame = packet.senderFrame
        }
        if (!ready && remotePeerFrame >= 0) {
            ready = true
            nextTickTargetNanos = System.nanoTime()
            currentFrame = 0
            Log.d(TAG, "ready: peer is at frame ${remotePeerFrame}, starting from 0")
        }
        storeConfirmedRemoteInput(packet.frameIndex, packet.bitmask)
        for ((frame, bitmask) in packet.redundant) {
            storeConfirmedRemoteInput(frame, bitmask)
        }
    }

    private fun storeConfirmedRemoteInput(frame: Long, bitmask: Int) {
        if (confirmedRemoteInputs.containsKey(frame)) return
        confirmedRemoteInputs[frame] = bitmask
        if (frame > lastConfirmedRemoteFrame) {
            lastConfirmedRemoteFrame = frame
            lastConfirmedRemoteInput = bitmask
        }
    }

    private fun handleDesyncCheck(packet: NetplayPacket.DesyncCheck) {
        val entry = findStateEntry(packet.frameIndex) ?: return
        val crc = CRC32()
        crc.update(entry.state)
        if (crc.value != packet.stateHash) {
            Log.w(TAG, "DESYNC detected at frame ${packet.frameIndex}: local=${crc.value} remote=${packet.stateHash}")
        }
    }

    private fun handleSnapshotRequest(packet: NetplayPacket.SnapshotRequest) {
        Log.d(TAG, "received SnapshotRequest reason=${packet.reasonCode}")
        val snapshotFrame = currentFrame
        scope.launch {
            try {
                val state = retroView.serializeState()
                val snapshotId = nextSnapshotId.getAndIncrement()
                enqueueSnapshot(snapshotId, state, snapshotFrame)
            } catch (t: Throwable) {
                Log.w(TAG, "serializeState failed: ${t.message}")
            }
        }
    }

    private fun handleSnapshotChunk(chunk: NetplayPacket.SnapshotChunk) {
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
        Log.d(TAG, "applySnapshot: id=$snapshotId frame=$snapshotFrame size=${bytes.size}")
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
        currentFrame = 0
        localInputHistory.clear()
        confirmedRemoteInputs.clear()
        lastConfirmedRemoteInput = 0
        lastConfirmedRemoteFrame = -1L
        for (i in stateRing.indices) stateRing[i] = null
        stateRingHead = 0
        nextTickTargetNanos = System.nanoTime()
        ready = true
        catchingUp = false
        catchupActive = false
        catchupObservationStartNanos = 0L
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

    private fun enqueueSnapshot(snapshotId: Int, payload: ByteArray, snapshotFrame: Long) {
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
                    payload = slice,
                    frameIndex = if (i == 0) snapshotFrame else 0L
                )
            )
        }
        val outstanding = (0 until total).toMutableSet()
        val attempts = IntArray(total)
        pendingSnapshots[snapshotId] = OutboundSnapshot(chunks, outstanding, attempts, startNanos = System.nanoTime())
        chunks.forEach { dispatchSend(it) }
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

    private class OutboundSnapshot(
        val chunks: List<NetplayPacket.SnapshotChunk>,
        val outstanding: MutableSet<Int>,
        val attempts: IntArray,
        val startNanos: Long
    )

    internal class ReassemblyBuffer(
        val snapshotId: Int,
        val total: Int
    ) {
        val received = java.util.TreeMap<Int, ByteArray>()
        @Volatile var lastProgressNanos: Long = System.nanoTime()
        @Volatile var snapshotFrame: Long = 0L

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
        private const val TAG = "NetplayPeerDriver"
        const val DEFAULT_INPUT_DELAY = 2
        const val DEFAULT_ROLLBACK_WINDOW = 12
        const val DEFAULT_DESYNC_CHECK_INTERVAL = 60
        private const val REDUNDANT_INPUT_COUNT = 3
        private const val HEARTBEAT_INTERVAL_NANOS = 250_000_000L
        private const val SNAPSHOT_CHUNK_BYTES = 1280
        private const val SNAPSHOT_RETRANSMIT_INTERVAL_MS = 100L
        private const val SNAPSHOT_MAX_ATTEMPTS = 10
        private const val ACK_INTERVAL_NANOS = 50_000_000L
        private const val REASSEMBLY_TIMEOUT_NANOS = 1_000_000_000L
        private const val REASON_CATCHUP = 1
        private const val FRAME_PERIOD_NANOS = 16_666_667L
        private const val HARD_STALL_FRAMES = 30L
        private const val CATCHUP_THRESHOLD = 3L
        private const val CATCHUP_EXIT_THRESHOLD = 1L
        private const val CATCHUP_OBSERVATION_NANOS = 500_000_000L
        private const val MAX_CATCHUP_FRAMES_PER_TICK = 5
    }
}
