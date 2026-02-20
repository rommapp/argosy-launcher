package com.nendo.argosy.hardware

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
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
import com.nendo.argosy.ui.common.LongPressAnimationConfig
import com.nendo.argosy.ui.common.longPressGesture
import com.nendo.argosy.ui.common.longPressGraphicsLayer
import com.nendo.argosy.ui.common.rememberLongPressAnimationState
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.screens.secondaryhome.DrawerAppUi
import com.nendo.argosy.ui.theme.Dimens
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
    onTabChanged: (CompanionPanel) -> Unit,
    isDrawerOpen: Boolean = false,
    drawerApps: List<DrawerAppUi> = emptyList(),
    onOpenDrawer: () -> Unit = {},
    onCloseDrawer: () -> Unit = {},
    onPinToggle: (String) -> Unit = {},
    onDrawerAppClick: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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

            val showAppBar = com.nendo.argosy.DualScreenManagerHolder.instance
                ?.isExternalDisplay != true
            if (showAppBar) {
                CompanionAppBar(
                    apps = homeApps,
                    onAppClick = onAppClick,
                    onOpenDrawer = onOpenDrawer
                )
            }
        }

        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCloseDrawer
                    )
            )
        }

        AnimatedVisibility(
            visible = isDrawerOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CompanionDrawer(
                apps = drawerApps,
                onPinToggle = onPinToggle,
                onAppClick = { pkg ->
                    onCloseDrawer()
                    onDrawerAppClick(pkg)
                }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
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

        Spacer(modifier = Modifier.weight(1f))

        SystemStatusBar(contentColor = Color.White.copy(alpha = 0.7f))
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                SaveStateIndicator(isDirty = state.isDirty)
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
    focusedIndex: Int = -1,
    onOpenDrawer: () -> Unit = {}
) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) {
            listState.animateScrollToItem(focusedIndex)
        }
    }

    Row(
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
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(64.dp)
                .touchOnly(onOpenDrawer)
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .then(
                        if (focusedIndex == -1) Modifier.border(
                            width = 2.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add app",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(apps.size, key = { apps[it] }) { index ->
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

@Composable
private fun CompanionDrawer(
    apps: List<DrawerAppUi>,
    onPinToggle: (String) -> Unit,
    onAppClick: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.65f)
            .background(
                Color(0xFF2A2A2A),
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }

        Text(
            text = "All Apps",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { _, app ->
                CompanionDrawerAppItem(
                    app = app,
                    onClick = { onAppClick(app.packageName) },
                    onPinToggle = { onPinToggle(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun CompanionDrawerAppItem(
    app: DrawerAppUi,
    onClick: () -> Unit,
    onPinToggle: () -> Unit
) {
    val longPressState = rememberLongPressAnimationState(
        config = LongPressAnimationConfig(
            targetScale = 1.2f,
            tapThreshold = 1.05f,
            withFadeEffect = false,
        ),
    )

    Column(
        modifier = Modifier
            .longPressGraphicsLayer(longPressState, applyAlpha = false)
            .longPressGesture(
                key = app.packageName,
                state = longPressState,
                onClick = onClick,
                onLongPress = onPinToggle,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            AsyncImage(
                model = AppIconData(app.packageName),
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            if (app.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .background(Color(0xFF6750A4), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
