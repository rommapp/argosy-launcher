package com.nendo.argosy.data.social

/**
 * Aggregate sentiment over one window.
 *
 * [percent] is null until the window holds enough reviews for a share to mean anything, which
 * the server sets at five. The counts are still populated below that, so a caller shows them
 * and withholds the percentage rather than rendering a misleading 100%.
 */
data class ReviewSentiment(
    val total: Int,
    val positive: Int,
    val percent: Int?
) {
    val hasPercent: Boolean get() = percent != null

    companion object {
        val EMPTY = ReviewSentiment(total = 0, positive = 0, percent = null)
    }
}

data class GameSentiment(
    val allTime: ReviewSentiment,
    val recent: ReviewSentiment
) {
    companion object {
        val EMPTY = GameSentiment(ReviewSentiment.EMPTY, ReviewSentiment.EMPTY)
    }
}

/**
 * One review. A review has no id of its own: [userId] and [igdbId] together name it, and that
 * pair is what the stance and report writes take.
 *
 * [body] is absent both when the author wrote none and when this viewer may not read it, so a
 * verdict with no prose is an ordinary state rather than a failure.
 */
data class GameReview(
    val userId: String,
    val igdbId: Int,
    val recommended: Boolean,
    val body: String?,
    val visibility: String,
    val playMinutes: Int,
    val createdAt: String,
    val updatedAt: String,
    val isFriend: Boolean,
    val helpfulCount: Int,
    val unhelpfulCount: Int,
    val myStance: String?
) {
    val hasBody: Boolean get() = !body.isNullOrBlank()

    companion object {
        const val STANCE_HELPFUL = "helpful"
        const val STANCE_UNHELPFUL = "unhelpful"
        const val VISIBILITY_FRIENDS = "friends"
        const val VISIBILITY_PUBLIC = "public"
    }
}

/**
 * A page of a game's reviews. [friends] rides along on the first page only, capped and unpaged;
 * [public] is what the cursor advances. The two never overlap and neither holds [myReview].
 */
data class GameReviewsPage(
    val igdbId: Int,
    val friends: List<GameReview>,
    val public: List<GameReview>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val users: Map<String, SocialUser>,
    val sentiment: GameSentiment,
    val myReview: GameReview?
)

/**
 * The per-game aggregate without a page of reviews attached, which is what a summary strip
 * needs. The numbers match [GameReviewsPage] for the same game.
 */
data class ReviewSummary(
    val igdbId: Int,
    val sentiment: GameSentiment,
    val myReview: GameReview?,
    val users: Map<String, SocialUser>
)

/**
 * Outcome of a review write. The repository has already folded a [Saved] or [Deleted] result
 * into the cached summary and page and shown the notification; an editor only needs to close or,
 * on [Failed], let the user try again.
 */
sealed interface ReviewWriteEvent {
    val igdbId: Int

    data class Saved(val review: GameReview) : ReviewWriteEvent {
        override val igdbId: Int get() = review.igdbId
    }

    data class Deleted(override val igdbId: Int) : ReviewWriteEvent

    data class Failed(override val igdbId: Int) : ReviewWriteEvent
}
