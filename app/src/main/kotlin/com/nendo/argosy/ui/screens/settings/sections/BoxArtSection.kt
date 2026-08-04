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
import androidx.compose.ui.unit.dp
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
import com.nendo.argosy.ui.common.rememberCoverAspectRatio
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.settings.DisplayState
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.BoxArtStyleConfig
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

internal sealed class BoxArtItem(
    val key: String,
    val section: String,
    val visibleWhen: (DisplayState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String, visibleWhen: (DisplayState) -> Boolean = { true })
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
        private val StylingHeader = Header("stylingHeader", "styling", "Styling")
        private val IconHeader = Header("iconHeader", "icon", "System Icon")
        private val OuterHeader = Header("outerHeader", "outer", "Outer Effect")
        private val InnerHeader = Header("innerHeader", "inner", "Inner Effect")
        private val GradientHeader = Header(
            key = "gradientHeader",
            section = "gradient",
            title = "Gradient Colors",
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

private val GRADIENT_PRESET_CHOICES = listOf(
    GradientPreset.VIBRANT,
    GradientPreset.BALANCED,
    GradientPreset.SUBTLE
)

private val boxArtLayout = SettingsLayout<BoxArtItem, DisplayState>(
    allItems = BoxArtItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "styling" -> "Styling"
            "icon" -> "System Icon"
            "outer" -> "Outer Effect"
            "inner" -> "Inner Effect"
            "gradient" -> "Gradient Colors"
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

    val visibleItems = remember(display) { boxArtLayout.visibleItems(display) }
    val sections = remember(display) { boxArtLayout.buildSections(display) }

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
                    is BoxArtItem.Header -> BoxArtSectionHeader(item.title)

                    BoxArtItem.Shape -> CyclePreference(
                        title = "Shape",
                        value = display.boxArtShape.displayName,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtShape() },
                        onPrev = { viewModel.cycleBoxArtShape(-1) },
                        options = remember { BoxArtShape.entries.map { it.displayName } },
                        onSelect = { viewModel.cycleBoxArtShape(it - display.boxArtShape.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.CornerRadius -> CyclePreference(
                        title = "Corner Radius",
                        value = display.boxArtCornerRadius.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtCornerRadius() },
                        onPrev = { viewModel.cycleBoxArtCornerRadius(-1) },
                        options = remember { BoxArtCornerRadius.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtCornerRadius(it - display.boxArtCornerRadius.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.BorderThickness -> CyclePreference(
                        title = "Border Thickness",
                        value = display.boxArtBorderThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtBorderThickness() },
                        onPrev = { viewModel.cycleBoxArtBorderThickness(-1) },
                        options = remember { BoxArtBorderThickness.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtBorderThickness(it - display.boxArtBorderThickness.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.BorderStyle -> CyclePreference(
                        title = "Border Style",
                        value = display.boxArtBorderStyle.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtBorderStyle() },
                        onPrev = { viewModel.cycleBoxArtBorderStyle(-1) },
                        options = remember { BoxArtBorderStyle.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtBorderStyle(it - display.boxArtBorderStyle.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GlassTint -> CyclePreference(
                        title = "Glass Tint",
                        value = display.glassBorderTint.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGlassBorderTint() },
                        onPrev = { viewModel.cycleGlassBorderTint(-1) },
                        options = remember { GlassBorderTint.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleGlassBorderTint(it - display.glassBorderTint.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GradientPresetItem -> CyclePreference(
                        title = "Color Preset",
                        value = display.gradientPreset.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientPreset() },
                        onPrev = { viewModel.cycleGradientPreset(-1) },
                        options = remember { GRADIENT_PRESET_CHOICES.map { it.displayName() } },
                        onSelect = { viewModel.setGradientPreset(GRADIENT_PRESET_CHOICES[it]) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GradientAdvanced -> SwitchPreference(
                        title = "Advanced",
                        isEnabled = display.gradientAdvancedMode,
                        isFocused = isFocused(item),
                        onToggle = { viewModel.toggleGradientAdvancedMode() }
                    )

                    BoxArtItem.IndicatorStyle -> CyclePreference(
                        title = "Style",
                        value = display.platformIndicatorStyle.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cyclePlatformIndicatorStyle() },
                        onPrev = { viewModel.cyclePlatformIndicatorStyle(-1) },
                        options = remember { PlatformIndicatorStyle.entries.map { it.displayName() } },
                        onSelect = { viewModel.cyclePlatformIndicatorStyle(it - display.platformIndicatorStyle.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.IndicatorContent -> CyclePreference(
                        title = "Display",
                        value = display.platformIndicatorContent.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cyclePlatformIndicatorContent() },
                        onPrev = { viewModel.cyclePlatformIndicatorContent(-1) },
                        options = remember { PlatformIndicatorContent.entries.map { it.displayName() } },
                        onSelect = { viewModel.cyclePlatformIndicatorContent(it - display.platformIndicatorContent.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.IconPos -> CyclePreference(
                        title = when (display.platformIndicatorStyle) {
                            PlatformIndicatorStyle.SPINE -> "Spine Corner"
                            PlatformIndicatorStyle.TAB -> "Tab Corner"
                            PlatformIndicatorStyle.OFF -> "Corner"
                        },
                        value = display.systemIconPosition.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSystemIconPosition() },
                        onPrev = { viewModel.cycleSystemIconPosition(-1) },
                        options = remember { SystemIconPosition.CORNERS.map { it.displayName() } },
                        onSelect = { index ->
                            val currentIndex = SystemIconPosition.CORNERS
                                .indexOf(display.systemIconPosition).coerceAtLeast(0)
                            viewModel.cycleSystemIconPosition(index - currentIndex)
                        },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.IconPad -> CyclePreference(
                        title = "Padding",
                        value = display.systemIconPadding.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSystemIconPadding() },
                        onPrev = { viewModel.cycleSystemIconPadding(-1) },
                        options = remember { SystemIconPadding.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleSystemIconPadding(it - display.systemIconPadding.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )

                    BoxArtItem.OuterEffect -> CyclePreference(
                        title = "Effect",
                        value = display.boxArtOuterEffect.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtOuterEffect() },
                        onPrev = { viewModel.cycleBoxArtOuterEffect(-1) },
                        options = remember { BoxArtOuterEffect.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtOuterEffect(it - display.boxArtOuterEffect.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.OuterThickness -> CyclePreference(
                        title = "Thickness",
                        value = display.boxArtOuterEffectThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtOuterEffectThickness() },
                        onPrev = { viewModel.cycleBoxArtOuterEffectThickness(-1) },
                        options = remember { BoxArtOuterEffectThickness.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtOuterEffectThickness(it - display.boxArtOuterEffectThickness.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GlowIntensity -> CyclePreference(
                        title = "Intensity",
                        value = display.boxArtGlowStrength.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtGlowStrength() },
                        onPrev = { viewModel.cycleBoxArtGlowStrength(-1) },
                        options = remember { BoxArtGlowStrength.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtGlowStrength(it - display.boxArtGlowStrength.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.GlowColor -> CyclePreference(
                        title = "Color",
                        value = display.glowColorMode.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGlowColorMode() },
                        onPrev = { viewModel.cycleGlowColorMode(-1) },
                        options = remember { GlowColorMode.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleGlowColorMode(it - display.glowColorMode.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )

                    BoxArtItem.InnerEffect -> CyclePreference(
                        title = "Effect",
                        value = display.boxArtInnerEffect.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtInnerEffect() },
                        onPrev = { viewModel.cycleBoxArtInnerEffect(-1) },
                        options = remember { BoxArtInnerEffect.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtInnerEffect(it - display.boxArtInnerEffect.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )
                    BoxArtItem.InnerThickness -> CyclePreference(
                        title = "Thickness",
                        value = display.boxArtInnerEffectThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtInnerEffectThickness() },
                        onPrev = { viewModel.cycleBoxArtInnerEffectThickness(-1) },
                        options = remember { BoxArtInnerEffectThickness.entries.map { it.displayName() } },
                        onSelect = { viewModel.cycleBoxArtInnerEffectThickness(it - display.boxArtInnerEffectThickness.ordinal) },
                        pickerRequestToken = pickerToken(item)
                    )

                    BoxArtItem.SampleGrid -> GradientTuningCycle(
                        title = "Sample Grid",
                        value = "${gradientConfig.samplesX}x${gradientConfig.samplesY}",
                        options = remember { listOf("8x12", "10x15", "12x18", "16x24") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientSampleGrid(it) }
                    )
                    BoxArtItem.SampleRadius -> GradientTuningCycle(
                        title = "Sample Radius",
                        value = gradientConfig.radius.toString(),
                        options = remember { listOf("1", "2", "3", "4") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientRadius(it) }
                    )
                    BoxArtItem.MinSaturation -> GradientTuningCycle(
                        title = "Min Saturation",
                        value = "%.0f%%".format(gradientConfig.minSaturation * 100),
                        options = remember { listOf("20%", "25%", "30%", "35%", "40%", "45%", "50%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientMinSaturation(it) }
                    )
                    BoxArtItem.MinBrightness -> GradientTuningCycle(
                        title = "Min Brightness",
                        value = "%.0f%%".format(gradientConfig.minValue * 100),
                        options = remember { listOf("10%", "15%", "20%", "25%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientMinValue(it) }
                    )
                    BoxArtItem.HueDistance -> GradientTuningCycle(
                        title = "Hue Distance",
                        value = "${gradientConfig.minHueDistance}deg",
                        options = remember { listOf("20deg", "30deg", "40deg", "50deg", "60deg") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientHueDistance(it) }
                    )
                    BoxArtItem.SaturationBoost -> GradientTuningCycle(
                        title = "Saturation Boost",
                        value = "+%.0f%%".format(gradientConfig.saturationBump * 100),
                        options = remember { listOf("+30%", "+35%", "+40%", "+45%", "+50%", "+55%") },
                        isFocused = isFocused(item),
                        pickerRequestToken = pickerToken(item),
                        onCycle = { viewModel.cycleGradientSaturationBump(it) }
                    )
                    BoxArtItem.BrightnessClamp -> GradientTuningCycle(
                        title = "Brightness Clamp",
                        value = ">=%.0f%%".format(gradientConfig.valueClamp * 100),
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
                    isDownloaded = game.localPath != null
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
                        .aspectRatio(previewAspect)
                )
            }

            if (showGradientSection && display.gradientAdvancedMode && extractionResult != null) {
                Text(
                    text = "${extractionResult.extractionTimeMs}ms | ${extractionResult.sampleCount} samples | ${extractionResult.colorFamiliesUsed} families",
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

private fun BoxArtCornerRadius.displayName(): String = when (this) {
    BoxArtCornerRadius.NONE -> "None"
    BoxArtCornerRadius.SMALL -> "Small"
    BoxArtCornerRadius.MEDIUM -> "Medium"
    BoxArtCornerRadius.LARGE -> "Large"
    BoxArtCornerRadius.EXTRA_LARGE -> "XL"
}

private fun BoxArtBorderThickness.displayName(): String = when (this) {
    BoxArtBorderThickness.NONE -> "None"
    BoxArtBorderThickness.THIN -> "Thin"
    BoxArtBorderThickness.MEDIUM -> "Medium"
    BoxArtBorderThickness.THICK -> "Thick"
}

private fun BoxArtBorderStyle.displayName(): String = when (this) {
    BoxArtBorderStyle.SOLID -> "Solid"
    BoxArtBorderStyle.GLASS -> "Glass"
    BoxArtBorderStyle.GRADIENT -> "Gradient"
}

private fun GlassBorderTint.displayName(): String = when (this) {
    GlassBorderTint.OFF -> "Off"
    GlassBorderTint.TINT_5 -> "5%"
    GlassBorderTint.TINT_10 -> "10%"
    GlassBorderTint.TINT_15 -> "15%"
    GlassBorderTint.TINT_20 -> "20%"
    GlassBorderTint.TINT_25 -> "25%"
}

private fun BoxArtGlowStrength.displayName(): String = when (this) {
    BoxArtGlowStrength.OFF -> "Off"
    BoxArtGlowStrength.LOW -> "Low"
    BoxArtGlowStrength.MEDIUM -> "Medium"
    BoxArtGlowStrength.HIGH -> "High"
    BoxArtGlowStrength.SHADOW_SMALL -> "Shadow S"
    BoxArtGlowStrength.SHADOW_LARGE -> "Shadow L"
}

private fun SystemIconPosition.displayName(): String = when (this) {
    SystemIconPosition.OFF -> "Off"
    SystemIconPosition.TOP_LEFT -> "Top-Left"
    SystemIconPosition.TOP_RIGHT -> "Top-Right"
    SystemIconPosition.BOTTOM_LEFT -> "Bottom-Left"
    SystemIconPosition.BOTTOM_RIGHT -> "Bottom-Right"
}

private fun SystemIconPadding.displayName(): String = when (this) {
    SystemIconPadding.SMALL -> "Small"
    SystemIconPadding.MEDIUM -> "Medium"
    SystemIconPadding.LARGE -> "Large"
}

private fun PlatformIndicatorStyle.displayName(): String = when (this) {
    PlatformIndicatorStyle.OFF -> "Off"
    PlatformIndicatorStyle.TAB -> "Tab"
    PlatformIndicatorStyle.SPINE -> "Spine"
}

private fun PlatformIndicatorContent.displayName(): String = when (this) {
    PlatformIndicatorContent.NAME -> "Name"
    PlatformIndicatorContent.ICON -> "Icon"
    PlatformIndicatorContent.NAME_AND_ICON -> "Name + Icon"
}

private fun BoxArtOuterEffect.displayName(): String = when (this) {
    BoxArtOuterEffect.OFF -> "Off"
    BoxArtOuterEffect.GLOW -> "Glow"
    BoxArtOuterEffect.SHADOW -> "Shadow"
    BoxArtOuterEffect.SHINE -> "Shine"
}

private fun BoxArtOuterEffectThickness.displayName(): String = when (this) {
    BoxArtOuterEffectThickness.THIN -> "Thin"
    BoxArtOuterEffectThickness.MEDIUM -> "Medium"
    BoxArtOuterEffectThickness.THICK -> "Thick"
}

private fun GlowColorMode.displayName(): String = when (this) {
    GlowColorMode.AUTO -> "Auto"
    GlowColorMode.ACCENT -> "Accent"
    GlowColorMode.ACCENT_GRADIENT -> "Theme Gradient"
    GlowColorMode.COVER -> "Cover"
}

private fun BoxArtInnerEffect.displayName(): String = when (this) {
    BoxArtInnerEffect.OFF -> "Off"
    BoxArtInnerEffect.GLOW -> "Glow"
    BoxArtInnerEffect.SHADOW -> "Shadow"
    BoxArtInnerEffect.GLASS -> "Glass"
    BoxArtInnerEffect.SHINE -> "Shine"
}

private fun BoxArtInnerEffectThickness.displayName(): String = when (this) {
    BoxArtInnerEffectThickness.THIN -> "Thin"
    BoxArtInnerEffectThickness.MEDIUM -> "Medium"
    BoxArtInnerEffectThickness.THICK -> "Thick"
}

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
