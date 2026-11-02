package com.nendo.argosy.data.netplay.external

import com.nendo.argosy.data.netplay.StunClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds this device's RetroArch host in the public libretro lobby, matched on the public address
 * STUN reports. Hosts only: the lobby never lists connected clients.
 */
@Singleton
class RetroArchLobby @Inject constructor() {

    private val stunClient = StunClient()
    private var publicIp: String? = null
    private var publicIpAtMillis = 0L

    suspend fun isHosting(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val ip = publicIp(nowMillis) ?: return false
        val body = fetchLobby() ?: return false
        return hostIps(body).contains(ip)
    }

    private suspend fun publicIp(nowMillis: Long): String? {
        publicIp?.takeIf { nowMillis - publicIpAtMillis < PUBLIC_IP_TTL_MS }?.let { return it }
        val found = StunClient.newShuffledServerList().take(STUN_ATTEMPTS).firstNotNullOfOrNull { server ->
            stunClient.discoverReflexiveAddress(server)?.address?.hostAddress
        }
        if (found != null) {
            publicIp = found
            publicIpAtMillis = nowMillis
        }
        return found
    }

    private suspend fun fetchLobby(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LOBBY_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) null
                else connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    companion object {
        private const val LOBBY_URL = "http://lobby.libretro.com/list"
        private const val TIMEOUT_MS = 8_000
        private const val PUBLIC_IP_TTL_MS = 5 * 60_000L
        private const val STUN_ATTEMPTS = 2

        fun hostIps(body: String): Set<String> = runCatching {
            val rooms = JSONArray(body)
            (0 until rooms.length()).mapNotNull { index ->
                rooms.optJSONObject(index)?.optJSONObject("fields")?.optString("ip", "")
                    ?.takeIf { it.isNotEmpty() }
            }.toSet()
        }.getOrDefault(emptySet())
    }
}
