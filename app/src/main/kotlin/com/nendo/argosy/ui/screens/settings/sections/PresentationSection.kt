package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MAX
import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_MIN
import com.nendo.argosy.domain.model.PRESENTATION_SCRIM_STEP
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationScrim
import com.nendo.argosy.domain.model.PresentationStat
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.dualscreen.CompanionDetail
import com.nendo.argosy.ui.dualscreen.PresentOnCompanion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.dualscreen.SlotOwner
import com.nendo.argosy.ui.screens.settings.SettingsInputHandler
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens

internal val PRESENTATION_MENU_STATS: List<PresentationStat> = listOf(
    PresentationStat.DEVELOPER,
    PresentationStat.RELEASE_YEAR,
    PresentationStat.PLAYERS,
    PresentationStat.GENRE,
    PresentationStat.COMMUNITY_RATING,
    PresentationStat.USER_RATING,
    PresentationStat.DIFFICULTY,
    PresentationStat.PLAY_TIME,
    PresentationStat.TIME_TO_BEAT,
    PresentationStat.ACHIEVEMENTS,
    PresentationStat.FRIENDS
)

internal sealed class PresentationItem(
    val key: String,
    val section: String,
    val visibleWhen: (PresentationStyle) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : PresentationItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (PresentationStyle) -> Boolean = { true }
    ) : PresentationItem(key, section, visibleWhen), SearchableSetting

    data object Layout : Row("presentationLayout", "layout", R.string.settings_presentation_layout_title)
    data object Scrim : Row("presentationScrim", "background", R.string.settings_presentation_scrim_title)
    data object ScrimStrength : Row(
        key = "presentationScrimStrength",
        section = "background",
        labelRes = R.string.settings_presentation_scrim_strength_title,
        visibleWhen = { it.scrim != PresentationScrim.NONE }
    )
    data object BackgroundBlur : Row(
        key = "presentationBackgroundBlur",
        section = "background",
        labelRes = R.string.settings_presentation_background_blur_title,
        visibleWhen = { it.scrim == PresentationScrim.GRADIENT }
    )
    data object Art : Row(
        key = "presentationArt",
        section = "artwork",
        labelRes = R.string.settings_presentation_art_title,
        visibleWhen = { it.layout == PresentationLayout.CINEMATIC }
    )
    data class Stat(val stat: PresentationStat) : Row(
        key = "presentationStat_${stat.name}",
        section = "stats",
        labelRes = presentationStatLabelRes(stat),
        visibleWhen = { it.layout != PresentationLayout.LOGO }
    )

    companion object {
        val ALL: List<PresentationItem>
            get() = listOf(
            Layout,
            Header("backgroundHeader", "background", R.string.settings_presentation_section_background),
            Scrim,
            ScrimStrength,
            BackgroundBlur,
            Header("artworkHeader", "artwork", R.string.settings_presentation_section_artwork),
            Art,
            Header("statsHeader", "stats", R.string.settings_presentation_section_stats)
        ) + PRESENTATION_MENU_STATS.map { Stat(it) }
    }
}

internal val presentationLayout = SettingsLayout<PresentationItem, PresentationStyle>(
    allItems = PresentationItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, style -> item.visibleWhen(style) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "background" -> R.string.settings_presentation_section_background
            "artwork" -> R.string.settings_presentation_section_artwork
            "stats" -> R.string.settings_presentation_section_stats
            else -> null
        }
    }
)

internal fun presentationMaxFocusIndex(style: PresentationStyle): Int =
    presentationLayout.maxFocusIndex(style)

internal fun presentationItemAtFocusIndex(index: Int, style: PresentationStyle): PresentationItem? =
    presentationLayout.itemAtFocusIndex(index, style)

internal fun presentationSections(style: PresentationStyle) = presentationLayout.buildSections(style)

internal fun presentationVisibleItems(style: PresentationStyle): List<PresentationItem> =
    presentationLayout.visibleItems(style)

internal fun adjustPresentationItem(
    style: PresentationStyle,
    item: PresentationItem,
    direction: Int
): PresentationStyle? = when (item) {
    PresentationItem.Layout -> style.copy(layout = stepEnum(style.layout, direction))
    PresentationItem.Scrim -> style.copy(scrim = stepEnum(style.scrim, direction))
    PresentationItem.ScrimStrength -> style.copy(
        scrimStrength = (style.scrimStrength + direction * PRESENTATION_SCRIM_STEP)
            .coerceIn(PRESENTATION_SCRIM_MIN, PRESENTATION_SCRIM_MAX)
    )
    PresentationItem.Art -> style.copy(art = stepEnum(style.art, direction))
    is PresentationItem.Stat -> style.withStat(item.stat, shown = direction > 0)
    PresentationItem.BackgroundBlur, is PresentationItem.Header -> null
}

private inline fun <reified T : Enum<T>> stepEnum(current: T, direction: Int): T {
    val values = enumValues<T>()
    return values[(current.ordinal + direction).coerceIn(0, values.size - 1)]
}

internal fun presentationLayoutLabelRes(layout: PresentationLayout): Int = when (layout) {
    PresentationLayout.CINEMATIC -> R.string.settings_presentation_layout_cinematic
    PresentationLayout.JOURNAL -> R.string.settings_presentation_layout_journal
    PresentationLayout.LOGO -> R.string.settings_presentation_layout_logo
}

