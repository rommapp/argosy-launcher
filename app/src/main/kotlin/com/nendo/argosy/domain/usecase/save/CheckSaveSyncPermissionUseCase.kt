package com.nendo.argosy.domain.usecase.save

import android.os.Build
import android.os.Environment
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class CheckSaveSyncPermissionUseCase @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository
) {
    sealed class Result {
        data object Granted : Result()
        data object NotNeeded : Result()
        data object MissingPermission : Result()
    }

    suspend operator fun invoke(): Result {
        val prefs = preferencesRepository.preferences.first()
        if (!prefs.saveSyncEnabled) return Result.NotNeeded

        return if (hasFileAccessPermission()) {
            Result.Granted
        } else {
            Result.MissingPermission
        }
    }

    private fun hasFileAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }
}
