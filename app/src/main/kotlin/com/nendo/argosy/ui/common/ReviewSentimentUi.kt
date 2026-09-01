package com.nendo.argosy.ui.common

import com.nendo.argosy.R
import com.nendo.argosy.data.social.ReviewSentiment

/**
 * The descriptor for a sentiment percentage.
 *
 * The server sends its own English label alongside the percentage, and this deliberately
 * ignores it. A label rendered on screen is a translated string, and taking the server's would
 * leave English in every other locale. The thresholds mirror the server's so the two agree;
 * if they ever diverge, this file is the one to correct.
 */
/**
 * Whether review sentiment stands in for the catalog's community rating.
 *
 * Both surfaces ask this, and they must agree: a game with three reviews would otherwise hide a
 * four-hundred-vote catalog rating behind a percentage the server refuses to compute.
 */
fun ReviewSentiment?.supersedesCommunityRating(): Boolean = this?.hasPercent == true

@get:androidx.annotation.StringRes
val ReviewSentiment.labelRes: Int?
    get() {
        val value = percent ?: return null
        return when {
            value >= 95 -> R.string.reviews_sentiment_overwhelmingly_positive
            value >= 80 -> R.string.reviews_sentiment_very_positive
            value >= 70 -> R.string.reviews_sentiment_mostly_positive
            value >= 40 -> R.string.reviews_sentiment_mixed
            value >= 20 -> R.string.reviews_sentiment_mostly_negative
            else -> R.string.reviews_sentiment_overwhelmingly_negative
        }
    }
