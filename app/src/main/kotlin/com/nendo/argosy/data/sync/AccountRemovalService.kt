package com.nendo.argosy.data.sync

import android.content.Context
import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.entity.SaveOwnershipEntity
import com.nendo.argosy.data.preferences.AccountPreferenceStoreRegistry
import com.nendo.argosy.data.preferences.AccountSwitchMarkerStore
import com.nendo.argosy.data.quaypass.QuayPassKeystore
import com.nendo.argosy.data.repository.RomMAccountRepository
import com.nendo.argosy.util.AppPaths
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AccountRemoval"

/**
 * Work the account still has outstanding, which removal would otherwise destroy silently.
 */
data class AccountPendingWork(
    val queuedSyncOperations: Int,
    val savesAwaitingUpload: Int,
    val queuedSocialEvents: Int,
    val queuedQuayPassReports: Int
) {
    val isEmpty: Boolean
        get() = queuedSyncOperations == 0 &&
            savesAwaitingUpload == 0 &&
            queuedSocialEvents == 0 &&
            queuedQuayPassReports == 0
}

/**
 * What to do about [AccountPendingWork]. There is no default: a caller has to say which, so
 * removal cannot throw away unsent progress by omission.
 */
enum class UnflushedQueuePolicy { REFUSE, DISCARD }

sealed interface AccountRemovalResult {
    data object UnknownAccount : AccountRemovalResult
    data object SwitchInProgress : AccountRemovalResult
    data class Refused(val pending: AccountPendingWork) : AccountRemovalResult
    data class Removed(
        val reclaimedArtifacts: Int,
        val abortedArtifacts: Int,
        val discarded: AccountPendingWork
    ) : AccountRemovalResult
}

/**
 * Deletes one account and everything attributed to it.
 *
 * `purgeDatabase` cannot express this: it deletes by [com.nendo.argosy.data.model.GameSource],
 * which is a property of the rom, not of who owns the rows hanging off it. Library rows are
 * deliberately left alone -- `games` is one row per rom shared by every account, and deleting
 * them would take the remaining accounts' saves with them through the CASCADE.
 *
 * On-disk saves the removed account still holds are torn down first, through the same
 * archive-verify-rehash-remove machinery a switch uses. The archive is transient here, deleted
 * with the rest of the account's rows, but it is what makes the removal recoverable: an
 * interrupted run leaves the bytes in a verified cache entry rather than nowhere.
 *
 * Local only. RomM exposes no device-revoke endpoint, so the device row registered against the
 * removed account and its non-expiring token both survive on the server and have to be revoked
 * there by hand.
 */
@Singleton
class AccountRemovalService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ALauncherDatabase,
    private val markerStore: AccountSwitchMarkerStore,
    private val artifactService: AccountSwitchArtifactService,
    private val rommAccountRepository: RomMAccountRepository,
    private val preferenceStores: AccountPreferenceStoreRegistry,
    private val quayPassKeystore: QuayPassKeystore
) {

    suspend fun pendingWork(accountId: Long): AccountPendingWork? = withContext(Dispatchers.IO) {
        val account = rommAccountRepository.accounts().firstOrNull { it.id == accountId }
            ?: return@withContext null
        pendingWorkFor(account.rommUserId)
    }

    /**
     * Removes [accountId]. [unflushed] is the answer to what should happen to anything the
     * account queued and never sent; [UnflushedQueuePolicy.REFUSE] returns the tally instead of
     * deleting anything.
     */
    suspend fun remove(
        accountId: Long,
        unflushed: UnflushedQueuePolicy
    ): AccountRemovalResult = withContext(Dispatchers.IO) {
        if (markerStore.isSwitching()) return@withContext AccountRemovalResult.SwitchInProgress

        val account = rommAccountRepository.accounts().firstOrNull { it.id == accountId }
            ?: return@withContext AccountRemovalResult.UnknownAccount
        val ownerUserId = account.rommUserId

        val pending = pendingWorkFor(ownerUserId)
        if (unflushed == UnflushedQueuePolicy.REFUSE && !pending.isEmpty) {
            Logger.info(TAG, "Removal of user $ownerUserId refused: $pending")
            return@withContext AccountRemovalResult.Refused(pending)
        }

        var reclaimed = 0
        var aborted = 0
        for (row in outstandingArtifacts(ownerUserId)) {
            val result = if (row.transitionState == SaveOwnershipEntity.STATE_RECLAIMED) {
                artifactService.resumeRemoval(row, ownerUserId)
            } else {
                artifactService.tearDown(row, ownerUserId, ownerUserId)
            }
            when (result) {
                AccountSwitchArtifactService.TeardownResult.RECLAIMED -> reclaimed++
                AccountSwitchArtifactService.TeardownResult.ABORTED -> aborted++
                AccountSwitchArtifactService.TeardownResult.NOTHING_TO_DO -> Unit
            }
        }

        deleteRows(ownerUserId)
        deleteCacheDirectories(ownerUserId)
        preferenceStores.clearFor(ownerUserId)
        quayPassKeystore.clear(QuayPassKeystore.aliasFor(ownerUserId))
        rommAccountRepository.forget(accountId)

        Logger.info(
            TAG,
            "Removed user $ownerUserId | reclaimed=$reclaimed, aborted=$aborted, discarded=$pending"
        )
        AccountRemovalResult.Removed(reclaimed, aborted, pending)
    }

    private suspend fun pendingWorkFor(ownerUserId: Long) = AccountPendingWork(
        queuedSyncOperations = database.pendingSyncQueueDao().countUnflushedForOwner(ownerUserId),
        savesAwaitingUpload = database.saveCacheDao().countNeedingRemoteSyncForOwner(ownerUserId),
        queuedSocialEvents = database.pendingSocialSyncDao().countPendingForOwner(ownerUserId),
        queuedQuayPassReports = database.quayPassPendingReportDao().countForOwner(ownerUserId)
    )

    private suspend fun outstandingArtifacts(ownerUserId: Long): List<SaveOwnershipEntity> =
        database.saveOwnershipDao().getByOwner(ownerUserId)
            .filter { it.transitionState != SaveOwnershipEntity.STATE_CLEARED }

    private suspend fun deleteRows(ownerUserId: Long) {
        database.withTransaction {
            database.gameUserOverlayDao().deleteForOwner(ownerUserId)
            database.userRomsHiddenDao().deleteForOwner(ownerUserId)
            database.collectionMembershipDao().deleteForOwner(ownerUserId)
            database.saveCacheDao().deleteByOwner(ownerUserId)
            database.pendingConflictDao().deleteByOwner(ownerUserId)
            database.pendingSyncQueueDao().deleteByOwner(ownerUserId)
            database.pendingSocialSyncDao().deleteByOwner(ownerUserId)
            database.playSessionDao().deleteByOwner(ownerUserId)
            database.achievementDao().deleteByOwner(ownerUserId)
            database.saveOwnershipDao().deleteByOwner(ownerUserId)
            database.quayPassEncounterDao().deleteByOwner(ownerUserId)
            database.quayPassPendingReportDao().deleteByOwner(ownerUserId)
        }
    }

    private fun deleteCacheDirectories(ownerUserId: Long) {
        listOfNotNull(
            AppPaths.ownerCacheDir(AppPaths.saveCacheDir(context.filesDir), ownerUserId),
            AppPaths.ownerCacheDir(AppPaths.stateCacheDir(context.filesDir), ownerUserId)
        ).forEach { dir ->
            runCatching { if (dir.exists()) dir.deleteRecursively() }
                .onFailure { Logger.error(TAG, "Could not delete ${dir.absolutePath}", it) }
        }
    }
}
