package com.nendo.argosy.data.repository

import android.view.KeyEvent
import com.nendo.argosy.core.input.DetectedLayout
import com.nendo.argosy.data.platform.PlatformDefinitions

object RetroButton {
    const val B = 0
    const val Y = 1
    const val SELECT = 2
    const val START = 3
    const val UP = 4
    const val DOWN = 5
    const val LEFT = 6
    const val RIGHT = 7
    const val A = 8
    const val X = 9
    const val L = 10
    const val R = 11
    const val L2 = 12
    const val R2 = 13
    const val L3 = 14
    const val R3 = 15
}

data class MappingPlatform(
    val id: String,
    val displayName: String,
    val buttons: List<Int>,
    val buttonLabels: Map<Int, String> = emptyMap(),
    /**
     * Buttons that retain gameplay priority over a single-button hotkey. Controls that only
     * duplicate another input route or affect presentation can remain exposed in [buttons]
     * without shadowing an explicitly configured hotkey.
     */
    val hotkeyBlockingButtons: Set<Int> = buttons.toSet()
)

object MappingPlatforms {
    private val DPAD = listOf(RetroButton.UP, RetroButton.DOWN, RetroButton.LEFT, RetroButton.RIGHT)

    val UNIVERSAL = MappingPlatform(
        id = "universal",
        displayName = "Universal",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val NES = MappingPlatform(
        id = "nes",
        displayName = "NES",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val SMS = MappingPlatform(
        id = "sms",
        displayName = "Master System",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "1",
            RetroButton.A to "2",
            RetroButton.START to "Start/Pause"
        )
    )

    val ATARI_7800 = MappingPlatform(
        id = "atari-7800",
        displayName = "Atari 7800",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.X,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "1",
            RetroButton.A to "2",
            RetroButton.X to "Console Reset",
            RetroButton.L to "Left Difficulty",
            RetroButton.R to "Right Difficulty",
            RetroButton.START to "Console Pause",
            RetroButton.SELECT to "Console Select"
        )
    )

