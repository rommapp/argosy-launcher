package com.nendo.argosy.ui.dualscreen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.ALauncherColors
import com.nendo.argosy.ui.theme.AspectRatioClass
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import com.nendo.argosy.util.formatPlayTime
import com.nendo.argosy.util.formatTimeToBeat
import java.time.Instant
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.labelRes

/** Shared ambience for DS upper showcase surfaces: backdrop, blurred art, dim wash. */
@Composable
fun ShowcaseAmbience(artPath: String?) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .surfaceBackdrop(BackdropRole.CONTENT)
    )
    Crossfade(
        targetState = artPath,
        animationSpec = tween(300),
        label = "showcase-ambience"
    ) { path ->
        if (path != null) {
            AsyncImage(
                model = rememberFileImageModel(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(28.dp)
                    .alpha(0.55f),
                onError = { }
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.surfaceBase.copy(alpha = 0.25f))
    )
}

@Composable
fun ShowcaseEyebrow(
    platformName: String?,
    releaseYear: Int?,
    developer: String?,
    titleId: String? = null,
    stacked: Boolean = false
) {
    val style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp)
    val color = LocalArgosyTheme.current.focusAccent
    val provenance = listOfNotNull(
        platformName?.takeIf { it.isNotBlank() }?.uppercase(),
        releaseYear?.toString()
    ).joinToString("  ·  ")
    val maker = listOfNotNull(
        developer,
        titleId?.takeIf { it.isNotBlank() }
    ).joinToString("  ·  ")

    if (!stacked) {
        Text(
            text = listOfNotNull(
                provenance.takeIf { it.isNotBlank() },
                maker.takeIf { it.isNotBlank() }
            ).joinToString("  ·  "),
            style = style,
            color = color
        )
        return
    }

    Column {
        if (provenance.isNotBlank()) {
            Text(text = provenance, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (maker.isNotBlank()) {
            Text(text = maker, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun ShowcaseRatingsCluster(
    communityRating: Float?,
    userRating: Int,
    userDifficulty: Int
) {
    val hasAnyRating = communityRating != null || userRating > 0 || userDifficulty > 0
    if (!hasAnyRating) return

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        communityRating?.let { rating ->
            ShowcaseRatingItem(
                icon = Icons.Default.People,
                value = "${rating.toInt()}",
                iconColor = LocalArgosyTheme.current.focusAccent
            )
            Spacer(modifier = Modifier.width(Dimens.spacingLg))
        }

        if (userRating > 0) {
            ShowcaseRatingItem(
                icon = Icons.Default.Star,
                value = "$userRating",
                iconColor = ALauncherColors.StarGold
            )
            if (userDifficulty > 0) {
                Spacer(modifier = Modifier.width(Dimens.spacingLg))
            }
        }

        if (userDifficulty > 0) {
            ShowcaseRatingItem(
                icon = Icons.Default.Whatshot,
                value = "$userDifficulty",
                iconColor = ALauncherColors.DifficultyRed
            )
        }
    }
}

@Composable
private fun ShowcaseRatingItem(
    icon: ImageVector,
    value: String,
    iconColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LocalArgosyTheme.current.textPrimary
        )
    }
}

/**
 * Fixed three-slot stats; geometry never varies with data presence.
 *
 * Slots share their width equally rather than taking what their text asks for. A row measures its
 * children in order and hands the last one whatever is left, so on a narrow panel the third slot
 * was collapsing to a few pixels and wrapping one character per line down the edge.
 *
 * A near-square panel drops status onto its own line instead of squeezing three across. A status
 * reads as a word rather than a number, so it is the slot that outgrows a third of the width, and
 * a full line keeps it at the same size as the two it sits under.
 */
@Composable
fun ShowcaseStatsRow(
    playTimeMinutes: Int,
    lastPlayedAt: Long,
    status: String?
) {
    val theme = LocalArgosyTheme.current
    val isWideDisplay = LocalUiScale.current.aspectRatioClass.let {
        it == AspectRatioClass.WIDE || it == AspectRatioClass.ULTRA_WIDE
    }

    val context = LocalContext.current
    val playTime: @Composable (Modifier) -> Unit = { slot ->
        ShowcaseStatCell(
            label = stringResource(R.string.dual_showcase_stat_play_time_label),
            value = formatPlayTime(context, playTimeMinutes),
            valueColor = theme.textPrimary,
            modifier = slot
        )
    }
    val lastPlayed: @Composable (Modifier) -> Unit = { slot ->
        ShowcaseStatCell(
            label = stringResource(R.string.dual_showcase_stat_last_played_label),
            value = if (lastPlayedAt > 0) {
                formatLastPlayedLabel(lastPlayedAt)
            } else {
                stringResource(R.string.dual_showcase_stat_last_played_never)
            },
            valueColor = theme.textPrimary,
            modifier = slot
        )
    }
    val completion: @Composable (Modifier) -> Unit = { slot ->
        ShowcaseStatCell(
            label = stringResource(R.string.dual_showcase_stat_status_label),
            value = status?.let { raw ->
                CompletionStatus.fromApiValue(raw)
                    ?.let { stringResource(it.labelRes) }
                    ?: raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
            } ?: stringResource(R.string.dual_showcase_stat_status_none),
            valueColor = if (status != null) theme.focusAccent else theme.textMute,
            modifier = slot
        )
    }

    if (isWideDisplay) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            playTime(Modifier.weight(1f))
            lastPlayed(Modifier.weight(1f))
            completion(Modifier.weight(1f))
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            playTime(Modifier.weight(1f))
            lastPlayed(Modifier.weight(1f))
        }
        completion(Modifier.fillMaxWidth())
    }
}

@Composable
fun ShowcaseTimeToBeatSection(
    mainSeconds: Int?,
    extraSeconds: Int?,
    completionistSeconds: Int?,
    modifier: Modifier = Modifier
) {
    val main = formatTimeToBeat(mainSeconds)
    val extra = formatTimeToBeat(extraSeconds)
    val completionist = formatTimeToBeat(completionistSeconds)
    if (main == null && extra == null && completionist == null) return

    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        main?.let {
            ShowcaseStatCell(
                label = stringResource(R.string.dual_showcase_hltb_main_story_label),
                value = it,
                valueColor = theme.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        extra?.let {
            ShowcaseStatCell(
                label = stringResource(R.string.dual_showcase_hltb_main_extras_label),
                value = it,
                valueColor = theme.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        completionist?.let {
            ShowcaseStatCell(
                label = stringResource(R.string.dual_showcase_hltb_completionist_label),
                value = it,
                valueColor = theme.textPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ShowcaseStatCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = theme.textMute,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun formatLastPlayedLabel(timestamp: Long): String {
    if (timestamp <= 0) return ""

    val now = Instant.now()
    val lastPlayed = Instant.ofEpochMilli(timestamp)
    val daysBetween = ChronoUnit.DAYS.between(lastPlayed, now)

    return when {
        daysBetween == 0L -> stringResource(R.string.dual_showcase_last_played_today)
        daysBetween == 1L -> stringResource(R.string.dual_showcase_last_played_yesterday)
        daysBetween < 7 -> pluralStringResource(
            R.plurals.dual_showcase_last_played_days,
            daysBetween.toInt(),
            daysBetween.toInt()
        )
        daysBetween < 30 -> {
            val weeks = (daysBetween / 7).toInt()
            pluralStringResource(R.plurals.dual_showcase_last_played_weeks, weeks, weeks)
        }
        else -> {
            val months = (daysBetween / 30).toInt()
            pluralStringResource(R.plurals.dual_showcase_last_played_months, months, months)
        }
    }
}
