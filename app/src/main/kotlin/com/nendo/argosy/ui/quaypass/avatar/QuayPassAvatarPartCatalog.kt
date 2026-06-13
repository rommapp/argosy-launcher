package com.nendo.argosy.ui.quaypass.avatar

import android.content.Context
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-category mapping from numeric part index to the original on-disk
 * filename suffix. Some categories use 2-digit padding (eyes-01.svg) and
 * others bare integers (hair-0.svg); the catalog records each original
 * suffix so callers don't have to guess the padding rule.
 */
@Singleton
class QuayPassAvatarPartCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var cache: Map<AvatarCategory, Map<Int, String>>? = null

    fun forCategory(category: AvatarCategory): List<Int> =
        ensureLoaded()[category]?.keys?.sorted().orEmpty()

    fun assetPathFor(category: AvatarCategory, index: Int): String? {
        val suffix = ensureLoaded()[category]?.get(index) ?: return null
        return "quaypass/avatar/${category.prefix}-$suffix.svg"
    }

    private fun ensureLoaded(): Map<AvatarCategory, Map<Int, String>> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val all = runCatching { context.assets.list(ASSET_DIR)?.toList() }.getOrNull().orEmpty()
            val grouped = AvatarCategory.entries.associateWith { category ->
                buildMap {
                    for (name in all) {
                        if (!name.endsWith(".svg")) continue
                        val base = name.removeSuffix(".svg")
                        val parts = base.split("-", limit = 2)
                        if (parts.size != 2 || parts[0] != category.prefix) continue
                        val n = parts[1].toIntOrNull() ?: continue
                        put(n, parts[1])
                    }
                }
            }
            cache = grouped
            return grouped
        }
    }

    companion object {
        private const val ASSET_DIR = "quaypass/avatar"
    }
}
