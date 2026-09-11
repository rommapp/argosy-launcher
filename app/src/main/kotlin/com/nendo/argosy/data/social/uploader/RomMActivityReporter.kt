package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.remote.romm.RomMActivityHeartbeatPayload
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDetailResponse
import com.nendo.argosy.util.Logger
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RomMActivityReporter"
private const val DEVICE_NOT_FOUND_PREFIX = "Device "
private const val DEVICE_NOT_FOUND_SUFFIX = " not found for this user"

/**
 * Tells RomM which game this device is playing, for as long as it is playing it. The server
 * expires the state itself after 90 seconds; a deliberate stop clears it at once instead.
 */
@Singleton
class RomMActivityReporter @Inject constructor(
    private val connectionManager: RomMConnectionManager,
    private val gameDao: GameDao
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val detailAdapter by lazy { Moshi.Builder().build().adapter(RomMDetailResponse::class.java) }

    private val canReport: Boolean
        get() = connectionManager.isConnected()

    /**
     * Heartbeats every [HEARTBEAT_INTERVAL_MS] until cancelled. A failed tick is retried every
     * [RETRY_INTERVAL_MS] up to [MAX_RETRIES] times before the loop gives up for the session; a
     * success resets that budget. Returns at once for a game RomM does not own.
     */
    suspend fun runHeartbeatLoop(gameId: Long) {
        val romId = gameDao.getById(gameId)?.rommId?.takeIf { it > 0 } ?: return
        var retriesLeft = MAX_RETRIES
        var deviceReregistered = false
        while (true) {
            val outcome = tick(romId)
            if (outcome is Outcome.Sent) {
                retriesLeft = MAX_RETRIES
                deviceReregistered = false
                delay(HEARTBEAT_INTERVAL_MS)
                continue
            }
            if (retriesLeft == 0) {
                Logger.info(TAG, "heartbeat stopped for this session | game=$gameId rom=$romId last=${outcome.describe()}")
                return
            }
            retriesLeft--
            if (outcome is Outcome.DeviceUnregistered && !deviceReregistered) {
                deviceReregistered = true
                connectionManager.reregisterDevice()
            }
            delay(RETRY_INTERVAL_MS)
        }
    }

    private suspend fun tick(romId: Long): Outcome {
        if (!canReport) return Outcome.NotConnected
        val api = connectionManager.getApi() ?: return Outcome.NotConnected
        val deviceId = connectionManager.getDeviceId() ?: return Outcome.NoDeviceId

        return try {
            val response = api.sendActivityHeartbeat(
                RomMActivityHeartbeatPayload(romId = romId, deviceId = deviceId)
            )
            if (response.isSuccessful) return Outcome.Sent
            val detail = parseDetail(response.errorBody()?.string())
            Logger.debug(TAG, "heartbeat rejected | rom=$romId code=${response.code()} detail=$detail")
            if (response.code() == 404 && isDeviceNotFound(detail)) {
                Outcome.DeviceUnregistered(response.code())
            } else {
                Outcome.Rejected(response.code())
            }
        } catch (e: Exception) {
            Logger.debug(TAG, "heartbeat failed | rom=$romId ${e.message}")
            Outcome.Failed(e.javaClass.simpleName)
        }
    }

    private fun parseDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try { detailAdapter.fromJson(body)?.detail } catch (_: Exception) { null }
    }

    private fun isDeviceNotFound(detail: String?): Boolean =
        detail != null && detail.startsWith(DEVICE_NOT_FOUND_PREFIX) && detail.endsWith(DEVICE_NOT_FOUND_SUFFIX)

    /**
     * Clears the live state on this reporter's own scope, so it lands after the session scope
     * that called it has already been torn down.
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

    private sealed class Outcome {
        object Sent : Outcome()
        object NotConnected : Outcome()
        object NoDeviceId : Outcome()
        data class Rejected(val code: Int) : Outcome()
        data class DeviceUnregistered(val code: Int) : Outcome()
        data class Failed(val exceptionClass: String) : Outcome()

        fun describe(): String = when (this) {
            Sent -> "sent"
            NotConnected -> "not connected"
            NoDeviceId -> "no device id"
            is Rejected -> "HTTP $code"
            is DeviceUnregistered -> "HTTP $code device not registered"
            is Failed -> exceptionClass
        }
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 30_000L
        const val RETRY_INTERVAL_MS = 10_000L
        const val MAX_RETRIES = 6
    }
}
