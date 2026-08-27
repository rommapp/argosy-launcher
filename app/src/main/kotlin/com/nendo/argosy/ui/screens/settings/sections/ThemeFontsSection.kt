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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
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

    class Header(key: String, section: String, val titleRes: Int) : ThemeFontsItem(key, section)

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
        private val DisplayHeader =
            Header("displayHeader", "display", R.string.settings_fonts_section_display)
        private val BodySpacer = SectionSpacer("bodySpacer", "body")
        private val BodyHeader = Header("bodyHeader", "body", R.string.settings_fonts_section_body)
        private val PreviewSpacer = SectionSpacer("previewSpacer", "preview")
        private val PreviewHeader =
            Header("previewHeader", "preview", R.string.settings_fonts_section_preview)

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
    sectionTitleRes = {
        when (it) {
            "display" -> R.string.settings_fonts_section_display
            "body" -> R.string.settings_fonts_section_body
            "preview" -> R.string.settings_fonts_section_preview
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
    val context = LocalContext.current

    val layoutState = remember(display.displayFontName, display.bodyFontName) {
        ThemeFontsLayoutState(
            displayCustom = display.displayFontName != null,
            bodyCustom = display.bodyFontName != null
        )
    }

    val visibleItems = remember(layoutState) { themeFontsLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { themeFontsLayout.buildSections(layoutState, context) }

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
            is ThemeFontsItem.Header -> ThemeFontsSectionHeader(stringResource(item.titleRes))
            is ThemeFontsItem.SectionSpacer -> Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ThemeFontsItem.DisplaySlot -> ActionPreference(
                title = stringResource(R.string.settings_fonts_display_slot_title),
                subtitle = stringResource(R.string.settings_fonts_display_slot_subtitle),
                icon = Icons.Outlined.TextFields,
                trailingText = display.displayFontName
                    ?: stringResource(R.string.settings_fonts_slot_default),
                isFocused = isFocused(item),
                onClick = { viewModel.openFontPicker(FontSlot.DISPLAY) }
            )

            ThemeFontsItem.DisplayScale -> SliderPreference(
                title = stringResource(R.string.settings_fonts_display_scale_title),
                value = display.displayFontScale,
                minValue = 50,
                maxValue = 150,
                step = 5,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustFontScale(FontSlot.DISPLAY, it) }
            )

            ThemeFontsItem.DisplayRevert -> ActionPreference(
                title = stringResource(R.string.settings_fonts_display_revert_title),
                subtitle = stringResource(R.string.settings_fonts_display_revert_subtitle),
                icon = Icons.Outlined.RestartAlt,
                isFocused = isFocused(item),
                onClick = { viewModel.revertFont(FontSlot.DISPLAY) }
            )

            ThemeFontsItem.BodySlot -> ActionPreference(
                title = stringResource(R.string.settings_fonts_body_slot_title),
                subtitle = stringResource(R.string.settings_fonts_body_slot_subtitle),
                icon = Icons.Outlined.TextFields,
                trailingText = display.bodyFontName
                    ?: stringResource(R.string.settings_fonts_slot_default),
                isFocused = isFocused(item),
                onClick = { viewModel.openFontPicker(FontSlot.BODY) }
            )

            ThemeFontsItem.BodyScale -> SliderPreference(
                title = stringResource(R.string.settings_fonts_body_scale_title),
                value = display.bodyFontScale,
                minValue = 50,
                maxValue = 150,
                step = 5,
                suffix = "%",
                isFocused = isFocused(item),
                onAdjust = { viewModel.adjustFontScale(FontSlot.BODY, it) }
            )

            ThemeFontsItem.BodyRevert -> ActionPreference(
                title = stringResource(R.string.settings_fonts_body_revert_title),
                subtitle = stringResource(R.string.settings_fonts_body_revert_subtitle),
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
            stringResource(R.string.settings_fonts_guideline_formats),
            stringResource(R.string.settings_fonts_guideline_legibility),
            stringResource(R.string.settings_fonts_guideline_weights),
            stringResource(R.string.settings_fonts_guideline_fallback)
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
            text = stringResource(R.string.settings_fonts_preview_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.settings_fonts_preview_body),
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
            text = stringResource(R.string.settings_fonts_preview_row),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor
        )
        Text(
            text = stringResource(R.string.settings_fonts_preview_specimen),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor.copy(alpha = 0.65f)
        )
    }
}
