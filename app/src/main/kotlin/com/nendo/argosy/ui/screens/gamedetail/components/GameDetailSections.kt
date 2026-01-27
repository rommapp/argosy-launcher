package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUiState
import com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair
import com.nendo.argosy.ui.screens.gamedetail.AchievementUi
import kotlinx.coroutines.delay

@Composable
fun GameHeader(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
    ) {
        val imageData = game.coverPath?.let { path ->
            if (path.startsWith("/")) File(path) else path
        }
        AsyncImage(
            model = imageData,
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(200.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.platformName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                game.releaseYear?.let { year ->
                    Text(text = "|", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingXs))

            Row(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                game.developer?.let { dev ->
                    Text(
                        text = dev,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                game.genre?.let { genre ->
                    if (game.developer != null) {
                        Text(text = "|", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingSm))

            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)) {
                game.players?.let { players ->
                    MetadataChip(label = "Players", value = players)
                }
                game.rating?.let { rating ->
                    CommunityRatingChip(rating = rating)
                }
                if (game.userRating > 0) {
                    RatingChip(
                        label = "My Rating",
                        value = game.userRating,
                        icon = Icons.Default.Star,
                        iconColor = ALauncherColors.StarGold
                    )
                }
                if (game.userDifficulty > 0) {
                    RatingChip(
                        label = "Difficulty",
                        value = game.userDifficulty,
                        icon = Icons.Default.Whatshot,
                        iconColor = ALauncherColors.DifficultyRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            if (game.playTimeMinutes > 0 || game.status != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)) {
                    if (game.playTimeMinutes > 0) {
                        PlayTimeChip(minutes = game.playTimeMinutes)
                    }
                    game.status?.let { status ->
                        StatusChip(statusValue = status)
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.radiusLg))
            }

            ActionButtons(game = game, uiState = uiState, viewModel = viewModel)

            uiState.saveStatusInfo?.let { statusInfo ->
                Spacer(modifier = Modifier.height(Dimens.radiusLg))
                SaveStatusRow(status = statusInfo)
            }
        }
    }
}

@Composable
fun ActionButtons(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel
) {
    val isButtonDisabled = uiState.downloadStatus in listOf(
        GameDownloadStatus.QUEUED,
        GameDownloadStatus.WAITING_FOR_STORAGE,
        GameDownloadStatus.DOWNLOADING,
        GameDownloadStatus.EXTRACTING,
        GameDownloadStatus.PAUSED
    )

    val isExtracting = uiState.downloadStatus == GameDownloadStatus.EXTRACTING
    val primaryColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "extracting_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (isExtracting) {
                        Modifier.drawBehind {
                            val strokeWidth = 3.dp.toPx()
                            val cornerRadius = size.height / 2
                            val center = Offset(size.width / 2, size.height / 2)

                            val bladeSize = 0.12f
                            val bladeCenter = (rotationAngle / 360f).mod(1f)
                            val bladeStart = (bladeCenter - bladeSize / 2).mod(1f)
                            val bladeEnd = (bladeCenter + bladeSize / 2).mod(1f)

                            val colorStops = if (bladeStart < bladeEnd) {
                                arrayOf(
                                    0f to primaryColor.copy(alpha = 0f),
                                    bladeStart to primaryColor.copy(alpha = 0f),
                                    bladeCenter to primaryColor,
                                    bladeEnd to primaryColor.copy(alpha = 0f),
                                    1f to primaryColor.copy(alpha = 0f)
                                )
                            } else {
                                arrayOf(
                                    0f to primaryColor,
                                    bladeEnd to primaryColor.copy(alpha = 0f),
                                    0.5f to primaryColor.copy(alpha = 0f),
                                    bladeStart to primaryColor.copy(alpha = 0f),
                                    1f to primaryColor
                                )
                            }

                            drawRoundRect(
                                brush = Brush.sweepGradient(
                                    colorStops = colorStops,
                                    center = center
                                ),
                                style = Stroke(width = strokeWidth),
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        }
                    } else Modifier
                )
        ) {
            Button(
                onClick = { viewModel.primaryAction() },
                enabled = !isButtonDisabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                when (uiState.downloadStatus) {
                    GameDownloadStatus.DOWNLOADED -> {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text("PLAY")
                    }
                    GameDownloadStatus.NOT_DOWNLOADED -> {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text("DOWNLOAD")
                    }
                    GameDownloadStatus.NEEDS_INSTALL -> {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text("INSTALL")
                    }
                    GameDownloadStatus.QUEUED -> Text("QUEUED...")
                    GameDownloadStatus.WAITING_FOR_STORAGE -> Text("NO SPACE")
                    GameDownloadStatus.DOWNLOADING -> Text("${(uiState.downloadProgress * 100).toInt()}%")
                    GameDownloadStatus.EXTRACTING -> Text("EXTRACTING...")
                    GameDownloadStatus.PAUSED -> Text("PAUSED ${(uiState.downloadProgress * 100).toInt()}%")
                }
            }
        }

        IconButton(onClick = { viewModel.toggleFavorite() }) {
            Icon(
                imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (game.isFavorite) "Unfavorite" else "Favorite",
                tint = if (game.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = { viewModel.toggleMoreOptions() }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun DescriptionSection(
    description: String,
    onPositioned: (Int) -> Unit
) {
    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Text(
            text = "DESCRIPTION",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

enum class SnapState { TOP, DESCRIPTION, SCREENSHOTS, ACHIEVEMENTS }

@Composable
fun ScreenshotsSection(
    screenshots: List<ScreenshotPair>,
    listState: LazyListState,
    currentSnapState: SnapState,
    focusedIndex: Int,
    onScreenshotTap: (Int) -> Unit,
    onPositioned: (Int) -> Unit
) {
    val showFocus = currentSnapState == SnapState.SCREENSHOTS

    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Text(
            text = "SCREENSHOTS",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        var scrollDirection by remember { mutableIntStateOf(1) }
        val currentSnapStateUpdated by rememberUpdatedState(currentSnapState)

        LaunchedEffect(screenshots) {
            if (screenshots.size <= 1) return@LaunchedEffect

            while (true) {
                delay(3000)
                if (currentSnapStateUpdated == SnapState.SCREENSHOTS) continue

                val layoutInfo = listState.layoutInfo
                val currentIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                val lastIndex = screenshots.size - 1

                val nextIndex = when {
                    scrollDirection > 0 && currentIndex >= lastIndex -> {
                        scrollDirection = -1
                        currentIndex - 1
                    }
                    scrollDirection < 0 && currentIndex <= 0 -> {
                        scrollDirection = 1
                        currentIndex + 1
                    }
                    else -> currentIndex + scrollDirection
                }.coerceIn(0, lastIndex)

                listState.animateScrollToItem(nextIndex)
            }
        }

        LaunchedEffect(focusedIndex, showFocus) {
            if (showFocus && screenshots.isNotEmpty()) {
                listState.animateScrollToItem(focusedIndex.coerceIn(0, screenshots.size - 1))
            }
        }

        val failedCachePaths = remember { mutableStateMapOf<Int, Boolean>() }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) {
            itemsIndexed(screenshots) { index, screenshot ->
                val isFocused = showFocus && index == focusedIndex
                val useRemote = failedCachePaths[index] == true || screenshot.cachedPath == null
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .then(
                            if (isFocused)
                                Modifier.border(Dimens.borderThick, MaterialTheme.colorScheme.primary, RoundedCornerShape(Dimens.radiusMd))
                            else Modifier
                        )
                        .clickableNoFocus { onScreenshotTap(index) }
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (useRemote) {
                        AsyncImage(
                            model = screenshot.remoteUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = java.io.File(screenshot.cachedPath!!),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            onError = { failedCachePaths[index] = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementsSection(
    achievements: List<AchievementUi>,
    listState: LazyListState,
    currentSnapState: SnapState,
    onPositioned: (Int) -> Unit
) {
    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = ALauncherColors.TrophyAmber,
                modifier = Modifier.size(Dimens.iconSm)
            )
            Text(
                text = "ACHIEVEMENTS",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "(${achievements.count { it.isUnlocked }}/${achievements.size})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        val achievementColumns = achievements.chunked(3)
        val currentSnapStateForAchievements by rememberUpdatedState(currentSnapState)

        LaunchedEffect(achievements.size) {
            if (achievementColumns.size <= 1) return@LaunchedEffect

            var scrollDirection = 1
            while (true) {
                delay(4000)
                if (currentSnapStateForAchievements == SnapState.ACHIEVEMENTS) continue

                val layoutInfo = listState.layoutInfo
                val currentIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                val lastIndex = achievementColumns.size - 1

                val nextIndex = when {
                    scrollDirection > 0 && currentIndex >= lastIndex -> {
                        scrollDirection = -1
                        currentIndex - 1
                    }
                    scrollDirection < 0 && currentIndex <= 0 -> {
                        scrollDirection = 1
                        currentIndex + 1
                    }
                    else -> currentIndex + scrollDirection
                }.coerceIn(0, lastIndex)

                listState.animateScrollToItem(nextIndex)
            }
        }

        BoxWithConstraints {
            val isWidescreen = maxWidth / maxHeight > 1.5f
            val columnWidth = if (isWidescreen) maxWidth / 2 else maxWidth

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                items(
                    count = achievementColumns.size,
                    key = { index -> index }
                ) { index ->
                    AchievementColumn(
                        achievements = achievementColumns[index],
                        modifier = Modifier.width(columnWidth - Dimens.spacingMd)
                    )
                }
            }
        }
    }
}
