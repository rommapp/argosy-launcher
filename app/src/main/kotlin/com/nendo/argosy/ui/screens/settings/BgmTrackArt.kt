package com.nendo.argosy.ui.screens.settings

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface BgmTrackArt {
    data object Loading : BgmTrackArt
    data object None : BgmTrackArt
    data class Embedded(val bitmap: ImageBitmap) : BgmTrackArt
}

private const val ART_CACHE_SIZE = 64
private const val ART_TARGET_EDGE_PX = 192
private val noArt = Any()
private val artCache = LruCache<String, Any>(ART_CACHE_SIZE)

@Composable
internal fun BgmTrackThumbnail(
    filePath: String,
    coverPath: String?,
    contentAlpha: Float,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val art = rememberBgmTrackArt(filePath)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = contentAlpha))
    ) {
        when (art) {
            is BgmTrackArt.Embedded -> Image(
                bitmap = art.bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = contentAlpha,
                modifier = Modifier.fillMaxSize()
            )
            BgmTrackArt.None -> if (coverPath != null) {
                AsyncImage(
                    model = rememberFileImageModel(coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = contentAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }
            BgmTrackArt.Loading -> Unit
        }
    }
}

@Composable
private fun rememberBgmTrackArt(filePath: String): BgmTrackArt {
    var art by remember(filePath) { mutableStateOf(cachedArt(filePath) ?: BgmTrackArt.Loading) }
    LaunchedEffect(filePath) {
        if (art == BgmTrackArt.Loading) {
            art = withContext(Dispatchers.IO) { resolveArt(filePath) }
        }
    }
    return art
}

private fun cachedArt(filePath: String): BgmTrackArt? = when (val hit = artCache.get(filePath)) {
    null -> null
    noArt -> BgmTrackArt.None
    else -> BgmTrackArt.Embedded(hit as ImageBitmap)
}

private fun resolveArt(filePath: String): BgmTrackArt {
    cachedArt(filePath)?.let { return it }
    val bitmap = extractEmbeddedArt(filePath)
    artCache.put(filePath, bitmap ?: noArt)
    return if (bitmap != null) BgmTrackArt.Embedded(bitmap) else BgmTrackArt.None
}

private fun extractEmbeddedArt(filePath: String): ImageBitmap? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(filePath)
        retriever.embeddedPicture?.let(::decodeScaled)
    } finally {
        retriever.release()
    }
}.getOrNull()

private fun decodeScaled(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= ART_TARGET_EDGE_PX &&
        bounds.outHeight / (sampleSize * 2) >= ART_TARGET_EDGE_PX
    ) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}
