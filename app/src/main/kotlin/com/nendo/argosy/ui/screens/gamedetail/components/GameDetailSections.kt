package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.util.formatBytes
import com.nendo.argosy.util.formatTimeToBeat
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.supersedesCommunityRating
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUiState
import com.nendo.argosy.ui.screens.gamedetail.GameDetailViewModel
import com.nendo.argosy.ui.screens.gamedetail.GameDownloadStatus
import com.nendo.argosy.ui.screens.gamedetail.ScreenshotPair
import com.nendo.argosy.core.game.AchievementUi
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.screens.home.HomeGameUi
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
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
        AsyncImage(
            model = rememberFileImageModel(game.coverPath),
            contentDescription = game.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(200.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(Dimens.radiusLg))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(modifier = Modifier.weight(1f)) {
            GameTitle(
                title = game.title,
                titleStyle = MaterialTheme.typography.headlineMedium,
                titleColor = MaterialTheme.colorScheme.onSurface,
                adaptiveSize = true,
                reducedScale = 0.85f,
                maxLines = 2
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                game.players?.let { players ->
                    MetadataChip(label = stringResource(R.string.gamedetail_chip_players_label), value = players)
                }
                val sentiment = uiState.reviewSummary?.sentiment?.allTime
                if (sentiment.supersedesCommunityRating()) {
                    ReviewSentimentChip(sentiment = sentiment!!)
                } else {
                    game.rating?.let { rating ->
                        CommunityRatingChip(rating = rating)
                    }
                }
                val context = LocalContext.current
                formatTimeToBeat(context, game.timeToBeatMainSec)?.let { time ->
                    MetadataChip(label = stringResource(R.string.gamedetail_chip_main_story_label), value = time)
                }
                formatTimeToBeat(context, game.timeToBeatExtraSec)?.let { time ->
                    MetadataChip(label = stringResource(R.string.gamedetail_chip_main_extras_label), value = time)
                }
                formatTimeToBeat(context, game.timeToBeatCompletionistSec)?.let { time ->
                    MetadataChip(label = stringResource(R.string.gamedetail_chip_completionist_label), value = time)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingLg))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                RatingChip(
                    label = stringResource(R.string.gamedetail_chip_my_rating_label),
                    value = game.userRating,
                    icon = Icons.Default.Star,
                    iconColor = ALauncherColors.StarGold
                )
                RatingChip(
                    label = stringResource(R.string.gamedetail_chip_difficulty_label),
                    value = game.userDifficulty,
                    icon = Icons.Default.Whatshot,
                    iconColor = ALauncherColors.DifficultyRed
                )
                if (uiState.hasSocialAccount) {
                    MyReviewChip(
                        review = uiState.reviewSummary?.myReview,
                        onClick = { viewModel.openReviewEditor() }
                    )
                }
                if (game.playTimeMinutes > 0) {
                    PlayTimeChip(minutes = game.playTimeMinutes)
                }
                game.status?.let { status ->
                    StatusChip(statusValue = status)
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            ActionButtons(game = game, uiState = uiState, viewModel = viewModel)
        }
    }
}

