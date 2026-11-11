package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.cache.GradientPreset
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtShape
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.GlassBorderTint
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.PlatformIndicatorContent
import com.nendo.argosy.data.preferences.PlatformIndicatorStyle
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.common.label
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.BoxArtStyleConfig
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import java.util.Locale

internal sealed class BoxArtItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val titleRes: Int, visibleWhen: (DisplayState) -> Boolean = { true })
        : BoxArtItem(key, section, visibleWhen)

    data object Shape : BoxArtItem("shape", "styling")
    data object CornerRadius : BoxArtItem("cornerRadius", "styling")
    data object BorderThickness : BoxArtItem("borderThickness", "styling")
    data object BorderStyle : BoxArtItem("borderStyle", "styling")

    data object GlassTint : BoxArtItem(
        key = "glassTint",
        section = "styling",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GLASS }
    )

    data object GradientPresetItem : BoxArtItem(
        key = "gradientPreset",
        section = "styling",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT }
    )

    data object GradientAdvanced : BoxArtItem(
        key = "gradientAdvanced",
        section = "styling",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT }
    )

    data object IndicatorStyle : BoxArtItem("indicatorStyle", "icon")

    data object IndicatorContent : BoxArtItem(
        key = "indicatorContent",
        section = "icon",
        visibleWhen = { it.platformIndicatorStyle != PlatformIndicatorStyle.OFF }
    )

    data object IconPos : BoxArtItem(
        key = "iconPos",
        section = "icon",
        visibleWhen = { it.platformIndicatorStyle != PlatformIndicatorStyle.OFF }
    )

    data object IconPad : BoxArtItem(
        key = "iconPad",
        section = "icon",
        visibleWhen = { it.platformIndicatorStyle != PlatformIndicatorStyle.OFF }
    )

    data object OuterEffect : BoxArtItem("outerEffect", "outer")

    data object OuterThickness : BoxArtItem(
        key = "outerThickness",
        section = "outer",
        visibleWhen = { it.boxArtOuterEffect != BoxArtOuterEffect.OFF }
    )

    data object GlowIntensity : BoxArtItem(
        key = "glowIntensity",
        section = "outer",
        visibleWhen = { it.boxArtOuterEffect == BoxArtOuterEffect.GLOW }
    )

    data object GlowColor : BoxArtItem(
        key = "glowColor",
        section = "outer",
        visibleWhen = { it.boxArtOuterEffect == BoxArtOuterEffect.GLOW }
    )

    data object InnerEffect : BoxArtItem("innerEffect", "inner")

    data object InnerThickness : BoxArtItem(
        key = "innerThickness",
        section = "inner",
        visibleWhen = { it.boxArtInnerEffect != BoxArtInnerEffect.OFF }
    )

    data object SampleGrid : BoxArtItem(
        key = "sampleGrid",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object SampleRadius : BoxArtItem(
        key = "sampleRadius",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object MinSaturation : BoxArtItem(
        key = "minSaturation",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object MinBrightness : BoxArtItem(
        key = "minBrightness",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object HueDistance : BoxArtItem(
        key = "hueDistance",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object SaturationBoost : BoxArtItem(
        key = "saturationBoost",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    data object BrightnessClamp : BoxArtItem(
        key = "brightnessClamp",
        section = "gradient",
        visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
    )

    companion object {
        private val StylingHeader = Header("stylingHeader", "styling", R.string.settings_box_art_section_styling)
        private val IconHeader = Header("iconHeader", "icon", R.string.settings_box_art_section_icon)
        private val OuterHeader = Header("outerHeader", "outer", R.string.settings_box_art_section_outer)
        private val InnerHeader = Header("innerHeader", "inner", R.string.settings_box_art_section_inner)
        private val GradientHeader = Header(
            key = "gradientHeader",
            section = "gradient",
            titleRes = R.string.settings_box_art_section_gradient,
            visibleWhen = { it.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT && it.gradientAdvancedMode }
        )

        val ALL: List<BoxArtItem>
            get() = listOf(
                StylingHeader,
                Shape, CornerRadius, BorderThickness, BorderStyle, GlassTint,
                GradientPresetItem, GradientAdvanced,
                GradientHeader,
                SampleGrid, SampleRadius, MinSaturation, MinBrightness,
                HueDistance, SaturationBoost, BrightnessClamp,
                IconHeader,
                IndicatorStyle, IndicatorContent, IconPos, IconPad,
                OuterHeader,
                OuterEffect, OuterThickness, GlowIntensity, GlowColor,
                InnerHeader,
                InnerEffect, InnerThickness
            )
    }
}

internal val GRADIENT_PRESET_CHOICES = listOf(
    GradientPreset.VIBRANT,
    GradientPreset.BALANCED,
    GradientPreset.SUBTLE
)

private val boxArtLayout = SettingsLayout<BoxArtItem, DisplayState>(
    allItems = BoxArtItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "styling" -> R.string.settings_box_art_section_styling
            "icon" -> R.string.settings_box_art_section_icon
            "outer" -> R.string.settings_box_art_section_outer
            "inner" -> R.string.settings_box_art_section_inner
            "gradient" -> R.string.settings_box_art_section_gradient
            else -> null
        }
    }
)

internal fun boxArtMaxFocusIndex(display: DisplayState): Int = boxArtLayout.maxFocusIndex(display)

internal fun boxArtItemAtFocusIndex(index: Int, display: DisplayState): BoxArtItem? =
    boxArtLayout.itemAtFocusIndex(index, display)

@Composable
fun BoxArtSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val display = uiState.display
    val gradientConfig = uiState.gradientConfig
    val extractionResult = uiState.gradientExtractionResult
    val showGradientSection = display.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT

    val context = LocalContext.current
    val visibleItems = remember(display) { boxArtLayout.visibleItems(display) }
    val sections = remember(display, context) { boxArtLayout.buildSections(display, context) }

    fun isFocused(item: BoxArtItem): Boolean =
        uiState.focusedIndex == boxArtLayout.focusIndexOf(item, display)

    fun pickerToken(item: BoxArtItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        SectionPaneLayout(
            items = visibleItems,
            sections = sections,
            focusedIndex = uiState.focusedIndex,
            focusToListIndex = { boxArtLayout.focusToListIndex(it, display) },
            itemKey = { it.key },
            isNavItem = { false },
            isHeader = { it is BoxArtItem.Header },
            onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) { item ->
                when (item) {
                    is BoxArtItem.Header -> BoxArtSectionHeader(stringResource(item.titleRes))

                    BoxArtItem.Shape -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_shape_title),
                        options = remember(context) { BoxArtShape.entries.map { it.label(context) } },
                        selectedIndex = BoxArtShape.entries.indexOf(display.boxArtShape),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtShape(BoxArtShape.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.CornerRadius -> CyclePreference(
                        title = stringResource(R.string.settings_box_art_corner_radius_title),
                        value = display.boxArtCornerRadius.displayName(context),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtCornerRadius() },
                        onPrev = { viewModel.cycleBoxArtCornerRadius(-1) },
                        options = remember(context) { BoxArtCornerRadius.entries.map { it.displayName(context) } },
                        onSelect = { viewModel.cycleBoxArtCornerRadius(it - display.boxArtCornerRadius.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.BorderThickness -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_border_thickness_title),
                        options = remember(context) { BoxArtBorderThickness.entries.map { it.displayName(context) } },
                        selectedIndex = BoxArtBorderThickness.entries.indexOf(display.boxArtBorderThickness),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtBorderThickness(BoxArtBorderThickness.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.BorderStyle -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_border_style_title),
                        options = remember(context) { BoxArtBorderStyle.entries.map { it.displayName(context) } },
                        selectedIndex = BoxArtBorderStyle.entries.indexOf(display.boxArtBorderStyle),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtBorderStyle(BoxArtBorderStyle.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.GlassTint -> CyclePreference(
                        title = stringResource(R.string.settings_box_art_glass_tint_title),
                        value = display.glassBorderTint.displayName(context),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGlassBorderTint() },
                        onPrev = { viewModel.cycleGlassBorderTint(-1) },
                        options = remember(context) { GlassBorderTint.entries.map { it.displayName(context) } },
                        onSelect = { viewModel.cycleGlassBorderTint(it - display.glassBorderTint.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GradientPresetItem -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_gradient_preset_title),
                        options = GRADIENT_PRESET_CHOICES.map { stringResource(it.labelRes) },
                        selectedIndex = GRADIENT_PRESET_CHOICES.indexOf(display.gradientPreset),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setGradientPreset(GRADIENT_PRESET_CHOICES[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.GradientAdvanced -> SwitchPreference(
                        title = stringResource(R.string.settings_box_art_gradient_advanced_title),
                        isEnabled = display.gradientAdvancedMode,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.toggleGradientAdvancedMode() }
                    )

                    BoxArtItem.IndicatorStyle -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_indicator_style_title),
                        options = remember(context) { PlatformIndicatorStyle.entries.map { it.displayName(context) } },
                        selectedIndex = PlatformIndicatorStyle.entries.indexOf(display.platformIndicatorStyle),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setPlatformIndicatorStyle(PlatformIndicatorStyle.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.IndicatorContent -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_indicator_content_title),
                        options = remember(context) { PlatformIndicatorContent.entries.map { it.displayName(context) } },
                        selectedIndex = PlatformIndicatorContent.entries.indexOf(display.platformIndicatorContent),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setPlatformIndicatorContent(PlatformIndicatorContent.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.IconPos -> CyclePreference(
                        title = stringResource(
                            when (display.platformIndicatorStyle) {
                                PlatformIndicatorStyle.SPINE -> R.string.settings_box_art_icon_position_title_spine
                                PlatformIndicatorStyle.TAB -> R.string.settings_box_art_icon_position_title_tab
                                PlatformIndicatorStyle.OFF -> R.string.settings_box_art_icon_position_title
                            }
                        ),
                        value = display.systemIconPosition.displayName(context),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSystemIconPosition() },
                        onPrev = { viewModel.cycleSystemIconPosition(-1) },
                        options = remember(context) { SystemIconPosition.CORNERS.map { it.displayName(context) } },
                        onSelect = { index ->
                            val currentIndex = SystemIconPosition.CORNERS
                                .indexOf(display.systemIconPosition).coerceAtLeast(0)
                            viewModel.cycleSystemIconPosition(index - currentIndex)
                        },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.IconPad -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_icon_padding_title),
                        options = remember(context) { SystemIconPadding.entries.map { it.displayName(context) } },
                        selectedIndex = SystemIconPadding.entries.indexOf(display.systemIconPadding),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setSystemIconPadding(SystemIconPadding.entries[it]) },
                        alwaysStacked = true
                    )

                    BoxArtItem.OuterEffect -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_outer_effect_title),
                        options = remember(context) { BoxArtOuterEffect.entries.map { it.displayName(context) } },
                        selectedIndex = BoxArtOuterEffect.entries.indexOf(display.boxArtOuterEffect),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtOuterEffect(BoxArtOuterEffect.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.OuterThickness -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_outer_thickness_title),
                        options = remember(context) { BoxArtOuterEffectThickness.entries.map { it.displayName(context) } },
                        selectedIndex = BoxArtOuterEffectThickness.entries.indexOf(display.boxArtOuterEffectThickness),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtOuterEffectThickness(BoxArtOuterEffectThickness.entries[it]) },
                        alwaysStacked = true
                    )
                    BoxArtItem.GlowIntensity -> CyclePreference(
                        title = stringResource(R.string.settings_box_art_glow_strength_title),
                        value = display.boxArtGlowStrength.displayName(context),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtGlowStrength() },
                        onPrev = { viewModel.cycleBoxArtGlowStrength(-1) },
                        options = remember(context) { BoxArtGlowStrength.entries.map { it.displayName(context) } },
                        onSelect = { viewModel.cycleBoxArtGlowStrength(it - display.boxArtGlowStrength.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GlowColor -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_glow_color_title),
                        options = GlowColorMode.entries.map { stringResource(it.labelRes) },
                        selectedIndex = GlowColorMode.entries.indexOf(display.glowColorMode),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setGlowColorMode(GlowColorMode.entries[it]) },
                        alwaysStacked = true
                    )

                    BoxArtItem.InnerEffect -> CyclePreference(
                        title = stringResource(R.string.settings_box_art_inner_effect_title),
                        value = display.boxArtInnerEffect.displayName(context),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtInnerEffect() },
                        onPrev = { viewModel.cycleBoxArtInnerEffect(-1) },
                        options = remember(context) { BoxArtInnerEffect.entries.map { it.displayName(context) } },
                        onSelect = { viewModel.cycleBoxArtInnerEffect(it - display.boxArtInnerEffect.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.InnerThickness -> SegmentedPreference(
                        title = stringResource(R.string.settings_box_art_inner_thickness_title),
                        options = remember(context) { BoxArtInnerEffectThickness.entries.map { it.displayName(context) } },
                        selectedIndex = BoxArtInnerEffectThickness.entries.indexOf(display.boxArtInnerEffectThickness),
                        isFocused = isFocused(item),
                        onSelect = { viewModel.setBoxArtInnerEffectThickness(BoxArtInnerEffectThickness.entries[it]) },
                        alwaysStacked = true
                    )

                    BoxArtItem.SampleGrid -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_sample_grid_title),
                        value = "${gradientConfig.samplesX}x${gradientConfig.samplesY}",
                        options = remember { listOf("8x12", "10x15", "12x18", "16x24") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientSampleGrid(it) }
                    )
                    BoxArtItem.SampleRadius -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_sample_radius_title),
                        value = gradientConfig.radius.toString(),
                        options = remember { listOf("1", "2", "3", "4") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientRadius(it) }
                    )
                    BoxArtItem.MinSaturation -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_min_saturation_title),
                        value = String.format(Locale.ROOT, "%.0f%%", gradientConfig.minSaturation * 100),
                        options = remember { listOf("20%", "25%", "30%", "35%", "40%", "45%", "50%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientMinSaturation(it) }
                    )
                    BoxArtItem.MinBrightness -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_min_brightness_title),
                        value = String.format(Locale.ROOT, "%.0f%%", gradientConfig.minValue * 100),
                        options = remember { listOf("10%", "15%", "20%", "25%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientMinValue(it) }
                    )
                    BoxArtItem.HueDistance -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_hue_distance_title),
                        value = "${gradientConfig.minHueDistance}deg",
                        options = remember { listOf("20deg", "30deg", "40deg", "50deg", "60deg") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientHueDistance(it) }
                    )
                    BoxArtItem.SaturationBoost -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_saturation_boost_title),
                        value = String.format(Locale.ROOT, "+%.0f%%", gradientConfig.saturationBump * 100),
                        options = remember { listOf("+30%", "+35%", "+40%", "+45%", "+50%", "+55%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientSaturationBump(it) }
                    )
                    BoxArtItem.BrightnessClamp -> GradientTuningCycle(
                        title = stringResource(R.string.settings_box_art_brightness_clamp_title),
                        value = String.format(Locale.ROOT, ">=%.0f%%", gradientConfig.valueClamp * 100),
                        options = remember { listOf(">=70%", ">=75%", ">=80%", ">=85%", ">=90%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientValueClamp(it) }
                    )
                }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val currentStyle = LocalBoxArtStyle.current
            val previewBoxArtStyle = BoxArtStyleConfig(
                aspectRatio = display.boxArtShape.aspectRatio,
                nativeAspectRatio = display.boxArtShape.isNative,
                cornerRadiusDp = display.boxArtCornerRadius.dp.dp,
                borderThicknessDp = display.boxArtBorderThickness.dp.dp,
                borderStyle = display.boxArtBorderStyle,
                glassBorderTintAlpha = display.glassBorderTint.alpha,
                glowAlpha = display.boxArtGlowStrength.alpha,
                isShadow = display.boxArtGlowStrength.isShadow,
                outerEffect = display.boxArtOuterEffect,
                outerEffectThicknessPx = display.boxArtOuterEffectThickness.px,
                glowColorMode = display.glowColorMode,
                accentColor = currentStyle.accentColor,
                secondaryColor = currentStyle.secondaryColor,
                innerEffect = display.boxArtInnerEffect,
                innerEffectThicknessPx = display.boxArtInnerEffectThickness.px,
                systemIconPosition = display.systemIconPosition,
                systemIconPaddingDp = display.systemIconPadding.dp.dp,
                platformIndicatorStyle = display.platformIndicatorStyle,
                platformIndicatorContent = display.platformIndicatorContent
            )

            val previewGradientColors = extractionResult?.let { Pair(it.primary, it.secondary) }

            val previewGame = uiState.previewGame?.let { game ->
                HomeGameUi(
                    id = game.id,
                    title = game.title,
                    platformId = game.platformId,
                    platformSlug = game.platformSlug,
                    platformDisplayName = game.platformSlug.uppercase(),
                    coverPath = game.coverPath,
                    gradientColors = previewGradientColors,
                    backgroundPath = null,
                    developer = null,
                    releaseYear = null,
                    genre = game.genre,
                    isFavorite = game.isFavorite,
                    isDownloaded = game.isDownloaded
                )
            } ?: HomeGameUi(
                id = 0,
                title = "GAME",
                platformId = 0L,
                platformSlug = "snes",
                platformDisplayName = "SNES",
                coverPath = null,
                backgroundPath = null,
                developer = null,
                releaseYear = null,
                genre = null,
                isFavorite = false,
                isDownloaded = false
            )

            val previewAspect = if (display.boxArtShape.isNative) {
                rememberCoverAspectRatio(previewGame.coverPath, display.boxArtShape.aspectRatio)
            } else {
                display.boxArtShape.aspectRatio
            }

            CompositionLocalProvider(LocalBoxArtStyle provides previewBoxArtStyle) {
                GameCard(
                    game = previewGame,
                    isFocused = true,
                    modifier = Modifier
                        .width(Dimens.gameCardWidth)
                        .aspectRatio(previewAspect),
                    showRunningMark = false
                )
            }

            if (showGradientSection && display.gradientAdvancedMode && extractionResult != null) {
                Text(
                    text = stringResource(
                        R.string.settings_box_art_gradient_diagnostics,
                        extractionResult.extractionTimeMs,
                        extractionResult.sampleCount,
                        extractionResult.colorFamiliesUsed
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Dimens.spacingMd)
                )
            }
        }
    }
}

