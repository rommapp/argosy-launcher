package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * A folder opened over the grid. It takes the input stack for as long as it is composed, so the
 * pad drives [inputHandler] and nothing behind it. A tap on a member focuses it, and a tap on the
 * focused member activates it, the way a tap works on the grid. [onRemoveFocused] is null for an
 * auto-folder, whose members come from its rule. [backdropPath] fills the screen behind the panel,
 * blurred when [blurBackdrop]; [accent] tints the title and the focus ring.
 */
@Composable
fun FolderOverlay(
    title: String,
    members: List<HomeTile>,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    focusIndex: Int,
    isLoading: Boolean,
    isAuto: Boolean,
    columns: Int,
    accent: Color,
    backdropPath: String?,
    blurBackdrop: Boolean,
    inputHandler: InputHandler,
    onFocusMember: (Int) -> Unit,
    onActivateFocused: () -> Unit,
    onRemoveFocused: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    ModalInputEffect(active = true, handler = inputHandler)
    val removeLabel = stringResource(R.string.ui_folder_overlay_hint_remove)
    val gridState = rememberLazyGridState()
    LaunchedEffect(focusIndex, members.size) {
        if (focusIndex in members.indices) gridState.animateScrollToItem(focusIndex)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (backdropPath != null) {
            coil.compose.AsyncImage(
                model = rememberFileImageModel(backdropPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blurBackdrop) Modifier.blur(Dimens.gameArtBackdropBlur) else Modifier)
            )
        }
        FolderPanel(
            title = title,
            members = members,
            contentFor = contentFor,
            focusIndex = focusIndex,
            isLoading = isLoading,
            isAuto = isAuto,
            columns = columns,
            accent = accent,
            gridState = gridState,
            removeLabel = removeLabel,
            onFocusMember = onFocusMember,
            onActivateFocused = onActivateFocused,
            onRemoveFocused = onRemoveFocused,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun FolderPanel(
    title: String,
    members: List<HomeTile>,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    focusIndex: Int,
    isLoading: Boolean,
    isAuto: Boolean,
    columns: Int,
    accent: Color,
    gridState: LazyGridState,
    removeLabel: String,
    onFocusMember: (Int) -> Unit,
    onActivateFocused: () -> Unit,
    onRemoveFocused: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    Modal(
        title = title,
        titleContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        },
        baseWidth = Dimens.modalWidthXl,
        onDismiss = onDismiss,
        footerHints = if (onRemoveFocused != null && members.isNotEmpty()) {
            listOf(InputButton.X to removeLabel)
        } else {
            null
        },
        inlineFooterHints = true,
        onFooterHintClick = { button -> if (button == InputButton.X) onRemoveFocused?.invoke() }
    ) {
        if (members.isEmpty()) {
            Text(
                text = stringResource(
                    when {
                        isLoading -> R.string.ui_folder_overlay_loading
                        isAuto -> R.string.ui_folder_overlay_empty_auto
                        else -> R.string.ui_folder_overlay_empty
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(vertical = Dimens.spacingLg)
            )
            return@Modal
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            itemsIndexed(members, key = { _, member -> member.id }) { index, member ->
                FolderMemberCell(
                    content = contentFor(member),
                    isFocused = index == focusIndex,
                    accent = accent,
                    onClick = {
                        if (index == focusIndex) onActivateFocused() else onFocusMember(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun FolderMemberCell(
    content: CustomGridTileContent?,
    isFocused: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.Ring, tint = accent, shape = shape)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingXs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            val game = content?.game
            val gameCover = game?.let { rememberResolvedCoverPath(it.id, it.coverPath) }
            val coverPath = content?.customCoverPath
                ?: gameCover
                ?: content?.coverPath
                ?: content?.decor?.path
            val appIcon = content?.packageName?.let { com.nendo.argosy.ui.coil.AppIconData(it) }
            val poster = content?.posterUrl?.takeIf { it.isNotBlank() }
            when {
                appIcon != null -> coil.compose.AsyncImage(
                    model = appIcon,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(Dimens.spacingSm)
                )
                poster != null -> coil.compose.AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                coverPath != null -> coil.compose.AsyncImage(
                    model = rememberFileImageModel(coverPath),
                    contentDescription = null,
                    contentScale = if (content?.decor?.isSticker == true) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                else -> Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = theme.textDim,
                    modifier = Modifier.size(Dimens.iconMd)
                )
            }
        }
        Text(
            text = content?.label.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
