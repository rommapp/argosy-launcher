package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class HomeLayoutKind { CAROUSEL, AUTO_GRID, CUSTOM_GRID }

enum class HomeRowAlignment { TOP, CENTER, BOTTOM }

enum class HomeFocusPosition { LEADING, CENTER }

enum class HomeScrollAxis { VERTICAL, HORIZONTAL }

enum class HomeSectionStyle { HEADINGS, FLAT }

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

data class CarouselConfig(
    val rowAlignment: HomeRowAlignment = HomeRowAlignment.BOTTOM,
    val focusPosition: HomeFocusPosition = HomeFocusPosition.LEADING,
    val inverted: Boolean = false,
    val focusScale: Float = 1.8f,
    val restingScale: Float = 0.9f,
    val neighbourPush: Boolean = true,
    val showPlatformBadge: Boolean = true
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.CAROUSEL
}

/**
 * [laneCount] counts the lanes across the axis you are not scrolling along, so it reads as columns
 * when scrolling vertically and as rows when scrolling horizontally. Storing the one number keeps
 * the choice meaningful when the axis is flipped, rather than leaving a stale count behind.
 */
data class AutoGridConfig(
    val scrollAxis: HomeScrollAxis = HomeScrollAxis.VERTICAL,
    val laneCount: Int = DEFAULT_LANE_COUNT,
    val sectionStyle: HomeSectionStyle = HomeSectionStyle.HEADINGS,
    val showTitles: Boolean = true
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.AUTO_GRID
}

/**
 * A page is [laneCount] lanes across its short side; how many cells run along the long side follows
 * from the display's shape, so one curated page keeps its proportions on a tall handheld and a wide
 * television instead of being authored for one and stretched on the other.
 */
data class CustomGridConfig(
    val laneCount: Int = DEFAULT_LANE_COUNT
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.CUSTOM_GRID
}

internal const val DEFAULT_LANE_COUNT = 3
internal const val MIN_LANE_COUNT = 2
internal const val MAX_LANE_COUNT = 8

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
                put(KEY_FOCUS_SCALE, carousel.focusScale.toDouble())
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
                put(KEY_SECTION_STYLE, autoGrid.sectionStyle.name)
                put(KEY_SHOW_TITLES, autoGrid.showTitles)
            }
        )
        put(
            KEY_CUSTOM_GRID,
            JSONObject().apply {
                put(KEY_LANE_COUNT, customGrid.laneCount)
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
        private const val KEY_FOCUS_SCALE = "focusScale"
        private const val KEY_RESTING_SCALE = "restingScale"
        private const val KEY_NEIGHBOUR_PUSH = "neighbourPush"
        private const val KEY_PLATFORM_BADGE = "showPlatformBadge"
        private const val KEY_SCROLL_AXIS = "scrollAxis"
        private const val KEY_LANE_COUNT = "laneCount"
        private const val KEY_SECTION_STYLE = "sectionStyle"
        private const val KEY_SHOW_TITLES = "showTitles"

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
                    focusScale = carousel?.optDouble(KEY_FOCUS_SCALE)?.toFloat()
                        ?.takeIf { it.isFinite() && it > 0f } ?: defaults.carousel.focusScale,
                    restingScale = carousel?.optDouble(KEY_RESTING_SCALE)?.toFloat()
                        ?.takeIf { it.isFinite() && it > 0f } ?: defaults.carousel.restingScale,
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
                        ?.takeIf { it in MIN_LANE_COUNT..MAX_LANE_COUNT } ?: defaults.autoGrid.laneCount,
                    sectionStyle = enumOrDefault(
                        autoGrid?.optString(KEY_SECTION_STYLE),
                        defaults.autoGrid.sectionStyle
                    ),
                    showTitles = autoGrid?.optBoolean(KEY_SHOW_TITLES, defaults.autoGrid.showTitles)
                        ?: defaults.autoGrid.showTitles
                ),
                customGrid = CustomGridConfig(
                    laneCount = customGrid?.optInt(KEY_LANE_COUNT, defaults.customGrid.laneCount)
                        ?.takeIf { it in MIN_LANE_COUNT..MAX_LANE_COUNT } ?: defaults.customGrid.laneCount
                )
            )
        }

        private const val MIN_GRID_SPAN = 2
        private const val MAX_GRID_SPAN = 8

        private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
            raw?.takeIf { it.isNotBlank() }
                ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
                ?: fallback
    }
}
