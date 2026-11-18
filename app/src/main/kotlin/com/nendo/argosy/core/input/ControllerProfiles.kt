package com.nendo.argosy.core.input

/**
 * How one pad reads: [controllerLayout] is a Controller Layout token ("auto", "nintendo", "xbox"),
 * and the swaps are the same three the navigation settings expose.
 */
data class ControllerProfile(
    val controllerLayout: String = LAYOUT_AUTO,
    val swapAB: Boolean = false,
    val swapXY: Boolean = false,
    val swapStartSelect: Boolean = false
) {
    companion object {
        const val LAYOUT_AUTO = "auto"
    }
}

/**
 * Saved per-pad profiles keyed by [controllerIdOf], and the [defaults] every pad without one uses.
 * The one place a pad's profile is resolved: its exact entry first, then the first entry for the
 * same vendor and product. A pad reporting vendor and product 0 never matches by model.
 */
data class ControllerProfiles(
    val defaults: ControllerProfile = ControllerProfile(),
    val saved: Map<String, ControllerProfile> = emptyMap()
) {
    fun savedKeyFor(controllerId: String?): String? {
        if (controllerId == null) return null
        if (controllerId in saved) return controllerId
        val model = modelPrefixOf(controllerId) ?: return null
        return saved.keys.firstOrNull { it.startsWith(model) }
    }

    fun savedFor(controllerId: String?): ControllerProfile? = savedKeyFor(controllerId)?.let(saved::get)

    fun forController(controllerId: String?): ControllerProfile = savedFor(controllerId) ?: defaults

    private fun modelPrefixOf(controllerId: String): String? {
        val parts = controllerId.split(':', limit = 3)
        val vendorId = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val productId = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (vendorId == 0 && productId == 0) return null
        return "$vendorId:$productId:"
    }
}

/**
 * The pad that last sent input and its detected lettering. [controllerId] is null until a pad has
 * sent input; [layout] then falls back to the first connected pad.
 */
data class ActivePad(
    val controllerId: String?,
    val layout: DetectedLayout?
)
