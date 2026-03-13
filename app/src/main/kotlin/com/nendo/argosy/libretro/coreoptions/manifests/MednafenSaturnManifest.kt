package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenSaturnManifest : CoreOptionManifest {
    override val coreId = "mednafen_saturn"
    override val options = listOf(
        // System
        CoreOptionDef(
            key = "beetle_saturn_region",
            displayName = "System Region",
            values = listOf(
                "Auto Detect", "Japan", "North America", "Europe",
                "South Korea", "Asia (NTSC)", "Asia (PAL)", "Brazil", "Latin America"
            ),
            defaultValue = "Auto Detect"
        ),
        CoreOptionDef(
            key = "beetle_saturn_cart",
            displayName = "Cartridge",
            values = listOf(
                "Auto Detect", "None", "Backup Memory", "Extended RAM (1MB)",
                "Extended RAM (4MB)", "The King of Fighters '95",
                "Ultraman: Hikari no Kyojin Densetsu"
            ),
            defaultValue = "Auto Detect",
            description = "Selects the expansion cartridge inserted in the Saturn's cartridge slot"
        ),
        // Input
        CoreOptionDef(
            key = "beetle_saturn_multitap_port1",
            displayName = "6Player Adaptor on Port 1",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the 6-player multitap adapter on controller port 1"
        ),
        CoreOptionDef(
            key = "beetle_saturn_multitap_port2",
            displayName = "6Player Adaptor on Port 2",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables the 6-player multitap adapter on controller port 2"
        ),
        CoreOptionDef(
            key = "beetle_saturn_analog_stick_deadzone",
            displayName = "Analog Stick Deadzone",
            values = listOf("0%", "5%", "10%", "15%", "20%", "25%", "30%"),
            defaultValue = "15%"
        ),
        CoreOptionDef(
            key = "beetle_saturn_trigger_deadzone",
            displayName = "Trigger Deadzone",
            values = listOf("0%", "5%", "10%", "15%", "20%", "25%", "30%"),
            defaultValue = "15%"
        ),
        CoreOptionDef(
            key = "beetle_saturn_mouse_sensitivity",
            displayName = "Mouse Sensitivity",
            values = listOf(
                "5%", "10%", "15%", "20%", "25%", "30%", "35%", "40%", "45%", "50%",
                "55%", "60%", "65%", "70%", "75%", "80%", "85%", "90%", "95%", "100%",
                "105%", "110%", "115%", "120%", "125%", "130%", "135%", "140%", "145%", "150%",
                "155%", "160%", "165%", "170%", "175%", "180%", "185%", "190%", "195%", "200%"
            ),
            defaultValue = "100%"
        ),
        CoreOptionDef(
            key = "beetle_saturn_virtuagun_crosshair",
            displayName = "Gun Crosshair",
            values = listOf("Cross", "Dot", "Off"),
            defaultValue = "Cross",
            description = "Selects the crosshair style shown when using the Virtua Gun"
        ),
        // CD
        CoreOptionDef(
            key = "beetle_saturn_cdimagecache",
            displayName = "CD Image Cache (Restart)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads the entire disc image into memory for faster access"
        ),
        // Performance
        CoreOptionDef(
            key = "beetle_saturn_midsync",
            displayName = "Mid-frame Input Synchronization",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Synchronizes input mid-frame for reduced latency at a performance cost"
        ),
        // RTC
        CoreOptionDef(
            key = "beetle_saturn_autortc",
            displayName = "Automatically Set RTC on Game Load",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Syncs the emulated real-time clock to the host system time on load"
        ),
        CoreOptionDef(
            key = "beetle_saturn_autortc_lang",
            displayName = "BIOS Language",
            values = listOf("english", "german", "french", "spanish", "italian", "japanese"),
            defaultValue = "english"
        ),
        // Display
        CoreOptionDef(
            key = "beetle_saturn_horizontal_overscan",
            displayName = "Horizontal Overscan Mask",
            values = (0..30).map { (it * 2).toString() },
            defaultValue = "0",
            description = "Sets how many pixels to crop from the left and right screen edges"
        ),
        CoreOptionDef(
            key = "beetle_saturn_initial_scanline",
            displayName = "Initial Scanline",
            values = (0..40).map { it.toString() },
            defaultValue = "0",
            description = "Sets the first visible scanline for NTSC output"
        ),
        CoreOptionDef(
            key = "beetle_saturn_last_scanline",
            displayName = "Last Scanline",
            values = (210..239).map { it.toString() },
            defaultValue = "239",
            description = "Sets the last visible scanline for NTSC output"
        ),
        CoreOptionDef(
            key = "beetle_saturn_initial_scanline_pal",
            displayName = "Initial Scanline PAL",
            values = (0..60).map { it.toString() },
            defaultValue = "16",
            description = "Sets the first visible scanline for PAL output"
        ),
        CoreOptionDef(
            key = "beetle_saturn_last_scanline_pal",
            displayName = "Last Scanline PAL",
            values = (230..287).map { it.toString() },
            defaultValue = "271",
            description = "Sets the last visible scanline for PAL output"
        ),
        CoreOptionDef(
            key = "beetle_saturn_horizontal_blend",
            displayName = "Enable Horizontal Blend (Blur)",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Blends adjacent horizontal pixels to simulate composite video blur"
        ),
    )
}
