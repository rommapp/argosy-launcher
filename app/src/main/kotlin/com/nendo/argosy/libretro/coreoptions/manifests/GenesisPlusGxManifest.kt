package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object GenesisPlusGxManifest : CoreOptionManifest {
    override val coreId = "genesis_plus_gx"
    override val options = listOf(
        CoreOptionDef(
            key = "genesis_plus_gx_system_hw",
            displayName = "System Hardware",
            values = listOf(
                "auto", "sg-1000", "sg-1000 II", "sg-1000 II + ram ext.",
                "mark-III", "master system", "master system II",
                "game gear", "mega drive / genesis"
            ),
            defaultValue = "auto",
            description = "Selects which Sega hardware to emulate"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_region_detect",
            displayName = "System Region",
            values = listOf("auto", "ntsc-u", "pal", "ntsc-j"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_vdp_mode",
            displayName = "Force VDP Mode",
            values = listOf("auto", "60hz", "50hz"),
            defaultValue = "auto",
            description = "Forces the video display processor to a specific refresh rate"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_bios",
            displayName = "System Boot ROM",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Uses the official system boot ROM for more accurate startup"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_system_bram",
            displayName = "CD System BRAM",
            values = listOf("per bios", "per game"),
            defaultValue = "per bios",
            description = "Selects whether CD system saves are shared per BIOS or per game"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_cart_bram",
            displayName = "CD Backup Cart BRAM",
            values = listOf("per cart", "per game"),
            defaultValue = "per cart",
            description = "Selects whether backup cart saves are shared or per game"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_cart_size",
            displayName = "CD Backup Cart BRAM Size",
            values = listOf("disabled", "128k", "256k", "512k", "1meg", "2meg", "4meg"),
            defaultValue = "4meg",
            description = "Sets the capacity of the CD backup RAM cartridge"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_add_on",
            displayName = "CD Add-On (MD Mode)",
            values = listOf("auto", "sega/mega cd", "megasd", "none"),
            defaultValue = "auto",
            description = "Selects the CD hardware add-on to emulate in Mega Drive mode"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_lock_on",
            displayName = "Cartridge Lock-On",
            values = listOf("disabled", "game genie", "action replay (pro)", "sonic & knuckles"),
            defaultValue = "disabled",
            description = "Attaches a lock-on cartridge for cheats or Sonic & Knuckles mode"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_aspect_ratio",
            displayName = "Core-Provided Aspect Ratio",
            values = listOf("auto", "NTSC PAR", "PAL PAR", "4:3", "Uncorrected"),
            defaultValue = "auto"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_overscan",
            displayName = "Borders",
            values = listOf("disabled", "top/bottom", "left/right", "full"),
            defaultValue = "disabled",
            description = "Shows the overscan border areas normally hidden on consumer TVs"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_left_border",
            displayName = "Hide Master System Side Borders",
            values = listOf("disabled", "left border", "left & right borders"),
            defaultValue = "disabled",
            description = "Hides the blank side borders shown in Master System games"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_gg_extra",
            displayName = "Game Gear Extended Screen",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Shows the full Game Gear display area that is normally cropped"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_blargg_ntsc_filter",
            displayName = "Blargg NTSC Filter",
            values = listOf("disabled", "monochrome", "composite", "svideo", "rgb"),
            defaultValue = "disabled",
            description = "Applies an NTSC signal filter to simulate different cable types"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_lcd_filter",
            displayName = "LCD Ghosting Filter",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Simulates the ghosting effect of Game Gear LCD screens"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_render",
            displayName = "Interlaced Mode 2 Output",
            values = listOf("single field", "double field"),
            defaultValue = "single field",
            description = "Controls how interlaced mode 2 content is rendered"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_frameskip",
            displayName = "Frameskip",
            values = listOf("disabled", "auto", "manual"),
            defaultValue = "disabled",
            description = "Skips rendering some frames to improve performance"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_frameskip_threshold",
            displayName = "Frameskip Threshold (%)",
            values = listOf(
                "15", "18", "21", "24", "27", "30", "33",
                "36", "39", "42", "45", "48", "51", "54", "57", "60"
            ),
            defaultValue = "33",
            description = "Sets the audio buffer occupancy below which frames will be skipped"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_ym2413",
            displayName = "Master System FM (YM2413)",
            values = listOf("auto", "disabled", "enabled"),
            defaultValue = "auto",
            description = "Enables FM sound chip emulation for Master System games that support it"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_ym2413_core",
            displayName = "Master System FM (YM2413) Core",
            values = listOf("mame", "nuked"),
            defaultValue = "mame",
            description = "Selects the YM2413 FM emulation core for audio quality vs performance"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_ym2612",
            displayName = "Mega Drive/Genesis FM",
            values = listOf(
                "mame (ym2612)", "mame (asic ym3438)", "mame (enhanced ym3438)",
                "nuked (ym2612)", "nuked (ym3438)"
            ),
            defaultValue = "mame (ym2612)",
            description = "Selects the FM synthesis chip and emulation core for Mega Drive audio"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sound_output",
            displayName = "Sound Output",
            values = listOf("stereo", "mono"),
            defaultValue = "stereo"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_audio_filter",
            displayName = "Audio Filter",
            values = listOf("disabled", "low-pass", "EQ"),
            defaultValue = "disabled",
            description = "Applies a post-processing audio filter to the output"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_lowpass_range",
            displayName = "Low-Pass Filter %",
            values = listOf(
                "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95"
            ),
            defaultValue = "60",
            description = "Sets the cutoff frequency for the low-pass audio filter"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_psg_preamp",
            displayName = "PSG Preamp Level",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100",
                "105", "110", "115", "120", "125", "130", "135", "140", "145", "150",
                "155", "160", "165", "170", "175", "180", "185", "190", "195", "200"
            ),
            defaultValue = "150",
            description = "Adjusts the PSG sound chip volume relative to other audio sources"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_fm_preamp",
            displayName = "FM Preamp Level",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100",
                "105", "110", "115", "120", "125", "130", "135", "140", "145", "150",
                "155", "160", "165", "170", "175", "180", "185", "190", "195", "200"
            ),
            defaultValue = "100",
            description = "Adjusts the FM synthesis volume relative to other audio sources"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_cdda_volume",
            displayName = "CD-DA Volume",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_pcm_volume",
            displayName = "PCM Volume",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_audio_eq_low",
            displayName = "EQ Low",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_audio_eq_mid",
            displayName = "EQ Mid",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_audio_eq_high",
            displayName = "EQ High",
            values = listOf(
                "0", "5", "10", "15", "20", "25", "30", "35", "40", "45", "50",
                "55", "60", "65", "70", "75", "80", "85", "90", "95", "100"
            ),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_gun_input",
            displayName = "Light Gun Input",
            values = listOf("lightgun", "touchscreen"),
            defaultValue = "lightgun",
            description = "Selects the input device used for light gun games"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_gun_cursor",
            displayName = "Show Light Gun Crosshair",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_invert_mouse",
            displayName = "Invert Mouse Y-Axis",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_no_sprite_limit",
            displayName = "Remove Per-Line Sprite Limit",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Removes the per-scanline sprite limit to eliminate flickering"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_enhanced_vscroll",
            displayName = "Enhanced Per-Tile Vertical Scroll",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Enables more accurate per-tile vertical scrolling emulation"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_enhanced_vscroll_limit",
            displayName = "Enhanced Per-Tile Vertical Scroll Limit",
            values = listOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16"),
            defaultValue = "8",
            description = "Sets the maximum tile offset for enhanced vertical scroll emulation"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_overclock",
            displayName = "CPU Speed",
            values = listOf(
                "100%", "125%", "150%", "175%", "200%", "225%", "250%", "275%",
                "300%", "325%", "350%", "375%", "400%", "425%", "450%", "475%", "500%"
            ),
            defaultValue = "100%",
            description = "Overclocks the emulated CPU to reduce slowdown in demanding games"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_force_dtack",
            displayName = "System Lock-Ups",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Emulates hardware lock-ups from illegal memory access for accuracy"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_addr_error",
            displayName = "68K Address Error",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Emulates 68000 CPU address error exceptions for accuracy"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_cd_latency",
            displayName = "CD Access Time",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Emulates realistic CD seek and access delays"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_cd_precache",
            displayName = "CD Image Cache",
            values = listOf("disabled", "enabled"),
            defaultValue = "disabled",
            description = "Loads the entire CD image into memory for faster access"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_show_advanced_audio_settings",
            displayName = "Show Advanced Audio Volume Settings",
            values = listOf("enabled", "disabled"),
            defaultValue = "disabled",
            description = "Reveals per-channel volume controls for audio mixing"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_psg_channel_0_volume",
            displayName = "PSG Tone Channel 0 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_psg_channel_1_volume",
            displayName = "PSG Tone Channel 1 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_psg_channel_2_volume",
            displayName = "PSG Tone Channel 2 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_psg_channel_3_volume",
            displayName = "PSG Noise Channel 3 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_0_volume",
            displayName = "Mega Drive/Genesis FM Channel 0 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_1_volume",
            displayName = "Mega Drive/Genesis FM Channel 1 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_2_volume",
            displayName = "Mega Drive/Genesis FM Channel 2 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_3_volume",
            displayName = "Mega Drive/Genesis FM Channel 3 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_4_volume",
            displayName = "Mega Drive/Genesis FM Channel 4 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_md_channel_5_volume",
            displayName = "Mega Drive/Genesis FM Channel 5 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_0_volume",
            displayName = "Master System FM Channel 0 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_1_volume",
            displayName = "Master System FM Channel 1 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_2_volume",
            displayName = "Master System FM Channel 2 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_3_volume",
            displayName = "Master System FM Channel 3 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_4_volume",
            displayName = "Master System FM Channel 4 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_5_volume",
            displayName = "Master System FM Channel 5 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_6_volume",
            displayName = "Master System FM Channel 6 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_7_volume",
            displayName = "Master System FM Channel 7 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
        CoreOptionDef(
            key = "genesis_plus_gx_sms_fm_channel_8_volume",
            displayName = "Master System FM Channel 8 Volume %",
            values = listOf("0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100"),
            defaultValue = "100"
        ),
    )
}
