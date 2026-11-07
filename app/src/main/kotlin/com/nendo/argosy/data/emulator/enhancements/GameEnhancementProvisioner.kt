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
 * Puts a game's enhancements where its emulator reads them, before the emulator starts. An
 * emulator reads these files once at boot, so the launch path is the only place that can make
 * them take effect on the first run.
 */
interface GameEnhancementProvisioner {
    /**
     * True when [coreId] takes cheats from files at boot instead of through retro_cheat_set, so a
     * change made in game lands on the next launch.
     */
    fun usesCheatFiles(coreId: String?): Boolean = false

    /**
     * Returns core options to apply as defaults for this launch. An option the player set
     * explicitly still wins over these.
     */
    suspend fun prepareForLaunch(
        game: GameEntity,
        romFile: File,
        target: EnhancementTarget
    ): Map<String, String> = emptyMap()

    /**
     * Reads what this provisioner needs out of a pack before the installer extracts it and
     * deletes the archive.
     */
    suspend fun importFromArchive(game: GameEntity, archive: File) = Unit

    suspend fun syncCheats(game: GameEntity, romFile: File, target: EnhancementTarget.Builtin) = Unit
}
