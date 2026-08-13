package com.nendo.argosy.data.remote.jellyfin

import com.nendo.argosy.util.Logger
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.TimeSource

private const val TAG = "JellyfinServerDiscovery"
private const val DISCOVERY_PORT = 7359
private const val DISCOVERY_MESSAGE = "who is JellyfinServer?"
private const val RECEIVE_BUFFER_BYTES = 4096
private const val SOCKET_POLL_TIMEOUT_MS = 500
private const val DEFAULT_DISCOVERY_TIMEOUT_MS = 3_000L

@JsonClass(generateAdapter = true)
internal data class JellyfinDiscoveryResponse(
    @Json(name = "Address") val address: String? = null,
    @Json(name = "Id") val id: String? = null,
    @Json(name = "Name") val name: String? = null,
    @Json(name = "EndpointAddress") val endpointAddress: String? = null
)

/**
 * Finds Jellyfin servers on the local network by UDP broadcast on port 7359.
 *
 * The datagram goes to every interface's own directed broadcast address as well as to the global
 * 255.255.255.255. Android routinely drops the global form on multi-interface devices - a handheld
 * on wifi with an active usb tether has two - and a scan that only sends the global form finds
 * nothing on exactly the setups where discovery matters most.
 *
 * A server that answers is a candidate, not a validated target: the address it reports is whatever
 * it was configured with, so the caller still puts it through `/System/Info/Public`.
 */
@Singleton
class JellyfinServerDiscovery @Inject constructor(
    private val moshi: Moshi
) {
    private val adapter by lazy { moshi.adapter(JellyfinDiscoveryResponse::class.java) }

    suspend fun discover(
        timeoutMs: Long = DEFAULT_DISCOVERY_TIMEOUT_MS
    ): List<JellyfinDiscoveredServer> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, JellyfinDiscoveredServer>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = SOCKET_POLL_TIMEOUT_MS
            }
            val payload = DISCOVERY_MESSAGE.toByteArray()
            for (target in broadcastTargets()) {
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, target, DISCOVERY_PORT))
                }.onFailure { Logger.debug(TAG, "broadcast to $target failed: ${it.message}") }
            }
            collectResponses(socket, timeoutMs, found)
        } catch (e: Exception) {
            Logger.info(TAG, "discovery failed: ${e.message}")
        } finally {
            socket?.close()
        }
        found.values.toList()
    }

    private fun collectResponses(
        socket: DatagramSocket,
        timeoutMs: Long,
        found: MutableMap<String, JellyfinDiscoveredServer>
    ) {
        val started = TimeSource.Monotonic.markNow()
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        while (started.elapsedNow().inWholeMilliseconds < timeoutMs) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (e: Exception) {
                Logger.debug(TAG, "receive failed: ${e.message}")
                break
            }
            val body = String(packet.data, packet.offset, packet.length).trim()
            val parsed = runCatching { adapter.fromJson(body) }.getOrNull() ?: continue
            val address = parsed.address?.trimEnd('/') ?: continue
            found[address] = JellyfinDiscoveredServer(
                address = address,
                name = parsed.name,
                id = parsed.id,
                endpointAddress = parsed.endpointAddress
            )
        }
    }

    private fun broadcastTargets(): List<InetAddress> {
        val targets = mutableListOf<InetAddress>()
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNull { it.broadcast }
                .forEach { targets += it }
        }.onFailure { Logger.debug(TAG, "interface enumeration failed: ${it.message}") }
        runCatching { targets += InetAddress.getByName("255.255.255.255") }
        return targets.distinct()
    }
}
