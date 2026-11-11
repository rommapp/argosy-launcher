package com.nendo.argosy.ui.quickmenu.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import com.nendo.argosy.ui.components.FooterHint
import com.nendo.argosy.ui.components.FooterStyleConfig
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.LocalFooterStyle
import com.nendo.argosy.ui.quickmenu.GameCardUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

private val RandomTokens = ComponentDefaults.QuickMenuRandom

@Composable
fun QuickMenuRandomContent(
    game: GameCardUi?,
    isResolved: Boolean,
    isFocused: Boolean,
    onPlay: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onFavorite: () -> Unit,
    onReroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Dimens.radiusPanel)
    val borderModifier = if (isFocused) {
        Modifier.border(Dimens.borderMedium, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(borderModifier)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            game != null -> AnimatedContent(
                targetState = game,
                transitionSpec = {
                    fadeIn(animationSpec = tween(Motion.durationContent)) togetherWith
                        fadeOut(animationSpec = tween(Motion.durationContent))
                },
                contentKey = { it.id },
                label = "quickMenuRandomGame"
            ) { shown ->
                Box(modifier = Modifier.fillMaxSize()) {
                    RandomBackdrop(path = shown.backdropPath, isScreenshot = shown.backdropIsScreenshotOrArt)
                    RandomGameLayout(
                        game = shown,
                        onPlay = onPlay,
                        onDetails = onDetails,
                        onFavorite = onFavorite,
                        onReroll = onReroll
                    )
                }
            }
            isResolved -> RandomPlaceholder(
                message = stringResource(R.string.ui_quick_menu_empty_random),
                isLoading = false
            )
            else -> RandomPlaceholder(
                message = stringResource(R.string.ui_quick_menu_random_loading),
                isLoading = true
            )
        }
    }
}

@Composable
private fun RandomBackdrop(path: String?, isScreenshot: Boolean) {
    val surface = MaterialTheme.colorScheme.surface
    val model = rememberFileImageModel(path)
    val context = LocalContext.current
    AsyncImage(
        model = remember(model, isScreenshot) {
            ImageRequest.Builder(context)
                .data(model)
                .apply { if (isScreenshot) stackedScreenshotBackdrop() }
                .build()
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .blur(RandomTokens.backdropBlurDp.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        surface.copy(alpha = RandomTokens.backdropScrimStartAlpha),
                        surface.copy(alpha = RandomTokens.backdropScrimEndAlpha)
                    )
                )
            )
    )
}

@Composable
private fun RandomGameLayout(
    game: GameCardUi,
    onPlay: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onFavorite: () -> Unit,
    onReroll: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val panelHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.spacingLg)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val coverHeight = minOf(
                    panelHeight * RandomTokens.coverHeightRatio,
                    maxHeight,
                    maxWidth * RandomTokens.coverWidthMaxRatio / RandomTokens.coverAspectRatio
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl)
                ) {
                    RandomCover(
                        game = game,
                        height = coverHeight,
                        onClick = { onDetails(game.id) }
                    )
                    RandomDetails(
                        game = game,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Dimens.spacingMd))

            RandomActions(
                game = game,
                onPlay = onPlay,
                onDetails = onDetails,
                onFavorite = onFavorite,
                onReroll = onReroll
            )
        }
    }
}

@Composable
private fun RandomCover(
    game: GameCardUi,
    height: Dp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusLg)
    AsyncImage(
        model = rememberFileImageModel(game.coverPath),
        contentDescription = game.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .height(height)
            .aspectRatio(RandomTokens.coverAspectRatio)
            .shadow(Dimens.elevationFocused, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickableNoFocus(onClick = onClick)
    )
}

@Composable
private fun RandomDetails(
    game: GameCardUi,
    modifier: Modifier = Modifier
) {
    val metaLine = listOfNotNull(game.year?.toString(), game.developer, game.genre)
        .joinToString(" | ")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Text(
            text = game.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = game.platformName
                ?: stringResource(R.string.ui_quick_menu_random_platform_unknown),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (game.rating != null || game.isDownloaded) {
            RandomBadges(rating = game.rating, isDownloaded = game.isDownloaded)
        }

        game.description?.let { description ->
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = RandomTokens.descriptionMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}

@Composable
private fun RandomBadges(
    rating: Float?,
    isDownloaded: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        rating?.let {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
            ) {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
                )
                Text(
                    text = "${it.toInt()}%",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isDownloaded) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.ui_quick_menu_random_downloaded),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconMd)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RandomActions(
    game: GameCardUi,
    onPlay: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onFavorite: () -> Unit,
    onReroll: () -> Unit
) {
    val isRunning = com.nendo.argosy.ui.common.LocalRunningGames.current.isRunning(game.id)
    val playLabel = stringResource(
        when {
            isRunning -> R.string.ui_quick_menu_random_action_resume_game
            game.needsInstall -> R.string.ui_quick_menu_random_action_install
            game.isDownloaded -> R.string.ui_quick_menu_random_action_play
            else -> R.string.ui_quick_menu_random_action_download
        }
    )
    val favoriteLabel = stringResource(
        if (game.isFavorite) {
            R.string.ui_quick_menu_random_action_unfavorite
        } else {
            R.string.ui_quick_menu_random_action_favorite
        }
    )

    CompositionLocalProvider(LocalFooterStyle provides FooterStyleConfig()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            RandomAction(
                button = InputButton.DPAD_HORIZONTAL,
                label = stringResource(R.string.ui_quick_menu_random_action_reroll),
                onClick = onReroll
            )
            RandomAction(
                button = InputButton.Y,
                label = favoriteLabel,
                onClick = onFavorite
            )
            RandomAction(
                button = InputButton.X,
                label = stringResource(R.string.ui_quick_menu_random_action_details),
                onClick = { onDetails(game.id) }
            )
            RandomAction(
                button = InputButton.A,
                label = playLabel,
                onClick = { onPlay(game.id) }
            )
        }
    }
}

@Composable
private fun RandomAction(
    button: InputButton,
    label: String,
    onClick: () -> Unit
) {
    FooterHint(
        button = button,
        action = label,
        modifier = Modifier
            .clip(RoundedCornerShape(Dimens.radiusPill))
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

@Composable
private fun RandomPlaceholder(
    message: String,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingXl),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = Dimens.borderMedium,
                modifier = Modifier.size(Dimens.iconLg)
            )
        } else {
            Icon(
                Icons.Default.Casino,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconXl)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
