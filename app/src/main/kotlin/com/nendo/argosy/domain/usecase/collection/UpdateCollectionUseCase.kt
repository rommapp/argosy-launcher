package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import javax.inject.Inject

class UpdateCollectionUseCase @Inject constructor(
    private val rommRepository: RomMRepository
) {
    suspend operator fun invoke(collectionId: Long, name: String, description: String? = null): Result<Unit> {
        return when (val result = rommRepository.updateCollectionWithSync(collectionId, name, description)) {
            is RomMResult.Success -> Result.success(Unit)
            is RomMResult.Error -> Result.failure(Exception(result.message))
        }
    }
}
