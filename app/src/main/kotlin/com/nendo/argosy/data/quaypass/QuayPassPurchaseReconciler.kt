package com.nendo.argosy.data.quaypass

import android.util.Base64
import com.nendo.argosy.data.local.dao.QuayPassOwnedPartDao
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatarCodec
import com.nendo.argosy.data.quaypass.ble.QuayPassPartPricing
import com.nendo.argosy.data.quaypass.ble.partIndexFor
import com.nendo.argosy.data.social.ArgosSocialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replays offline part purchases when the connection returns. Equipped parts are
 * replayed first so they claim tickets ahead of unequipped ones; a purchase the
 * server rejects for insufficient tickets drops the local ownership (the part
 * then shows as locked again in the customizer). Dormant until pricing is on.
 */
@Singleton
class QuayPassPurchaseReconciler @Inject constructor(
    private val socialService: ArgosSocialService,
    private val ownedPartDao: QuayPassOwnedPartDao,
    private val preferencesRepository: UserPreferencesRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            socialService.connectionState.collect { state ->
                if (state is ArgosSocialService.ConnectionState.Connected) replayUnsynced()
            }
        }
        scope.launch {
            socialService.incomingMessages.collect { message ->
                if (message is ArgosSocialService.IncomingMessage.QuayPassPartPurchased) {
                    handleResult(message)
                }
            }
        }
    }

    private suspend fun replayUnsynced() {
        val unsynced = ownedPartDao.unsynced()
        if (unsynced.isEmpty()) return
        val equipped = equippedKeys()
        QuayPassPurchaseReconciliation.orderForReplay(unsynced, equipped).forEach {
            socialService.purchaseQuayPassPart(it.partKey)
        }
    }

    private suspend fun handleResult(message: ArgosSocialService.IncomingMessage.QuayPassPartPurchased) {
        preferencesRepository.setQuayPassTicketBalance(message.balance)
        if (message.success) {
            ownedPartDao.markSynced(message.partKey)
        } else {
            ownedPartDao.delete(message.partKey)
        }
    }

    private suspend fun equippedKeys(): Set<String> {
        val raw = preferencesRepository.userPreferences.first().quayPassAvatarBytes ?: return emptySet()
        val avatar = runCatching {
            QuayPassAvatarCodec.decode(Base64.decode(raw, Base64.NO_WRAP))
        }.getOrNull() ?: return emptySet()
        return AvatarCategory.entries
            .map { QuayPassPartPricing.partKey(it, avatar.partIndexFor(it)) }
            .toSet()
    }
}
