package com.nendo.argosy.domain.model

import org.json.JSONObject

enum class HomeLayoutKind { CAROUSEL, AUTO_GRID, CUSTOM_GRID, COLUMNS }

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
    val showTitles: Boolean = true,
    val showAllGames: Boolean = false
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
 * One axis of the custom grid. [Fill] takes as many square cells as the screen fits; [Scroll] is an
 * unbounded axis the grid scrolls along. At most one axis of a config is anything but [Fixed].
 */
sealed interface GridAxis {
    data class Fixed(val count: Int) : GridAxis
    data object Fill : GridAxis
    data object Scroll : GridAxis
}

val GridAxis.isFixed: Boolean get() = this is GridAxis.Fixed

const val MIN_GRID_AXIS_COUNT = 2

const val MAX_GRID_AXIS_COUNT = 12

/**
 * The values the settings stepper walks, left to right.
 */
val GRID_AXIS_STEPS: List<GridAxis> =
    listOf(GridAxis.Scroll, GridAxis.Fill) +
        (MIN_GRID_AXIS_COUNT..MAX_GRID_AXIS_COUNT).map { GridAxis.Fixed(it) }

val GridAxis.stepIndex: Int
    get() = GRID_AXIS_STEPS.indexOf(this).coerceAtLeast(0)

/**
 * The neighbouring stepper value in [direction], clamped at either end.
 */
fun GridAxis.stepped(direction: Int): GridAxis =
    GRID_AXIS_STEPS[(stepIndex + direction).coerceIn(0, GRID_AXIS_STEPS.lastIndex)]

fun GridAxis.isAtStepBound(direction: Int): Boolean = direction != 0 && stepped(direction) == this

/**
 * How many columns and rows a page actually has on one screen.
 */
data class CustomGridShape(val columns: Int, val rows: Int)

/**
 * A shape a screen measured, with the axes it was measured for. A shape measured for other axes
 * says nothing about the current config.
 */
data class ResolvedGridShape(
    val columns: GridAxis,
    val rows: GridAxis,
    val shape: CustomGridShape,
    val portrait: Boolean = false
)

/**
 * Which set of tiles a custom grid shows. Paged and scrolling grids hold separate tiles; the name is
 * the token stored on each tile row.
 */
enum class HomeGridKind { PAGED, SCROLL }

fun gridKindFor(scrollAxis: HomeScrollAxis?): HomeGridKind =
    if (scrollAxis == null) HomeGridKind.PAGED else HomeGridKind.SCROLL

/**
 * Where a grid puts new tiles: a page of [shape] when [scrollAxis] is null, else one canvas unbounded
 * along [scrollAxis] with [lanes] cells across it.
 */
data class CustomGridLayout(
    val shape: CustomGridShape,
    val scrollAxis: HomeScrollAxis? = null
) {
    val kind: HomeGridKind get() = gridKindFor(scrollAxis)

    val lanes: Int
        get() = when (scrollAxis) {
            HomeScrollAxis.HORIZONTAL -> shape.rows
            else -> shape.columns
        }

    val scrollArrangement: ScrollArrangement?
        get() = scrollAxis?.let { ScrollArrangement(it, lanes) }

    /**
     * True when both layouts scroll and differ in scroll axis or lane count. Entering or leaving
     * scroll mode is never a reflow.
     */
    fun needsScrollReflowTo(next: CustomGridLayout): Boolean =
        scrollAxis != null && next.scrollAxis != null &&
            (scrollAxis != next.scrollAxis || lanes != next.lanes)
}

/**
 * The scroll axis and lane count the stored scrolling tiles are arranged for. It outlives a switch to
 * the paged grid, so returning to scroll mode in a different shape still reflows from the right one.
 */
data class ScrollArrangement(val axis: HomeScrollAxis, val lanes: Int) {
    val layout: CustomGridLayout
        get() = CustomGridLayout(CustomGridShape(columns = lanes, rows = lanes), axis)
}

/**
 * @param lanesOnShortEdge marks axes migrated from a single lane count, which counted lanes along the
 *   screen's short edge. [columns] and [rows] hold the landscape reading until [orientedTo] settles it.
 */
