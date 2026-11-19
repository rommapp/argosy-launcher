package com.nendo.argosy.data.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapHeroPixelSource : HeroPixelSource {

    override fun aspect(file: File): Float? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return bounds.outWidth.toFloat() / bounds.outHeight
    }

    override fun pixels(file: File, longSide: Int): HeroPixels? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sourceLongSide = max(bounds.outWidth, bounds.outHeight)
        if (sourceLongSide <= 0) return null
        var sampleSize = 1
        while (sourceLongSide / (sampleSize * 2) >= longSide) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        return try {
            heroPixelsOf(bitmap, longSide)
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * [bitmap] scaled so its long side is at most [longSide], as ARGB pixels for [HeroFocusAnalyzer].
 * Leaves [bitmap] itself untouched; the caller still owns and recycles it.
 */
fun heroPixelsOf(bitmap: Bitmap, longSide: Int): HeroPixels? {
    if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.config == Bitmap.Config.HARDWARE) return null
    val scale = (longSide.toFloat() / max(bitmap.width, bitmap.height)).coerceAtMost(1f)
    val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    val scaled = if (width == bitmap.width && height == bitmap.height) {
        bitmap
    } else {
        Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
    return try {
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        HeroPixels(pixels, width, height)
    } finally {
        if (scaled !== bitmap) scaled.recycle()
    }
}
