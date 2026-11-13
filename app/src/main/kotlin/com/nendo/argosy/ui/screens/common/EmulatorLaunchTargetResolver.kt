package com.nendo.argosy.ui.screens.common

import android.content.Context
import android.os.Bundle
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.preferences.EmulatorDisplayTarget
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.repository.EmulatorConfigRepository
import com.nendo.argosy.util.DisplayAffinityHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A game intent with the placement its display target resolved to.
 */
data class GameLaunchRequest(
    val intent: android.content.Intent,
    val options: Bundle? = null
)

@Singleton
class EmulatorLaunchTargetResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val emulatorConfigRepository: EmulatorConfigRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val sessionStateStore by lazy { SessionStateStore(context) }

    suspend fun launchOptionsFor(
        gameId: Long,
        overrideDisplayId: Int? = null,
        emulatorPackage: String? = null,
        builtInDrawsBothScreens: Boolean = false
    ): Bundle? {
        val rolesSwapped = sessionStateStore.isRolesSwapped()
        val drawsBothScreens = builtInDrawsBothScreens ||
            emulatorPackage?.let(::drawsBothScreens) == true
        val displayId = displayAffinityHelper.usableDisplayId(overrideDisplayId)
            ?: displayAffinityHelper.bothScreensDisplayId().takeIf { drawsBothScreens }
            ?: targetDisplayId(
                stored = EmulatorDisplayTarget.fromString(
                    emulatorConfigRepository.getEffectiveDisplayTarget(gameId)
                ),
                gamesDefault = preferencesRepository.userPreferences.first().gamesDisplayTarget,
                rolesSwapped = rolesSwapped
            )
        DualScreenManagerHolder.instance?.setEmulatorDisplay(placedOn(displayId, rolesSwapped))
        return GameLaunchOptions.forDisplay(
            displayAffinityHelper.launchDisplayId(
                forEmulator = true,
                rolesSwapped = rolesSwapped,
                overrideDisplayId = displayId
            )
        )
    }

    /**
     * The display a game opens on when [stored] is its effective target, the way [launchOptionsFor]
     * places it.
     */
    fun placementDisplayId(
        stored: EmulatorDisplayTarget,
        gamesDefault: EmulatorDisplayTarget,
        rolesSwapped: Boolean
    ): Int = placedOn(targetDisplayId(stored, gamesDefault, rolesSwapped), rolesSwapped)

    private fun targetDisplayId(
        stored: EmulatorDisplayTarget,
        gamesDefault: EmulatorDisplayTarget,
        rolesSwapped: Boolean
    ): Int? = displayAffinityHelper.getDisplayTargetId(placementTarget(stored, gamesDefault), rolesSwapped)

    private fun placedOn(displayId: Int?, rolesSwapped: Boolean): Int =
        displayId ?: displayAffinityHelper.getEmulatorDisplayId(rolesSwapped)

    companion object {
        fun drawsBothScreens(packageName: String): Boolean =
            (EmulatorRegistry.getByPackage(packageName)
                ?: EmulatorRegistry.findFamilyForPackage(packageName)
                    ?.let { EmulatorRegistry.createDefFromFamily(it, packageName) })
                ?.drawsBothScreens == true

        fun placementTarget(
            stored: EmulatorDisplayTarget,
            gamesDefault: EmulatorDisplayTarget
        ): EmulatorDisplayTarget = when {
            stored != EmulatorDisplayTarget.DEFAULT -> stored
            gamesDefault == EmulatorDisplayTarget.PRESENTATION -> EmulatorDisplayTarget.DEFAULT
            else -> gamesDefault
        }

        fun resolvedTarget(
            stored: EmulatorDisplayTarget,
            gamesDefault: EmulatorDisplayTarget
        ): EmulatorDisplayTarget =
            if (stored != EmulatorDisplayTarget.DEFAULT) stored
            else EmulatorDisplayTarget.gamesDefaultFrom(gamesDefault.name)
    }
}
