package com.nendo.argosy.ui.dualscreen.gamedetail

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.nendo.argosy.R
import com.nendo.argosy.data.social.GameReview
import com.nendo.argosy.data.social.GameReviewsPage
import com.nendo.argosy.data.social.SocialUser
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The reviews reader on the companion panel.
 *
 * The single-screen surface opens an overlay for this because a rail card cannot hold prose.
 * The lower panel is already a full list, so the same content renders in place with no overlay
 * and no modal. The first row is the reader's own review, or the row that opens the editor
 * until one exists; tapping either sends the editor to the other screen.
 */
@Composable
internal fun ReviewsTabContent(
    page: GameReviewsPage?,
    focusIndex: Int,
    showWriteRow: Boolean,
    onEntryTapped: (Int) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val entries = remembered(page, showWriteRow)

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.dual_detail_reviews_empty),
                color = theme.textMute
            )
        }
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex) {
        if (focusIndex in entries.indices) listState.animateScrollToItem(focusIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            if (entry.heading != null) {
                Spacer(modifier = Modifier.height(Dimens.spacingXs))
                Text(
                    text = stringResource(entry.heading),
                    color = theme.focusAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            val review = entry.review
            if (review == null) {
                DualWriteReviewRow(
                    isFocused = index == focusIndex,
                    onClick = { onEntryTapped(index) }
                )
            } else {
                DualReviewCard(
                    review = review,
                    author = page?.users?.get(review.userId),
                    isFocused = index == focusIndex,
                    onClick = { onEntryTapped(index) }
                )
            }
        }
    }
}

private data class DualReviewEntry(
    val review: GameReview?,
    @androidx.annotation.StringRes val heading: Int?
) {
    val key: String get() = review?.let { "${it.userId}:${it.igdbId}" } ?: WRITE_ROW_KEY
}

private const val WRITE_ROW_KEY = "write"

private fun remembered(page: GameReviewsPage?, showWriteRow: Boolean): List<DualReviewEntry> = buildList {
    if (showWriteRow) add(DualReviewEntry(null, R.string.reviews_heading_yours))
    if (page == null) return@buildList
    page.myReview?.let { add(DualReviewEntry(it, R.string.reviews_heading_yours)) }
    page.friends.forEachIndexed { index, review ->
        add(DualReviewEntry(review, R.string.reviews_heading_friends.takeIf { index == 0 }))
    }
    page.public.forEachIndexed { index, review ->
        add(DualReviewEntry(review, R.string.reviews_heading_everyone.takeIf { index == 0 }))
    }
}

@Composable
private fun DualWriteReviewRow(isFocused: Boolean, onClick: () -> Unit) {
    val theme = LocalArgosyTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceRaised, RoundedCornerShape(Dimens.radiusSm))
            .border(
                width = if (isFocused) Dimens.borderThick else Dimens.borderThin,
                color = if (isFocused) theme.focusAccent else theme.hairlineHigh,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Default.ThumbUp,
            contentDescription = null,
            tint = theme.textMute,
            modifier = Modifier.size(Dimens.iconXs)
        )
        Text(
            text = stringResource(R.string.dual_detail_reviews_write_row),
            color = theme.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DualReviewCard(
    review: GameReview,
    author: SocialUser?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surfaceRaised, RoundedCornerShape(Dimens.radiusSm))
            .border(
                width = if (isFocused) Dimens.borderThick else Dimens.borderThin,
                color = if (isFocused) theme.focusAccent else theme.hairlineHigh,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
        ) {
            Icon(
                imageVector = if (review.recommended) Icons.Default.ThumbUp else Icons.Default.ThumbDown,
                contentDescription = null,
                tint = if (review.recommended) {
                    LocalLauncherTheme.current.semanticColors.success
                } else {
                    theme.destructive
                },
                modifier = Modifier.size(Dimens.iconXs)
            )
            Text(
                text = author?.displayName ?: review.userId,
                color = theme.textPrimary,
                fontWeight = FontWeight.Bold
            )
        }
        if (review.hasBody) {
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(text = review.body.orEmpty(), color = theme.textDim)
        }
    }
}