    val LYNX = MappingPlatform(
        id = "lynx",
        displayName = "Lynx",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L to "Option 1",
            RetroButton.R to "Option 2",
            RetroButton.START to "Pause",
            RetroButton.SELECT to "Rotate Screen"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.B, RetroButton.A,
            RetroButton.L, RetroButton.R,
            RetroButton.START
        ) + DPAD
    )

    val NGP = MappingPlatform(
        id = "ngp",
        displayName = "Neo Geo Pocket",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A",
            RetroButton.A to "B",
            RetroButton.START to "Option"
        )
    )

    val WONDERSWAN = MappingPlatform(
        id = "wonderswan",
        displayName = "WonderSwan",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L to "Y Cursor Left",
            RetroButton.R to "Y Cursor Right",
            RetroButton.R2 to "Y Cursor Up",
            RetroButton.L2 to "Y Cursor Down",
            RetroButton.SELECT to "Rotate Screen"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START
        ) + DPAD
    )

    val VIRTUALBOY = MappingPlatform(
        id = "vb",
        displayName = "Virtual Boy",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.X,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.X to "Low Battery Toggle",
            RetroButton.R2 to "Right Pad Left",
            RetroButton.L2 to "Right Pad Up",
            RetroButton.L3 to "Right Pad Down",
            RetroButton.R3 to "Right Pad Right"
        )
    )

    val PSP = MappingPlatform(
        id = "psp",
        displayName = "PSP",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Cross",
            RetroButton.A to "Circle",
            RetroButton.Y to "Square",
            RetroButton.X to "Triangle"
        )
    )

    val GB = MappingPlatform(
        id = "gb",
        displayName = "Game Boy",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val SNES = MappingPlatform(
        id = "snes",
        displayName = "SNES",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val GBA = MappingPlatform(
        id = "gba",
        displayName = "GBA",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R,
            RetroButton.X, RetroButton.Y, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.X to "Turbo A",
            RetroButton.Y to "Turbo B",
            RetroButton.L2 to "Turbo L",
            RetroButton.R2 to "Turbo R",
            RetroButton.L3 to "Darken Solar Sensor",
            RetroButton.R3 to "Brighten Solar Sensor"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val N64 = MappingPlatform(
        id = "n64",
        displayName = "N64",
        buttons = listOf(
            RetroButton.B, RetroButton.Y, RetroButton.A, RetroButton.X,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A / C-Down (C mode)",
            RetroButton.Y to "B / C-Left (C mode)",
            RetroButton.L to "L",
            RetroButton.R to "R",
            RetroButton.L2 to "Z",
            RetroButton.R2 to "C Buttons (hold)",
            RetroButton.A to "C-Right (C mode)",
            RetroButton.X to "C-Up (C mode)"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.B, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2,
            RetroButton.START
        ) + DPAD
    )

    val PSX = MappingPlatform(
        id = "psx",
        displayName = "PlayStation",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Cross",
            RetroButton.A to "Circle",
            RetroButton.Y to "Square",
            RetroButton.X to "Triangle"
        )
    )

    val GENESIS = MappingPlatform(
        id = "genesis",
        displayName = "Genesis",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.Y,
            RetroButton.X, RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.Y to "A",
            RetroButton.B to "B",
            RetroButton.A to "C",
            RetroButton.L to "X",
            RetroButton.X to "Y",
            RetroButton.R to "Z",
            RetroButton.SELECT to "Mode"
        )
    )

    val THREEDO = MappingPlatform(
        id = "3do",
        displayName = "3DO",
        buttons = listOf(
            RetroButton.Y, RetroButton.B, RetroButton.A,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.Y to "A",
            RetroButton.B to "B",
            RetroButton.A to "C",
            RetroButton.L to "L",
            RetroButton.R to "R",
            RetroButton.START to "P (Play/Pause)",
            RetroButton.SELECT to "X (Stop)"
        )
    )

    val SATURN = MappingPlatform(
        id = "saturn",
        displayName = "Saturn",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.R,
            RetroButton.Y, RetroButton.X, RetroButton.L,
            RetroButton.L2, RetroButton.R2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A",
            RetroButton.A to "B",
            RetroButton.R to "C",
            RetroButton.Y to "X",
            RetroButton.X to "Y",
            RetroButton.L to "Z",
            RetroButton.L2 to "L",
            RetroButton.R2 to "R",
            RetroButton.SELECT to "Mode"
        )
    )

    private val ARCADE_BUTTONS = listOf(
        RetroButton.B, RetroButton.A, RetroButton.Y, RetroButton.X,
        RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
        RetroButton.L3, RetroButton.R3,
        RetroButton.START, RetroButton.SELECT
    ) + DPAD

    /**
     * FBNeo and MAME both default to their "Classic" pad but number it differently from button 5
     * onward, so the two cores cannot share one profile. Keeps the original id so existing arcade
     * remaps still resolve for FBNeo, which is the default core for the generic arcade slugs.
     */
    val ARCADE6 = MappingPlatform(
        id = "arcade6",
        displayName = "Arcade (FBNeo)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.B to "Button 1",
            RetroButton.A to "Button 2",
            RetroButton.Y to "Button 3",
            RetroButton.X to "Button 4",
            RetroButton.R to "Button 5",
            RetroButton.L to "Button 6",
            RetroButton.R2 to "Button 7",
            RetroButton.L2 to "Button 8",
            RetroButton.R3 to "Button 9",
            RetroButton.L3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_6PANEL = MappingPlatform(
        id = "arcade-6panel",
        displayName = "Arcade (6-Button Panel)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.Y to "Button 1",
            RetroButton.X to "Button 2",
            RetroButton.L to "Button 3",
            RetroButton.B to "Button 4",
            RetroButton.A to "Button 5",
            RetroButton.R to "Button 6",
            RetroButton.R2 to "Button 7",
            RetroButton.L2 to "Button 8",
            RetroButton.R3 to "Button 9",
            RetroButton.L3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_MODERN = MappingPlatform(
        id = "arcade-modern",
        displayName = "Arcade (Modern)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.B to "Button 1",
            RetroButton.A to "Button 2",
            RetroButton.Y to "Button 3",
            RetroButton.X to "Button 4",
            RetroButton.R2 to "Button 5",
            RetroButton.R to "Button 6",
            RetroButton.L2 to "Button 7",
            RetroButton.L to "Button 8",
            RetroButton.R3 to "Button 9",
            RetroButton.L3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_MAME = MappingPlatform(
        id = "arcade-mame",
        displayName = "Arcade (MAME)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.B to "Button 1",
            RetroButton.A to "Button 2",
            RetroButton.Y to "Button 3",
            RetroButton.X to "Button 4",
            RetroButton.L to "Button 5",
            RetroButton.R to "Button 6",
            RetroButton.L2 to "Button 7",
            RetroButton.R2 to "Button 8",
            RetroButton.L3 to "Button 9",
            RetroButton.R3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_MAME_FIGHTSTICK = MappingPlatform(
        id = "arcade-mame-fightstick",
        displayName = "Arcade MAME (Fightstick)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.Y to "Button 1",
            RetroButton.X to "Button 2",
            RetroButton.R to "Button 3",
            RetroButton.B to "Button 4",
            RetroButton.A to "Button 5",
            RetroButton.R2 to "Button 6",
            RetroButton.L to "Button 7",
            RetroButton.L2 to "Button 8",
            RetroButton.L3 to "Button 9",
            RetroButton.R3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_MAME_8BUTTON = MappingPlatform(
        id = "arcade-mame-8button",
        displayName = "Arcade MAME (8-Button)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.Y to "Button 1",
            RetroButton.X to "Button 2",
            RetroButton.L to "Button 3",
            RetroButton.B to "Button 4",
            RetroButton.A to "Button 5",
            RetroButton.L2 to "Button 6",
            RetroButton.R to "Button 7",
            RetroButton.R2 to "Button 8",
            RetroButton.L3 to "Button 9",
            RetroButton.R3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    val ARCADE_MAME_6BUTTON = MappingPlatform(
        id = "arcade-mame-6button",
        displayName = "Arcade MAME (6-Button)",
        buttons = ARCADE_BUTTONS,
        buttonLabels = mapOf(
            RetroButton.Y to "Button 1",
            RetroButton.X to "Button 2",
            RetroButton.L to "Button 3",
            RetroButton.B to "Button 4",
            RetroButton.A to "Button 5",
            RetroButton.R to "Button 6",
            RetroButton.L2 to "Button 7",
            RetroButton.R2 to "Button 8",
            RetroButton.L3 to "Button 9",
            RetroButton.R3 to "Button 10",
            RetroButton.SELECT to "Coin"
        )
    )

    /**
     * The 3D Control Pad drives L and R as analog triggers and claims Select as its mode switch, so
     * the digital L2/R2 the standard pad uses for the shoulder buttons are absent here.
     */
    val SATURN_3D = MappingPlatform(
        id = "saturn-3d",
        displayName = "Saturn 3D Control Pad",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.R,
            RetroButton.Y, RetroButton.X, RetroButton.L,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A",
            RetroButton.A to "B",
            RetroButton.R to "C",
            RetroButton.Y to "X",
            RetroButton.X to "Y",
            RetroButton.L to "Z",
            RetroButton.SELECT to "Mode"
        )
    )

    val VECTREX = MappingPlatform(
        id = "vectrex",
        displayName = "Vectrex",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "1",
            RetroButton.B to "2",
            RetroButton.X to "3",
            RetroButton.Y to "4"
        )
    )

    val INTV = MappingPlatform(
        id = "intv",
        displayName = "Intellivision",
        buttons = listOf(
            RetroButton.Y, RetroButton.B, RetroButton.A, RetroButton.X,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.Y to "Top",
            RetroButton.A to "Left",
            RetroButton.B to "Right",
            RetroButton.X to "Last KP",
            RetroButton.L to "Mini KP",
            RetroButton.R to "Mini KP",
            RetroButton.L2 to "Clear",
            RetroButton.R2 to "Enter",
            RetroButton.L3 to "KP 0",
            RetroButton.R3 to "KP 5",
            RetroButton.START to "Pause",
            RetroButton.SELECT to "Swap"
        )
    )

    val ATARI_SINGLE = MappingPlatform(
        id = "atari-single",
        displayName = "Atari 2600",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Fire",
            RetroButton.A to "Trigger",
            RetroButton.Y to "Booster",
            RetroButton.L to "Left Difficulty A",
            RetroButton.R to "Right Difficulty A",
            RetroButton.L2 to "Left Difficulty B",
            RetroButton.R2 to "Right Difficulty B",
            RetroButton.L3 to "Color",
            RetroButton.R3 to "Black/White",
            RetroButton.START to "Reset",
            RetroButton.SELECT to "Select"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.B, RetroButton.A, RetroButton.Y,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val ATARI_5200 = MappingPlatform(
        id = "atari-5200",
        displayName = "Atari 5200",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "Fire 1",
            RetroButton.B to "Fire 2",
            RetroButton.X to "Keypad #",
            RetroButton.Y to "Keypad *",
            RetroButton.L to "Show/Hide OSK",
            RetroButton.R to "Keypad 0",
            RetroButton.L2 to "Keypad 3",
            RetroButton.R2 to "Keypad 1",
            RetroButton.L3 to "Keypad 7",
            RetroButton.R3 to "Keypad 5",
            RetroButton.SELECT to "Pause"
        )
    )

    val DS = MappingPlatform(
        id = "nds",
        displayName = "Nintendo DS",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L2 to "Microphone",
            RetroButton.R2 to "Swap Screens",
            RetroButton.L3 to "Close Lid",
            RetroButton.R3 to "Touch Joystick"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val THREEDS = MappingPlatform(
        id = "3ds",
        displayName = "Nintendo 3DS",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L2 to "ZL",
            RetroButton.R2 to "ZR",
            RetroButton.L3 to "Swap Screens",
            RetroButton.R3 to "Tap Touch Screen"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val GAMECUBE = MappingPlatform(
        id = "gamecube",
        displayName = "GameCube",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L2, RetroButton.R2, RetroButton.R,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L2 to "L",
            RetroButton.R2 to "R",
            RetroButton.R to "Z",
            RetroButton.L3 to "L Analog",
            RetroButton.R3 to "R Analog",
            RetroButton.START to "Start"
        )
    )

    val DREAMCAST = MappingPlatform(
        id = "dreamcast",
        displayName = "Dreamcast",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.Y, RetroButton.X,
            RetroButton.L2, RetroButton.R2,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A",
            RetroButton.A to "B",
            RetroButton.Y to "X",
            RetroButton.X to "Y",
            RetroButton.L2 to "L Trigger",
            RetroButton.R2 to "R Trigger"
        )
    )

    /**
     * Dolphin advertises the bare Wiimote first, and a port takes the first device its core
     * advertises, so this is the device a Wii game runs with unless the user stored an override.
     */
    val WII = MappingPlatform(
        id = "wii",
        displayName = "Wii",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.R2, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.X to "1",
            RetroButton.Y to "2",
            RetroButton.R2 to "Shake Wiimote",
            RetroButton.R3 to "Home",
            RetroButton.START to "+",
            RetroButton.SELECT to "-"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val WII_NUNCHUK = MappingPlatform(
        id = "wii-nunchuk",
        displayName = "Wii Nunchuk",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.X to "C",
            RetroButton.Y to "Z",
            RetroButton.L to "-",
            RetroButton.R to "+",
            RetroButton.L2 to "Shake Nunchuk",
            RetroButton.R2 to "Shake Wiimote",
            RetroButton.R3 to "Home",
            RetroButton.START to "1",
            RetroButton.SELECT to "2"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val WII_CLASSIC = MappingPlatform(
        id = "wii-classic",
        displayName = "Wii Classic",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L to "ZL",
            RetroButton.R to "ZR",
            RetroButton.L2 to "L",
            RetroButton.R2 to "R",
            RetroButton.R3 to "Home",
            RetroButton.START to "+",
            RetroButton.SELECT to "-"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val WII_CLASSIC_PRO = MappingPlatform(
        id = "wii-classic-pro",
        displayName = "Wii Classic Pro",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L2 to "ZL",
            RetroButton.R2 to "ZR",
            RetroButton.R3 to "Home",
            RetroButton.START to "+",
            RetroButton.SELECT to "-"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val PCE = MappingPlatform(
        id = "pce",
        displayName = "PC Engine",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.Y, RetroButton.X,
            RetroButton.L, RetroButton.R, RetroButton.L2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "I",
            RetroButton.B to "II",
            RetroButton.Y to "III",
            RetroButton.X to "IV",
            RetroButton.L to "V",
            RetroButton.R to "VI",
            RetroButton.L2 to "Mode Switch",
            RetroButton.START to "Run"
        )
    )

    val PCFX = MappingPlatform(
        id = "pcfx",
        displayName = "PC-FX",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "I",
            RetroButton.B to "II",
            RetroButton.X to "III",
            RetroButton.Y to "IV",
            RetroButton.L to "V",
            RetroButton.R to "VI",
            RetroButton.L2 to "Mode 1 (Switch)",
            RetroButton.R2 to "Mode 2 (Switch)",
            RetroButton.START to "Run"
        )
    )

    val JAGUAR = MappingPlatform(
        id = "jaguar",
        displayName = "Jaguar",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.Y, RetroButton.X,
            RetroButton.L, RetroButton.R, RetroButton.L2, RetroButton.R2,
            RetroButton.L3, RetroButton.R3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "A",
            RetroButton.B to "B",
            RetroButton.Y to "C",
            RetroButton.X to "Numpad 0",
            RetroButton.L to "Numpad 1",
            RetroButton.R to "Numpad 2",
            RetroButton.L2 to "Numpad 3",
            RetroButton.R2 to "Numpad 4",
            RetroButton.L3 to "Numpad 5",
            RetroButton.R3 to "Numpad 6",
            RetroButton.START to "Option",
            RetroButton.SELECT to "Pause"
        )
    )

    val CDI = MappingPlatform(
        id = "cdi",
        displayName = "CD-i",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "Button 1",
            RetroButton.B to "Button 2",
            RetroButton.X to "Button 3"
        )
    )

    val NEOGEO = MappingPlatform(
        id = "neogeo",
        displayName = "Neo Geo",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.Y, RetroButton.X,
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "A",
            RetroButton.A to "B",
            RetroButton.Y to "C",
            RetroButton.X to "D"
        )
    )

    val COMPUTER = MappingPlatform(
        id = "computer",
        displayName = "Computer",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val C64 = MappingPlatform(
        id = "c64",
        displayName = "Commodore 64",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Fire"
        )
    )

    val AMIGA = MappingPlatform(
        id = "amiga",
        displayName = "Amiga",
        buttons = listOf(
            RetroButton.B, RetroButton.A,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Fire / Red",
            RetroButton.A to "2nd Fire / Blue"
        )
    )

    val MSX = MappingPlatform(
        id = "msx",
        displayName = "MSX",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.Y, RetroButton.X,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "1",
            RetroButton.B to "2",
            RetroButton.Y to "3",
            RetroButton.X to "4",
            RetroButton.START to "5",
            RetroButton.SELECT to "6"
        )
    )

    val COLECO = MappingPlatform(
        id = "coleco",
        displayName = "ColecoVision",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.X, RetroButton.Y,
            RetroButton.R, RetroButton.L, RetroButton.R2, RetroButton.L2,
            RetroButton.R3, RetroButton.L3,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "Button 1",
            RetroButton.B to "Button 2",
            RetroButton.X to "Keypad 1",
            RetroButton.Y to "Keypad 2",
            RetroButton.R to "Keypad 3",
            RetroButton.L to "Keypad 4",
            RetroButton.R2 to "Keypad 5",
            RetroButton.L2 to "Keypad 6",
            RetroButton.R3 to "Keypad 7",
            RetroButton.L3 to "Keypad 8",
            RetroButton.START to "Keypad #",
            RetroButton.SELECT to "Keypad *"
        )
    )

    val ZX = MappingPlatform(
        id = "zx",
        displayName = "ZX Spectrum",
        buttons = listOf(
            RetroButton.A, RetroButton.X, RetroButton.Y, RetroButton.B,
            RetroButton.L, RetroButton.R, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "Fire",
            RetroButton.X to "Fire",
            RetroButton.Y to "Fire",
            RetroButton.B to "Up",
            RetroButton.L to "Enter",
            RetroButton.R to "Space",
            RetroButton.SELECT to "Keyboard Overlay"
        )
    )

    val ODYSSEY2 = MappingPlatform(
        id = "odyssey2",
        displayName = "Odyssey 2",
        buttons = listOf(
            RetroButton.B, RetroButton.Y,
            RetroButton.X, RetroButton.L, RetroButton.R,
            RetroButton.L2, RetroButton.R2, RetroButton.L3, RetroButton.R3,
            RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Action",
            RetroButton.Y to "Move Keyboard",
            RetroButton.SELECT to "Show/Hide Keyboard",
            RetroButton.X to "Keypad 0",
            RetroButton.L to "Keypad 1",
            RetroButton.R to "Keypad 2",
            RetroButton.L2 to "Keypad 3",
            RetroButton.R2 to "Keypad 4",
            RetroButton.L3 to "Keypad 5",
            RetroButton.R3 to "Keypad 6"
        )
    )

    val CHANNELF = MappingPlatform(
        id = "channelf",
        displayName = "Channel F",
        buttons = listOf(
            RetroButton.B, RetroButton.A, RetroButton.X, RetroButton.Y,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.B to "Push",
            RetroButton.A to "Rotate Right",
            RetroButton.X to "Pull",
            RetroButton.Y to "Rotate Left",
            RetroButton.START to "Swap Console/Controller",
            RetroButton.SELECT to "Swap Controllers"
        )
    )

    val POKEMINI = MappingPlatform(
        id = "pokemini",
        displayName = "Pokemon Mini",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.R,
            RetroButton.L, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.R to "C",
            RetroButton.L to "Shake",
            RetroButton.SELECT to "Power"
        )
    )

    val FDS = MappingPlatform(
        id = "fds",
        displayName = "Famicom Disk System",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L to "Disk Side Change",
            RetroButton.R to "Insert/Eject Disk"
        ),
        hotkeyBlockingButtons = setOf(
            RetroButton.A, RetroButton.B,
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val PICO8 = MappingPlatform(
        id = "pico8",
        displayName = "PICO-8",
        buttons = listOf(RetroButton.A, RetroButton.B) + DPAD,
        buttonLabels = mapOf(
            RetroButton.A to "O",
            RetroButton.B to "X"
        )
    )

    val ALL = listOf(
        UNIVERSAL, NES, SMS, GB, SNES, GBA, N64, PSX, PSP, GENESIS, THREEDO,
        SATURN, DREAMCAST, ARCADE6, VECTREX, INTV, ATARI_SINGLE, ATARI_5200,
        ATARI_7800, LYNX, NGP, WONDERSWAN, VIRTUALBOY,
        DS, GAMECUBE, WII, WII_NUNCHUK, WII_CLASSIC, WII_CLASSIC_PRO, PCE, NEOGEO, COMPUTER,
        C64, AMIGA, MSX, COLECO, ZX, ODYSSEY2, CHANNELF, POKEMINI, FDS, PICO8,
        ARCADE_MAME, ARCADE_6PANEL, ARCADE_MODERN,
        ARCADE_MAME_FIGHTSTICK, ARCADE_MAME_8BUTTON, ARCADE_MAME_6BUTTON,
        SATURN_3D, PCFX, JAGUAR, CDI
    )

    fun getByIndex(index: Int): MappingPlatform = ALL[index.coerceIn(0, ALL.lastIndex)]

    fun getNextIndex(currentIndex: Int): Int = (currentIndex + 1) % ALL.size

    fun getPrevIndex(currentIndex: Int): Int = if (currentIndex <= 0) ALL.lastIndex else currentIndex - 1

    fun dbPlatformId(platformIndex: Int): String? {
        val platform = getByIndex(platformIndex)
        return if (platform.id == "universal") null else platform.id
    }

    fun dbPlatformIdForSlug(slug: String): String? = dbPlatformId(indexForPlatformSlug(slug))

    fun indexForPlatformSlug(slug: String): Int = ALL.indexOf(profileForSlug(slug))

    fun profileForSlug(slug: String): MappingPlatform = when (PlatformDefinitions.getCanonicalSlug(slug).lowercase()) {
        "atari2600" -> ATARI_SINGLE
        "atari5200" -> ATARI_5200
        "atari7800" -> ATARI_7800
        "lynx" -> LYNX
        "ngp", "ngpc" -> NGP
        "wonderswan", "wsc" -> WONDERSWAN
        "vectrex" -> VECTREX
        "intellivision" -> INTV
        "saturn" -> SATURN
        "dreamcast" -> DREAMCAST
        "mame" -> ARCADE_MAME

        "arcade", "fbneo", "cps1", "cps2", "cps3", "neogeocd" -> ARCADE6

        "nes", "gameandwatch",
        "megaduck", "supervision", "arduboy", "uzebox" -> NES

        "fds" -> FDS

        "coleco" -> COLECO

        "odyssey2" -> ODYSSEY2

        "channelf" -> CHANNELF

        "pokemini" -> POKEMINI

        "pico8" -> PICO8

        "sg1000", "sms", "gg" -> SMS

        "gb", "gbc" -> GB

        "gba" -> GBA

        "snes", "satellaview" -> SNES

        "vb" -> VIRTUALBOY

        "tg16", "pce", "turbografx16", "pcengine",
        "supergrafx", "tgcd" -> PCE

        "pcfx" -> PCFX

        "jaguar" -> JAGUAR

        "cdi" -> CDI

        "n64", "n64dd" -> N64

        "psx", "ps2" -> PSX

        "psp", "vita" -> PSP

        "nds", "dsi" -> DS

        "3ds", "n3ds" -> THREEDS

        "gc", "ngc", "gamecube" -> GAMECUBE

        "wii" -> WII

        "3do" -> THREEDO

        "genesis", "scd", "32x", "pico" -> GENESIS

        "neogeo" -> NEOGEO

        "c64" -> C64

        "amiga", "amigacd32", "cdtv" -> AMIGA

        "msx", "msx2" -> MSX

        "zx" -> ZX

        "amstradcpc" -> COMPUTER

        else -> UNIVERSAL
    }
}

