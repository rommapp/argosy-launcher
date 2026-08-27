package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.libretro.shader.ShaderRegistry

@get:StringRes
val ShaderRegistry.Category.labelRes: Int
    get() = when (this) {
        ShaderRegistry.Category.CRT -> R.string.shader_category_crt
        ShaderRegistry.Category.HANDHELD -> R.string.shader_category_handheld
        ShaderRegistry.Category.SCALING -> R.string.shader_category_scaling
        ShaderRegistry.Category.SCANLINES -> R.string.shader_category_scanlines
        ShaderRegistry.Category.SHARPENING -> R.string.shader_category_sharpening
        ShaderRegistry.Category.ANTI_ALIASING -> R.string.shader_category_anti_aliasing
        ShaderRegistry.Category.OTHER -> R.string.shader_category_other
    }
