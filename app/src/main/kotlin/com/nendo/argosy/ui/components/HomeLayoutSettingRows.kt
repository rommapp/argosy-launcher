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
import com.nendo.argosy.domain.model.MAX_LANE_COUNT
import com.nendo.argosy.domain.model.MIN_LANE_COUNT
import com.nendo.argosy.domain.model.HomeLayoutSettings
import com.nendo.argosy.domain.model.HomeRowAlignment
import com.nendo.argosy.domain.model.HomeScrollAxis
import com.nendo.argosy.domain.model.HomeTileAutoAdd
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
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
    CAROUSEL_BOX_ART,
    AUTO_GRID_BOX_ART,
    CUSTOM_GRID_LANES,
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
        HomeLayoutSettingField.CAROUSEL_BOX_ART,
        HomeLayoutSettingField.INVERTED
    )
    HomeLayoutKind.AUTO_GRID -> listOf(
        HomeLayoutSettingField.SCROLL_AXIS,
        HomeLayoutSettingField.AUTO_GRID_LANES,
        HomeLayoutSettingField.SHOW_TITLES,
        HomeLayoutSettingField.AUTO_GRID_BOX_ART,
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL
    )
    HomeLayoutKind.CUSTOM_GRID -> listOf(
        HomeLayoutSettingField.CUSTOM_GRID_LANES,
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS,
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT,
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES,
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD
    )
}

/**
 * Left/right adjustment for [field]. Booleans follow the house rule that left is off and right is
 * on; enums wrap; numbers clamp.
 */