internal fun presentationScrimLabelRes(scrim: PresentationScrim): Int = when (scrim) {
    PresentationScrim.GRADIENT -> R.string.settings_presentation_scrim_gradient
    PresentationScrim.SOLID -> R.string.settings_presentation_scrim_solid
    PresentationScrim.BLUR -> R.string.settings_presentation_scrim_blur
    PresentationScrim.NONE -> R.string.settings_presentation_scrim_none
}

internal fun presentationArtLabelRes(art: PresentationArt): Int = when (art) {
    PresentationArt.COVER -> R.string.settings_presentation_art_cover
    PresentationArt.BOX_3D -> R.string.settings_presentation_art_box_3d
    PresentationArt.TITLE -> R.string.settings_presentation_art_title_only
}

internal fun presentationStatLabelRes(stat: PresentationStat): Int = when (stat) {
    PresentationStat.DEVELOPER -> R.string.settings_presentation_stat_developer
    PresentationStat.RELEASE_YEAR -> R.string.settings_presentation_stat_release_year
    PresentationStat.PLAYERS -> R.string.settings_presentation_stat_players
    PresentationStat.GENRE -> R.string.settings_presentation_stat_genre
    PresentationStat.COMMUNITY_RATING -> R.string.settings_presentation_stat_community_rating
    PresentationStat.USER_RATING -> R.string.settings_presentation_stat_user_rating
    PresentationStat.DIFFICULTY -> R.string.settings_presentation_stat_difficulty
    PresentationStat.PLAY_TIME -> R.string.settings_presentation_stat_play_time
    PresentationStat.TIME_TO_BEAT -> R.string.settings_presentation_stat_time_to_beat
    PresentationStat.ACHIEVEMENTS -> R.string.settings_presentation_stat_achievements
    PresentationStat.FRIENDS -> R.string.settings_presentation_stat_friends
}

@Composable
fun PresentationSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val style = uiState.display.presentationStyle
    val context = LocalContext.current

    val companionActive = DualScreenManagerHolder.instance
        ?.isCompanionActive?.collectAsState()?.value == true
    var sample by remember { mutableStateOf<CompanionDetail?>(null) }
    LaunchedEffect(Unit) { sample = viewModel.presentationSample() }
    val shown = sample
    if (companionActive && shown != null) {
        PresentOnCompanion(
            owner = SlotOwner("settings.presentation"),
            slot = PresentationSlot.Detail(shown)
        )
    }

    val visibleItems = remember(style) { presentationVisibleItems(style) }
    val sections = remember(style, context) { presentationLayout.buildSections(style, context) }

    fun isFocused(item: PresentationItem): Boolean =
        uiState.focusedIndex == presentationLayout.focusIndexOf(item, style)

    fun focus(item: PresentationItem) =
        viewModel.setFocusIndex(presentationLayout.focusIndexOf(item, style))

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { presentationLayout.focusToListIndex(it, style) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is PresentationItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is PresentationItem.Header -> Text(
                text = stringResource(item.titleRes).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = Dimens.spacingXs)
            )

            is PresentationItem.Layout -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = PresentationLayout.entries.map { stringResource(presentationLayoutLabelRes(it)) },
                selectedIndex = PresentationLayout.entries.indexOf(style.layout),
                isFocused = isFocused(item),
                onSelect = { index ->
                    focus(item)
                    viewModel.setPresentationStyle(style.copy(layout = PresentationLayout.entries[index]))
                }
            )

            is PresentationItem.Scrim -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = PresentationScrim.entries.map { stringResource(presentationScrimLabelRes(it)) },
                selectedIndex = PresentationScrim.entries.indexOf(style.scrim),
                isFocused = isFocused(item),
                onSelect = { index ->
                    focus(item)
                    viewModel.setPresentationStyle(style.copy(scrim = PresentationScrim.entries[index]))
                }
            )

            is PresentationItem.ScrimStrength -> SliderPreference(
                title = stringResource(item.labelRes),
                value = style.scrimStrength,
                minValue = PRESENTATION_SCRIM_MIN,
                maxValue = PRESENTATION_SCRIM_MAX,
                step = PRESENTATION_SCRIM_STEP,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { delta ->
                    focus(item)
                    adjustPresentationItem(style, item, if (delta < 0) -1 else 1)
                        ?.let(viewModel::setPresentationStyle)
                }
            )

            is PresentationItem.BackgroundBlur -> SliderPreference(
                title = stringResource(item.labelRes),
                value = uiState.display.presentationBackgroundBlur / 10,
                minValue = 0,
                maxValue = 10,
                isFocused = isFocused(item),
                onAdjust = {
                    focus(item)
                    viewModel.adjustPresentationBackgroundBlur(it * SettingsInputHandler.SLIDER_STEP)
                }
            )

            is PresentationItem.Art -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = PresentationArt.entries.map { stringResource(presentationArtLabelRes(it)) },
                selectedIndex = PresentationArt.entries.indexOf(style.art),
                isFocused = isFocused(item),
                onSelect = { index ->
                    focus(item)
                    viewModel.setPresentationStyle(style.copy(art = PresentationArt.entries[index]))
                }
            )

            is PresentationItem.Stat -> SwitchPreference(
                title = stringResource(item.labelRes),
                isEnabled = style.shows(item.stat),
                isFocused = isFocused(item),
                onToggle = { enabled ->
                    focus(item)
                    viewModel.setPresentationStyle(style.withStat(item.stat, enabled))
                }
            )
        }
    }
}
