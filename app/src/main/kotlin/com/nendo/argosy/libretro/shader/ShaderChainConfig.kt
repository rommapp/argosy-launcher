package com.nendo.argosy.libretro.shader

import org.json.JSONArray
import org.json.JSONObject

data class ShaderChainConfig(
    val entries: List<Entry> = emptyList()
) {
    data class Entry(
        val shaderId: String,
        val params: Map<String, String> = emptyMap()
    )

    fun summary(): String {
        if (entries.isEmpty()) return "None"
        val names = entries.map { entry ->
            entry.shaderId
                .removePrefix("custom:")
                .replace('-', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        return when {
            names.size == 1 -> names.first()
            names.size == 2 -> "${names[0]} + ${names[1]}"
            else -> "${names[0]} + ${names[1]} +${names.size - 2} more"
        }
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("id", entry.shaderId)
            if (entry.params.isNotEmpty()) {
                val paramsObj = JSONObject()
                for ((k, v) in entry.params) paramsObj.put(k, v)
                obj.put("params", paramsObj)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    companion object {
        fun fromJson(json: String): ShaderChainConfig {
            if (json.isBlank()) return ShaderChainConfig()
            return try {
                val arr = JSONArray(json)
                val entries = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val params = if (obj.has("params")) {
                        val paramsObj = obj.getJSONObject("params")
                        paramsObj.keys().asSequence()
                            .associateWith { paramsObj.getString(it) }
                    } else emptyMap()
                    Entry(shaderId = obj.getString("id"), params = params)
                }
                ShaderChainConfig(entries)
            } catch (_: Exception) {
                ShaderChainConfig()
            }
        }
    }
}
