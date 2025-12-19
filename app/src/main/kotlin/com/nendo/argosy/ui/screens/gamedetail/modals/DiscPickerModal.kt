package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.DiscUi

@Composable
fun DiscPickerModal(
    discs: List<DiscUi>,
    focusIndex: Int,
    onSelectDisc: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    val itemHeight = 56.dp
    val maxVisibleItems = 5

    LaunchedEffect(focusIndex) {
        listState.animateScrollToItem(focusIndex.coerceIn(0, (discs.size - 1).coerceAtLeast(0)))
    }

    Modal(
        title = "SELECT DISC",
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD_VERTICAL to "Select",
            InputButton.SOUTH to "Play",
            InputButton.EAST to "Cancel"
        )
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = itemHeight * maxVisibleItems)
        ) {
            items(discs.size) { index ->
                val disc = discs[index]
                val isFocused = focusIndex == index
                val backgroundColor = if (isFocused) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
                val contentColor = if (isFocused) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onSelectDisc(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Disc ${disc.discNumber}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )
                        Text(
                            text = disc.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (disc.isLastPlayed) {
                        Text(
                            text = "[Last Played]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!disc.isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Not downloaded",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
