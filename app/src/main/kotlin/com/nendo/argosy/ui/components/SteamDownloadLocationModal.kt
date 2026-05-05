package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

data class SteamDownloadLocationPrompt(
    val gameId: Long,
    val title: String,
    val coverPath: String? = null
)

data class SteamMarkOption(
    val launcherPackage: String,
    val displayName: String
)

@Composable
fun SteamDownloadLocationModal(
    prompt: SteamDownloadLocationPrompt,
    focusIndex: Int,
    markOptions: List<SteamMarkOption>,
    onDownloadToSd: () -> Unit,
    onMarkAsInstalled: (launcherPackage: String) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = prompt.title.uppercase(),
        onDismiss = onDismiss
    ) {
        val listState = rememberLazyListState()
        FocusedScroll(listState = listState, focusedIndex = focusIndex)

        LazyColumn(state = listState) {
            item {
                SteamLocationRow(
                    label = "Download via Argosy",
                    subtitle = null,
                    isFocused = focusIndex == 0,
                    onClick = onDownloadToSd
                )
            }
            markOptions.forEachIndexed { index, option ->
                item {
                    SteamLocationRow(
                        label = "Mark as Installed",
                        subtitle = "Managed by ${option.displayName}",
                        isFocused = focusIndex == index + 1,
                        onClick = { onMarkAsInstalled(option.launcherPackage) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamLocationRow(
    label: String,
    subtitle: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        else MaterialTheme.colorScheme.onSurfaceVariant
    val background = if (isFocused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(background, RoundedCornerShape(Dimens.radiusMd))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.radiusLg, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        }
    }
}
