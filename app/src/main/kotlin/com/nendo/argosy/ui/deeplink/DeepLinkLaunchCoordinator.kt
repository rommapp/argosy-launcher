package com.nendo.argosy.ui.deeplink

import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.model.DeepLinkRequest
import com.nendo.argosy.domain.usecase.game.DeepLinkResolution
import com.nendo.argosy.domain.usecase.game.ResolveDeepLinkGameUseCase
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "DeepLinkLaunch"
private const val CONNECT_TIMEOUT_MS = 10_000L

sealed interface DeepLinkLaunch {
    data class Ready(val gameId: Long, val channelName: String?) : DeepLinkLaunch
    data class Failed(val message: String) : DeepLinkLaunch
}

/**
 * Resolves an external launch request, and gates it on RomM connectivity.
 *
 * [resolve] maps the request to a game id. [awaitConnectionIfSyncing] waits up to
 * [CONNECT_TIMEOUT_MS] for a connection when save sync is enabled, so a deep link arriving
 * during a cold start does not launch before the pre-launch pull can run. On timeout it
 * returns anyway and logs a warning; the caller still launches.
 */
class DeepLinkLaunchCoordinator @Inject constructor(
    private val resolveDeepLinkGame: ResolveDeepLinkGameUseCase,
    private val romMRepository: RomMRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    suspend fun resolve(request: DeepLinkRequest): DeepLinkLaunch {
        val resolution = resolveDeepLinkGame(request)

        return when (resolution) {
            is DeepLinkResolution.Resolved -> {
                DeepLinkLaunch.Ready(resolution.gameId, request.channelName)
            }
            is DeepLinkResolution.NotFound -> {
                Logger.warn(TAG, "Deep link unresolved: ${resolution.reason}")
                DeepLinkLaunch.Failed("Game not found in Argosy")
            }
            is DeepLinkResolution.Ambiguous -> {
                Logger.warn(TAG, "Deep link ambiguous: ${resolution.reason}")
                DeepLinkLaunch.Failed("More than one game matches that file name")
            }
        }
    }

    suspend fun awaitConnectionIfSyncing() {
        if (romMRepository.isConnected()) return
        if (!preferencesRepository.userPreferences.first().saveSyncEnabled) return

        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            romMRepository.connectionState.first { it is ConnectionState.Connected }
        }

        if (connected == null) {
            Logger.warn(
                TAG,
                "RomM not connected after ${CONNECT_TIMEOUT_MS}ms, launching without a save pull"
            )
        }
    }
}
