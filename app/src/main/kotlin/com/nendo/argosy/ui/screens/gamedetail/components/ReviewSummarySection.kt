package com.nendo.argosy.ui.screens.gamedetail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.social.ReviewSummary
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The one-row summary that stands in for a rail of review cards.
 *
 * A rail would be empty on most games in a library this size, and a vertical list of prose
 * inside a scrolling page nests two scrolls. A fixed row reads correctly at zero reviews and at
 * five hundred, and the reading happens in the overlay it opens.
 */
@Composable
fun ReviewSummarySection(
    summary: ReviewSummary?,
    isActive: Boolean,
    onOpen: () -> Unit,
    onWriteReview: () -> Unit,
    onPositioned: (Int) -> Unit,
    onSectionFocus: () -> Unit
) {
    val theme = LocalLauncherTheme.current
    val sentiment = summary?.sentiment?.allTime

    Column(
        modifier = Modifier.onGloballyPositioned { coords ->
            onPositioned(coords.positionInParent().y.toInt())
        }
    ) {
        Text(
            text = stringResource(R.string.gamedetail_section_reviews_heading),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = Dimens.spacingSm))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    RoundedCornerShape(Dimens.radiusMd)
                )
                .border(
                    width = if (isActive) Dimens.borderThick else Dimens.borderThin,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = RoundedCornerShape(Dimens.radiusMd)
                )
                .clickableNoFocus { if (isActive) onOpen() else onSectionFocus() }
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = sentiment?.labelRes?.let { stringResource(it) }
                        ?: stringResource(R.string.reviews_summary_none),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier
                    .clickableNoFocus(onClick = onWriteReview)
                    .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
            ) {
                val myReview = summary?.myReview
                Icon(
                    imageVector = if (myReview?.recommended == false) {
                        Icons.Default.ThumbDown
                    } else {
                        Icons.Default.ThumbUp
                    },
                    contentDescription = null,
                    tint = when {
                        myReview == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        myReview.recommended -> theme.semanticColors.success
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(Dimens.iconXs)
                )
                Text(
                    text = stringResource(
                        if (myReview == null) {
                            R.string.reviews_my_review_none
                        } else {
                            R.string.gamedetail_chip_my_review_label
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
