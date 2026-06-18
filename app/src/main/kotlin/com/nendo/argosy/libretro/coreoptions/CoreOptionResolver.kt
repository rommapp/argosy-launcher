package com.nendo.argosy.libretro.coreoptions

import com.nendo.argosy.data.local.dao.CoreOptionOverrideDao
import com.nendo.argosy.data.local.dao.GameCoreOptionOverrideDao
import com.swordfish.libretrodroid.Variable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoreOptionResolver @Inject constructor(
    private val coreOptionOverrideDao: CoreOptionOverrideDao,
    private val gameCoreOptionOverrideDao: GameCoreOptionOverrideDao
) {
    suspend fun resolveVariables(coreId: String, gameId: Long? = null): Array<Variable> {
        val manifest = CoreOptionManifestRegistry.getManifest(coreId)
            ?: return emptyArray()
        val globalOverrides = coreOptionOverrideDao.getOverridesForCore(coreId)
            .associate { it.optionKey to it.value }
        val gameOverrides = gameId?.let { id ->
            gameCoreOptionOverrideDao.getForGame(id, coreId).associate { it.optionKey to it.value }
        } ?: emptyMap()

        return manifest.options.mapNotNull { option ->
            val gameOverride = gameOverrides[option.key]
            val globalOverride = globalOverrides[option.key]
            val hasArgosyOverride = option.defaultValue != option.coreDefault
            when {
                gameOverride != null -> Variable(key = option.key, value = gameOverride)
                globalOverride != null -> Variable(key = option.key, value = globalOverride)
                hasArgosyOverride -> Variable(key = option.key, value = option.defaultValue)
                else -> null
            }
        }.toTypedArray()
    }
}
