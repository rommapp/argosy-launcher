package com.nendo.argosy.data.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import javax.inject.Inject
import javax.inject.Singleton
import android.graphics.Color as AndroidColor

enum class GradientPreset {
    VIBRANT,
    BALANCED,
    SUBTLE,
    CUSTOM;

    fun toConfig(): GradientExtractionConfig = when (this) {
        VIBRANT -> GradientExtractionConfig(
            minSaturation = 0.25f,
            saturationBump = 0.60f,
            valueClamp = 0.90f
        )
        BALANCED -> GradientExtractionConfig()
        SUBTLE -> GradientExtractionConfig(
            minSaturation = 0.40f,
            saturationBump = 0.30f,
            valueClamp = 0.70f
        )
        CUSTOM -> GradientExtractionConfig()
    }

    fun displayName(): String = when (this) {
        VIBRANT -> "Vibrant"
        BALANCED -> "Balanced"
        SUBTLE -> "Subtle"
        CUSTOM -> "Custom"
    }

    companion object {
        fun fromString(value: String?): GradientPreset =
            entries.find { it.name == value } ?: BALANCED
    }
}

data class GradientExtractionConfig(
    val samplesX: Int = 12,
    val samplesY: Int = 18,
    val radius: Int = 3,
    val minSaturation: Float = 0.35f,
    val minValue: Float = 0.15f,
    val minHueDistance: Int = 40,
    val saturationBump: Float = 0.45f,
    val valueClamp: Float = 0.8f,
    val safeLimits: Boolean = true
)

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

    private val cache = object : LruCache<String, Pair<Color, Color>>(200) {
        override fun sizeOf(key: String, value: Pair<Color, Color>) = 1
    }

    fun getGradientColors(
        coverPath: String,
        preset: GradientPreset,
        customConfig: GradientExtractionConfig? = null
    ): Pair<Color, Color>? {
        val config = if (preset == GradientPreset.CUSTOM) {
            customConfig ?: GradientPreset.VIBRANT.toConfig()
        } else {
            preset.toConfig()
        }

        val cacheKey = buildCacheKey(coverPath, preset, customConfig)
        cache.get(cacheKey)?.let { return it }

        val bitmap = BitmapFactory.decodeFile(coverPath) ?: return null
        return try {
            val result = extractWithMetrics(bitmap, config)
            val colors = Pair(result.primary, result.secondary)
            cache.put(cacheKey, colors)
            colors
        } finally {
            bitmap.recycle()
        }
    }

    private fun buildCacheKey(
        coverPath: String,
        preset: GradientPreset,
        customConfig: GradientExtractionConfig?
    ): String {
        return if (preset == GradientPreset.CUSTOM && customConfig != null) {
            "$coverPath:custom:${customConfig.hashCode()}"
        } else {
            "$coverPath:${preset.name}"
        }
    }

    fun clearCache() = cache.evictAll()

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

    fun serializeColors(primary: Color, secondary: Color): String {
        val hsvPrimary = FloatArray(3)
        val hsvSecondary = FloatArray(3)
        AndroidColor.colorToHSV(primary.toArgb(), hsvPrimary)
        AndroidColor.colorToHSV(secondary.toArgb(), hsvSecondary)
        return "${hsvPrimary[0]},${hsvPrimary[1]},${hsvPrimary[2]}:" +
                "${hsvSecondary[0]},${hsvSecondary[1]},${hsvSecondary[2]}"
    }

    fun deserializeColors(encoded: String): Pair<Color, Color>? {
        return try {
            val parts = encoded.split(":")
            if (parts.size != 2) return null

            val primaryHsv = parts[0].split(",").map { it.toFloat() }
            val secondaryHsv = parts[1].split(",").map { it.toFloat() }

            if (primaryHsv.size != 3 || secondaryHsv.size != 3) return null

            val primary = Color(
                AndroidColor.HSVToColor(floatArrayOf(primaryHsv[0], primaryHsv[1], primaryHsv[2]))
            )
            val secondary = Color(
                AndroidColor.HSVToColor(floatArrayOf(secondaryHsv[0], secondaryHsv[1], secondaryHsv[2]))
            )

            Pair(primary, secondary)
        } catch (e: Exception) {
            null
        }
    }
}
