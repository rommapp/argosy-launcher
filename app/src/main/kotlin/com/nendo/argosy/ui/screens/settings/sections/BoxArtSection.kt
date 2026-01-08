package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.ListSection
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
import com.nendo.argosy.data.preferences.SystemIconPadding
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.BoxArtStyleConfig
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle

@Composable
fun BoxArtSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val previewRatio = uiState.boxArtPreviewRatio
    val listState = rememberLazyListState()
    val display = uiState.display
    val showIconPadding = display.systemIconPosition != SystemIconPosition.OFF
    val showOuterThickness = display.boxArtOuterEffect != BoxArtOuterEffect.OFF
    val showInnerThickness = display.boxArtInnerEffect != BoxArtInnerEffect.OFF
    val showGlassTint = display.boxArtBorderStyle == BoxArtBorderStyle.GLASS

    var focusIdx = 3
    val glassTintIndex = if (showGlassTint) focusIdx++ else -1
    val iconPosIndex = focusIdx++
    val iconPadIndex = if (showIconPadding) focusIdx++ else -1
    val outerEffectIndex = focusIdx++
    val outerThicknessIndex = if (showOuterThickness) focusIdx++ else -1
    val innerEffectIndex = focusIdx++
    val innerThicknessIndex = if (showInnerThickness) focusIdx++ else -1

    val stylingListEnd = 3 + (if (showGlassTint) 1 else 0)
    val iconListStart = stylingListEnd + 1
    val iconListEnd = iconListStart + 1 + (if (showIconPadding) 1 else 0)
    val outerListStart = iconListEnd + 1
    val outerListEnd = outerListStart + 1 + (if (showOuterThickness) 1 else 0)
    val innerListStart = outerListEnd + 1
    val innerListEnd = innerListStart + 1 + (if (showInnerThickness) 1 else 0)

    val sections = listOf(
        ListSection(listStartIndex = 0, listEndIndex = stylingListEnd, focusStartIndex = 0, focusEndIndex = if (showGlassTint) 3 else 2),
        ListSection(listStartIndex = iconListStart, listEndIndex = iconListEnd, focusStartIndex = iconPosIndex, focusEndIndex = if (showIconPadding) iconPadIndex else iconPosIndex),
        ListSection(listStartIndex = outerListStart, listEndIndex = outerListEnd, focusStartIndex = outerEffectIndex, focusEndIndex = if (showOuterThickness) outerThicknessIndex else outerEffectIndex),
        ListSection(listStartIndex = innerListStart, listEndIndex = innerListEnd, focusStartIndex = innerEffectIndex, focusEndIndex = if (showInnerThickness) innerThicknessIndex else innerEffectIndex)
    )

    val focusToListIndex: (Int) -> Int = { focus ->
        when {
            focus <= 2 -> focus + 1
            focus == glassTintIndex -> 4
            focus == iconPosIndex -> 4 + (if (showGlassTint) 1 else 0) + 1
            focus == iconPadIndex -> 4 + (if (showGlassTint) 1 else 0) + 2
            focus == outerEffectIndex -> iconListEnd + 2
            focus == outerThicknessIndex -> iconListEnd + 3
            focus == innerEffectIndex -> outerListEnd + 2
            focus == innerThicknessIndex -> outerListEnd + 3
            else -> focus + 1
        }
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = focusToListIndex,
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
            item {
                BoxArtSectionHeader("Styling")
            }
            item {
                CyclePreference(
                    title = "Corner Radius",
                    value = display.boxArtCornerRadius.displayName(),
                    isFocused = uiState.focusedIndex == 0,
                    onClick = { viewModel.cycleBoxArtCornerRadius() }
                )
            }
            item {
                CyclePreference(
                    title = "Border Thickness",
                    value = display.boxArtBorderThickness.displayName(),
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.cycleBoxArtBorderThickness() }
                )
            }
            item {
                CyclePreference(
                    title = "Border Style",
                    value = display.boxArtBorderStyle.displayName(),
                    isFocused = uiState.focusedIndex == 2,
                    onClick = { viewModel.cycleBoxArtBorderStyle() }
                )
            }
            if (showGlassTint) {
                item {
                    CyclePreference(
                        title = "Glass Tint",
                        value = display.glassBorderTint.displayName(),
                        isFocused = uiState.focusedIndex == glassTintIndex,
                        onClick = { viewModel.cycleGlassBorderTint() }
                    )
                }
            }
            item {
                BoxArtSectionHeader("System Icon")
            }
            item {
                CyclePreference(
                    title = "Position",
                    value = display.systemIconPosition.displayName(),
                    isFocused = uiState.focusedIndex == iconPosIndex,
                    onClick = { viewModel.cycleSystemIconPosition() }
                )
            }
            if (showIconPadding) {
                item {
                    CyclePreference(
                        title = "Padding",
                        value = display.systemIconPadding.displayName(),
                        isFocused = uiState.focusedIndex == iconPadIndex,
                        onClick = { viewModel.cycleSystemIconPadding() }
                    )
                }
            }
            item {
                BoxArtSectionHeader("Outer Effect")
            }
            item {
                CyclePreference(
                    title = "Effect",
                    value = display.boxArtOuterEffect.displayName(),
                    isFocused = uiState.focusedIndex == outerEffectIndex,
                    onClick = { viewModel.cycleBoxArtOuterEffect() }
                )
            }
            if (showOuterThickness) {
                item {
                    CyclePreference(
                        title = "Thickness",
                        value = display.boxArtOuterEffectThickness.displayName(),
                        isFocused = uiState.focusedIndex == outerThicknessIndex,
                        onClick = { viewModel.cycleBoxArtOuterEffectThickness() }
                    )
                }
            }
            item {
                BoxArtSectionHeader("Inner Effect")
            }
            item {
                CyclePreference(
                    title = "Effect",
                    value = display.boxArtInnerEffect.displayName(),
                    isFocused = uiState.focusedIndex == innerEffectIndex,
                    onClick = { viewModel.cycleBoxArtInnerEffect() }
                )
            }
            if (showInnerThickness) {
                item {
                    CyclePreference(
                        title = "Thickness",
                        value = display.boxArtInnerEffectThickness.displayName(),
                        isFocused = uiState.focusedIndex == innerThicknessIndex,
                        onClick = { viewModel.cycleBoxArtInnerEffectThickness() }
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
            val previewBoxArtStyle = BoxArtStyleConfig(
                cornerRadiusDp = display.boxArtCornerRadius.dp.dp,
                borderThicknessDp = display.boxArtBorderThickness.dp.dp,
                borderStyle = display.boxArtBorderStyle,
                glassBorderTintAlpha = display.glassBorderTint.alpha,
                glowAlpha = display.boxArtGlowStrength.alpha,
                isShadow = display.boxArtGlowStrength.isShadow,
                outerEffect = display.boxArtOuterEffect,
                outerEffectThicknessPx = display.boxArtOuterEffectThickness.px,
                innerEffect = display.boxArtInnerEffect,
                innerEffectThicknessPx = display.boxArtInnerEffectThickness.px,
                systemIconPosition = display.systemIconPosition,
                systemIconPaddingDp = display.systemIconPadding.dp.dp
            )

            val previewGame = uiState.previewGame?.let { game ->
                HomeGameUi(
                    id = game.id,
                    title = game.title,
                    platformId = game.platformId,
                    platformSlug = game.platformSlug,
                    platformDisplayName = game.platformSlug.uppercase(),
                    coverPath = game.coverPath,
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
                        .width(120.dp)
                        .aspectRatio(previewRatio.ratio)
                        .clickable(
                            onClick = { viewModel.cycleNextPreviewRatio() },
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        )
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
