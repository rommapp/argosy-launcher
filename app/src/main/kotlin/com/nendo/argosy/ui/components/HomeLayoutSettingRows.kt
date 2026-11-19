package com.nendo.argosy.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import androidx.compose.ui.Modifier
import com.nendo.argosy.domain.model.CustomGridShape
import com.nendo.argosy.domain.model.GRID_AXIS_STEPS
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.isAtStepBound
import com.nendo.argosy.domain.model.stepIndex
import com.nendo.argosy.domain.model.stepped
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeGridKind
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.MAX_LANE_COUNT
import com.nendo.argosy.domain.model.MIN_LANE_COUNT
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTileAutoAdd
import com.nendo.argosy.ui.primitives.SegmentedControl
import kotlin.math.roundToInt

/**
 * Every adjustable home field, so a caller routes one exhaustive `when` instead of one per layout.
 *
 * Most entries belong to a single layout and are listed by [homeLayoutFieldsFor]. The media entries
 * are the exception: they say which rows home offers rather than how one layout draws, so they are
 * listed by [homeRailFields] and a host places them among its own content rows.
 */
enum class HomeLayoutSettingField {
    ROW_ALIGNMENT,
    FOCUS_POSITION,
    INVERTED,
    RESTING_SCALE,
    NEIGHBOUR_PUSH,
    PLATFORM_BADGE,
    SCROLL_AXIS,
    AUTO_GRID_LANES,
    SHOW_TITLES,
    AUTO_GRID_SHOW_ALL,
    CUSTOM_GRID_COLUMNS,
    CUSTOM_GRID_ROWS,
    CUSTOM_GRID_AUTO_ADD,
    CUSTOM_GRID_EMPTY_SLOTS,
    CUSTOM_GRID_PERSIST_PAGES,
    CUSTOM_GRID_AUTO_FIT,
    RAIL_MEDIA_LIBRARIES,
    RAIL_CONTINUE_WATCHING,
    RAIL_NEXT_UP
}

/**
 * The media row toggles, in render order. Kept apart from the per-layout lists because which rows
 * home offers is not a layout setting; a host that draws rows at all draws these regardless of which
 * layout is selected.
 */
fun homeRailFields(): List<HomeLayoutSettingField> = listOf(
    HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES,
    HomeLayoutSettingField.RAIL_CONTINUE_WATCHING,
    HomeLayoutSettingField.RAIL_NEXT_UP
)

/**
 * The row title for this field. [HomeLayoutSettingField.AUTO_GRID_LANES] names its columns
 * form here; its row title follows the scroll axis and reads rows when the grid scrolls sideways.
 */
@get:StringRes
val HomeLayoutSettingField.titleRes: Int
    get() = when (this) {
        HomeLayoutSettingField.ROW_ALIGNMENT -> R.string.ui_home_layout_row_alignment
        HomeLayoutSettingField.FOCUS_POSITION -> R.string.ui_home_layout_focus_position
        HomeLayoutSettingField.INVERTED -> R.string.ui_home_layout_inverted
        HomeLayoutSettingField.RESTING_SCALE -> R.string.ui_home_layout_resting_scale
        HomeLayoutSettingField.NEIGHBOUR_PUSH -> R.string.ui_home_layout_neighbour_push
        HomeLayoutSettingField.PLATFORM_BADGE -> R.string.ui_home_layout_platform_badge
        HomeLayoutSettingField.SCROLL_AXIS -> R.string.ui_home_layout_scroll_axis
        HomeLayoutSettingField.AUTO_GRID_LANES -> R.string.ui_home_layout_lanes_columns
        HomeLayoutSettingField.SHOW_TITLES -> R.string.ui_home_layout_show_titles
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL -> R.string.ui_home_layout_show_all_games
        HomeLayoutSettingField.CUSTOM_GRID_COLUMNS -> R.string.ui_home_layout_custom_grid_columns
        HomeLayoutSettingField.CUSTOM_GRID_ROWS -> R.string.ui_home_layout_custom_grid_rows
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD -> R.string.ui_home_layout_custom_grid_auto_add
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS -> R.string.ui_home_layout_custom_grid_empty_slots
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES -> R.string.ui_home_layout_custom_grid_persist_pages
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT -> R.string.ui_home_layout_custom_grid_auto_fit
        HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES -> R.string.ui_home_layout_rail_libraries
        HomeLayoutSettingField.RAIL_CONTINUE_WATCHING -> R.string.ui_home_layout_rail_continue_watching
        HomeLayoutSettingField.RAIL_NEXT_UP -> R.string.ui_home_layout_rail_next_up
    }