data class CustomGridConfig(
    val columns: GridAxis = GridAxis.Fill,
    val rows: GridAxis = GridAxis.Fixed(DEFAULT_LANE_COUNT),
    val autoAdd: HomeTileAutoAdd = HomeTileAutoAdd.OFF,
    val showEmptySlots: Boolean = true,
    val persistBlankPages: Boolean = false,
    val autoFit: Boolean = true,
    val pageCount: Int = 0,
    val scrollArrangement: ScrollArrangement? = null,
    val lanesOnShortEdge: Boolean = false
) : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.CUSTOM_GRID

    val scrollAxis: HomeScrollAxis?
        get() = when {
            rows is GridAxis.Scroll -> HomeScrollAxis.VERTICAL
            columns is GridAxis.Scroll -> HomeScrollAxis.HORIZONTAL
            else -> null
        }

    val gridKind: HomeGridKind get() = gridKindFor(scrollAxis)

    fun layoutFor(resolved: ResolvedGridShape?): CustomGridLayout =
        CustomGridLayout(shapeFor(resolved), scrollAxis)

    fun shapeFor(resolved: ResolvedGridShape?): CustomGridShape {
        val measured = resolved?.takeIf { it.columns == columns && it.rows == rows }?.shape
        if (measured != null) return measured
        val fixedColumns = (columns as? GridAxis.Fixed)?.count
        val fixedRows = (rows as? GridAxis.Fixed)?.count
        return CustomGridShape(
            columns = fixedColumns ?: fixedRows ?: DEFAULT_LANE_COUNT,
            rows = fixedRows ?: fixedColumns ?: DEFAULT_LANE_COUNT
        )
    }

    fun withColumns(axis: GridAxis, resolved: CustomGridShape): CustomGridConfig =
        if (!axis.isFixed && !rows.isFixed) {
            copy(
                columns = axis,
                rows = GridAxis.Fixed(resolved.rows.coerceIn(MIN_GRID_AXIS_COUNT, MAX_GRID_AXIS_COUNT)),
                lanesOnShortEdge = false
            )
        } else {
            copy(columns = axis, lanesOnShortEdge = false)
        }

    fun withRows(axis: GridAxis, resolved: CustomGridShape): CustomGridConfig =
        if (!axis.isFixed && !columns.isFixed) {
            copy(
                rows = axis,
                columns = GridAxis.Fixed(resolved.columns.coerceIn(MIN_GRID_AXIS_COUNT, MAX_GRID_AXIS_COUNT)),
                lanesOnShortEdge = false
            )
        } else {
            copy(rows = axis, lanesOnShortEdge = false)
        }

    /**
     * Settles a migrated lane count onto the short edge of a screen that is [portrait] or not.
     * Returns this config unchanged when it carries no migrated lane count.
     */
    fun orientedTo(portrait: Boolean): CustomGridConfig = when {
        !lanesOnShortEdge -> this
        portrait -> copy(columns = rows, rows = columns, lanesOnShortEdge = false)
        else -> copy(lanesOnShortEdge = false)
    }
}

