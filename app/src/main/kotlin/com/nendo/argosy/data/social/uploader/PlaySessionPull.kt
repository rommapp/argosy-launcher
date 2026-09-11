package com.nendo.argosy.data.social.uploader

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApi
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import com.nendo.argosy.data.remote.romm.RomMDevice
import com.nendo.argosy.data.remote.romm.RomMPlaySession
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlaySessionPull"
private const val PAGE_SIZE = 200

/**
 * Copies down the play sessions RomM holds for the account's other devices, so hours played
 * elsewhere count here. This device is skipped: its sessions originate locally, and a row the
 * ingest acknowledged as a duplicate carries no server id to match on. Runs once after the
 * first-connect backfill and on demand from the Play Time hub.
 */
@Singleton
class PlaySessionPull @Inject constructor(
    private val playSessionDao: PlaySessionDao,
    private val gameDao: GameDao,
    private val syncPreferencesRepository: SyncPreferencesRepository,
    private val connectionManager: RomMConnectionManager
) {
    data class Summary(
        val added: Int,
        val devices: Int,
        val stoppedBy: String? = null
    )

    private val runMutex = Mutex()

    val canPull: Boolean
        get() = connectionManager.getCapabilities().supportsPlaySessionIngest &&
            connectionManager.isConnected()

    suspend fun run(): Summary = runMutex.withLock {
        if (!canPull) return Summary(0, 0, "Capability or connection unavailable")
        val api = connectionManager.getApi() ?: return Summary(0, 0, "No api")
        val ownerUserId = syncPreferencesRepository.getRommUserId() ?: return Summary(0, 0, "No account")
        val thisDeviceId = connectionManager.getDeviceId()

        val devices = try {
            val response = api.getDevices()
            if (!response.isSuccessful) return Summary(0, 0, "Devices returned ${response.code()}")
            response.body().orEmpty()
        } catch (e: Exception) {
            Logger.error(TAG, "run: devices failed", e)
            return Summary(0, 0, e.message ?: "Network failure")
        }

        val romCache = HashMap<Long, PulledGameRef>()
        var added = 0
        var devicesDone = 0
        for (device in devices) {
            if (device.id == thisDeviceId) continue
            val since = playSessionDao.getLatestRommStartForDevice(device.id, ownerUserId)
            var offset = 0
            while (true) {
                val page = try {
                    val response = api.getPlaySessions(
                        deviceId = device.id,
                        startAfter = since?.toString(),
                        limit = PAGE_SIZE,
                        offset = offset
                    )
                    if (!response.isSuccessful) {
                        return Summary(added, devicesDone, "Play sessions returned ${response.code()}")
                    }
                    response.body().orEmpty()
                } catch (e: Exception) {
                    Logger.error(TAG, "run: page failed | device=${device.id} offset=$offset", e)
                    return Summary(added, devicesDone, e.message ?: "Network failure")
                }
                if (page.isEmpty()) break
                added += insertMissing(page, device, ownerUserId) { romId ->
                    romCache.getOrPut(romId) { resolveGame(api, romId) }
                }
                if (page.size < PAGE_SIZE) break
                offset += page.size
            }
            devicesDone++
        }
        syncPreferencesRepository.setRommPlaySessionLastPull(Instant.now())
        Logger.info(TAG, "run: added=$added devices=$devicesDone")
        Summary(added, devicesDone)
    }

    /**
     * Inserts every session in [page] whose server id is not already held, filed under the
     * game [resolveGame] answers for its rom. Returns how many rows were written.
     */
    internal suspend fun insertMissing(
        page: List<RomMPlaySession>,
        device: RomMDevice,
        ownerUserId: Long,
        resolveGame: suspend (Long) -> PulledGameRef
    ): Int {
        val held = playSessionDao.getHeldRommSessionIds(page.map { it.id }).toSet()
        var added = 0
        var alreadyHeld = 0
        var noRom = 0
        var unmappable = 0
        for (remote in page) {
            if (remote.id in held) {
                alreadyHeld++
                continue
            }
            val romId = remote.romId
            if (romId == null) {
                noRom++
                continue
            }
            val entity = PlaySessionRemoteMapper.toEntity(remote, device, ownerUserId, resolveGame(romId))
            if (entity == null) {
                unmappable++
                continue
            }
            playSessionDao.insert(entity)
            added++
        }
        if (added < page.size) {
            Logger.info(
                TAG,
                "insertMissing: device=${device.id} page=${page.size} added=$added held=$alreadyHeld " +
                    "noRom=$noRom unmappable=$unmappable sample=${page.firstOrNull()?.startTime}"
            )
        }
        return added
    }

    private suspend fun resolveGame(api: RomMApi, romId: Long): PulledGameRef {
        gameDao.getByRommId(romId)?.let { game ->
            return PulledGameRef(
                gameId = game.id,
                igdbId = game.igdbId,
                title = game.title,
                platformSlug = game.platformSlug
            )
        }
        val rom = try {
            api.getRom(romId).takeIf { it.isSuccessful }?.body()
        } catch (e: Exception) {
            Logger.debug(TAG, "resolveGame: rom $romId lookup failed | ${e.message}")
            null
        } ?: return PulledGameRef.unresolved(romId)
        return PulledGameRef(
            gameId = -romId,
            igdbId = rom.igdbId,
            title = rom.name,
            platformSlug = rom.platformSlug
        )
    }
}
