package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class HomeLayoutKind { CAROUSEL, AUTO_GRID, CUSTOM_GRID }

enum class HomeRowAlignment { TOP, CENTER, BOTTOM }

enum class HomeFocusPosition { LEADING, CENTER }

enum class HomeScrollAxis { VERTICAL, HORIZONTAL }


/**
 * Per-layout presentation settings. Each layout owns its own type, so a name two layouts happen to
 * share cannot leak between them and adding a fourth layout stays additive.
 *
 * [inverted] reverses the reading order relative to the current layout direction rather than
 * absolutely, so it composes with a right-to-left locale instead of cancelling it out.
 */
sealed interface HomeLayoutConfig {
    val kind: HomeLayoutKind
}

/**
 * @param restingScale how large an unfocused cover is against the focused one. The focused cover
 *   always fills the height its rail is given, so its size is not a setting; the only thing left to
 *   choose is how much its neighbours shrink away from it.
 */
data class CarouselConfig(
    val rowAlignment: HomeRowAlignment = HomeRowAlignment.BOTTOM,
    val focusPosition: HomeFocusPosition = HomeFocusPosition.LEADING,
    val inverted: Boolean = false,
    val restingScale: Float = 0.5f,
    val neighbourPush: Boolean = true,
    val showPlatformBadge: Boolean = true
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.CAROUSEL

    val focusScale: Float get() = 1f / restingScale.coerceAtLeast(MIN_RESTING_SCALE)
}

const val MIN_RESTING_SCALE = 0.5f

/**
 * [laneCount] counts the lanes across the axis you are not scrolling along, so it reads as columns
 * when scrolling vertically and as rows when scrolling horizontally. Storing the one number keeps
 * the choice meaningful when the axis is flipped, rather than leaving a stale count behind.
 */
data class AutoGridConfig(
    val scrollAxis: HomeScrollAxis = HomeScrollAxis.VERTICAL,
    val laneCount: Int = DEFAULT_LANE_COUNT,
    val showTitles: Boolean = true
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.AUTO_GRID
}

/**
 * What happens to a game the moment its download finishes, for a grid whose contents are otherwise
 * placed by hand. Off leaves the grid alone; the other two exist because a freshly downloaded game
 * is the one thing a curator almost always wants to hand.
 */
enum class HomeTileAutoAdd { OFF, AUTO, PROMPT }

/**
 * A page is [laneCount] lanes across its short side; how many cells run along the long side follows
 * from the display's shape, so one curated page keeps its proportions on a tall handheld and a wide
 * television instead of being authored for one and stretched on the other.
 */
data class CustomGridConfig(
    val laneCount: Int = DEFAULT_LANE_COUNT,
    val autoAdd: HomeTileAutoAdd = HomeTileAutoAdd.OFF,
    val showEmptySlots: Boolean = true,
    val persistBlankPages: Boolean = false,
    val autoFit: Boolean = true,
    val pageCount: Int = 0
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.CUSTOM_GRID
}

const val DEFAULT_LANE_COUNT = 3

const val MIN_LANE_COUNT = 2

/**
 * Ceiling on lanes. Renderers honour whatever is stored within this range rather than second
 * guessing it against their own dimensions; picking a count that suits the screen is the reader's
 * call, not the grid's.
 */
const val MAX_LANE_COUNT = 8

/**
 * The selected layout plus every layout's settings, so switching back and forth does not discard
 * what was configured for the layout being left.
 */
