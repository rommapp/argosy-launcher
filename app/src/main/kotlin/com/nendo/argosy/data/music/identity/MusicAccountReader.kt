package com.nendo.argosy.data.music.identity

import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import kotlinx.coroutines.flow.first
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private const val NO_ACCOUNT = 0L

/**
 * The accounts music identity is filed under, read once per operation. [ownerKey] is the active
 * RomM user id (0 when signed out); account keys are `<user>@<server>` with the server as
 * host[:port].
 */
data class MusicAccounts(
    val ownerKey: Long,
    val rommServerKey: String,
    val rommAccountKey: String,
    val navidromeAccountKey: String
)

@Singleton
class MusicAccountReader @Inject constructor(
    private val syncPreferences: SyncPreferencesRepository,
    private val navidromePreferences: NavidromePreferencesRepository
) {
    suspend fun current(): MusicAccounts {
        val sync = syncPreferences.preferences.first()
        val navidrome = navidromePreferences.preferences.first()
        val rommServer = serverKey(sync.rommBaseUrl)
        val ownerKey = sync.rommUserId ?: NO_ACCOUNT
        return MusicAccounts(
            ownerKey = ownerKey,
            rommServerKey = rommServer,
            rommAccountKey = "$ownerKey@$rommServer",
            navidromeAccountKey = "${navidrome.username.orEmpty()}@${serverKey(navidrome.serverUrl)}"
        )
    }

    companion object {
        fun serverKey(url: String?): String {
            val trimmed = url?.trim().orEmpty()
            if (trimmed.isEmpty()) return ""
            val parsed = runCatching { URI(trimmed) }.getOrNull()
            val host = parsed?.host
            val authority = if (host != null) {
                if (parsed.port > 0) "$host:${parsed.port}" else host
            } else {
                trimmed.substringAfter("://").substringBefore('/')
            }
            return authority.lowercase(Locale.ROOT)
        }
    }
}
