package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.data.cache.ImageCacheProgress
import com.nendo.argosy.data.preferences.AnimationSpeed
import com.nendo.argosy.data.preferences.RegionFilterMode
import com.nendo.argosy.data.preferences.SyncFilterPreferences
import com.nendo.argosy.data.preferences.ThemeMode
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.ColorPickerPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.PlatformPreference
import com.nendo.argosy.ui.components.PlatformStatsPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.Dimens

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

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
    }

    DisposableEffect(inputHandler) {
        inputDispatcher.setActiveScreen(inputHandler)
        onDispose {
            inputDispatcher.setActiveScreen(null)
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

    val lifecycleOwner = LocalLifecycleOwner.current
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SettingsHeader(
            title = when (uiState.currentSection) {
                SettingsSection.MAIN -> "SETTINGS"
                SettingsSection.APPEARANCE -> "APPEARANCE"
                SettingsSection.EMULATORS -> "EMULATORS"
                SettingsSection.COLLECTION -> "COLLECTION"
                SettingsSection.SYNC_FILTERS -> "SYNC FILTERS"
                SettingsSection.LIBRARY_BREAKDOWN -> "LIBRARY"
                SettingsSection.ABOUT -> "ABOUT"
            }
        )

        when (uiState.currentSection) {
            SettingsSection.MAIN -> MainSettingsSection(uiState, viewModel)
            SettingsSection.APPEARANCE -> AppearanceSection(uiState, viewModel)
            SettingsSection.EMULATORS -> EmulatorsSection(uiState, viewModel)
            SettingsSection.COLLECTION -> CollectionSection(uiState, viewModel, imageCacheProgress)
            SettingsSection.SYNC_FILTERS -> SyncFiltersSection(uiState, viewModel)
            SettingsSection.LIBRARY_BREAKDOWN -> LibraryBreakdownSection(uiState)
            SettingsSection.ABOUT -> AboutSection(uiState)
        }

        Spacer(modifier = Modifier.weight(1f))

        SettingsFooter()
    }

    if (uiState.showMigrationDialog) {
        val sizeText = formatFileSize(uiState.collection.downloadedGamesSize)
        AlertDialog(
            onDismissRequest = { viewModel.cancelMigration() },
            title = { Text("Migrate Downloads?") },
            text = {
                Text("Move ${uiState.collection.downloadedGamesCount} games ($sizeText) to the new location?")
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

@Composable
private fun MainSettingsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    LazyColumn(
        modifier = Modifier.padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            NavigationPreference(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = "Theme, colors, animations",
                isFocused = uiState.focusedIndex == 0,
                onClick = { viewModel.navigateToSection(SettingsSection.APPEARANCE) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Gamepad,
                title = "Emulators",
                subtitle = "${uiState.emulators.installedEmulators.size} installed",
                isFocused = uiState.focusedIndex == 1,
                onClick = { viewModel.navigateToSection(SettingsSection.EMULATORS) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Folder,
                title = "Collection",
                subtitle = "${uiState.collection.totalGames} games, ${uiState.collection.totalPlatforms} platforms",
                isFocused = uiState.focusedIndex == 2,
                onClick = { viewModel.navigateToSection(SettingsSection.COLLECTION) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Version ${uiState.appVersion}",
                isFocused = uiState.focusedIndex == 3,
                onClick = { viewModel.navigateToSection(SettingsSection.ABOUT) }
            )
        }
    }
}

@Composable
private fun AppearanceSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val presetColors = listOf(
        null to "Default",
        0xFF9575CD.toInt() to "Violet",
        0xFF4DB6AC.toInt() to "Teal",
        0xFFFFB74D.toInt() to "Amber",
        0xFF81C784.toInt() to "Green",
        0xFFF06292.toInt() to "Rose",
        0xFF64B5F6.toInt() to "Blue"
    )

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..5) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            CyclePreference(
                title = "Theme",
                value = uiState.appearance.themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 0,
                onClick = {
                    val next = when (uiState.appearance.themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    viewModel.setThemeMode(next)
                }
            )
        }
        item {
            ColorPickerPreference(
                title = "Accent Color",
                presetColors = presetColors,
                currentColor = uiState.appearance.primaryColor,
                isFocused = uiState.focusedIndex == 1,
                focusedColorIndex = uiState.colorFocusIndex,
                onColorSelect = { viewModel.setPrimaryColor(it) },
                colorCircleContent = { color, isSelected, isColorFocused ->
                    ColorCircle(
                        color = color,
                        isSelected = isSelected,
                        isFocused = isColorFocused
                    )
                }
            )
        }
        item {
            SwitchPreference(
                title = "Haptic Feedback",
                isEnabled = uiState.appearance.hapticEnabled,
                isFocused = uiState.focusedIndex == 2,
                onToggle = { viewModel.setHapticEnabled(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Nintendo Button Layout",
                subtitle = "Swap A/B button mappings",
                isEnabled = uiState.appearance.nintendoButtonLayout,
                isFocused = uiState.focusedIndex == 3,
                onToggle = { viewModel.setNintendoButtonLayout(it) }
            )
        }
        item {
            SwitchPreference(
                title = "Swap Start/Select",
                subtitle = "Flip the Start and Select button functions",
                isEnabled = uiState.appearance.swapStartSelect,
                isFocused = uiState.focusedIndex == 4,
                onToggle = { viewModel.setSwapStartSelect(it) }
            )
        }
        item {
            CyclePreference(
                title = "Animation Speed",
                value = uiState.appearance.animationSpeed.name.lowercase().replaceFirstChar { it.uppercase() },
                isFocused = uiState.focusedIndex == 5,
                onClick = {
                    val next = when (uiState.appearance.animationSpeed) {
                        AnimationSpeed.SLOW -> AnimationSpeed.NORMAL
                        AnimationSpeed.NORMAL -> AnimationSpeed.FAST
                        AnimationSpeed.FAST -> AnimationSpeed.OFF
                        AnimationSpeed.OFF -> AnimationSpeed.SLOW
                    }
                    viewModel.setAnimationSpeed(next)
                }
            )
        }
    }
}

@Composable
private fun ColorCircle(color: Int?, isSelected: Boolean, isFocused: Boolean) {
    val circleColor = if (color != null) Color(color) else Color(0xFF5C6BC0)
    val borderModifier = when {
        isFocused -> Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
        isSelected -> Modifier.border(Dimens.borderMedium, Color.White, CircleShape)
        else -> Modifier
    }

    Box(
        modifier = Modifier
            .size(if (isFocused) 40.dp else Dimens.iconLg)
            .clip(CircleShape)
            .background(circleColor)
            .then(borderModifier),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
    }
}

@Composable
private fun EmulatorsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()
    val focusOffset = if (uiState.emulators.canAutoAssign) 1 else 0

    LaunchedEffect(uiState.focusedIndex) {
        val totalItems = uiState.emulators.platforms.size + focusOffset
        if (totalItems > 0 && uiState.focusedIndex in 0 until totalItems) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            if (uiState.emulators.canAutoAssign) {
                item {
                    ActionPreference(
                        title = "Auto-assign Emulators",
                        subtitle = "Set recommended emulators for all platforms",
                        isFocused = uiState.focusedIndex == 0,
                        onClick = { viewModel.autoAssignAllEmulators() }
                    )
                }
            }
            itemsIndexed(uiState.emulators.platforms) { index, config ->
                PlatformPreference(
                    platformName = config.platform.name,
                    emulatorCount = config.availableEmulators.size,
                    selectedEmulator = if (config.hasInstalledEmulators) config.selectedEmulator else null,
                    isFocused = uiState.focusedIndex == index + focusOffset,
                    isEnabled = config.hasInstalledEmulators,
                    onCycle = { viewModel.showEmulatorPicker(config) }
                )
            }
        }

        if (uiState.emulators.showEmulatorPicker && uiState.emulators.emulatorPickerInfo != null) {
            EmulatorPickerPopup(
                info = uiState.emulators.emulatorPickerInfo,
                focusIndex = uiState.emulators.emulatorPickerFocusIndex,
                onConfirm = { viewModel.confirmEmulatorPickerSelection() },
                onDismiss = { viewModel.dismissEmulatorPicker() }
            )
        }
    }
}

@Composable
private fun CollectionSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    imageCacheProgress: ImageCacheProgress
) {
    if (uiState.romm.rommConfiguring) {
        RomMConfigForm(uiState, viewModel)
    } else {
        val listState = rememberLazyListState()
        val downloadedText = if (uiState.collection.downloadedGamesCount > 0) {
            val sizeText = formatFileSize(uiState.collection.downloadedGamesSize)
            "${uiState.collection.downloadedGamesCount} downloaded ($sizeText)"
        } else {
            "No games downloaded"
        }

        val maxIndex = when {
            uiState.romm.connectionStatus == ConnectionStatus.ONLINE ||
            uiState.romm.connectionStatus == ConnectionStatus.OFFLINE -> 6
            else -> 2
        }

        LaunchedEffect(uiState.focusedIndex) {
            if (uiState.focusedIndex in 0..maxIndex) {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
                val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
                listState.animateScrollToItem(uiState.focusedIndex, -centerOffset)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item {
                NavigationPreference(
                    icon = Icons.Default.Storage,
                    title = "Library  \u2014  ${uiState.collection.totalGames} games",
                    subtitle = downloadedText,
                    isFocused = uiState.focusedIndex == 0,
                    onClick = { viewModel.navigateToSection(SettingsSection.LIBRARY_BREAKDOWN) }
                )
            }
            item {
                ActionPreference(
                    icon = Icons.Default.Folder,
                    title = "Download Location",
                    subtitle = formatStoragePath(uiState.collection.romStoragePath),
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.openFolderPicker() }
                )
            }
            item {
                NavigationPreference(
                    icon = Icons.Default.Dns,
                    title = "Rom Manager",
                    subtitle = when (uiState.romm.connectionStatus) {
                        ConnectionStatus.CHECKING -> "Checking connection..."
                        ConnectionStatus.ONLINE -> uiState.romm.rommUrl
                        ConnectionStatus.OFFLINE -> "${uiState.romm.rommUrl} (offline)"
                        ConnectionStatus.NOT_CONFIGURED -> "Not configured"
                    },
                    isFocused = uiState.focusedIndex == 2,
                    onClick = { viewModel.startRommConfig() }
                )
            }
            if (uiState.romm.connectionStatus == ConnectionStatus.ONLINE ||
                uiState.romm.connectionStatus == ConnectionStatus.OFFLINE) {
                item {
                    val enabledRegions = uiState.syncFilter.syncFilters.enabledRegions
                    val regionsText = if (enabledRegions.isEmpty()) {
                        "All regions"
                    } else {
                        enabledRegions.take(3).joinToString(", ") +
                            if (enabledRegions.size > 3) " +${enabledRegions.size - 3}" else ""
                    }

                    NavigationPreference(
                        icon = Icons.Default.Tune,
                        title = "Sync Filters",
                        subtitle = regionsText,
                        isFocused = uiState.focusedIndex == 3,
                        onClick = { viewModel.navigateToSection(SettingsSection.SYNC_FILTERS) }
                    )
                }
                item {
                    InfoPreference(
                        title = "Sync Images",
                        value = "Background images sync automatically",
                        isFocused = uiState.focusedIndex == 4,
                        icon = Icons.Outlined.Image
                    )
                }
                item {
                    SwitchPreference(
                        title = "Sync Screenshots",
                        subtitle = "Cache screenshots for faster loading",
                        icon = Icons.Outlined.PhotoLibrary,
                        isEnabled = uiState.syncFilter.syncScreenshotsEnabled,
                        isFocused = uiState.focusedIndex == 5,
                        onToggle = { viewModel.toggleSyncScreenshots() }
                    )
                }
                item {
                    val lastSyncText = uiState.romm.lastRommSync?.let { instant ->
                        val formatter = java.time.format.DateTimeFormatter
                            .ofPattern("MMM d, yyyy h:mm a")
                            .withZone(java.time.ZoneId.systemDefault())
                        formatter.format(instant)
                    } ?: "Never"

                    ActionPreference(
                        icon = Icons.Default.Sync,
                        title = "Sync Library",
                        subtitle = "Last sync: $lastSyncText",
                        isFocused = uiState.focusedIndex == 6,
                        isEnabled = uiState.romm.connectionStatus == ConnectionStatus.ONLINE,
                        onClick = { viewModel.syncRomm() }
                    )
                }
                if (imageCacheProgress.isProcessing) {
                    item {
                        ImageCacheProgressItem(imageCacheProgress)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCacheProgressItem(progress: ImageCacheProgress) {
    val disabledAlpha = 0.45f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .padding(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Caching images",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            )
            Text(
                text = "${progress.progressPercent}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = progress.currentGameTitle.take(30) + if (progress.currentGameTitle.length > 30) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha * 0.7f)
            )
            Text(
                text = progress.currentType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha * 0.7f)
            )
        }
    }
}

@Composable
private fun SyncFiltersSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..5) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            item {
                val enabledRegions = uiState.syncFilter.syncFilters.enabledRegions
                val regionsText = if (enabledRegions.isEmpty()) {
                    "None selected"
                } else {
                    enabledRegions.sorted().joinToString(", ")
                }

                ActionPreference(
                    title = "Regions",
                    subtitle = regionsText,
                    isFocused = uiState.focusedIndex == 0,
                    onClick = { viewModel.showRegionPicker() }
                )
            }
            item {
                val modeText = when (uiState.syncFilter.syncFilters.regionMode) {
                    RegionFilterMode.INCLUDE -> "Include selected"
                    RegionFilterMode.EXCLUDE -> "Exclude selected"
                }
                CyclePreference(
                    title = "Region Mode",
                    value = modeText,
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.toggleRegionMode() }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Beta",
                    isEnabled = uiState.syncFilter.syncFilters.excludeBeta,
                    isFocused = uiState.focusedIndex == 2,
                    onToggle = { viewModel.setExcludeBeta(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Prototype",
                    isEnabled = uiState.syncFilter.syncFilters.excludePrototype,
                    isFocused = uiState.focusedIndex == 3,
                    onToggle = { viewModel.setExcludePrototype(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Exclude Demo",
                    isEnabled = uiState.syncFilter.syncFilters.excludeDemo,
                    isFocused = uiState.focusedIndex == 4,
                    onToggle = { viewModel.setExcludeDemo(it) }
                )
            }
            item {
                SwitchPreference(
                    title = "Remove Orphaned Entries",
                    isEnabled = uiState.syncFilter.syncFilters.deleteOrphans,
                    isFocused = uiState.focusedIndex == 5,
                    onToggle = { viewModel.setDeleteOrphans(it) }
                )
            }
        }

        if (uiState.syncFilter.showRegionPicker) {
            RegionPickerPopup(
                enabledRegions = uiState.syncFilter.syncFilters.enabledRegions,
                focusIndex = uiState.syncFilter.regionPickerFocusIndex,
                onToggle = { viewModel.toggleRegion(it) },
                onDismiss = { viewModel.dismissRegionPicker() }
            )
        }
    }
}

@Composable
private fun RegionPickerPopup(
    enabledRegions: Set<String>,
    focusIndex: Int,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val allRegions = SyncFilterPreferences.ALL_KNOWN_REGIONS

    LaunchedEffect(focusIndex) {
        listState.animateScrollToItem(focusIndex.coerceAtLeast(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "SELECT REGIONS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Toggle regions to include/exclude during sync",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(allRegions) { index, region ->
                    val isFocused = focusIndex == index
                    val isSelected = region in enabledRegions
                    RegionPickerItem(
                        name = region,
                        isFocused = isFocused,
                        isSelected = isSelected,
                        onClick = { onToggle(region) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.A to "Toggle",
                    InputButton.B to "Close"
                )
            )
        }
    }
}

@Composable
private fun RegionPickerItem(
    name: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleMedium,
            color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun LibraryBreakdownSection(uiState: SettingsUiState) {
    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.collection.platformBreakdowns.isNotEmpty() && uiState.focusedIndex in uiState.collection.platformBreakdowns.indices) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            listState.animateScrollToItem(uiState.focusedIndex, -centerOffset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(uiState.collection.platformBreakdowns) { index, breakdown ->
            val downloadedText = if (breakdown.downloadedGames > 0) {
                val sizeText = formatFileSize(breakdown.downloadedSize)
                "${breakdown.downloadedGames} downloaded ($sizeText)"
            } else {
                "None downloaded"
            }

            PlatformStatsPreference(
                platformName = breakdown.platformName,
                gamesCount = "${breakdown.totalGames} games",
                downloadedText = downloadedText,
                isFocused = uiState.focusedIndex == index
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
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
private fun RomMConfigForm(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.romm.rommFocusField) {
        when (uiState.romm.rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            1 -> usernameFocusRequester.requestFocus()
            2 -> passwordFocusRequester.requestFocus()
        }
        if (uiState.romm.rommFocusField != null) {
            viewModel.clearRommFocusField()
        }
    }

    Column(
        modifier = Modifier
            .padding(Dimens.spacingMd)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        OutlinedTextField(
            value = uiState.romm.rommConfigUrl,
            onValueChange = { viewModel.setRommConfigUrl(it) },
            label = { Text("Server URL") },
            placeholder = { Text("https://romm.example.com") },
            singleLine = true,
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(urlFocusRequester)
                .then(
                    if (uiState.focusedIndex == 0)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = uiState.romm.rommConfigUsername,
            onValueChange = { viewModel.setRommConfigUsername(it) },
            label = { Text("Username") },
            singleLine = true,
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(usernameFocusRequester)
                .then(
                    if (uiState.focusedIndex == 1)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        OutlinedTextField(
            value = uiState.romm.rommConfigPassword,
            onValueChange = { viewModel.setRommConfigPassword(it) },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(passwordFocusRequester)
                .then(
                    if (uiState.focusedIndex == 2)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        if (uiState.romm.rommConfigError != null) {
            Text(
                text = uiState.romm.rommConfigError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spacingSm)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            modifier = Modifier.fillMaxWidth()
        ) {
            ActionPreference(
                title = if (uiState.romm.rommConnecting) "Connecting..." else "Connect",
                subtitle = "Connect to RomM server",
                isFocused = uiState.focusedIndex == 3,
                onClick = { viewModel.connectToRomm() }
            )
        }

        ActionPreference(
            title = "Cancel",
            subtitle = "Return to RomM settings",
            isFocused = uiState.focusedIndex == 4,
            onClick = { viewModel.cancelRommConfig() }
        )
    }
}

@Composable
private fun AboutSection(uiState: SettingsUiState) {
    LazyColumn(
        modifier = Modifier.padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            InfoPreference(
                title = "Version",
                value = uiState.appVersion,
                isFocused = uiState.focusedIndex == 0
            )
        }
        item {
            InfoPreference(
                title = "Installed Emulators",
                value = "${uiState.emulators.installedEmulators.size} detected",
                isFocused = uiState.focusedIndex == 1
            )
        }
        item {
            InfoPreference(
                title = "Argosy",
                value = "Emulation-focused launcher for Android handhelds",
                isFocused = uiState.focusedIndex == 2
            )
        }
    }
}

@Composable
private fun SettingsFooter() {
    FooterBar(
        hints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.A to "Select",
            InputButton.B to "Back"
        )
    )
}

@Composable
private fun EmulatorPickerPopup(
    info: EmulatorPickerInfo,
    focusIndex: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val installedCount = info.installedEmulators.size
    val hasDownloadSection = info.downloadableEmulators.isNotEmpty()
    val hasInstalled = installedCount > 0

    LaunchedEffect(focusIndex) {
        // Calculate LazyColumn item index from focus index
        // Focus indices: Auto(0 if hasInstalled), installed(1..N or 0..N-1), downloadable(N+1.. or 0..)
        // LazyColumn has header item before downloadable section
        val downloadStartFocusIndex = if (hasInstalled) 1 + installedCount else 0
        val scrollIndex = when {
            !hasInstalled -> {
                // No Auto item, downloadable starts at focus 0
                // LazyColumn: header at 0, downloadable at 1+
                if (hasDownloadSection) focusIndex + 1 else focusIndex
            }
            hasDownloadSection && focusIndex >= downloadStartFocusIndex -> {
                // After installed section, account for header
                focusIndex + 1
            }
            else -> focusIndex
        }
        listState.animateScrollToItem(scrollIndex.coerceAtLeast(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = false, onClick = {})
                .padding(Dimens.spacingLg),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            Text(
                text = "SELECT EMULATOR",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = info.platformName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                // "Auto" option - only show if there are installed emulators
                if (installedCount > 0) {
                    item {
                        val isFocused = focusIndex == 0
                        val isSelected = info.selectedEmulatorName == null
                        EmulatorPickerItem(
                            name = "Auto",
                            subtitle = "Use recommended emulator",
                            isFocused = isFocused,
                            isSelected = isSelected,
                            isDownload = false,
                            onClick = onConfirm
                        )
                    }
                }

                // Installed emulators
                itemsIndexed(info.installedEmulators) { index, emulator ->
                    val itemIndex = 1 + index // Auto is index 0
                    val isFocused = focusIndex == itemIndex
                    val isSelected = emulator.def.displayName == info.selectedEmulatorName
                    EmulatorPickerItem(
                        name = emulator.def.displayName,
                        subtitle = "Installed" + (emulator.versionName?.let { " - v$it" } ?: ""),
                        isFocused = isFocused,
                        isSelected = isSelected,
                        isDownload = false,
                        onClick = onConfirm
                    )
                }

                // Downloadable emulators section
                if (info.downloadableEmulators.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(Dimens.spacingSm))
                        Text(
                            text = "AVAILABLE TO DOWNLOAD",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(horizontal = Dimens.spacingSm)
                        )
                    }

                    itemsIndexed(info.downloadableEmulators) { index, emulator ->
                        // Focus index: if no installed, downloadable starts at 0; otherwise at installedCount + 1
                        val baseIndex = if (installedCount > 0) 1 + installedCount else 0
                        val itemIndex = baseIndex + index
                        val isFocused = focusIndex == itemIndex
                        val isPlayStore = emulator.downloadUrl?.contains("play.google.com") == true
                        EmulatorPickerItem(
                            name = emulator.displayName,
                            subtitle = if (isPlayStore) "Play Store" else "GitHub",
                            isFocused = isFocused,
                            isSelected = false,
                            isDownload = true,
                            onClick = onConfirm
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD to "Navigate",
                    InputButton.A to "Select",
                    InputButton.B to "Close"
                )
            )
        }
    }
}

@Composable
private fun EmulatorPickerItem(
    name: String,
    subtitle: String,
    isFocused: Boolean,
    isSelected: Boolean,
    isDownload: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable(onClick = onClick)
            .padding(Dimens.spacingMd),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        when {
            isSelected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            isDownload -> Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = null,
                tint = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                       else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
