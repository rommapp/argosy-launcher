package com.nendo.argosy.data.cache

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
