package com.nendo.argosy.data.media

/**
 * Whether a downloaded copy can be opened right now.
 *
 * A stored path is not a file. The three downloaded states are kept apart because two of them look
 * identical from the database and mean opposite things: [UNAVAILABLE] is a copy on storage that is
 * not connected and stays downloaded, while [ABSENT] is a copy the user deleted from outside the app
 * and is the only one whose record may be forgotten.
 */
enum class MediaAvailability {
    NOT_DOWNLOADED,
    PRESENT,
    UNAVAILABLE,
    ABSENT;

    val hasLocalCopy: Boolean get() = this == PRESENT || this == UNAVAILABLE

    val playsFromDisk: Boolean get() = this == PRESENT
}

/**
 * The state one row is drawn with, given what verification has established so far.
 *
 * A row with a path that has not been verified yet reads as [MediaAvailability.PRESENT] rather than
 * as unknown: the record says a copy was stored, and a screen that opened before the first pass
 * finished should show what the record says instead of blanking every indicator for a moment.
 */
fun mediaAvailabilityOf(
    localPath: String?,
    verified: MediaAvailability?
): MediaAvailability = when {
    localPath == null -> MediaAvailability.NOT_DOWNLOADED
    verified == null || verified == MediaAvailability.NOT_DOWNLOADED -> MediaAvailability.PRESENT
    else -> verified
}
