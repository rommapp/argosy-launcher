package com.nendo.argosy.ui.common.savechannel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.UnifiedStateEntry
import java.io.File

@Composable
fun StateSlotRow(
    entry: UnifiedStateEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    clickModifier: Modifier = Modifier
) {
    val isEmpty = entry.localCacheId == null && entry.serverStateId == null
    val textAlpha = if (isEmpty) 0.45f else 1f

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                    .copy(alpha = 0.6f)
                else Color.Transparent
            )
            .then(
                if (isSelected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .then(clickModifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val screenshotFile = entry.screenshotPath?.let { File(it) }
            ?.takeIf { it.exists() }

        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (screenshotFile != null) {
                AsyncImage(
                    model = screenshotFile,
                    contentDescription = entry.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 64.dp, height = 48.dp)
                )
            } else {
                Text(
                    text = entry.slotLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.5f)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
                    .copy(alpha = textAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isEmpty) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(entry.timestamp.toEpochMilli()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = entry.sizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    text = "(Empty)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = 0.5f)
                )
            }
        }

        if (!isEmpty) {
            val syncLabel = when (entry.syncStatus) {
                UnifiedStateEntry.SyncStatus.SYNCED -> "Synced"
                UnifiedStateEntry.SyncStatus.PENDING_UPLOAD -> "Pending"
                UnifiedStateEntry.SyncStatus.SERVER_ONLY -> "Server"
                UnifiedStateEntry.SyncStatus.LOCAL_ONLY -> "Local"
            }
            val syncColor = when (entry.syncStatus) {
                UnifiedStateEntry.SyncStatus.SYNCED -> Color(0xFF4CAF50)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                text = "[$syncLabel]",
                style = MaterialTheme.typography.labelSmall,
                color = syncColor
            )
        }
    }
}
