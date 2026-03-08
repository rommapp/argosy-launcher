package com.nendo.argosy.data.social

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeviceKeyResponse(
    val key: String,
    @Json(name = "qr_url") val qrUrl: String,
    @Json(name = "pending_ws") val pendingWs: String,
    @Json(name = "expires_at") val expiresAt: String
)

@JsonClass(generateAdapter = true)
data class SocialUser(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_color") val avatarColor: String
)

@JsonClass(generateAdapter = true)
data class WSMessage(
    val type: String,
    val payload: Any?
)

@JsonClass(generateAdapter = true)
data class AuthSuccessPayload(
    val token: String,
    val user: SocialUser
)

@JsonClass(generateAdapter = true)
data class ErrorPayload(
    val code: String,
    val message: String
)

sealed class SocialConnectionState {
    data object Disconnected : SocialConnectionState()
    data object Connecting : SocialConnectionState()
    data class AwaitingAuth(
        val qrUrl: String,
        val loginCode: String
    ) : SocialConnectionState()
    data class Connected(val user: SocialUser) : SocialConnectionState()
    data class Failed(val reason: String) : SocialConnectionState()
}

enum class PresenceStatus(val value: String) {
    ONLINE("online"),
    AWAY("away"),
    IN_GAME("in_game"),
    OFFLINE("offline");

    companion object {
        fun fromValue(value: String): PresenceStatus =
            entries.find { it.value == value } ?: OFFLINE
    }
}

@JsonClass(generateAdapter = true)
data class SetPresenceRequest(
    val status: String,
    @Json(name = "game_igdb_id") val gameIgdbId: Int? = null,
    @Json(name = "device_name") val deviceName: String? = null
)

@JsonClass(generateAdapter = true)
data class PresenceUpdate(
    @Json(name = "user_id") val userId: String,
    val status: String,
    val game: PresenceGameInfo? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    val timestamp: String
)

@JsonClass(generateAdapter = true)
data class GameInfo(
    val id: String,
    @Json(name = "igdb_id") val igdbId: Int,
    val title: String,
    val platform: String?
)

@JsonClass(generateAdapter = true)
data class PresenceGameInfo(
    val title: String,
    @Json(name = "cover_thumb") val coverThumb: String? = null
)

enum class FriendshipStatus(val value: String) {
    PENDING("pending"),
    ACCEPTED("accepted"),
    BLOCKED("blocked");

    companion object {
        fun fromValue(value: String): FriendshipStatus =
            entries.find { it.value == value } ?: PENDING
    }
}

@JsonClass(generateAdapter = true)
data class Friend(
    val id: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_color") val avatarColor: String,
    val status: String,
    val presence: PresenceStatus? = null,
    @Json(name = "current_game") val currentGame: PresenceGameInfo? = null,
    @Json(name = "device_name") val deviceName: String? = null,
    @Json(name = "is_favorite") val isFavorite: Boolean = false
) {
    val friendshipStatus: FriendshipStatus
        get() = FriendshipStatus.fromValue(status)
}

@JsonClass(generateAdapter = true)
data class FriendAcceptedPayload(
    @Json(name = "user_id") val userId: String,
    val username: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_color") val avatarColor: String
)

@JsonClass(generateAdapter = true)
data class FriendCodePayload(
    val code: String
)

object MessageTypes {
    const val REGISTER_DEVICE = "register_device"
    const val AUTH_SUCCESS = "auth_success"
    const val ERROR = "error"
    const val SET_PRESENCE = "set_presence"
    const val PRESENCE_UPDATE = "presence_update"
    const val SYNC_LIBRARY = "sync_library"
    const val LIBRARY_SYNCED = "library_synced"
    const val SYNC_PLAY_SESSIONS = "sync_play_sessions"
    const val PLAY_SESSIONS_SYNCED = "play_sessions_synced"
    const val GET_FRIEND = "get_friend"
    const val FRIEND_DATA = "friend_data"
    const val SEND_FRIEND_REQ = "send_friend_req"
    const val FRIEND_REQUEST = "friend_request"
    const val ACCEPT_FRIEND = "accept_friend"
    const val FRIEND_ACCEPTED = "friend_accepted"
    const val LOOKUP_FRIEND_CODE = "lookup_friend_code"
    const val GET_FRIEND_CODE = "get_friend_code"
    const val REGENERATE_FRIEND_CODE = "regenerate_friend_code"
    const val FRIEND_CODE_DATA = "friend_code"
    const val FRIEND_ADDED = "friend_added"
    const val FRIEND_REMOVED = "friend_removed"
    const val GET_FEED = "get_feed"
    const val FEED_DATA = "feed_data"
    const val FEED_EVENT = "feed_event"
    const val FEED_EVENT_UPDATED = "feed_event_updated"
    const val FEED_COMMENT = "feed_comment"
    const val LIKE_EVENT = "like_event"
    const val COMMENT_EVENT = "comment_event"
    const val DELETE_COMMENT = "delete_comment"
    const val HIDE_EVENT = "hide_event"
    const val REPORT_EVENT = "report_event"
    const val UPDATE_FEED_PRIVACY = "update_feed_privacy"
    const val DEVICE_REVOKED = "device_revoked"
    const val REQUEST_GAME_DATA = "request_game_data"
    const val GAME_DATA = "game_data"
    const val CREATE_DOODLE = "create_doodle"
    const val CREATE_FEED_EVENT = "create_feed_event"
    const val GET_EVENT_COMMENTS = "get_event_comments"
    const val EVENT_COMMENTS = "event_comments"

