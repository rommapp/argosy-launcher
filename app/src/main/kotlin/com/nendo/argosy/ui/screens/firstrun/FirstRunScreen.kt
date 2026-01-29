package com.nendo.argosy.ui.screens.firstrun

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import com.nendo.argosy.data.local.entity.PlatformEntity
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun FirstRunScreen(
    onComplete: () -> Unit,
    viewModel: FirstRunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val requestPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }

    val requestUsageStats = {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val chooseFolder = { viewModel.openFolderPicker() }
    val chooseImageCacheFolder = { viewModel.openImageCachePicker() }

    var showFileBrowser by remember { mutableStateOf(false) }
    var showImageCacheBrowser by remember { mutableStateOf(false) }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onComplete) {
        viewModel.createInputHandler(
            onComplete = onComplete,
            onRequestPermission = requestPermission,
            onChooseFolder = chooseFolder,
            onChooseImageCacheFolder = chooseImageCacheFolder,
            onRequestUsageStats = requestUsageStats
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            showFileBrowser = true
            viewModel.clearFolderPickerFlag()
        }
    }

    LaunchedEffect(uiState.launchImageCachePicker) {
        if (uiState.launchImageCachePicker) {
            showImageCacheBrowser = true
            viewModel.clearImageCachePickerFlag()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkStoragePermission()
        viewModel.checkUsageStatsPermission()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = 0.85f,
                scaleY = 0.85f
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                fadeIn(animationSpec = tween(150)) togetherWith fadeOut(animationSpec = tween(150))
            },
            label = "wizard_step"
        ) { step ->
            when (step) {
                FirstRunStep.WELCOME -> WelcomeStep(
                    isFocused = true,
                    onGetStarted = { viewModel.nextStep() }
                )
                FirstRunStep.ROMM_LOGIN -> RommLoginStep(
                    url = uiState.rommUrl,
                    username = uiState.rommUsername,
                    password = uiState.rommPassword,
                    isConnecting = uiState.isConnecting,
                    error = uiState.connectionError,
                    focusedIndex = uiState.focusedIndex,
                    rommFocusField = uiState.rommFocusField,
                    onUrlChange = viewModel::setRommUrl,
                    onUsernameChange = viewModel::setRommUsername,
                    onPasswordChange = viewModel::setRommPassword,
                    onConnect = { viewModel.connectToRomm() },
                    onBack = { viewModel.previousStep() },
                    onClearFocusField = { viewModel.clearRommFocusField() }
                )
                FirstRunStep.ROMM_SUCCESS -> RommSuccessStep(
                    serverName = uiState.rommUrl,
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    isFocused = true,
                    onContinue = { viewModel.nextStep() }
                )
                FirstRunStep.ROM_PATH -> RomPathStep(
                    currentPath = uiState.romStoragePath,
                    folderSelected = uiState.folderSelected,
                    hasStoragePermission = uiState.hasStoragePermission,
                    focusedIndex = uiState.focusedIndex,
                    onRequestPermission = requestPermission,
                    onChooseFolder = chooseFolder,
                    onContinue = { viewModel.proceedFromRomPath() }
                )
                FirstRunStep.IMAGE_CACHE -> ImageCacheStep(
                    currentPath = uiState.imageCachePath,
                    folderSelected = uiState.imageCacheFolderSelected,
                    focusedIndex = uiState.focusedIndex,
                    onChooseFolder = chooseImageCacheFolder,
                    onContinue = { viewModel.proceedFromImageCache() },
                    onSkip = { viewModel.skipImageCachePath() }
                )
                FirstRunStep.SAVE_SYNC -> SaveSyncStep(
                    focusedIndex = uiState.focusedIndex,
                    onEnable = { viewModel.enableSaveSync() },
                    onSkip = { viewModel.skipSaveSync() }
                )
                FirstRunStep.USAGE_STATS -> UsageStatsStep(
                    hasPermission = uiState.hasUsageStatsPermission,
                    isFocused = uiState.focusedIndex == 0,
                    onRequestPermission = requestUsageStats,
                    onContinue = { viewModel.proceedFromUsageStats() }
                )
                FirstRunStep.PLATFORM_SELECT -> PlatformSelectStep(
                    platforms = uiState.platforms,
                    focusedIndex = uiState.focusedIndex,
                    buttonFocusIndex = uiState.platformButtonFocus,
                    onToggle = { viewModel.togglePlatform(it) },
                    onToggleAll = { viewModel.toggleAllPlatforms() },
                    onContinue = { viewModel.proceedFromPlatformSelect() }
                )
                FirstRunStep.CORE_PROMPT -> CorePromptStep(
                    missingCoreCount = uiState.missingCoreCount,
                    focusedIndex = uiState.focusedIndex,
                    onDownload = { viewModel.nextStep() },
                    onSkip = { viewModel.skipCorePrompt() }
                )
                FirstRunStep.CORE_DOWNLOAD -> CoreDownloadStep(
                    coreDownloads = uiState.coreDownloads,
                    isComplete = uiState.coreDownloadComplete,
                    focusedIndex = uiState.focusedIndex,
                    onRetry = { viewModel.retryCoreDownload(it) },
                    onContinue = { viewModel.nextStep() },
                    onSkip = { viewModel.skipCoreDownloads() }
                )
                FirstRunStep.COMPLETE -> CompleteStep(
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    isFocused = true,
                    onStart = {
                        viewModel.completeSetup()
                        onComplete()
                    }
                )
            }
        }
    }

    if (showFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showFileBrowser = false
                viewModel.setStoragePath(path)
            },
            onDismiss = {
                showFileBrowser = false
            }
        )
    }

    if (showImageCacheBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showImageCacheBrowser = false
                viewModel.setImageCachePath(path)
            },
            onDismiss = {
                showImageCacheBrowser = false
            }
        )
    }
}

