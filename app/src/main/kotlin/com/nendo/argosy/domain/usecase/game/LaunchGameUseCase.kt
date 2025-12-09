package com.nendo.argosy.domain.usecase.game

import android.content.Intent
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.LaunchResult
import com.nendo.argosy.data.emulator.PlaySessionTracker
import javax.inject.Inject

class LaunchGameUseCase @Inject constructor(
    private val gameLauncher: GameLauncher,
    private val playSessionTracker: PlaySessionTracker
) {
    suspend operator fun invoke(gameId: Long, discId: Long? = null): LaunchResult {
        return when (val result = gameLauncher.launch(gameId, discId)) {
            is LaunchResult.Success -> {
                playSessionTracker.startSession(
                    gameId = gameId,
                    emulatorPackage = result.intent.`package` ?: ""
                )
                result
            }
            else -> result
        }
    }
}
