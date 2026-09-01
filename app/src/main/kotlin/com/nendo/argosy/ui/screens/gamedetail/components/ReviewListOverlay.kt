package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.data.social.GameReview
import com.nendo.argosy.data.social.GameReviewsPage
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.util.formatPlayTime

/**
 * The full reader for a game's reviews, opened from the summary row.
 *
 * Ordering is yours, then friends, then everyone else, which is the order the server sends and
 * the order that matters: the friends block arrives on the first page only and never pages, so
 * it cannot be interleaved with the public list without losing that guarantee.
 */
@Composable
fun ReviewListOverlay(
    visible: Boolean,
    gameTitle: String,
    page: GameReviewsPage?,
    focusIndex: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ReviewListHeader(gameTitle = gameTitle, page = page)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                val entries = reviewEntries(page)
                if (entries.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.reviews_summary_none),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Column
                }

                val listState = rememberLazyListState()
                LaunchedEffect(focusIndex) {
                    if (focusIndex in entries.indices) listState.animateScrollToItem(focusIndex)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Dimens.spacingXl),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    itemsIndexed(
                        entries,
                        key = { _, entry -> "${entry.review.userId}:${entry.review.igdbId}" }
                    ) { index, entry ->
                        if (entry.heading != null) {
                            Spacer(modifier = Modifier.height(Dimens.spacingMd))
                            Text(
                                text = stringResource(entry.heading),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        ReviewCard(
                            review = entry.review,
                            author = page?.users?.get(entry.review.userId),
                            isFocused = index == focusIndex
                        )
                    }
                }
            }
        }
    }
}

private data class ReviewEntry(
    val review: GameReview,
    @androidx.annotation.StringRes val heading: Int?
)

private fun reviewEntries(page: GameReviewsPage?): List<ReviewEntry> {
    if (page == null) return emptyList()
    return buildList {
        page.myReview?.let { add(ReviewEntry(it, R.string.reviews_heading_yours)) }
        page.friends.forEachIndexed { index, review ->
            add(ReviewEntry(review, R.string.reviews_heading_friends.takeIf { index == 0 }))
        }
        page.public.forEachIndexed { index, review ->
            add(ReviewEntry(review, R.string.reviews_heading_everyone.takeIf { index == 0 }))
        }
    }
}

@Composable
private fun ReviewListHeader(gameTitle: String, page: GameReviewsPage?) {
    val sentiment = page?.sentiment?.allTime
    Column(modifier = Modifier.padding(Dimens.spacingXl)) {
        Text(
            text = gameTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = sentiment?.labelRes?.let { stringResource(it) }
                ?: stringResource(R.string.reviews_summary_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (sentiment != null && sentiment.total > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.reviews_count,
                    sentiment.total,
                    sentiment.total
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewCard(review: GameReview, author: SocialUser?, isFocused: Boolean) {
    val theme = LocalLauncherTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .border(
                width = if (isFocused) Dimens.borderThick else Dimens.borderThin,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(Dimens.radiusMd)
            )
            .padding(Dimens.spacingMd)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            Icon(
                imageVector = if (review.recommended) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = null,
                tint = if (review.recommended) {
                    theme.semanticColors.success
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(Dimens.iconSm)
            )
            Text(
                text = author?.displayName ?: review.userId,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (review.playMinutes > 0) {
                Text(
                    text = formatPlayTime(LocalContext.current, review.playMinutes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (review.hasBody) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = review.body.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (review.helpfulCount > 0 || review.unhelpfulCount > 0) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = pluralStringResource(
                    R.plurals.reviews_helpful_count,
                    review.helpfulCount,
                    review.helpfulCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
