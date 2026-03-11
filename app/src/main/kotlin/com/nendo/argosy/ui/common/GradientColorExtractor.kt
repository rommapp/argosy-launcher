package com.nendo.argosy.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.nendo.argosy.data.cache.GradientExtractionConfig
import com.nendo.argosy.data.cache.GradientPreset
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import android.graphics.Color as AndroidColor

data class GradientExtractionResult(
    val primary: Color,
    val secondary: Color,
    val extractionTimeMs: Long,
    val sampleCount: Int,
    val colorFamiliesUsed: Int,
    val effectiveSaturationThreshold: Float
)

@Singleton
class GradientColorExtractor @Inject constructor() {

    fun extractAllPresets(coverPath: String): Map<GradientPreset, Pair<Color, Color>>? {
        val bitmap = BitmapFactory.decodeFile(coverPath) ?: return null
        return try {
            extractAllPresetsFromBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun extractAllPresetsFromBitmap(bitmap: Bitmap): Map<GradientPreset, Pair<Color, Color>> {
        val baseConfig = GradientExtractionConfig()
        val samples = sampleBitmapColors(bitmap, baseConfig)
        return STANDARD_PRESETS.associateWith { preset ->
            val config = preset.toConfig()
            val threshold = calculateEffectiveThreshold(samples, config)
            val families = groupIntoFamilies(samples, threshold)
            val primary = selectPrimaryColor(families, config)
            val secondary = selectSecondaryColor(families, primary, config)
            Pair(primary, secondary)
        }
    }

    fun extractForCustomConfig(coverPath: String, config: GradientExtractionConfig): Pair<Color, Color>? {
        val bitmap = BitmapFactory.decodeFile(coverPath) ?: return null
        return try {
            val result = extractWithMetrics(bitmap, config)
            Pair(result.primary, result.secondary)
        } finally {
            bitmap.recycle()
        }
    }

    fun extractGradientColors(bitmap: Bitmap): Pair<Color, Color> {
        val result = extractWithMetrics(bitmap, GradientExtractionConfig())
        return Pair(result.primary, result.secondary)
    }

    fun extractWithMetrics(
        bitmap: Bitmap,
        config: GradientExtractionConfig = GradientExtractionConfig()
    ): GradientExtractionResult {
        val startTime = System.nanoTime()

        val sampleData = sampleBitmapColors(bitmap, config)
        val effectiveThreshold = calculateEffectiveThreshold(sampleData, config)
        val familyData = groupIntoFamilies(sampleData, effectiveThreshold)
        val primaryColor = selectPrimaryColor(familyData, config)
        val secondaryColor = selectSecondaryColor(familyData, primaryColor, config)

        return GradientExtractionResult(
            primary = primaryColor,
            secondary = secondaryColor,
            extractionTimeMs = (System.nanoTime() - startTime) / 1_000_000,
            sampleCount = config.samplesX * config.samplesY,
            colorFamiliesUsed = familyData.familiesUsed,
            effectiveSaturationThreshold = effectiveThreshold
        )
    }

    private fun sampleBitmapColors(
        bitmap: Bitmap,
        config: GradientExtractionConfig
    ): List<SamplePoint> {
        val hsv = FloatArray(3)
        val colorResolution = 360 / COLOR_FAMILIES
        val sampleData = mutableListOf<SamplePoint>()

        for (iy in 1..config.samplesY) {
            for (ix in 1..config.samplesX) {
                val centerX = bitmap.width * ix / (config.samplesX + 1)
                val centerY = bitmap.height * iy / (config.samplesY + 1)
                val color = averagePixelRegion(bitmap, centerX, centerY, config.radius)

                AndroidColor.colorToHSV(color, hsv)
                if (hsv[2] >= config.minValue) {
                    sampleData.add(SamplePoint(
                        color = color,
                        hue = hsv[0],
                        saturation = hsv[1],
                        family = (hsv[0].toInt() % 360) / colorResolution
                    ))
                }
            }
        }
        return sampleData
    }

    private fun averagePixelRegion(bitmap: Bitmap, centerX: Int, centerY: Int, radius: Int): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = (centerX + dx).coerceIn(0, bitmap.width - 1)
                val y = (centerY + dy).coerceIn(0, bitmap.height - 1)
                val pixel = bitmap.getPixel(x, y)
                red += AndroidColor.red(pixel)
                green += AndroidColor.green(pixel)
                blue += AndroidColor.blue(pixel)
                count++
            }
        }
        return if (count > 0) {
            AndroidColor.rgb((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
        } else {
            AndroidColor.BLACK
        }
    }

    private fun calculateEffectiveThreshold(
        samples: List<SamplePoint>,
        config: GradientExtractionConfig
    ): Float {
        if (!config.safeLimits || samples.isEmpty()) return config.minSaturation

        val minThreshold = 0.15f
        val targetQualifyRate = 0.15f
        var threshold = config.minSaturation

        while (threshold > minThreshold) {
            val qualifyRate = samples.count { it.saturation >= threshold }.toFloat() / samples.size
            if (qualifyRate >= targetQualifyRate) break
            threshold -= 0.05f
        }
        return threshold.coerceAtLeast(minThreshold)
    }

    private data class FamilyData(
        val sortedIndices: List<Int>,
        val colors: Array<MutableList<Long>>,
        val chosenCounts: IntArray,
        val hasSaturated: Boolean,
        val familiesUsed: Int
    ) {
        fun unpackColors(idx: Int): List<Int> {
            val bucket = colors[idx]
            return if (hasSaturated) {
                bucket.filter { (it shr 32) == 1L }.map { it.toInt() }
            } else {
                bucket.map { it.toInt() }
            }
        }
    }

    private fun groupIntoFamilies(samples: List<SamplePoint>, threshold: Float): FamilyData {
        val chosenCounts = IntArray(COLOR_FAMILIES)
        val colors = Array(COLOR_FAMILIES) { mutableListOf<Long>() }

        for (sample in samples) {
            val chosen = sample.saturation >= threshold
            val packed = (sample.color.toLong() and 0xFFFFFFFFL) or (if (chosen) 1L shl 32 else 0L)
            colors[sample.family].add(packed)
            if (chosen) chosenCounts[sample.family]++
        }

        val hasSaturated = chosenCounts.any { it > 0 }
        val sortedIndices = colors.indices.sortedByDescending { i ->
            if (hasSaturated) chosenCounts[i] else colors[i].size
        }

        return FamilyData(
            sortedIndices = sortedIndices,
            colors = colors,
            chosenCounts = chosenCounts,
            hasSaturated = hasSaturated,
            familiesUsed = colors.count { it.isNotEmpty() }
        )
    }

    private fun selectPrimaryColor(familyData: FamilyData, config: GradientExtractionConfig): Color {
        val primaryFamily = familyData.sortedIndices.firstOrNull() ?: 0
        val primaryColorRaw = averageColorGroup(familyData.unpackColors(primaryFamily))
        return adjustColorHsv(primaryColorRaw, config.saturationBump, config.valueClamp)
    }

    private fun selectSecondaryColor(
        familyData: FamilyData,
        primaryColor: Color,
        config: GradientExtractionConfig
    ): Color {
        val colorResolution = 360 / COLOR_FAMILIES
        val primaryFamily = familyData.sortedIndices.firstOrNull() ?: 0

        for (i in 1 until familyData.sortedIndices.size) {
            val currentFamily = familyData.sortedIndices[i]
            if (familyData.colors[currentFamily].isEmpty()) continue

            val diff = kotlin.math.abs(currentFamily - primaryFamily)
            val distance = kotlin.math.min(diff, COLOR_FAMILIES - diff) * colorResolution
            if (distance >= config.minHueDistance) {
                val secondaryColorRaw = averageColorGroup(familyData.unpackColors(currentFamily))
                return adjustColorHsv(secondaryColorRaw, config.saturationBump, config.valueClamp)
            }
        }
        return getComplementaryColor(primaryColor)
    }

    private fun adjustColorHsv(color: Color, saturationBump: Float, valueClamp: Float): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hsv[1] = (hsv[1] + saturationBump).coerceIn(0f, 1f)
        hsv[2] = hsv[2].coerceIn(valueClamp, 1f)
        return Color(AndroidColor.HSVToColor(hsv))
    }

