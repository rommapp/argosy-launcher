package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.settings.components.EmulatorPickerPopup
import com.nendo.argosy.ui.screens.settings.components.SoundPickerPopup
import com.nendo.argosy.ui.screens.settings.sections.AboutSection
import com.nendo.argosy.ui.screens.settings.sections.ControlsSection
import com.nendo.argosy.ui.screens.settings.sections.DisplaySection
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsSection
import com.nendo.argosy.ui.screens.settings.sections.GameDataSection
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.SoundsSection
import com.nendo.argosy.ui.screens.settings.sections.SteamSection
import com.nendo.argosy.ui.screens.settings.sections.StorageSection
import com.nendo.argosy.ui.screens.settings.sections.SyncFiltersSection
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.formatFileSize
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageCacheProgress by viewModel.imageCacheProgress.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val filePath = getFilePathFromUri(context, it)
            if (filePath != null) {
                viewModel.setStoragePath(filePath)
            }
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if permission can't be persisted
            }
            viewModel.setCustomBackgroundPath(it.toString())
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SETTINGS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SETTINGS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            folderPickerLauncher.launch(null)
            viewModel.clearFolderPickerFlag()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collect { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.downloadUpdateEvent.collect {
            viewModel.downloadAndInstallUpdate(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestStoragePermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openBackgroundPickerEvent.collect {
            backgroundPickerLauncher.launch(arrayOf("image/*"))
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEmulators()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val soundPickerBlur by animateDpAsState(
        targetValue = if (uiState.sounds.showSoundPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "soundPickerBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(soundPickerBlur)
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsHeader(
                title = when (uiState.currentSection) {
                    SettingsSection.MAIN -> "SETTINGS"
                    SettingsSection.SERVER -> "GAME DATA"
                    SettingsSection.SYNC_SETTINGS -> "SYNC SETTINGS"
                    SettingsSection.SYNC_FILTERS -> "METADATA FILTERS"
                    SettingsSection.STEAM_SETTINGS -> "STEAM (EXPERIMENTAL)"
                    SettingsSection.STORAGE -> "STORAGE"
                    SettingsSection.DISPLAY -> "DISPLAY"
                    SettingsSection.CONTROLS -> "CONTROLS"
                    SettingsSection.SOUNDS -> "SOUNDS"
                    SettingsSection.EMULATORS -> "EMULATORS"
                    SettingsSection.ABOUT -> "ABOUT"
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentSection) {
                    SettingsSection.MAIN -> MainSettingsSection(uiState, viewModel)
                    SettingsSection.SERVER -> GameDataSection(uiState, viewModel)
                    SettingsSection.SYNC_SETTINGS -> SyncSettingsSection(uiState, viewModel, imageCacheProgress)
                    SettingsSection.SYNC_FILTERS -> SyncFiltersSection(uiState, viewModel)
                    SettingsSection.STEAM_SETTINGS -> SteamSection(uiState, viewModel)
                    SettingsSection.STORAGE -> StorageSection(uiState, viewModel)
                    SettingsSection.DISPLAY -> DisplaySection(uiState, viewModel)
                    SettingsSection.CONTROLS -> ControlsSection(uiState, viewModel)
                    SettingsSection.SOUNDS -> SoundsSection(uiState, viewModel)
                    SettingsSection.EMULATORS -> EmulatorsSection(uiState, viewModel)
                    SettingsSection.ABOUT -> AboutSection(uiState, viewModel)
                }
            }

            SettingsFooter()
        }

        AnimatedVisibility(
            visible = uiState.sounds.showSoundPicker && uiState.sounds.soundPickerType != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.sounds.soundPickerType?.let { soundType ->
                SoundPickerPopup(
                    soundType = soundType,
                    presets = uiState.sounds.presets,
                    focusIndex = uiState.sounds.soundPickerFocusIndex,
                    currentPreset = uiState.sounds.getCurrentPresetForType(soundType),
                    onConfirm = { viewModel.confirmSoundPickerSelection() },
                    onDismiss = { viewModel.dismissSoundPicker() }
                )
            }
        }
    }

    if (uiState.showMigrationDialog) {
        val sizeText = formatFileSize(uiState.storage.downloadedGamesSize)
        AlertDialog(
            onDismissRequest = { viewModel.cancelMigration() },
            title = { Text("Migrate Downloads?") },
            text = {
                Text("Move ${uiState.storage.downloadedGamesCount} games ($sizeText) to the new location?")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmMigration() }) {
                    Text("Migrate")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.cancelMigration() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { viewModel.skipMigration() }) {
                        Text("Skip")
                    }
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatStoragePath(rawPath: String): String {
    if (rawPath.isBlank()) return "Not set"
    val decoded = Uri.decode(rawPath)

    // Extract tree path from various URI formats
    val treePath = when {
        decoded.contains("/tree/") -> decoded.substringAfter("/tree/")
        else -> decoded
    }

    // Handle drive:path format (e.g., "primary:Games" or "SDCARD-ID:path")
    if (treePath.contains(":")) {
        val drive = treePath.substringBefore(":")
        val folder = treePath.substringAfter(":")
        val driveName = if (drive == "primary") "Internal" else "SD Card"
        return if (folder.isEmpty()) driveName else "$driveName:/$folder"
    }

    // Handle regular file paths
    val externalStorage = Environment.getExternalStorageDirectory().absolutePath
    return when {
        treePath.startsWith(externalStorage) -> {
            val relative = treePath.removePrefix(externalStorage).trimStart('/')
            if (relative.isEmpty()) "Internal" else "Internal:/$relative"
        }
        treePath.startsWith("/storage/") -> {
            val parts = treePath.removePrefix("/storage/").split("/", limit = 2)
            if (parts.size == 2) "SD Card:/${parts[1]}" else "SD Card"
        }
        else -> treePath.substringAfterLast("/").ifEmpty { treePath }
    }
}

@Suppress("UNUSED_PARAMETER")
private fun getFilePathFromUri(context: Context, uri: Uri): String? {
    val rawPath = uri.path ?: return null
    val path = Uri.decode(rawPath)

    // Tree URIs have format: /tree/primary:path/to/folder
    val treePath = path.substringAfter("/tree/", "")
    if (treePath.isEmpty()) return null

    return when {
        treePath.startsWith("primary:") -> {
            val relativePath = treePath.removePrefix("primary:")
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }
        treePath.contains(":") -> {
            // External SD card: storage-id:path
            val parts = treePath.split(":", limit = 2)
            if (parts.size == 2) {
                "/storage/${parts[0]}/${parts[1]}"
            } else null
        }
        else -> null
    }
}

@Composable
private fun SettingsFooter() {
    FooterBar(
        hints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.SOUTH to "Select",
            InputButton.EAST to "Back"
        )
    )
}

