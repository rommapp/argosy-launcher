package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.GlassBorderTint
import com.nendo.argosy.data.preferences.BoxArtInnerEffectThickness
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffectThickness
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.GameCard
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

    data object IconPos : BoxArtItem("iconPos", "icon")

    data object IconPad : BoxArtItem(
        key = "iconPad",
        section = "icon",
        visibleWhen = { it.systemIconPosition != SystemIconPosition.OFF }
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

        val ALL: List<BoxArtItem> = listOf(
            StylingHeader,
            Shape, CornerRadius, BorderThickness, BorderStyle, GlassTint,
            GradientPresetItem, GradientAdvanced,
            GradientHeader,
            SampleGrid, SampleRadius, MinSaturation, MinBrightness,
            HueDistance, SaturationBoost, BrightnessClamp,
            IconHeader,
            IconPos, IconPad,
            OuterHeader,
            OuterEffect, OuterThickness, GlowIntensity, GlowColor,
            InnerHeader,
            InnerEffect, InnerThickness
        )
    }
}

private val boxArtLayout = SettingsLayout<BoxArtItem, DisplayState>(
    allItems = BoxArtItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section }
)

internal fun boxArtMaxFocusIndex(display: DisplayState): Int = boxArtLayout.maxFocusIndex(display)

internal fun boxArtItemAtFocusIndex(index: Int, display: DisplayState): BoxArtItem? =
    boxArtLayout.itemAtFocusIndex(index, display)

@Composable
fun BoxArtSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val listState = rememberLazyListState()
    val display = uiState.display
    val gradientConfig = uiState.gradientConfig
    val extractionResult = uiState.gradientExtractionResult
    val showGradientSection = display.boxArtBorderStyle == BoxArtBorderStyle.GRADIENT

    val visibleItems = remember(display) { boxArtLayout.visibleItems(display) }
    val sections = remember(display) { boxArtLayout.buildSections(display) }

    fun isFocused(item: BoxArtItem): Boolean =
        uiState.focusedIndex == boxArtLayout.focusIndexOf(item, display)

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { boxArtLayout.focusToListIndex(it, display) },
        sections = sections
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            items(visibleItems, key = { it.key }) { item ->
                when (item) {
                    is BoxArtItem.Header -> BoxArtSectionHeader(item.title)

                    BoxArtItem.Shape -> CyclePreference(
                        title = "Shape",
                        value = display.boxArtShape.displayName,
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtShape() }
                    )
                    BoxArtItem.CornerRadius -> CyclePreference(
                        title = "Corner Radius",
                        value = display.boxArtCornerRadius.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtCornerRadius() }
                    )
                    BoxArtItem.BorderThickness -> CyclePreference(
                        title = "Border Thickness",
                        value = display.boxArtBorderThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtBorderThickness() }
                    )
                    BoxArtItem.BorderStyle -> CyclePreference(
                        title = "Border Style",
                        value = display.boxArtBorderStyle.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtBorderStyle() }
                    )
                    BoxArtItem.GlassTint -> CyclePreference(
                        title = "Glass Tint",
                        value = display.glassBorderTint.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGlassBorderTint() }
                    )
                    BoxArtItem.GradientPresetItem -> CyclePreference(
                        title = "Color Preset",
                        value = display.gradientPreset.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientPreset() }
                    )
                    BoxArtItem.GradientAdvanced -> CyclePreference(
                        title = "Advanced",
                        value = if (display.gradientAdvancedMode) "On" else "Off",
                        isFocused = isFocused(item),
                        onClick = { viewModel.toggleGradientAdvancedMode() }
                    )

                    BoxArtItem.IconPos -> CyclePreference(
                        title = "Position",
                        value = display.systemIconPosition.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSystemIconPosition() }
                    )
                    BoxArtItem.IconPad -> CyclePreference(
                        title = "Padding",
                        value = display.systemIconPadding.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSystemIconPadding() }
                    )

                    BoxArtItem.OuterEffect -> CyclePreference(
                        title = "Effect",
                        value = display.boxArtOuterEffect.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtOuterEffect() }
                    )
                    BoxArtItem.OuterThickness -> CyclePreference(
                        title = "Thickness",
                        value = display.boxArtOuterEffectThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtOuterEffectThickness() }
                    )
                    BoxArtItem.GlowIntensity -> CyclePreference(
                        title = "Intensity",
                        value = display.boxArtGlowStrength.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtGlowStrength() }
                    )
                    BoxArtItem.GlowColor -> CyclePreference(
                        title = "Color",
                        value = display.glowColorMode.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGlowColorMode() }
                    )

                    BoxArtItem.InnerEffect -> CyclePreference(
                        title = "Effect",
                        value = display.boxArtInnerEffect.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtInnerEffect() }
                    )
                    BoxArtItem.InnerThickness -> CyclePreference(
                        title = "Thickness",
                        value = display.boxArtInnerEffectThickness.displayName(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleBoxArtInnerEffectThickness() }
                    )

                    BoxArtItem.SampleGrid -> CyclePreference(
                        title = "Sample Grid",
                        value = "${gradientConfig.samplesX}x${gradientConfig.samplesY}",
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientSampleGrid(1) }
                    )
                    BoxArtItem.SampleRadius -> CyclePreference(
                        title = "Sample Radius",
                        value = gradientConfig.radius.toString(),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientRadius(1) }
                    )
                    BoxArtItem.MinSaturation -> CyclePreference(
                        title = "Min Saturation",
                        value = "%.0f%%".format(gradientConfig.minSaturation * 100),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientMinSaturation(1) }
                    )
                    BoxArtItem.MinBrightness -> CyclePreference(
                        title = "Min Brightness",
                        value = "%.0f%%".format(gradientConfig.minValue * 100),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientMinValue(1) }
                    )
                    BoxArtItem.HueDistance -> CyclePreference(
                        title = "Hue Distance",
                        value = "${gradientConfig.minHueDistance}deg",
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientHueDistance(1) }
                    )
                    BoxArtItem.SaturationBoost -> CyclePreference(
                        title = "Saturation Boost",
                        value = "+%.0f%%".format(gradientConfig.saturationBump * 100),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientSaturationBump(1) }
                    )
                    BoxArtItem.BrightnessClamp -> CyclePreference(
                        title = "Brightness Clamp",
                        value = ">=%.0f%%".format(gradientConfig.valueClamp * 100),
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleGradientValueClamp(1) }
                    )
                }
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
                systemIconPaddingDp = display.systemIconPadding.dp.dp
            )

            val previewGradientColors = if (showGradientSection && extractionResult != null) {
                Pair(extractionResult.primary, extractionResult.secondary)
            } else null

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

            CompositionLocalProvider(LocalBoxArtStyle provides previewBoxArtStyle) {
                GameCard(
                    game = previewGame,
                    isFocused = true,
                    modifier = Modifier
                        .width(Dimens.gameCardWidth)
                        .aspectRatio(display.boxArtShape.aspectRatio)
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
}

private fun SystemIconPadding.displayName(): String = when (this) {
    SystemIconPadding.SMALL -> "Small"
    SystemIconPadding.MEDIUM -> "Medium"
    SystemIconPadding.LARGE -> "Large"
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