data object ColumnsConfig : HomeLayoutConfig {
    override val kind: HomeLayoutKind get() = HomeLayoutKind.COLUMNS
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
 * Which of the optional media rows home offers. These are not a property of any one layout -- a row
 * is either on the home surface or it is not -- so they sit beside the layout choice rather than
 * inside one of the per-layout configs.
 *
 * Nothing here shows anything without a signed-in media account, so the defaults cost someone with
 * no media server nothing either way.
 *
 * [showLibraries] governs the whole run of per-library rows rather than one row each, matching the
 * platform rows it sits beside: a platform is a row because it has games, not because it was
 * switched on, and a library is a row because the server has it.
 *
 * [showContinueWatching] defaults off. The server's resume list accumulates every abandoned item for
 * as long as the account exists, so the row is correct and still mostly noise; it stays available for
 * anyone who wants it and stays out of the way of everyone who does not.
 */
data class HomeRailSettings(
    val showContinueWatching: Boolean = false,
    val showNextUp: Boolean = true,
    val showLibraries: Boolean = true
)

/**
 * The selected layout plus every layout's settings, so switching back and forth does not discard
 * what was configured for the layout being left.
 */
data class HomeLayoutSettings(
    val selected: HomeLayoutKind = HomeLayoutKind.CAROUSEL,
    val carousel: CarouselConfig = CarouselConfig(),
    val autoGrid: AutoGridConfig = AutoGridConfig(),
    val customGrid: CustomGridConfig = CustomGridConfig(),
    val columns: ColumnsConfig = ColumnsConfig,
    val rails: HomeRailSettings = HomeRailSettings(),
    val boxArt3d: Boolean = false
) {
    val active: HomeLayoutConfig
        get() = when (selected) {
            HomeLayoutKind.CAROUSEL -> carousel
            HomeLayoutKind.AUTO_GRID -> autoGrid
            HomeLayoutKind.CUSTOM_GRID -> customGrid
            HomeLayoutKind.COLUMNS -> columns
        }

    val showsEveryGame: Boolean
        get() = selected == HomeLayoutKind.AUTO_GRID && autoGrid.showAllGames

    fun toJson(): String = JSONObject().apply {
        put(KEY_SELECTED, selected.name)
        put(KEY_BOX_ART_3D, boxArt3d)
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
                put(KEY_SHOW_ALL_GAMES, autoGrid.showAllGames)
            }
        )
        put(
            KEY_CUSTOM_GRID,
            JSONObject().apply {
                put(KEY_COLUMNS, customGrid.columns.toJsonValue())
                put(KEY_ROWS, customGrid.rows.toJsonValue())
                put(KEY_AUTO_ADD, customGrid.autoAdd.name)
                put(KEY_EMPTY_SLOTS, customGrid.showEmptySlots)
                put(KEY_PERSIST_PAGES, customGrid.persistBlankPages)
                put(KEY_AUTO_FIT, customGrid.autoFit)
                put(KEY_PAGE_COUNT, customGrid.pageCount)
                if (customGrid.lanesOnShortEdge) put(KEY_LANES_ON_SHORT_EDGE, true)
                customGrid.scrollArrangement?.let { arrangement ->
                    put(
                        KEY_SCROLL_ARRANGEMENT,
                        JSONObject().apply {
                            put(KEY_SCROLL_AXIS, arrangement.axis.name)
                            put(KEY_LANE_COUNT, arrangement.lanes)
                        }
                    )
                }
            }
        )
        put(KEY_COLUMNS_LAYOUT, JSONObject())
        put(
            KEY_RAILS,
            JSONObject().apply {
                put(KEY_CONTINUE_WATCHING, rails.showContinueWatching)
                put(KEY_NEXT_UP, rails.showNextUp)
                put(KEY_LIBRARIES, rails.showLibraries)
            }
        )
    }.toString()

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val KEY_CAROUSEL = "carousel"
        private const val KEY_AUTO_GRID = "autoGrid"
        private const val KEY_CUSTOM_GRID = "customGrid"
        private const val KEY_COLUMNS_LAYOUT = "columns"
        private const val KEY_ROW_ALIGNMENT = "rowAlignment"
        private const val KEY_FOCUS_POSITION = "focusPosition"
        private const val KEY_INVERTED = "inverted"
        private const val KEY_RESTING_SCALE = "restingScale"
        private const val KEY_NEIGHBOUR_PUSH = "neighbourPush"
        private const val KEY_PLATFORM_BADGE = "showPlatformBadge"
        private const val KEY_SCROLL_AXIS = "scrollAxis"
        private const val KEY_LANE_COUNT = "laneCount"
        private const val KEY_SHOW_TITLES = "showTitles"
        private const val KEY_SHOW_ALL_GAMES = "showAllGames"
        private const val KEY_LEGACY_USE_BOX_ART = "useBoxArt"
        private const val KEY_BOX_ART_3D = "boxArt3d"
        private const val KEY_AUTO_ADD = "autoAdd"
        private const val KEY_EMPTY_SLOTS = "showEmptySlots"
        private const val KEY_PERSIST_PAGES = "persistBlankPages"
        private const val KEY_AUTO_FIT = "autoFit"
        private const val KEY_PAGE_COUNT = "pageCount"
        private const val KEY_COLUMNS = "columns"
        private const val KEY_ROWS = "rows"
        private const val KEY_SCROLL_ARRANGEMENT = "scrollArrangement"
        private const val KEY_LANES_ON_SHORT_EDGE = "lanesOnShortEdge"
        private const val TOKEN_FILL = "FILL"
        private const val TOKEN_SCROLL = "SCROLL"
        private const val KEY_RAILS = "rails"
        private const val KEY_CONTINUE_WATCHING = "showContinueWatching"
        private const val KEY_NEXT_UP = "showNextUp"
        private const val KEY_LIBRARIES = "showLibraries"

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
            val rails = root.optJSONObject(KEY_RAILS)
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
                        ?: defaults.autoGrid.showTitles,
                    showAllGames = autoGrid?.optBoolean(
                        KEY_SHOW_ALL_GAMES,
                        defaults.autoGrid.showAllGames
                    ) ?: defaults.autoGrid.showAllGames
                ),
                customGrid = customGridAxes(customGrid, defaults.customGrid).copy(
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
                        ?.coerceAtLeast(0) ?: defaults.customGrid.pageCount,
                    scrollArrangement = scrollArrangementOrNull(customGrid?.optJSONObject(KEY_SCROLL_ARRANGEMENT))
                ),
                rails = HomeRailSettings(
                    showContinueWatching = rails?.optBoolean(
                        KEY_CONTINUE_WATCHING,
                        defaults.rails.showContinueWatching
                    ) ?: defaults.rails.showContinueWatching,
                    showNextUp = rails?.optBoolean(KEY_NEXT_UP, defaults.rails.showNextUp)
                        ?: defaults.rails.showNextUp,
                    showLibraries = rails?.optBoolean(KEY_LIBRARIES, defaults.rails.showLibraries)
                        ?: defaults.rails.showLibraries
                ),
                boxArt3d = if (root.has(KEY_BOX_ART_3D)) {
                    root.optBoolean(KEY_BOX_ART_3D, defaults.boxArt3d)
                } else {
                    carousel?.optBoolean(KEY_LEGACY_USE_BOX_ART) == true ||
                        autoGrid?.optBoolean(KEY_LEGACY_USE_BOX_ART) == true
                }
            )
        }

        private fun customGridAxes(json: JSONObject?, defaults: CustomGridConfig): CustomGridConfig {
            val columns = axisOrNull(json, KEY_COLUMNS)
            val rows = axisOrNull(json, KEY_ROWS)
            val legacyLanes = json?.takeIf { columns == null && rows == null && it.has(KEY_LANE_COUNT) }
                ?.optInt(KEY_LANE_COUNT, DEFAULT_LANE_COUNT)
                ?.coerceIn(MIN_GRID_AXIS_COUNT, MAX_GRID_AXIS_COUNT)
            val resolvedColumns = columns ?: if (legacyLanes != null) GridAxis.Fill else defaults.columns
            val resolvedRows = rows ?: legacyLanes?.let { GridAxis.Fixed(it) } ?: defaults.rows
            val lanesOnShortEdge = legacyLanes != null ||
                json?.optBoolean(KEY_LANES_ON_SHORT_EDGE, false) == true
            return if (!resolvedColumns.isFixed && !resolvedRows.isFixed) {
                defaults.copy(columns = resolvedColumns, rows = GridAxis.Fixed(DEFAULT_LANE_COUNT))
            } else {
                defaults.copy(columns = resolvedColumns, rows = resolvedRows, lanesOnShortEdge = lanesOnShortEdge)
            }
        }

        private fun scrollArrangementOrNull(json: JSONObject?): ScrollArrangement? {
            val axis = json?.optString(KEY_SCROLL_AXIS)
                ?.let { name -> HomeScrollAxis.entries.firstOrNull { it.name == name } }
                ?: return null
            val lanes = json.optInt(KEY_LANE_COUNT, 0).takeIf { it > 0 } ?: return null
            return ScrollArrangement(axis, lanes)
        }

        private fun axisOrNull(json: JSONObject?, key: String): GridAxis? {
            if (json == null || !json.has(key)) return null
            return when (val raw = json.opt(key)) {
                is Number -> GridAxis.Fixed(raw.toInt().coerceIn(MIN_GRID_AXIS_COUNT, MAX_GRID_AXIS_COUNT))
                TOKEN_FILL -> GridAxis.Fill
                TOKEN_SCROLL -> GridAxis.Scroll
                else -> raw?.toString()?.toIntOrNull()
                    ?.let { GridAxis.Fixed(it.coerceIn(MIN_GRID_AXIS_COUNT, MAX_GRID_AXIS_COUNT)) }
            }
        }

        private fun GridAxis.toJsonValue(): Any = when (this) {
            is GridAxis.Fixed -> count
            GridAxis.Fill -> TOKEN_FILL
            GridAxis.Scroll -> TOKEN_SCROLL
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, fallback: T): T =
            raw?.takeIf { it.isNotBlank() }
                ?.let { name -> enumValues<T>().firstOrNull { it.name == name } }
                ?: fallback
    }
}
