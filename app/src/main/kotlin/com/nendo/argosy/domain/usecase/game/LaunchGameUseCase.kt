package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.ExternalSessionStart
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.LaunchProgressTracker
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.startExternal
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.permissions.PermissionWatch
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.LibraryPointerRepair
import com.nendo.argosy.data.sync.CrossEmulatorSaveMigrator
import com.nendo.argosy.data.wallpaper.LockScreenArtManager
import com.nendo.argosy.domain.model.LaunchStep
import javax.inject.Inject

/**
 * Builds the launch intent, joining the caller's [LaunchProgressTracker] launch or opening one.
 * With sessionAtWindow a fresh external launch returns its play session as
 * [LaunchResult.Success.session] for the caller to start; without it the session starts here.
 * A fresh launch runs [CrossEmulatorSaveMigrator] first unless savesPrepared is passed.
 */
class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker,
    private val libraryPointerRepair: LibraryPointerRepair,
    private val lockScreenArtManager: LockScreenArtManager,
    private val launchProgressTracker: LaunchProgressTracker,
    private val gameRepository: GameRepository,
    private val crossEmulatorSaveMigrator: CrossEmulatorSaveMigrator,
    private val permissionWatch: PermissionWatch
) {
    suspend operator fun invoke(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        prefetchedGame: GameEntity? = null,
        origin: LaunchOrigin = LaunchOrigin.INTERNAL,
        savesPrepared: Boolean = false,
        sessionAtWindow: Boolean = false
    ): LaunchResult {
        val joined = launchProgressTracker.current
        val ticket = joined ?: launchProgressTracker.begin(
            gameId,
            prefetchedGame?.title ?: gameRepository.getById(gameId)?.title
        ) ?: return LaunchResult.Cancelled
        var result: LaunchResult = LaunchResult.Cancelled
        try {
            result = launch(ticket, gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame, origin, savesPrepared, sessionAtWindow)
            return result
        } finally {
            if (joined == null) launchProgressTracker.finish(ticket, launched = result is LaunchResult.Success)
        }
    }

    private suspend fun launch(
        ticket: LaunchProgressTracker.Ticket,
        gameId: Long,
        discId: Long?,
        forResume: Boolean,
        selectedDiscPath: String?,
        variantFileId: Long?,
        skipVariantPrompt: Boolean,
        allowVariantPrompt: Boolean,
        prefetchedGame: GameEntity?,
        origin: LaunchOrigin,
        savesPrepared: Boolean,
        sessionAtWindow: Boolean
    ): LaunchResult {
        libraryPointerRepair.await()
        if (!forResume && !permissionWatch.clearForLaunch()) return LaunchResult.Cancelled
        if (!forResume && !savesPrepared) crossEmulatorSaveMigrator.migrateBeforeLaunch(gameId)
        val result = gameLauncher.launch(gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame)
        if (ticket.isCancelled) return LaunchResult.Cancelled
        if (result is LaunchResult.Success && !forResume) {
            ticket.step(LaunchStep.PreparingUi)
            lockScreenArtManager.showBeforeLaunch(gameId)
            if (ticket.isCancelled) {
                lockScreenArtManager.showLibraryAfterCancelledLaunch()
                return LaunchResult.Cancelled
            }
        }
        if (result !is LaunchResult.Success || result.inProcess || forResume) return result
        val session = ExternalSessionStart(
            gameId = gameId,
            emulatorPackage = result.intent.component?.packageName
                ?: result.intent.`package`
                ?: "",
            coreName = extractCoreName(result.intent),
            variantFileId = result.variantFileId,
            origin = origin
        )
        if (sessionAtWindow) return result.copy(session = session)
        playSessionTracker.startExternal(session)
        return result
    }

    private fun extractCoreName(intent: Intent): String? {
        val libretroPath = intent.getStringExtra("LIBRETRO") ?: return null
        val coreFile = libretroPath.substringAfterLast("/")
        return coreFile
            .removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
            .takeIf { it.isNotEmpty() }
    }
}
