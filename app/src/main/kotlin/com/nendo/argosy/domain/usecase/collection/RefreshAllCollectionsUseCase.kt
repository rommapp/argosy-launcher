package com.nendo.argosy.domain.usecase.collection

import com.nendo.argosy.data.remote.romm.RomMRepository
import javax.inject.Inject

class RefreshAllCollectionsUseCase @Inject constructor(
    private val romMRepository: RomMRepository,
    private val syncVirtualCollectionsUseCase: SyncVirtualCollectionsUseCase
) {
    suspend operator fun invoke() {
        romMRepository.syncCollections()
        syncVirtualCollectionsUseCase()
    }
}
