package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.R
import com.nendo.argosy.data.model.VariantCategory

/**
 * The display label. `VariantCategory` lives in `data/model` and must not import `R`, so the
 * label is attached here beside the persisted `key`, which stays the wire/folder value.
 */
@get:StringRes
val VariantCategory.labelRes: Int
    get() = when (this) {
        VariantCategory.GAME -> R.string.variant_category_game
        VariantCategory.PATCH -> R.string.variant_category_patch
        VariantCategory.TRANSLATION -> R.string.variant_category_translation
        VariantCategory.MOD -> R.string.variant_category_mod
        VariantCategory.HACK -> R.string.variant_category_hack
        VariantCategory.DEMO -> R.string.variant_category_demo
        VariantCategory.PROTOTYPE -> R.string.variant_category_prototype
        VariantCategory.UPDATE -> R.string.variant_category_update
        VariantCategory.DLC -> R.string.variant_category_dlc
        VariantCategory.MANUAL -> R.string.variant_category_manual
        VariantCategory.CHEAT -> R.string.variant_category_cheat
        VariantCategory.SOUNDTRACK -> R.string.variant_category_soundtrack
        VariantCategory.SCREENSHOT -> R.string.variant_category_screenshot
        VariantCategory.UNKNOWN -> R.string.variant_category_unknown
    }