@Composable
private fun WelcomeStep(isFocused: Boolean, onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        Text(
            text = "ARGOSY",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Text(
            text = "Welcome! Let's get you set up.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXxl))
        FocusableButton(
            text = "Get Started",
            isFocused = isFocused,
            onClick = onGetStarted
        )
    }
}

@Composable
private fun RommLoginStep(
    url: String,
    username: String,
    password: String,
    isConnecting: Boolean,
    error: String?,
    focusedIndex: Int,
    rommFocusField: Int?,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    onClearFocusField: () -> Unit
) {
    val inputShape = RoundedCornerShape(Dimens.radiusMd)
    val urlFocusRequester = remember { FocusRequester() }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }

    LaunchedEffect(rommFocusField) {
        when (rommFocusField) {
            0 -> urlFocusRequester.requestFocus()
            1 -> usernameFocusRequester.requestFocus()
            2 -> passwordFocusRequester.requestFocus()
        }
        if (rommFocusField != null) {
            onClearFocusField()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(Dimens.spacingXl)
            .verticalScroll(rememberScrollState())
    ) {
        StepHeader(step = 1, title = "Rom Manager Login", totalSteps = 4)
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://romm.example.com") },
            singleLine = true,
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .focusRequester(urlFocusRequester)
                .then(
                    if (focusedIndex == 0)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .focusRequester(usernameFocusRequester)
                .then(
                    if (focusedIndex == 1)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = inputShape,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .focusRequester(passwordFocusRequester)
                .then(
                    if (focusedIndex == 2)
                        Modifier.background(MaterialTheme.colorScheme.primaryContainer, inputShape)
                    else Modifier
                )
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingXl))

        Row {
            FocusableOutlinedButton(
                text = "Back",
                isFocused = focusedIndex == 4,
                enabled = !isConnecting,
                onClick = onBack
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            FocusableButton(
                text = if (isConnecting) "Connecting..." else "Connect",
                isFocused = focusedIndex == 3,
                enabled = !isConnecting && url.isNotBlank(),
                onClick = onConnect
            )
        }
    }
}

@Composable
private fun RommSuccessStep(
    serverName: String,
    gameCount: Int,
    platformCount: Int,
    isFocused: Boolean,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Connected successfully!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Text(
            text = "Server: $serverName",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "$gameCount games across $platformCount platforms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        FocusableButton(
            text = "Continue",
            isFocused = isFocused,
            onClick = onContinue
        )
    }
}

@Composable
private fun RomPathStep(
    currentPath: String?,
    folderSelected: Boolean,
    hasStoragePermission: Boolean,
    focusedIndex: Int,
    onRequestPermission: () -> Unit,
    onChooseFolder: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        StepHeader(step = 2, title = "Storage Access", totalSteps = 4)
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Grant storage access to download and manage your game files.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasStoragePermission) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(Dimens.spacingMd)
            ) {
                Icon(
                    if (hasStoragePermission) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (hasStoragePermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(modifier = Modifier.width(Dimens.radiusLg))
                Text(
                    text = if (hasStoragePermission) "Storage access granted" else "Storage access required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasStoragePermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }

        if (!hasStoragePermission) {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            FocusableButton(
                text = "Grant Storage Access",
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Lock,
                onClick = onRequestPermission
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "This permission is required to download games and sync saves.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            if (folderSelected && currentPath != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(Dimens.spacingMd)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(Dimens.radiusLg))
                        Text(
                            text = currentPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacingLg))
                FocusableButton(
                    text = "Continue",
                    isFocused = focusedIndex == 0,
                    onClick = onContinue
                )
                Spacer(modifier = Modifier.height(Dimens.radiusLg))
                FocusableOutlinedButton(
                    text = "Choose Different Folder",
                    isFocused = focusedIndex == 1,
                    onClick = onChooseFolder
                )
            } else {
                Text(
                    text = "Choose where your game files will be stored.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.spacingMd))
                FocusableButton(
                    text = "Choose Folder",
                    isFocused = focusedIndex == 0,
                    icon = Icons.Default.Folder,
                    onClick = onChooseFolder
                )
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "We'll create subfolders for each console automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImageCacheStep(
    currentPath: String?,
    folderSelected: Boolean,
    focusedIndex: Int,
    onChooseFolder: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        Text(
            text = "OPTIONAL",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "Image Cache Location",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "By default, game artwork is stored in the app's internal cache. If your device has limited storage, you can choose an external location.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        if (folderSelected && currentPath != null) {
            val displayPath = "${currentPath.substringAfterLast("/")}/argosy_images"
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(Dimens.spacingMd)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(Dimens.radiusLg))
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            FocusableButton(
                text = "Continue",
                isFocused = focusedIndex == 0,
                onClick = onContinue
            )
            Spacer(modifier = Modifier.height(Dimens.radiusLg))
            FocusableOutlinedButton(
                text = "Choose Different Folder",
                isFocused = focusedIndex == 1,
                onClick = onChooseFolder
            )
        } else {
            FocusableButton(
                text = "Choose External Folder",
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Folder,
                onClick = onChooseFolder
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            FocusableOutlinedButton(
                text = "Use Default (Internal)",
                isFocused = focusedIndex == 1,
                onClick = onSkip
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Images will be stored in an 'argosy_images' subfolder.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaveSyncStep(
    focusedIndex: Int,
    onEnable: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        StepHeader(step = 3, title = "Save Data Sync", totalSteps = 4)
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Sync your game saves with your RomM server to continue playing across multiple devices.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "Saves are uploaded when you stop playing and downloaded when needed.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        FocusableButton(
            text = "Enable Save Sync",
            isFocused = focusedIndex == 0,
            icon = Icons.Default.Sync,
            onClick = onEnable
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        FocusableOutlinedButton(
            text = "Skip for Now",
            isFocused = focusedIndex == 1,
            onClick = onSkip
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Text(
            text = "You can enable this later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UsageStatsStep(
    hasPermission: Boolean,
    isFocused: Boolean,
    onRequestPermission: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        StepHeader(step = 4, title = "Usage Access", totalSteps = 4)
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "This permission allows Argosy to detect when you're playing a game, enabling accurate play time tracking and seamless game resumption.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (hasPermission) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                }
            ),
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(Dimens.spacingMd)
            ) {
                Icon(
                    if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (hasPermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(modifier = Modifier.width(Dimens.radiusLg))
                Text(
                    text = if (hasPermission) "Usage access granted" else "Usage access required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (hasPermission) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        if (!hasPermission) {
            FocusableButton(
                text = "Grant Usage Access",
                isFocused = isFocused,
                icon = Icons.Default.Lock,
                onClick = onRequestPermission
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "Find Argosy in the list and enable access.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FocusableButton(
                text = "Continue",
                isFocused = isFocused,
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun PlatformSelectStep(
    platforms: List<PlatformEntity>,
    focusedIndex: Int,
    buttonFocusIndex: Int,
    onToggle: (Long) -> Unit,
    onToggleAll: () -> Unit,
    onContinue: () -> Unit
) {
    val listState = rememberLazyListState()
    val enabledCount = platforms.count { it.syncEnabled }
    val allEnabled = platforms.isNotEmpty() && enabledCount == platforms.size
    val isOnButtons = focusedIndex >= platforms.size

    LaunchedEffect(focusedIndex) {
        if (platforms.isNotEmpty() && focusedIndex in platforms.indices) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
    ) {
        Text(
            text = "SELECT PLATFORMS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "Choose which platforms to sync",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "$enabledCount of ${platforms.size} platforms selected",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            itemsIndexed(platforms, key = { _, p -> p.id }) { index, platform ->
                val isFocused = index == focusedIndex
                PlatformToggleItem(
                    platform = platform,
                    isFocused = isFocused,
                    onToggle = { onToggle(platform.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.spacingLg))

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            FocusableOutlinedButton(
                text = if (allEnabled) "Deselect All" else "Select All",
                isFocused = isOnButtons && buttonFocusIndex == 0,
                onClick = onToggleAll
            )
            Spacer(modifier = Modifier.weight(1f))
            FocusableButton(
                text = "Continue",
                isFocused = isOnButtons && buttonFocusIndex == 1,
                onClick = onContinue
            )
        }
    }
}

@Composable
private fun PlatformToggleItem(
    platform: PlatformEntity,
    isFocused: Boolean,
    onToggle: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.radiusLg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platform.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${platform.gameCount} games",
                style = MaterialTheme.typography.bodySmall,
                color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = platform.syncEnabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.focusProperties { canFocus = false },
            interactionSource = remember { MutableInteractionSource() }
        )
    }
}

@Composable
private fun CorePromptStep(
    missingCoreCount: Int,
    focusedIndex: Int,
    onDownload: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Download Emulator Cores?",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        if (missingCoreCount > 0) {
            Text(
                text = "$missingCoreCount libretro cores are available for your selected platforms.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = "These enable built-in emulation without needing separate emulator apps.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "All cores for your selected platforms are already installed.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingXl))

        if (missingCoreCount > 0) {
            FocusableButton(
                text = "Download Cores",
                isFocused = focusedIndex == 0,
                icon = Icons.Default.Download,
                onClick = onDownload
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
        }

        FocusableOutlinedButton(
            text = if (missingCoreCount > 0) "Skip (Use Standalone Emulators)" else "Continue",
            isFocused = if (missingCoreCount > 0) focusedIndex == 1 else focusedIndex == 0,
            onClick = onSkip
        )

        if (missingCoreCount > 0) {
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            Text(
                text = "You can download cores later from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoreDownloadStep(
    coreDownloads: List<CoreDownloadState>,
    isComplete: Boolean,
    focusedIndex: Int,
    onRetry: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    val completeCount = coreDownloads.count { it.status == CoreDownloadStatus.COMPLETE }
    val failedCount = coreDownloads.count { it.status == CoreDownloadStatus.FAILED }
    val downloadingCount = coreDownloads.count { it.status == CoreDownloadStatus.DOWNLOADING }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Downloading Emulator Cores",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        if (coreDownloads.isEmpty()) {
            Text(
                text = "No cores needed for selected platforms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXl))
            FocusableButton(
                text = "Continue",
                isFocused = focusedIndex == 0,
                onClick = onContinue
            )
        } else {
            Text(
                text = if (isComplete) {
                    if (failedCount > 0) "$completeCount of ${coreDownloads.size} cores downloaded"
                    else "All cores downloaded"
                } else {
                    "Downloading $completeCount of ${coreDownloads.size} cores..."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                itemsIndexed(coreDownloads, key = { _, c -> c.coreId }) { _, core ->
                    CoreDownloadItem(
                        core = core,
                        onRetry = { onRetry(core.coreId) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            Text(
                text = "Skip if you prefer to use standalone emulators",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                FocusableOutlinedButton(
                    text = "Skip",
                    isFocused = focusedIndex == 1,
                    onClick = onSkip
                )
                Spacer(modifier = Modifier.weight(1f))
                FocusableButton(
                    text = "Continue",
                    isFocused = focusedIndex == 0,
                    enabled = isComplete,
                    onClick = onContinue
                )
            }
        }
    }
}

@Composable
private fun CoreDownloadItem(
    core: CoreDownloadState,
    onRetry: () -> Unit
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusMd))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = core.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = core.platforms.joinToString(", ") { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (core.status) {
            CoreDownloadStatus.PENDING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CoreDownloadStatus.DOWNLOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }
            CoreDownloadStatus.COMPLETE -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Complete",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            CoreDownloadStatus.FAILED -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingSm))
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteStep(
    gameCount: Int,
    platformCount: Int,
    isFocused: Boolean,
    onStart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(Dimens.spacingXl)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = Dimens.spacingMd)
        )
        Text(
            text = "All Set!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Text(
            text = "$gameCount games across $platformCount platforms ready to sync",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "Sync your library from Collection settings to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        FocusableButton(
            text = "Start Playing",
            isFocused = isFocused,
            onClick = onStart
        )
    }
}

@Composable
private fun StepHeader(step: Int, title: String, totalSteps: Int = 4) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SETUP $step/$totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun FocusableButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val containerColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
        }
        Text(text)
    }
}

@Composable
private fun FocusableOutlinedButton(
    text: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val containerColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor
        )
    ) {
        Text(text)
    }
}
