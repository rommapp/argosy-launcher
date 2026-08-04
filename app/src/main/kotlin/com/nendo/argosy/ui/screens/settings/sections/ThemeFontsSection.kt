package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.SliderPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal data class ThemeFontsLayoutState(
    val displayCustom: Boolean,
    val bodyCustom: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = ThemeFontsLayoutState(
            displayCustom = state.display.displayFontName != null,
            bodyCustom = state.display.bodyFontName != null
        )
    }
}

internal sealed class ThemeFontsItem(
    val key: String,
    val section: String,
    val visibleWhen: (ThemeFontsLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = when (this) {
        is Header, is SectionSpacer, Guidelines, Preview -> false
        else -> true
    }

    class Header(key: String, section: String, val title: String) : ThemeFontsItem(key, section)

    class SectionSpacer(key: String, section: String) : ThemeFontsItem(key, section)

    data object DisplaySlot : ThemeFontsItem("displayFont", "display")
    data object DisplayScale : ThemeFontsItem("displayScale", "display")
    data object DisplayRevert : ThemeFontsItem("displayRevert", "display", { it.displayCustom })
    data object BodySlot : ThemeFontsItem("bodyFont", "body")
    data object BodyScale : ThemeFontsItem("bodyScale", "body")
    data object BodyRevert : ThemeFontsItem("bodyRevert", "body", { it.bodyCustom })
    data object Guidelines : ThemeFontsItem("fontGuidelines", "preview")
    data object Preview : ThemeFontsItem("fontPreview", "preview")

    companion object {
        private val DisplayHeader = Header("displayHeader", "display", "Display Font")
        private val BodySpacer = SectionSpacer("bodySpacer", "body")
        private val BodyHeader = Header("bodyHeader", "body", "Body Font")
        private val PreviewSpacer = SectionSpacer("previewSpacer", "preview")
        private val PreviewHeader = Header("previewHeader", "preview", "Preview")

        val ALL: List<ThemeFontsItem>
            get() = listOf(
                DisplayHeader, DisplaySlot, DisplayScale, DisplayRevert,
                BodySpacer, BodyHeader, BodySlot, BodyScale, BodyRevert,
                PreviewSpacer, PreviewHeader, Guidelines, Preview
            )
    }
}

private val themeFontsLayout = SettingsLayout<ThemeFontsItem, ThemeFontsLayoutState>(
    allItems = ThemeFontsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitle = {
        when (it) {
            "display" -> "Display Font"
            "body" -> "Body Font"
            "preview" -> "Preview"
            else -> null
        }
    }
)

internal fun themeFontsMaxFocusIndex(state: ThemeFontsLayoutState): Int =
    themeFontsLayout.maxFocusIndex(state)

internal fun themeFontsItemAtFocusIndex(index: Int, state: ThemeFontsLayoutState): ThemeFontsItem? =
    themeFontsLayout.itemAtFocusIndex(index, state)

internal fun themeFontsSections(state: ThemeFontsLayoutState) = themeFontsLayout.buildSections(state)

@Composable
fun ThemeFontsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val display = uiState.display

    val layoutState = remember(display.displayFontName, display.bodyFontName) {
        ThemeFontsLayoutState(
            displayCustom = display.displayFontName != null,
            bodyCustom = display.bodyFontName != null
        )
    }

    val visibleItems = remember(layoutState) { themeFontsLayout.visibleItems(layoutState) }
    val sections = remember(layoutState) { themeFontsLayout.buildSections(layoutState) }

    fun isFocused(item: ThemeFontsItem): Boolean =
        uiState.focusedIndex == themeFontsLayout.focusIndexOf(item, layoutState)

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { themeFontsLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { it is ThemeFontsItem.SectionSpacer },
        isHeader = { it is ThemeFontsItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is ThemeFontsItem.Header -> ThemeFontsSectionHeader(item.title)
            is ThemeFontsItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeFontsItem.DisplaySlot -> ActionPreference(
                title = "Display Font",
                subtitle = "Titles, headers, and eyebrows",
                icon = Icons.Outlined.TextFields,
                trailingText = display.displayFontName ?: "Default",
                isFocused = isFocused(item),
                onClick = { viewModel.openFontPicker(FontSlot.DISPLAY) }
            )

            ThemeFontsItem.DisplayScale -> SliderPreference(
                title = "Display Scale",
                value = display.displayFontScale,
                minValue = 50,
                maxValue = 150,
                step = 5,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustFontScale(FontSlot.DISPLAY, it) }
            )

            ThemeFontsItem.DisplayRevert -> ActionPreference(
                title = "Revert to Default",
                subtitle = "Remove the imported display font",
                icon = Icons.Outlined.RestartAlt,
                isFocused = isFocused(item),
                onClick = { viewModel.revertFont(FontSlot.DISPLAY) }
            )

            ThemeFontsItem.BodySlot -> ActionPreference(
                title = "Body Font",
                subtitle = "All other UI text",
                icon = Icons.Outlined.TextFields,
                trailingText = display.bodyFontName ?: "Default",
                isFocused = isFocused(item),
                onClick = { viewModel.openFontPicker(FontSlot.BODY) }
            )

            ThemeFontsItem.BodyScale -> SliderPreference(
                title = "Body Scale",
                value = display.bodyFontScale,
                minValue = 50,
                maxValue = 150,
                step = 5,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustFontScale(FontSlot.BODY, it) }
            )

            ThemeFontsItem.BodyRevert -> ActionPreference(
                title = "Revert to Default",
                subtitle = "Remove the imported body font",
                icon = Icons.Outlined.RestartAlt,
                isFocused = isFocused(item),
                onClick = { viewModel.revertFont(FontSlot.BODY) }
            )

            ThemeFontsItem.Guidelines -> FontGuidelines()

            ThemeFontsItem.Preview -> FontPreviewPanel()
        }
    }
}

@Composable
private fun ThemeFontsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = Dimens.spacingXs)
    )
}

@Composable
private fun FontGuidelines() {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
        listOf(
            "TTF and OTF files only; fonts are validated on import.",
            "Favor faces that stay readable at 10-foot TV distance.",
            "Cover weights 400, 500, and 600 or headings may render faux-bold.",
            "Glyphs the font lacks (e.g. CJK) fall back to the system font per glyph."
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FontPreviewPanel() {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = "Continue Playing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Pick up where you left off. Saves, states, and play time stay in sync across your library.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FontPreviewFocusedRow()
    }
}

@Composable
private fun FontPreviewFocusedRow() {
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val accent = LocalArgosyTheme.current.focusAccent
    val surface = MaterialTheme.colorScheme.surface
    val contentColor = lerp(accent, Color.White, 0.45f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.menuRowHeight)
            .clip(shape)
            .background(accent.copy(alpha = 0.15f).compositeOver(surface))
            .border(Dimens.borderThin, accent.copy(alpha = 0.8f), shape)
            .padding(horizontal = Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Focused Menu Row",
            style = MaterialTheme.typography.titleMedium,
            color = contentColor
        )
        Text(
            text = "Aa Bb 0123",
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.65f)
        )
    }
}
