package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.romm.RomMActivityHeartbeatPayload
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMActivityReporter"

/**
 * Tells RomM which game this device is playing, for as long as it is playing it.
 *
 * The server holds the state for 90 seconds and expires it on its own, so a device that crashes or
 * loses its network stops showing as playing without anything having to clean up after it. Stopping
 * deliberately clears it at once rather than waiting out the window.
 *
 * A game RomM does not own has nothing to report; local, Steam and Android titles are skipped
 * rather than reported against a missing id.
 */
@Singleton
class RomMActivityReporter @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val gameDao: GameDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val canReport: Boolean
        get() = connectionManager.isConnected()

    /**
     * Answers false when there was nothing to say, so a caller can stop asking. A refusal from the
     * server is answered the same way: an unregistered device will not become registered by being
     * asked again on the next tick.
     */
    suspend fun report(gameId: Long): Boolean {
        if (!canReport) return false
        val api = connectionManager.getApi() ?: return false
        val deviceId = connectionManager.getDeviceId() ?: return false
        val romId = gameDao.getById(gameId)?.rommId ?: return false

        return try {
            val response = api.sendActivityHeartbeat(
                RomMActivityHeartbeatPayload(romId = romId, deviceId = deviceId)
            )
            if (!response.isSuccessful) {
                Logger.debug(TAG, "heartbeat rejected | rom=$romId code=${response.code()}")
            }
            response.isSuccessful
        } catch (e: Exception) {
            Logger.debug(TAG, "heartbeat failed | rom=$romId ${e.message}")
            false
        }
    }

    /**
     * Clears the live state without waiting for it to be cleared. Run on this reporter's own scope
     * rather than the caller's: a session ending takes its scope down with it, and the point of the
     * call is to land after that. Missing it costs nothing beyond the server's own expiry.
     */
    fun clearAsync() {
        scope.launch {
            if (!canReport) return@launch
            val api = connectionManager.getApi() ?: return@launch
            val deviceId = connectionManager.getDeviceId() ?: return@launch
            try {
                api.clearActivity(deviceId)
            } catch (e: Exception) {
                Logger.debug(TAG, "clear failed | ${e.message}")
            }
        }
    }
}
