package com.nendo.argosy.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.coil.AppIconData

/**
 * Companion mode content for secondary display.
 * Shows header with channel/game info, save state indicator, and app bar.
 * Used when Argosy is not in foreground (game running, other app, etc.)
 */
@Composable
fun CompanionContent(
    channelName: String?,
    isHardcore: Boolean,
    gameName: String?,
    isDirty: Boolean,
    homeApps: List<String>,
    onAppClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CompanionHeader(
            channelName = channelName,
            isHardcore = isHardcore,
            gameName = gameName,
            isDirty = isDirty
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        if (homeApps.isNotEmpty()) {
            CompanionAppBar(
                apps = homeApps,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun CompanionHeader(
    channelName: String?,
    isHardcore: Boolean,
    gameName: String?,
    isDirty: Boolean
) {
    val headerText = when {
        isHardcore -> "Hardcore Mode"
        channelName != null -> channelName
        else -> gameName ?: "Playing"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xCC1E1E1E),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = headerText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channelName != null && gameName != null) {
                    Text(
                        text = gameName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            SaveStateIndicator(isDirty = isDirty)
        }
    }
}

@Composable
private fun SaveStateIndicator(isDirty: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (isDirty) Color(0xFFFF9800) else Color(0xFF4CAF50)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isDirty) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Saved",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun CompanionAppBar(
    apps: List<String>,
    onAppClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xCC1E1E1E)
                    )
                )
            )
            .padding(vertical = 12.dp)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps) { packageName ->
                CompanionAppItem(
                    packageName = packageName,
                    onClick = { onAppClick(packageName) }
                )
            }
        }
    }
}

@Composable
private fun CompanionAppItem(
    packageName: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = AppIconData(packageName),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}
