package com.nendo.argosy.ui.quickmenu.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.common.stackedScreenshotBackdrop
import com.nendo.argosy.ui.components.gamelist.DownloadStateMark
import com.nendo.argosy.ui.quickmenu.FavoritesSort
import com.nendo.argosy.ui.quickmenu.MostPlayedRange
import com.nendo.argosy.ui.quickmenu.QuickMenuGameUi
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.quickmenu.RecentGroup
import com.nendo.argosy.ui.quickmenu.TopNewReason
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

private val QuickMenuTokens = ComponentDefaults.QuickMenu
private val RandomTokens = ComponentDefaults.QuickMenuRandom

enum class ChipTone { NEUTRAL, ACCENT, SUCCESS, RUNNING }

@Composable
fun QuickMenuChip(text: String, tone: ChipTone = ChipTone.NEUTRAL, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val semantic = LocalLauncherTheme.current.semanticColors
    val tint = when (tone) {
        ChipTone.NEUTRAL -> null
        ChipTone.ACCENT -> theme.focusAccent
        ChipTone.SUCCESS -> semantic.success
        ChipTone.RUNNING -> semantic.progress
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tint ?: theme.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(tint?.copy(alpha = QuickMenuTokens.chipTintAlpha) ?: theme.surfaceElevated)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

@Composable
fun QuickMenuBackdrop(path: String?, isScreenshot: Boolean, modifier: Modifier = Modifier) {
    val surface = MaterialTheme.colorScheme.surface
    val model = rememberFileImageModel(path)
    val context = LocalContext.current
    Box(modifier = modifier) {
        if (path != null) {
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
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            surface.copy(alpha = RandomTokens.backdropScrimEndAlpha),
                            surface.copy(alpha = RandomTokens.backdropScrimStartAlpha)
                        )
                    )
                )
        )
    }
}

/**
 * A game's cover. With no [path] it draws [title] on a neutral surface; [showTitle] false keeps a
 * small tile plain.
 */
@Composable
fun QuickMenuCover(path: String?, title: String, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    if (path == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (showTitle) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Dimens.spacingSm)
                )
            }
        }
        return
    }
    AsyncImage(
        model = rememberFileImageModel(path),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    )
}

@Composable
fun NotDownloadedMark(game: QuickMenuGameUi, indicator: GameDownloadIndicator, size: Dp = Dimens.iconSm) {
    if (indicator.isShown || game.needsInstall) {
        DownloadStateMark(indicator = indicator, isDownloaded = false, needsInstall = game.needsInstall)
        return
    }
    if (game.isDownloaded) return
    Icon(
        imageVector = Icons.Default.CloudDownload,
        contentDescription = stringResource(R.string.ui_quick_menu_row_not_downloaded_cd),
        tint = LocalArgosyTheme.current.textDim,
        modifier = Modifier.size(size)
    )
}

@Composable
fun TileNotDownloadedMark(game: QuickMenuGameUi, indicator: GameDownloadIndicator, modifier: Modifier = Modifier) {
    if (game.isDownloaded && !game.needsInstall) return
    if (indicator.isShown) return
    Box(
        modifier = modifier
            .padding(Dimens.spacingXs)
            .size(Dimens.iconMd)
            .background(MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        NotDownloadedMark(game = game, indicator = indicator, size = Dimens.iconSm)
    }
}

@Composable
fun PlayStrip(weeks: List<Long>, busiestWeek: Int?, modifier: Modifier = Modifier) {
    val accent = LocalArgosyTheme.current.focusAccent
    val peak = weeks.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = modifier.height(QuickMenuTokens.playStripHeightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
        verticalAlignment = Alignment.Bottom
    ) {
        weeks.forEachIndexed { index, minutes ->
            val color: Color = if (index == busiestWeek) accent else accent.copy(alpha = QuickMenuTokens.playStripRestAlpha)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight((minutes.toFloat() / peak).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(topStart = Dimens.radiusSm, topEnd = Dimens.radiusSm))
                    .background(color)
            )
        }
    }
}

@Composable
fun StatBlock(value: String, label: String, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, color = theme.textPrimary, maxLines = 1)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = theme.textDim, maxLines = 1)
    }
}

@Composable
fun SectionEmpty(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalArgosyTheme.current.textDim,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun topNewReasonText(reason: TopNewReason?): String = when {
    reason == null -> stringResource(R.string.ui_quick_menu_top_new_reason_top_rated)
    reason.genre != null && reason.platformName != null ->
        stringResource(R.string.ui_quick_menu_top_new_reason_both, reason.genre, reason.platformName)
    reason.genre != null -> stringResource(R.string.ui_quick_menu_top_new_reason_genre, reason.genre)
    else -> stringResource(R.string.ui_quick_menu_top_new_reason_platform, reason.platformName.orEmpty())
}

val QuickMenuOrb.icon: ImageVector
    get() = when (this) {
        QuickMenuOrb.SEARCH -> Icons.Default.Search
        QuickMenuOrb.RANDOM -> Icons.Default.Casino
        QuickMenuOrb.MOST_PLAYED -> Icons.Default.Timer
        QuickMenuOrb.TOP_UNPLAYED -> Icons.Default.Star
        QuickMenuOrb.RECENT -> Icons.Default.History
        QuickMenuOrb.FAVORITES -> Icons.Default.Favorite
    }

@get:StringRes
val QuickMenuOrb.labelRes: Int
    get() = when (this) {
        QuickMenuOrb.SEARCH -> R.string.ui_quick_menu_orb_search
        QuickMenuOrb.RANDOM -> R.string.ui_quick_menu_orb_random
        QuickMenuOrb.MOST_PLAYED -> R.string.ui_quick_menu_orb_most_played
        QuickMenuOrb.TOP_UNPLAYED -> R.string.ui_quick_menu_orb_top_unplayed
        QuickMenuOrb.RECENT -> R.string.ui_quick_menu_orb_recent
        QuickMenuOrb.FAVORITES -> R.string.ui_quick_menu_orb_favorites
    }

@get:StringRes
val RecentGroup.labelRes: Int
    get() = when (this) {
        RecentGroup.TODAY -> R.string.ui_quick_menu_recent_group_today
        RecentGroup.YESTERDAY -> R.string.ui_quick_menu_recent_group_yesterday
        RecentGroup.THIS_WEEK -> R.string.ui_quick_menu_recent_group_this_week
        RecentGroup.EARLIER -> R.string.ui_quick_menu_recent_group_earlier
    }

@get:StringRes
val FavoritesSort.labelRes: Int
    get() = when (this) {
        FavoritesSort.RECENTLY_ADDED -> R.string.ui_quick_menu_favorites_sort_recently_added
        FavoritesSort.TITLE -> R.string.ui_quick_menu_favorites_sort_title
        FavoritesSort.PLATFORM -> R.string.ui_quick_menu_favorites_sort_platform
        FavoritesSort.LAST_PLAYED -> R.string.ui_quick_menu_favorites_sort_last_played
    }

@get:StringRes
val MostPlayedRange.labelRes: Int
    get() = when (this) {
        MostPlayedRange.ALL_TIME -> R.string.ui_quick_menu_most_played_range_all_time
        MostPlayedRange.LAST_30_DAYS -> R.string.ui_quick_menu_most_played_range_last_30
    }
