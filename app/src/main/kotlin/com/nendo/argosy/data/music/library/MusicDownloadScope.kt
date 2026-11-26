package com.nendo.argosy.data.music.library

import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.MusicDownloadDao
import com.nendo.argosy.data.local.entity.MusicDownloadEntity
import com.nendo.argosy.data.music.identity.MusicAccountReader
import com.nendo.argosy.data.preferences.NavidromePreferencesRepository
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.domain.model.MusicSourceKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The server each music source is signed in to, in [MusicDownloadEntity.serverKey] form; null when
 * the source has none. A downloaded row belongs to the current device state when its server key
 * equals the key of its own source, whoever downloaded it.
 */
data class MusicDownloadServers(val romm: String?, val navidrome: String?) {

    val keys: List<String> get() = listOfNotNull(romm, navidrome)

    fun keyFor(kind: MusicSourceKind): String? = when (kind) {
        MusicSourceKind.ROMM -> romm
        MusicSourceKind.NAVIDROME -> navidrome
    }

    fun matches(row: MusicDownloadEntity, trackKey: String): Boolean =
        row.trackKey == trackKey && MusicSourceKind.fromToken(row.source)?.let(::keyFor) == row.serverKey
}

data class MusicDownloadScope(val ownerKey: Long, val servers: MusicDownloadServers)

@Singleton
class MusicDownloadScopeReader @Inject constructor(
    private val database: ALauncherDatabase,
    private val accountReader: MusicAccountReader,
    rommAccounts: RomMAccountRepository,
    navidromePreferences: NavidromePreferencesRepository
) {
    private val dao: MusicDownloadDao get() = database.musicDownloadDao()
    private val adoptLock = Mutex()
    private val adoptedServerKeys = mutableSetOf<String>()

    val servers: Flow<MusicDownloadServers> = combine(
        rommAccounts.observeActiveAccount(),
        navidromePreferences.preferences
    ) { account, navidrome ->
        MusicDownloadServers(
            romm = MusicDownloadEntity.serverKeyOf(MusicSourceKind.ROMM.token, account?.baseUrl),
            navidrome = MusicDownloadEntity.serverKeyOf(MusicSourceKind.NAVIDROME.token, navidrome.serverUrl)
        )
    }.distinctUntilChanged()

    /**
     * The owner and servers for one operation. Rows still carrying the unscoped server key are
     * handed to the current server of their source the first time that server is seen.
     */
    suspend fun current(): MusicDownloadScope {
        val scope = MusicDownloadScope(accountReader.current().ownerKey, servers.first())
        adoptUnscoped(scope)
        return scope
    }

    private suspend fun adoptUnscoped(scope: MusicDownloadScope) = adoptLock.withLock {
        MusicSourceKind.entries.forEach { kind ->
            val serverKey = scope.servers.keyFor(kind) ?: return@forEach
            if (serverKey in adoptedServerKeys) return@forEach
            dao.adoptUnscoped(kind.token, serverKey, scope.ownerKey)
            adoptedServerKeys += serverKey
        }
    }
}
