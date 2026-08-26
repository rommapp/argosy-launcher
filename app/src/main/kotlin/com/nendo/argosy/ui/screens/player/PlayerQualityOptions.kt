package com.nendo.argosy.ui.screens.player

import java.util.Locale

/**
 * The three ceilings a playback can be capped at, together. Null on any axis means the source is
 * left alone on that axis, which is why an all-null value reads as "Original": nothing is capped
 * and the title reaches the device untouched wherever the codecs allow.
 */
data class PlayerQualityCeilings(
    val maxHeight: Int? = null,
    val maxFramerate: Int? = null,
    val maxBitrateKbps: Int? = null
)

/**
 * What the source actually is, read from the video stream the negotiation already fetched. It is
 * the hard ceiling the quality wheels are cut down to: offering 1080p for a 432p source would be a
 * promise the server cannot keep.
 */
data class PlayerSourceVideo(
    val height: Int? = null,
    val framerate: Int? = null,
    val bitrateKbps: Int? = null
)

/**
 * The three wheels of the quality picker, in the order they are walked left to right.
 */
enum class QualityWheel(val title: String) {
    RESOLUTION("Resolution"),
    FRAMERATE("Frame Rate"),
    BITRATE("Bit Rate")
}

/**
 * One row on a wheel. A null [value] is the "Original" row - no ceiling on that axis.
 */
data class QualityWheelOption(
    val label: String,
    val value: Int?
)

private const val ORIGINAL_LABEL = "Original"
private const val KBPS_PER_MBPS = 1000

private val RESOLUTION_LADDER = listOf(2160, 1440, 1080, 720, 480, 360)
private val FRAMERATE_LADDER = listOf(60, 30, 24)
private val BITRATE_LADDER_KBPS =
    listOf(20_000, 15_000, 10_000, 8_000, 6_000, 4_000, 3_000, 2_000, 1_500, 1_000, 750)

/**
 * The most bitrate worth offering for a picture of a given height, descending. A 480p stream has no
 * use for the top of a 4K ladder, which is what ties the bitrate wheel to the resolution wheel.
 */
private val MAX_USEFUL_BITRATE_BY_HEIGHT = listOf(
    2160 to 40_000,
    1440 to 20_000,
    1080 to 10_000,
    720 to 6_000,
    480 to 3_000,
    360 to 1_500
)

/**
 * What one wheel offers for this source and this draft. Only the bitrate wheel reads the draft,
 * because its ladder depends on the resolution the draft has selected; the other two depend on the
 * source alone.
 */
fun qualityWheelOptions(
    wheel: QualityWheel,
    source: PlayerSourceVideo?,
    draft: PlayerQualityCeilings
): List<QualityWheelOption> = when (wheel) {
    QualityWheel.RESOLUTION -> resolutionWheelOptions(source)
    QualityWheel.FRAMERATE -> framerateWheelOptions(source)
    QualityWheel.BITRATE -> bitrateWheelOptions(source, draft.maxHeight)
}

/**
 * The wheels worth drawing for this source, in walk order. A wheel whose ladder holds nothing
 * below the source offers only "Original", which is no choice at all, so it is left out.
 * Visibility reads the source alone, never the draft, so wheels cannot appear or vanish mid-edit:
 * a lower drafted resolution can shrink the bitrate ladder but its floor rungs survive every
 * ceiling, so a wheel visible under the uncapped draft stays non-empty under any draft.
 */
fun availableQualityWheels(source: PlayerSourceVideo?): List<QualityWheel> =
    QualityWheel.entries.filter {
        qualityWheelOptions(it, source, PlayerQualityCeilings()).size > 1
    }

fun resolutionWheelOptions(source: PlayerSourceVideo?): List<QualityWheelOption> =
    listOf(QualityWheelOption(ORIGINAL_LABEL, null)) +
        RESOLUTION_LADDER
            .filter { source?.height == null || it < source.height }
            .map { QualityWheelOption("${it}p", it) }

fun framerateWheelOptions(source: PlayerSourceVideo?): List<QualityWheelOption> =
    listOf(QualityWheelOption(ORIGINAL_LABEL, null)) +
        FRAMERATE_LADDER
            .filter { source?.framerate == null || it < source.framerate }
            .map { QualityWheelOption("$it fps", it) }

fun bitrateWheelOptions(source: PlayerSourceVideo?, selectedMaxHeight: Int?): List<QualityWheelOption> {
    val effectiveHeight = selectedMaxHeight ?: source?.height
    val usefulCeiling = effectiveHeight?.let { height ->
        MAX_USEFUL_BITRATE_BY_HEIGHT.firstOrNull { height >= it.first }?.second
    }
    return listOf(QualityWheelOption(ORIGINAL_LABEL, null)) +
        BITRATE_LADDER_KBPS
            .filter { usefulCeiling == null || it <= usefulCeiling }
            .filter { source?.bitrateKbps == null || it < source.bitrateKbps }
            .map { QualityWheelOption(bitrateLabel(it), it) }
}

fun bitrateLabel(kbps: Int): String = when {
    kbps >= KBPS_PER_MBPS && kbps % KBPS_PER_MBPS == 0 -> "${kbps / KBPS_PER_MBPS} Mbps"
    kbps >= KBPS_PER_MBPS ->
        String.format(Locale.US, "%.1f Mbps", kbps / KBPS_PER_MBPS.toFloat())
    else -> "$kbps Kbps"
}

fun List<QualityWheelOption>.indexOfValue(value: Int?): Int =
    indexOfFirst { it.value == value }.coerceAtLeast(0)

fun PlayerQualityCeilings.valueFor(wheel: QualityWheel): Int? = when (wheel) {
    QualityWheel.RESOLUTION -> maxHeight
    QualityWheel.FRAMERATE -> maxFramerate
    QualityWheel.BITRATE -> maxBitrateKbps
}

/**
 * Re-fits the bitrate ceiling after the resolution changed under it. A bitrate the new ladder no
 * longer offers falls to the nearest one below it, and to "Original" only when the ladder has
 * nothing capped left to offer.
 */
fun clampBitrateToLadder(
    draft: PlayerQualityCeilings,
    source: PlayerSourceVideo?
): PlayerQualityCeilings {
    val target = draft.maxBitrateKbps ?: return draft
    val values = bitrateWheelOptions(source, draft.maxHeight).mapNotNull { it.value }
    if (target in values) return draft
    val nearest = values.filter { it <= target }.maxOrNull() ?: values.firstOrNull()
    return draft.copy(maxBitrateKbps = nearest)
}

/**
 * How the ceilings read on the chrome's caption line: each capped axis by its value, or "Original"
 * when nothing is capped.
 */
fun PlayerQualityCeilings.summaryLabel(): String {
    val parts = listOfNotNull(
        maxHeight?.let { "${it}p" },
        maxFramerate?.let { "$it fps" },
        maxBitrateKbps?.let { bitrateLabel(it) }
    )
    return if (parts.isEmpty()) ORIGINAL_LABEL else parts.joinToString(" ")
}