@Composable
private fun BoxArtSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

private fun BoxArtCornerRadius.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtCornerRadius.NONE -> R.string.settings_box_art_corner_radius_none
        BoxArtCornerRadius.SMALL -> R.string.settings_box_art_corner_radius_small
        BoxArtCornerRadius.MEDIUM -> R.string.settings_box_art_corner_radius_medium
        BoxArtCornerRadius.LARGE -> R.string.settings_box_art_corner_radius_large
        BoxArtCornerRadius.EXTRA_LARGE -> R.string.settings_box_art_corner_radius_xl
    }
)

private fun BoxArtBorderThickness.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtBorderThickness.NONE -> R.string.settings_box_art_border_thickness_none
        BoxArtBorderThickness.THIN -> R.string.settings_box_art_border_thickness_thin
        BoxArtBorderThickness.MEDIUM -> R.string.settings_box_art_border_thickness_medium
        BoxArtBorderThickness.THICK -> R.string.settings_box_art_border_thickness_thick
    }
)

private fun BoxArtBorderStyle.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtBorderStyle.SOLID -> R.string.settings_box_art_border_style_solid
        BoxArtBorderStyle.GLASS -> R.string.settings_box_art_border_style_glass
        BoxArtBorderStyle.GRADIENT -> R.string.settings_box_art_border_style_gradient
    }
)

