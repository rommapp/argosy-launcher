package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object Mupen64PlusNextGles2Manifest : CoreOptionManifest {
    override val coreId = "mupen64plus_next_gles2"
    override val options = Mupen64PlusNextGles3Manifest.options
        .filterNot { it.key == "mupen64plus-MultiSampling" }
        .map { option ->
            if (option.key == "mupen64plus-EnableCopyColorToRDRAM") {
                option.copy(values = listOf("Off", "Sync"), defaultValue = "Sync")
            } else {
                option
            }
        }
}