data class InputPreset(
    val name: String,
    val displayName: String,
    val mapping: Map<Int, Int>
)

object InputPresets {
    /**
     * Gamepad buttons a resolved mapping silences when it omits them. Any other key the user was
     * offered, a keyboard key for instance, keeps its default route to the core when unbound, so a
     * remapped controller does not take the keyboard away from a core that reads it.
     */
    val BINDABLE_KEYCODES: Set<Int> = setOf(
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_Z,
        KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_MODE,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT
    ) + (KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16)

    private val RESERVED_KEYCODES: Set<Int> = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_BACK
    )

    /**
     * Whether a key may be recorded as a binding or a hotkey. Every key a physical device sends is
     * accepted except the two the system owns, so a controller's extra buttons and keyboard keys
     * all count without the app having to know them in advance.
     */
    fun isBindableKey(keyCode: Int): Boolean = keyCode !in RESERVED_KEYCODES

    private val DEFAULT_MAPPING = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to RetroButton.A,
        KeyEvent.KEYCODE_BUTTON_B to RetroButton.B,
        KeyEvent.KEYCODE_BUTTON_X to RetroButton.X,
        KeyEvent.KEYCODE_BUTTON_Y to RetroButton.Y,
        KeyEvent.KEYCODE_BUTTON_START to RetroButton.START,
        KeyEvent.KEYCODE_BUTTON_SELECT to RetroButton.SELECT,
        KeyEvent.KEYCODE_BUTTON_L1 to RetroButton.L,
        KeyEvent.KEYCODE_BUTTON_R1 to RetroButton.R,
        KeyEvent.KEYCODE_BUTTON_L2 to RetroButton.L2,
        KeyEvent.KEYCODE_BUTTON_R2 to RetroButton.R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL to RetroButton.L3,
        KeyEvent.KEYCODE_BUTTON_THUMBR to RetroButton.R3,
        KeyEvent.KEYCODE_DPAD_UP to RetroButton.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to RetroButton.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to RetroButton.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to RetroButton.RIGHT
    )

    private val XBOX_LABEL_MAPPING = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to RetroButton.A,
        KeyEvent.KEYCODE_BUTTON_B to RetroButton.B,
        KeyEvent.KEYCODE_BUTTON_X to RetroButton.X,
        KeyEvent.KEYCODE_BUTTON_Y to RetroButton.Y,
        KeyEvent.KEYCODE_BUTTON_START to RetroButton.START,
        KeyEvent.KEYCODE_BUTTON_SELECT to RetroButton.SELECT,
        KeyEvent.KEYCODE_BUTTON_L1 to RetroButton.L,
        KeyEvent.KEYCODE_BUTTON_R1 to RetroButton.R,
        KeyEvent.KEYCODE_BUTTON_L2 to RetroButton.L2,
        KeyEvent.KEYCODE_BUTTON_R2 to RetroButton.R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL to RetroButton.L3,
        KeyEvent.KEYCODE_BUTTON_THUMBR to RetroButton.R3,
        KeyEvent.KEYCODE_DPAD_UP to RetroButton.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to RetroButton.DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT to RetroButton.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to RetroButton.RIGHT
    )

    val PRESETS = listOf(
        InputPreset(
            name = "DEFAULT",
            displayName = "Default (Position)",
            mapping = DEFAULT_MAPPING
        ),
        InputPreset(
            name = "NINTENDO",
            displayName = "Nintendo",
            mapping = DEFAULT_MAPPING
        ),
        InputPreset(
            name = "XBOX",
            displayName = "Xbox (Label)",
            mapping = XBOX_LABEL_MAPPING
        )
    )

    fun getPresetByName(name: String): InputPreset? =
        PRESETS.find { it.name.equals(name, ignoreCase = true) }

    fun getDefaultMappingForLayout(layout: DetectedLayout): Map<Int, Int> {
        return when (layout) {
            DetectedLayout.NINTENDO -> DEFAULT_MAPPING
            DetectedLayout.XBOX -> DEFAULT_MAPPING
        }
    }

    /**
     * Whether the default mapping sends [keyCode] to a gameplay-priority button on this platform.
     * The hotkey editor has no per-controller mapping context, so its warning reflects this
     * default-layout signal; runtime priority uses each controller's resolved mapping.
     */
    fun keyMapsToConsoleButton(keyCode: Int, platformSlug: String): Boolean {
        val retroButton = DEFAULT_MAPPING[keyCode] ?: return false
        return retroButton in MappingPlatforms.profileForSlug(platformSlug).hotkeyBlockingButtons
    }

    fun getPresetNamesForCycling(): List<String> = PRESETS.map { it.name }

    fun getNextPreset(currentPresetName: String?): InputPreset {
        if (currentPresetName == null) return PRESETS.first()
        val currentIndex = PRESETS.indexOfFirst { it.name == currentPresetName }
        val nextIndex = (currentIndex + 1) % PRESETS.size
        return PRESETS[nextIndex]
    }

    fun getPreviousPreset(currentPresetName: String?): InputPreset {
        if (currentPresetName == null) return PRESETS.last()
        val currentIndex = PRESETS.indexOfFirst { it.name == currentPresetName }
        val prevIndex = if (currentIndex <= 0) PRESETS.size - 1 else currentIndex - 1
        return PRESETS[prevIndex]
    }

    fun getRetroButtonName(retroButton: Int, platform: MappingPlatform? = null): String {
        platform?.buttonLabels?.get(retroButton)?.let { return it }
        return when (retroButton) {
            RetroButton.A -> "A"
            RetroButton.B -> "B"
            RetroButton.X -> "X"
            RetroButton.Y -> "Y"
            RetroButton.START -> "Start"
            RetroButton.SELECT -> "Select"
            RetroButton.L -> "L1"
            RetroButton.R -> "R1"
            RetroButton.L2 -> "L2"
            RetroButton.R2 -> "R2"
            RetroButton.L3 -> "L3"
            RetroButton.R3 -> "R3"
            RetroButton.UP -> "D-Pad Up"
            RetroButton.DOWN -> "D-Pad Down"
            RetroButton.LEFT -> "D-Pad Left"
            RetroButton.RIGHT -> "D-Pad Right"
            else -> "Unknown"
        }
    }

    fun getAndroidButtonName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "L2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "R2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "R3"
            KeyEvent.KEYCODE_DPAD_UP -> "D-Pad Up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "D-Pad Down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "D-Pad Left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "D-Pad Right"
            KeyEvent.KEYCODE_BACK -> "Back"
            KeyEvent.KEYCODE_BUTTON_MODE -> "Mode"
            in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 ->
                "B${keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1}"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        }
    }
}