private fun GlassBorderTint.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        GlassBorderTint.OFF -> R.string.settings_box_art_glass_tint_off
        GlassBorderTint.TINT_5 -> R.string.settings_box_art_glass_tint_5
        GlassBorderTint.TINT_10 -> R.string.settings_box_art_glass_tint_10
        GlassBorderTint.TINT_15 -> R.string.settings_box_art_glass_tint_15
        GlassBorderTint.TINT_20 -> R.string.settings_box_art_glass_tint_20
        GlassBorderTint.TINT_25 -> R.string.settings_box_art_glass_tint_25
    }
)

private fun BoxArtGlowStrength.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtGlowStrength.OFF -> R.string.settings_box_art_glow_strength_off
        BoxArtGlowStrength.LOW -> R.string.settings_box_art_glow_strength_low
        BoxArtGlowStrength.MEDIUM -> R.string.settings_box_art_glow_strength_medium
        BoxArtGlowStrength.HIGH -> R.string.settings_box_art_glow_strength_high
        BoxArtGlowStrength.SHADOW_SMALL -> R.string.settings_box_art_glow_strength_shadow_small
        BoxArtGlowStrength.SHADOW_LARGE -> R.string.settings_box_art_glow_strength_shadow_large
    }
)

private fun SystemIconPosition.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        SystemIconPosition.OFF -> R.string.settings_box_art_icon_position_off
        SystemIconPosition.TOP_LEFT -> R.string.settings_box_art_icon_position_top_left
        SystemIconPosition.TOP_RIGHT -> R.string.settings_box_art_icon_position_top_right
        SystemIconPosition.BOTTOM_LEFT -> R.string.settings_box_art_icon_position_bottom_left
        SystemIconPosition.BOTTOM_RIGHT -> R.string.settings_box_art_icon_position_bottom_right
    }
)

