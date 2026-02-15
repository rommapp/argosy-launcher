package com.nendo.argosy.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.coil.AppIconData
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.util.touchOnly
import kotlinx.coroutines.delay

@Composable
fun CompanionContent(
    state: CompanionInGameState,
    sessionTimer: CompanionSessionTimer?,
    homeApps: List<String>,
    onAppClick: (String) -> Unit,
    onTabChanged: (CompanionPanel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CompanionTabHeader(
            currentPanel = state.currentPanel,
            onTabChanged = onTabChanged
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (state.currentPanel) {
                CompanionPanel.DASHBOARD -> DashboardPanel(
                    state = state,
                    sessionTimer = sessionTimer
                )
            }
        }

        if (homeApps.isNotEmpty()) {
            CompanionAppBar(
                apps = homeApps,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun CompanionTabHeader(
    currentPanel: CompanionPanel,
    onTabChanged: (CompanionPanel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompanionPanel.entries.forEach { panel ->
            val isSelected = panel == currentPanel
            val backgroundColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(backgroundColor)
                    .then(
                        if (!isSelected) Modifier.touchOnly { onTabChanged(panel) }
                        else Modifier
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = panel.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun DashboardPanel(
    state: CompanionInGameState,
    sessionTimer: CompanionSessionTimer?
) {
    if (!state.isLoaded) return

    var sessionMillis by remember { mutableLongStateOf(sessionTimer?.getActiveMillis() ?: 0L) }

    LaunchedEffect(sessionTimer) {
        if (sessionTimer == null) return@LaunchedEffect
        while (true) {
            delay(1000)
            sessionMillis = sessionTimer.getActiveMillis()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item { HeroGameCard(state) }
        item { SessionTimerCard(sessionMillis) }
        if (state.achievementCount > 0) {
            item { AchievementProgress(state) }
        }
        item { PlayStatsCard(state, sessionMillis) }
    }
}

@Composable
private fun HeroGameCard(state: CompanionInGameState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (state.coverPath != null) {
            AsyncImage(
                model = state.coverPath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        ),
                        startY = 40f
                    )
                )
        )

        SaveStateIndicator(
            isDirty = state.isDirty,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.platformName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (state.developer != null) {
                    MetadataDot()
                    Text(
                        text = state.developer,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.releaseYear != null) {
                    MetadataDot()
                    Text(
                        text = state.releaseYear.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataDot() {
    Text(
        text = "  \u00B7  ",
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.4f)
    )
}

@Composable
private fun SessionTimerCard(activeMillis: Long) {
    if (activeMillis <= 0L) return

    val totalSeconds = activeMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val formatted = "%d:%02d:%02d".format(hours, minutes, seconds)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Session",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun AchievementProgress(state: CompanionInGameState) {
    val progress = if (state.achievementCount > 0) {
        state.earnedAchievementCount.toFloat() / state.achievementCount
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                text = "${state.earnedAchievementCount} / ${state.achievementCount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ALauncherColors.TrophyAmber
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = ALauncherColors.TrophyAmber,
            trackColor = Color.White.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlayStatsCard(state: CompanionInGameState, sessionMillis: Long) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp)
    ) {
        val sessionMinutes = (sessionMillis / 60_000).toInt()
        val totalMinutes = state.playTimeMinutes + sessionMinutes
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeText = when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            mins > 0 -> "${mins}m"
            else -> "0m"
        }

        StatRow(label = "Total Play Time", value = timeText)
        Spacer(modifier = Modifier.height(8.dp))
        StatRow(label = "Times Played", value = state.playCount.toString())
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun SaveStateIndicator(
    isDirty: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
internal fun CompanionAppBar(
    apps: List<String>,
    onAppClick: (String) -> Unit,
    focusedIndex: Int = -1
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

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
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(apps.size) { index ->
                CompanionAppItem(
                    packageName = apps[index],
                    isFocused = index == focusedIndex,
                    onClick = { onAppClick(apps[index]) }
                )
            }
        }
    }
}

@Composable
internal fun CompanionAppItem(
    packageName: String,
    isFocused: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .touchOnly(onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = AppIconData(packageName),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .then(
                    if (isFocused) Modifier.border(
                        width = 2.dp,
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    ) else Modifier
                ),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(4.dp))
    }
}
