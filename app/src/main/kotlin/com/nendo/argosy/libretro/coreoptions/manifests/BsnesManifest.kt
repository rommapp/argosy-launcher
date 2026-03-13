package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object BsnesManifest : CoreOptionManifest {
    override val coreId = "bsnes"
    override val options = listOf(
        CoreOptionDef(
            key = "bsnes_aspect_ratio",
            displayName = "Preferred Aspect Ratio",
            values = listOf("Auto", "1:1", "4:3", "NTSC", "PAL"),
            defaultValue = "Auto"
        ),
        CoreOptionDef(
            key = "bsnes_ppu_overscan_v",
            displayName = "Crop Vertical Overscan",
            values = listOf("0", "8", "12", "16"),
            defaultValue = "8",
            description = "Removes empty border lines at the top and bottom of the screen"
        ),
        CoreOptionDef(
            key = "bsnes_blur_emulation",
            displayName = "Blur Emulation",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Simulates the horizontal blur of the original SNES video output"
        ),
        CoreOptionDef(
            key = "bsnes_video_filter",
            displayName = "Video Filter",
            values = listOf("None", "NTSC (RF)", "NTSC (Composite)", "NTSC (S-Video)", "NTSC (RGB)"),
            defaultValue = "None",
            description = "Applies an NTSC signal filter to simulate different cable types"
        ),
        CoreOptionDef(
            key = "bsnes_video_luminance",
            displayName = "Color Adjustment - Luminance",
            values = listOf(
                "0", "10", "20", "30", "40", "50",
                "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "bsnes_video_saturation",
            displayName = "Color Adjustment - Saturation",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90",
                "100", "110", "120", "130", "140", "150", "160", "170",
                "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "bsnes_video_gamma",
            displayName = "Color Adjustment - Gamma",
            values = listOf(
                "100", "110", "120", "130", "140", "150",
                "160", "170", "180", "190", "200"
            ),
            defaultValue = "150"
        ),
        CoreOptionDef(
            key = "bsnes_ppu_fast",
            displayName = "PPU (Video) - Fast Mode",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Speeds up video rendering at the cost of minor accuracy loss"
        ),
        CoreOptionDef(
            key = "bsnes_ppu_deinterlace",
            displayName = "PPU (Video) - Deinterlace",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Renders all fields of interlaced content as full frames"
        ),
        CoreOptionDef(
            key = "bsnes_ppu_no_sprite_limit",
            displayName = "PPU (Video) - No Sprite Limit",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Removes the per-scanline sprite limit to eliminate flickering"
        ),
        CoreOptionDef(
            key = "bsnes_ppu_no_vram_blocking",
            displayName = "PPU (Video) - No VRAM Blocking",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Removes VRAM access restrictions for reduced accuracy but fewer glitches"
        ),
        CoreOptionDef(
            key = "bsnes_dsp_fast",
            displayName = "DSP (Audio) - Fast Mode",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Speeds up audio DSP emulation at the cost of minor accuracy loss"
        ),
        CoreOptionDef(
            key = "bsnes_dsp_cubic",
            displayName = "DSP (Audio) - Cubic Interpolation",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Uses cubic interpolation for audio, producing smoother sound than Gaussian"
        ),
        CoreOptionDef(
            key = "bsnes_dsp_echo_shadow",
            displayName = "DSP (Audio) - Echo Shadow RAM",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Emulates a hardware quirk where echo buffer memory overlaps with main RAM"
        ),
        CoreOptionDef(
            key = "bsnes_mode7_scale",
            displayName = "HD Mode 7 - Scale",
            values = listOf("1x", "2x", "3x", "4x", "5x", "6x", "7x", "8x"),
            defaultValue = "1x",
            description = "Increases the resolution of Mode 7 perspective effects"
        ),
        CoreOptionDef(
            key = "bsnes_mode7_perspective",
            displayName = "HD Mode 7 - Perspective Correction",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Fixes perspective distortion in Mode 7 backgrounds at higher scales"
        ),
        CoreOptionDef(
            key = "bsnes_mode7_supersample",
            displayName = "HD Mode 7 - Supersampling",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Applies supersampling anti-aliasing to HD Mode 7 rendering"
        ),
        CoreOptionDef(
            key = "bsnes_mode7_mosaic",
            displayName = "HD Mode 7 - HD->SD Mosaic",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Shows the mosaic effect at SD resolution even when using HD Mode 7"
        ),
        CoreOptionDef(
            key = "bsnes_run_ahead_frames",
            displayName = "Internal Run-Ahead",
            values = listOf("OFF", "1", "2", "3", "4"),
            defaultValue = "OFF",
            description = "Reduces input latency by running frames ahead and discarding them"
        ),
        CoreOptionDef(
            key = "bsnes_coprocessor_delayed_sync",
            displayName = "Coprocessors - Fast Mode",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Speeds up coprocessor emulation by reducing sync frequency"
        ),
        CoreOptionDef(
            key = "bsnes_coprocessor_prefer_hle",
            displayName = "Coprocessors - Prefer HLE",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Uses high-level emulation for coprocessors instead of low-level firmware"
        ),
        CoreOptionDef(
            key = "bsnes_hotfixes",
            displayName = "Hotfixes",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Applies game-specific patches to fix bugs in certain ROMs"
        ),
        CoreOptionDef(
            key = "bsnes_entropy",
            displayName = "Entropy (Randomization)",
            values = listOf("Low", "High", "None"),
            defaultValue = "Low",
            description = "Controls how much initial memory is randomized at startup"
        ),
        CoreOptionDef(
            key = "bsnes_cpu_fastmath",
            displayName = "CPU Fast Math",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Skips CPU multiply and divide wait cycles for faster performance"
        ),
        CoreOptionDef(
            key = "bsnes_cpu_overclock",
            displayName = "Overclocking - CPU",
            values = listOf(
                "10", "20", "30", "40", "50", "60", "70", "80", "90",
                "100", "110", "120", "130", "140", "150", "160", "170",
                "180", "190", "200", "210", "220", "230", "240", "250",
                "260", "270", "280", "290", "300", "310", "320", "330",
                "340", "350", "360", "370", "380", "390", "400"
            ),
            defaultValue = "100",
            description = "Adjusts the CPU clock speed percentage to reduce slowdown"
        ),
        CoreOptionDef(
            key = "bsnes_cpu_sa1_overclock",
            displayName = "Overclocking - SA-1 Coprocessor",
            values = listOf(
                "10", "20", "30", "40", "50", "60", "70", "80", "90",
                "100", "110", "120", "130", "140", "150", "160", "170",
                "180", "190", "200", "210", "220", "230", "240", "250",
                "260", "270", "280", "290", "300", "310", "320", "330",
                "340", "350", "360", "370", "380", "390", "400"
            ),
            defaultValue = "100",
            description = "Adjusts the SA-1 coprocessor clock speed for games that use it"
        ),
        CoreOptionDef(
            key = "bsnes_cpu_sfx_overclock",
            displayName = "Overclocking - SuperFX Coprocessor",
            values = listOf(
                "10", "20", "30", "40", "50", "60", "70", "80", "90",
                "100", "110", "120", "130", "140", "150", "160", "170",
                "180", "190", "200", "210", "220", "230", "240", "250",
                "260", "270", "280", "290", "300", "310", "320", "330",
                "340", "350", "360", "370", "380", "390", "400", "410",
                "420", "430", "440", "450", "460", "470", "480", "490",
                "500", "510", "520", "530", "540", "550", "560", "570",
                "580", "590", "600", "610", "620", "630", "640", "650",
                "660", "670", "680", "690", "700", "710", "720", "730",
                "740", "750", "760", "770", "780", "790", "800"
            ),
            defaultValue = "100",
            description = "Adjusts the SuperFX coprocessor clock speed for games like Star Fox"
        ),
        CoreOptionDef(
            key = "bsnes_sgb_bios",
            displayName = "Preferred Super Game Boy BIOS",
            values = listOf("SGB1.sfc", "SGB2.sfc"),
            defaultValue = "SGB1.sfc",
            description = "Selects which Super Game Boy BIOS version to use"
        ),
        CoreOptionDef(
            key = "bsnes_hide_sgb_border",
            displayName = "Hide SGB Border",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Hides the decorative border shown in Super Game Boy mode"
        ),
        CoreOptionDef(
            key = "bsnes_touchscreen_lightgun",
            displayName = "Touchscreen Light Gun",
            values = listOf("ON", "OFF"),
            defaultValue = "ON",
            description = "Allows using the touchscreen as a light gun input device"
        ),
        CoreOptionDef(
            key = "bsnes_touchscreen_lightgun_superscope_reverse",
            displayName = "Super Scope Reverse Trigger Buttons",
            values = listOf("OFF", "ON"),
            defaultValue = "OFF",
            description = "Swaps the fire and cursor buttons for the Super Scope"
        )
    )
}
