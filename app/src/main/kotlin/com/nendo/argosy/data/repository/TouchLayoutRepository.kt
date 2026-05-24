package com.nendo.argosy.data.repository

import android.content.res.Configuration
import com.nendo.argosy.data.local.dao.TouchLayoutOverrideDao
import com.nendo.argosy.data.local.entity.TouchLayoutOverrideEntity
import com.nendo.argosy.libretro.touch.LayoutDefaults
import com.nendo.argosy.libretro.touch.ResolvedLayout
import com.nendo.argosy.libretro.touch.TouchLayoutRegistry
import com.nendo.argosy.libretro.touch.TouchLayoutSerializer
import com.nendo.argosy.libretro.touch.TouchLayoutSpec
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TouchLayoutRepository @Inject constructor(
    private val dao: TouchLayoutOverrideDao
) {
    suspend fun load(platformSlug: String, orientation: Int): ResolvedLayout {
        val spec = TouchLayoutRegistry.forPlatform(platformSlug)
        return load(spec, platformSlug, orientation)
    }

    suspend fun load(spec: TouchLayoutSpec, platformSlug: String, orientation: Int): ResolvedLayout {
        val default = LayoutDefaults.forOrientation(spec, orientation)
        val key = orientationKey(orientation)
        val row = dao.get(platformSlug, key) ?: return default
        return TouchLayoutSerializer.fromJson(row.layoutJson, default)
    }

    suspend fun save(platformSlug: String, orientation: Int, layout: ResolvedLayout) {
        dao.upsert(
            TouchLayoutOverrideEntity(
                platformSlug = platformSlug,
                orientation = orientationKey(orientation),
                schemaVersion = TouchLayoutSerializer.schemaVersion(),
                layoutJson = TouchLayoutSerializer.toJson(layout),
                updatedAt = Instant.now()
            )
        )
    }

    suspend fun reset(platformSlug: String, orientation: Int) {
        dao.delete(platformSlug, orientationKey(orientation))
    }

    suspend fun resetPlatform(platformSlug: String) {
        dao.deleteAllForPlatform(platformSlug)
    }

    private fun orientationKey(orientation: Int): String =
        if (orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "landscape"
}