data class HomeLayoutSettings(
    val selected: HomeLayoutKind = HomeLayoutKind.CAROUSEL,
    val carousel: CarouselConfig = CarouselConfig(),
    val autoGrid: AutoGridConfig = AutoGridConfig(),
    val customGrid: CustomGridConfig = CustomGridConfig()
) {
    val active: HomeLayoutConfig
        get() = when (selected) {
            HomeLayoutKind.CAROUSEL -> carousel
            HomeLayoutKind.AUTO_GRID -> autoGrid
            HomeLayoutKind.CUSTOM_GRID -> customGrid
        }

    fun toJson(): String = JSONObject().apply {
        put(KEY_SELECTED, selected.name)
        put(
            KEY_CAROUSEL,
            JSONObject().apply {
                put(KEY_ROW_ALIGNMENT, carousel.rowAlignment.name)
                put(KEY_FOCUS_POSITION, carousel.focusPosition.name)
                put(KEY_INVERTED, carousel.inverted)
                put(KEY_RESTING_SCALE, carousel.restingScale.toDouble())
                put(KEY_NEIGHBOUR_PUSH, carousel.neighbourPush)
                put(KEY_PLATFORM_BADGE, carousel.showPlatformBadge)
            }
        )
        put(
            KEY_AUTO_GRID,
            JSONObject().apply {
                put(KEY_SCROLL_AXIS, autoGrid.scrollAxis.name)
                put(KEY_LANE_COUNT, autoGrid.laneCount)
                put(KEY_SHOW_TITLES, autoGrid.showTitles)
            }
        )
        put(
            KEY_CUSTOM_GRID,
            JSONObject().apply {
                put(KEY_LANE_COUNT, customGrid.laneCount)
                put(KEY_AUTO_ADD, customGrid.autoAdd.name)
                put(KEY_EMPTY_SLOTS, customGrid.showEmptySlots)
                put(KEY_PERSIST_PAGES, customGrid.persistBlankPages)
                put(KEY_AUTO_FIT, customGrid.autoFit)
                put(KEY_PAGE_COUNT, customGrid.pageCount)
            }
        )
    }.toString()

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val KEY_CAROUSEL = "carousel"
        private const val KEY_AUTO_GRID = "autoGrid"
        private const val KEY_CUSTOM_GRID = "customGrid"
        private const val KEY_ROW_ALIGNMENT = "rowAlignment"
        private const val KEY_FOCUS_POSITION = "focusPosition"
        private const val KEY_INVERTED = "inverted"
        private const val KEY_RESTING_SCALE = "restingScale"
        private const val KEY_NEIGHBOUR_PUSH = "neighbourPush"
        private const val KEY_PLATFORM_BADGE = "showPlatformBadge"
        private const val KEY_SCROLL_AXIS = "scrollAxis"
        private const val KEY_LANE_COUNT = "laneCount"
        private const val KEY_SHOW_TITLES = "showTitles"
        private const val KEY_AUTO_ADD = "autoAdd"
        private const val KEY_EMPTY_SLOTS = "showEmptySlots"
        private const val KEY_PERSIST_PAGES = "persistBlankPages"
        private const val KEY_AUTO_FIT = "autoFit"
        private const val KEY_PAGE_COUNT = "pageCount"

        /**
         * Reads what it can and defaults the rest. A layout the user curated is not thrown away
         * because one field arrived malformed or a newer build wrote a key this one does not know,
         * so every field is recovered independently rather than the whole object being discarded.
         */
        fun fromJson(raw: String?): HomeLayoutSettings {
            if (raw.isNullOrBlank()) return HomeLayoutSettings()
            val root = runCatching { JSONObject(raw) }.getOrNull() ?: return HomeLayoutSettings()
            val defaults = HomeLayoutSettings()
            val carousel = root.optJSONObject(KEY_CAROUSEL)
            val autoGrid = root.optJSONObject(KEY_AUTO_GRID)
            val customGrid = root.optJSONObject(KEY_CUSTOM_GRID)
            return HomeLayoutSettings(
                selected = enumOrDefault(root.optString(KEY_SELECTED), defaults.selected),
                carousel = CarouselConfig(
                    rowAlignment = enumOrDefault(
                        carousel?.optString(KEY_ROW_ALIGNMENT),
                        defaults.carousel.rowAlignment
                    ),
                    focusPosition = enumOrDefault(
                        carousel?.optString(KEY_FOCUS_POSITION),
                        defaults.carousel.focusPosition
                    ),
                    inverted = carousel?.optBoolean(KEY_INVERTED, defaults.carousel.inverted)
                        ?: defaults.carousel.inverted,
                    restingScale = carousel?.optDouble(KEY_RESTING_SCALE)?.toFloat()
                        ?.takeIf { it.isFinite() && it > 0f }
                        ?.coerceIn(MIN_RESTING_SCALE, 1f) ?: defaults.carousel.restingScale,
                    neighbourPush = carousel?.optBoolean(KEY_NEIGHBOUR_PUSH, defaults.carousel.neighbourPush)
                        ?: defaults.carousel.neighbourPush,
                    showPlatformBadge = carousel?.optBoolean(KEY_PLATFORM_BADGE, defaults.carousel.showPlatformBadge)
                        ?: defaults.carousel.showPlatformBadge
                ),
                autoGrid = AutoGridConfig(
                    scrollAxis = enumOrDefault(
                        autoGrid?.optString(KEY_SCROLL_AXIS),
                        defaults.autoGrid.scrollAxis
                    ),
                    laneCount = autoGrid?.optInt(KEY_LANE_COUNT, defaults.autoGrid.laneCount)
                        ?.coerceIn(MIN_LANE_COUNT, MAX_LANE_COUNT) ?: defaults.autoGrid.laneCount,
                    showTitles = autoGrid?.optBoolean(KEY_SHOW_TITLES, defaults.autoGrid.showTitles)
                        ?: defaults.autoGrid.showTitles
                ),
                customGrid = CustomGridConfig(
                    laneCount = customGrid?.optInt(KEY_LANE_COUNT, defaults.customGrid.laneCount)
                        ?.coerceIn(MIN_LANE_COUNT, MAX_LANE_COUNT) ?: defaults.customGrid.laneCount,
                    autoAdd = enumOrDefault(
                        customGrid?.optString(KEY_AUTO_ADD),
                        defaults.customGrid.autoAdd
                    ),
                    showEmptySlots = customGrid?.optBoolean(
                        KEY_EMPTY_SLOTS,
                        defaults.customGrid.showEmptySlots
                    ) ?: defaults.customGrid.showEmptySlots,
                    persistBlankPages = customGrid?.optBoolean(
                        KEY_PERSIST_PAGES,
                        defaults.customGrid.persistBlankPages
                    ) ?: defaults.customGrid.persistBlankPages,
                    autoFit = customGrid?.optBoolean(KEY_AUTO_FIT, defaults.customGrid.autoFit)
                        ?: defaults.customGrid.autoFit,
                    pageCount = customGrid?.optInt(KEY_PAGE_COUNT, defaults.customGrid.pageCount)
                        ?.coerceAtLeast(0) ?: defaults.customGrid.pageCount
                )
            )
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
            raw?.takeIf { it.isNotBlank() }
                ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
                ?: fallback
    }
}
