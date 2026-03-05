package com.nendo.argosy.data.social

import android.app.Application
import android.os.Build
import android.provider.Settings
import android.util.Log
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

    private var webSocket: WebSocket? = null
    private var sessionToken: String? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = false
    private var reconnectJob: kotlinx.coroutines.Job? = null

    var onSessionRevoked: (() -> Unit)? = null

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
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

        webSocket?.cancel()
        webSocket = null

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
                Log.d(TAG, "Social message received: ${text.take(200)}")
                handleMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val responseInfo = response?.let { "HTTP ${it.code} ${it.message}" } ?: "no response"
                Log.e(TAG, "Social WebSocket failure: ${t.javaClass.simpleName}: ${t.message} | $responseInfo", t)
                _connectionState.value = ConnectionState.Failed(t.message ?: "Connection failed")
                if (shouldReconnect) {
                    scheduleReconnect()
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
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

    fun requestDiscordTokens() {
        val json = JSONObject().apply {
            put("type", MessageTypes.REQUEST_DISCORD_TOKENS)
        }
        webSocket?.send(json.toString())
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

    fun deleteComment(commentId: String) {
        Log.d(TAG, "deleteComment: commentId=$commentId")
        send(MessageTypes.DELETE_COMMENT, mapOf("comment_id" to commentId))
    }

    fun hideEvent(eventId: String) {
        Log.d(TAG, "hideEvent: eventId=$eventId")
        send(MessageTypes.HIDE_EVENT, mapOf("event_id" to eventId))
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
        val continued: Boolean
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
            "continued" to continued
        )
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
        if (state == ConnectionState.Connected || state == ConnectionState.Connecting) return
        Log.d(TAG, "Proactive reconnect triggered (state=$state)")
        reconnectJob?.cancel()
        reconnectAttempts = 0
        connectInternal()
    }

    fun disconnect() {
        shouldReconnect = false
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
                    deviceName = presenceObj?.optString("device_name", null)
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
        private const val WS_URL = "https://api.argosy.dev/"
    }
}
