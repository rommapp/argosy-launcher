package com.nendo.argosy.data.permissions

import com.nendo.argosy.domain.model.PermissionSheetKind
import com.nendo.argosy.domain.model.PermissionSheetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The check every write that turns save sync on goes through. Save sync turns on only while every
 * sync-required permission is granted; with any missing, the person is shown the sheet that grants
 * them.
 */
@Singleton
class SaveSyncGate @Inject constructor(
    private val registry: PermissionRegistry,
    private val sheets: PermissionSheetController
) {
    suspend fun clearToEnable(): Boolean {
        val missing = withContext(Dispatchers.IO) { registry.snapshot() }.missingForSaveSync
        if (missing.isEmpty()) return true
        if (!sheets.show(PermissionSheetRequest(PermissionSheetKind.SAVE_SYNC, missing))) return false
        return withContext(Dispatchers.IO) { registry.snapshot() }.missingForSaveSync.isEmpty()
    }
}
