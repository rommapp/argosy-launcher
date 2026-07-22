package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.settings.SoundValueLabel
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.delegates.VolumeLevels
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

internal data class ThemeSoundsLayoutState(
    val uiSoundsEnabled: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = ThemeSoundsLayoutState(
            uiSoundsEnabled = state.sounds.enabled
        )
    }
}

internal sealed class ThemeSoundsItem(
    val key: String,
    val section: String,
    val visibleWhen: (ThemeSoundsLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer -> false
        else -> true
    }

    class Header(
        key: String,
        section: String,
        val title: String,
        visibleWhen: (ThemeSoundsLayoutState) -> Boolean = { true }
    ) : ThemeSoundsItem(key, section, visibleWhen)

    class SectionSpacer(key: String, section: String, visibleWhen: (ThemeSoundsLayoutState) -> Boolean = { true })
        : ThemeSoundsItem(key, section, visibleWhen)

    data object UiSoundsToggle : ThemeSoundsItem("uiSoundsToggle", "uiSounds")
    data object UiSoundsVolume : ThemeSoundsItem("uiVolume", "uiSounds", { it.uiSoundsEnabled })

    class SoundTypeItem(val soundType: SoundType) : ThemeSoundsItem(
        key = "soundType_${soundType.name}",
        section = "customize",
        visibleWhen = { it.uiSoundsEnabled }
    )

    companion object {
        private val CustomizeSpacer = SectionSpacer(
            key = "customizeSpacer",
            section = "customize",
            visibleWhen = { it.uiSoundsEnabled }
        )
        private val CustomizeHeader = Header(
            key = "customizeHeader",
            section = "customize",
            title = "Customize",
            visibleWhen = { it.uiSoundsEnabled }
        )

        val ALL: List<ThemeSoundsItem> = listOf(
            UiSoundsToggle, UiSoundsVolume,
            CustomizeSpacer, CustomizeHeader
        ) + SoundType.entries.filterNot { it == SoundType.SILENT }.map { SoundTypeItem(it) }
    }
}

private val themeSoundsLayout = SettingsLayout<ThemeSoundsItem, ThemeSoundsLayoutState>(
    allItems = ThemeSoundsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "uiSounds" -> "UI Sounds"
            "customize" -> "Customize"
            else -> null
        }
    }
)

internal fun themeSoundsMaxFocusIndex(state: ThemeSoundsLayoutState): Int =
    themeSoundsLayout.maxFocusIndex(state)

internal fun themeSoundsItemAtFocusIndex(index: Int, state: ThemeSoundsLayoutState): ThemeSoundsItem? =
    themeSoundsLayout.itemAtFocusIndex(index, state)

internal fun themeSoundsSections(state: ThemeSoundsLayoutState) = themeSoundsLayout.buildSections(state)

@Composable
fun ThemeSoundsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val uiSoundsEnabled = uiState.sounds.enabled

    val layoutState = remember(uiSoundsEnabled) {
        ThemeSoundsLayoutState(uiSoundsEnabled)
    }

    val visibleItems = remember(layoutState) {
        themeSoundsLayout.visibleItems(layoutState)
    }
    val sections = remember(layoutState) {
        themeSoundsLayout.buildSections(layoutState)
    }

    fun isFocused(item: ThemeSoundsItem): Boolean =
        uiState.focusedIndex == themeSoundsLayout.focusIndexOf(item, layoutState)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { themeSoundsLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is ThemeSoundsItem.SectionSpacer },
        isHeader = { it is ThemeSoundsItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is ThemeSoundsItem.Header -> ThemeSoundsSectionHeader(item.title)
            is ThemeSoundsItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeSoundsItem.UiSoundsToggle -> SwitchPreference(
                title = "UI Sounds",
                subtitle = "Play tones on navigation and selection",
                isEnabled = uiState.sounds.enabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.setSoundEnabled(it) }
            )

            ThemeSoundsItem.UiSoundsVolume -> {
                val volumeLevels = VolumeLevels.UI_SOUNDS
                val currentIndex = volumeLevels.indexOfFirst { it >= uiState.sounds.volume }.takeIf { it >= 0 } ?: 0
                SliderPreference(
                    title = "Volume",
                    value = volumeLevels[currentIndex],
                    suffix = "%",
                    minValue = volumeLevels.first(),
                    maxValue = volumeLevels.last(),
                    isFocused = isFocused(item),
                    onClick = {
                        val nextIndex = (currentIndex + 1).mod(volumeLevels.size)
                        viewModel.setSoundVolume(volumeLevels[nextIndex])
                    }
                )
            }

            is ThemeSoundsItem.SoundTypeItem -> SoundCustomizationItem(
                soundType = item.soundType,
                displayValue = uiState.sounds.getSoundValueForType(item.soundType),
                isFocused = isFocused(item),
                onClick = { viewModel.showSoundPicker(item.soundType) }
            )
        }
    }
}

@Composable
private fun ThemeSoundsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

@Composable
private fun SoundCustomizationItem(
    soundType: SoundType,
    displayValue: SoundValueLabel,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val displayName = soundType.name
        .replace("_", " ")
        .lowercase()
        .split(" ")
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContent = lerp(focusAccent, Color.White, 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) focusAccent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = displayName,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) focusedContent
                    else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = displayValue.primary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFocused) focusedContent.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            displayValue.secondary?.let { secondary ->
                val isDark = LocalLauncherTheme.current.isDarkTheme
                val palePrimary = lerp(
                    MaterialTheme.colorScheme.primary,
                    if (isDark) Color.White else Color.Black,
                    0.35f
                )
                val secondaryColor = if (isFocused) focusedContent.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                Text(
                    text = buildAnnotatedString {
                        displayValue.secondaryPrefix?.let { prefix ->
                            withStyle(SpanStyle(color = palePrimary)) { append(prefix) }
                            append(" - ")
                        }
                        append(secondary)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = secondaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
