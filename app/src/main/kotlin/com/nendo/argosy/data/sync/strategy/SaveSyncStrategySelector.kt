package com.nendo.argosy.data.sync.strategy

import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveSyncStrategySelector @Inject constructor(
    private val legacy: Lazy<LegacySaveSyncStrategy>,
    private val connectionManager: RomMConnectionManager,
) {
    @Volatile
    internal var negotiatorProvider: (() -> SaveSyncStrategy)? = null

    fun current(): SaveSyncStrategy {
        val caps = connectionManager.getCapabilities()
        if (caps.supportsSyncNegotiate) {
            negotiatorProvider?.invoke()?.let { return it }
        }
        return legacy.get()
    }
}
