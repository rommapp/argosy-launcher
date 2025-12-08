package com.nendo.argosy.ui.screens.gamedetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.theme.Motion
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GameDetailScreen(
    gameId: Long,
    onBack: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(gameId) {
        viewModel.loadGame(gameId)
    }

    LaunchedEffect(Unit) {
        viewModel.launchEvents.collectLatest { event ->
            when (event) {
                is LaunchEvent.Launch -> {
                    try {
                        android.util.Log.d("GameDetailScreen", "Starting activity: ${event.intent}")
                        context.startActivity(event.intent)
                        android.util.Log.d("GameDetailScreen", "Activity started successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("GameDetailScreen", "Failed to start activity", e)
                        viewModel.showLaunchError("Failed to launch: ${e.message}")
                    }
                }
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.game?.id) {
        scrollState.scrollTo(0)
    }

    val inputHandler = remember(onBack, scrollState) {
        viewModel.createInputHandler(
            onBack = onBack,
            onScrollUp = { coroutineScope.launch { scrollState.animateScrollBy(-200f) } },
            onScrollDown = { coroutineScope.launch { scrollState.animateScrollBy(200f) } }
        )
    }

    DisposableEffect(inputHandler) {
        inputDispatcher.subscribeView(inputHandler)
        onDispose { }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val game = uiState.game
        if (uiState.isLoading || game == null) {
            GameDetailSkeleton()
        } else {
            GameDetailContent(
                game = game,
                uiState = uiState,
                viewModel = viewModel,
                scrollState = scrollState
            )
        }
    }
}

@Composable
private fun GameDetailContent(
    game: GameDetailUi,
    uiState: GameDetailUiState,
    viewModel: GameDetailViewModel,
    scrollState: ScrollState
) {
    val showAnyOverlay = uiState.showMoreOptions || uiState.showEmulatorPicker || uiState.showRatingPicker
    val modalBlur by animateDpAsState(
        targetValue = if (showAnyOverlay) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "modalBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().blur(modalBlur)) {
            if (game.backgroundPath != null) {
            AsyncImage(
                model = game.backgroundPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 80.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                AsyncImage(
                    model = game.coverPath,
                    contentDescription = game.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = game.platformName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        game.releaseYear?.let { year ->
                            Text(
                                text = "|",
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        game.developer?.let { dev ->
                            Text(
                                text = dev,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                        game.genre?.let { genre ->
                            if (game.developer != null) {
                                Text(
                                    text = "|",
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                iconColor = Color(0xFFFFD700)
                            )
                        }
                        if (game.userDifficulty > 0) {
                            RatingChip(
                                label = "Difficulty",
                                value = game.userDifficulty,
                                icon = Icons.Default.Whatshot,
                                iconColor = Color(0xFFE53935)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.primaryAction() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            when (uiState.downloadStatus) {
                                GameDownloadStatus.DOWNLOADED -> {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PLAY")
                                }
                                GameDownloadStatus.NOT_DOWNLOADED -> {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("DOWNLOAD")
                                }
                                GameDownloadStatus.QUEUED -> {
                                    Text("QUEUED...")
                                }
                                GameDownloadStatus.WAITING_FOR_STORAGE -> {
                                    Text("NO SPACE")
                                }
                                GameDownloadStatus.DOWNLOADING -> {
                                    Text("${(uiState.downloadProgress * 100).toInt()}%")
                                }
                                GameDownloadStatus.PAUSED -> {
                                    Text("PAUSED ${(uiState.downloadProgress * 100).toInt()}%")
                                }
                            }
                        }

                        IconButton(onClick = { viewModel.toggleFavorite() }) {
                            Icon(
                                imageVector = if (game.isFavorite) Icons.Default.Favorite
                                    else Icons.Default.FavoriteBorder,
                                contentDescription = if (game.isFavorite) "Unfavorite" else "Favorite",
                                tint = if (game.isFavorite) Color.Red
                                    else Color.White
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(onClick = { viewModel.toggleMoreOptions() }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (!game.description.isNullOrBlank()) {
                Text(
                    text = "DESCRIPTION",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            if (game.screenshots.isNotEmpty()) {
                Text(
                    text = "SCREENSHOTS",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                val screenshotListState = rememberLazyListState()
                var scrollDirection by remember { mutableIntStateOf(1) }

                LaunchedEffect(game.screenshots) {
                    if (game.screenshots.size <= 1) return@LaunchedEffect

                    while (true) {
                        delay(3000)

                        val layoutInfo = screenshotListState.layoutInfo
                        val currentIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                        val lastIndex = game.screenshots.size - 1

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

                        screenshotListState.animateScrollToItem(nextIndex)
                    }
                }

                LazyRow(
                    state = screenshotListState,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(game.screenshots) { screenshot ->
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .height(135.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            screenshot.cachedPath?.let { path ->
                                AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            AsyncImage(
                                model = screenshot.remoteUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
        }

        AnimatedVisibility(
            visible = uiState.showMoreOptions,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MoreOptionsOverlay(
                game = game,
                focusIndex = uiState.moreOptionsFocusIndex,
                isDownloaded = uiState.downloadStatus == GameDownloadStatus.DOWNLOADED
            )
        }

        AnimatedVisibility(
            visible = uiState.showEmulatorPicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            EmulatorPickerOverlay(
                availableEmulators = uiState.availableEmulators,
                currentEmulatorName = game.emulatorName,
                focusIndex = uiState.emulatorPickerFocusIndex
            )
        }

        AnimatedVisibility(
            visible = uiState.showRatingPicker,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            RatingPickerOverlay(
                type = uiState.ratingPickerType,
                value = uiState.ratingPickerValue
            )
        }

        AnimatedVisibility(
            visible = !uiState.showMoreOptions && !uiState.showEmulatorPicker && !uiState.showRatingPicker,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FooterBar(
                hints = listOf(
                    InputButton.DPAD_VERTICAL to "Scroll",
                    InputButton.DPAD_HORIZONTAL to "Change Game",
                    InputButton.A to when (uiState.downloadStatus) {
                        GameDownloadStatus.DOWNLOADED -> "Play"
                        GameDownloadStatus.NOT_DOWNLOADED -> "Download"
                        GameDownloadStatus.QUEUED -> "Queued"
                        GameDownloadStatus.WAITING_FOR_STORAGE -> "No Space"
                        GameDownloadStatus.DOWNLOADING -> "Downloading"
                        GameDownloadStatus.PAUSED -> "Paused"
                    },
                    InputButton.B to "Back",
                    InputButton.Y to if (uiState.game?.isFavorite == true) "Unfavorite" else "Favorite"
                )
            )
        }
    }
}

@Composable
private fun MetadataChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun RatingChip(
    label: String,
    value: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$value/10",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun CommunityRatingChip(rating: Float) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(
                Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = Color(0xFF64B5F6),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "${rating.toInt()}%",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
        }
        Text(
            text = "Rating",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun RatingPickerOverlay(
    type: RatingType,
    value: Int
) {
    val isRating = type == RatingType.OPINION
    val title = if (isRating) "RATE GAME" else "SET DIFFICULTY"
    val filledIcon = if (isRating) Icons.Default.Star else Icons.Default.Whatshot
    val outlineIcon = if (isRating) Icons.Default.StarOutline else Icons.Outlined.Whatshot
    val filledColor = if (isRating) Color(0xFFFFD700) else Color(0xFFE53935)
    val outlineColor = Color.White.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (i in 1..10) {
                    val isFilled = i <= value
                    Icon(
                        imageVector = if (isFilled) filledIcon else outlineIcon,
                        contentDescription = null,
                        tint = if (isFilled) filledColor else outlineColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (value == 0) "Not set" else "$value/10",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            FooterBar(
                hints = listOf(
                    InputButton.DPAD_HORIZONTAL to "Adjust",
                    InputButton.A to "Confirm",
                    InputButton.B to "Cancel"
                )
            )
        }
    }
}

@Composable
private fun MoreOptionsOverlay(
    game: GameDetailUi,
    focusIndex: Int,
    isDownloaded: Boolean
) {
    val isRommGame = game.isRommGame
    var currentIndex = 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .padding(24.dp)
                .width(350.dp)
        ) {
            Text(
                text = "MORE OPTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            OptionItem(
                label = "Change Emulator",
                value = game.emulatorName ?: "Default",
                isFocused = focusIndex == currentIndex++
            )
            if (isRommGame) {
                OptionItem(
                    icon = Icons.Default.Star,
                    label = "Rate Game",
                    value = if (game.userRating > 0) "${game.userRating}/10" else "Not rated",
                    isFocused = focusIndex == currentIndex++
                )
                OptionItem(
                    icon = Icons.Default.Whatshot,
                    label = "Set Difficulty",
                    value = if (game.userDifficulty > 0) "${game.userDifficulty}/10" else "Not set",
                    isFocused = focusIndex == currentIndex++
                )
            }
            if (isDownloaded) {
                OptionItem(
                    icon = Icons.Default.DeleteOutline,
                    label = "Delete Download",
                    isFocused = focusIndex == currentIndex++,
                    isDangerous = true
                )
            }
            OptionItem(
                label = "Hide",
                isFocused = focusIndex == currentIndex,
                isDangerous = true
            )
        }
    }
}

@Composable
private fun EmulatorPickerOverlay(
    availableEmulators: List<InstalledEmulator>,
    currentEmulatorName: String?,
    focusIndex: Int
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(12.dp)
                )
                .padding(24.dp)
                .width(350.dp)
        ) {
            Text(
                text = "SELECT EMULATOR",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            OptionItem(
                label = "Use Platform Default",
                isFocused = focusIndex == 0,
                isSelected = currentEmulatorName == null
            )

            availableEmulators.forEachIndexed { index, emulator ->
                OptionItem(
                    label = emulator.def.displayName,
                    value = emulator.versionName,
                    isFocused = focusIndex == index + 1,
                    isSelected = emulator.def.displayName == currentEmulatorName
                )
            }
        }
    }
}

@Composable
private fun OptionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    value: String? = null,
    isFocused: Boolean = false,
    isDangerous: Boolean = false,
    isSelected: Boolean = false
) {
    val contentColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.onErrorContainer
        isDangerous -> MaterialTheme.colorScheme.error
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val backgroundColor = when {
        isDangerous && isFocused -> MaterialTheme.colorScheme.errorContainer
        isFocused -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.width(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Text(
                text = "[Current]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (value != null) {
            Text(
                text = "[$value]",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GameDetailSkeleton() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.5f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}
