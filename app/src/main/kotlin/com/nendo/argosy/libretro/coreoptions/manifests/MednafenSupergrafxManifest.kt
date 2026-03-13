package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenSupergrafxManifest : CoreOptionManifest {
    override val coreId = "mednafen_supergrafx"
    override val options = listOf(
        CoreOptionDef(
            key = "sgx_palette",
            displayName = "Palette",
            values = listOf("RGB", "Composite"),
            defaultValue = "RGB",
            description = "Selects between clean RGB or composite video color palette"
        ),
        CoreOptionDef(
            key = "sgx_aspect_ratio",
            displayName = "Aspect Ratio",
            values = listOf("auto", "6:5", "4:3"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "sgx_hoverscan",
            displayName = "Horizontal Overscan (352 Width Mode Only)",
            values = listOf(
                "300", "302", "304", "306", "308", "310", "312", "314", "316", "318",
                "320", "322", "324", "326", "328", "330", "332", "334", "336", "338",
                "340", "342", "344", "346", "348", "350", "352"
            ),
            defaultValue = "352",
            description = "Sets the visible horizontal width in 352-pixel display mode"
        ),
        CoreOptionDef(
            key = "sgx_initial_scanline",
            displayName = "Initial Scanline",
            values = listOf(
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
                "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
                "31", "32", "33", "34", "35", "36", "37", "38", "39", "40"
            ),
            defaultValue = "3",
            description = "Sets the first visible scanline to crop the top border"
        ),
        CoreOptionDef(
            key = "sgx_last_scanline",
            displayName = "Last Scanline",
            values = listOf(
                "208", "209", "210", "211", "212", "213", "214", "215", "216", "217",
                "218", "219", "220", "221", "222", "223", "224", "225", "226", "227",
                "228", "229", "230", "231", "232", "233", "234", "235", "236", "237",
                "238", "239", "240", "241", "242"
            ),
            defaultValue = "242",
            description = "Sets the last visible scanline to crop the bottom border"
        ),
        CoreOptionDef(
            key = "sgx_mouse_sensitivity",
            displayName = "Mouse Sensitivity",
            values = listOf(
                "0.25", "0.50", "0.75", "1.00", "1.25", "1.50", "1.75", "2.00",
                "2.25", "2.50", "2.75", "3.00", "3.25", "3.50", "3.75", "4.00",
                "4.25", "4.50", "4.75", "5.00"
            ),
            defaultValue = "1.25"
        ),
        CoreOptionDef(
            key = "sgx_up_down_allowed",
            displayName = "Allow Opposing Directions",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Allows pressing left+right or up+down at the same time"
        ),
        CoreOptionDef(
            key = "sgx_disable_softreset",
            displayName = "Disable Soft Reset",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Prevents the Run+Select button combo from resetting the game"
        ),
        CoreOptionDef(
            key = "sgx_multitap",
            displayName = "Multitap",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Enables the 5-player multitap adapter for multiplayer games"
        ),
        CoreOptionDef(
            key = "sgx_default_joypad_type_p1",
            displayName = "P1 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons"
        ),
        CoreOptionDef(
            key = "sgx_default_joypad_type_p2",
            displayName = "P2 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons"
        ),
        CoreOptionDef(
            key = "sgx_default_joypad_type_p3",
            displayName = "P3 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons"
        ),
        CoreOptionDef(
            key = "sgx_default_joypad_type_p4",
            displayName = "P4 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons"
        ),
        CoreOptionDef(
            key = "sgx_default_joypad_type_p5",
            displayName = "P5 Default Joypad Type",
            values = listOf("2 Buttons", "6 Buttons"),
            defaultValue = "2 Buttons"
        ),
        CoreOptionDef(
            key = "sgx_turbo_toggle",
            displayName = "Turbo ON/OFF Toggle",
            values = listOf("disabled", "switch", "dedicated"),
            defaultValue = "disabled",
            description = "Sets how turbo rapid-fire buttons are activated"
        ),
        CoreOptionDef(
            key = "sgx_turbo_toggle_hotkey",
            displayName = "Alternate Turbo Hotkey",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables an alternate button combination to toggle turbo mode"
        ),
        CoreOptionDef(
            key = "sgx_turbo_delay",
            displayName = "Turbo Delay",
            values = listOf(
                "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15", "30", "60"
            ),
            defaultValue = "3",
            description = "Sets how many frames between each turbo button press"
        ),
        CoreOptionDef(
            key = "sgx_cdimagecache",
            displayName = "CD Image Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads the entire disc image into memory for faster access"
        ),
        CoreOptionDef(
            key = "sgx_cdbios",
            displayName = "CD BIOS",
            values = listOf("Games Express", "System Card 1", "System Card 2", "System Card 3"),
            defaultValue = "System Card 3",
            description = "Selects the System Card BIOS version used for CD-ROM games"
        ),
        CoreOptionDef(
            key = "sgx_detect_gexpress",
            displayName = "Detect Games Express CD",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Automatically detects and uses the Games Express BIOS when needed"
        ),
        CoreOptionDef(
            key = "sgx_cdspeed",
            displayName = "CD Speed",
            values = listOf("1", "2", "4", "8"),
            defaultValue = "1",
            description = "Sets the CD-ROM read speed multiplier for faster loading"
        ),
        CoreOptionDef(
            key = "sgx_adpcmvolume",
            displayName = "(CD) ADPCM Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "sgx_cddavolume",
            displayName = "(CD) CDDA Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "sgx_cdpsgvolume",
            displayName = "(CD) CD PSG Volume %",
            values = listOf(
                "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100",
                "110", "120", "130", "140", "150", "160", "170", "180", "190", "200"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "sgx_forcesgx",
            displayName = "Force SuperGrafx Emulation",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Forces SuperGrafx mode even for standard PC Engine games"
        ),
        CoreOptionDef(
            key = "sgx_nospritelimit",
            displayName = "No Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the per-scanline hardware sprite limit to reduce flicker"
        ),
        CoreOptionDef(
            key = "sgx_ocmultiplier",
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
