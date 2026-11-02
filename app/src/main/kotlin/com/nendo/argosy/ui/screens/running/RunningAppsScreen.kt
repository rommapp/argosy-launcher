package com.nendo.argosy.ui.screens.running

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.components.CustomTileMenuModal
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun RunningAppsScreen(
    onBack: () -> Unit,
    onOpenGame: (Long) -> Unit,
    viewModel: RunningAppsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.openGame.collect { onOpenGame(it) }
    }
    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) { viewModel.createInputHandler(onBack) }
    val menuInputHandler = remember(viewModel) { viewModel.createMenuInputHandler() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_RUNNING)
                    viewModel.onVisible()
                }
                Lifecycle.Event.ON_PAUSE -> viewModel.onHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_RUNNING)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onHidden()
        }
    }

    ModalInputEffect(active = uiState.menu != null, handler = menuInputHandler)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.running_header_title),
                style = MaterialTheme.typography.headlineMedium,
                color = LocalArgosyTheme.current.textPrimary,
                modifier = Modifier.padding(Dimens.spacingLg)
            )

            when {
                uiState.isLoading -> CenteredContent {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconLg),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.apps.isEmpty() -> CenteredContent {
                    Text(
                        text = stringResource(R.string.running_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LocalArgosyTheme.current.textDim
                    )
                }
                else -> RunningAppsList(
                    uiState = uiState,
                    onTap = viewModel::switchTo,
                    onLongPress = viewModel::openMenu,
                    modifier = Modifier.weight(1f)
                )
            }

            if (uiState.apps.isNotEmpty()) {
                FooterHints(
                    hints = listOf(
                        InputButton.X to stringResource(R.string.running_hint_close),
                        InputButton.Y to stringResource(R.string.running_hint_close_all)
                    ),
                    onHintClick = { button ->
                        when (button) {
                            InputButton.X -> viewModel.requestClose(uiState.focusedIndex)
                            InputButton.Y -> viewModel.requestCloseAll()
                            else -> Unit
                        }
                    }
                )
            }
            FooterSpacer()
        }

        val menu = uiState.menu
        val menuApp = uiState.menuApp
        if (menu != null && menuApp != null) {
            CustomTileMenuModal(
                header = menuApp.label,
                title = menuApp.gameTitle,
                entries = RunningMenuAction.entries.map { stringResource(it.labelRes) },
                focusIndex = menu.focusIndex,
                dangerFromIndex = RunningMenuAction.CLOSE.ordinal,
                onSelect = viewModel::selectMenuAction,
                onDismiss = viewModel::dismissMenu
            )
        }

        when (val confirm = uiState.confirm) {
            is RunningConfirm.CloseGame -> ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(R.string.running_confirm_close_title, confirm.title),
                message = stringResource(R.string.running_confirm_close_message),
                confirmLabel = stringResource(R.string.running_confirm_close_confirm),
                destructive = true,
                onConfirm = viewModel::confirmClose,
                onDismiss = viewModel::dismissConfirm
            )
            is RunningConfirm.CloseAll -> ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(R.string.running_confirm_close_all_title),
                message = stringResource(R.string.running_confirm_close_all_message),
                confirmLabel = stringResource(R.string.running_confirm_close_all_confirm),
                destructive = true,
                onConfirm = viewModel::confirmClose,
                onDismiss = viewModel::dismissConfirm
            )
            null -> Unit
        }
    }
}

@Composable
private fun ColumnScope.CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun RunningAppsList(
    uiState: RunningAppsUiState,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.focusedIndex) {
        listState.animateScrollToItemCentered(uiState.focusedIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
        verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
    ) {
        itemsIndexed(uiState.apps, key = { _, app -> app.packageName }) { index, app ->
            RunningAppRow(
                app = app,
                isFocused = index == uiState.focusedIndex,
                onClick = { onTap(index) },
                onLongClick = { onLongPress(index) }
            )
        }
    }
}

@Composable
private fun RunningAppRow(
    app: RunningAppUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.menuRowHeightLg)
            .clip(shape)
            .argosyFocusIndicators(focused = isFocused, indicators = FocusIndicators.ListRow, shape = shape)
            .clickableNoFocus(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = AppIconData(app.packageName),
            contentDescription = null,
            modifier = Modifier
                .size(Dimens.iconXl)
                .clip(RoundedCornerShape(Dimens.radiusMd))
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            app.gameTitle?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Column(horizontalAlignment = Alignment.End) {
            app.memoryMb?.let { megabytes ->
                Text(
                    text = stringResource(R.string.running_row_memory, megabytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
            app.screenNumber?.let { number ->
                Text(
                    text = stringResource(R.string.running_row_screen, number),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim
                )
            }
        }
    }
}
