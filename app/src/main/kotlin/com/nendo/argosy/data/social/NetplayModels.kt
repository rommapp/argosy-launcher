package com.nendo.argosy.data.social

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetplaySession(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "game_igdb_id") val gameIgdbId: Int? = null,
    @Json(name = "game_title") val gameTitle: String,
    @Json(name = "core_id") val coreId: String,
    @Json(name = "rom_hash_prefix") val romHashPrefix: String,
    @Json(name = "core_hash") val coreHash: String,
    val joinable: Boolean = false,
    @Json(name = "protocol_version") val protocolVersion: Int
)

@JsonClass(generateAdapter = true)
data class NetplayOpenPayload(
    @Json(name = "game_igdb_id") val gameIgdbId: Int? = null,
    @Json(name = "game_title") val gameTitle: String,
    @Json(name = "core_id") val coreId: String,
    @Json(name = "rom_hash_prefix") val romHashPrefix: String,
    @Json(name = "core_hash") val coreHash: String
)

@JsonClass(generateAdapter = true)
data class NetplayReadyPayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "session_key") val sessionKey: String,
    @Json(name = "protocol_version") val protocolVersion: Int
)

@JsonClass(generateAdapter = true)
data class NetplayJoinRequestPayload(
    @Json(name = "session_id") val sessionId: String
)

@JsonClass(generateAdapter = true)
data class NetplayJoinRequestedPayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "from_user_id") val fromUserId: String,
    @Json(name = "from_username") val fromUsername: String
)

@JsonClass(generateAdapter = true)
data class NetplayJoinResponsePayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "guest_id") val guestId: String,
    val accept: Boolean,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class NetplayJoinDeclinedPayload(
    @Json(name = "session_id") val sessionId: String,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class NetplayCandidate(
    val type: String,
    val address: String,
    val port: Int,
    val priority: Int = 0
)

@JsonClass(generateAdapter = true)
data class NetplayCandidatesPayload(
    @Json(name = "session_id") val sessionId: String,
    val candidates: List<NetplayCandidate>
)

@JsonClass(generateAdapter = true)
data class NetplayPeerCandidatesPayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "peer_user_id") val peerUserId: String,
    val candidates: List<NetplayCandidate>
)

@JsonClass(generateAdapter = true)
data class NetplayPunchStartPayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "start_at_unix_ms") val startAtUnixMs: Long,
    @Json(name = "server_now_unix_ms") val serverNowUnixMs: Long
)

@JsonClass(generateAdapter = true)
data class NetplayHandshakeResultPayload(
    @Json(name = "session_id") val sessionId: String,
    val success: Boolean,
    @Json(name = "measured_rtt_ms") val measuredRttMs: Int? = null,
    @Json(name = "measured_jitter_ms") val measuredJitterMs: Int? = null,
    @Json(name = "latched_candidate") val latchedCandidate: String? = null,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class NetplayHandshakeFailedPayload(
    @Json(name = "session_id") val sessionId: String,
    val reason: String
)

@JsonClass(generateAdapter = true)
data class NetplaySessionEndedPayload(
    @Json(name = "session_id") val sessionId: String,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class NetplayKickedPayload(
    @Json(name = "session_id") val sessionId: String,
    val reason: String? = null
)

@JsonClass(generateAdapter = true)
data class NetplayReserveRequestPayload(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "reserved_for_user_id") val reservedForUserId: String? = null
)

@JsonClass(generateAdapter = true)
data class NetplayHandshakeTelemetryPayload(
    val outcome: String,
    @Json(name = "latched_candidate") val latchedCandidate: String? = null,
    @Json(name = "measured_rtt_ms") val measuredRttMs: Int? = null,
    @Json(name = "measured_jitter_ms") val measuredJitterMs: Int? = null,
    @Json(name = "nat_hint") val natHint: String? = null,
    @Json(name = "region_pair_hint") val regionPairHint: String? = null
)

sealed class NetplaySessionState {
    data object Idle : NetplaySessionState()
    data class Opening(val gameTitle: String) : NetplaySessionState()
    data class Waiting(
        val sessionId: String,
        val gameTitle: String
    ) : NetplaySessionState()
    data class JoinRequestReceived(
        val sessionId: String,
        val fromUserId: String,
        val fromUsername: String
    ) : NetplaySessionState()
    data class Handshaking(
        val sessionId: String,
        val peerUserId: String
    ) : NetplaySessionState()
    data class Connected(
        val sessionId: String,
        val peerUserId: String
    ) : NetplaySessionState()
    data class Reconnecting(
        val sessionId: String,
        val peerUserId: String
    ) : NetplaySessionState()
    data class PeerDisconnected(
        val sessionId: String,
        val peerUserId: String,
        val role: PeerRole
    ) : NetplaySessionState()
    data class Ending(val sessionId: String) : NetplaySessionState()
    data class Error(val reason: String) : NetplaySessionState()

    enum class PeerRole { Host, Guest }
}
