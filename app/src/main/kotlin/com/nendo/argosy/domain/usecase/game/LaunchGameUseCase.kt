package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.LaunchRetryTracker
import com.nendo.argosy.data.emulator.PlaySessionTracker
import javax.inject.Inject

class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker,
    private val launchRetryTracker: LaunchRetryTracker
) {
    suspend operator fun invoke(gameId: Long, discId: Long? = null): LaunchResult {
        val result = gameLauncher.launch(gameId, discId)
        if (result is LaunchResult.Success) {
            val coreName = extractCoreName(result.intent)
            playSessionTracker.startSession(
                gameId = gameId,
                emulatorPackage = result.intent.component?.packageName
                    ?: result.intent.`package`
                    ?: "",
                coreName = coreName
            )
            launchRetryTracker.onLaunchStarted(result.intent)
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
