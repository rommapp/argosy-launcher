package com.nendo.argosy.domain.usecase.save

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.storage.AndroidDataAccessor
import com.nendo.argosy.data.storage.ManagedStorageAccessor
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CheckSaveSyncPermissionUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val androidDataAccessor: AndroidDataAccessor,
    private val managedStorageAccessor: ManagedStorageAccessor
) {
    sealed class Result {
        data object Granted : Result()
        data object NotNeeded : Result()
        data object MissingStoragePermission : Result()
        data object MissingSafGrant : Result()

        @Deprecated("Use MissingStoragePermission or MissingSafGrant instead")
        val isMissingPermission: Boolean
            get() = this is MissingStoragePermission || this is MissingSafGrant
    }

    suspend operator fun invoke(): Result {
        val prefs = preferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return Result.NotNeeded

        val hasStorage = hasFileAccessPermission()
        if (!hasStorage) {
            return Result.MissingStoragePermission
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check if Unicode path trick works (preferred method - no prompts)
            if (androidDataAccessor.isUnicodeTrickSupported()) {
                return Result.Granted
            }

            // Fallback: check SAF grant for devices where Unicode trick doesn't work
            managedStorageAccessor.setTreeUri(prefs.androidDataSafUri)
            val hasValidGrant = managedStorageAccessor.hasValidSafGrant()
            if (!hasValidGrant) {
                return Result.MissingSafGrant
            }
        }

        return Result.Granted
    }

    private fun hasFileAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
