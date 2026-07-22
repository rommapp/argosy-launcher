package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.HueSliderPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SliderPreference
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

    class Header(key: String, section: String, val title: String) : ThemeItem(key, section)

    class SectionSpacer(key: String, section: String) : ThemeItem(key, section)

    data object Mode : ThemeItem("theme", "appearance")
    data object AccentColor : ThemeItem("accentColor", "appearance")
    data object SecondaryColor : ThemeItem("secondaryColor", "appearance")
    data object TintBleed : ThemeItem("tintBleed", "appearance")

    data object BoxArt : ThemeItem("boxArt", "identity")
    data object Backdrop : ThemeItem("backdrop", "identity")
    data object Fonts : ThemeItem("fonts", "identity")
    data object Sounds : ThemeItem("sounds", "identity")
    data object Music : ThemeItem("music", "identity")

    companion object {
        private val AppearanceHeader = Header("appearanceHeader", "appearance", "Appearance")
        private val IdentitySpacer = SectionSpacer("identitySpacer", "identity")
        private val IdentityHeader = Header("identityHeader", "identity", "Identity")

        val ALL: List<ThemeItem> = listOf(
            AppearanceHeader,
            Mode, AccentColor, SecondaryColor, TintBleed,
            IdentitySpacer, IdentityHeader,
            BoxArt, Backdrop, Fonts, Sounds, Music
        )
    }
}

private val themeLayout = SettingsLayout<ThemeItem, Unit>(
    allItems = ThemeItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "appearance" -> "Appearance"
            "identity" -> "Identity"
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

@Composable
fun ThemeSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display
    val currentHue = display.primaryColor?.let { colorIntToHue(it) }
    val secondaryHue = display.secondaryColor?.let { colorIntToHue(it) }

    val visibleItems = remember { themeLayout.visibleItems(Unit) }
    val sections = remember { themeLayout.buildSections(Unit) }

    fun isFocused(item: ThemeItem): Boolean =
        uiState.focusedIndex == themeLayout.focusIndexOf(item, Unit)

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
            is ThemeItem.Header -> ThemeSectionHeader(item.title)
            is ThemeItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeItem.Mode -> CyclePreference(
                title = "Mode",
                value = display.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = isFocused(item),
                onClick = { viewModel.cycleThemeMode(1) },
                onPrev = { viewModel.cycleThemeMode(-1) },
                options = remember { ThemeMode.entries.map { mode -> mode.name.lowercase().replaceFirstChar { c -> c.uppercase() } } },
                onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                pickerRequestToken = pickerToken(item)
            )

            ThemeItem.AccentColor -> HueSliderPreference(
                title = "Accent Color",
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
                title = "Secondary Color",
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

            ThemeItem.TintBleed -> SliderPreference(
                title = "Surface Tint",
                value = display.surfaceTintBleed,
                minValue = 0,
                maxValue = 100,
                step = 10,
                suffix = "%",
                isFocused = isFocused(item),
                onClick = { viewModel.cycleSurfaceTintBleed() },
                onAdjust = { viewModel.adjustSurfaceTintBleed(it) }
            )

            ThemeItem.BoxArt -> NavigationPreference(
                icon = Icons.Outlined.Image,
                title = "Box Art",
                subtitle = "Customize card appearance",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToBoxArt() }
            )

            ThemeItem.Backdrop -> NavigationPreference(
                icon = Icons.Outlined.Texture,
                title = "Surface Backdrop",
                subtitle = if (display.surfaceBackdrop.enabled) display.surfaceBackdrop.preset.displayName else "Off",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToThemeBackdrop() }
            )

            ThemeItem.Fonts -> NavigationPreference(
                icon = Icons.Outlined.TextFields,
                title = "Fonts",
                subtitle = fontsSubtitle(display.displayFontName, display.bodyFontName),
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToThemeFonts() }
            )

            ThemeItem.Sounds -> NavigationPreference(
                icon = Icons.Outlined.MusicNote,
                title = "Sounds",
                subtitle = if (uiState.sounds.enabled) "On, ${uiState.sounds.volume}%" else "Off",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToThemeSounds() }
            )

            ThemeItem.Music -> NavigationPreference(
                icon = Icons.Outlined.LibraryMusic,
                title = "Music",
                subtitle = if (uiState.ambientAudio.enabled) "On, ${uiState.ambientAudio.volume}%" else "Off",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToThemeMusic() }
            )
        }
    }
}

private fun fontsSubtitle(displayFont: String?, bodyFont: String?): String = when {
    displayFont == null && bodyFont == null -> "Default"
    else -> listOf(displayFont ?: "Default", bodyFont ?: "Default").distinct().joinToString(" / ")
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
