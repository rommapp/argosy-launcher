package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.local.dao.QuayPassOwnedPartDao
import com.nendo.argosy.data.local.entity.QuayPassOwnedPartEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cached ticket balance and owned-parts set in step with the server.
 * Requests both on every reconnect and applies the server's balance/owned-parts
 * pushes. The server is authoritative for already-synced parts; locally-unsynced
 * parts (pending offline purchases) are preserved.
 */
@Singleton
class QuayPassWalletCoordinator @Inject constructor(
    private val socialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository,
    private val ownedPartDao: QuayPassOwnedPartDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state is ArgosSocialService.ConnectionState.Connected && isLinked()) {
                    socialService.requestQuayPassBalance()
                    socialService.requestQuayPassOwnedParts()
                }
            }
        }
        scope.launch {
            socialService.incomingMessages.collect { message ->
                when (message) {
                    is ArgosSocialService.IncomingMessage.QuayPassBalance ->
                        preferencesRepository.setQuayPassTicketBalance(message.balance)
                    is ArgosSocialService.IncomingMessage.QuayPassOwnedParts ->
                        adoptServerOwnedParts(message.parts)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun isLinked(): Boolean =
        preferencesRepository.userPreferences.first().isSocialLinked

    private suspend fun adoptServerOwnedParts(serverParts: List<String>) {
        val server = serverParts.toSet()
        val local = ownedPartDao.all()
        val now = Instant.now()
        server.forEach { key ->
            ownedPartDao.upsert(QuayPassOwnedPartEntity(partKey = key, acquiredAt = now, synced = true))
        }
        local.filter { it.synced && it.partKey !in server }.forEach { ownedPartDao.delete(it.partKey) }
    }
}
