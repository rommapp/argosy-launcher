package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.local.entity.GameEntity
import javax.inject.Inject

/**
 * Builds the launch intent and opens the play session for an external emulator launch.
 *
 * An in-process launch opens its session from LibretroActivity, which alone knows the confirmed
 * hardcore mode, the loaded core and the netplay role. A resume keeps the session that is already
 * running. Neither opens a session here.
 */
class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker
) {
    suspend operator fun invoke(
        gameId: Long,
        discId: Long? = null,
        forResume: Boolean = false,
        selectedDiscPath: String? = null,
        variantFileId: Long? = null,
        skipVariantPrompt: Boolean = false,
        allowVariantPrompt: Boolean = true,
        prefetchedGame: GameEntity? = null
    ): LaunchResult {
        val result = gameLauncher.launch(gameId, discId, forResume, selectedDiscPath, variantFileId, skipVariantPrompt, allowVariantPrompt, prefetchedGame)
        if (result is LaunchResult.Success && !result.inProcess && !forResume) {
            val coreName = extractCoreName(result.intent)
            playSessionTracker.startSession(
                gameId = gameId,
                emulatorPackage = result.intent.component?.packageName
                    ?: result.intent.`package`
                    ?: "",
                coreName = coreName,
                isNewGame = true,
                variantFileId = variantFileId
            )
        }
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
