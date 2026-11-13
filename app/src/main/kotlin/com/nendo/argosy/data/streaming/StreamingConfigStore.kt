package com.nendo.argosy.data.streaming

import com.nendo.argosy.data.local.entity.serverInstanceKey
import com.nendo.argosy.data.remote.romm.ConnectionState
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMStreamingConfig
import com.nendo.argosy.data.remote.romm.RomMStreamingContainer
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The streaming config of the connected server. [serverKey] names the server it was read from,
 * [startRefused] is set by a 403 on a claim and cleared by the next read.
 */
data class StreamingAvailability(
    val serverKey: String? = null,
    val config: RomMStreamingConfig? = null,
    val startRefused: Boolean = false
)

/**
 * The connected server's `GET /api/streaming/config`, read again on every capability refresh of
 * [RomMConnectionManager].
 */
@Singleton
class StreamingConfigStore @Inject constructor(
    private val connectionManager: RomMConnectionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val _availability = MutableStateFlow(StreamingAvailability())
    val availability: StateFlow<StreamingAvailability> = _availability.asStateFlow()

    init {
        if (!com.nendo.argosy.libretro.slot.GameSlots.isSlotProcess) {
            scope.launch { connectionManager.capabilityRefreshes.collect { refresh() } }
            scope.launch {
                connectionManager.connectionState.collect { state ->
                    if (state is ConnectionState.Connected) refresh()
                    if (state is ConnectionState.Disconnected) _availability.value = StreamingAvailability()
                }
            }
        }
    }

    val enabled: Flow<Boolean> = availability.map { isEnabledFor(it) }.distinctUntilChanged()

    fun isEnabled(): Boolean = isEnabledFor(_availability.value)

    fun containerFor(platformSlug: String?): RomMStreamingContainer? {
        val current = _availability.value
        if (current.serverKey != currentServerKey()) return null
        return StreamTriggerGate.containerFor(
            capabilities = connectionManager.getCapabilities(),
            config = current.config,
            startRefused = current.startRefused,
            platformSlug = platformSlug
        )
    }

    fun streamablePlatforms(): Set<String> {
        val current = _availability.value
        if (current.serverKey != currentServerKey()) return emptySet()
        return StreamTriggerGate.streamablePlatforms(
            capabilities = connectionManager.getCapabilities(),
            config = current.config,
            startRefused = current.startRefused
        )
    }

    private fun isEnabledFor(current: StreamingAvailability): Boolean =
        current.serverKey == currentServerKey() && StreamTriggerGate.isEnabled(
            capabilities = connectionManager.getCapabilities(),
            config = current.config,
            startRefused = current.startRefused
        )

    fun markStartRefused() {
        val key = currentServerKey() ?: return
        _availability.update { if (it.serverKey == key) it.copy(startRefused = true) else it }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        refreshLock.withLock {
            val key = currentServerKey()
            val api = connectionManager.getApi()
            if (key == null || api == null || !connectionManager.getCapabilities().supportsStreaming) {
                _availability.value = StreamingAvailability(serverKey = key)
                return@withLock
            }
            val config = try {
                val response = api.getStreamingConfig()
                if (response.isSuccessful) response.body() else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.warn(TAG, "streaming config could not be read: ${e.message}")
                null
            }
            _availability.value = StreamingAvailability(serverKey = key, config = config)
        }
    }

    private fun currentServerKey(): String? =
        connectionManager.getBaseUrl().takeIf { it.isNotBlank() }?.let(::serverInstanceKey)

    private companion object {
        const val TAG = "StreamingConfigStore"
    }
}
