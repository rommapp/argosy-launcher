package com.nendo.argosy.domain.model

import org.json.JSONArray
import org.json.JSONObject

private const val KEY_CONTROLLER_ID = "controllerId"
private const val KEY_NAME = "name"

data class GripAutoController(
    val controllerId: String,
    val name: String
)

data class GripAutoControllers(
    val controllers: List<GripAutoController> = emptyList()
) {
    val controllerIds: Set<String> get() = controllers.mapTo(mutableSetOf()) { it.controllerId }

    fun with(controllerId: String, name: String): GripAutoControllers {
        if (controllerId.isEmpty()) return this
        val kept = controllers.filterNot { it.controllerId == controllerId }
        return GripAutoControllers(kept + GripAutoController(controllerId, name))
    }

    fun without(controllerId: String): GripAutoControllers =
        GripAutoControllers(controllers.filterNot { it.controllerId == controllerId })

    fun toJson(): String = JSONArray().apply {
        controllers.forEach { controller ->
            put(
                JSONObject().apply {
                    put(KEY_CONTROLLER_ID, controller.controllerId)
                    put(KEY_NAME, controller.name)
                }
            )
        }
    }.toString()

    companion object {
        fun fromJson(json: String?): GripAutoControllers {
            if (json.isNullOrBlank()) return GripAutoControllers()
            return try {
                val array = JSONArray(json)
                val parsed = (0 until array.length()).mapNotNull { index ->
                    val entry = array.optJSONObject(index) ?: return@mapNotNull null
                    val controllerId = entry.optString(KEY_CONTROLLER_ID)
                    if (controllerId.isEmpty()) return@mapNotNull null
                    GripAutoController(
                        controllerId = controllerId,
                        name = entry.optString(KEY_NAME).ifEmpty { "Controller" }
                    )
                }
                GripAutoControllers(parsed)
            } catch (e: org.json.JSONException) {
                GripAutoControllers()
            }
        }
    }
}
