package com.nendo.argosy.libretro.touch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object TouchBackdropCache {

    private const val SUBDIR = "touch_layout_backdrop"
    private const val MAX_DIMENSION = 720

    fun fileFor(context: Context, platformSlug: String): File {
        val dir = File(context.cacheDir, SUBDIR).apply { if (!exists()) mkdirs() }
        val safe = platformSlug.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safe.png")
    }

    fun save(context: Context, platformSlug: String, bitmap: Bitmap?) {
        val src = bitmap ?: return
        try {
            val scaled = scale(src)
            val out = fileFor(context, platformSlug)
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.PNG, 90, it) }
            if (scaled !== src) scaled.recycle()
        } catch (_: Exception) {
        }
    }

    fun load(context: Context, platformSlug: String): Bitmap? {
        val f = fileFor(context, platformSlug)
        if (!f.exists()) return null
        return try { BitmapFactory.decodeFile(f.absolutePath) } catch (_: Exception) { null }
    }

    private fun scale(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val maxSide = maxOf(w, h)
        if (maxSide <= MAX_DIMENSION) return src
        val ratio = MAX_DIMENSION.toFloat() / maxSide
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }
}
