package com.nendo.argosy.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nendo.argosy.ui.icons.InputIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onGameSelect: (Long) -> Unit,
    onDrawerToggle: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.focusedGameIndex, uiState.currentRow, uiState.currentGames.size) {
        if (uiState.currentGames.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(
                    index = uiState.focusedGameIndex.coerceIn(0, uiState.currentGames.lastIndex),
                    scrollOffset = 100
                )
            }
        }
    }

    BackHandler(enabled = true) {
        // Prevent back from popping Home screen off nav stack
        // Home is the root destination - back should do nothing
    }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.launchEvents.collect { event ->
            when (event) {
                is HomeLaunchEvent.Launch -> {
                    try {
                        android.util.Log.d("HomeScreen", "Starting activity: ${event.intent}")
                        context.startActivity(event.intent)
                        android.util.Log.d("HomeScreen", "Activity started successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Failed to start activity", e)
                        viewModel.showLaunchError("Failed to launch: ${e.message}")
                    }
                }
            }
        }
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onGameSelect, onDrawerToggle) {
        viewModel.createInputHandler(
            onGameSelect = onGameSelect,
            onDrawerToggle = onDrawerToggle
        )
    }

    DisposableEffect(inputHandler) {
        inputDispatcher.setActiveScreen(inputHandler)
        onDispose {
            inputDispatcher.setActiveScreen(null)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.focusedGame?.backgroundPath,
            transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            },
            label = "background"
        ) { backgroundPath ->
            if (backgroundPath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(backgroundPath)
                        .size(640, 360)
                        .crossfade(300)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            HomeHeader(
                sectionTitle = uiState.rowTitle,
                platformLogo = if (uiState.currentRow is HomeRow.Platform) {
                    uiState.currentPlatform?.logoPath
                } else null,
                showPlatformNav = false
            )

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        LoadingState()
                    }
                    uiState.currentGames.isEmpty() -> {
                        EmptyState(
                            onSync = { viewModel.syncFromRomm() }
                        )
                    }
                    else -> {
                        GameRail(
                            games = uiState.currentGames,
                            focusedIndex = uiState.focusedGameIndex,
                            listState = listState,
                            rowKey = uiState.currentRow.toString(),
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }

                GameInfo(
                    title = uiState.focusedGame?.title ?: "",
                    developer = uiState.focusedGame?.developer,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .align(Alignment.TopEnd)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (uiState.isSyncing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Syncing library...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.showGameMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val focusedGame = uiState.focusedGame
            if (focusedGame != null) {
                GameSelectOverlay(
                    game = focusedGame,
                    focusIndex = uiState.gameMenuFocusIndex,
                    onDismiss = { viewModel.toggleGameMenu() }
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    sectionTitle: String,
    platformLogo: String?,
    showPlatformNav: Boolean
) {
    val currentTime = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
            currentTime.longValue = System.currentTimeMillis()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedContent(
                targetState = sectionTitle to platformLogo,
                transitionSpec = {
                    (slideInVertically { -it / 2 } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically { it / 2 } + fadeOut(tween(150)))
                },
                label = "section"
            ) { (title, logo) ->
                if (logo != null) {
                    AsyncImage(
                        model = logo,
                        contentDescription = title,
                        modifier = Modifier.height(48.dp)
                    )
                } else {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                }
            }

            if (showPlatformNav) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        painter = InputIcons.BumperLeft,
                        contentDescription = "Previous platform",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        painter = InputIcons.BumperRight,
                        contentDescription = "Next platform",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Text(
            text = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(currentTime.longValue)),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun GameInfo(
    title: String,
    developer: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        if (developer != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = developer,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun GameRail(
    games: List<HomeGameUi>,
    focusedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    rowKey: String,
    modifier: Modifier = Modifier
) {
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(start = 48.dp, end = 600.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
    ) {
        itemsIndexed(games, key = { _, game -> "$rowKey-${game.id}" }) { index, game ->
            val isFocused = index == focusedIndex
            GameCard(
                game = game,
                isFocused = isFocused,
                focusScale = 1.8f,
                scaleFromBottom = true,
                modifier = Modifier
                    .padding(horizontal = if (isFocused) 64.dp else 0.dp)
                    .width(120.dp)
                    .height(160.dp)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun EmptyState(
    onSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No games yet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sync your library from Rom Manager to get started",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        FooterHint(button = InputButton.A, action = "Sync Library")
    }
}

@Composable
private fun GameSelectOverlay(
    game: HomeGameUi,
    focusIndex: Int,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                .padding(24.dp)
                .width(350.dp)
        ) {
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            MenuOption(
                icon = if (game.isDownloaded) Icons.Default.PlayArrow else Icons.Default.Download,
                label = if (game.isDownloaded) "Play" else "Download",
                isFocused = focusIndex == 0
            )
            MenuOption(
                icon = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (game.isFavorite) "Unfavorite" else "Favorite",
                isFocused = focusIndex == 1
            )
            MenuOption(
                icon = Icons.Default.Info,
                label = "Details",
                isFocused = focusIndex == 2
            )
            MenuOption(
                label = "Hide",
                isFocused = focusIndex == 3,
                isDangerous = true
            )
        }
    }
}

@Composable
private fun MenuOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    isFocused: Boolean = false,
    isDangerous: Boolean = false
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
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
    }
}
