package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.AchievementDao
import com.nendo.argosy.data.local.dao.DownloadQueueDao
import com.nendo.argosy.data.local.dao.PendingSocialSyncDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import com.nendo.argosy.data.local.dao.QuayPassPendingReportDao
import com.nendo.argosy.data.local.dao.RomMAccountDao
import com.nendo.argosy.data.local.dao.SaveCacheDao
import com.nendo.argosy.data.local.dao.SaveSyncDao
import com.nendo.argosy.data.local.dao.StateCacheDao
import com.nendo.argosy.data.local.dao.StateTombstoneDao
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMApiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the set of paired RomM accounts and which one is live.
 *
 * `romm_accounts` is the registry; the RomM credential keys in DataStore are a mirror of the
 * active account so the boot path and existing readers keep working unchanged. This class is
 * the only writer of that mirror, so the two cannot drift.
 */
@Singleton
class RomMAccountRepository @Inject constructor(
    private val rommAccountDao: RomMAccountDao,
    private val achievementDao: AchievementDao,
    private val saveCacheDao: SaveCacheDao,
    private val saveSyncDao: SaveSyncDao,
    private val stateCacheDao: StateCacheDao,
    private val stateTombstoneDao: StateTombstoneDao,
    private val playSessionDao: PlaySessionDao,
    private val pendingSocialSyncDao: PendingSocialSyncDao,
    private val downloadQueueDao: DownloadQueueDao,
    private val quayPassPendingReportDao: QuayPassPendingReportDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val rommApiProvider: RomMApiProvider
) {
    fun observeAccounts(): Flow<List<RomMAccountEntity>> = rommAccountDao.observeAll()

    fun observeActiveAccount(): Flow<RomMAccountEntity?> = rommAccountDao.observeActive()

    suspend fun accounts(): List<RomMAccountEntity> = rommAccountDao.getAll()

    suspend fun activeAccount(): RomMAccountEntity? = rommAccountDao.getActive()

    suspend fun accountCount(): Int = rommAccountDao.count()

    /**
     * Records a successful pairing and makes it the live account. Returns the row id.
     *
     * An existing row for the same RomM user is updated in place rather than duplicated, so a
     * re-pair after a revoked token keeps everything already attributed to that account.
     */
    suspend fun onSignedIn(
        rommUserId: Long,
        username: String,
        baseUrl: String,
        token: String,
        deviceId: String?,
        deviceClientVersion: String?
    ): Long {
        val now = Instant.now()
        val existing = rommAccountDao.getByRommUserId(rommUserId)
        val id = rommAccountDao.upsert(
            RomMAccountEntity(
                id = existing?.id ?: 0,
                rommUserId = rommUserId,
                username = username,
                baseUrl = baseUrl,
                token = token,
                deviceId = deviceId ?: existing?.deviceId,
                deviceClientVersion = deviceClientVersion ?: existing?.deviceClientVersion,
                avatarPath = existing?.avatarPath,
                isActive = true,
                lastLoginAt = now,
                createdAt = existing?.createdAt ?: now
            )
        ).let { if (it > 0) it else existing?.id ?: 0 }
        rommAccountDao.setActive(id)
        rommApiProvider.invalidate(id)
        adoptUnownedAchievements(rommUserId)
        adoptUnownedRows(rommUserId)
        return id
    }

    /**
     * Hands achievements recorded before accounts existed to the first account that appears.
     *
     * They carry the no-owner sentinel, and achievement reads match the owner exactly rather than
     * tolerating it the way the save tables do, so without this every unlock on an upgrading
     * install becomes invisible the moment an identity is adopted. Only ever runs while no row is
     * owned, so a genuine second account cannot claim the first account's history.
     */
    private suspend fun adoptUnownedAchievements(ownerUserId: Long) {
        if (achievementDao.countOwned() > 0) return
        if (achievementDao.countUnowned() == 0) return
        achievementDao.adoptUnowned(ownerUserId)
    }

    /**
     * Claims rows the account migration could not reach.
     *
     * That migration backfilled every owner column from `romm_accounts`, but the registry is
     * seeded by [adoptLegacyCredentialsIfNeeded] after the database opens, so the subselect saw an
     * empty table and wrote NULL everywhere. The rows are not ambiguous - they predate multiple
     * accounts, so they belong to the only account there has ever been - but per-account reads
     * filter them out, which strands cached saves, queued uploads and play sessions alike.
     *
     * Runs only while a single account exists; past that a NULL owner cannot be attributed safely
     * and the rows are left alone.
     */
    private suspend fun adoptUnownedRows(ownerUserId: Long) {
        if (rommAccountDao.count() > 1) return
        saveCacheDao.adoptUnowned(ownerUserId)
        saveSyncDao.adoptUnowned(ownerUserId)
        stateCacheDao.adoptUnowned(ownerUserId)
        stateTombstoneDao.adoptUnowned(ownerUserId)
        playSessionDao.adoptUnowned(ownerUserId)
        pendingSocialSyncDao.adoptUnowned(ownerUserId)
        downloadQueueDao.adoptUnowned(ownerUserId)
        quayPassPendingReportDao.adoptUnowned(ownerUserId)
    }

    /**
     * Records a pairing WITHOUT making it live and without touching the credential mirror.
     *
     * Pairing a second account must not hand the device to it: the outgoing account's saves are
     * still on disk unarchived, so activating here would let the new account launch straight into
     * them. Activation is the switch coordinator's job, which tears down first.
     */
    suspend fun registerAdditional(
        rommUserId: Long,
        username: String,
        baseUrl: String,
        token: String,
        deviceId: String?,
        deviceClientVersion: String?
    ): Long {
        val now = Instant.now()
        val existing = rommAccountDao.getByRommUserId(rommUserId)
        val id = rommAccountDao.upsert(
            RomMAccountEntity(
                id = existing?.id ?: 0,
                rommUserId = rommUserId,
                username = username,
                baseUrl = baseUrl,
                token = token,
                deviceId = deviceId ?: existing?.deviceId,
                deviceClientVersion = deviceClientVersion ?: existing?.deviceClientVersion,
                avatarPath = existing?.avatarPath,
                isActive = existing?.isActive ?: false,
                lastLoginAt = now,
                createdAt = existing?.createdAt ?: now
            )
        ).let { if (it > 0) it else existing?.id ?: 0 }
        rommApiProvider.invalidate(id)
        return id
    }

    suspend fun recordDeviceRegistration(deviceId: String, clientVersion: String) {
        val active = rommAccountDao.getActive() ?: return
        rommAccountDao.updateDevice(active.id, deviceId, clientVersion)
        rommApiProvider.invalidate(active.id)
    }

    /**
     * Makes [id] the live account and mirrors its credentials into DataStore. Callers still
     * have to rebind the connection; this only moves the stored identity.
     */
    suspend fun activate(id: Long): RomMAccountEntity? {
        val account = rommAccountDao.getById(id) ?: return null
        rommAccountDao.setActive(id)
        userPreferencesRepository.setRomMCredentials(
            baseUrl = account.baseUrl,
            token = account.token,
            username = account.username,
            userId = account.rommUserId
        )
        account.deviceId?.let { deviceId ->
            userPreferencesRepository.setRommDeviceId(
                deviceId,
                account.deviceClientVersion.orEmpty()
            )
        } ?: userPreferencesRepository.clearRommDeviceId()
        return account
    }

    suspend fun forget(id: Long) {
        val account = rommAccountDao.getById(id) ?: return
        rommAccountDao.deleteById(id)
        rommApiProvider.invalidate(id)
        if (account.isActive) {
            userPreferencesRepository.clearRomMCredentials()
        }
    }

    /**
     * Seeds the registry from the pre-multi-account DataStore credentials so an upgrading
     * install keeps its saves, queued work and library attributed to the account that owns
     * them. Runs once: a non-empty registry is left alone.
     */
    suspend fun adoptLegacyCredentialsIfNeeded() {
        rommAccountDao.getActive()?.let {
            adoptUnownedAchievements(it.rommUserId)
            adoptUnownedRows(it.rommUserId)
        }
        if (rommAccountDao.count() > 0) return
        val prefs = userPreferencesRepository.preferences.first()
        val baseUrl = prefs.rommBaseUrl?.takeIf { it.isNotBlank() } ?: return
        val token = prefs.rommToken?.takeIf { it.isNotBlank() } ?: return
        val userId = prefs.rommUserId ?: return
        onSignedIn(
            rommUserId = userId,
            username = prefs.rommUsername.orEmpty(),
            baseUrl = baseUrl,
            token = token,
            deviceId = prefs.rommDeviceId,
            deviceClientVersion = prefs.rommDeviceClientVersion
        )
    }
}