private const val SCALE_PERCENT_STEP = 10
private const val RESTING_SCALE_MIN_PERCENT = 50
private const val RESTING_SCALE_MAX_PERCENT = 100
private const val PERCENT = 100f

/**
 * The fields [kind] owns, in render order. The host lays these out among its own rows and drives
 * focus against them, so what gamepad focus reaches and what is drawn cannot drift apart.
 */
fun homeLayoutFieldsFor(kind: HomeLayoutKind): List<HomeLayoutSettingField> = when (kind) {
    HomeLayoutKind.CAROUSEL -> listOf(
        HomeLayoutSettingField.ROW_ALIGNMENT,
        HomeLayoutSettingField.FOCUS_POSITION,
        HomeLayoutSettingField.RESTING_SCALE,
        HomeLayoutSettingField.NEIGHBOUR_PUSH,
        HomeLayoutSettingField.PLATFORM_BADGE,
        HomeLayoutSettingField.INVERTED
    )
    HomeLayoutKind.AUTO_GRID -> listOf(
        HomeLayoutSettingField.SCROLL_AXIS,
        HomeLayoutSettingField.AUTO_GRID_LANES,
        HomeLayoutSettingField.SHOW_TITLES,
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL
    )
    HomeLayoutKind.CUSTOM_GRID -> listOf(
        HomeLayoutSettingField.CUSTOM_GRID_COLUMNS,
        HomeLayoutSettingField.CUSTOM_GRID_ROWS,
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS,
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT,
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES,
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD
    )
    HomeLayoutKind.COLUMNS -> emptyList()
}

/**
 * Whether [field] is offered for [settings]: one of the selected layout's fields, less the page rows
 * a scrolling custom grid has no pages for.
 */
fun isHomeLayoutFieldShown(settings: HomeLayoutSettings, field: HomeLayoutSettingField): Boolean =
    field in homeLayoutFieldsFor(settings.selected) &&
        !(field == HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES &&
            settings.customGrid.gridKind == HomeGridKind.SCROLL)

/**
 * Left/right adjustment for [field]. Booleans follow the house rule that left is off and right is
 * on; enums and numbers stop at either end. [customGridShape] is the custom grid's current shape, which an axis
 * locks to when the other axis becomes non-fixed.
 */
