package com.nendo.argosy.data.social

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.nendo.argosy.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArgosSocialService @Inject constructor(
    private val application: Application
) {
    private val deviceId: String by lazy {
        Settings.Secure.getString(application.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    private val deviceManufacturer: String = Build.MANUFACTURER
    private val deviceModel: String = Build.MODEL
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val moshi = Moshi.Builder().build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<IncomingMessage>(replay = 1)
    val incomingMessages: SharedFlow<IncomingMessage> = _incomingMessages.asSharedFlow()

    private val _syncAchievementResult = MutableStateFlow<List<Long>>(emptyList())
    val syncAchievementResult: StateFlow<List<Long>> = _syncAchievementResult.asStateFlow()

    data class SessionSyncResult(
        val startTime: String,
        val status: String,
        val reason: String?
    )

    private val _playSessionSyncResult = MutableSharedFlow<List<SessionSyncResult>>(replay = 1)
    val playSessionSyncResult: SharedFlow<List<SessionSyncResult>> = _playSessionSyncResult.asSharedFlow()

    private var webSocket: WebSocket? = null
    private var sessionToken: String? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = false
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var lastPongReceivedAt: Long = 0L
    private var missedPongs: Int = 0

    var onSessionRevoked: (() -> Unit)? = null

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .build()
    }

    sealed class ConnectionState {
        data object Disconnected : ConnectionState()
        data object Connecting : ConnectionState()
        data object Connected : ConnectionState()
        data class Failed(val reason: String) : ConnectionState()
        data object Reconnecting : ConnectionState()
    }

    sealed class IncomingMessage {
        data class PresenceUpdate(val update: com.nendo.argosy.data.social.PresenceUpdate) : IncomingMessage()
        data class FriendRequest(val fromUserId: String, val fromUsername: String) : IncomingMessage()
        data class FriendAccepted(
            val userId: String,
            val username: String,
            val displayName: String,
            val avatarColor: String
        ) : IncomingMessage()
        data class FriendAdded(
            val userId: String,
            val username: String,
            val displayName: String,
            val avatarColor: String
        ) : IncomingMessage()
        data class FriendRemoved(val userId: String) : IncomingMessage()
        data class FriendCodeData(val code: String, val url: String) : IncomingMessage()
        data class FriendsData(val friends: List<Friend>) : IncomingMessage()
        data class SharedCollections(val collections: List<CollectionSummary>) : IncomingMessage()
        data class SavedCollections(val collections: List<CollectionSummary>) : IncomingMessage()
        data class FeedData(val events: List<FeedEventDto>, val hasMore: Boolean) : IncomingMessage()
        data class FeedEvent(val event: FeedEventDto) : IncomingMessage()
        data class FeedEventUpdated(val eventId: String, val likeCount: Int, val commentCount: Int) : IncomingMessage()
        data class FeedCommentReceived(val eventId: String, val comment: FeedComment) : IncomingMessage()
        data class EventCommentsData(val eventId: String, val comments: List<FeedComment>) : IncomingMessage()
        data class Error(val code: String, val message: String) : IncomingMessage()
        data class Raw(val type: String, val payload: String) : IncomingMessage()
        data class SessionRevoked(val reason: String) : IncomingMessage()
        data class RequestGameData(
            val igdbId: Long,
            val gameTitle: String,
            val platform: String?,
            val fields: List<String>
        ) : IncomingMessage()
        data class DiscordTokens(
            val accessToken: String,
            val tokenType: String,
            val expiresIn: Long
        ) : IncomingMessage()
        data object DiscordNotLinked : IncomingMessage()
        data class SyncAchievementUnlocksResult(val acceptedRaIds: List<Long>) : IncomingMessage()
        data class FavoriteFriendUpdated(val friendId: String, val isFavorite: Boolean) : IncomingMessage()
        data class UnreadCount(val count: Int) : IncomingMessage()
        data class NotificationReceived(
            val notification: SocialNotification,
            val users: Map<String, SocialUser>
        ) : IncomingMessage()
        data class NotificationsData(
            val notifications: List<SocialNotification>,
            val users: Map<String, SocialUser>,
            val hasMore: Boolean
        ) : IncomingMessage()
        data class CommunityFeedData(val events: List<FeedEventDto>, val hasMore: Boolean) : IncomingMessage()
        data class CommunityFollowsData(val follows: List<CommunityFollow>) : IncomingMessage()
        data class CommunityFollowUpdated(val follow: CommunityFollow) : IncomingMessage()
        data class UserSettingsData(val settings: UserSettings) : IncomingMessage()
        data class HiddenGames(val igdbGameIds: Set<Int>) : IncomingMessage()
    }

    fun connect(token: String) {
        if (_connectionState.value == ConnectionState.Connected ||
            _connectionState.value == ConnectionState.Connecting) {
            return
        }

        sessionToken = token
        shouldReconnect = true
        reconnectAttempts = 0
        connectInternal()
    }

    private fun connectInternal() {
        val token = sessionToken ?: return

        stopHeartbeat()
        val oldSocket = webSocket
        webSocket = null
        oldSocket?.cancel()

        _connectionState.value = if (reconnectAttempts > 0) {
            ConnectionState.Reconnecting
        } else {
            ConnectionState.Connecting
        }

        val wsUrl = WS_URL.replace("https://", "wss://")
            .replace("http://", "ws://") + "ws"

        Log.d(TAG, "Connecting to social WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Social WebSocket connected, sending auth")
                val authMessage = JSONObject().apply {
                    put("type", "auth")
                    put("token", token)
                }
                webSocket.send(authMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Social message received: ${text.take(200)}")
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== this@ArgosSocialService.webSocket) return
                stopHeartbeat()
                val responseInfo = response?.let { "HTTP ${it.code} ${it.message}" } ?: "no response"
                Log.e(TAG, "Social WebSocket failure: ${t.javaClass.simpleName}: ${t.message} | $responseInfo", t)
                _connectionState.value = ConnectionState.Failed(t.message ?: "Connection failed")
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== this@ArgosSocialService.webSocket) return
                stopHeartbeat()
                Log.d(TAG, "Social WebSocket closed: $code $reason")
                _connectionState.value = ConnectionState.Disconnected
                if (shouldReconnect && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            val payload = json.optJSONObject("payload")

            val message = when (type) {
                MessageTypes.AUTH_SUCCESS -> {
                    Log.d(TAG, "Auth successful")
                    reconnectAttempts = 0
                    _connectionState.value = ConnectionState.Connected
                    startHeartbeat()
                    sendDeviceRegistration()
                    null
                }

                MessageTypes.PRESENCE_UPDATE -> {
                    if (payload != null) {
                        val update = PresenceUpdate(
                            userId = payload.getString("user_id"),
                            status = payload.getString("status"),
                            game = payload.optJSONObject("game")?.let { gameJson ->
                                PresenceGameInfo(
                                    title = gameJson.getString("title"),
                                    coverThumb = gameJson.optString("cover_thumb", null)
                                )
                            },
                            deviceName = payload.optString("device_name", null),
                            timestamp = payload.getString("timestamp")
                        )
                        IncomingMessage.PresenceUpdate(update)
                    } else null
                }

                MessageTypes.FRIEND_REQUEST -> {
                    if (payload != null) {
                        val fromUser = payload.getJSONObject("from_user")
                        IncomingMessage.FriendRequest(
                            fromUserId = fromUser.getString("id"),
                            fromUsername = fromUser.getString("username")
                        )
                    } else null
                }

                MessageTypes.FRIEND_ACCEPTED -> {
                    if (payload != null) {
                        IncomingMessage.FriendAccepted(
                            userId = payload.getString("user_id"),
                            username = payload.getString("username"),
                            displayName = payload.getString("display_name"),
                            avatarColor = payload.getString("avatar_color")
                        )
                    } else null
                }

                MessageTypes.FRIEND_ADDED -> {
                    if (payload != null) {
                        IncomingMessage.FriendAdded(
                            userId = payload.getString("user_id"),
                            username = payload.getString("username"),
                            displayName = payload.getString("display_name"),
                            avatarColor = payload.getString("avatar_color")
                        )
                    } else null
                }

                MessageTypes.FRIEND_REMOVED -> {
                    if (payload != null) {
                        IncomingMessage.FriendRemoved(userId = payload.getString("user_id"))
                    } else null
                }

                MessageTypes.FRIEND_CODE_DATA -> {
                    if (payload != null) {
                        val code = payload.getString("code")
                        val url = payload.getString("url")
                        Log.d(TAG, "Received friend code: $code, url: $url")
                        IncomingMessage.FriendCodeData(code, url)
                    } else {
                        Log.w(TAG, "FRIEND_CODE_DATA received but payload is null")
                        null
                    }
                }

                MessageTypes.FRIENDS_DATA -> {
                    val friendsArray = payload?.optJSONArray("friends")
                    val friends = parseFriendsList(friendsArray)
                    Log.d(TAG, "Received initial friends: ${friends.size}")
                    IncomingMessage.FriendsData(friends)
                }

                MessageTypes.SHARED_COLLECTIONS -> {
                    val collectionsArray = payload?.optJSONArray("collections")
                    val collections = parseCollectionsList(collectionsArray)
                    Log.d(TAG, "Received shared collections: ${collections.size}")
                    IncomingMessage.SharedCollections(collections)
                }

                MessageTypes.SAVED_COLLECTIONS -> {
                    val collectionsArray = payload?.optJSONArray("collections")
                    val collections = parseCollectionsList(collectionsArray)
                    Log.d(TAG, "Received saved collections: ${collections.size}")
                    IncomingMessage.SavedCollections(collections)
                }

                MessageTypes.FEED_DATA -> {
                    val eventsArray = payload?.optJSONArray("events")
                    val hasMore = payload?.optBoolean("has_more", false) ?: false
                    Log.v(TAG, "FEED_DATA raw payload: events=${eventsArray?.length() ?: 0}, has_more=$hasMore")
                    val events = parseFeedEvents(eventsArray)
                    Log.d(TAG, "FEED_DATA parsed: ${events.size} events, hasMore=$hasMore")
                    events.forEachIndexed { i, e ->
                        Log.v(TAG, "  [$i] id=${e.id}, type=${e.type}, user=${e.user?.displayName}, title=${e.fallbackTitle}")
                    }
                    IncomingMessage.FeedData(events, hasMore)
                }

                MessageTypes.FEED_EVENT -> {
                    Log.v(TAG, "FEED_EVENT raw payload: $payload")
                    val event = payload?.let { parseFeedEvent(it) }
                    if (event != null) {
                        Log.d(TAG, "FEED_EVENT parsed: id=${event.id}, type=${event.type}, user=${event.user?.displayName}")
                        IncomingMessage.FeedEvent(event)
                    } else {
                        Log.w(TAG, "FEED_EVENT failed to parse")
                        null
                    }
                }

                MessageTypes.FEED_EVENT_UPDATED -> {
                    if (payload != null) {
                        val eventId = payload.getString("event_id")
                        val likeCount = payload.getInt("like_count")
                        val commentCount = payload.getInt("comment_count")
                        Log.d(TAG, "FEED_EVENT_UPDATED: eventId=$eventId, likes=$likeCount, comments=$commentCount")
                        IncomingMessage.FeedEventUpdated(eventId, likeCount, commentCount)
                    } else null
                }

                MessageTypes.FEED_COMMENT -> {
                    if (payload != null) {
                        val eventId = payload.getString("event_id")
                        val commentJson = payload.getJSONObject("comment")
                        Log.v(TAG, "FEED_COMMENT raw: eventId=$eventId, comment=$commentJson")
                        val comment = parseFeedComment(commentJson)
                        if (comment != null) {
                            Log.d(TAG, "FEED_COMMENT parsed: eventId=$eventId, commentId=${comment.id}, user=${comment.user?.displayName}")
                            IncomingMessage.FeedCommentReceived(eventId, comment)
                        } else {
                            Log.w(TAG, "FEED_COMMENT failed to parse comment")
                            null
                        }
                    } else null
                }

                MessageTypes.DEVICE_REVOKED -> {
                    val reason = payload?.optString("reason", "session_revoked") ?: "session_revoked"
                    Log.w(TAG, "Device session revoked: $reason")
                    shouldReconnect = false
                    onSessionRevoked?.invoke()
                    IncomingMessage.SessionRevoked(reason)
                }

                MessageTypes.ERROR -> {
                    val errorMsg = json.optString("error", null)
                        ?: payload?.optString("message", "Unknown error")
                        ?: "Unknown error"
                    val errorCode = payload?.optString("code", "error") ?: "error"
                    Log.e(TAG, "Server error: code=$errorCode, message=$errorMsg")

                    if (errorMsg == "authentication_failed" || errorCode == "authentication_failed") {
                        Log.w(TAG, "Authentication failed - session invalid, triggering logout")
                        shouldReconnect = false
                        onSessionRevoked?.invoke()
                        IncomingMessage.SessionRevoked("authentication_failed")
                    } else {
                        IncomingMessage.Error(code = errorCode, message = errorMsg)
                    }
                }

                MessageTypes.REQUEST_GAME_DATA -> {
                    if (payload != null) {
                        val igdbId = payload.getLong("igdb_id")
                        val gameTitle = payload.getString("game_title")
                        val platform = payload.optString("platform", null)
                        val fieldsArray = payload.optJSONArray("fields")
                        val fields = if (fieldsArray != null) {
                            (0 until fieldsArray.length()).map { fieldsArray.getString(it) }
                        } else emptyList()
                        Log.d(TAG, "Server requesting game data: igdbId=$igdbId, title=$gameTitle, fields=$fields")
                        IncomingMessage.RequestGameData(igdbId, gameTitle, platform, fields)
                    } else null
                }

                MessageTypes.DISCORD_TOKENS -> {
                    if (payload != null) {
                        IncomingMessage.DiscordTokens(
                            accessToken = payload.getString("access_token"),
                            tokenType = payload.optString("token_type", "Bearer"),
                            expiresIn = payload.optLong("expires_in", 3600)
                        )
                    } else null
                }

                MessageTypes.DISCORD_NOT_LINKED -> {
                    IncomingMessage.DiscordNotLinked
                }

                MessageTypes.EVENT_COMMENTS -> {
                    if (payload != null) {
                        val eventId = payload.getString("event_id")
                        val commentsArray = payload.optJSONArray("comments")
                        val comments = if (commentsArray != null) {
                            (0 until commentsArray.length()).mapNotNull { i ->
                                parseFeedComment(commentsArray.getJSONObject(i))
                            }
                        } else emptyList()
                        Log.d(TAG, "EVENT_COMMENTS: eventId=$eventId, count=${comments.size}")
                        IncomingMessage.EventCommentsData(eventId, comments)
                    } else null
                }

                MessageTypes.FAVORITE_FRIEND_UPDATED -> {
                    if (payload != null) {
                        IncomingMessage.FavoriteFriendUpdated(
                            friendId = payload.getString("friend_id"),
                            isFavorite = payload.getBoolean("is_favorite")
                        )
                    } else null
                }

                MessageTypes.SYNC_ACHIEVEMENT_UNLOCKS_RESULT -> {
                    if (payload != null) {
                        val idsArray = payload.optJSONArray("accepted_ra_ids")
                        val ids = if (idsArray != null) {
                            (0 until idsArray.length()).map { idsArray.getLong(it) }
                        } else emptyList()
                        Log.d(TAG, "Sync achievement unlocks result: ${ids.size} accepted")
                        _syncAchievementResult.value = ids
                    }
                    null
                }

                MessageTypes.PLAY_SESSIONS_SYNCED -> {
                    if (payload != null) {
                        val resultsArray = payload.optJSONArray("results")
                        val results = if (resultsArray != null) {
                            (0 until resultsArray.length()).map { i ->
                                val obj = resultsArray.getJSONObject(i)
                                SessionSyncResult(
                                    startTime = obj.getString("start_time"),
                                    status = obj.getString("status"),
                                    reason = obj.optString("reason", null)
                                )
                            }
                        } else emptyList()
                        Log.d(TAG, "Play sessions synced: ${results.size} results")
                        scope.launch { _playSessionSyncResult.emit(results) }
                    }
                    null
                }

                MessageTypes.UNREAD_COUNT -> {
                    val count = payload?.optInt("count", 0) ?: 0
                    Log.d(TAG, "Unread count: $count")
                    IncomingMessage.UnreadCount(count)
                }

                MessageTypes.NOTIFICATION -> {
                    if (payload != null) {
                        val notifJson = payload.optJSONObject("notification")
                        val usersJson = payload.optJSONObject("users")
                        val notif = notifJson?.let { parseNotification(it) }
                        val users = parseUsersMap(usersJson)
                        if (notif != null) {
                            Log.d(TAG, "Notification received: id=${notif.id}, type=${notif.type}")
                            IncomingMessage.NotificationReceived(notif, users)
                        } else null
                    } else null
                }

                MessageTypes.NOTIFICATIONS -> {
                    if (payload != null) {
                        val notifArray = payload.optJSONArray("notifications")
                        val usersJson = payload.optJSONObject("users")
                        val hasMore = payload.optBoolean("has_more", false)
                        val notifications = parseNotificationsList(notifArray)
                        val users = parseUsersMap(usersJson)
                        Log.d(TAG, "Notifications data: ${notifications.size}, hasMore=$hasMore")
                        IncomingMessage.NotificationsData(notifications, users, hasMore)
                    } else null
                }

                MessageTypes.COMMUNITY_FEED_DATA -> {
                    if (payload != null) {
                        val eventsArray = payload.optJSONArray("events")
                        val hasMore = payload.optBoolean("has_more", false)
                        val events = parseFeedEvents(eventsArray)
                        Log.d(TAG, "COMMUNITY_FEED_DATA: ${events.size} events, hasMore=$hasMore")
                        IncomingMessage.CommunityFeedData(events, hasMore)
                    } else null
                }

                MessageTypes.COMMUNITY_FOLLOWS -> {
                    if (payload != null) {
                        val followsArray = payload.optJSONArray("follows")
                        val follows = parseCommunityFollows(followsArray)
                        Log.d(TAG, "COMMUNITY_FOLLOWS: ${follows.size} follows")
                        IncomingMessage.CommunityFollowsData(follows)
                    } else null
                }

                MessageTypes.COMMUNITY_FOLLOW_UPDATED -> {
                    if (payload != null) {
                        val followObj = payload.optJSONObject("follow") ?: payload
                        val follow = parseCommunityFollow(followObj)
                        if (follow != null) {
                            Log.d(TAG, "COMMUNITY_FOLLOW_UPDATED: igdbGameId=${follow.igdbGameId}")
                            IncomingMessage.CommunityFollowUpdated(follow)
                        } else null
                    } else null
                }

                MessageTypes.USER_SETTINGS_DATA -> {
                    if (payload != null) {
                        val settings = UserSettings(
                            communityAutoShare = payload.optBoolean("community_auto_share", true)
                        )
                        Log.d(TAG, "USER_SETTINGS: autoShare=${settings.communityAutoShare}")
                        IncomingMessage.UserSettingsData(settings)
                    } else null
                }

                MessageTypes.HIDDEN_GAMES -> {
                    val gamesArray = payload?.optJSONArray("games")
                    val ids = if (gamesArray != null) {
                        (0 until gamesArray.length()).map { i ->
                            gamesArray.getJSONObject(i).getInt("igdb_game_id")
                        }.toSet()
                    } else emptySet()
                    Log.d(TAG, "HIDDEN_GAMES: ${ids.size} hidden games")
                    IncomingMessage.HiddenGames(ids)
                }

                MessageTypes.PONG -> {
                    lastPongReceivedAt = System.currentTimeMillis()
                    missedPongs = 0
                    if (BuildConfig.DEBUG) Log.v(TAG, "Pong received")
                    null
                }

                else -> IncomingMessage.Raw(type, payload?.toString() ?: "{}")
            }

            message?.let {
                scope.launch {
                    _incomingMessages.emit(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message", e)
        }
    }

    fun send(type: String, payload: Map<String, Any?>): Boolean {
        val json = JSONObject().apply {
            put("type", type)
            put("payload", JSONObject(payload))
        }
        val sent = webSocket?.send(json.toString()) ?: false
        if (!sent) {
            Log.w(TAG, "Failed to send message: $type (webSocket=${webSocket != null})")
        }
        return sent
    }

    private fun sendDeviceRegistration() {
        Log.d(TAG, "Sending device registration: model=$deviceModel, manufacturer=$deviceManufacturer")
        send(MessageTypes.REGISTER_DEVICE, mapOf(
            "device_id" to deviceId,
            "device_manufacturer" to deviceManufacturer,
            "device_model" to deviceModel
        ))
    }

    fun sendPresence(status: PresenceStatus, gameIgdbId: Int? = null, gameTitle: String? = null, deviceName: String? = null): Boolean {
        return send(MessageTypes.SET_PRESENCE, mapOf(
            "status" to status.value,
            "game_igdb_id" to gameIgdbId,
            "game_title" to gameTitle,
            "device_name" to deviceName
        ))
    }

    fun syncPlaySessions(sessions: List<PlaySessionPayload>) {
        send(MessageTypes.SYNC_PLAY_SESSIONS, mapOf(
            "sessions" to sessions.map { it.toMap() }
        ))
    }

    fun syncAchievementUnlocks(igdbId: Long?, raGameId: Long?, gameTitle: String, unlocks: List<Map<String, Any?>>): Boolean {
        return send(MessageTypes.SYNC_ACHIEVEMENT_UNLOCKS, mapOf(
            "igdb_id" to igdbId,
            "ra_game_id" to raGameId,
            "game_title" to gameTitle,
            "unlocks" to unlocks
        ))
    }

    fun requestDiscordTokens() {
        val json = JSONObject().apply {
            put("type", MessageTypes.REQUEST_DISCORD_TOKENS)
        }
        webSocket?.send(json.toString())
    }

    fun sendHideGame(igdbGameId: Int): Boolean {
        return send(MessageTypes.HIDE_GAME, mapOf("igdb_game_id" to igdbGameId))
    }

    fun sendUnhideGame(igdbGameId: Int): Boolean {
        return send(MessageTypes.UNHIDE_GAME, mapOf("igdb_game_id" to igdbGameId))
    }

    fun sendGetHiddenGames(): Boolean {
        return send(MessageTypes.GET_HIDDEN_GAMES, emptyMap())
    }

    fun getFriendCode() {
        val json = JSONObject().apply {
            put("type", MessageTypes.GET_FRIEND_CODE)
        }
        val sent = webSocket?.send(json.toString()) ?: false
        Log.d(TAG, "getFriendCode: sent=$sent, webSocket=${webSocket != null}")
    }

    fun regenerateFriendCode() {
        send(MessageTypes.REGENERATE_FRIEND_CODE, emptyMap())
    }

    fun addFriendByCode(code: String) {
        send(MessageTypes.LOOKUP_FRIEND_CODE, mapOf("code" to code))
    }

    fun getFeed(limit: Int? = null, beforeId: String? = null, userId: String? = null) {
        Log.d(TAG, "getFeed: limit=$limit, beforeId=$beforeId, userId=$userId")
        val payload = mutableMapOf<String, Any?>()
        if (limit != null) payload["limit"] = limit
        if (beforeId != null) payload["before_id"] = beforeId
        if (userId != null) payload["user_id"] = userId

        val json = JSONObject().apply {
            put("type", MessageTypes.GET_FEED)
            if (payload.isNotEmpty()) put("payload", JSONObject(payload))
        }
        Log.v(TAG, "getFeed sending: $json")
        webSocket?.send(json.toString())
    }

    fun likeEvent(eventId: String) {
        Log.d(TAG, "likeEvent: eventId=$eventId")
        send(MessageTypes.LIKE_EVENT, mapOf("event_id" to eventId))
    }

    fun commentEvent(eventId: String, content: String) {
        Log.d(TAG, "commentEvent: eventId=$eventId, content=${content.take(50)}")
        send(MessageTypes.COMMENT_EVENT, mapOf(
            "event_id" to eventId,
            "content" to content
        ))
    }

    fun getEventComments(eventId: String) {
        Log.d(TAG, "getEventComments: eventId=$eventId")
        send(MessageTypes.GET_EVENT_COMMENTS, mapOf("event_id" to eventId))
    }

    fun getNotifications(limit: Int? = null, before: String? = null) {
        val payload = mutableMapOf<String, Any?>()
        if (limit != null) payload["limit"] = limit
        if (before != null) payload["before"] = before
        val json = JSONObject().apply {
            put("type", MessageTypes.GET_NOTIFICATIONS)
            if (payload.isNotEmpty()) put("payload", JSONObject(payload))
        }
        webSocket?.send(json.toString())
    }

    fun markNotificationRead(notificationId: String? = null, eventId: String? = null, type: String? = null) {
        val payload = mutableMapOf<String, Any?>()
        if (notificationId != null) payload["notification_id"] = notificationId
        if (eventId != null) payload["event_id"] = eventId
        if (type != null) payload["type"] = type
        send(MessageTypes.MARK_NOTIFICATION_READ, payload)
    }

    fun markAllNotificationsRead() {
        send(MessageTypes.MARK_ALL_READ, emptyMap())
    }

    fun getEvent(eventId: String) {
        send(MessageTypes.GET_EVENT, mapOf("event_id" to eventId))
    }

    fun deleteComment(commentId: String) {
        Log.d(TAG, "deleteComment: commentId=$commentId")
        send(MessageTypes.DELETE_COMMENT, mapOf("comment_id" to commentId))
    }

    fun hideEvent(eventId: String) {
        Log.d(TAG, "hideEvent: eventId=$eventId")
        send(MessageTypes.HIDE_EVENT, mapOf("event_id" to eventId))
    }

    fun toggleFavoriteFriend(friendId: String): Boolean {
        return send(MessageTypes.TOGGLE_FAVORITE_FRIEND, mapOf("friend_id" to friendId))
    }

    fun reportEvent(eventId: String, reason: String? = null) {
        Log.d(TAG, "reportEvent: eventId=$eventId, reason=$reason")
        send(MessageTypes.REPORT_EVENT, buildMap {
            put("event_id", eventId)
            if (reason != null) put("reason", reason)
        })
    }

    fun sendGameData(
        igdbId: Long,
        gameTitle: String,
        developer: String?,
        releaseYear: Int?,
        genre: String?,
        description: String?,
        coverThumbBase64: String?,
        gradientColors: String?
    ) {
        Log.d(TAG, "sendGameData: igdbId=$igdbId, title=$gameTitle, hasCover=${coverThumbBase64 != null}")
        send(MessageTypes.GAME_DATA, mapOf(
            "igdb_id" to igdbId,
            "game_title" to gameTitle,
            "developer" to developer,
            "release_year" to releaseYear,
            "genre" to genre,
            "description" to description,
            "cover_thumb" to coverThumbBase64,
            "gradient_colors" to gradientColors
        ))
    }

    fun createFeedEvent(
        eventType: String,
        igdbId: Long?,
        gameTitle: String,
        data: Map<String, Any?>,
        occurredAt: String? = null
    ): Boolean {
        return send(MessageTypes.CREATE_FEED_EVENT, buildMap {
            put("event_type", eventType)
            put("igdb_id", igdbId)
            put("game_title", gameTitle)
            put("data", JSONObject(data))
            if (occurredAt != null) put("occurred_at", occurredAt)
        })
    }

    fun createDoodle(
        canvasSize: Int,
        data: String,
        caption: String?,
        igdbId: Int?,
        gameTitle: String?
    ) {
        Log.d(TAG, "createDoodle: size=$canvasSize, dataLen=${data.length}, caption=${caption?.take(20)}")
        send(MessageTypes.CREATE_DOODLE, mapOf(
            "canvas_size" to canvasSize,
            "data" to data,
            "caption" to caption,
            "igdb_id" to igdbId,
            "game_title" to gameTitle
        ))
    }

    fun createPost(
        body: String,
        canvasSize: Int? = null,
        doodleData: String? = null,
        igdbId: Int? = null,
        gameTitle: String? = null,
        public: Boolean = false
    ) {
        Log.d(TAG, "createPost: bodyLen=${body.length}, hasDoodle=${doodleData != null}, igdbId=$igdbId, public=$public")
        send(MessageTypes.CREATE_POST, buildMap {
            put("body", body)
            if (canvasSize != null) put("canvas_size", canvasSize)
            if (doodleData != null) put("doodle_data", doodleData)
            if (igdbId != null) put("igdb_id", igdbId)
            if (gameTitle != null) put("game_title", gameTitle)
            put("public", public)
        })
    }

    fun followCommunity(igdbGameId: Int, isPrivate: Boolean = false, anonymous: Boolean = false) {
        send(MessageTypes.FOLLOW_COMMUNITY, mapOf(
            "igdb_game_id" to igdbGameId,
            "private" to isPrivate,
            "anonymous" to anonymous
        ))
    }

    fun unfollowCommunity(igdbGameId: Int) {
        send(MessageTypes.UNFOLLOW_COMMUNITY, mapOf("igdb_game_id" to igdbGameId))
    }

    fun updateCommunityFollow(igdbGameId: Int, isPrivate: Boolean, anonymous: Boolean) {
        send(MessageTypes.UPDATE_COMMUNITY_FOLLOW, mapOf(
            "igdb_game_id" to igdbGameId,
            "private" to isPrivate,
            "anonymous" to anonymous
        ))
    }

    fun getCommunityFeed(igdbGameId: Int? = null, limit: Int? = null, beforeId: String? = null) {
        val payload = mutableMapOf<String, Any?>()
        if (igdbGameId != null) payload["igdb_game_id"] = igdbGameId
        if (limit != null) payload["limit"] = limit
        if (beforeId != null) payload["before_id"] = beforeId
        val json = JSONObject().apply {
            put("type", MessageTypes.GET_COMMUNITY_FEED)
            if (payload.isNotEmpty()) put("payload", JSONObject(payload))
        }
        webSocket?.send(json.toString())
    }

    fun getCommunityFollows() {
        send(MessageTypes.GET_COMMUNITY_FOLLOWS, emptyMap())
    }

    fun getUserSettings() {
        send(MessageTypes.GET_USER_SETTINGS, emptyMap())
    }

    fun updateUserSettings(communityAutoShare: Boolean? = null) {
        val payload = mutableMapOf<String, Any?>()
        if (communityAutoShare != null) payload["community_auto_share"] = communityAutoShare
        send(MessageTypes.UPDATE_USER_SETTINGS, payload)
    }

    fun updateEventVisibility(eventId: String, public: Boolean) {
        send(MessageTypes.UPDATE_EVENT_VISIBILITY, mapOf(
            "event_id" to eventId,
            "public" to public
        ))
    }

    data class PlaySessionPayload(
        val userId: String,
        val deviceId: String,
        val deviceManufacturer: String,
        val deviceModel: String,
        val igdbId: Long?,
        val gameTitle: String,
        val platformSlug: String,
        val startTime: String,
        val endTime: String,
        val continued: Boolean,
        val standbyMs: Long = 0,
        val activePlayMs: Long = 0
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "user_id" to userId,
            "device_id" to deviceId,
            "device_manufacturer" to deviceManufacturer,
            "device_model" to deviceModel,
            "igdb_id" to igdbId,
            "game_title" to gameTitle,
            "platform_slug" to platformSlug,
            "start_time" to startTime,
            "end_time" to endTime,
            "continued" to continued,
            "standby_ms" to standbyMs,
            "active_play_ms" to activePlayMs
        )
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        lastPongReceivedAt = System.currentTimeMillis()
        missedPongs = 0
        heartbeatJob = scope.launch {
            Log.d(TAG, "Heartbeat started (interval=${HEARTBEAT_INTERVAL_MS}ms, timeout=${PONG_TIMEOUT_MS}ms)")
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)

                val elapsed = System.currentTimeMillis() - lastPongReceivedAt
                if (elapsed > HEARTBEAT_INTERVAL_MS + PONG_TIMEOUT_MS) {
                    missedPongs++
                    Log.w(TAG, "Heartbeat: pong overdue by ${elapsed}ms (missed=$missedPongs). Forcing reconnect.")
                    webSocket?.cancel()
                    webSocket = null
                    _connectionState.value = ConnectionState.Failed("Heartbeat timeout")
                    if (shouldReconnect) {
                        scheduleReconnect()
                    }
                    return@launch
                }

                val sent = sendPing()
                Log.d(TAG, "Heartbeat: ping sent=$sent, lastPong=${elapsed}ms ago, missed=$missedPongs")

                if (!sent) {
                    Log.w(TAG, "Heartbeat: ping send failed, connection likely dead. Forcing reconnect.")
                    webSocket?.cancel()
                    webSocket = null
                    _connectionState.value = ConnectionState.Failed("Heartbeat send failed")
                    if (shouldReconnect) {
                        scheduleReconnect()
                    }
                    return@launch
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Heartbeat stopped")
    }

    private fun sendPing(): Boolean {
        val json = JSONObject().apply {
            put("type", MessageTypes.PING)
        }
        return webSocket?.send(json.toString()) ?: false
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val delayMs = calculateReconnectDelay()
            Log.d(TAG, "Scheduling reconnect in ${delayMs}ms (attempt ${reconnectAttempts + 1})")
            delay(delayMs)
            reconnectAttempts++
            connectInternal()
        }
    }

    private fun calculateReconnectDelay(): Long {
        val baseDelay = 1000L
        val maxDelay = 60000L
        val delay = (baseDelay * (1 shl reconnectAttempts.coerceAtMost(6))).coerceAtMost(maxDelay)
        return delay
    }

    fun reconnectIfNeeded() {
        if (sessionToken == null) return
        val state = _connectionState.value
        if (state == ConnectionState.Connected || state == ConnectionState.Connecting || state == ConnectionState.Reconnecting) return
        Log.d(TAG, "Proactive reconnect triggered (state=$state)")
        reconnectJob?.cancel()
        reconnectAttempts = 0
        connectInternal()
    }

    fun disconnect() {
        shouldReconnect = false
        stopHeartbeat()
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        sessionToken = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.Connected

    private fun parseFriendsList(array: org.json.JSONArray?): List<Friend> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                val userObj = obj.getJSONObject("user")
                val presenceObj = obj.optJSONObject("presence")
                Friend(
                    id = userObj.getString("id"),
                    username = userObj.getString("username"),
                    displayName = userObj.getString("display_name"),
                    avatarColor = userObj.getString("avatar_color"),
                    status = obj.getString("status"),
                    presence = presenceObj?.let {
                        PresenceStatus.fromValue(it.optString("status", "offline"))
                    },
                    currentGame = presenceObj?.optJSONObject("game")?.let { gameJson ->
                        PresenceGameInfo(
                            title = gameJson.getString("title"),
                            coverThumb = gameJson.optString("cover_thumb", null)
                        )
                    },
                    deviceName = presenceObj?.optString("device_name", null),
                    isFavorite = obj.optBoolean("is_favorite", false)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse friend", e)
                null
            }
        }
    }

    private fun parseCollectionsList(array: org.json.JSONArray?): List<CollectionSummary> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                val obj = array.getJSONObject(i)
                CollectionSummary(
                    id = obj.getString("id"),
                    ownerId = obj.getString("owner_id"),
                    ownerName = obj.getString("owner_name"),
                    name = obj.getString("name"),
                    description = obj.optString("description", null),
                    gameCount = obj.optInt("game_count", 0)
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse collection", e)
                null
            }
        }
    }

    private fun parseFeedEvents(array: org.json.JSONArray?): List<FeedEventDto> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            try {
                parseFeedEvent(array.getJSONObject(i))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse feed event", e)
                null
            }
        }
    }

    private fun parseFeedEvent(obj: JSONObject): FeedEventDto? {
        return try {
            val user = obj.optJSONObject("user")?.let { parseUser(it) }
            val payloadObj = obj.optJSONObject("payload")
            val payload = payloadObj?.let { jsonObjectToMap(it) }
            val game = obj.optJSONObject("game")?.let { parseGameInfo(it) }
            val commentsArray = obj.optJSONArray("comments")
            val comments = if (commentsArray != null) {
                (0 until commentsArray.length()).mapNotNull { i ->
                    parseFeedComment(commentsArray.getJSONObject(i))
                }
            } else emptyList()

            FeedEventDto(
                id = obj.getString("id"),
                userId = obj.getString("user_id"),
                user = user,
                type = obj.getString("type"),
                payload = payload,
                igdbId = obj.optInt("igdb_id", -1).takeIf { it != -1 },
                raGameId = obj.optInt("ra_game_id", -1).takeIf { it != -1 },
                fallbackTitle = obj.optString("fallback_title", ""),
                game = game,
                createdAt = obj.getString("created_at"),
                hidden = obj.optBoolean("hidden", false),
                likeCount = obj.optInt("like_count", 0),
                commentCount = obj.optInt("comment_count", 0),
                isLikedByMe = obj.optBoolean("is_liked_by_me", false),
                comments = comments
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse feed event", e)
            null
        }
    }

    private fun parseGameInfo(obj: JSONObject): FeedGameInfo {
        return FeedGameInfo(
            id = obj.optString("id", null),
            igdbId = obj.optInt("igdb_id", -1).takeIf { it != -1 },
            title = obj.optString("title", null),
            platform = obj.optString("platform", null),
            coverThumb = obj.optString("cover_thumb", null),
            gradientColors = obj.optString("gradient_colors", null)
        )
    }

    private fun parseFeedComment(obj: JSONObject): FeedComment? {
        return try {
            val user = obj.optJSONObject("user")?.let { parseUser(it) }
            FeedComment(
                id = obj.getString("id"),
                eventId = obj.getString("event_id"),
                userId = obj.getString("user_id"),
                user = user,
                content = obj.getString("content"),
                createdAt = obj.getString("created_at")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse feed comment", e)
            null
        }
    }

    private fun parseUser(obj: JSONObject): SocialUser {
        return SocialUser(
            id = obj.getString("id"),
            username = obj.getString("username"),
            displayName = obj.getString("display_name"),
            avatarColor = obj.getString("avatar_color")
        )
    }

    private fun parseNotification(obj: JSONObject): SocialNotification? {
        return try {
            val actorsArray = obj.optJSONArray("actors")
            val actors = if (actorsArray != null) {
                (0 until actorsArray.length()).map { actorsArray.getString(it) }
            } else emptyList()

            val metadataObj = obj.optJSONObject("metadata")
            val metadata = metadataObj?.let { jsonObjectToMap(it) }

            SocialNotification(
                id = obj.getString("id"),
                type = obj.getString("type"),
                eventId = obj.optString("event_id", null),
                actors = actors,
                newCount = obj.optInt("new_count", 0),
                metadata = metadata,
                viewedAt = obj.optString("viewed_at", null),
                updatedAt = obj.getString("updated_at"),
                createdAt = obj.getString("created_at"),
                eventType = obj.optString("event_type", null),
                eventPreview = obj.optString("event_preview", null)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse notification", e)
            null
        }
    }

    private fun parseNotificationsList(array: org.json.JSONArray?): List<SocialNotification> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            parseNotification(array.getJSONObject(i))
        }
    }

    private fun parseUsersMap(obj: JSONObject?): Map<String, SocialUser> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, SocialUser>()
        obj.keys().forEach { key ->
            try {
                val userObj = obj.getJSONObject(key)
                map[key] = parseUser(userObj)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse user in users map: $key", e)
            }
        }
        return map
    }

    private fun parseCommunityFollows(array: org.json.JSONArray?): List<CommunityFollow> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            parseCommunityFollow(array.getJSONObject(i))
        }
    }

    private fun parseCommunityFollow(obj: JSONObject): CommunityFollow? {
        return try {
            CommunityFollow(
                userId = obj.getString("user_id"),
                igdbGameId = obj.getInt("igdb_game_id"),
                isPrivate = obj.optBoolean("private", false),
                anonymous = obj.optBoolean("anonymous", false),
                createdAt = obj.optString("created_at", ""),
                gameTitle = obj.optString("game_title", ""),
                coverThumb = obj.optString("cover_thumb", null)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse community follow", e)
            null
        }
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        obj.keys().forEach { key ->
            map[key] = when (val value = obj.get(key)) {
                JSONObject.NULL -> null
                is JSONObject -> jsonObjectToMap(value)
                is org.json.JSONArray -> (0 until value.length()).map { value.get(it) }
                else -> value
            }
        }
        return map
    }

    companion object {
        private const val TAG = "ArgosSocialService"
        private const val WS_URL = BuildConfig.SOCIAL_API_URL
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 10_000L
    }
}
