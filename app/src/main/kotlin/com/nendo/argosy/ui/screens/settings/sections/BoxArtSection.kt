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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nendo.argosy.data.preferences.BoxArtBorderThickness
import com.nendo.argosy.data.preferences.BoxArtCornerRadius
import com.nendo.argosy.data.preferences.BoxArtGlowStrength
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
import com.nendo.argosy.ui.theme.Motion

@Composable
fun BoxArtSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val previewRatio = uiState.boxArtPreviewRatio
    val listState = rememberLazyListState()
    val display = uiState.display
    val showIconPadding = display.systemIconPosition != SystemIconPosition.OFF
    val maxIndex = if (showIconPadding) 4 else 3

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset + paddingBuffer)
        }
    }

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
                    title = "Glow Effect",
                    value = display.boxArtGlowStrength.displayName(),
                    isFocused = uiState.focusedIndex == 2,
                    onClick = { viewModel.cycleBoxArtGlowStrength() }
                )
            }
            item {
                BoxArtSectionHeader("System Icon")
            }
            item {
                CyclePreference(
                    title = "Position",
                    value = display.systemIconPosition.displayName(),
                    isFocused = uiState.focusedIndex == 3,
                    onClick = { viewModel.cycleSystemIconPosition() }
                )
            }
            if (showIconPadding) {
                item {
                    CyclePreference(
                        title = "Padding",
                        value = display.systemIconPadding.displayName(),
                        isFocused = uiState.focusedIndex == 4,
                        onClick = { viewModel.cycleSystemIconPadding() }
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
                glowAlpha = display.boxArtGlowStrength.alpha,
                systemIconPosition = display.systemIconPosition,
                systemIconPaddingDp = display.systemIconPadding.dp.dp
            )

            val dummyGame = HomeGameUi(
                id = 0,
                title = "GAME",
                platformId = "snes",
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
                    game = dummyGame,
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

private fun BoxArtGlowStrength.displayName(): String = when (this) {
    BoxArtGlowStrength.OFF -> "Off"
    BoxArtGlowStrength.LOW -> "Low"
    BoxArtGlowStrength.MEDIUM -> "Medium"
    BoxArtGlowStrength.HIGH -> "High"
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
