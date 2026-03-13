package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object BluemxManifest : CoreOptionManifest {
    override val coreId = "bluemsx"
    override val options = listOf(
        CoreOptionDef(
            key = "bluemsx_msxtype",
            displayName = "Machine Type (Restart)",
            values = listOf(
                "Auto", "MSX", "MSXturboR", "MSX2", "MSX2+",
                "SEGA - SG-1000", "SEGA - SC-3000", "SEGA - SF-7000",
                "SVI - Spectravideo SVI-318", "SVI - Spectravideo SVI-328",
                "SVI - Spectravideo SVI-328 MK2",
                "ColecoVision", "Coleco (Spectravideo SVI-603)"
            ),
            defaultValue = "Auto",
            description = "Selects which hardware platform to emulate"
        ),
        CoreOptionDef(
            key = "bluemsx_overscan",
            displayName = "Crop Overscan",
            values = listOf("disabled", "enabled", "MSX2"),
            defaultValue = "disabled",
            description = "Removes the border area around the visible picture"
        ),
        CoreOptionDef(
            key = "bluemsx_vdp_synctype",
            displayName = "VDP Sync Type (Restart)",
            values = listOf("Auto", "50Hz", "60Hz"),
            defaultValue = "Auto",
            description = "Sets the video display processor refresh rate"
        ),
        CoreOptionDef(
            key = "bluemsx_nospritelimits",
            displayName = "No Sprite Limit",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Removes the per-scanline sprite limit to reduce flickering"
        ),
        CoreOptionDef(
            key = "bluemsx_ym2413_enable",
            displayName = "Sound YM2413 Enable (Restart)",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the YM2413 FM synthesis sound chip"
        ),
        CoreOptionDef(
            key = "bluemsx_cartmapper",
            displayName = "Cart Mapper Type (Restart)",
            values = listOf(
                "Auto", "Normal", "mirrored", "basic", "0x4000", "0xC000",
                "ascii8", "ascii8sram", "ascii16", "ascii16sram", "ascii16nf",
                "konami4", "konami4nf", "konami5", "konamisynth", "korean80",
                "korean90", "korean126", "MegaFlashRomScc", "MegaFlashRomSccPlus",
                "msxdos2", "scc", "sccexpanded", "sccmirrored", "sccplus",
                "snatcher", "sdsnatcher", "SegaBasic", "SG1000", "SG1000Castle",
                "SG1000RamA", "SG1000RamB", "SC3000"
            ),
            defaultValue = "Auto",
            description = "Overrides the memory mapper type used by the cartridge"
        ),
    )
}