private fun SystemIconPadding.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        SystemIconPadding.SMALL -> R.string.settings_box_art_icon_padding_small
        SystemIconPadding.MEDIUM -> R.string.settings_box_art_icon_padding_medium
        SystemIconPadding.LARGE -> R.string.settings_box_art_icon_padding_large
    }
)

private fun PlatformIndicatorStyle.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        PlatformIndicatorStyle.OFF -> R.string.settings_box_art_indicator_style_off
        PlatformIndicatorStyle.TAB -> R.string.settings_box_art_indicator_style_tab
        PlatformIndicatorStyle.SPINE -> R.string.settings_box_art_indicator_style_spine
    }
)

private fun PlatformIndicatorContent.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        PlatformIndicatorContent.NAME -> R.string.settings_box_art_indicator_content_name
        PlatformIndicatorContent.ICON -> R.string.settings_box_art_indicator_content_icon
        PlatformIndicatorContent.NAME_AND_ICON -> R.string.settings_box_art_indicator_content_both
    }
)

private fun BoxArtOuterEffect.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtOuterEffect.OFF -> R.string.settings_box_art_outer_effect_off
        BoxArtOuterEffect.GLOW -> R.string.settings_box_art_outer_effect_glow
        BoxArtOuterEffect.SHADOW -> R.string.settings_box_art_outer_effect_shadow
        BoxArtOuterEffect.SHINE -> R.string.settings_box_art_outer_effect_shine
    }
)

