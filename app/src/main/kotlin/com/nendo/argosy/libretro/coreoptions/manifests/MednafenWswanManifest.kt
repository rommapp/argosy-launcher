package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object MednafenWswanManifest : CoreOptionManifest {
    override val coreId = "mednafen_wswan"
    override val options = listOf(
        CoreOptionDef(
            key = "wswan_rotate_display",
            displayName = "Display Rotation",
            values = listOf("manual", "landscape", "portrait"),
            defaultValue = "manual",
            description = "Sets the screen orientation for games that use portrait mode"
        ),
        CoreOptionDef(
            key = "wswan_rotate_keymap",
            displayName = "Rotate Button Mappings",
            values = listOf("auto", "disabled", "enabled"),
            defaultValue = "auto",
            description = "Rotates button mappings to match the display orientation"
        ),
        CoreOptionDef(
            key = "wswan_mono_palette",
            displayName = "Color Palette",
            values = listOf(
                "default", "wonderswan", "wondeswan_color", "swancrystal",
                "gb_dmg", "gb_pocket", "gb_light", "blossom_pink", "bubbles_blue",
                "buttercup_green", "digivice", "game_com", "gameking", "game_master",
                "golden_wild", "greenscale", "hokage_orange", "labo_fawn",
                "legendary_super_saiyan", "microvision", "million_live_gold",
                "odyssey_gold", "shiny_sky_blue", "slime_blue", "ti_83",
                "travel_wood", "virtual_boy"
            ),
            defaultValue = "default",
            description = "Selects the color palette used for WonderSwan mono games"
        ),
        CoreOptionDef(
            key = "wswan_gfx_colors",
            displayName = "Color Depth (Restart Required)",
            values = listOf("16bit", "24bit"),
            defaultValue = "16bit",
            description = "Sets the color depth used for rendering"
        ),
        CoreOptionDef(
            key = "wswan_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Skips rendering some frames to improve performance"
        ),
        CoreOptionDef(
            key = "wswan_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf("15", "18", "21", "24", "27", "30", "33", "36", "39", "42", "45", "48", "51", "54", "57", "60"),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames are skipped"
        ),
        CoreOptionDef(
            key = "wswan_60hz_mode",
            displayName = "60Hz Mode",
            values = listOf("disabled", "enabled"),
            defaultValue = "enabled",
            description = "Forces 60Hz output instead of the WonderSwan's native 75Hz"
        ),
        CoreOptionDef(
            key = "wswan_sound_sample_rate",
            displayName = "Sound Output Sample Rate",
            values = listOf("11025", "22050", "44100", "48000"),
            defaultValue = "44100",
            description = "Sets the audio output sample rate in Hz"
        ),
        CoreOptionDef(
            key = "wswan_sound_low_pass",
            displayName = "Audio Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Applies a low-pass filter to soften audio output"
        ),
    )
}
