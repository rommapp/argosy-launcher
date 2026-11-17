package com.nendo.argosy.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.nendo.argosy.domain.model.StickerOutline
import com.nendo.argosy.domain.model.StickerOutlineColor
import com.nendo.argosy.domain.model.StickerOutlineWidth
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val STICKER_DECODE_MAX_SIDE = 1024
private const val OUTLINE_DIRECTIONS = 24
private const val OUTLINE_RINGS = 3
private const val OUTLINE_CACHE_DIR = "sticker_outlines"

/**
 * The name an outline mask is cached under. It changes with the source file's path, size or
 * modification time and with the outline width; colour is applied when drawing, so it never enters
 * the key.
 */
fun stickerOutlineCacheKey(path: String, lastModified: Long, length: Long, widthRatio: Float): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$path|$lastModified|$length|$widthRatio".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }.take(OUTLINE_KEY_LENGTH)
}

private const val OUTLINE_KEY_LENGTH = 32

val StickerOutlineWidth.ratio: Float
    get() = when (this) {
        StickerOutlineWidth.THIN -> ComponentDefaults.CustomGrid.stickerOutlineThinRatio
        StickerOutlineWidth.MEDIUM -> ComponentDefaults.CustomGrid.stickerOutlineMediumRatio
        StickerOutlineWidth.THICK -> ComponentDefaults.CustomGrid.stickerOutlineThickRatio
    }

private class OutlinedSticker(
    val sticker: ImageBitmap,
    val outline: ImageBitmap,
    val padPx: Int
)

private object StickerOutlineStore {
    private val lock = Mutex()

    suspend fun load(context: Context, path: String, widthRatio: Float): OutlinedSticker? =
        withContext(Dispatchers.IO) {
            val source = File(path)
            if (!source.isFile) return@withContext null
            val sticker = decodeBounded(path) ?: return@withContext null
            val padPx = (minOf(sticker.width, sticker.height) * widthRatio).roundToInt().coerceAtLeast(1)
            val key = stickerOutlineCacheKey(path, source.lastModified(), source.length(), widthRatio)
            val cached = File(File(context.cacheDir, OUTLINE_CACHE_DIR), "$key.png")
            val outline = lock.withLock {
                cached.takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path) }
                    ?: traceOutline(sticker, padPx).also { write(cached, it) }
            }
            OutlinedSticker(sticker.asImageBitmap(), outline.asImageBitmap(), padPx)
        }

    private fun decodeBounded(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= STICKER_DECODE_MAX_SIDE) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun traceOutline(sticker: Bitmap, padPx: Int): Bitmap {
        val mask = sticker.extractAlpha()
        val out = Bitmap.createBitmap(sticker.width + padPx * 2, sticker.height + padPx * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        for (ring in 1..OUTLINE_RINGS) {
            val radius = padPx * ring / OUTLINE_RINGS.toFloat()
            for (step in 0 until OUTLINE_DIRECTIONS) {
                val angle = 2.0 * Math.PI * step / OUTLINE_DIRECTIONS
                canvas.drawBitmap(
                    mask,
                    padPx + (radius * cos(angle)).toFloat(),
                    padPx + (radius * sin(angle)).toFloat(),
                    paint
                )
            }
        }
        mask.recycle()
        return out
    }

    private fun write(file: File, bitmap: Bitmap) {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}

/**
 * A sticker drawn fitted inside its box with no chrome. With [outline] it draws the traced outline
 * beneath the picture, traced once off the main thread and cached; until the trace is ready the
 * picture shows on its own.
 */
@Composable
fun StickerImage(
    path: String,
    outline: StickerOutline?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ratio = outline?.width?.ratio
    var traced by remember(path, ratio) { mutableStateOf<OutlinedSticker?>(null) }
    LaunchedEffect(path, ratio) {
        traced = if (ratio == null) null else StickerOutlineStore.load(context.applicationContext, path, ratio)
    }
    val ready = traced
    if (outline == null || ready == null) {
        coil.compose.AsyncImage(
            model = rememberFileImageModel(path),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
        )
        return
    }
    val tint = when (outline.color) {
        StickerOutlineColor.WHITE -> ColorTokens.Domain.StickerOutline.white
        StickerOutlineColor.BLACK -> ColorTokens.Domain.StickerOutline.black
        StickerOutlineColor.ACCENT -> LocalArgosyTheme.current.focusAccent
    }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = ready.outline.width.toFloat()
            val frameHeight = ready.outline.height.toFloat()
            val scale = minOf(size.width / frameWidth, size.height / frameHeight)
            val drawnWidth = frameWidth * scale
            val drawnHeight = frameHeight * scale
            val left = (size.width - drawnWidth) / 2f
            val top = (size.height - drawnHeight) / 2f
            drawImage(
                image = ready.outline,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(ready.outline.width, ready.outline.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
                colorFilter = ColorFilter.tint(tint)
            )
            val pad = ready.padPx * scale
            drawImage(
                image = ready.sticker,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(ready.sticker.width, ready.sticker.height),
                dstOffset = IntOffset((left + pad).roundToInt(), (top + pad).roundToInt()),
                dstSize = IntSize(
                    (ready.sticker.width * scale).roundToInt(),
                    (ready.sticker.height * scale).roundToInt()
                )
            )
        }
    }
}
