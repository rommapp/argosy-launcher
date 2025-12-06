package com.nendo.argosy.ui.screens.firstrun

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun FirstRunScreen(
    onComplete: () -> Unit,
    viewModel: FirstRunViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
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

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            folderPickerLauncher.launch(null)
            viewModel.clearFolderPickerFlag()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                    onGetStarted = { viewModel.nextStep() }
                )
                FirstRunStep.ROMM_CHOICE -> RommChoiceStep(
                    onConnectRomm = { viewModel.nextStep() },
                    onSkip = { viewModel.skipRomm() }
                )
                FirstRunStep.ROMM_LOGIN -> RommLoginStep(
                    url = uiState.rommUrl,
                    username = uiState.rommUsername,
                    password = uiState.rommPassword,
                    isConnecting = uiState.isConnecting,
                    error = uiState.connectionError,
                    onUrlChange = viewModel::setRommUrl,
                    onUsernameChange = viewModel::setRommUsername,
                    onPasswordChange = viewModel::setRommPassword,
                    onConnect = { viewModel.connectToRomm() },
                    onBack = { viewModel.previousStep() }
                )
                FirstRunStep.ROMM_SUCCESS -> RommSuccessStep(
                    serverName = uiState.rommUrl,
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    onContinue = { viewModel.nextStep() }
                )
                FirstRunStep.ROM_PATH -> RomPathStep(
                    currentPath = uiState.romStoragePath,
                    folderSelected = uiState.folderSelected,
                    onChooseFolder = { viewModel.openFolderPicker() },
                    onContinue = { viewModel.proceedFromRomPath() }
                )
                FirstRunStep.COMPLETE -> CompleteStep(
                    rommConnected = !uiState.skippedRomm,
                    gameCount = uiState.rommGameCount,
                    platformCount = uiState.rommPlatformCount,
                    onStart = {
                        viewModel.completeSetup()
                        onComplete()
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "ARGOSY",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome! Let's get you set up.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onGetStarted) {
            Text("Get Started")
        }
    }
}

@Composable
private fun RommChoiceStep(
    onConnectRomm: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        StepHeader(step = 1, title = "Connect to Rom Manager?")
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Rom Manager lets you sync your game library from a self-hosted server.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onConnectRomm) {
            Icon(Icons.Default.Cloud, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Yes, connect to Rom Manager")
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onSkip) {
            Text("Skip, use local files only")
        }
    }
}

@Composable
private fun RommLoginStep(
    url: String,
    username: String,
    password: String,
    isConnecting: Boolean,
    error: String?,
    onUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        StepHeader(step = 1, title = "Rom Manager Login")
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Server URL") },
            placeholder = { Text("https://romm.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(0.8f)
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row {
            OutlinedButton(onClick = onBack, enabled = !isConnecting) {
                Text("Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = onConnect, enabled = !isConnecting && url.isNotBlank()) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp).width(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
private fun RommSuccessStep(
    serverName: String,
    gameCount: Int,
    platformCount: Int,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(64.dp).width(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Connected successfully!",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Server: $serverName",
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = "$gameCount games across $platformCount platforms",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onContinue) {
            Text("Continue")
        }
    }
}

@Composable
private fun RomPathStep(
    currentPath: String?,
    folderSelected: Boolean,
    onChooseFolder: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        StepHeader(step = 2, title = "Select ROM Storage Folder")
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose where your game files will be stored. This grants the app permission to read and write to that folder.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (folderSelected && currentPath != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onContinue) {
                Text("Continue")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onChooseFolder) {
                Text("Choose Different Folder")
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No folder selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onChooseFolder) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose Folder")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "We'll create subfolders for each console automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CompleteStep(
    rommConnected: Boolean,
    gameCount: Int,
    platformCount: Int,
    onStart: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = "All Set!",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (rommConnected) {
            Text(
                text = "$gameCount games across $platformCount platforms ready to sync",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sync your library from Collection settings to get started.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Configure Rom Manager in Collection settings to sync your library.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onStart) {
            Text("Start Playing")
        }
    }
}

@Composable
private fun StepHeader(step: Int, title: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "SETUP $step/3",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Suppress("UNUSED_PARAMETER")
private fun getFilePathFromUri(context: Context, uri: Uri): String? {
    val rawPath = uri.path ?: return null
    val path = Uri.decode(rawPath)

    val treePath = path.substringAfter("/tree/", "")
    if (treePath.isEmpty()) return null

    return when {
        treePath.startsWith("primary:") -> {
            val relativePath = treePath.removePrefix("primary:")
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        }
        treePath.contains(":") -> {
            val parts = treePath.split(":", limit = 2)
            if (parts.size == 2) {
                "/storage/${parts[0]}/${parts[1]}"
            } else null
        }
        else -> null
    }
}
