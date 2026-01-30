package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.screens.gamedetail.PermissionModalType
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme

@Composable
fun PermissionRequiredModal(
    isVisible: Boolean,
    permissionType: PermissionModalType = PermissionModalType.STORAGE,
    onGrantPermission: () -> Unit,
    onDisableSync: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val isDarkTheme = LocalLauncherTheme.current.isDarkTheme
    val overlayColor = if (isDarkTheme) Color.Black.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)

    val (title, description, buttonText) = when (permissionType) {
        PermissionModalType.STORAGE -> Triple(
            "Permission Required",
            "Save sync is enabled but file access permission is not granted. " +
                "Grant permission to sync saves, or disable save sync to continue.",
            "Grant Permission"
        )
        PermissionModalType.SAF -> Triple(
            "Folder Access Required",
            "Save sync needs access to the Android/data folder to sync saves from other emulators. " +
                "Select the Android folder when prompted, or disable save sync to continue.",
            "Select Folder"
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(overlayColor),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(Dimens.radiusXl),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Dimens.elevationLg,
            modifier = Modifier
                .padding(Dimens.spacingLg)
                .fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(Dimens.spacingLg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(Dimens.iconXl)
                )

                Spacer(modifier = Modifier.height(Dimens.spacingMd))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Dimens.spacingSm))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(Dimens.spacingLg))

                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonText)
                    }

                    OutlinedButton(
                        onClick = onDisableSync,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Disable Save Sync")
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
