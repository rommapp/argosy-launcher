package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.colorIntToHue
import com.nendo.argosy.ui.theme.hueToColorInt

internal sealed class ThemeItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(key: String, section: String, val titleRes: Int) : ThemeItem(key, section)

    class SectionSpacer(key: String, section: String) : ThemeItem(key, section)

    data object Mode : ThemeItem("theme", "appearance")
    data object AccentColor : ThemeItem("accentColor", "appearance")
    data object AccentFooter : ThemeItem("accentFooter", "appearance")
    data object SecondaryColor : ThemeItem("secondaryColor", "appearance")
    data object TintBleed : ThemeItem("tintBleed", "appearance")

    data object Backdrop : ThemeItem("backdrop", "identity")
    data object Fonts : ThemeItem("fonts", "identity")

    companion object {
        private val AppearanceHeader =
            Header("appearanceHeader", "appearance", R.string.settings_theme_section_appearance)
        private val IdentitySpacer = SectionSpacer("identitySpacer", "identity")
        private val IdentityHeader =
            Header("identityHeader", "identity", R.string.settings_theme_section_identity)

        val ALL: List<ThemeItem>
            get() = listOf(
                AppearanceHeader,
                Mode, AccentColor, AccentFooter, SecondaryColor, TintBleed,
                IdentitySpacer, IdentityHeader,
                Backdrop, Fonts
            )
    }
}

private val themeLayout = SettingsLayout<ThemeItem, Unit>(
    allItems = ThemeItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "appearance" -> R.string.settings_theme_section_appearance
            "identity" -> R.string.settings_theme_section_identity
            else -> null
        }
    }
)

internal fun themeMaxFocusIndex(): Int = themeLayout.maxFocusIndex(Unit)

internal fun themeItemAtFocusIndex(index: Int): ThemeItem? =
    themeLayout.itemAtFocusIndex(index, Unit)

internal fun themeSections() = themeLayout.buildSections(Unit)

internal fun themeFocusIndexOf(item: ThemeItem): Int =
    themeLayout.focusIndexOf(item, Unit)

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.LIGHT -> R.string.settings_theme_mode_light
    ThemeMode.DARK -> R.string.settings_theme_mode_dark
    ThemeMode.SYSTEM -> R.string.settings_theme_mode_system
}

@Composable
fun ThemeSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val context = LocalContext.current
    val currentHue = display.primaryColor?.let { colorIntToHue(it) }
    val secondaryHue = display.secondaryColor?.let { colorIntToHue(it) }

    val visibleItems = remember { themeLayout.visibleItems(Unit) }
    val sections = remember(context) { themeLayout.buildSections(Unit, context) }

    fun isFocused(item: ThemeItem): Boolean =
        uiState.focusedIndex == themeLayout.focusIndexOf(item, Unit)

    fun openFrom(item: ThemeItem, enter: () -> Unit) {
        viewModel.setFocusIndex(themeLayout.focusIndexOf(item, Unit))
        enter()
    }

    fun pickerToken(item: ThemeItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { themeLayout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { it is ThemeItem.SectionSpacer },
        isHeader = { it is ThemeItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is ThemeItem.Header -> ThemeSectionHeader(stringResource(item.titleRes))
            is ThemeItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeItem.Mode -> CyclePreference(
                title = stringResource(R.string.settings_theme_mode_title),
                value = stringResource(themeModeLabelRes(display.themeMode)),
                isFocused = isFocused(item),
                onClick = { viewModel.cycleThemeMode(1) },
                onPrev = { viewModel.cycleThemeMode(-1) },
                options = remember(context) {
                    ThemeMode.entries.map { mode -> context.getString(themeModeLabelRes(mode)) }
                },
                onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeItem.AccentColor -> HueSliderPreference(
                title = stringResource(R.string.settings_theme_accent_color_title),
                currentHue = currentHue,
                isFocused = isFocused(item),
                onHueChange = { hue ->
                    if (hue != null) {
                        viewModel.setPrimaryColor(hueToColorInt(hue))
                    } else {
                        viewModel.resetToDefaultColor()
                    }
                }
            )

            ThemeItem.SecondaryColor -> HueSliderPreference(
                title = stringResource(R.string.settings_theme_secondary_color_title),
                currentHue = secondaryHue,
                isFocused = isFocused(item),
                onHueChange = { hue ->
                    if (hue != null) {
                        viewModel.setSecondaryColor(hueToColorInt(hue))
                    } else {
                        viewModel.resetToDefaultSecondaryColor()
                    }
                }
            )

            ThemeItem.AccentFooter -> SwitchPreference(
                title = stringResource(R.string.settings_theme_accent_footer_title),
                subtitle = stringResource(R.string.settings_theme_accent_footer_subtitle),
                isEnabled = display.useAccentColorFooter,
                isFocused = isFocused(item),
                onToggle = { viewModel.setUseAccentColorFooter(it) }
            )

            ThemeItem.TintBleed -> SliderPreference(
                title = stringResource(R.string.settings_theme_tint_bleed_title),
                value = display.surfaceTintBleed,
                minValue = 0,
                maxValue = 100,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustSurfaceTintBleed(it) }
            )

            ThemeItem.Backdrop -> NavigationPreference(
                icon = Icons.Outlined.Texture,
                title = stringResource(R.string.settings_theme_backdrop_title),
                subtitle = if (display.surfaceBackdrop.enabled) {
                    display.surfaceBackdrop.preset.displayName
                } else {
                    stringResource(R.string.settings_theme_backdrop_subtitle_off)
                },
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeBackdrop() } }
            )

            ThemeItem.Fonts -> NavigationPreference(
                icon = Icons.Outlined.TextFields,
                title = stringResource(R.string.settings_theme_fonts_title),
                subtitle = fontsSubtitle(context, display.displayFontName, display.bodyFontName),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeFonts() } }
            )
        }
    }
}

private fun fontsSubtitle(
    context: android.content.Context,
    displayFont: String?,
    bodyFont: String?
): String {
    val fallback = context.getString(R.string.settings_theme_fonts_subtitle_default)
    if (displayFont == null && bodyFont == null) return fallback
    return listOf(displayFont ?: fallback, bodyFont ?: fallback).distinct().joinToString(" / ")
}

@Composable
private fun ThemeSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}
