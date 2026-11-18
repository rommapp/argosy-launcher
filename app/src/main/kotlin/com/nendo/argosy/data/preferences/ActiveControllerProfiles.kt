package com.nendo.argosy.data.preferences

import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.input.ControllerProfile
import com.nendo.argosy.core.input.ControllerProfiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The profile of the pad that last sent input. [controllerId] is null until a pad has sent input,
 * and [profile] is then the defaults. [saved] is true when a stored profile, exact or same-model,
 * applies to the pad.
 */
data class ActiveControllerProfile(
    val controllerId: String?,
    val profile: ControllerProfile,
    val saved: Boolean
)

/**
 * Reads and edits the Controller Layout and button swaps of the pad that last sent input, or the
 * defaults while no pad has. Every settings row, quick settings toggle and setup choice for those
 * values reads and writes through here.
 */
@Singleton
class ActiveControllerProfiles @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    private val controllerTracker: ConnectedControllerTracker
) {
    val active: Flow<ActiveControllerProfile> =
        combine(preferencesRepository.userPreferences, controllerTracker.activeController) { prefs, pad ->
            resolve(prefs.controllerProfiles, pad?.controllerId)
        }.distinctUntilChanged()

    fun current(prefs: UserPreferences): ActiveControllerProfile =
        resolve(prefs.controllerProfiles, activeControllerId())

    suspend fun setControllerLayout(layout: String) = edit { it.copy(controllerLayout = layout) }

    suspend fun setSwapAB(enabled: Boolean) = edit { it.copy(swapAB = enabled) }

    suspend fun setSwapXY(enabled: Boolean) = edit { it.copy(swapXY = enabled) }

    suspend fun setSwapStartSelect(enabled: Boolean) = edit { it.copy(swapStartSelect = enabled) }

    suspend fun forgetActive() {
        val controllerId = activeControllerId() ?: return
        preferencesRepository.forgetControllerProfile(controllerId)
    }

    private suspend fun edit(change: (ControllerProfile) -> ControllerProfile) =
        preferencesRepository.editControllerProfile(activeControllerId(), change)

    private fun activeControllerId(): String? = controllerTracker.activeController.value?.controllerId

    private fun resolve(profiles: ControllerProfiles, controllerId: String?) = ActiveControllerProfile(
        controllerId = controllerId,
        profile = profiles.forController(controllerId),
        saved = profiles.savedFor(controllerId) != null
    )
}
