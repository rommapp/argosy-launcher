package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.data.preferences.GridDensity
import com.nendo.argosy.domain.model.HomeFocusPosition
import com.nendo.argosy.domain.model.HomeLayoutKind
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeSectionStyle
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.roundToInt

/**
 * Every adjustable field across the three layouts, so a caller routes one exhaustive `when`
 * instead of one per layout.
 */
enum class HomeLayoutSettingField {
    ROW_ALIGNMENT,
    FOCUS_POSITION,
    INVERTED,
    FOCUS_SCALE,
    RESTING_SCALE,
    NEIGHBOUR_PUSH,
    PLATFORM_BADGE,
    SCROLL_AXIS,
    AUTO_GRID_LANES,
    SECTION_STYLE,
    SHOW_TITLES,
    CUSTOM_GRID_LANES
}

/**
 * A focusable line of the picker, in render order. A row's index in [homeLayoutPickerRows] is its
 * focus index.
 */
sealed interface HomeLayoutPickerRow {
    data object LayoutSelector : HomeLayoutPickerRow
    data class Setting(val field: HomeLayoutSettingField) : HomeLayoutPickerRow
}

private const val SCALE_PERCENT_STEP = 10
private const val FOCUS_SCALE_MIN_PERCENT = 100
private const val FOCUS_SCALE_MAX_PERCENT = 300
private const val RESTING_SCALE_MIN_PERCENT = 50
private const val RESTING_SCALE_MAX_PERCENT = 100
private const val GRID_SPAN_MIN = 2
private const val GRID_SPAN_MAX = 8
private const val PERCENT = 100f

/**
 * Rows the picker renders for [settings]: the layout selector, then the selected layout's own
 * fields. The caller's input handler drives focus against this list, so what gamepad focus can
 * reach and what the picker draws cannot drift apart.
 */
fun homeLayoutPickerRows(settings: HomeLayoutSettings): List<HomeLayoutPickerRow> = buildList {
    add(HomeLayoutPickerRow.LayoutSelector)
    val fields = when (settings.selected) {
        HomeLayoutKind.CAROUSEL -> listOf(
            HomeLayoutSettingField.ROW_ALIGNMENT,
            HomeLayoutSettingField.FOCUS_POSITION,
            HomeLayoutSettingField.FOCUS_SCALE,
            HomeLayoutSettingField.RESTING_SCALE,
            HomeLayoutSettingField.NEIGHBOUR_PUSH,
            HomeLayoutSettingField.PLATFORM_BADGE,
            HomeLayoutSettingField.INVERTED
        )
        HomeLayoutKind.AUTO_GRID -> listOf(
            HomeLayoutSettingField.SCROLL_AXIS,
            HomeLayoutSettingField.AUTO_GRID_LANES,
            HomeLayoutSettingField.SECTION_STYLE,
            HomeLayoutSettingField.SHOW_TITLES
        )
        HomeLayoutKind.CUSTOM_GRID -> listOf(
            HomeLayoutSettingField.CUSTOM_GRID_LANES
        )
    }
    fields.forEach { add(HomeLayoutPickerRow.Setting(it)) }
}

/**
 * Left/right adjustment for [row]. Booleans follow the house rule that left is off and right is on;
 * enums wrap; numbers clamp. Returns [settings] unchanged when the row has nothing to adjust.
 */
