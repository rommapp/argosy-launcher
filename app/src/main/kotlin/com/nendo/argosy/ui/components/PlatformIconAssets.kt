package com.nendo.argosy.ui.components

import android.content.Context
import com.nendo.argosy.data.platform.PlatformDefinitions

object PlatformIconAssets {
    private const val ASSET_DIR = "platforms"

    private val iconFallbacks = mapOf(
        "fbneo" to "arcade",
        "mame" to "arcade",
        "cps1" to "arcade",
        "cps2" to "arcade",
        "cps3" to "arcade"
    )

    @Volatile private var cached: Set<String>? = null

    fun resolveAssetUri(context: Context, platformSlug: String): String? {
        if (platformSlug.isBlank()) return null
        val available = available(context)
        if (available.isEmpty()) return null
        for (candidate in PlatformDefinitions.getSlugsForCanonical(platformSlug)) {
            if (candidate in available) return "file:///android_asset/$ASSET_DIR/$candidate.svg"
        }
        iconFallbacks[PlatformDefinitions.getCanonicalSlug(platformSlug)]?.let { fallback ->
            if (fallback in available) return "file:///android_asset/$ASSET_DIR/$fallback.svg"
        }
        return null
    }

    private fun available(context: Context): Set<String> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val list = context.assets.list(ASSET_DIR)
                ?.asSequence()
                ?.filter { it.endsWith(".svg") }
                ?.map { it.removeSuffix(".svg") }
                ?.toSet()
                ?: emptySet()
            cached = list
            return list
        }
    }
}
