package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.RomMAccountDao
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
        return id
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
