package com.nendo.argosy.data.remote.romm

import com.nendo.argosy.data.local.dao.RomMAccountDao
import com.nendo.argosy.data.local.entity.RomMAccountEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class AccountApi(
    val accountId: Long,
    val rommUserId: Long,
    val api: RomMApi,
    val deviceId: String?,
    val baseUrl: String
)

/**
 * Hands out a RomM client for a specific account rather than for whoever is signed in.
 *
 * Queued work is bound to the account that created it, so a save archived by one user must
 * upload under that user's token and against that user's device row: save negotiation is
 * evaluated per device, and using the live account's device id would evaluate against a
 * device the owner does not have.
 *
 * Clients are cached per account and rebuilt when the stored credentials change.
 */
@Singleton
class RomMApiProvider @Inject constructor(
    private val rommAccountDao: RomMAccountDao,
    private val apiFactory: RomMApiFactory
) {
    private data class Entry(val baseUrl: String, val token: String, val value: AccountApi)

    private val cache = ConcurrentHashMap<Long, Entry>()

    suspend fun forAccount(accountId: Long): AccountApi? =
        rommAccountDao.getById(accountId)?.let { forAccount(it) }

    suspend fun forRommUser(rommUserId: Long): AccountApi? =
        rommAccountDao.getByRommUserId(rommUserId)?.let { forAccount(it) }

    fun forAccount(account: RomMAccountEntity): AccountApi {
        val cached = cache[account.id]
        if (cached != null && cached.token == account.token && cached.baseUrl == account.baseUrl) {
            return cached.value.copy(deviceId = account.deviceId)
        }
        val built = AccountApi(
            accountId = account.id,
            rommUserId = account.rommUserId,
            api = apiFactory.create(account.baseUrl, account.token),
            deviceId = account.deviceId,
            baseUrl = account.baseUrl
        )
        cache[account.id] = Entry(account.baseUrl, account.token, built)
        return built
    }

    fun invalidate(accountId: Long) {
        cache.remove(accountId)
    }

    fun invalidateAll() {
        cache.clear()
    }
}
