package com.nendo.argosy.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.PinnedCollection
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.rememberDragReorderState
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun ManagePinsScreen(
    onBack: () -> Unit,
    viewModel: ManagePinsViewModel = hiltViewModel()
) {
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MANAGE_PINS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_MANAGE_PINS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current

    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> uiState.pins.any { it.id == key } },
        onLift = { key ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.liftAt(key)
        },
        onMove = { key, index -> viewModel.moveHeldTo(key, index) },
        onDrop = { viewModel.drop() }
    )

    LaunchedEffect(uiState.focusedIndex) {
        if (dragState.draggingKey != null) return@LaunchedEffect
        if (uiState.pins.isNotEmpty() && uiState.focusedIndex in uiState.pins.indices) {
            listState.animateScrollToItemCentered(uiState.focusedIndex)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ManagePinsHeader(onBack = onBack)

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_shell_managepins_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                uiState.pins.isEmpty() -> {
                    EmptyPinsState()
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.dragReorderContainer(dragState),
                        contentPadding = PaddingValues(
                            start = Dimens.spacingLg,
                            end = Dimens.spacingLg,
                            top = Dimens.spacingSm,
                            bottom = Dimens.footerClearance
                        ),
                        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                    ) {
                        itemsIndexed(uiState.pins, key = { _, pin -> pin.id }) { index, pin ->
                            PinRow(
                                pin = pin,
                                isFocused = uiState.focusedIndex == index,
                                isBeingMoved = uiState.reorder?.heldIndex == index,
                                modifier = Modifier
                                    .then(
                                        if (dragState.draggingKey == null || dragState.draggingKey == pin.id) {
                                            Modifier
                                        } else {
                                            Modifier.animateItem()
                                        }
                                    )
                                    .dragReorderItem(dragState, pin.id),
                                onClick = { viewModel.setFocusIndex(index) }
                            )
                        }
                    }
                }
            }
        }

        val hints = if (uiState.isReorderMode) {
            liftedReorderHints(
                move = stringResource(R.string.settings_shell_managepins_reorder_move),
                cancel = stringResource(R.string.settings_shell_managepins_reorder_cancel)
            )
        } else {
            buildList {
                add(InputButton.DPAD to stringResource(R.string.settings_shell_managepins_navigate))
                if (uiState.pins.isNotEmpty()) {
                    add(InputButton.Y to stringResource(R.string.settings_shell_managepins_reorder_hint))
                    add(InputButton.X to stringResource(R.string.settings_shell_managepins_unpin))
                }
                add(InputButton.B to stringResource(R.string.settings_shell_managepins_back_hint))
            }
        }

        FooterHints(
            forced = uiState.isReorderMode,
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> { inputHandler.onConfirm() }
                    InputButton.X -> { inputHandler.onContextMenu() }
                    InputButton.Y -> { inputHandler.onSecondaryAction() }
                    InputButton.B -> { inputHandler.onBack() }
                    else -> Unit
                }
            }
        )
    }
}

@Composable
private fun ManagePinsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_shell_managepins_back_desc),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.settings_shell_managepins_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PinRow(
    pin: PinnedCollection,
    isFocused: Boolean,
    isBeingMoved: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (isBeingMoved) 0.7f else 1f,
        label = "alpha"
    )

    val (icon, typeLabel) = when (pin) {
        is PinnedCollection.Regular ->
            Icons.Default.Folder to stringResource(R.string.settings_shell_managepins_type_collection)
        is PinnedCollection.Virtual -> when (pin.type) {
            CategoryType.GENRE -> Icons.Default.Category to stringResource(R.string.settings_shell_managepins_type_genre)
            CategoryType.GAME_MODE -> Icons.Default.Category to stringResource(R.string.settings_shell_managepins_type_game_mode)
            CategoryType.SERIES -> Icons.Default.Category to stringResource(R.string.settings_shell_managepins_type_series)
        }
    }

    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContentColor = lerp(focusAccent, Color.White, 0.45f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBeingMoved) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.settings_shell_managepins_moving_desc),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconMd)
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isFocused) focusedContentColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pin.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) focusedContentColor
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = pluralStringResource(
                    R.plurals.settings_shell_managepins_row_subtitle,
                    pin.gameCount,
                    typeLabel,
                    pin.gameCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) focusedContentColor.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyPinsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = stringResource(R.string.settings_shell_managepins_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(R.string.settings_shell_managepins_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