private fun BoxArtOuterEffectThickness.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtOuterEffectThickness.THIN -> R.string.settings_box_art_outer_thickness_thin
        BoxArtOuterEffectThickness.MEDIUM -> R.string.settings_box_art_outer_thickness_medium
        BoxArtOuterEffectThickness.THICK -> R.string.settings_box_art_outer_thickness_thick
    }
)

private fun BoxArtInnerEffect.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtInnerEffect.OFF -> R.string.settings_box_art_inner_effect_off
        BoxArtInnerEffect.GLOW -> R.string.settings_box_art_inner_effect_glow
        BoxArtInnerEffect.SHADOW -> R.string.settings_box_art_inner_effect_shadow
        BoxArtInnerEffect.GLASS -> R.string.settings_box_art_inner_effect_glass
        BoxArtInnerEffect.SHINE -> R.string.settings_box_art_inner_effect_shine
    }
)

private fun BoxArtInnerEffectThickness.displayName(context: android.content.Context): String = context.getString(
    when (this) {
        BoxArtInnerEffectThickness.THIN -> R.string.settings_box_art_inner_thickness_thin
        BoxArtInnerEffectThickness.MEDIUM -> R.string.settings_box_art_inner_thickness_medium
        BoxArtInnerEffectThickness.THICK -> R.string.settings_box_art_inner_thickness_thick
    }
)

@Composable
private fun GradientTuningCycle(
    title: String,
    value: String,
    options: List<String>,
    isFocused: Boolean,
    pickerRequestToken: Int,
    onCycle: (Int) -> Unit
) {
    val currentIndex = options.indexOf(value).coerceAtLeast(0)
    CyclePreference(
        title = title,
        value = value,
        isFocused = isFocused,
        onClick = { onCycle(1) },
        onPrev = { onCycle(-1) },
        options = options,
        onSelect = { onCycle(it - currentIndex) },
        pickerRequestToken = pickerRequestToken
    )
}