@Composable
fun ActionButtons(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel
) {
    val context = LocalContext.current
    val isButtonDisabled = uiState.downloadStatus in listOf(
        GameDownloadStatus.QUEUED,
        GameDownloadStatus.DOWNLOADING,
        GameDownloadStatus.EXTRACTING
    )
    val isDestructive = uiState.downloadStatus == GameDownloadStatus.FAILED ||
        uiState.downloadStatus == GameDownloadStatus.WAITING_FOR_STORAGE

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
                modifier = Modifier.focusProperties { canFocus = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = if (isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                when (uiState.downloadStatus) {
                    GameDownloadStatus.DOWNLOADED -> {
                        val saveInfo = uiState.saveStatusInfo
                        if (saveInfo != null) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.gamedetail_action_buttons_play))
                                Text(
                                    text = saveInfo.channelName
                                        ?: stringResource(R.string.gamedetail_action_buttons_autosave),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                                )
                                saveInfo.displayTime(context)?.let { time ->
                                    Text(
                                        text = time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = saveInfo.effectiveStatus.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                modifier = Modifier.size(Dimens.spacingMd)
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(Dimens.spacingSm))
                            Text(stringResource(R.string.gamedetail_action_buttons_play))
                        }
                    }
                    GameDownloadStatus.NOT_DOWNLOADED -> {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.gamedetail_action_buttons_download))
                            uiState.downloadSizeBytes?.let { size ->
                                Text(
                                    text = formatBytes(size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    GameDownloadStatus.NEEDS_INSTALL -> {
                        Icon(Icons.Default.InstallMobile, contentDescription = null)
                        Spacer(modifier = Modifier.width(Dimens.spacingSm))
                        Text(stringResource(R.string.gamedetail_action_buttons_install))
                    }
                    GameDownloadStatus.QUEUED -> Text(stringResource(R.string.gamedetail_action_buttons_queued))
                    GameDownloadStatus.WAITING_FOR_STORAGE -> Text(stringResource(R.string.gamedetail_action_buttons_no_space))
                    GameDownloadStatus.DOWNLOADING -> Text(
                        stringResource(
                            R.string.gamedetail_action_buttons_downloading_percent,
                            (uiState.downloadProgress * 100).toInt()
                        )
                    )
                    GameDownloadStatus.EXTRACTING -> Text(stringResource(R.string.gamedetail_action_buttons_extracting))
                    GameDownloadStatus.PAUSED -> Text(
                        stringResource(
                            R.string.gamedetail_action_buttons_resume_percent,
                            (uiState.downloadProgress * 100).toInt()
                        )
                    )
                    GameDownloadStatus.FAILED -> Text(stringResource(R.string.gamedetail_action_buttons_retry))
                }
            }
        }

        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(
                imageVector = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (game.isFavorite) {
                    stringResource(R.string.gamedetail_action_buttons_unfavorite)
                } else {
                    stringResource(R.string.gamedetail_action_buttons_favorite)
                },
                tint = if (game.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(onClick = { viewModel.toggleMoreOptions() }, modifier = Modifier.focusProperties { canFocus = false }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.gamedetail_action_buttons_more_options),
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
            text = stringResource(R.string.gamedetail_section_description_heading),
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

@Composable
fun ScreenshotsSection(
    screenshots: List<ScreenshotPair>,
    listState: LazyListState,
    onScreenshotTap: (Int) -> Unit,
    onPositioned: (Int) -> Unit,
    isActive: Boolean = false,
    onSectionFocus: () -> Unit = {},
    gameId: Long = 0L,
    cacheEnabled: Boolean = false
) {
    val cacheManager = com.nendo.argosy.ui.common.LocalImageCacheManager.current
    LaunchedEffect(gameId, cacheEnabled, screenshots) {
        if (!cacheEnabled || cacheManager == null || gameId == 0L) return@LaunchedEffect
        val missingRemotes = screenshots
            .filter { it.cachedPath == null && it.remoteUrl.isNotBlank() }
            .map { it.remoteUrl }
        if (missingRemotes.isNotEmpty()) {
            cacheManager.queueScreenshotCacheByGameId(gameId, missingRemotes)
        }
    }

    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Text(
            text = stringResource(R.string.gamedetail_section_screenshots_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        var scrollDirection by remember { mutableIntStateOf(1) }
        val isActiveUpdated by rememberUpdatedState(isActive)

        LaunchedEffect(screenshots) {
            if (screenshots.size <= 1) return@LaunchedEffect

            while (true) {
                delay(3000)
                if (isActiveUpdated) continue

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

        val failedCachePaths = remember { mutableStateMapOf<Int, Boolean>() }

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Dimens.radiusLg)
        ) {
            itemsIndexed(screenshots) { index, screenshot ->
                val useRemote = failedCachePaths[index] == true || screenshot.cachedPath == null
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(135.dp)
                        .clip(RoundedCornerShape(Dimens.radiusMd))
                        .clickableNoFocus {
                            if (isActive) {
                                onScreenshotTap(index)
                            } else {
                                onSectionFocus()
                            }
                        }
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
fun RelatedGamesSection(
    games: List<HomeGameUi>,
    listState: LazyListState,
    focusedIndex: Int,
    isActive: Boolean,
    onGameTap: (Long) -> Unit,
    onPositioned: (Int) -> Unit,
    onSectionFocus: () -> Unit = {}
) {
    val boxArtStyle = LocalBoxArtStyle.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = screenWidth * 0.12f
    val cardHeight = cardWidth / boxArtStyle.aspectRatio

    LaunchedEffect(focusedIndex, isActive) {
        if (isActive && games.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, games.size - 1))
        }
    }

    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Text(
            text = stringResource(R.string.gamedetail_section_related_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        val endInset = Dimens.spacingXl
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            contentPadding = PaddingValues(end = endInset),
            modifier = Modifier.layout { measurable, constraints ->
                val extra = endInset.roundToPx()
                val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + extra))
                layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
            }
        ) {
            itemsIndexed(games, key = { _, related -> related.id }) { index, related ->
                Box(
                    modifier = Modifier
                        .width(cardWidth)
                        .height(cardHeight)
                ) {
                    GameCard(
                        game = related,
                        isFocused = isActive && index == focusedIndex,
                        focusScale = 1f,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickableNoFocus {
                                if (isActive) onGameTap(related.id) else onSectionFocus()
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementsSection(
    achievements: List<AchievementUi>,
    listState: LazyListState,
    onPositioned: (Int) -> Unit,
    isActive: Boolean = false
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
                text = stringResource(R.string.gamedetail_section_achievements_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(
                    R.string.gamedetail_section_achievements_count,
                    achievements.count { it.isUnlocked },
                    achievements.size
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(Dimens.radiusLg))

        val achievementColumns = achievements.chunked(3)
        val isActiveUpdated by rememberUpdatedState(isActive)

        LaunchedEffect(achievements.size) {
            if (achievementColumns.size <= 1) return@LaunchedEffect

            var scrollDirection = 1
            while (true) {
                delay(4000)
                if (isActiveUpdated) continue

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

