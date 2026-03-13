package com.nendo.argosy.libretro.coreoptions.manifests

import com.nendo.argosy.libretro.coreoptions.CoreOptionDef
import com.nendo.argosy.libretro.coreoptions.CoreOptionManifest

object Cap32Manifest : CoreOptionManifest {
    override val coreId = "cap32"
    override val options = listOf(
        CoreOptionDef(
            key = "cap32_autorun",
            displayName = "Autorun",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Automatically load and run the first program on the inserted disk"
        ),
        CoreOptionDef(
            key = "cap32_combokey",
            displayName = "Combo Key",
            values = listOf("select", "y", "b", "disabled"),
            defaultValue = "select",
            description = "Sets which gamepad button opens the virtual keyboard"
        ),
        CoreOptionDef(
            key = "cap32_resolution",
            displayName = "Internal Resolution",
            values = listOf("384x272", "400x300"),
            defaultValue = "384x272",
            description = "Sets the emulated screen resolution"
        ),
        CoreOptionDef(
            key = "cap32_model",
            displayName = "Model",
            values = listOf("6128", "464", "6128+"),
            defaultValue = "6128",
            description = "Selects which Amstrad CPC model to emulate"
        ),
        CoreOptionDef(
            key = "cap32_ram",
            displayName = "RAM Size",
            values = listOf("128", "64", "192", "576"),
            defaultValue = "128",
            description = "Sets the amount of emulated RAM in kilobytes"
        ),
        CoreOptionDef(
            key = "cap32_statusbar",
            displayName = "Status Bar",
            values = listOf("onloading", "enabled", "disabled"),
            defaultValue = "onloading",
            description = "Controls when the disk activity status bar is shown"
        ),
        CoreOptionDef(
            key = "cap32_floppy_sound",
            displayName = "Floppy Sound",
            values = listOf("enabled", "disabled"),
            defaultValue = "enabled",
            description = "Plays floppy disk drive sound effects during disk access"
        ),
        CoreOptionDef(
            key = "cap32_scr_tube",
            displayName = "Monitor Type",
            values = listOf("color", "green", "white"),
            defaultValue = "color",
            description = "Simulates a color, green phosphor, or white phosphor monitor"
        ),
        CoreOptionDef(
            key = "cap32_scr_intensity",
            displayName = "Monitor Intensity",
            values = listOf("5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15"),
            defaultValue = "5",
            description = "Adjusts the brightness level of the emulated monitor"
        ),
        CoreOptionDef(
            key = "cap32_lang_layout",
            displayName = "CPC Language",
            values = listOf("english", "french", "spanish"),
            defaultValue = "english",
            description = "Sets the keyboard layout language for the emulated CPC"
        ),
        CoreOptionDef(
            key = "cap32_retrojoy0",
            displayName = "User 1 Joystick Configuration",
            values = listOf("joystick", "qaop", "incentive"),
            defaultValue = "joystick",
            description = "Selects the keyboard-to-joystick mapping scheme for player 1"
        ),
        CoreOptionDef(
            key = "cap32_retrojoy1",
            displayName = "User 2 Joystick Configuration",
            values = listOf("joystick", "qaop", "incentive", "joystick_port2"),
            defaultValue = "joystick",
            description = "Selects the keyboard-to-joystick mapping scheme for player 2"
        ),
    )
}
