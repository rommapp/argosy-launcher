package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenPceManifest : CoreOptionManifest {
    override val coreId = "mednafen_pce"
    override val options = listOf(
        CoreOptionDef(
            key = "pce_fast_palette",
            displayName = "Palette",
            values = listOf("RGB", "Composite"),
            defaultValue = "RGB",
            description = "Selects between clean RGB output or composite video color simulation"
        ),
        CoreOptionDef(
            key = "pce_fast_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Skips rendering some frames to improve performance"
        ),
        CoreOptionDef(
            key = "pce_fast_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf(
                "15", "18", "21", "24", "27", "30", "33",
                "36", "39", "42", "45", "48", "51", "54", "57", "60"
            ),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames will be skipped"
        ),
        CoreOptionDef(
            key = "pce_fast_hoverscan",
            displayName = "Horizontal Overscan (352 Width Mode Only)",
            values = listOf(
                "300", "302", "304", "306", "308", "310", "312", "314", "316", "318",
                "320", "322", "324", "326", "328", "330", "332", "334", "336", "338",
                "340", "342", "344", "346", "348", "350", "352"
            ),
            defaultValue = "352",
            description = "Sets the horizontal display width in 352-pixel mode"
        ),
        CoreOptionDef(
            key = "pce_fast_initial_scanline",
            displayName = "Initial Scanline",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
                "31", "32", "33", "34", "35", "36", "37", "38", "39", "40"
            ),
            defaultValue = "3",
            description = "Sets the first visible scanline to crop the top of the display"
        ),
        CoreOptionDef(
            key = "pce_fast_last_scanline",
            displayName = "Last Scanline",
            values = listOf(
                "208", "209", "210", "211", "212", "213", "214", "215", "216", "217",
                "218", "219", "220", "221", "222", "223", "224", "225", "226", "227",
                "228", "229", "230", "231", "232", "233", "234", "235", "236", "237",
                "238", "239", "240", "241", "242"
            ),
            defaultValue = "242",
            description = "Sets the last visible scanline to crop the bottom of the display"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_0_volume",
            displayName = "Sound Channel 0 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_1_volume",
            displayName = "Sound Channel 1 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_2_volume",
            displayName = "Sound Channel 2 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_3_volume",
            displayName = "Sound Channel 3 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_4_volume",
            displayName = "Sound Channel 4 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_sound_channel_5_volume",
            displayName = "Sound Channel 5 Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_mouse_sensitivity",
            displayName = "Mouse Sensitivity",
            values = listOf(
                "0.25", "0.50", "0.75", "1.00", "1.25", "1.50", "1.75", "2.00",
                "2.25", "2.50", "2.75", "3.00", "3.25", "3.50", "3.75", "4.00",
                "4.25", "4.50", "4.75", "5.00"
            ),
            defaultValue = "1.25"
        ),
        CoreOptionDef(
            key = "pce_fast_disable_softreset",
            displayName = "Disable Soft Reset",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Prevents the Run+Select button combo from triggering a soft reset"
        ),
        CoreOptionDef(
            key = "pce_fast_default_joypad_type_p1",
            displayName = "P1 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons",
            description = "Sets whether Player 1 uses a 2-button or 6-button controller"
        ),
        CoreOptionDef(
            key = "pce_fast_default_joypad_type_p2",
            displayName = "P2 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons",
            description = "Sets whether Player 2 uses a 2-button or 6-button controller"
        ),
        CoreOptionDef(
            key = "pce_fast_default_joypad_type_p3",
            displayName = "P3 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons",
            description = "Sets whether Player 3 uses a 2-button or 6-button controller"
        ),
        CoreOptionDef(
            key = "pce_fast_default_joypad_type_p4",
            displayName = "P4 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons",
            description = "Sets whether Player 4 uses a 2-button or 6-button controller"
        ),
        CoreOptionDef(
            key = "pce_fast_default_joypad_type_p5",
            displayName = "P5 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons",
            description = "Sets whether Player 5 uses a 2-button or 6-button controller"
        ),
        CoreOptionDef(
            key = "pce_fast_turbo_toggling",
            displayName = "Turbo ON/OFF Toggle",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Allows turbo buttons to be toggled on and off with a button press"
        ),
        CoreOptionDef(
            key = "pce_fast_turbo_toggle_hotkey",
            displayName = "Alternate Turbo Hotkey",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables an alternate hotkey combination for turbo toggling"
        ),
        CoreOptionDef(
            key = "pce_fast_turbo_delay",
            displayName = "Turbo Delay",
            values = listOf(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15"
            ),
            defaultValue = "3",
            description = "Sets the number of frames between each turbo button press"
        ),
        CoreOptionDef(
            key = "pce_fast_cdimagecache",
            displayName = "CD Image Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads the entire CD image into memory for faster access"
        ),
        CoreOptionDef(
            key = "pce_fast_cdbios",
            displayName = "CD BIOS",
            values = listOf(
                "Games Express", "System Card 1", "System Card 2",
                "System Card 3", "System Card 2 US", "System Card 3 US"
            ),
            defaultValue = "System Card 3",
            description = "Selects which CD System Card BIOS to use for CD-ROM games"
        ),
        CoreOptionDef(
            key = "pce_fast_cdspeed",
            displayName = "CD Speed",
            values = listOf("1", "2", "4", "8"),
            defaultValue = "1",
            description = "Sets the CD-ROM read speed multiplier to reduce loading times"
        ),
        CoreOptionDef(
            key = "pce_fast_adpcmvolume",
            displayName = "(CD) ADPCM Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_cddavolume",
            displayName = "(CD) CDDA Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_cdpsgvolume",
            displayName = "(CD) CD PSG Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "pce_fast_nospritelimit",
            displayName = "No Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the per-scanline sprite limit to eliminate flickering"
        ),
        CoreOptionDef(
            key = "pce_fast_ocmultiplier",
            displayName = "CPU Overclock Multiplier",
            values = listOf(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "20", "30", "40", "50"
            ),
            defaultValue = "1",
            description = "Multiplies the emulated CPU speed to reduce slowdown in games"
        ),
    )
}
