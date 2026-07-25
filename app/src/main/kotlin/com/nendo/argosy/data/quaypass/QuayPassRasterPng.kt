package com.nendo.argosy.data.quaypass

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.nendo.argosy.data.quaypass.ble.QuayPassDoodleCodec
import java.io.ByteArrayOutputStream

/**
 * Renders the frozen 4bpp doodle raster captured over BLE into a PNG for the
 * server ledger (peer_card.avatar_raster). The BLE wire keeps the compact nibble
 * raster; only the encounter report carries a PNG, the format the server sniffs
 * and dimension-caps. Palette index 0 is blank and maps to transparent, matching
 * how the check-in card renders the same raster.
 */
object QuayPassRasterPng {

    fun fromRasterBase64(rasterBase64: String?): String? {
        if (rasterBase64.isNullOrEmpty()) return null
        return runCatching {
            val raster = QuayPassDoodleCodec.decode(Base64.decode(rasterBase64, Base64.NO_WRAP))
                ?: return null
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
