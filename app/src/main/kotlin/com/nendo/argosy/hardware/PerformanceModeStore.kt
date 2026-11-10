package com.nendo.argosy.hardware

import com.nendo.argosy.data.access.PrivilegedAccess
import com.nendo.argosy.data.preferences.PerformancePreferencesRepository
import com.nendo.argosy.util.Logger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved performance mode. The root manager's approval is the one [PrivilegedAccess] keeps.
 * Calls that reach the backend block on its I/O, so callers run them off the main thread.
 */
@Singleton
class PerformanceModeStore @Inject constructor(
    private val resolver: DevicePerformanceResolver,
    private val preferences: PerformancePreferencesRepository,
    private val privilegedAccess: PrivilegedAccess
) {
    /**
     * Applies the saved mode once at startup. A backend gated on root approval is touched only
     * when approval is already on record, so a device never approved is never asked at startup.
     */
    suspend fun restoreSavedMode() {
        val saved = PerformanceMode.fromString(preferences.getPerformanceMode()) ?: return
        val approved = privilegedAccess.isMagiskApproved()
        val backend = resolver.resolve()?.takeIf { it.restoresSavedMode } ?: return
        if (backend.needsRootGrant && !approved) return
        if (!requestAccess(backend)) return
        if (!backend.applyMode(saved)) Logger.warn(TAG, "saved mode $saved did not take")
    }

    suspend fun requestAccess(backend: DevicePerformance): Boolean {
        val granted = backend.requestAccess()
        if (backend.needsRootGrant) privilegedAccess.recordMagiskApproval(granted)
        return granted
    }

    /**
     * Re-acquires write access in a new process when approval is on record.
     */
    suspend fun resumeAccess(backend: DevicePerformance) {
        if (!backend.needsRootGrant || backend.canWrite) return
        if (privilegedAccess.isMagiskApproved()) requestAccess(backend)
    }

    suspend fun remember(backend: DevicePerformance, mode: PerformanceMode) {
        if (backend.restoresSavedMode) preferences.setPerformanceMode(mode.name)
    }

    private companion object {
        const val TAG = "PerformanceModeStore"
    }
}