    // Friend favorites
    const val TOGGLE_FAVORITE_FRIEND = "toggle_favorite_friend"
    const val FAVORITE_FRIEND_UPDATED = "favorite_friend_updated"

    // Discord token relay
    const val REQUEST_DISCORD_TOKENS = "request_discord_tokens"
    const val DISCORD_TOKENS = "discord_tokens"
    const val DISCORD_NOT_LINKED = "discord_not_linked"

    // Achievement sync
    const val SYNC_ACHIEVEMENT_UNLOCKS = "sync_achievement_unlocks"
    const val SYNC_ACHIEVEMENT_UNLOCKS_RESULT = "sync_achievement_unlocks_result"

    // Heartbeat
    const val PING = "ping"
    const val PONG = "pong"

    // Initial state provisioning (received after auth_success)
    const val FRIENDS_DATA = "friends"
    const val SHARED_COLLECTIONS = "shared_collections"
    const val SAVED_COLLECTIONS = "saved_collections"
}

@JsonClass(generateAdapter = true)
data class CollectionSummary(
    val id: String,
    @Json(name = "owner_id") val ownerId: String,
    @Json(name = "owner_name") val ownerName: String,
    val name: String,
    val description: String? = null,
    @Json(name = "game_count") val gameCount: Int = 0
)

enum class FeedEventType(val value: String) {
    STARTED_PLAYING("started_playing"),
    PLAY_MILESTONE("play_milestone"),
    MARATHON_SESSION("marathon_session"),
    COMPLETED("completed"),
    ACHIEVEMENT_UNLOCKED("achievement_unlocked"),
    ACHIEVEMENT_MILESTONE("achievement_milestone"),
    PERFECT_GAME("perfect_game"),
    GAME_ADDED("game_added"),
    GAME_FAVORITED("game_favorited"),
    GAME_RATED("game_rated"),
    FRIEND_ADDED("friend_added"),
    COLLECTION_SHARED("collection_shared"),
    COLLECTION_SAVED("collection_saved"),
    COLLECTION_CREATED("collection_created"),
    COLLECTION_UPDATED("collection_updated"),
    DOODLE("doodle");

    companion object {
        fun fromValue(value: String): FeedEventType? =
            entries.find { it.value == value }
    }
}

@JsonClass(generateAdapter = true)
data class FeedGameInfo(
    val id: String? = null,
    @Json(name = "igdb_id") val igdbId: Int? = null,
    val title: String? = null,
    val platform: String? = null,
    @Json(name = "cover_thumb") val coverThumb: String? = null,
    @Json(name = "gradient_colors") val gradientColors: String? = null
)

@JsonClass(generateAdapter = true)
data class FeedEventDto(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val user: SocialUser? = null,
    val type: String,
    val payload: Map<String, Any?>? = null,
    @Json(name = "igdb_id") val igdbId: Int? = null,
    @Json(name = "ra_game_id") val raGameId: Int? = null,
    @Json(name = "fallback_title") val fallbackTitle: String = "",
    val game: FeedGameInfo? = null,
    @Json(name = "created_at") val createdAt: String,
    val hidden: Boolean = false,
    @Json(name = "like_count") val likeCount: Int = 0,
    @Json(name = "comment_count") val commentCount: Int = 0,
    @Json(name = "is_liked_by_me") val isLikedByMe: Boolean = false,
    val comments: List<FeedComment> = emptyList()
) {
    val eventType: FeedEventType?
        get() = FeedEventType.fromValue(type)
}

@JsonClass(generateAdapter = true)
data class FeedComment(
    val id: String,
    @Json(name = "event_id") val eventId: String,
    @Json(name = "user_id") val userId: String,
    val user: SocialUser? = null,
    val content: String,
    @Json(name = "created_at") val createdAt: String
)

@JsonClass(generateAdapter = true)
data class FeedResponse(
    val events: List<FeedEventDto>,
    @Json(name = "has_more") val hasMore: Boolean = false
)

@JsonClass(generateAdapter = true)
data class FeedRequest(
    val limit: Int? = null,
    @Json(name = "before_id") val beforeId: String? = null,
    @Json(name = "user_id") val userId: String? = null
)

@JsonClass(generateAdapter = true)
data class LikeEventRequest(
    @Json(name = "event_id") val eventId: String
)

@JsonClass(generateAdapter = true)
data class CommentEventRequest(
    @Json(name = "event_id") val eventId: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class DeleteCommentRequest(
    @Json(name = "comment_id") val commentId: String
)

@JsonClass(generateAdapter = true)
data class HideEventRequest(
    @Json(name = "event_id") val eventId: String
)

@JsonClass(generateAdapter = true)
data class FeedEventUpdatedPayload(
    @Json(name = "event_id") val eventId: String,
    @Json(name = "like_count") val likeCount: Int,
    @Json(name = "comment_count") val commentCount: Int
)

@JsonClass(generateAdapter = true)
data class FeedCommentPayload(
    @Json(name = "event_id") val eventId: String,
    val comment: FeedComment
)
