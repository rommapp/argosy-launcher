package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object FuseManifest : CoreOptionManifest {
    override val coreId = "fuse"
    override val options = listOf(
        CoreOptionDef(
            key = "fuse_machine",
            displayName = "Model",
            values = listOf(
                "Spectrum 48K", "Spectrum 48K (NTSC)", "Spectrum 128K",
                "Spectrum +2", "Spectrum +2A", "Spectrum +3", "Spectrum +3e",
                "Spectrum SE", "Timex TC2048", "Timex TC2068", "Timex TS2068",
                "Spectrum 16K", "Pentagon 128K", "Pentagon 512K", "Pentagon 1024",
                "Scorpion 256K"
            ),
            defaultValue = "Spectrum 48K",
            description = "Selects which ZX Spectrum hardware model to emulate"
        ),
        CoreOptionDef(
            key = "fuse_hide_border",
            displayName = "Hide Video Border",
            values = listOf("Off", "On"),
            defaultValue = "Off",
            description = "Hides the colored border area around the main display"
        ),
        CoreOptionDef(
            key = "fuse_fast_load",
            displayName = "Tape Fast Load",
            values = listOf("Off", "On"),
            defaultValue = "On",
            description = "Accelerates tape loading to skip real-time wait"
        ),
        CoreOptionDef(
            key = "fuse_load_sound",
            displayName = "Tape Load Sound",
            values = listOf("Off", "On"),
            defaultValue = "On",
            description = "Plays the cassette loading sounds during tape operations"
        ),
        CoreOptionDef(
            key = "fuse_speaker_type",
            displayName = "Speaker Type",
            values = listOf("tv speaker", "beeper", "unfiltered"),
            defaultValue = "tv speaker",
            description = "Selects the audio output filter to simulate different speakers"
        ),
        CoreOptionDef(
            key = "fuse_ay_stereo_separation",
            displayName = "AY Stereo Separation",
            values = listOf("none", "acb", "abc"),
            defaultValue = "none",
            description = "Sets the stereo channel layout for the AY sound chip"
        ),
        CoreOptionDef(
            key = "fuse_key_ovrlay_transp",
            displayName = "Transparent Keyboard Overlay",
            values = listOf("Off", "On"),
            defaultValue = "On",
            description = "Makes the on-screen keyboard overlay semi-transparent"
        ),
        CoreOptionDef(
            key = "fuse_key_hold_time",
            displayName = "Time to Release Key in ms",
            values = listOf("100", "300", "500", "1000"),
            defaultValue = "500",
            description = "Sets how long a virtual key press is held before releasing"
        ),
    )
}
