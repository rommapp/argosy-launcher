package com.nendo.argosy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.FolderAppearance
import com.nendo.argosy.domain.model.TileImageSource
import com.nendo.argosy.ui.common.color
import com.nendo.argosy.ui.common.folderOutlineLabelRes
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.EnumValueControl
import com.nendo.argosy.ui.theme.Dimens

/**
 * The folder appearance panel. It takes the input stack while composed, so the pad drives
 * [inputHandler]. A tap on a row focuses it and acts on it the way A or Right would.
 */
@Composable
fun FolderAppearanceModal(
    folderName: String,
    appearance: FolderAppearance,
    rows: List<FolderAppearanceRow>,
    focusIndex: Int,
    inputHandler: InputHandler,
    onFocusRow: (Int) -> Unit,
    onCycle: (FolderAppearanceRow, Int) -> Unit,
    onActivate: (FolderAppearanceRow) -> Unit,
    onDismiss: () -> Unit
) {
    ModalInputEffect(active = true, handler = inputHandler)
    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex, rows.size) {
        if (focusIndex in rows.indices) listState.animateScrollToItem(focusIndex)
    }
    Modal(
        title = stringResource(R.string.ui_folder_appearance_title),
        subtitle = folderName.takeIf { it.isNotBlank() },
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            itemsIndexed(rows, key = { _, row -> row.name }) { index, row ->
                AppearanceRow(
                    row = row,
                    appearance = appearance,
                    isFocused = index == focusIndex,
                    onCycle = { delta ->
                        onFocusRow(index)
                        onCycle(row, delta)
                    },
                    onActivate = {
                        onFocusRow(index)
                        onActivate(row)
                    }
                )
            }
        }
    }
}

@Composable
private fun AppearanceRow(
    row: FolderAppearanceRow,
    appearance: FolderAppearance,
    isFocused: Boolean,
    onCycle: (Int) -> Unit,
    onActivate: () -> Unit
) {
    when (row) {
        FolderAppearanceRow.COVER -> CycleRow(
            title = stringResource(R.string.ui_folder_appearance_cover),
            value = stringResource(appearance.cover.labelRes),
            isFocused = isFocused,
            onCycle = onCycle
        )
        FolderAppearanceRow.COVER_PICTURE -> PictureRow(
            title = stringResource(R.string.ui_folder_appearance_cover_picture),
            source = appearance.coverSource,
            isFocused = isFocused,
            onActivate = onActivate
        )
        FolderAppearanceRow.COVER_OUTLINE -> CycleRow(
            title = stringResource(R.string.ui_folder_appearance_outline),
            value = stringResource(
                folderOutlineLabelRes(appearance.coverOutline.color.takeIf { appearance.coverOutline.enabled })
            ),
            isFocused = isFocused,
            onCycle = onCycle
        )
        FolderAppearanceRow.SHOW_NAME -> SwitchPreference(
            title = stringResource(R.string.ui_folder_appearance_show_name),
            isEnabled = appearance.showName,
            isFocused = isFocused,
            onToggle = { onActivate() }
        )
        FolderAppearanceRow.BACKDROP -> CycleRow(
            title = stringResource(R.string.ui_folder_appearance_backdrop),
            value = stringResource(appearance.backdrop.labelRes),
            isFocused = isFocused,
            onCycle = onCycle
        )
        FolderAppearanceRow.BACKDROP_PICTURE -> PictureRow(
            title = stringResource(R.string.ui_folder_appearance_backdrop_picture),
            source = appearance.backdropSource,
            isFocused = isFocused,
            onActivate = onActivate
        )
        FolderAppearanceRow.ACCENT -> ActionPreference(
            title = stringResource(R.string.ui_folder_appearance_accent),
            subtitle = null,
            isFocused = isFocused,
            icon = Icons.Filled.Circle,
            iconTint = appearance.accent.color,
            trailingContent = {
                EnumValueControl(
                    value = stringResource(appearance.accent.labelRes),
                    focused = isFocused,
                    onPrev = { onCycle(-1) },
                    onNext = { onCycle(1) },
                    onOpen = { onCycle(1) }
                )
            },
            onClick = { onCycle(1) }
        )
        FolderAppearanceRow.MEMBER_SIZE -> CycleRow(
            title = stringResource(R.string.ui_folder_appearance_member_size),
            value = stringResource(appearance.memberSize.labelRes),
            isFocused = isFocused,
            onCycle = onCycle
        )
    }
}

@Composable
private fun CycleRow(title: String, value: String, isFocused: Boolean, onCycle: (Int) -> Unit) {
    CyclePreference(
        title = title,
        value = value,
        isFocused = isFocused,
        onClick = { onCycle(1) },
        onPrev = { onCycle(-1) }
    )
}

@Composable
private fun PictureRow(title: String, source: TileImageSource?, isFocused: Boolean, onActivate: () -> Unit) {
    ActionPreference(
        title = title,
        subtitle = stringResource(
            if (source == null) R.string.ui_folder_appearance_picture_none else R.string.ui_folder_appearance_picture_chosen
        ),
        isFocused = isFocused,
        onClick = onActivate
    )
}
