package com.nendo.argosy.data.emulator.enhancements

import com.nendo.argosy.data.local.entity.GameEntity
import java.io.File

sealed interface EnhancementTarget {
    data class Builtin(
        val coreId: String,
        val systemDir: File,
        val saveDir: File
    ) : EnhancementTarget

    data class External(val emulatorPackage: String) : EnhancementTarget
}

/**
 * Places a game's enhancements where its emulator reads them, before the emulator starts.
 */
interface GameEnhancementProvisioner {
    /**
     * Core options for this launch, applied beneath any option the player set.
     */
    suspend fun prepareForLaunch(
        game: GameEntity,
        romFile: File,
        target: EnhancementTarget
    ): Map<String, String> = emptyMap()
}
