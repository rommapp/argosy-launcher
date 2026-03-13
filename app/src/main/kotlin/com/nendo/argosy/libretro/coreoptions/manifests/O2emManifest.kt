package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object O2emManifest : CoreOptionManifest {
    override val coreId = "o2em"
    override val options = listOf(
        CoreOptionDef(
            key = "o2em_bios",
            displayName = "Emulated Hardware",
            values = listOf(
                "o2rom.bin", "Videopac G7000 (European)",
                "Videopac+ G7400 (European)", "Videopac+ G7400 (French)"
            ),
            defaultValue = "o2rom.bin",
            description = "Selects the console hardware variant and BIOS to emulate"
        ),
        CoreOptionDef(
            key = "o2em_region",
            displayName = "Console Region",
            values = listOf("Auto", "NTSC", "PAL"),
            defaultValue = "Auto"
        ),
        CoreOptionDef(
            key = "o2em_swap_gamepads",
            displayName = "Swap Gamepads",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Swaps controller port 1 and port 2 inputs"
        ),
        CoreOptionDef(
            key = "o2em_vkb_transparency",
            displayName = "Virtual Keyboard Transparency",
            values = listOf("0%", "25%", "50%", "75%"),
            defaultValue = "0%",
            description = "Sets how transparent the on-screen virtual keyboard overlay appears"
        ),
        CoreOptionDef(
            key = "o2em_crop_overscan",
            displayName = "Crop Overscan",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the border area around the visible screen"
        ),
        CoreOptionDef(
            key = "o2em_mix_frames",
            displayName = "Interframe Blending",
            values = listOf(
                "Simple", "Ghosting (65%)", "Ghosting (75%)",
                "Ghosting (85%)", "Ghosting (95%)"
            ),
            defaultValue = "Simple",
            description = "Blends consecutive frames to simulate CRT persistence or reduce flicker"
        ),
        CoreOptionDef(
            key = "o2em_audio_volume",
            displayName = "Audio Volume",
            values = listOf(
                "0%", "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%",
                "45%", "50%", "55%", "60%", "65%", "70%", "75%", "80%", "85%",
                "90%", "95%", "100%"
            ),
            defaultValue = "50%"
        ),
        CoreOptionDef(
            key = "o2em_voice_volume",
            displayName = "Voice Volume",
            values = listOf(
                "0%", "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%",
                "45%", "50%", "55%", "60%", "65%", "70%", "75%", "80%", "85%",
                "90%", "95%", "100%"
            ),
            defaultValue = "70%",
            description = "Sets the volume level for The Voice speech synthesis module"
        ),
        CoreOptionDef(
            key = "o2em_low_pass_filter",
            displayName = "Audio Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften harsh audio"
        ),
        CoreOptionDef(
            key = "o2em_low_pass_range",
            displayName = "Audio Filter Level",
            values = listOf(
                "0%", "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%",
                "45%", "50%", "55%", "60%", "65%", "70%", "75%", "80%", "85%",
                "90%", "95%", "100%"
            ),
            defaultValue = "60%",
            description = "Sets the cutoff strength of the low-pass audio filter"
        ),
    )
}
