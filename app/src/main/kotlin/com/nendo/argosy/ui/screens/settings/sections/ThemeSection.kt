package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
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
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.colorIntToHue
import com.nendo.argosy.ui.theme.hueToColorInt

internal sealed class ThemeItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : ThemeItem(key, section)

    class SectionSpacer(key: String, section: String) : ThemeItem(key, section)

    sealed class Row(key: String, section: String, @StringRes override val labelRes: Int) :
        ThemeItem(key, section), SearchableSetting

    data object Mode : Row("theme", "appearance", R.string.settings_theme_mode_title)
    data object AccentColor : Row("accentColor", "appearance", R.string.settings_theme_accent_color_title)
    data object AccentFooter : Row("accentFooter", "appearance", R.string.settings_theme_accent_footer_title)
    data object SecondaryColor :
        Row("secondaryColor", "appearance", R.string.settings_theme_secondary_color_title)
    data object TintBleed : Row("tintBleed", "appearance", R.string.settings_theme_tint_bleed_title)

    data object Backdrop : Row("backdrop", "identity", R.string.settings_theme_backdrop_title)
    data object Fonts : Row("fonts", "identity", R.string.settings_theme_fonts_title)
    data object IconPack : Row("iconPack", "identity", R.string.settings_theme_icon_pack_title)

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
                Backdrop, Fonts, IconPack
            )
    }
}

internal val themeLayout =SettingsLayout<ThemeItem, Unit>(
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

            is ThemeItem.Mode -> SegmentedPreference(
                title = stringResource(item.labelRes),
                options = ThemeMode.entries.map { mode -> stringResource(themeModeLabelRes(mode)) },
                selectedIndex = ThemeMode.entries.indexOf(display.themeMode),
                isFocused = isFocused(item),
                onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) }
            )

            is ThemeItem.AccentColor -> HueSliderPreference(
                title = stringResource(item.labelRes),
                currentHue = currentHue,
                isFocused = isFocused(item),
                readableOnTheme = true,
                onHueChange = { hue ->
                    if (hue != null) {
                        viewModel.setPrimaryColor(hueToColorInt(hue))
                    } else {
                        viewModel.resetToDefaultColor()
                    }
                }
            )

            is ThemeItem.SecondaryColor -> HueSliderPreference(
                title = stringResource(item.labelRes),
                currentHue = secondaryHue,
                isFocused = isFocused(item),
                readableOnTheme = true,
                onHueChange = { hue ->
                    if (hue != null) {
                        viewModel.setSecondaryColor(hueToColorInt(hue))
                    } else {
                        viewModel.resetToDefaultSecondaryColor()
                    }
                }
            )

            is ThemeItem.AccentFooter -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_theme_accent_footer_subtitle),
                isEnabled = display.useAccentColorFooter,
                isFocused = isFocused(item),
                onToggle = { viewModel.setUseAccentColorFooter(it) }
            )

            is ThemeItem.TintBleed -> SliderPreference(
                title = stringResource(item.labelRes),
                value = display.surfaceTintBleed,
                minValue = 0,
                maxValue = 100,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustSurfaceTintBleed(it) }
            )

            is ThemeItem.Backdrop -> NavigationPreference(
                icon = Icons.Outlined.Texture,
                title = stringResource(item.labelRes),
                subtitle = if (display.surfaceBackdrop.enabled) {
                    display.surfaceBackdrop.preset.displayName
                } else {
                    stringResource(R.string.settings_theme_backdrop_subtitle_off)
                },
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeBackdrop() } }
            )

            is ThemeItem.Fonts -> NavigationPreference(
                icon = Icons.Outlined.TextFields,
                title = stringResource(item.labelRes),
                subtitle = fontsSubtitle(context, display.displayFontName, display.bodyFontName),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToThemeFonts() } }
            )

            is ThemeItem.IconPack -> IconPackRow(
                title = stringResource(item.labelRes),
                uiState = uiState,
                isFocused = isFocused(item),
                onCycle = { direction -> openFrom(item) { viewModel.cycleIconPack(direction) } },
                onSelect = { packageName -> viewModel.setIconPack(packageName) }
            )
        }
    }
}

@Composable
private fun IconPackRow(
    title: String,
    uiState: SettingsUiState,
    isFocused: Boolean,
    onCycle: (Int) -> Unit,
    onSelect: (String?) -> Unit
) {
    val display = uiState.display
    val labels = listOf(stringResource(R.string.settings_theme_icon_pack_system)) +
        display.iconPacks.map { it.label }
    val subtitle = if (display.iconPacks.isEmpty()) {
        stringResource(R.string.settings_theme_icon_pack_subtitle_none)
    } else {
        stringResource(R.string.settings_theme_icon_pack_subtitle)
    }
    CyclePreference(
        title = title,
        subtitle = subtitle,
        value = labels[display.selectedIconPackIndex],
        isFocused = isFocused,
        onClick = { onCycle(1) },
        onPrev = { onCycle(-1) },
        options = labels.takeIf { it.size > 1 },
        onSelect = { index -> onSelect(display.iconPackOptions.getOrNull(index)) },
        pickerRequestToken = if (uiState.enumPickerKey == ThemeItem.IconPack.key) uiState.enumPickerToken else 0
    )
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
