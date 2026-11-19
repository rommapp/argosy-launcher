package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.PresentationArt
import com.nendo.argosy.domain.model.PresentationLayout
import com.nendo.argosy.domain.model.PresentationStyle
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.rememberResolvedArt
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.dualscreen.GameHero
import com.nendo.argosy.ui.dualscreen.withLiveArt
import com.nendo.argosy.ui.screens.gamedetail.GameDetailUi
import com.nendo.argosy.ui.screens.gamedetail.toCompanionDetail
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.motionEnter
import com.nendo.argosy.ui.theme.motionExit
import com.nendo.argosy.ui.theme.motionSpec
import com.nendo.argosy.util.formatPlayTime

private val COLLAPSED_THUMBNAIL_SIZE = 48.dp
private val COLLAPSED_BAR_HEIGHT = 64.dp
private val DETAIL_HERO_STYLE = PresentationStyle(
    layout = PresentationLayout.CINEMATIC,
    art = PresentationArt.BOX_3D,
    hiddenStats = emptySet()
)

data class CollapsingHeaderState(
    val isCollapsed: Boolean = false
)

@Composable
fun CollapsingHeader(
    game: GameDetailUi,
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier
) {
    val collapseProgress by animateFloatAsState(
        targetValue = if (state.isCollapsed) 1f else 0f,
        animationSpec = motionSpec(spring()),
        label = "collapse_progress"
    )

    Box(modifier = modifier) {
        ExpandedHeader(
            game = game,
            modifier = Modifier
                .alpha(1f - collapseProgress)
                .graphicsLayer {
                    scaleX = 1f - (collapseProgress * 0.1f)
                    scaleY = 1f - (collapseProgress * 0.1f)
                }
        )

        if (collapseProgress > 0f) {
            CollapsedHeader(
                game = game,
                modifier = Modifier
                    .alpha(collapseProgress)
            )
        }
    }
}

@Composable
fun StickyCollapsedHeader(
    game: GameDetailUi,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = motionEnter(expandVertically()),
        exit = motionExit(shrinkVertically()),
        modifier = modifier
    ) {
        CollapsedHeader(game = game)
    }
}

@Composable
fun ExpandedHeader(
    game: GameDetailUi,
    modifier: Modifier = Modifier,
    friends: List<com.nendo.argosy.data.social.FriendActivity> = emptyList()
) {
    val art = rememberResolvedArt(game.id)
    val detail = remember(game, friends, art) { game.toCompanionDetail(friends).withLiveArt(art) }
    Column(modifier = modifier.fillMaxWidth()) {
        GameHero(
            detail = detail,
            style = DETAIL_HERO_STYLE,
            coverMaxHeight = Dimens.gameCardHeight,
            modifier = Modifier.fillMaxWidth(),
            interactiveCover = true
        )
        if (game.isRemovedFromRomm) {
            Text(
                text = stringResource(R.string.gamedetail_header_not_on_romm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.spacingSm)
            )
        }
    }
}

@Composable
internal fun CollapsedHeader(
    game: GameDetailUi,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(COLLAPSED_BAR_HEIGHT)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        AsyncImage(
            model = rememberFileImageModel(rememberResolvedCoverPath(game.id, game.coverPath)),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(COLLAPSED_THUMBNAIL_SIZE)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = game.platformName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            if (game.rating != null || game.userRating > 0 || game.userDifficulty > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    game.rating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(
                                    R.string.gamedetail_collapsed_header_rating_percent,
                                    rating.toInt()
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (game.userRating > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = ALauncherColors.StarGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${game.userRating}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (game.userDifficulty > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                tint = ALauncherColors.DifficultyRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${game.userDifficulty}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (game.playTimeMinutes > 0 || game.playCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (game.playTimeMinutes > 0) {
                        Text(
                            text = formatPlayTime(context, game.playTimeMinutes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (game.playCount > 0) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.gamedetail_collapsed_header_play_count,
                                game.playCount,
                                game.playCount
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