fun adjustHomeLayoutField(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    direction: Int
): HomeLayoutSettings = when (field) {
        HomeLayoutSettingField.ROW_ALIGNMENT ->
            settings.copy(carousel = settings.carousel.copy(rowAlignment = cycle(settings.carousel.rowAlignment, direction)))
        HomeLayoutSettingField.FOCUS_POSITION ->
            settings.copy(carousel = settings.carousel.copy(focusPosition = cycle(settings.carousel.focusPosition, direction)))
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
            settings.copy(autoGrid = settings.autoGrid.copy(scrollAxis = cycle(settings.autoGrid.scrollAxis, direction)))
        HomeLayoutSettingField.SHOW_TITLES ->
            settings.copy(autoGrid = settings.autoGrid.copy(showTitles = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL ->
            settings.copy(autoGrid = settings.autoGrid.copy(showAllGames = direction > 0))
        HomeLayoutSettingField.CAROUSEL_BOX_ART ->
            settings.copy(carousel = settings.carousel.copy(useBoxArt = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_BOX_ART ->
            settings.copy(autoGrid = settings.autoGrid.copy(useBoxArt = direction > 0))
        HomeLayoutSettingField.AUTO_GRID_LANES ->
            settings.copy(autoGrid = settings.autoGrid.copy(laneCount = stepSpan(settings.autoGrid.laneCount, direction)))
        HomeLayoutSettingField.CUSTOM_GRID_LANES ->
            settings.copy(customGrid = settings.customGrid.copy(laneCount = stepSpan(settings.customGrid.laneCount, direction)))
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD ->
            settings.copy(customGrid = settings.customGrid.copy(autoAdd = cycle(settings.customGrid.autoAdd, direction)))
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
        HomeLayoutSettingField.CAROUSEL_BOX_ART ->
            settings.copy(carousel = settings.carousel.copy(useBoxArt = !settings.carousel.useBoxArt))
        HomeLayoutSettingField.AUTO_GRID_BOX_ART ->
            settings.copy(autoGrid = settings.autoGrid.copy(useBoxArt = !settings.autoGrid.useBoxArt))
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
 * The layout selector: one tile per layout, the selected one raised. Kept separate from the picker
 * so a settings pane can place it among its own rows with the preview sitting directly above it.
 */
@Composable
fun HomeLayoutSelectorRow(
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

/**
 * One layout setting rendered with the standard preference controls, so a layout field looks and
 * behaves like every other row wherever it is hosted.
 */
/**
 * [boxArtCapableGames] is how many games hold both a front cover and a spine. The box art rows say
 * so when it is zero, because the setting still turns on and still changes nothing: a library whose
 * metadata source never supplied spine art draws every game flat, and without a word here that
 * looks like a broken toggle rather than missing artwork.
 */
@Composable
fun HomeLayoutSettingRow(
    settings: HomeLayoutSettings,
    field: HomeLayoutSettingField,
    isFocused: Boolean,
    onAdjust: (Int) -> Unit,
    onToggle: () -> Unit,
    boxArtCapableGames: Int = 0
) {
    val boxArtSubtitle = if (boxArtCapableGames > 0) {
        "Draw games that have a spine as a box, and the rest as covers"
    } else {
        "No game has spine art, so every game draws as a 2D cover"
    }
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
        HomeLayoutSettingField.SHOW_TITLES -> SwitchPreference(
            title = "Show Titles",
            isEnabled = settings.autoGrid.showTitles,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.AUTO_GRID_SHOW_ALL -> SwitchPreference(
            title = "Show Every Game",
            subtitle = "Scroll a platform's whole library instead of stopping at View All",
            isEnabled = settings.autoGrid.showAllGames,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.CAROUSEL_BOX_ART -> SwitchPreference(
            title = "3D Box Art",
            subtitle = boxArtSubtitle,
            isEnabled = settings.carousel.useBoxArt,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.AUTO_GRID_BOX_ART -> SwitchPreference(
            title = "3D Box Art",
            subtitle = boxArtSubtitle,
            isEnabled = settings.autoGrid.useBoxArt,
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
        HomeLayoutSettingField.CUSTOM_GRID_LANES -> SliderPreference(
            title = "Cells across",
            value = settings.customGrid.laneCount,
            minValue = MIN_LANE_COUNT,
            maxValue = MAX_LANE_COUNT,
            isFocused = isFocused,
            onAdjust = { delta -> onAdjust(if (delta < 0) -1 else 1) }
        )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_ADD -> CyclePreference(
            title = "Add New Downloads",
            value = autoAddLabel(settings.customGrid.autoAdd),
            isFocused = isFocused,
            onClick = { onAdjust(1) },
            onPrev = { onAdjust(-1) }
        )
        HomeLayoutSettingField.CUSTOM_GRID_EMPTY_SLOTS -> SwitchPreference(
            title = "Show Blank Slots",
            subtitle = "Outline the cells nothing sits on",
            isEnabled = settings.customGrid.showEmptySlots,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.CUSTOM_GRID_PERSIST_PAGES -> SwitchPreference(
            title = "Keep Blank Pages",
            subtitle = "An added page stays even while it holds nothing",
            isEnabled = settings.customGrid.persistBlankPages,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.CUSTOM_GRID_AUTO_FIT -> SwitchPreference(
            title = "Auto-fit Cells",
            subtitle = "Move or shrink tiles a placement lands on, instead of refusing it",
            isEnabled = settings.customGrid.autoFit,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_MEDIA_LIBRARIES -> SwitchPreference(
            title = "Media Libraries",
            subtitle = "A row for each library on your media server",
            isEnabled = settings.rails.showLibraries,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_CONTINUE_WATCHING -> SwitchPreference(
            title = "Continue Watching",
            subtitle = "A row of movies and episodes you are part way through",
            isEnabled = settings.rails.showContinueWatching,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
        HomeLayoutSettingField.RAIL_NEXT_UP -> SwitchPreference(
            title = "Next Up",
            subtitle = "A row of the next episode of each show you are watching",
            isEnabled = settings.rails.showNextUp,
            isFocused = isFocused,
            onToggle = { onToggle() }
        )
    }
}

private fun autoAddLabel(mode: HomeTileAutoAdd): String = when (mode) {
    HomeTileAutoAdd.OFF -> "Off"
    HomeTileAutoAdd.AUTO -> "Automatically"
    HomeTileAutoAdd.PROMPT -> "Ask First"
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
    (current + direction).coerceIn(MIN_LANE_COUNT, MAX_LANE_COUNT)

private inline fun <reified T : Enum<T>> cycle(current: T, direction: Int): T {
    val values = enumValues<T>()
    return values[(current.ordinal + direction).mod(values.size)]
}
