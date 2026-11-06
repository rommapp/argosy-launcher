package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object ScummvmManifest : CoreOptionManifest {
    override val coreId = "scummvm"
    override val options = listOf(
        CoreOptionDef(
            key = "scummvm_pointer_device",
            displayName = "Pointer Device",
            values = listOf("retropad", "mouse", "pointer"),
            defaultValue = "pointer",
            coreDefault = "mouse",
            description = "What moves the cursor besides the controller",
            valueLabels = mapOf(
                "retropad" to "Controller Only",
                "mouse" to "Controller + Mouse",
                "pointer" to "Controller + Touch"
            )
        ),
        CoreOptionDef(
            key = "scummvm_gamepad_cursor_speed",
            displayName = "Controller Cursor Speed",
            values = listOf("0.25", "0.5", "0.75", "1.0", "1.5", "2.0", "2.5", "3.0"),
            defaultValue = "1.0",
            description = "Cursor speed from the stick or d-pad; 2.0 suits 640x480 games"
        ),
        CoreOptionDef(
            key = "scummvm_gamepad_cursor_acceleration_time",
            displayName = "Controller Cursor Acceleration",
            values = listOf("off", "0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9", "1.0"),
            defaultValue = "0.2",
            description = "Seconds the cursor takes to reach full speed"
        ),
        CoreOptionDef(
            key = "scummvm_analog_deadzone",
            displayName = "Analog Deadzone",
            values = listOf("0", "5", "10", "15", "20", "25", "30"),
            defaultValue = "15",
            description = "Stick travel ignored before the cursor moves",
            valueLabels = mapOf(
                "0" to "0%", "5" to "5%", "10" to "10%", "15" to "15%",
                "20" to "20%", "25" to "25%", "30" to "30%"
            )
        ),
        CoreOptionDef(
            key = "scummvm_framerate",
            displayName = "Frame Rate Cap",
            values = listOf("disabled", "60 Hz", "50 Hz", "30 Hz", "25 Hz"),
            defaultValue = "disabled",
            description = "Lower caps ease load on slower devices; changing it resets the core",
            valueLabels = mapOf("disabled" to "Off")
        ),
        CoreOptionDef(
            key = "scummvm_browsing_mode",
            displayName = "File Browser",
            values = listOf("local", "authorized"),
            defaultValue = "local",
            coreDefault = "authorized",
            description = "Locations the ScummVM launcher's file browser lists",
            valueLabels = mapOf(
                "local" to "Local Filesystem",
                "authorized" to "Authorized Storage"
            )
        ),
    )
}
