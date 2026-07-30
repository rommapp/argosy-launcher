package com.nendo.argosy.data.quaypass

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the cached ticket balance in step with the server. Requests it on
 * every reconnect and applies the server's balance pushes.
 */
@Singleton
class QuayPassWalletCoordinator @Inject constructor(
    private val socialService: ArgosSocialService,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state is ArgosSocialService.ConnectionState.Connected && isLinked()) {
                    socialService.requestQuayPassBalance()
                }
            }
        }
        scope.launch {
            socialService.incomingMessages.collect { message ->
                when (message) {
                    is ArgosSocialService.IncomingMessage.QuayPassBalance ->
                        preferencesRepository.setQuayPassTicketBalance(message.balance)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun isLinked(): Boolean =
        preferencesRepository.userPreferences.first().isSocialLinked
}
