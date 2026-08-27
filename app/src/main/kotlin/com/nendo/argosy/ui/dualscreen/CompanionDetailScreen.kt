package com.nendo.argosy.ui.dualscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale

/**
 * The showcase screen while the driven screen is on Library or Media.
 *
 * It describes what the other screen has focused and takes no input of its own, which is the whole
 * point of the role: one screen is being driven and this one is answering "what is that".
 */
@Composable
fun CompanionDetailScreen(
    detail: CompanionDetail,
    modifier: Modifier = Modifier,
    footerHints: @Composable (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    val isWideDisplay = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }
    val gutter = if (isWideDisplay) Dimens.spacingXxl else Dimens.spacingLg

    Box(modifier = modifier.fillMaxSize().background(theme.surfaceBase)) {
        detail.backdropUrl?.let { backdrop ->
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                theme.surfaceBase.copy(alpha = SCRIM_TOP_ALPHA),
                                theme.surfaceBase.copy(alpha = SCRIM_BOTTOM_ALPHA)
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gutter)
        ) {
            detail.artUrl?.let { art ->
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.Center,
                    modifier = Modifier
                        .weight(ART_WEIGHT)
                        .fillMaxHeight(if (isWideDisplay) WIDE_ART_HEIGHT else NARROW_ART_HEIGHT)
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                )
            }

            Column(
                modifier = Modifier.weight(TEXT_WEIGHT),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
            ) {
                detail.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.focusAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (detail.isGameTitle) {
                    GameTitle(
                        title = detail.title,
                        titleStyle = MaterialTheme.typography.displaySmall,
                        titleColor = theme.textPrimary,
                        maxLines = 2
                    )
                } else {
                    Text(
                        text = detail.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = theme.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (detail.facts.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                        detail.facts.forEach { fact ->
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fact.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.textMute,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = fact.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                detail.overview?.let { overview ->
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.textDim,
                        maxLines = if (isWideDisplay) OVERVIEW_LINES_WIDE else OVERVIEW_LINES_NARROW,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        footerHints?.let { hints ->
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { hints() }
        }
    }
}

/**
 * The art and the text share the row by weight rather than the art sizing itself.
 *
 * An image asked only for a height reports whatever width its bitmap wants, which on a backdrop is
 * wider than the screen, so the text beside it is measured at nothing and never appears.
 */
private const val ART_WEIGHT = 0.4f
private const val TEXT_WEIGHT = 1f
/**
 * What the buttons do on the screen being driven, spelled out on the screen describing it.
 *
 * The showcase takes no input of its own, so these name the other screen's actions; a viewer
 * looking at the description still needs to be told what pressing A over there will do.
 */
@Composable
fun companionDetailHints(
    detail: CompanionDetail,
    viewMode: String = ""
): List<Pair<com.nendo.argosy.ui.components.InputButton, String>> = when {
    detail.isGameTitle -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_game_section),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_game_open),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_game_back)
    )
    viewMode == "MEDIA_GRID" -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_media_grid_library),
        com.nendo.argosy.ui.components.InputButton.Y to
            stringResource(R.string.dual_detail_hint_media_grid_resume),
        com.nendo.argosy.ui.components.InputButton.X to
            stringResource(R.string.dual_detail_hint_media_grid_options),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_media_grid_play),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_media_grid_back)
    )
    viewMode == "MEDIA_INFO" -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_media_info_title),
        com.nendo.argosy.ui.components.InputButton.DPAD_HORIZONTAL to
            stringResource(R.string.dual_detail_hint_media_info_season),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_media_info_watch),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_media_info_back)
    )
    else -> listOf(
        com.nendo.argosy.ui.components.InputButton.LB_RB to
            stringResource(R.string.dual_detail_hint_default_section),
        com.nendo.argosy.ui.components.InputButton.Y to
            stringResource(R.string.dual_detail_hint_default_favorite),
        com.nendo.argosy.ui.components.InputButton.X to
            stringResource(R.string.dual_detail_hint_default_options),
        com.nendo.argosy.ui.components.InputButton.A to
            stringResource(R.string.dual_detail_hint_default_play),
        com.nendo.argosy.ui.components.InputButton.B to
            stringResource(R.string.dual_detail_hint_default_back)
    )
}

private const val SCRIM_TOP_ALPHA = 0.55f
private const val SCRIM_BOTTOM_ALPHA = 0.92f
private const val WIDE_ART_HEIGHT = 0.72f
private const val NARROW_ART_HEIGHT = 0.55f
private const val OVERVIEW_LINES_WIDE = 5
private const val OVERVIEW_LINES_NARROW = 3
