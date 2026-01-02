package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.data.remote.romm.RomMResult
import javax.inject.Inject

class CreateCollectionUseCase @Inject constructor(
    private val rommRepository: RomMRepository
) {
    suspend operator fun invoke(name: String, description: String? = null): Result<Long> {
        return when (val result = rommRepository.createCollectionWithSync(name, description)) {
            is RomMResult.Success -> Result.success(result.data)
            is RomMResult.Error -> Result.failure(Exception(result.message))
        }
    }
}
