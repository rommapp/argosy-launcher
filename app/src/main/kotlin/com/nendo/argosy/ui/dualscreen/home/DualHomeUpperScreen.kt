/**
 * DUAL-SCREEN COMPONENT - Upper display game showcase.
 * Runs in main process (MainActivity).
 * Receives selection from lower display via broadcasts.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.Box3dCover
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.SystemStatusBar
import com.nendo.argosy.ui.dualscreen.ShowcaseAmbience
import com.nendo.argosy.ui.dualscreen.ShowcaseEyebrow
import com.nendo.argosy.ui.dualscreen.ShowcaseRatingsCluster
import com.nendo.argosy.ui.dualscreen.ShowcaseStatsRow
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.util.formatPlayTime
import java.time.Instant
import java.time.temporal.ChronoUnit

data class DualHomeShowcaseState(
    val gameId: Long = -1,
    val title: String = "",
    val coverPath: String? = null,
    val coverAspectRatio: Float? = null,
    val backgroundPath: String? = null,
    val boxBackPath: String? = null,
    val boxSpinePath: String? = null,
    val platformName: String = "",
    val platformSlug: String = "",
    val playTimeMinutes: Int = 0,
    val lastPlayedAt: Long = 0,
    val status: String? = null,
    val communityRating: Float? = null,
    val userRating: Int = 0,
    val userDifficulty: Int = 0,
    val description: String? = null,
    val developer: String? = null,
    val releaseYear: Int? = null,
    val titleId: String? = null,
    val timeToBeatMainSec: Int? = null,
    val timeToBeatExtraSec: Int? = null,
    val timeToBeatCompletionistSec: Int? = null,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = true,
    val useGameBackground: Boolean = true,
    val customWallpaperPath: String? = null
)

@Composable
fun DualHomeUpperScreen(
    state: DualHomeShowcaseState,
    footerHints: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val effectiveBackgroundPath = if (state.useGameBackground) {
        state.backgroundPath ?: state.coverPath
    } else {
        state.customWallpaperPath
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        ShowcaseAmbience(artPath = effectiveBackgroundPath)

        SystemStatusBar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Dimens.spacingXxl, vertical = Dimens.spacingLg)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (state.gameId > 0) {
                val isWideDisplay = LocalUiScale.current.aspectRatioClass.let {
                    it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
                }
                val gutter = if (isWideDisplay) Dimens.spacingXxl else Dimens.spacingLg
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = gutter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gutter)
                ) {
                    ShowcaseHeroCard(
                        state = state,
                        heightFraction = if (isWideDisplay) WIDE_ART_HEIGHT else NARROW_ART_HEIGHT
                    )
                    ShowcaseInfoColumn(
                        state = state,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dual_home_showcase_no_selection),
                        style = MaterialTheme.typography.headlineSmall,
                        color = theme.textDim
                    )
                }
            }

            HorizontalDivider(color = theme.hairlineLow)

            footerHints()
        }
    }
}

@Composable
private fun ShowcaseHeroCard(state: DualHomeShowcaseState, heightFraction: Float) {
    val boxArtStyle = LocalBoxArtStyle.current
    if (state.coverPath == null) return
    if (state.boxSpinePath != null && state.coverPath.startsWith("/")) {
        Box3dCover(
            frontPath = state.coverPath,
            spinePath = state.boxSpinePath,
            backPath = state.boxBackPath,
            modifier = Modifier.fillMaxHeight(heightFraction)
        )
    } else {
        val artRatio = if (boxArtStyle.nativeAspectRatio) {
            state.coverAspectRatio
                ?: com.nendo.argosy.ui.common.rememberCoverAspectRatio(
                    state.coverPath,
                    boxArtStyle.aspectRatio
                )
        } else {
            boxArtStyle.aspectRatio
        }
        AsyncImage(
            model = rememberFileImageModel(state.coverPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxHeight(heightFraction)
                .aspectRatio(artRatio)
                .clip(RoundedCornerShape(Dimens.radiusSm))
        )
    }
}

@Composable
private fun ShowcaseInfoColumn(
    state: DualHomeShowcaseState,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val isWideDisplay = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        ShowcaseEyebrow(
            platformName = state.platformName,
            releaseYear = state.releaseYear,
            developer = state.developer,
            stacked = !isWideDisplay
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        GameTitle(
            title = state.title,
            titleStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            titleColor = theme.textPrimary,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        ShowcaseRatingsCluster(
            communityRating = state.communityRating,
            userRating = state.userRating,
            userDifficulty = state.userDifficulty,
            timeToBeatMainSec = state.timeToBeatMainSec
        )
        Spacer(modifier = Modifier.height(Dimens.spacingXl))
        ShowcaseStatsRow(
            playTimeMinutes = state.playTimeMinutes,
            lastPlayedAt = state.lastPlayedAt,
            status = state.status
        )
    }
}

/**
 * How much of the panel's height the cover takes, which is also what decides its width.
 *
 * The art is sized by height and then given its own aspect ratio, so on a near-square panel the
 * same fraction costs far more of the width than it does on a 16:9 one and squeezes everything
 * beside it. The narrow figure buys that width back for the text.
 */
private const val WIDE_ART_HEIGHT = 0.72f
private const val NARROW_ART_HEIGHT = 0.55f

