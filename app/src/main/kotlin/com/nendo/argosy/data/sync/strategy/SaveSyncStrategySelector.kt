package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncStrategySelector @Inject constructor(
    private val legacy: Lazy<LegacySaveSyncStrategy>,
    private val negotiator: Lazy<NegotiatorSaveSyncStrategy>,
    private val connectionManager: RomMConnectionManager,
) {
    fun current(): SaveSyncStrategy {
        val caps = connectionManager.getCapabilities()
        return if (caps.supportsSyncNegotiate) negotiator.get() else legacy.get()
    }
}
