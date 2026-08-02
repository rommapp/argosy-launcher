/**
 * DUAL-SCREEN COMPONENT - Upper display collection showcase.
 * Runs in main process (MainActivity).
 * Shows collection metadata when lower screen is in COLLECTIONS mode.
 */
package com.nendo.argosy.ui.dualscreen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import java.io.File

data class DualCollectionShowcaseState(
    val name: String = "",
    val description: String? = null,
    val coverPaths: List<String> = emptyList(),
    val gameCount: Int = 0,
    val platformSummary: String = "",
    val totalPlaytimeMinutes: Int = 0,
    val installedCount: Int = 0,
    val achievementsEarned: Int = 0,
    val achievementsTotal: Int = 0
)

@Composable
fun DualCollectionShowcase(
    state: DualCollectionShowcaseState,
    footerHints: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(theme.surfaceBase)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.surfaceBase.copy(alpha = 0.7f))
                    .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm + Dimens.spacingXs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = state.name.ifEmpty { "Collections" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = theme.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (state.gameCount > 0) {
                    Text(
                        text = "${state.gameCount} games",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.focusAccent
                    )
                }
            }

            HorizontalDivider(
                color = theme.hairlineLow
            )

            Spacer(modifier = Modifier.weight(1f))

            if (state.coverPaths.isNotEmpty()) {
                CollectionCoverGrid(
                    coverPaths = state.coverPaths,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingXxl)
                )
            }

            CollectionStatStrip(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.spacingXxl, vertical = Dimens.spacingSm)
            )

            Spacer(modifier = Modifier.weight(1f))

            if (state.platformSummary.isNotBlank() || state.totalPlaytimeMinutes > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingSm),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Column(
                        modifier = Modifier
                            .background(theme.surfaceRaised.copy(alpha = 0.9f))
                            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm + Dimens.spacingXs),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (state.platformSummary.isNotBlank()) {
                            Text(
                                text = state.platformSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = theme.textDim
                            )
                        }
                        if (state.totalPlaytimeMinutes > 0) {
                            Text(
                                text = formatCollectionPlayTime(state.totalPlaytimeMinutes),
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.textPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = theme.hairlineLow
            )

            footerHints()
        }
    }
}

@Composable
private fun ShowcaseCoverCollage(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(theme.surfaceRaised)
    ) {
        when {
            coverPaths.size == 1 -> {
                AsyncImage(
                    model = File(coverPaths[0]),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                val displayed = coverPaths.take(4)
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        displayed.getOrNull(0)?.let { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                        displayed.getOrNull(1)?.let { path ->
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                            )
                        }
                    }
                    if (displayed.size > 2) {
                        Row(modifier = Modifier.weight(1f)) {
                            displayed.getOrNull(2)?.let { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                            displayed.getOrNull(3)?.let { path ->
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            } ?: Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun formatCollectionPlayTime(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes}m total"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m total"
        else -> "${minutes / 60}h total"
    }
}

/**
 * The collection as a wall of what is in it. A single stacked collage says "a collection"; a grid
 * says which games, which is the thing worth looking at from across a desk.
 */
@Composable
private fun CollectionCoverGrid(
    coverPaths: List<String>,
    modifier: Modifier = Modifier
) {
    val aspect = LocalBoxArtStyle.current.aspectRatio
    val shown = coverPaths.take(COLLECTION_GRID_COVERS)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.CenterHorizontally)
    ) {
        shown.forEach { path ->
            AsyncImage(
                model = rememberFileImageModel(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )
        }
        repeat(COLLECTION_GRID_COVERS - shown.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/**
 * What a collection amounts to: how much of it is on the device, how far through it you are, and
 * how long it has taken. Each figure is omitted when it has nothing to report rather than shown as
 * a zero.
 */
@Composable
private fun CollectionStatStrip(
    state: DualCollectionShowcaseState,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXl, Alignment.CenterHorizontally)
    ) {
        CollectionStat(
            label = "Installed",
            value = "${state.installedCount}/${state.gameCount}"
        )
        if (state.achievementsTotal > 0) {
            CollectionStat(
                label = "Achievements",
                value = "${state.achievementsEarned}/${state.achievementsTotal}"
            )
        }
        if (state.totalPlaytimeMinutes > 0) {
            CollectionStat(
                label = "Playtime",
                value = formatCollectionPlayTime(state.totalPlaytimeMinutes)
            )
        }
    }
    if (state.gameCount == 0) {
        Text(
            text = "Nothing in this collection yet",
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim
        )
    }
}

@Composable
private fun CollectionStat(label: String, value: String) {
    val theme = LocalArgosyTheme.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim
        )
    }
}

private const val COLLECTION_GRID_COVERS = 6
