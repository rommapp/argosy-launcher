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
     * True when [coreId] loads cheats from files at boot, not through retro_cheat_set; in-game edits
     * then apply on the next launch.
     */
    fun usesCheatFiles(coreId: String?): Boolean = false

    /**
     * Core options for this launch, applied beneath any option the player set.
     */
    suspend fun prepareForLaunch(
        game: GameEntity,
        romFile: File,
        target: EnhancementTarget
    ): Map<String, String> = emptyMap()

    /**
     * Reads this provisioner's files out of a pack before the installer extracts and deletes it.
     */
    suspend fun importFromArchive(game: GameEntity, archive: File) = Unit

    suspend fun syncCheats(game: GameEntity, romFile: File, target: EnhancementTarget.Builtin) = Unit
}
