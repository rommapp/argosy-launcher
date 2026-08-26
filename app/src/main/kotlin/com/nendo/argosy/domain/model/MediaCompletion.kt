package com.nendo.argosy.domain.model

/**
 * The fraction of a runtime past which an item counts as watched even without a played flag.
 *
 * Credits routinely run minutes, so a viewing that stops inside them leaves a position with no
 * completion event behind - a process death, an install, or the viewer backing out during the
 * credits all end this way. A position past this threshold means the story was finished, not that
 * the item is part watched. Ninety percent is the Jellyfin server's own resume cutoff.
 */
const val MEDIA_COMPLETION_PERCENT = 90.0

/**
 * Whether a stored position says the item is finished. Measured against the runtime when one is
 * known and against the recorded played percentage when it is not. With neither known it answers
 * false, because wiping a real mid-file position costs more than replaying a rare stale one.
 */
fun isPastMediaCompletion(positionMs: Long, runtimeMs: Long, playedPercentage: Double?): Boolean {
    if (runtimeMs > 0) return positionMs >= runtimeMs * (MEDIA_COMPLETION_PERCENT / 100.0)
    val percent = playedPercentage ?: return false
    return percent >= MEDIA_COMPLETION_PERCENT
}
