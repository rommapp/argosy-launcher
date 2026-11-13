package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMStreamSaveAndExitRequest
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class StreamClaim(
    val platform: String,
    val containerKey: String,
    val claimedAt: String
)

enum class StreamReleaseOutcome { RELEASED, ALREADY_ENDED, FAILED }

@Singleton
class StreamClaimReleaser internal constructor(
    private val connectionManager: RomMConnectionManager,
    private val io: CoroutineDispatcher
) {
    @Inject
    constructor(connectionManager: RomMConnectionManager) : this(connectionManager, Dispatchers.IO)

    suspend fun release(claim: StreamClaim): StreamReleaseOutcome = withContext(io) {
        val api = connectedApi() ?: return@withContext StreamReleaseOutcome.FAILED
        saveAndExit(api, claim) ?: releaseOnly(api, claim)
    }

    private suspend fun connectedApi(): RomMApi? {
        connectionManager.getApi()?.let { return it }
        try {
            connectionManager.initialize()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "RomM could not be reached to release a stream claim: ${e.message}")
        }
        return connectionManager.getApi()
    }

    private suspend fun saveAndExit(api: RomMApi, claim: StreamClaim): StreamReleaseOutcome? {
        val response = try {
            api.saveAndExitStreamSession(claim.platform, RomMStreamSaveAndExitRequest(), claim.containerKey, claim.claimedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "save and exit failed for ${claim.platform}: ${e.message}")
            return null
        }
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            Logger.warn(TAG, "save and exit for ${claim.platform} answered ${response.code()}")
            return null
        }
        return when {
            body.isNotFound -> StreamReleaseOutcome.ALREADY_ENDED
            body.released -> StreamReleaseOutcome.RELEASED
            else -> null
        }
    }

    private suspend fun releaseOnly(api: RomMApi, claim: StreamClaim): StreamReleaseOutcome {
        val response = try {
            api.releaseStreamSession(claim.platform, claim.containerKey, claim.claimedAt, save = true)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG, "release failed for ${claim.platform}: ${e.message}")
            return StreamReleaseOutcome.FAILED
        }
        val body = response.body()
        return when {
            !response.isSuccessful || body == null -> StreamReleaseOutcome.FAILED
            body.isNotFound -> StreamReleaseOutcome.ALREADY_ENDED
            else -> StreamReleaseOutcome.RELEASED
        }
    }

    private companion object {
        const val TAG = "StreamClaimReleaser"
    }
}
