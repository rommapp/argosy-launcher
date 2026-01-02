package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import javax.inject.Inject

class RemoveGameFromCollectionUseCase @Inject constructor(
    private val rommRepository: RomMRepository
) {
    suspend operator fun invoke(gameId: Long, collectionId: Long): Result<Unit> {
        return when (val result = rommRepository.removeGameFromCollectionWithSync(gameId, collectionId)) {
            is RomMResult.Success -> Result.success(Unit)
            is RomMResult.Error -> Result.failure(Exception(result.message))
        }
    }
}
