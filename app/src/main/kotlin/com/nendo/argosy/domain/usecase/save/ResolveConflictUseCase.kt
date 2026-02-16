package com.nendo.argosy.domain.usecase.save

import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.SaveSyncResult
import javax.inject.Inject

class ResolveConflictUseCase @Inject constructor(
    private val saveSyncRepository: SaveSyncRepository
) {
    enum class Resolution { KEEP_LOCAL, KEEP_SERVER }

    sealed class Result {
        data object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(
        gameId: Long,
        emulatorId: String,
        resolution: Resolution,
        channelName: String? = null
    ): Result {
        return when (resolution) {
            Resolution.KEEP_LOCAL -> {
                when (val result = saveSyncRepository.uploadSave(gameId, emulatorId, channelName = channelName)) {
                    is SaveSyncResult.Success -> Result.Success
                    is SaveSyncResult.Error -> Result.Error(result.message)
                    else -> Result.Error("Unexpected result")
                }
            }
            Resolution.KEEP_SERVER -> {
                when (val result = saveSyncRepository.downloadSave(gameId, emulatorId, channelName = channelName)) {
                    is SaveSyncResult.Success -> Result.Success
                    is SaveSyncResult.Error -> Result.Error(result.message)
                    else -> Result.Error("Unexpected result")
                }
            }
        }
    }
}
