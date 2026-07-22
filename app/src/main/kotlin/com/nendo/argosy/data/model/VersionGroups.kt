package com.nendo.argosy.data.model

import com.nendo.argosy.data.local.entity.GameFileEntity

/** Collapses a version group (set of game_files rows sharing versionGroup) to its launch file. */
object VersionGroups {
    fun groupKey(siblingRommId: Long): String = "romm:$siblingRommId"

    fun launchFile(groupFiles: List<GameFileEntity>): GameFileEntity? {
        val candidates = groupFiles.filter {
            VariantCategory.fromKey(it.category).isLaunchTarget
        }
        return candidates.firstOrNull { it.m3uPath != null && it.isLocallyPresent() }
            ?: candidates.filter { it.isLocallyPresent() }.maxByOrNull { it.fileSize }
            ?: candidates.maxByOrNull { it.fileSize }
    }
}
