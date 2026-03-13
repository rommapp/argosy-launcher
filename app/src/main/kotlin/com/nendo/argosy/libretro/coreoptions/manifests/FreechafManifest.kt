package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object FreechafManifest : CoreOptionManifest {
    override val coreId = "freechaf"
    override val options = listOf(
        CoreOptionDef(
            key = "freechaf_fast_scrclr",
            displayName = "Clear Screen in Single Frame",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Clears the entire screen in one frame instead of gradually"
        ),
    )
}
