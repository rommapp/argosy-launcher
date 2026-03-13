package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object PicodriveManifest : CoreOptionManifest {
    override val coreId = "picodrive"
    override val options = listOf(
        CoreOptionDef(
            key = "picodrive_region",
            displayName = "System Region",
            values = listOf("Auto", "Japan NTSC", "Japan PAL", "US", "Europe"),
            defaultValue = "Auto"
        ),
        CoreOptionDef(
            key = "picodrive_smstype",
            displayName = "Master System Type",
            values = listOf("Auto", "Game Gear", "Master System", "SG-1000", "SC-3000"),
            defaultValue = "Auto",
            description = "Forces emulation of a specific Sega 8-bit hardware variant"
        ),
        CoreOptionDef(
            key = "picodrive_smsmapper",
            displayName = "Master System ROM Mapping",
            values = listOf(
                "Auto", "Sega", "Codemasters", "Korea", "Korea MSX",
                "Korea X-in-1", "Korea 4-Pak", "Korea Janggun",
                "Korea Nemesis", "Taiwan 8K RAM"
            ),
            defaultValue = "Auto",
            description = "Selects the memory mapper used for Master System cartridge banking"
        ),
        CoreOptionDef(
            key = "picodrive_smstms",
            displayName = "Master System Palette in TMS Modes",
            values = listOf("SMS", "SG-1000"),
            defaultValue = "SMS",
            description = "Selects which palette to use when running TMS9918 graphics modes"
        ),
        CoreOptionDef(
            key = "picodrive_ramcart",
            displayName = "Sega CD RAM Cart",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables a RAM expansion cartridge for extra Sega CD save storage"
        ),
        CoreOptionDef(
            key = "picodrive_aspect",
            displayName = "Core-Provided Aspect Ratio",
            values = listOf("PAR", "4/3", "CRT"),
            defaultValue = "PAR",
            description = "Sets the display aspect ratio reported by the core"
        ),
        CoreOptionDef(
            key = "picodrive_ggghost",
            displayName = "LCD Ghosting Filter",
            values = listOf("off", "weak", "normal"),
            defaultValue = "off",
            description = "Simulates the motion blur of the original Game Gear LCD screen"
        ),
        CoreOptionDef(
            key = "picodrive_renderer",
            displayName = "Video Renderer",
            values = listOf("accurate", "good", "fast"),
            defaultValue = "accurate",
            description = "Selects the rendering accuracy level, lower is faster but less accurate"
        ),
        CoreOptionDef(
            key = "picodrive_sound_rate",
            displayName = "Audio Sample Rate (Hz)",
            values = listOf("16000", "22050", "32000", "44100", "native"),
            defaultValue = "44100"
        ),
        CoreOptionDef(
            key = "picodrive_fm_filter",
            displayName = "FM Filtering",
            values = listOf("off", "on"),
            defaultValue = "off",
            description = "Applies a low-pass filter to FM synthesis audio output"
        ),
        CoreOptionDef(
            key = "picodrive_smsfm",
            displayName = "Master System FM Sound Unit",
            values = listOf("off", "on"),
            defaultValue = "off",
            description = "Enables the YM2413 FM sound unit expansion for Master System"
        ),
        CoreOptionDef(
            key = "picodrive_fmchip",
            displayName = "Mega Drive FM Chip Type",
            values = listOf("ym2612", "ym3438"),
            defaultValue = "ym2612",
            description = "Selects which FM sound chip variant to emulate"
        ),
        CoreOptionDef(
            key = "picodrive_audio_filter",
            displayName = "Audio Filter",
            values = listOf("disabled", "low-pass"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften harsh audio"
        ),
        CoreOptionDef(
            key = "picodrive_lowpass_range",
            displayName = "Low-Pass Filter %",
            values = listOf(
                "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95"
            ),
            defaultValue = "60",
            description = "Sets the cutoff strength of the low-pass audio filter"
        ),
        CoreOptionDef(
            key = "picodrive_input1",
            displayName = "Input Device 1",
            values = listOf("3 button pad", "6 button pad", "team player", "4way play", "None"),
            defaultValue = "3 button pad",
            description = "Selects the controller type connected to port 1"
        ),
        CoreOptionDef(
            key = "picodrive_input2",
            displayName = "Input Device 2",
            values = listOf("3 button pad", "6 button pad", "None"),
            defaultValue = "3 button pad",
            description = "Selects the controller type connected to port 2"
        ),
        CoreOptionDef(
            key = "picodrive_drc",
            displayName = "Dynamic Recompilers",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Uses dynamic recompilation for faster CPU emulation"
        ),
        CoreOptionDef(
            key = "picodrive_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Selects the frameskip method used to improve performance"
        ),
        CoreOptionDef(
            key = "picodrive_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf(
                "15", "18", "21", "24", "27", "30", "33",
                "36", "39", "42", "45", "48", "51", "54", "57", "60"
            ),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames will be skipped"
        ),
        CoreOptionDef(
            key = "picodrive_sprlim",
            displayName = "No Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the per-scanline hardware sprite limit to reduce flicker"
        ),
        CoreOptionDef(
            key = "picodrive_overclk68k",
            displayName = "68K Overclock",
            values = listOf("disabled", "+25%", "+50%", "+75%", "+100%", "+200%", "+400%"),
            defaultValue = "disabled",
            description = "Increases the emulated 68000 CPU speed to reduce slowdown"
        ),
    )
}
