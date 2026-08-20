package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

/**
 * SAME CDi builds its option keys as `same_cdi_<suffix>` at runtime, so the keys never appear
 * whole in the shipped library; the suffixes are read from the core's own retro_set_environment.
 * The MAME plumbing options are withheld on purpose - `mame_paths_enable` would move the romset
 * search away from the directory Argosy stages the CD-i BIOS into, and cheats and save states are
 * declared unsupported upstream.
 */
object SameCdiManifest : CoreOptionManifest {
    override val coreId = "same_cdi"
    override val options = listOf(
        CoreOptionDef(
            key = "same_cdi_altres",
            displayName = "Resolution",
            values = listOf(
                "640x360", "640x480", "800x600", "800x450", "960x720", "960x540",
                "1024x768", "1024x576", "1280x960", "1280x720", "1600x1200", "1600x900",
                "1440x1080", "1920x1080", "1920x1440", "2560x1440", "2880x2160", "3840x2160"
            ),
            defaultValue = "640x480",
            description = "Internal render resolution; higher costs performance for little gain on CD-i"
        ),
        CoreOptionDef(
            key = "same_cdi_alternate_renderer",
            displayName = "Alternate Render Method",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Switches MAME's video path, which some titles render more reliably with"
        ),
        CoreOptionDef(
            key = "same_cdi_mouse_enable",
            displayName = "In-Game Mouse",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Drives the CD-i pointer from touch instead of the controller"
        ),
        CoreOptionDef(
            key = "same_cdi_lightgun_mode",
            displayName = "Lightgun Mode",
            values = listOf("none", "touchscreen", "lightgun"),
            defaultValue = "none",
            description = "Aiming source for the games that expect a pointing device"
        ),
        CoreOptionDef(
            key = "same_cdi_buttons_profiles",
            displayName = "Per-Game Button Profiles",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Lets the core pick a button arrangement that suits the loaded game"
        ),
        CoreOptionDef(
            key = "same_cdi_nvram_saves",
            displayName = "NVRAM Saves Per Game",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Keeps each game's battery-backed memory separate instead of sharing one file"
        ),
        CoreOptionDef(
            key = "same_cdi_cpu_overclock",
            displayName = "CPU Overclock",
            values = listOf(
                "default", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
                "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
                "50", "51", "52", "53", "54", "55", "60", "65", "70", "75",
                "80", "85", "90", "95", "100", "105", "110", "115", "120",
                "125", "130", "135", "140", "145", "150"
            ),
            defaultValue = "default",
            description = "Percentage of the stock main CPU speed; below 100 can steady demanding scenes"
        ),
    )
}
