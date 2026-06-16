package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

data class CoreCrashPrompt(
    val coreId: String,
    val displayName: String,
    val platformId: Long,
    val platformSlug: String,
    val options: List<CoreCrashOption>
)

data class CoreDownloadProgress(
    val label: String,
    val fraction: Float,
    val done: Boolean = false,
    val failed: Boolean = false
)

sealed interface CoreCrashOption {
    val label: String
    data class Revert(val historyId: Long, override val label: String) : CoreCrashOption
    data class SwitchCore(val targetCoreId: String, override val label: String) : CoreCrashOption
    data class Redownload(override val label: String = "Re-download latest") : CoreCrashOption
    data class Keep(override val label: String = "Keep current core") : CoreCrashOption
}

@Composable
fun CoreCrashModal(
    prompt: CoreCrashPrompt,
    focusedIndex: Int,
    downloading: CoreDownloadProgress?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Modal(
        title = "Core Crashed",
        baseWidth = 420.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.A to "Select",
            InputButton.B to "Dismiss"
        )
    ) {
        if (downloading != null) {
            Text(
                text = downloading.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (downloading.failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            LinearProgressIndicator(
                progress = { downloading.fraction },
                modifier = Modifier.fillMaxWidth()
            )
            return@Modal
        }

        Text(
            text = "'${prompt.displayName}' crashed during your last session. " +
                "Choose how to recover.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Dimens.spacingMd))

        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
            prompt.options.forEachIndexed { index, option ->
                CoreCrashOptionRow(
                    label = option.label,
                    focused = index == focusedIndex,
                    onClick = { onSelect(index) }
                )
            }
        }
    }
}

@Composable
private fun CoreCrashOptionRow(
    label: String,
    focused: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (focused) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickableNoFocus { onClick() }
            .padding(horizontal = Dimens.spacingMd),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}
