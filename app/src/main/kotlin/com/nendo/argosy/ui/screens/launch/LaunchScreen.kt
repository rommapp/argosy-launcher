package com.nendo.argosy.ui.screens.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.domain.model.SyncProgress
import com.nendo.argosy.ui.components.DiscPickerModal
import com.nendo.argosy.ui.components.SyncOverlay
import com.nendo.argosy.ui.input.DiscPickerInputHandler
import com.nendo.argosy.ui.input.HardcoreConflictInputHandler
import com.nendo.argosy.ui.input.LocalModifiedInputHandler
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun LaunchScreen(
    gameId: Long,
    channelName: String?,
    discId: Long?,
    viewModel: LaunchViewModel = hiltViewModel(),
    onLaunchComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val inputDispatcher = LocalInputDispatcher.current

    val syncOverlayState by viewModel.syncOverlayState.collectAsState()
    val discPickerState by viewModel.discPickerState.collectAsState()
    val launchIntent by viewModel.launchIntent.collectAsState()
    val launchOptions by viewModel.launchOptions.collectAsState()
    val gameTitle by viewModel.gameTitle.collectAsState()
    val isSessionEnded by viewModel.isSessionEnded.collectAsState()

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val backgroundColor = if (isDarkTheme) Color.Black else Color.White

    var discPickerFocusIndex by remember { mutableIntStateOf(0) }
    var hardcoreConflictFocusIndex by remember { mutableIntStateOf(0) }
    var localModifiedFocusIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(gameId) {
        viewModel.startLaunchFlow(gameId, channelName, discId)
    }

    LaunchedEffect(launchIntent) {
        launchIntent?.let { intent ->
            try {
                if (launchOptions != null) {
                    context.startActivity(intent, launchOptions)
                } else {
                    context.startActivity(intent)
                }
                viewModel.clearLaunchIntent()
            } catch (e: Exception) {
                android.util.Log.e("LaunchScreen", "Failed to start activity", e)
            }
        }
    }

    LaunchedEffect(isSessionEnded) {
        if (isSessionEnded) {
            onLaunchComplete()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.handleSessionEnd {
                    // Session complete callback - isSessionEnded will trigger navigation
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val syncProgress = syncOverlayState?.syncProgress
    val isHardcoreConflict = syncProgress is SyncProgress.HardcoreConflict
    val isLocalModified = syncProgress is SyncProgress.LocalModified
    val showDiscPicker = discPickerState != null

    val hardcoreConflictInputHandler = remember(syncOverlayState) {
        HardcoreConflictInputHandler(
            getFocusIndex = { hardcoreConflictFocusIndex },
            onFocusChange = { hardcoreConflictFocusIndex = it },
            onKeepHardcore = { syncOverlayState?.onKeepHardcore?.invoke() },
            onDowngradeToCasual = { syncOverlayState?.onDowngradeToCasual?.invoke() },
            onKeepLocal = { syncOverlayState?.onKeepLocal?.invoke() }
        )
    }

    val localModifiedInputHandler = remember(syncOverlayState) {
        LocalModifiedInputHandler(
            getFocusIndex = { localModifiedFocusIndex },
            onFocusChange = { localModifiedFocusIndex = it },
            onKeepLocal = { syncOverlayState?.onKeepLocalModified?.invoke() },
            onRestoreSelected = { syncOverlayState?.onRestoreSelected?.invoke() }
        )
    }

    val discPickerInputHandler = remember(discPickerState) {
        DiscPickerInputHandler(
            getDiscs = { discPickerState?.discs ?: emptyList() },
            getFocusIndex = { discPickerFocusIndex },
            onFocusChange = { discPickerFocusIndex = it },
            onSelect = { filePath -> viewModel.selectDisc(filePath) },
            onDismiss = { viewModel.dismissDiscPicker() }
        )
    }

    LaunchedEffect(isHardcoreConflict) {
        if (isHardcoreConflict) {
            hardcoreConflictFocusIndex = 0
            inputDispatcher.pushModal(hardcoreConflictInputHandler)
        }
    }

    DisposableEffect(isHardcoreConflict) {
        onDispose {
            if (isHardcoreConflict) {
                inputDispatcher.popModal()
            }
        }
    }

    LaunchedEffect(isLocalModified) {
        if (isLocalModified) {
            localModifiedFocusIndex = 0
            inputDispatcher.pushModal(localModifiedInputHandler)
        }
    }

    DisposableEffect(isLocalModified) {
        onDispose {
            if (isLocalModified) {
                inputDispatcher.popModal()
            }
        }
    }

    LaunchedEffect(showDiscPicker) {
        if (showDiscPicker) {
            discPickerFocusIndex = 0
            inputDispatcher.pushModal(discPickerInputHandler)
        }
    }

    DisposableEffect(showDiscPicker) {
        onDispose {
            if (showDiscPicker) {
                inputDispatcher.popModal()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        SyncOverlay(
            syncProgress = syncProgress,
            gameTitle = syncOverlayState?.gameTitle ?: gameTitle,
            onGrantPermission = syncOverlayState?.onGrantPermission,
            onDisableSync = syncOverlayState?.onDisableSync,
            onOpenSettings = syncOverlayState?.onOpenSettings,
            onSkip = syncOverlayState?.onSkip,
            onKeepHardcore = syncOverlayState?.onKeepHardcore,
            onDowngradeToCasual = syncOverlayState?.onDowngradeToCasual,
            onKeepLocal = syncOverlayState?.onKeepLocal,
            onKeepLocalModified = syncOverlayState?.onKeepLocalModified,
            onRestoreSelected = syncOverlayState?.onRestoreSelected,
            hardcoreConflictFocusIndex = hardcoreConflictFocusIndex,
            localModifiedFocusIndex = localModifiedFocusIndex
        )

        discPickerState?.let { state ->
            DiscPickerModal(
                discs = state.discs,
                focusIndex = discPickerFocusIndex,
                onSelectDisc = { viewModel.selectDisc(it) },
                onDismiss = { viewModel.dismissDiscPicker() }
            )
        }
    }
}
