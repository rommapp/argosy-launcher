package com.nendo.argosy.ui.common

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache of cover-image aspect ratios (width / height) keyed by the
 * absolute file path. Decoding only the image bounds is cheap, but doing it for
 * every visible cover on every recomposition/scroll would still be wasteful, so
 * resolved ratios are memoized here and shared across the whole app.
 */
private val aspectRatioCache = ConcurrentHashMap<String, Float>()

/**
 * Resolves the real aspect ratio (width / height) of a cover image so that box
 * art can be laid out at its native proportions instead of a forced shape.
 *
 * Returns [fallback] immediately (the configured box-art shape ratio) and, for
 * local files, decodes the image bounds off the main thread. Once known, the
 * ratio is cached and the caller recomposes with the real value. Remote paths
 * (not yet cached to disk) keep using [fallback] until they resolve to a file.
 */
@Composable
fun rememberCoverAspectRatio(path: String?, fallback: Float): Float {
    val cached = path?.let { aspectRatioCache[it] }
    var ratio by remember(path) { mutableStateOf(cached ?: fallback) }

    LaunchedEffect(path) {
        if (path == null) return@LaunchedEffect
        aspectRatioCache[path]?.let {
            ratio = it
            return@LaunchedEffect
        }
        if (!path.startsWith("/")) return@LaunchedEffect
        val resolved = withContext(Dispatchers.IO) { decodeAspectRatio(path) }
        if (resolved != null) {
            aspectRatioCache[path] = resolved
            ratio = resolved
        }
    }

    return ratio
}

/**
 * Fits a cover with the given [ratio] (width / height) inside the box defined by
 * [maxWidth] x [maxHeight], preserving the native ratio. The returned size never
 * exceeds the box in either dimension, so callers can keep a fixed footprint
 * (e.g. carousels with a focus zoom) while still showing covers at their real
 * proportions instead of cropping them.
 */
fun coverSizeWithin(maxWidth: Dp, maxHeight: Dp, ratio: Float): DpSize {
    if (ratio <= 0f) return DpSize(maxWidth, maxHeight)
    val boxRatio = maxWidth / maxHeight
    return if (ratio >= boxRatio) {
        DpSize(maxWidth, maxWidth / ratio)
    } else {
        DpSize(maxHeight * ratio, maxHeight)
    }
}

private fun decodeAspectRatio(path: String): Float? {
    val file = File(path)
    if (!file.exists()) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, options)
    val w = options.outWidth
    val h = options.outHeight
    if (w <= 0 || h <= 0) return null
    return w.toFloat() / h.toFloat()
}
