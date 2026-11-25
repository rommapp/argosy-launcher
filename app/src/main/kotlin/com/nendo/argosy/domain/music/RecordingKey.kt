package com.nendo.argosy.domain.music

import com.nendo.argosy.domain.model.MusicTrackIdentity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Cross-source recording key for top-list dedupe. Precedence: MusicBrainz id, then ISRC
 * (uppercase, letters and digits only), then content hash, then primary credit + title +
 * duration in 2 s buckets. The last form needs an artist credit and a known duration; null
 * when none applies.
 */
object RecordingKey {
    private const val DURATION_BUCKET_SEC = 2.0
    private val ISRC_NOISE = Regex("[^A-Z0-9]")

    fun of(identity: MusicTrackIdentity): String? = of(
        musicBrainzId = identity.musicBrainzId,
        isrc = identity.isrc,
        contentHash = identity.contentHash,
        artistCredit = identity.artistCredit,
        title = identity.title,
        durationSec = identity.durationSec
    )

    fun of(
        musicBrainzId: String?,
        isrc: String?,
        contentHash: String?,
        artistCredit: String?,
        title: String?,
        durationSec: Int?
    ): String? {
        musicBrainzId?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { return "mbid:$it" }
        isrc?.uppercase(Locale.ROOT)?.replace(ISRC_NOISE, "")?.takeIf { it.isNotEmpty() }?.let { return "isrc:$it" }
        contentHash?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }?.let { return "hash:$it" }
        val artist = CreditSplitter.split(artistCredit).firstOrNull()?.let(MusicNames::words)
            ?.takeIf { it.isNotEmpty() } ?: return null
        val name = title?.let(MusicNames::words)?.takeIf { it.isNotEmpty() } ?: return null
        val seconds = durationSec?.takeIf { it > 0 } ?: return null
        return "meta:$artist|$name|${(seconds / DURATION_BUCKET_SEC).roundToInt()}"
    }
}
