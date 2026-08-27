package com.nendo.argosy.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.ApkAssetMatcher
import com.nendo.argosy.util.formatBytes
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.primitives.ArgosyProgressBar
import com.nendo.argosy.ui.primitives.ProgressBarStyle
import com.nendo.argosy.ui.screens.settings.VariantOption
import com.nendo.argosy.ui.screens.settings.VariantPickerInfo
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun VariantPickerModal(
    info: VariantPickerInfo,
    focusIndex: Int,
    onItemTap: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    FocusedScroll(
        listState = listState,
        focusedIndex = focusIndex
    )

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_variant_picker_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = info.emulatorName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(info.variants, key = { _, v -> v.assetName }) { index, variant ->
                    VariantPickerItem(
                        variant = variant,
                        isFocused = index == focusIndex,
                        onClick = { onItemTap(index) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterHints(
                hints = listOf(
                    InputButton.DPAD to stringResource(R.string.settings_variant_picker_hint_navigate),
                    InputButton.A to stringResource(R.string.settings_variant_picker_hint_select),
                    InputButton.B to stringResource(R.string.settings_variant_picker_hint_cancel)
                ),
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> onConfirm()
                        InputButton.B -> onDismiss()
                        else -> Unit
                    }
                }
            )
        }
    }
}

@Composable
private fun VariantPickerItem(
    variant: VariantOption,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val displayName = ApkAssetMatcher.formatVariantDisplay(variant.variant)
    val fileSize = formatBytes(variant.fileSize)
    val focusContent = lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) focusContent
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = fileSize,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) focusContent.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun EmulatorUpdateModal(
    modal: com.nendo.argosy.ui.screens.settings.EmulatorUpdateModal,
    focusIndex: Int,
    onVariantTap: (Int) -> Unit,
    onConfirmVariant: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    if (modal.state is com.nendo.argosy.ui.screens.settings.UpdateModalState.SelectVariant) {
        FocusedScroll(listState = listState, focusedIndex = focusIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(overlayColor)
            .clickableNoFocus(onClick = {
                val dismissable = modal.state is com.nendo.argosy.ui.screens.settings.UpdateModalState.SelectVariant ||
                    modal.state is com.nendo.argosy.ui.screens.settings.UpdateModalState.Installed ||
                    modal.state is com.nendo.argosy.ui.screens.settings.UpdateModalState.Failed
                if (dismissable) onDismiss()
            }),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(Dimens.modalWidthLg)
                .clip(RoundedCornerShape(Dimens.radiusPanel))
                .background(MaterialTheme.colorScheme.surface)
                .clickableNoFocus(enabled = false) {}
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = stringResource(R.string.settings_emulator_update_title, modal.emulatorName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            when (val state = modal.state) {
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.Fetching -> {
                    Text(
                        text = stringResource(R.string.settings_emulator_update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.SelectVariant -> {
                    Text(
                        text = stringResource(R.string.settings_emulator_update_select_version),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        itemsIndexed(state.variants, key = { _, v -> v.assetName }) { index, variant ->
                            VariantPickerItem(
                                variant = variant,
                                isFocused = index == focusIndex,
                                onClick = { onVariantTap(index) }
                            )
                        }
                    }
                }
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.Downloading -> {
                    val percent = (state.progress * 100).toInt()
                    Text(
                        text = stringResource(R.string.settings_emulator_update_downloading, percent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ArgosyProgressBar(progress = state.progress)
                }
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.WaitingForInstall -> {
                    Text(
                        text = stringResource(R.string.settings_emulator_update_installing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    ArgosyProgressBar(progress = null, style = ProgressBarStyle.Working)
                }
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.Installed -> {
                    Text(
                        text = stringResource(R.string.settings_emulator_update_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is com.nendo.argosy.ui.screens.settings.UpdateModalState.Failed -> {
                    Text(
                        text = stringResource(R.string.settings_emulator_update_failed, state.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

