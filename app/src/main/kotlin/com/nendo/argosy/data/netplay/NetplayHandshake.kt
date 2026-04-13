package com.nendo.argosy.data.netplay

import android.util.Log
import com.nendo.argosy.data.social.ArgosSocialService
import com.nendo.argosy.data.social.ArgosSocialService.IncomingMessage
import com.nendo.argosy.data.social.NetplayCandidate
import com.nendo.argosy.data.social.NetplayCandidatesPayload
import com.nendo.argosy.data.social.NetplayHandshakeResultPayload
import com.nendo.argosy.data.social.NetplayHandshakeTelemetryPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NetplayHandshake(
    private val candidateGatherer: CandidateGatherer,
    private val socialService: ArgosSocialService
) {

    enum class Role { Host, Guest }

    data class HandshakeArgs(
        val sessionId: String,
        val sessionKey: ByteArray,
        val role: Role,
        val peerUserId: String,
        val localSocket: DatagramSocket,
        val peerCandidatesChannel: Channel<IncomingMessage.NetplayPeerCandidates>,
        val punchStartChannel: Channel<IncomingMessage.NetplayPunchStart>,
        val scope: CoroutineScope
    )

    sealed class HandshakeResult {
        data class Success(
            val latchedCandidate: NetplayCandidate,
            val transport: NetplayTransport,
            val peerAddress: InetSocketAddress,
            val measuredRttMs: Int,
            val measuredJitterMs: Int
        ) : HandshakeResult()

        data class QualityWarning(
            val latchedCandidate: NetplayCandidate,
            val transport: NetplayTransport,
            val peerAddress: InetSocketAddress,
            val measuredRttMs: Int,
            val measuredJitterMs: Int,
            val ratingLabel: String
        ) : HandshakeResult()

        data class Failure(val reason: String) : HandshakeResult()
    }

    suspend fun performHandshake(
        args: HandshakeArgs,
        onRttBurstStarted: (() -> Unit)? = null
    ): HandshakeResult {
        val localCandidates = try {
            candidateGatherer.gatherCandidates(args.localSocket)
        } catch (_: Throwable) {
            emptyList()
        }
        if (localCandidates.isEmpty()) {
            reportFailure(args.sessionId, "no_candidates")
            sendTelemetry(TelemetryOutcome.HANDSHAKE_TIMEOUT)
            return HandshakeResult.Failure("no_candidates")
        }

        socialService.sendNetplayCandidates(
            NetplayCandidatesPayload(sessionId = args.sessionId, candidates = localCandidates)
        )

        val peerMessage = withTimeoutOrNull(PEER_CANDIDATES_TIMEOUT) {
            args.peerCandidatesChannel.receive()
        } ?: run {
            reportFailure(args.sessionId, "peer_candidates_timeout")
            sendTelemetry(TelemetryOutcome.HANDSHAKE_TIMEOUT)
            return HandshakeResult.Failure("peer_candidates_timeout")
        }
        val peerCandidates = peerMessage.payload.candidates
        if (peerCandidates.isEmpty()) {
            reportFailure(args.sessionId, "peer_candidates_timeout")
            sendTelemetry(TelemetryOutcome.HANDSHAKE_TIMEOUT)
            return HandshakeResult.Failure("peer_candidates_timeout")
        }

        val punchStart = withTimeoutOrNull(PUNCH_START_TIMEOUT) {
            args.punchStartChannel.receive()
        } ?: run {
            reportFailure(args.sessionId, "punch_start_timeout")
            sendTelemetry(TelemetryOutcome.HANDSHAKE_TIMEOUT)
            return HandshakeResult.Failure("punch_start_timeout")
        }

        val clockSkewMs = punchStart.payload.serverNowUnixMs - System.currentTimeMillis()
        val localStartMs = punchStart.payload.startAtUnixMs - clockSkewMs
        val sleepMs = (localStartMs - System.currentTimeMillis()).coerceAtLeast(0L)
        if (sleepMs > 0) delay(sleepMs.milliseconds)

        val crypto = PacketCrypto.fromMasterKey(args.sessionKey)
        val localDirection = when (args.role) {
            Role.Host -> PacketCrypto.Direction.HostToGuest
            Role.Guest -> PacketCrypto.Direction.GuestToHost
        }
        val transport = NetplayTransport(
            crypto = crypto,
            socket = args.localSocket,
            localDirection = localDirection,
            scope = args.scope
        )

        val latched = simultaneousPunch(transport, peerCandidates)
        if (latched == null) {
            transport.close()
            reportFailure(args.sessionId, "candidate_pair_failed")
            sendTelemetry(TelemetryOutcome.CANDIDATE_PAIR_FAILED)
            return HandshakeResult.Failure("candidate_pair_failed")
        }

        val (latchedCandidate, peerAddress) = latched
        onRttBurstStarted?.invoke()
        val burst = runRttBurst(transport, peerAddress)
        if (burst == null) {
            transport.close()
            reportFailure(args.sessionId, "candidate_pair_failed")
            sendTelemetry(TelemetryOutcome.CANDIDATE_PAIR_FAILED, latchedCandidate = latchedCandidate.type)
            return HandshakeResult.Failure("candidate_pair_failed")
        }

        val (medianRttMs, jitterMs) = burst
        if (medianRttMs > QUALITY_MAX_RTT_MS || jitterMs > QUALITY_MAX_JITTER_MS) {
            transport.close()
            socialService.sendNetplayHandshakeResult(
                NetplayHandshakeResultPayload(
                    sessionId = args.sessionId,
                    success = false,
                    measuredRttMs = medianRttMs,
                    measuredJitterMs = jitterMs,
                    latchedCandidate = latchedCandidate.type,
                    reason = "quality_rejected"
                )
            )
            sendTelemetry(
                outcome = TelemetryOutcome.QUALITY_REJECTED,
                latchedCandidate = latchedCandidate.type,
                rttMs = medianRttMs,
                jitterMs = jitterMs
            )
            return HandshakeResult.Failure("quality_rejected")
        }

        if (medianRttMs > QUALITY_WARN_RTT_MS) {
            val warnPingResponder = args.scope.launch {
                transport.incomingPackets
                    .filter { it.source.address == peerAddress.address && it.source.port == peerAddress.port }
                    .collect { incoming ->
                        if (incoming.packet is NetplayPacket.Ping) {
                            runCatching { transport.send(peerAddress, NetplayPacket.Pong(incoming.packet.timestampNanos)) }
                        }
                    }
            }
            args.scope.launch { delay(RTT_BURST_TIMEOUT); warnPingResponder.cancel() }

            socialService.sendNetplayHandshakeResult(
                NetplayHandshakeResultPayload(
                    sessionId = args.sessionId,
                    success = true,
                    measuredRttMs = medianRttMs,
                    measuredJitterMs = jitterMs,
                    latchedCandidate = latchedCandidate.type
                )
            )
            sendTelemetry(
                outcome = TelemetryOutcome.QUALITY_WARN,
                latchedCandidate = latchedCandidate.type,
                rttMs = medianRttMs,
                jitterMs = jitterMs
            )
            return HandshakeResult.QualityWarning(
                latchedCandidate = latchedCandidate,
                transport = transport,
                peerAddress = peerAddress,
                measuredRttMs = medianRttMs,
                measuredJitterMs = jitterMs,
                ratingLabel = "Bad"
            )
        }

        // Keep responding to peer's Pings while the peer finishes its own burst.
        // Without this, the faster side stops responding and the slower side times out.
        val pingResponderJob = args.scope.launch {
            transport.incomingPackets
                .filter { it.source.address == peerAddress.address && it.source.port == peerAddress.port }
                .collect { incoming ->
                    if (incoming.packet is NetplayPacket.Ping) {
                        runCatching { transport.send(peerAddress, NetplayPacket.Pong(incoming.packet.timestampNanos)) }
                    }
                }
        }
        // Auto-cancel after grace period — by then the driver's drainIncoming takes over
        args.scope.launch {
            delay(RTT_BURST_TIMEOUT)
            pingResponderJob.cancel()
        }

        socialService.sendNetplayHandshakeResult(
            NetplayHandshakeResultPayload(
                sessionId = args.sessionId,
                success = true,
                measuredRttMs = medianRttMs,
                measuredJitterMs = jitterMs,
                latchedCandidate = latchedCandidate.type
            )
        )
        sendTelemetry(
            outcome = TelemetryOutcome.SUCCESS,
            latchedCandidate = latchedCandidate.type,
            rttMs = medianRttMs,
            jitterMs = jitterMs
        )

        return HandshakeResult.Success(
            latchedCandidate = latchedCandidate,
            transport = transport,
            peerAddress = peerAddress,
            measuredRttMs = medianRttMs,
            measuredJitterMs = jitterMs
        )
    }

    internal enum class TelemetryOutcome(val wire: String) {
        SUCCESS("success"),
        QUALITY_WARN("quality_warn"),
        QUALITY_REJECTED("quality_rejected"),
        CANDIDATE_PAIR_FAILED("candidate_pair_failed"),
        HANDSHAKE_TIMEOUT("handshake_timeout")
    }

    private fun sendTelemetry(
        outcome: TelemetryOutcome,
        latchedCandidate: String? = null,
        rttMs: Int? = null,
        jitterMs: Int? = null
    ) {
        runCatching {
            socialService.sendNetplayHandshakeTelemetry(
                NetplayHandshakeTelemetryPayload(
                    outcome = outcome.wire,
                    latchedCandidate = latchedCandidate,
                    measuredRttMs = rttMs,
                    measuredJitterMs = jitterMs
                )
            )
        }
    }

    private suspend fun simultaneousPunch(
        transport: NetplayTransport,
        peerCandidates: List<NetplayCandidate>
    ): Pair<NetplayCandidate, InetSocketAddress>? = coroutineScope {
        Log.d(TAG, "simultaneousPunch: ${peerCandidates.size} peer candidates")
        val resolved = peerCandidates.mapNotNull { cand ->
            try {
                val addr = withContext(Dispatchers.IO) { InetAddress.getByName(cand.address) }
                Log.d(TAG, "resolved candidate: ${cand.type} ${cand.address}:${cand.port} -> ${addr.hostAddress}:${cand.port}")
                cand to InetSocketAddress(addr, cand.port)
            } catch (t: Throwable) {
                Log.w(TAG, "failed to resolve candidate ${cand.address}: ${t.message}")
                null
            }
        }
        if (resolved.isEmpty()) { Log.w(TAG, "no resolved candidates"); return@coroutineScope null }

        val firstResponse = Channel<NetplayTransport.Incoming>(capacity = Channel.BUFFERED)
        val collectorJob = launch {
            transport.incomingPackets
                .filter { incoming ->
                    resolved.any { (_, addr) ->
                        addr.address == incoming.source.address && addr.port == incoming.source.port
                    }
                }
                .collect { incoming ->
                    firstResponse.trySend(incoming)
                }
        }

        delay(10)

        val senderJob = launch(Dispatchers.IO) {
            repeat(PUNCH_ROUNDS) { round ->
                for ((cand, addr) in resolved) {
                    runCatching {
                        transport.send(addr, NetplayPacket.Ping(System.nanoTime()))
                        if (round == 0) Log.d(TAG, "punch send #$round to ${cand.type} ${addr.address.hostAddress}:${addr.port}")
                    }.onFailure { Log.w(TAG, "punch send failed to ${addr}: ${it.message}") }
                }
                delay(PUNCH_INTERVAL)
            }
            Log.d(TAG, "punch sender done after $PUNCH_ROUNDS rounds")
        }

        val latched = withTimeoutOrNull(PUNCH_RESPONSE_TIMEOUT) {
            firstResponse.receive()
        }
        senderJob.cancel()
        collectorJob.cancel()

        Log.d(TAG, "punch result: ${if (latched != null) "latched from ${latched.source}" else "TIMEOUT - no response"}")
        if (latched == null) return@coroutineScope null
        val match = resolved.first { (_, addr) ->
            addr.address == latched.source.address && addr.port == latched.source.port
        }
        match
    }

    private suspend fun runRttBurst(
        transport: NetplayTransport,
        peer: InetSocketAddress
    ): Pair<Int, Int>? = coroutineScope {
        val pongChannel = Channel<Pair<Long, Long>>(capacity = Channel.BUFFERED)
        val collectorJob = launch {
            transport.incomingPackets
                .filter { it.source.address == peer.address && it.source.port == peer.port }
                .collect { incoming ->
                    when (incoming.packet) {
                        is NetplayPacket.Pong -> {
                            val pong = incoming.packet
                            pongChannel.trySend(pong.timestampNanos to System.nanoTime())
                        }
                        is NetplayPacket.Ping -> {
                            runCatching { transport.send(peer, NetplayPacket.Pong(incoming.packet.timestampNanos)) }
                        }
                        else -> {}
                    }
                }
        }

        delay(10)

        val senderJob = launch(Dispatchers.IO) {
            repeat(RTT_BURST_COUNT) {
                val sentAt = System.nanoTime()
                runCatching { transport.send(peer, NetplayPacket.Ping(sentAt)) }
                delay(RTT_BURST_INTERVAL)
            }
        }

        val samples = mutableListOf<Long>()
        withTimeoutOrNull(RTT_BURST_TIMEOUT) {
            while (samples.size < RTT_BURST_COUNT) {
                val (sentAt, receivedAt) = pongChannel.receive()
                val rttNanos = receivedAt - sentAt
                if (rttNanos > 0) samples.add(rttNanos / 1_000_000L)
            }
        }

        senderJob.cancel()
        collectorJob.cancel()

        if (samples.size < RTT_MIN_SAMPLES) return@coroutineScope null

        val sorted = samples.sorted()
        val median = sorted[sorted.size / 2]
        val mean = sorted.average()
        val variance = sorted.sumOf { (it - mean) * (it - mean) } / sorted.size
        val stdev = sqrt(variance)
        median.toInt() to stdev.toInt()
    }

    private fun reportFailure(sessionId: String, reason: String) {
        socialService.sendNetplayHandshakeResult(
            NetplayHandshakeResultPayload(
                sessionId = sessionId,
                success = false,
                reason = reason
            )
        )
    }

    companion object {
        private const val TAG = "NetplayHandshake"
        private val PEER_CANDIDATES_TIMEOUT = 10.seconds
        private val PUNCH_START_TIMEOUT = 10.seconds
        private val PUNCH_RESPONSE_TIMEOUT = 3.seconds
        private val PUNCH_INTERVAL = 100.milliseconds
        private const val PUNCH_ROUNDS = 20

        private val RTT_BURST_INTERVAL = 50.milliseconds
        private val RTT_BURST_TIMEOUT = 3.seconds
        private const val RTT_BURST_COUNT = 20
        private const val RTT_MIN_SAMPLES = 5

        private const val QUALITY_WARN_RTT_MS = 200
        private const val QUALITY_MAX_RTT_MS = 300
        private const val QUALITY_MAX_JITTER_MS = 50
    }
}
