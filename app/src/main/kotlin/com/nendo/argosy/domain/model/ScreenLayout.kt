package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class ScreenRole {
    PRIMARY,
    PRESENTATION,
    APP_TARGET,
    OFF;

    companion object {
        fun fromString(value: String?): ScreenRole = entries.find { it.name == value } ?: PRESENTATION
    }
}

data class ScreenSpec(
    val key: String,
    val widthPx: Int,
    val heightPx: Int,
    val builtIn: Boolean
) {
    val areaPx: Long get() = widthPx.toLong() * heightPx.toLong()
}

private const val PIN_CONTROL_TOKEN = "control"
private const val PIN_PRESENTATION_TOKEN = "presentation"
private const val PIN_PANEL_TOKEN_PREFIX = "screen:"
private const val ROLES_FIELD = "roles"
private const val NAVIGATION_MENU_FIELD = "navigationMenu"

/**
 * The screen a screen set draws the navigation menu on. [Panel] names a physical screen by its
 * stable key. Anything unreadable is [Control].
 */
sealed interface NavigationMenuPin {
    data object Control : NavigationMenuPin

    data object Presentation : NavigationMenuPin

    data class Panel(val screenKey: String) : NavigationMenuPin

    val token: String
        get() = when (this) {
            Control -> PIN_CONTROL_TOKEN
            Presentation -> PIN_PRESENTATION_TOKEN
            is Panel -> PIN_PANEL_TOKEN_PREFIX + screenKey
        }

    /**
     * Whether the menu draws on the presentation screen, given the stable key of the attached
     * screen holding PRESENTATION right now, or null when no screen holds it.
     */
    fun drawsOnPresentation(presentationScreenKey: String?): Boolean = when (this) {
        Control -> false
        Presentation -> presentationScreenKey != null
        is Panel -> screenKey == presentationScreenKey
    }

    companion object {
        fun fromToken(token: String?): NavigationMenuPin = when {
            token == PIN_PRESENTATION_TOKEN -> Presentation
            token != null && token.startsWith(PIN_PANEL_TOKEN_PREFIX) &&
                token.length > PIN_PANEL_TOKEN_PREFIX.length ->
                Panel(token.removePrefix(PIN_PANEL_TOKEN_PREFIX))
            else -> Control
        }
    }
}

data class ScreenLayout(
    val roles: Map<String, ScreenRole> = emptyMap(),
    val navigationMenuPin: NavigationMenuPin = NavigationMenuPin.Control
) {

    fun roleFor(screenKey: String): ScreenRole? = roles[screenKey]

    /**
     * Assigns [role] to [screenKey], handing the screen that held [role] the role [screenKey] is
     * giving up. Every role stays held by at most one screen through any assignment, including a
     * promotion out of APP_TARGET or OFF. Assigning OFF takes no role from another screen.
     */
    fun withRole(screenKey: String, role: ScreenRole): ScreenLayout {
        val previous = roles[screenKey] ?: ScreenRole.PRESENTATION
        val holder = roles.entries.find { it.value == role && it.key != screenKey }?.key
        val next = roles.toMutableMap()
        next[screenKey] = role
        if (holder != null && role != ScreenRole.OFF) next[holder] = previous
        return copy(roles = next)
    }

    val primaryKey: String? get() = roles.entries.find { it.value == ScreenRole.PRIMARY }?.key

    val presentationKey: String? get() = roles.entries.find { it.value == ScreenRole.PRESENTATION }?.key

    val appTargetKey: String? get() = roles.entries.find { it.value == ScreenRole.APP_TARGET }?.key

    val isSingleDisplay: Boolean get() = presentationKey == null

    fun toJson(): String = JSONObject().apply {
        put(ROLES_FIELD, JSONObject().apply { roles.forEach { (key, role) -> put(key, role.name) } })
        if (navigationMenuPin != NavigationMenuPin.Control) put(NAVIGATION_MENU_FIELD, navigationMenuPin.token)
    }.toString()

    companion object {
        /**
         * Reads both the current shape, roles under [ROLES_FIELD] beside the menu pin, and the
         * earlier flat map of screen key to role, which carries no pin.
         */
        fun fromJson(json: String?): ScreenLayout {
            if (json.isNullOrBlank()) return ScreenLayout()
            return try {
                val obj = JSONObject(json)
                val nested = obj.optJSONObject(ROLES_FIELD)
                val rolesObj = nested ?: obj
                val roles = rolesObj.keys().asSequence().associateWith { key ->
                    ScreenRole.fromString(rolesObj.optString(key))
                }
                val pin = if (nested != null && obj.has(NAVIGATION_MENU_FIELD)) {
                    NavigationMenuPin.fromToken(obj.optString(NAVIGATION_MENU_FIELD))
                } else {
                    NavigationMenuPin.Control
                }
                ScreenLayout(roles, pin)
            } catch (_: Exception) {
                ScreenLayout()
            }
        }

        /**
         * The layout a screen set holds before anyone assigns a role. The smallest internal panel
         * takes PRIMARY, remaining internals then externals take [FILL_ORDER] in turn, and
         * [invertInternalOrder] reverses the internal ordering for a model that needs it.
         * Externals keep the order they arrive in.
         */
        fun defaultFor(
            screens: List<ScreenSpec>,
            invertInternalOrder: Boolean = false
        ): ScreenLayout {
            if (screens.isEmpty()) return ScreenLayout()
            val internals = screens.filter { it.builtIn }
                .sortedWith(compareByDescending<ScreenSpec> { it.areaPx }.thenBy { screens.indexOf(it) })
                .let { if (invertInternalOrder) it.asReversed() else it }
            val externals = screens.filterNot { it.builtIn }
            val primary = internals.lastOrNull() ?: externals.firstOrNull() ?: return ScreenLayout()
            val rest = (internals + externals).filterNot { it.key == primary.key }
            val roles = mutableMapOf(primary.key to ScreenRole.PRIMARY)
            rest.forEachIndexed { index, screen ->
                roles[screen.key] = FILL_ORDER.getOrNull(index) ?: ScreenRole.OFF
            }
            return ScreenLayout(roles)
        }

        private val FILL_ORDER = listOf(ScreenRole.PRESENTATION, ScreenRole.APP_TARGET)
    }
}

data class ScreenLayouts(val bySet: Map<String, ScreenLayout> = emptyMap()) {

    fun layoutFor(setKey: String): ScreenLayout? = bySet[setKey]

    fun with(setKey: String, layout: ScreenLayout): ScreenLayouts =
        ScreenLayouts(bySet + (setKey to layout))

    fun toJson(): String = JSONObject().apply {
        bySet.forEach { (setKey, layout) -> put(setKey, JSONObject(layout.toJson())) }
    }.toString()

    companion object {
        fun fromJson(json: String?): ScreenLayouts {
            if (json.isNullOrBlank()) return ScreenLayouts()
            return try {
                val obj = JSONObject(json)
                val sets = obj.keys().asSequence().associateWith { setKey ->
                    ScreenLayout.fromJson(obj.optJSONObject(setKey)?.toString())
                }
                ScreenLayouts(sets)
            } catch (_: Exception) {
                ScreenLayouts()
            }
        }

        fun setKeyOf(screenKeys: List<String>): String = screenKeys.sorted().joinToString("|")
    }
}
