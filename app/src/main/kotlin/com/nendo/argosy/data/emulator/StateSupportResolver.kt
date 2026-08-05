package com.nendo.argosy.data.emulator

import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.libretro.LibretroCoreRegistry
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether save states can be produced and consumed for a game as it is currently configured.
 *
 * The launcher and the built-in emulator have to answer this the same way or the UI offers state
 * actions that LibretroActivity then refuses. For the built-in emulator that means resolving the
 * same core through [BuiltinCoreResolver] and applying the same core and platform rules the
 * activity applies at runtime; the only thing this cannot know is the per-content serialize probe,
 * which needs a loaded core.
 */
@Singleton
class StateSupportResolver @Inject constructor(
    private val builtinCoreResolver: BuiltinCoreResolver,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun supportsStates(
        emulatorId: String?,
        gameId: Long?,
        platformId: Long?,
        platformSlug: String?
    ): Boolean {
        if (emulatorId == null) return false
        if (StatePathRegistry.getConfig(emulatorId) == null) return false
        if (emulatorId != EmulatorRegistry.BUILTIN_ID) return true

        if (platformSlug != null &&
            PlatformDefinitions.getCanonicalSlug(platformSlug) in
            LibretroCoreRegistry.PLATFORMS_WITHOUT_STATE_SUPPORT
        ) {
            return false
        }
        if (platformId == null || platformSlug == null) return true

        val coreId = builtinCoreResolver.resolve(gameId, platformId, platformSlug).coreId
        val hwCoreSaveStatesEnabled = userPreferencesRepository
            .getBuiltinEmulatorSettings().first().hwCoreSaveStatesEnabled
        return LibretroCoreRegistry.coreStatesAllowed(coreId, hwCoreSaveStatesEnabled)
    }
}
