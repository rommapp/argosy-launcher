package com.nendo.argosy.data.remote.jellyfin

import com.nendo.argosy.data.local.entity.MediaSourceEntity
import com.nendo.argosy.data.local.entity.MediaStreamEntity

private const val STREAM_TYPE_VIDEO = "Video"
private const val BITS_PER_KILOBIT = 1000

/**
 * The video track the server probed, which is where a source's picture size is described.
 */
private val JellyfinMediaSource.videoStream: JellyfinMediaStream?
    get() = mediaStreams.firstOrNull { it.type == STREAM_TYPE_VIDEO }

/**
 * How tall the source picture is. A source carrying no video track, or one the server never
 * measured, reports nothing rather than a zero, so a tier comparison treats it as unknown instead of
 * as tiny.
 */
val JellyfinMediaSource.videoHeight: Int?
    get() = videoStream?.height?.takeIf { it > 0 }

/**
 * The whole-file bitrate, which is what a tier ceiling is measured against. The video track's own
 * rate stands in when the server reported no total.
 */
val JellyfinMediaSource.bitrateKbps: Int?
    get() = (bitrate ?: videoStream?.bitRate)?.takeIf { it > 0 }?.div(BITS_PER_KILOBIT)

/**
 * The single resolved container, never the ffprobe-style comma list a list item can carry.
 */
val JellyfinMediaSource.resolvedContainer: String?
    get() = container?.takeIf { it.isNotBlank() }?.substringBefore(',')

fun JellyfinMediaSource.toSourceEntity(ownerUserId: String, itemId: String): MediaSourceEntity =
    MediaSourceEntity(
        ownerUserId = ownerUserId,
        itemId = itemId,
        mediaSourceId = id,
        container = resolvedContainer,
        sizeBytes = size?.takeIf { it > 0 },
        bitrateKbps = bitrateKbps,
        videoHeight = videoHeight
    )

fun JellyfinMediaSource.toStreamEntities(ownerUserId: String, itemId: String): List<MediaStreamEntity> =
    mediaStreams.mapNotNull { stream ->
        val type = stream.type ?: return@mapNotNull null
        MediaStreamEntity(
            ownerUserId = ownerUserId,
            itemId = itemId,
            mediaSourceId = id,
            streamIndex = stream.index,
            streamType = type,
            codec = stream.codec,
            language = stream.language,
            displayTitle = stream.displayTitle ?: stream.title,
            channels = stream.channels,
            bitRate = stream.bitRate,
            width = stream.width,
            height = stream.height,
            isDefault = stream.isDefault,
            isForced = stream.isForced,
            isExternal = stream.isExternal
        )
    }