fun adjustHomeLayoutRow(
    settings: HomeLayoutSettings,
    row: HomeLayoutPickerRow,
    direction: Int
): HomeLayoutSettings = when (row) {
    HomeLayoutPickerRow.LayoutSelector -> settings.copy(selected = cycle(settings.selected, direction))
    is HomeLayoutPickerRow.Setting -> when (row.field) {
        HomeLayoutSettingField.ROW_ALIGNMENT ->
            settings.copy(carousel = settings.carousel.copy(rowAlignment = cycle(settings.carousel.rowAlignment, direction)))
        HomeLayoutSettingField.FOCUS_POSITION ->
            settings.copy(carousel = settings.carousel.copy(focusPosition = cycle(settings.carousel.focusPosition, direction)))
        HomeLayoutSettingField.INVERTED ->
            settings.copy(carousel = settings.carousel.copy(inverted = direction > 0))
        HomeLayoutSettingField.FOCUS_SCALE ->
            settings.copy(
                carousel = settings.carousel.copy(
                    focusScale = stepScale(
                        settings.carousel.focusScale,
                        direction * SCALE_PERCENT_STEP,
                        FOCUS_SCALE_MIN_PERCENT,
                        FOCUS_SCALE_MAX_PERCENT
                    )
                )
            )
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
            settings.copy(autoGrid = settings.autoGrid.copy(scrollAxis = cycle(settings.autoGrid.scrollAxis, direction)))
        HomeLayoutSettingField.SECTION_STYLE ->
            settings.copy(autoGrid = settings.autoGrid.copy(sectionStyle = cycle(settings.autoGrid.sectionStyle, direction)))
        HomeLayoutSettingField.SHOW_TITLES ->
            settings.copy(autoGrid = settings.autoGrid.copy(showTitles = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_LANES ->
            settings.copy(autoGrid = settings.autoGrid.copy(laneCount = stepSpan(settings.autoGrid.laneCount, direction)))
        HomeLayoutSettingField.CUSTOM_GRID_LANES ->
            settings.copy(customGrid = settings.customGrid.copy(laneCount = stepSpan(settings.customGrid.laneCount, direction)))
    }
}

/**
 * Confirm handling for [row]. Toggles flip; everything else is unchanged, because confirm never
 * adjusts a value in this menu system.
 */
fun toggleHomeLayoutRow(settings: HomeLayoutSettings, row: HomeLayoutPickerRow): HomeLayoutSettings {
    if (row !is HomeLayoutPickerRow.Setting) return settings
    return when (row.field) {
        HomeLayoutSettingField.INVERTED ->
            settings.copy(carousel = settings.carousel.copy(inverted = !settings.carousel.inverted))
        HomeLayoutSettingField.NEIGHBOUR_PUSH ->
            settings.copy(carousel = settings.carousel.copy(neighbourPush = !settings.carousel.neighbourPush))
        HomeLayoutSettingField.PLATFORM_BADGE ->
            settings.copy(carousel = settings.carousel.copy(showPlatformBadge = !settings.carousel.showPlatformBadge))
        HomeLayoutSettingField.SHOW_TITLES ->
            settings.copy(autoGrid = settings.autoGrid.copy(showTitles = !settings.autoGrid.showTitles))
        else -> settings
    }
}

/**
 * Layout picker: a live schematic of [settings], the layout selector, and the selected layout's own
 * settings. Every change is applied through [onSettingsChange] as it happens; there is no apply
 * step, and the component owns neither the settings nor the focus index so a wizard and a settings
 * pane can drive it from their own state.
 *
 * Renders a plain [Column] rather than a lazy list because it is meant to be placed inside the
 * host's own scrolling container.
 *
 * @param focusedIndex index into [homeLayoutPickerRows]; the caller's input handler owns it.
 * @param onFocusIndex a touch on a row asks the caller to move focus there, so tap and d-pad agree.
 */
@Composable
fun HomeLayoutPicker(
    settings: HomeLayoutSettings,
    focusedIndex: Int,
    onSettingsChange: (HomeLayoutSettings) -> Unit,
    onFocusIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    animatePreview: Boolean = true,
    gridDensity: GridDensity = ComponentDefaults.Launcher.gridDensity
) {
    val rows = homeLayoutPickerRows(settings)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        HomeLayoutPreview(
            settings = settings,
            animate = animatePreview,
            gridDensity = gridDensity,
            modifier = Modifier.fillMaxWidth()
        )
        LayoutSelectorRow(
            selected = settings.selected,
            isFocused = focusedIndex == 0,
            onSelect = { kind ->
                onFocusIndex(0)
                onSettingsChange(settings.copy(selected = kind))
            }
        )
        rows.forEachIndexed { index, row ->
            if (row is HomeLayoutPickerRow.Setting) {
                SettingRow(
                    settings = settings,
                    field = row.field,
                    isFocused = focusedIndex == index,
                    onAdjust = { direction ->
                        onFocusIndex(index)
                        onSettingsChange(adjustHomeLayoutRow(settings, row, direction))
                    },
                    onToggle = {
                        onFocusIndex(index)
                        onSettingsChange(toggleHomeLayoutRow(settings, row))
                    }
                )
            }
        }
    }
}

@Composable
private fun LayoutSelectorRow(
    selected: HomeLayoutKind,
    isFocused: Boolean,
    onSelect: (HomeLayoutKind) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        HomeLayoutKind.entries.forEach { kind ->
            LayoutSelectorTile(
                kind = kind,
                isSelected = kind == selected,
                isFocused = isFocused && kind == selected,
                onClick = { onSelect(kind) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LayoutSelectorTile(
    kind: HomeLayoutKind,
    isSelected: Boolean,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = modifier
            .heightIn(min = Dimens.menuRowHeight)
            .clip(shape)
            .background(if (isSelected) theme.surfaceRaised else theme.surfaceBase)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.Pill,
                selected = isSelected,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = layoutLabel(kind),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = if (isSelected) theme.textPrimary else theme.textDim
        )
    }
}

@Composable
private fun SettingRow(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    isFocused: Boolean,
    onAdjust: (Int) -> Unit,
    onToggle: () -> Unit
) {
    when (field) {
        HomeLayoutSettingField.ROW_ALIGNMENT -> CyclePreference(
            title = "Row Position",
            value = alignmentLabel(settings.carousel.rowAlignment),
            isFocused = isFocused,
            onClick = { onAdjust(1) },
            onPrev = { onAdjust(-1) }
        )
        HomeLayoutSettingField.FOCUS_POSITION -> CyclePreference(
            title = "Focus Anchor",
            value = focusPositionLabel(settings.carousel.focusPosition),
            isFocused = isFocused,
            onClick = { onAdjust(1) },
            onPrev = { onAdjust(-1) }
        )
        HomeLayoutSettingField.INVERTED -> SwitchPreference(
            title = "Reverse Order",
            isEnabled = settings.carousel.inverted,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.FOCUS_SCALE -> SliderPreference(
            title = "Focused Size",
            value = percentOf(settings.carousel.focusScale),
            minValue = FOCUS_SCALE_MIN_PERCENT,
            maxValue = FOCUS_SCALE_MAX_PERCENT,
            isFocused = isFocused,
            step = SCALE_PERCENT_STEP,
            suffix = "%",
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.RESTING_SCALE -> SliderPreference(
            title = "Resting Size",
            value = percentOf(settings.carousel.restingScale),
            minValue = RESTING_SCALE_MIN_PERCENT,
            maxValue = RESTING_SCALE_MAX_PERCENT,
            isFocused = isFocused,
            step = SCALE_PERCENT_STEP,
            suffix = "%",
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.NEIGHBOUR_PUSH -> SwitchPreference(
            title = "Push Neighbours",
            isEnabled = settings.carousel.neighbourPush,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.PLATFORM_BADGE -> SwitchPreference(
            title = "Platform Badge",
            isEnabled = settings.carousel.showPlatformBadge,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.SCROLL_AXIS -> CyclePreference(
            title = "Scroll Direction",
            value = scrollAxisLabel(settings.autoGrid.scrollAxis),
            isFocused = isFocused,
            onClick = { onAdjust(1) },
            onPrev = { onAdjust(-1) }
        )
        HomeLayoutSettingField.SECTION_STYLE -> CyclePreference(
            title = "Section Style",
            value = sectionStyleLabel(settings.autoGrid.sectionStyle),
            isFocused = isFocused,
            onClick = { onAdjust(1) },
            onPrev = { onAdjust(-1) }
        )
        HomeLayoutSettingField.SHOW_TITLES -> SwitchPreference(
            title = "Show Titles",
            isEnabled = settings.autoGrid.showTitles,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.AUTO_GRID_LANES -> SliderPreference(
            title = laneCountLabel(settings.autoGrid.scrollAxis),
            value = settings.autoGrid.laneCount,
            minValue = GRID_SPAN_MIN,
            maxValue = GRID_SPAN_MAX,
            isFocused = isFocused,
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.CUSTOM_GRID_LANES -> SliderPreference(
            title = "Cells across",
            value = settings.customGrid.laneCount,
            minValue = GRID_SPAN_MIN,
            maxValue = GRID_SPAN_MAX,
            isFocused = isFocused,
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
    }
}

private fun layoutLabel(kind: HomeLayoutKind): String = when (kind) {
    HomeLayoutKind.CAROUSEL -> "Carousel"
    HomeLayoutKind.AUTO_GRID -> "Auto Grid"
    HomeLayoutKind.CUSTOM_GRID -> "Custom Grid"
}

private fun alignmentLabel(alignment: HomeRowAlignment): String = when (alignment) {
    HomeRowAlignment.TOP -> "Top"
    HomeRowAlignment.CENTER -> "Center"
    HomeRowAlignment.BOTTOM -> "Bottom"
}

private fun focusPositionLabel(position: HomeFocusPosition): String = when (position) {
    HomeFocusPosition.LEADING -> "Leading"
    HomeFocusPosition.CENTER -> "Center"
}

private fun scrollAxisLabel(axis: HomeScrollAxis): String = when (axis) {
    HomeScrollAxis.VERTICAL -> "Vertical"
    HomeScrollAxis.HORIZONTAL -> "Horizontal"
}

private fun sectionStyleLabel(style: HomeSectionStyle): String = when (style) {
    HomeSectionStyle.HEADINGS -> "Headings"
    HomeSectionStyle.FLAT -> "Flat"
}

/**
 * Lanes run across the axis that is not scrolling, so the same stored number is presented as
 * columns or rows depending on which way the grid moves.
 */
private fun laneCountLabel(axis: HomeScrollAxis): String = when (axis) {
    HomeScrollAxis.VERTICAL -> "Columns"
    HomeScrollAxis.HORIZONTAL -> "Rows"
}

private fun percentOf(scale: Float): Int = (scale * PERCENT).roundToInt()

private fun stepScale(current: Float, deltaPercent: Int, minPercent: Int, maxPercent: Int): Float {
    val stepped = ((current * PERCENT).roundToInt() + deltaPercent).coerceIn(minPercent, maxPercent)
    return stepped / PERCENT
}

private fun stepSpan(current: Int, direction: Int): Int =
    (current + direction).coerceIn(GRID_SPAN_MIN, GRID_SPAN_MAX)

private inline fun <reified T : Enum<T>> cycle(current: T, direction: Int): T {
    val values = enumValues<T>()
    return values[(current.ordinal + direction).mod(values.size)]
}