fun adjustHomeLayoutField(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    direction: Int,
    customGridShape: CustomGridShape
): HomeLayoutSettings = when (field) {
        HomeLayoutSettingField.ROW_ALIGNMENT ->
            settings.copy(carousel = settings.carousel.copy(rowAlignment = step(settings.carousel.rowAlignment, direction)))
        HomeLayoutSettingField.FOCUS_POSITION ->
            settings.copy(carousel = settings.carousel.copy(focusPosition = step(settings.carousel.focusPosition, direction)))
        HomeLayoutSettingField.INVERTED ->
            settings.copy(carousel = settings.carousel.copy(inverted = direction > 0))
        HomeLayoutSettingField.RESTING_SCALE ->
            settings.copy(
                carousel = settings.carousel.copy(
                    restingScale = stepScale(
                        settings.carousel.restingScale,
                        direction * SCALE_PERCENT_STEP,
                        RESTING_SCALE_MIN_PERCENT,
                        RESTING_SCALE_MAX_PERCENT
                    )
                )
            )
        HomeLayoutSettingField.NEIGHBOUR_PUSH ->
            settings.copy(carousel = settings.carousel.copy(neighbourPush = direction > 0))
        HomeLayoutSettingField.PLATFORM_BADGE ->
            settings.copy(carousel = settings.carousel.copy(showPlatformBadge = direction > 0))
        HomeLayoutSettingField.SCROLL_AXIS ->
            settings.copy(autoGrid = settings.autoGrid.copy(scrollAxis = step(settings.autoGrid.scrollAxis, direction)))
        HomeLayoutSettingField.SHOW_TITLES ->
            settings.copy(autoGrid = settings.autoGrid.copy(showTitles = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL ->
            settings.copy(autoGrid = settings.autoGrid.copy(showAllGames = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_LANES ->
            settings.copy(autoGrid = settings.autoGrid.copy(laneCount = stepSpan(settings.autoGrid.laneCount, direction)))
        HomeLayoutSettingField.CUSTOM_GRID_COLUMNS ->
            settings.copy(
                customGrid = settings.customGrid.withColumns(
                    settings.customGrid.columns.stepped(direction),
                    customGridShape
                )
            )
        HomeLayoutSettingField.CUSTOM_GRID_ROWS ->
            settings.copy(
                customGrid = settings.customGrid.withRows(
                    settings.customGrid.rows.stepped(direction),
                    customGridShape
                )
            )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD ->
            settings.copy(customGrid = settings.customGrid.copy(autoAdd = step(settings.customGrid.autoAdd, direction)))
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS ->
            settings.copy(customGrid = settings.customGrid.copy(showEmptySlots = direction > 0))
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES ->
            settings.copy(customGrid = settings.customGrid.copy(persistBlankPages = direction > 0))
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT ->
            settings.copy(customGrid = settings.customGrid.copy(autoFit = direction > 0))
        HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES ->
            settings.copy(rails = settings.rails.copy(showLibraries = direction > 0))
        HomeLayoutSettingField.RAIL_CONTINUE_WATCHING ->
            settings.copy(rails = settings.rails.copy(showContinueWatching = direction > 0))
        HomeLayoutSettingField.RAIL_NEXT_UP ->
            settings.copy(rails = settings.rails.copy(showNextUp = direction > 0))
}

fun isHomeLayoutFieldAtBound(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    direction: Int
): Boolean = when (field) {
    HomeLayoutSettingField.CUSTOM_GRID_COLUMNS -> settings.customGrid.columns.isAtStepBound(direction)
    HomeLayoutSettingField.CUSTOM_GRID_ROWS -> settings.customGrid.rows.isAtStepBound(direction)
    HomeLayoutSettingField.ROW_ALIGNMENT ->
        settings.carousel.rowAlignment.isAtEnd(direction, HomeRowAlignment.entries.size)
    HomeLayoutSettingField.FOCUS_POSITION ->
        settings.carousel.focusPosition.isAtEnd(direction, HomeFocusPosition.entries.size)
    HomeLayoutSettingField.SCROLL_AXIS ->
        settings.autoGrid.scrollAxis.isAtEnd(direction, HomeScrollAxis.entries.size)
    HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD ->
        settings.customGrid.autoAdd.isAtEnd(direction, HomeTileAutoAdd.entries.size)
    else -> false
}

/**
 * Confirm handling for [field]. Toggles flip; everything else is unchanged, because confirm never
 * adjusts a value in this menu system.
 */
fun toggleHomeLayoutField(settings: HomeLayoutSettings, field: HomeLayoutSettingField): HomeLayoutSettings {
    return when (field) {
        HomeLayoutSettingField.INVERTED ->
            settings.copy(carousel = settings.carousel.copy(inverted = !settings.carousel.inverted))
        HomeLayoutSettingField.NEIGHBOUR_PUSH ->
            settings.copy(carousel = settings.carousel.copy(neighbourPush = !settings.carousel.neighbourPush))
        HomeLayoutSettingField.PLATFORM_BADGE ->
            settings.copy(carousel = settings.carousel.copy(showPlatformBadge = !settings.carousel.showPlatformBadge))
        HomeLayoutSettingField.SHOW_TITLES ->
            settings.copy(autoGrid = settings.autoGrid.copy(showTitles = !settings.autoGrid.showTitles))
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL ->
            settings.copy(
                autoGrid = settings.autoGrid.copy(showAllGames = !settings.autoGrid.showAllGames)
            )
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS ->
            settings.copy(
                customGrid = settings.customGrid.copy(
                    showEmptySlots = !settings.customGrid.showEmptySlots
                )
            )
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES ->
            settings.copy(
                customGrid = settings.customGrid.copy(
                    persistBlankPages = !settings.customGrid.persistBlankPages
                )
            )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT ->
            settings.copy(customGrid = settings.customGrid.copy(autoFit = !settings.customGrid.autoFit))
        HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES ->
            settings.copy(rails = settings.rails.copy(showLibraries = !settings.rails.showLibraries))
        HomeLayoutSettingField.RAIL_CONTINUE_WATCHING ->
            settings.copy(
                rails = settings.rails.copy(
                    showContinueWatching = !settings.rails.showContinueWatching
                )
            )
        HomeLayoutSettingField.RAIL_NEXT_UP ->
            settings.copy(rails = settings.rails.copy(showNextUp = !settings.rails.showNextUp))
        else -> settings
    }
}

/**
 * The layout selector: a segmented track with one segment per layout. Kept separate from the picker
 * so a settings pane can place it among its own rows with the preview sitting directly above it.
 */
@Composable
fun HomeLayoutSelectorRow(
    selected: HomeLayoutKind,
    isFocused: Boolean,
    onSelect: (HomeLayoutKind) -> Unit
) {
    SegmentedControl(
        options = HomeLayoutKind.entries.map { layoutLabel(it) },
        selectedIndex = selected.ordinal,
        onSelect = { onSelect(HomeLayoutKind.entries[it]) },
        focused = isFocused,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * One layout setting rendered with the standard preference controls, so a layout field looks and
 * behaves like every other row wherever it is hosted.
 */
@Composable
fun HomeLayoutSettingRow(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    isFocused: Boolean,
    onAdjust: (Int) -> Unit,
    onToggle: () -> Unit
) {
    when (field) {
        HomeLayoutSettingField.ROW_ALIGNMENT -> SegmentedPreference(
            title = stringResource(field.titleRes),
            options = HomeRowAlignment.entries.map { alignmentLabel(it) },
            selectedIndex = settings.carousel.rowAlignment.ordinal,
            isFocused = isFocused,
            onSelect = { onAdjust(it - settings.carousel.rowAlignment.ordinal) }
        )
        HomeLayoutSettingField.FOCUS_POSITION -> SegmentedPreference(
            title = stringResource(field.titleRes),
            options = HomeFocusPosition.entries.map { focusPositionLabel(it) },
            selectedIndex = settings.carousel.focusPosition.ordinal,
            isFocused = isFocused,
            onSelect = { onAdjust(it - settings.carousel.focusPosition.ordinal) }
        )
        HomeLayoutSettingField.INVERTED -> SwitchPreference(
            title = stringResource(field.titleRes),
            isEnabled = settings.carousel.inverted,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RESTING_SCALE -> SliderPreference(
            title = stringResource(field.titleRes),
            value = percentOf(settings.carousel.restingScale),
            minValue = RESTING_SCALE_MIN_PERCENT,
            maxValue = RESTING_SCALE_MAX_PERCENT,
            isFocused = isFocused,
            step = SCALE_PERCENT_STEP,
            suffix = "%",
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.NEIGHBOUR_PUSH -> SwitchPreference(
            title = stringResource(field.titleRes),
            isEnabled = settings.carousel.neighbourPush,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.PLATFORM_BADGE -> SwitchPreference(
            title = stringResource(field.titleRes),
            isEnabled = settings.carousel.showPlatformBadge,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.SCROLL_AXIS -> SegmentedPreference(
            title = stringResource(field.titleRes),
            options = HomeScrollAxis.entries.map { scrollAxisLabel(it) },
            selectedIndex = settings.autoGrid.scrollAxis.ordinal,
            isFocused = isFocused,
            onSelect = { onAdjust(it - settings.autoGrid.scrollAxis.ordinal) }
        )
        HomeLayoutSettingField.SHOW_TITLES -> SwitchPreference(
            title = stringResource(field.titleRes),
            isEnabled = settings.autoGrid.showTitles,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_show_all_games_subtitle),
            isEnabled = settings.autoGrid.showAllGames,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.AUTO_GRID_LANES -> SliderPreference(
            title = laneCountLabel(settings.autoGrid.scrollAxis),
            value = settings.autoGrid.laneCount,
            minValue = MIN_LANE_COUNT,
            maxValue = MAX_LANE_COUNT,
            isFocused = isFocused,
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.CUSTOM_GRID_COLUMNS -> GridAxisStepperRow(
            title = stringResource(field.titleRes),
            axis = settings.customGrid.columns,
            isFocused = isFocused,
            onAdjust = onAdjust
        )
        HomeLayoutSettingField.CUSTOM_GRID_ROWS -> GridAxisStepperRow(
            title = stringResource(field.titleRes),
            axis = settings.customGrid.rows,
            isFocused = isFocused,
            onAdjust = onAdjust
        )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD -> SegmentedPreference(
            title = stringResource(field.titleRes),
            options = HomeTileAutoAdd.entries.map { autoAddLabel(it) },
            selectedIndex = settings.customGrid.autoAdd.ordinal,
            isFocused = isFocused,
            onSelect = { onAdjust(it - settings.customGrid.autoAdd.ordinal) }
        )
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_custom_grid_empty_slots_subtitle),
            isEnabled = settings.customGrid.showEmptySlots,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_custom_grid_persist_pages_subtitle),
            isEnabled = settings.customGrid.persistBlankPages,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_custom_grid_auto_fit_subtitle),
            isEnabled = settings.customGrid.autoFit,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_rail_libraries_subtitle),
            isEnabled = settings.rails.showLibraries,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_CONTINUE_WATCHING -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_rail_continue_watching_subtitle),
            isEnabled = settings.rails.showContinueWatching,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_NEXT_UP -> SwitchPreference(
            title = stringResource(field.titleRes),
            subtitle = stringResource(R.string.ui_home_layout_rail_next_up_subtitle),
            isEnabled = settings.rails.showNextUp,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
    }
}

@Composable
private fun GridAxisStepperRow(
    title: String,
    axis: GridAxis,
    isFocused: Boolean,
    onAdjust: (Int) -> Unit
) {
    SliderPreference(
        title = title,
        value = axis.stepIndex,
        minValue = 0,
        maxValue = GRID_AXIS_STEPS.lastIndex,
        isFocused = isFocused,
        valueLabel = when (axis) {
            is GridAxis.Fixed -> axis.count.toString()
            GridAxis.Fill -> stringResource(R.string.ui_home_layout_custom_grid_axis_fill)
            GridAxis.Scroll -> stringResource(R.string.ui_home_layout_custom_grid_axis_scroll)
        },
        onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
    )
}

@Composable
private fun autoAddLabel(mode: HomeTileAutoAdd): String = when (mode) {
    HomeTileAutoAdd.OFF -> stringResource(R.string.ui_home_layout_auto_add_off)
    HomeTileAutoAdd.AUTO -> stringResource(R.string.ui_home_layout_auto_add_auto)
    HomeTileAutoAdd.PROMPT -> stringResource(R.string.ui_home_layout_auto_add_prompt)
}

@Composable
private fun layoutLabel(kind: HomeLayoutKind): String = when (kind) {
    HomeLayoutKind.CAROUSEL -> stringResource(R.string.ui_home_layout_kind_carousel)
    HomeLayoutKind.AUTO_GRID -> stringResource(R.string.ui_home_layout_kind_auto_grid)
    HomeLayoutKind.CUSTOM_GRID -> stringResource(R.string.ui_home_layout_kind_custom_grid)
    HomeLayoutKind.COLUMNS -> stringResource(R.string.ui_home_layout_kind_columns)
}

@Composable
private fun alignmentLabel(alignment: HomeRowAlignment): String = when (alignment) {
    HomeRowAlignment.TOP -> stringResource(R.string.ui_home_layout_alignment_top)
    HomeRowAlignment.CENTER -> stringResource(R.string.ui_home_layout_alignment_center)
    HomeRowAlignment.BOTTOM -> stringResource(R.string.ui_home_layout_alignment_bottom)
}

@Composable
private fun focusPositionLabel(position: HomeFocusPosition): String = when (position) {
    HomeFocusPosition.LEADING -> stringResource(R.string.ui_home_layout_focus_leading)
    HomeFocusPosition.CENTER -> stringResource(R.string.ui_home_layout_focus_center)
}

@Composable
private fun scrollAxisLabel(axis: HomeScrollAxis): String = when (axis) {
    HomeScrollAxis.VERTICAL -> stringResource(R.string.ui_home_layout_axis_vertical)
    HomeScrollAxis.HORIZONTAL -> stringResource(R.string.ui_home_layout_axis_horizontal)
}


/**
 * Lanes run across the axis that is not scrolling, so the same stored number is presented as
 * columns or rows depending on which way the grid moves.
 */
@Composable
private fun laneCountLabel(axis: HomeScrollAxis): String = when (axis) {
    HomeScrollAxis.VERTICAL -> stringResource(R.string.ui_home_layout_lanes_columns)
    HomeScrollAxis.HORIZONTAL -> stringResource(R.string.ui_home_layout_lanes_rows)
}

private fun percentOf(scale: Float): Int = (scale * PERCENT).roundToInt()

private fun stepScale(current: Float, deltaPercent: Int, minPercent: Int, maxPercent: Int): Float {
    val stepped = ((current * PERCENT).roundToInt() + deltaPercent).coerceIn(minPercent, maxPercent)
    return stepped / PERCENT
}

private fun stepSpan(current: Int, direction: Int): Int =
    (current + direction).coerceIn(MIN_LANE_COUNT, MAX_LANE_COUNT)

private inline fun <reified T : Enum<T>> step(current: T, direction: Int): T {
    val values = enumValues<T>()
    return values[(current.ordinal + direction).coerceIn(0, values.size - 1)]
}

private fun Enum<*>.isAtEnd(direction: Int, count: Int): Boolean =
    if (direction < 0) ordinal == 0 else ordinal == count - 1