    companion object {
        private const val COLOR_FAMILIES = 36
        private val STANDARD_PRESETS = listOf(GradientPreset.VIBRANT, GradientPreset.BALANCED, GradientPreset.SUBTLE)
        private val PRESET_KEYS = mapOf(
            GradientPreset.VIBRANT to "v",
            GradientPreset.BALANCED to "b",
            GradientPreset.SUBTLE to "s"
        )
        private val KEY_TO_PRESET = PRESET_KEYS.entries.associate { (k, v) -> v to k }
    }

    private data class SamplePoint(
        val color: Int,
        val hue: Float,
        val saturation: Float,
        val family: Int
    )

    private fun getComplementaryColor(color: Color): Color {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color.toArgb(), hsv)
        hsv[0] = (hsv[0] + 180f) % 360f
        return Color(AndroidColor.HSVToColor(hsv))
    }

    private fun averageColorGroup(pixelColors: List<Int>): Color {
        if (pixelColors.isEmpty()) return Color.Gray
        var r = 0L
        var g = 0L
        var b = 0L
        for (pixel in pixelColors) {
            r += AndroidColor.red(pixel)
            g += AndroidColor.green(pixel)
            b += AndroidColor.blue(pixel)
        }
        val count = pixelColors.size
        val avgInt = AndroidColor.rgb(
            (r / count).toInt(),
            (g / count).toInt(),
            (b / count).toInt()
        )
        return Color(avgInt)
    }

    fun serializeAllPresets(presets: Map<GradientPreset, Pair<Color, Color>>): String {
        val json = JSONObject()
        for ((preset, colors) in presets) {
            val key = PRESET_KEYS[preset] ?: continue
            json.put(key, serializeHsvPair(colors.first, colors.second))
        }
        return json.toString()
    }

    fun deserializeAllPresets(json: String): Map<GradientPreset, Pair<Color, Color>>? {
        return try {
            val obj = JSONObject(json)
            val result = mutableMapOf<GradientPreset, Pair<Color, Color>>()
            for ((key, preset) in KEY_TO_PRESET) {
                val hsvStr = obj.optString(key, "")
                if (hsvStr.isNotEmpty()) {
                    deserializeHsvPair(hsvStr)?.let { result[preset] = it }
                }
            }
            if (result.isEmpty()) null else result
        } catch (e: Exception) {
            null
        }
    }

    fun getColorsForPreset(
        persisted: Map<GradientPreset, Pair<Color, Color>>,
        preset: GradientPreset
    ): Pair<Color, Color>? = persisted[preset]

    private fun serializeHsvPair(primary: Color, secondary: Color): String {
        val hsvP = FloatArray(3)
        val hsvS = FloatArray(3)
        AndroidColor.colorToHSV(primary.toArgb(), hsvP)
        AndroidColor.colorToHSV(secondary.toArgb(), hsvS)
        return "${hsvP[0]},${hsvP[1]},${hsvP[2]}:${hsvS[0]},${hsvS[1]},${hsvS[2]}"
    }

    private fun deserializeHsvPair(encoded: String): Pair<Color, Color>? {
        val parts = encoded.split(":")
        if (parts.size != 2) return null
        val p = parts[0].split(",").map { it.toFloat() }
        val s = parts[1].split(",").map { it.toFloat() }
        if (p.size != 3 || s.size != 3) return null
        return Pair(
            Color(AndroidColor.HSVToColor(floatArrayOf(p[0], p[1], p[2]))),
            Color(AndroidColor.HSVToColor(floatArrayOf(s[0], s[1], s[2])))
        )
    }
}
