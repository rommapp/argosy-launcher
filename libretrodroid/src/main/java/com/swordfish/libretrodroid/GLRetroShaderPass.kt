package com.swordfish.libretrodroid

internal data class GLRetroShaderPass(
    val vertex: String,
    val fragment: String,
    val linear: Boolean = false,
    val scale: Float = 1.0f
)
