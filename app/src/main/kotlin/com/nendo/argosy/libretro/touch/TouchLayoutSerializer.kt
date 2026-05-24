package com.nendo.argosy.libretro.touch

import org.json.JSONArray
import org.json.JSONObject

private const val CURRENT_SCHEMA_VERSION = 1

object TouchLayoutSerializer {

    fun toJson(layout: ResolvedLayout): String {
        val arr = JSONArray()
        layout.placements.forEach { (id, p) ->
            arr.put(
                JSONObject()
                    .put("group", id.name)
                    .put("x", p.anchorX.toDouble())
                    .put("y", p.anchorY.toDouble())
                    .put("scale", p.scale.toDouble())
            )
        }
        return JSONObject().put("schemaVersion", CURRENT_SCHEMA_VERSION).put("groups", arr).toString()
    }

    fun fromJson(json: String, default: ResolvedLayout): ResolvedLayout {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("groups") ?: return default
            val parsed = mutableMapOf<GroupId, GroupPlacement>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = runCatching { GroupId.valueOf(obj.optString("group")) }.getOrNull() ?: continue
                parsed[id] = GroupPlacement(
                    anchorX = obj.optDouble("x", 0.5).toFloat(),
                    anchorY = obj.optDouble("y", 0.5).toFloat(),
                    scale = obj.optDouble("scale", 1.0).toFloat()
                )
            }
            val merged = default.placements.toMutableMap()
            parsed.forEach { (k, v) -> merged[k] = v }
            ResolvedLayout(merged)
        } catch (_: Exception) {
            default
        }
    }

    fun schemaVersion(): Int = CURRENT_SCHEMA_VERSION
}
