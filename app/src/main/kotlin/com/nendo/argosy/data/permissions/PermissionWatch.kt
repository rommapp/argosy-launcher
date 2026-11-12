package com.nendo.argosy.data.permissions

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SyncPreferencesRepository
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionSheetKind
import com.nendo.argosy.domain.model.PermissionSheetRequest
import com.nendo.argosy.domain.permissions.PermissionChanges
import com.nendo.argosy.domain.permissions.PermissionReview
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compares live permissions with the recorded state when Argosy comes to the foreground and before
 * a game launches. A revoked sync-required permission turns save sync off; other revocations post
 * one notice on return and the change sheet before the next launch.
 */
@Singleton
class PermissionWatch @Inject constructor(
    private val registry: PermissionRegistry,
    private val appPreferences: AppPreferencesRepository,
    private val syncPreferences: SyncPreferencesRepository,
    private val notificationManager: NotificationManager,
    private val sheets: PermissionSheetController
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, TAG)
    private val reviewLock = Mutex()
    private val expected = MutableStateFlow<Set<ArgosyPermission>>(emptySet())

    private val foregroundObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            scope.launch { guarded("foreground review") { reviewOnForeground() } }
        }
    }

    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    /**
     * Takes [permission]'s state at the next review as acknowledged, for a change the person makes
     * from Argosy's own permissions page.
     */
    fun expectChange(permission: ArgosyPermission) {
        expected.update { it + permission }
    }

    suspend fun clearForLaunch(): Boolean {
        val review = guarded("launch review") {
            reviewLock.withLock { review().also { appPreferences.setPermissionRecord(it.record) } }
        } ?: return true
        if (review.revoked.isEmpty() || !sheets.hasHost) return true
        val goesOn = sheets.show(PermissionSheetRequest(PermissionSheetKind.BEFORE_LAUNCH, review.revoked))
        if (goesOn) {
            guarded("launch acknowledgement") {
                reviewLock.withLock {
                    val current = withContext(Dispatchers.IO) { registry.snapshot() }
                    appPreferences.setPermissionRecord(PermissionChanges.acknowledge(current))
                }
            }
        }
        return goesOn
    }

    private suspend fun reviewOnForeground() = reviewLock.withLock {
        val review = review()
        postRevocationNotice(review)
        appPreferences.setPermissionRecord(review.noticedRecord)
    }

    private suspend fun review(): PermissionReview {
        val current = withContext(Dispatchers.IO) { registry.snapshot() }
        val saveSyncEnabled = syncPreferences.preferences.first().saveSyncEnabled
        val review = PermissionChanges.review(
            record = appPreferences.permissionRecord(),
            current = current,
            saveSyncEnabled = saveSyncEnabled,
            expected = expected.getAndUpdate { emptySet() }
        )
        if (review.turnSaveSyncOff) {
            syncPreferences.setSaveSyncEnabled(false)
            review.revoked.firstOrNull { it.syncRequired }?.let(::postSaveSyncOffNotice)
        }
        return review
    }

    private fun postSaveSyncOffNotice(permission: ArgosyPermission) {
        val body = when (permission) {
            ArgosyPermission.ALL_FILES_ACCESS -> R.string.notif_permissions_sync_off_body_all_files
            ArgosyPermission.USAGE_ACCESS -> R.string.notif_permissions_sync_off_body_usage
            else -> R.string.notif_permissions_sync_off_body_notifications
        }
        notificationManager.show(
            title = NotificationText.Res(R.string.notif_permissions_sync_off_title),
            subtitle = NotificationText.Res(body),
            type = NotificationType.WARNING,
            duration = NotificationDuration.LONG,
            key = KEY_SAVE_SYNC_OFF,
            immediate = true
        )
    }

    private fun postRevocationNotice(review: PermissionReview) {
        val explained = if (review.turnSaveSyncOff) review.revoked.filter { it.syncRequired }.toSet() else emptySet()
        val first = review.unnoticed.firstOrNull { it !in explained && it != ArgosyPermission.ALL_FILES_ACCESS }
            ?: return
        notificationManager.show(
            title = NotificationText.Res(R.string.notif_permissions_revoked_title),
            subtitle = NotificationText.Res(revocationBody(first)),
            type = NotificationType.WARNING,
            duration = NotificationDuration.LONG,
            key = KEY_REVOKED,
            immediate = true
        )
    }

    private fun revocationBody(permission: ArgosyPermission): Int = when (permission) {
        ArgosyPermission.USAGE_ACCESS -> R.string.notif_permissions_revoked_body_usage
        ArgosyPermission.NOTIFICATIONS -> R.string.notif_permissions_revoked_body_notifications
        ArgosyPermission.DISPLAY_OVERLAY -> R.string.notif_permissions_revoked_body_overlay
        ArgosyPermission.ACCESSIBILITY -> R.string.notif_permissions_revoked_body_accessibility
        else -> R.string.notif_permissions_revoked_body_battery
    }

    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.warn(TAG, "Permission $what failed", e)
        null
    }

    private companion object {
        const val TAG = "PermissionWatch"
        const val KEY_SAVE_SYNC_OFF = "permissions_save_sync_off"
        const val KEY_REVOKED = "permissions_revoked"
    }
}
