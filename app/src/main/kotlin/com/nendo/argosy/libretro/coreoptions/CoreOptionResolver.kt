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
        pruneRetiredOverrides(coreId, manifest.options.map { it.key })
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
                gameOverride != null ->
                    Variable(key = option.key, value = option.resolveStored(gameOverride))
                globalOverride != null ->
                    Variable(key = option.key, value = option.resolveStored(globalOverride))
                hasArgosyOverride -> Variable(key = option.key, value = option.defaultValue)
                else -> null
            }
        }.toTypedArray()
    }

    /**
     * Overrides outlive the option they were set for. When a core retires a key there is no
     * current key to repair the row into and no surface that can show or clear it, so it is
     * dropped rather than left to resurrect with a stale value if the key ever returns. Renamed
     * values are a different case and are repaired, not dropped - see [CoreOptionDef.resolveStored].
     */
    private suspend fun pruneRetiredOverrides(coreId: String, keptKeys: List<String>) {
        if (keptKeys.isEmpty()) return
        coreOptionOverrideDao.deleteRetired(coreId, keptKeys)
        gameCoreOptionOverrideDao.deleteRetired(coreId, keptKeys)
    }
}
