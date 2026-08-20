package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenPcfxManifest : CoreOptionManifest {
    override val coreId = "mednafen_pcfx"
    override val options = listOf(
        CoreOptionDef(
            key = "pcfx_cdimagecache",
            displayName = "CD Image Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads the entire disc image into memory for faster access"
        ),
        CoreOptionDef(
            key = "pcfx_high_dotclock_width",
            displayName = "High Dotclock Width",
            values = listOf("256", "341", "1024"),
            defaultValue = "1024",
            description = "Emulated width for the 7.16 MHz dot-clock mode; lower is faster but distorts pixels"
        ),
        CoreOptionDef(
            key = "pcfx_suppress_channel_reset_clicks",
            displayName = "Suppress Channel Reset Clicks",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Silences the audio clicks caused by forced channel resets"
        ),
        CoreOptionDef(
            key = "pcfx_emulate_buggy_codec",
            displayName = "Emulate Buggy Codec",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Reproduces the faulty ADPCM encoder some games' audio was mastered with"
        ),
        CoreOptionDef(
            key = "pcfx_resamp_quality",
            displayName = "Sound Quality",
            values = listOf("0", "1", "2", "3", "4", "5"),
            defaultValue = "3",
            description = "Higher values keep more high-frequency detail at a higher CPU cost"
        ),
        CoreOptionDef(
            key = "pcfx_rainbow_chromaip",
            displayName = "Chroma Bilinear Interpolation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Smooths the chroma channel of RAINBOW video; can glitch some games"
        ),
        CoreOptionDef(
            key = "pcfx_nospritelimit",
            displayName = "No Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the 16-sprites-per-scanline hardware limit to reduce flicker"
        ),
        CoreOptionDef(
            key = "pcfx_initial_scanline",
            displayName = "Initial Scanline",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
                "31", "32", "33", "34", "35", "36", "37", "38", "39", "40"
            ),
            defaultValue = "4",
            description = "Sets the first visible scanline to crop the top border"
        ),
        CoreOptionDef(
            key = "pcfx_last_scanline",
            displayName = "Last Scanline",
            values = listOf(
                "208", "209", "210", "211", "212", "213", "214", "215", "216", "217",
                "218", "219", "220", "221", "222", "223", "224", "225", "226", "227",
                "228", "229", "230", "231", "232", "233", "234", "235", "236", "237",
                "238", "239"
            ),
            defaultValue = "235",
            description = "Sets the last visible scanline to crop the bottom border"
        ),
        CoreOptionDef(
            key = "pcfx_mouse_sensitivity",
            displayName = "Mouse Sensitivity",
            values = listOf(
                "1.00", "1.25", "1.50", "1.75", "2.00", "2.25", "2.50", "2.75",
                "3.00", "3.25", "3.50", "3.75", "4.00", "4.25", "4.50", "4.75", "5.00"
            ),
            defaultValue = "1.25"
        ),
    )
}
