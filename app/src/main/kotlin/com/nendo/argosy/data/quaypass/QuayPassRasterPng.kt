package com.nendo.argosy.data.quaypass

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import java.io.ByteArrayOutputStream

/**
 * Renders the frozen 4bpp doodle raster captured over BLE into a PNG. The PNG is
 * the canonical stored form of an encounter avatar (entity + server ledger +
 * future synced cards all use it); the compact nibble raster is only the BLE
 * transmission format and is converted away on receipt. Palette index 0 is blank
 * and maps to transparent.
 */
object QuayPassRasterPng {

    fun fromSparseBase64(sparseBase64: String?): String? {
        if (sparseBase64.isNullOrEmpty()) return null
        return fromRasterBytes(QuayPassDoodleCodec.encodeFromSparseBase64(sparseBase64))
    }

    fun fromRasterBytes(rasterBytes: ByteArray?): String? {
        if (rasterBytes == null || rasterBytes.isEmpty()) return null
        return runCatching {
            val raster = QuayPassDoodleCodec.decode(rasterBytes) ?: return null
            val size = raster.size
            val argb = IntArray(size * size) { i ->
                val index = raster.paletteIndices[i]
                if (index == 0) Color.TRANSPARENT
                else 0xFF000000.toInt() or (QuayPassDoodleCodec.PALETTE_RGB[index] and 0xFFFFFF)
            }
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(argb, 0, size, 0, 0, size, size)
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }
}
