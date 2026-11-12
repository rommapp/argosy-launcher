package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.remote.ssl.ClientCertStore
import com.nendo.argosy.data.remote.ssl.ROMM_CLIENT_CERT_SCOPE
import com.nendo.argosy.data.remote.ssl.UserCertStore
import com.nendo.argosy.data.remote.ssl.UserCertTrustManager.withUserCertTrust
import com.nendo.argosy.util.Logger
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMDeviceSocket"
private const val DEVICES_NAMESPACE = "/devices"
private const val SOCKET_PATH = "/ws/socket.io"
private const val EVENT_INSTALL_QUEUED = "install:queued"
private const val RECONNECT_DELAY_MS = 1_000L
private const val RECONNECT_DELAY_MAX_MS = 60_000L
private const val RECONNECT_JITTER = 0.5
private const val READ_TIMEOUT_SECONDS = 60L
private val REFUSAL_MESSAGES = setOf("unauthorized", "disabled")
private const val SERVER_DISCONNECT_REASON = "io server disconnect"

/**
 * The RomM `/devices` socket.io namespace, authenticated with the device-bound client token.
 * It carries nudges only; every install is read through the claim endpoint.
 */
@Singleton
class RomMDeviceSocket @Inject constructor(
    private val userCertStore: UserCertStore,
    private val clientCertStore: ClientCertStore
) {
    sealed interface Event {
        data object Connected : Event
        data object InstallQueued : Event
        data class Refused(val reason: String) : Event
    }

    data class Target(val baseUrl: String, val token: String)

    private val _events = MutableSharedFlow<Event>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var socket: Socket? = null
    private var target: Target? = null

    @Synchronized
    fun connect(next: Target) {
        if (next == target && socket != null) return
        closeCurrent()
        val built = runCatching { buildSocket(next) }
            .onFailure { Logger.warn(TAG, "connect: could not build the socket for ${next.baseUrl}", it) }
            .getOrNull() ?: return
        target = next
        socket = built
        built.connect()
        Logger.info(TAG, "connect: opening $DEVICES_NAMESPACE at ${next.baseUrl}")
    }

    @Synchronized
    fun disconnect() {
        if (socket == null) return
        closeCurrent()
        Logger.info(TAG, "disconnect: closed")
    }

    private fun closeCurrent() {
        socket?.let {
            it.off()
            it.disconnect()
        }
        socket = null
        target = null
    }

    private fun buildSocket(next: Target): Socket {
        val base = URI(next.baseUrl)
        val prefix = base.rawPath.orEmpty().trimEnd('/')
        val client = OkHttpClient.Builder()
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withUserCertTrust(userCertStore, clientCertStore, ROMM_CLIENT_CERT_SCOPE)
            .build()
        val options = IO.Options().apply {
            path = "$prefix$SOCKET_PATH"
            auth = mapOf("token" to next.token)
            transports = arrayOf(WebSocket.NAME)
            forceNew = true
            reconnection = true
            reconnectionDelay = RECONNECT_DELAY_MS
            reconnectionDelayMax = RECONNECT_DELAY_MAX_MS
            randomizationFactor = RECONNECT_JITTER
            callFactory = client
            webSocketFactory = client
        }
        val uri = URI("${base.scheme}://${base.rawAuthority}$DEVICES_NAMESPACE")
        val created = IO.socket(uri, options)
        created.on(Socket.EVENT_CONNECT) {
            Logger.info(TAG, "connected")
            _events.tryEmit(Event.Connected)
        }
        created.on(Socket.EVENT_DISCONNECT) { args ->
            Logger.info(TAG, "disconnected: ${args.firstOrNull()}")
            if (args.firstOrNull() == SERVER_DISCONNECT_REASON) reopenAfterServerClose(created)
        }
        created.on(Socket.EVENT_CONNECT_ERROR) { args -> onConnectError(created, args) }
        created.on(EVENT_INSTALL_QUEUED) {
            _events.tryEmit(Event.InstallQueued)
        }
        return created
    }

    private fun reopenAfterServerClose(source: Socket) {
        synchronized(this) {
            if (socket !== source) return
            Logger.info(TAG, "server closed the device socket, reconnecting")
            source.connect()
        }
    }

    private fun onConnectError(source: Socket, args: Array<out Any?>) {
        val refusal = (args.firstOrNull() as? JSONObject)?.optString("message")
            ?.takeIf { it in REFUSAL_MESSAGES }
        if (refusal == null) {
            Logger.info(TAG, "connect error, retrying: ${args.firstOrNull()}")
            return
        }
        Logger.warn(TAG, "server refused the device socket: $refusal")
        synchronized(this) {
            if (socket === source) closeCurrent()
        }
        _events.tryEmit(Event.Refused(refusal))
    }
}
