package com.nendo.argosy.libretro

import com.nendo.argosy.data.platform.PlatformDefinitions

data class CorePortDevice(val id: Int, val name: String)

/**
 * Each built-in core's `RETRO_ENVIRONMENT_SET_CONTROLLER_INFO` devices per port, as the core's
 * upstream source declares them, for picking a controller type outside a running game. The first
 * device of a port is the one libretrodroid sets when nothing is stored. Ports past
 * [MAX_PORTS] are never configured.
 */
object CorePortDeviceCatalog {

    const val MAX_PORTS = 4

    private const val NONE = 0
    private const val JOYPAD = 1
    private const val MOUSE = 2
    private const val KEYBOARD = 3
    private const val LIGHTGUN = 4
    private const val ANALOG = 5
    private const val POINTER = 6

    private fun subclass(base: Int, id: Int): Int = ((id + 1) shl 8) or base

    private fun d(id: Int, name: String) = CorePortDevice(id, name)

    private fun same(ports: Int, devices: List<CorePortDevice>) = List(ports) { devices }

    private val DOLPHIN_WII = listOf(
        d(JOYPAD, "WiiMote (upright)"),
        d(subclass(JOYPAD, 1), "WiiMote (sideways)"),
        d(subclass(JOYPAD, 2), "WiiMote + Nunchuk"),
        d(subclass(JOYPAD, 3), "WiiMote + Classic Controller"),
        d(subclass(JOYPAD, 4), "WiiMote + Classic Controller Pro"),
        d(subclass(JOYPAD, 6), "WiiMote + MotionPlus"),
        d(subclass(JOYPAD, 7), "WiiMote + MotionPlus (sideways)"),
        d(subclass(JOYPAD, 8), "WiiMote + MotionPlus + Nunchuk"),
        d(subclass(JOYPAD, 9), "WiiMote + MotionPlus + Classic Controller"),
        d(subclass(JOYPAD, 10), "WiiMote + MotionPlus + Classic Controller Pro"),
        d(subclass(JOYPAD, 5), "GameCube Controller")
    )

    private val GENESIS_PLUS_GX_PORT0 = listOf(
        d(JOYPAD, "Joypad Auto"),
        d(NONE, "Joypad Port Empty"),
        d(subclass(JOYPAD, 0), "MD Joypad 3 Button"),
        d(subclass(JOYPAD, 1), "MD Joypad 6 Button"),
        d(subclass(JOYPAD, 2), "MS Joypad 2 Button"),
        d(subclass(JOYPAD, 3), "MD Joypad 3 Button + 4-WayPlay"),
        d(subclass(JOYPAD, 4), "MD Joypad 6 Button + 4-WayPlay"),
        d(subclass(JOYPAD, 5), "MD Joypad 3 Button + Teamplayer"),
        d(subclass(JOYPAD, 6), "MD Joypad 6 Button + Teamplayer"),
        d(subclass(JOYPAD, 7), "MS Joypad 2 Button + Master Tap"),
        d(subclass(LIGHTGUN, 0), "MS Light Phaser"),
        d(subclass(ANALOG, 0), "MS Paddle Control"),
        d(subclass(ANALOG, 1), "MS Sports Pad"),
        d(subclass(POINTER, 0), "MS Graphic Board"),
        d(subclass(ANALOG, 2), "MD XE-1AP"),
        d(MOUSE, "MD Mouse")
    )

    private val PCSX_REARMED = listOf(
        d(JOYPAD, "standard"),
        d(subclass(ANALOG, 1), "dualshock"),
        d(subclass(ANALOG, 0), "analog"),
        d(subclass(ANALOG, 2), "negcon"),
        d(subclass(LIGHTGUN, 0), "guncon"),
        d(subclass(LIGHTGUN, 1), "konami gun"),
        d(subclass(MOUSE, 0), "mouse")
    )

    private val BEETLE_PSX = listOf(
        d(JOYPAD, "PlayStation Controller"),
        d(subclass(ANALOG, 1), "DualShock"),
        d(subclass(ANALOG, 0), "Analog Controller"),
        d(subclass(ANALOG, 2), "Analog Joystick"),
        d(subclass(LIGHTGUN, 0), "Guncon / G-Con 45"),
        d(subclass(LIGHTGUN, 1), "Justifier"),
        d(subclass(MOUSE, 0), "Mouse"),
        d(subclass(ANALOG, 3), "neGcon"),
        d(subclass(ANALOG, 4), "neGcon Rumble")
    )

    private val BEETLE_SATURN = listOf(
        d(JOYPAD, "Control Pad"),
        d(subclass(ANALOG, 0), "3D Control Pad"),
        d(subclass(LIGHTGUN, 0), "Virtua Gun"),
        d(subclass(LIGHTGUN, 1), "Stunner"),
        d(subclass(MOUSE, 0), "Mouse"),
        d(subclass(ANALOG, 1), "Arcade Racer"),
        d(subclass(ANALOG, 2), "Mission Stick"),
        d(subclass(ANALOG, 3), "Dual Mission Sticks"),
        d(subclass(ANALOG, 4), "Twin-Stick"),
        d(subclass(KEYBOARD, 0), "Keyboard")
    )

    private val FLYCAST = listOf(
        d(JOYPAD, "Controller"),
        d(subclass(JOYPAD, 3), "Arcade Stick"),
        d(KEYBOARD, "Keyboard"),
        d(MOUSE, "Mouse"),
        d(LIGHTGUN, "Light Gun"),
        d(subclass(JOYPAD, 1), "Twin Stick"),
        d(subclass(JOYPAD, 2), "Saturn Twin-Stick"),
        d(POINTER, "Pointer"),
        d(subclass(JOYPAD, 4), "Maracas"),
        d(subclass(JOYPAD, 5), "Fishing Controller"),
        d(subclass(JOYPAD, 6), "Pop'n Music"),
        d(subclass(JOYPAD, 7), "Race Controller"),
        d(subclass(JOYPAD, 8), "Densha de Go!"),
        d(subclass(JOYPAD, 9), "Full Controller")
    )

    private val STELLA_PORT0 = listOf(
        d(JOYPAD, "Automatic (from ROM database)"),
        d(subclass(JOYPAD, 0), "Joystick"),
        d(subclass(JOYPAD, 1), "BoosterGrip"),
        d(subclass(JOYPAD, 2), "Genesis"),
        d(subclass(JOYPAD, 3), "Joy 2B+"),
        d(subclass(JOYPAD, 4), "Paddles"),
        d(subclass(JOYPAD, 5), "Driving"),
        d(subclass(JOYPAD, 6), "Keyboard"),
        d(subclass(JOYPAD, 7), "TrakBall"),
        d(subclass(JOYPAD, 8), "Amiga Mouse"),
        d(subclass(JOYPAD, 9), "Atari Mouse"),
        d(subclass(JOYPAD, 10), "Lightgun"),
        d(subclass(JOYPAD, 11), "QuadTari"),
        d(subclass(JOYPAD, 12), "MindLink"),
        d(subclass(JOYPAD, 13), "AtariVox"),
        d(subclass(JOYPAD, 14), "SaveKey"),
        d(subclass(JOYPAD, 15), "KidVid"),
        d(NONE, "None")
    )

    private val VIRTUALJAGUAR_PORT0 = listOf(
        d(JOYPAD, "Standard Joypad"),
        d(subclass(JOYPAD, 0), "Team Tap (4-player adaptor)"),
        d(subclass(MOUSE, 3), "Rotary (Tempest)"),
        d(subclass(ANALOG, 0), "Analog Joystick (bank-switching)"),
        d(subclass(ANALOG, 1), "Driving Controller (bank-switching)"),
        d(subclass(ANALOG, 2), "Analog Stick (paddle ADC)"),
        d(subclass(ANALOG, 3), "6D Controller (bank-switching)"),
        d(LIGHTGUN, "Light Gun")
    )

    private val VIRTUALJAGUAR_PORT1 = listOf(
        d(JOYPAD, "Standard Joypad"),
        d(subclass(JOYPAD, 0), "Team Tap (4-player adaptor)"),
        d(subclass(MOUSE, 0), "Atari ST / PS2 Mouse"),
        d(subclass(MOUSE, 1), "Amiga Mouse (ST adapter)"),
        d(subclass(MOUSE, 2), "Amiga Mouse (Amiga adapter)"),
        d(subclass(MOUSE, 3), "Rotary (Tempest)"),
        d(subclass(ANALOG, 0), "Analog Joystick (bank-switching)"),
        d(subclass(ANALOG, 1), "Driving Controller (bank-switching)"),
        d(subclass(ANALOG, 2), "Analog Stick (paddle ADC)"),
        d(subclass(ANALOG, 3), "6D Controller (bank-switching)")
    )

    private val PCE = listOf(d(JOYPAD, "PCE Joypad"), d(MOUSE, "PCE Mouse"))

    private val OPERA = listOf(
        d(JOYPAD, "3DO Joypad"),
        d(subclass(JOYPAD, 0), "3DO Flightstick"),
        d(MOUSE, "3DO Mouse"),
        d(LIGHTGUN, "3DO Lightgun"),
        d(subclass(LIGHTGUN, 0), "Arcade Lightgun"),
        d(subclass(JOYPAD, 1), "Orbatak Trackball")
    )

    private val FBNEO = listOf(
        d(ANALOG, "Classic"),
        d(subclass(ANALOG, 1), "Modern"),
        d(subclass(ANALOG, 0), "6-Button Panel"),
        d(subclass(ANALOG, 2), "Mouse (ball only)"),
        d(subclass(MOUSE, 1), "Mouse (full)"),
        d(POINTER, "Pointer"),
        d(subclass(POINTER, 0), "Touchscreen"),
        d(LIGHTGUN, "Lightgun"),
        d(subclass(ANALOG, 3), "Analog Arcade Gun")
    )

    private val PUAE_JOYPORT = listOf(
        d(JOYPAD, "Automatic"),
        d(subclass(JOYPAD, 0), "RetroPad"),
        d(subclass(ANALOG, 1), "CD32 Pad"),
        d(subclass(ANALOG, 2), "Analog Joystick"),
        d(subclass(JOYPAD, 1), "Arcadia"),
        d(subclass(LIGHTGUN, 0), "Trojan Phazer Lightgun"),
        d(subclass(LIGHTGUN, 1), "Lightpen"),
        d(subclass(ANALOG, 0), "Joystick"),
        d(subclass(KEYBOARD, 0), "Keyboard"),
        d(NONE, "None")
    )

    private val FUSE = listOf(
        d(JOYPAD, "Core defined Input"),
        d(subclass(JOYPAD, 0), "Cursor Joystick"),
        d(subclass(JOYPAD, 1), "Kempston Joystick"),
        d(subclass(JOYPAD, 2), "Sinclair 1 Joystick"),
        d(subclass(JOYPAD, 3), "Sinclair 2 Joystick"),
        d(subclass(JOYPAD, 4), "Timex 1 Joystick"),
        d(subclass(JOYPAD, 5), "Timex 2 Joystick"),
        d(subclass(JOYPAD, 6), "Fuller Joystick"),
        d(subclass(MOUSE, 0), "Kempston Mouse"),
        d(subclass(KEYBOARD, 0), "Sinclair Keyboard")
    )

    private val cores: Map<String, List<List<CorePortDevice>>> = mapOf(
        "fceumm" to listOf(
            listOf(d(JOYPAD, "Auto"), d(subclass(JOYPAD, 1), "Gamepad"), d(subclass(MOUSE, 0), "Zapper")),
            listOf(
                d(JOYPAD, "Auto"), d(subclass(JOYPAD, 1), "Gamepad"), d(subclass(MOUSE, 1), "Arkanoid"),
                d(subclass(MOUSE, 0), "Zapper"), d(subclass(KEYBOARD, 0), "Power Pad A"),
                d(subclass(KEYBOARD, 1), "Power Pad B")
            ),
            listOf(
                d(JOYPAD, "Auto"), d(subclass(JOYPAD, 1), "Gamepad"),
                d(subclass(KEYBOARD, 0), "Power Pad A"), d(subclass(KEYBOARD, 1), "Power Pad B")
            ),
            listOf(d(JOYPAD, "Auto"), d(subclass(JOYPAD, 1), "Gamepad"))
        ),
        "nestopia" to listOf(
            listOf(d(JOYPAD, "Auto"), d(subclass(JOYPAD, 0), "Gamepad")),
            listOf(
                d(JOYPAD, "Auto"), d(subclass(JOYPAD, 0), "Gamepad"),
                d(subclass(MOUSE, 0), "Arkanoid"), d(subclass(POINTER, 0), "Zapper")
            ),
            listOf(d(JOYPAD, "Auto"), d(subclass(JOYPAD, 0), "Gamepad")),
            listOf(d(JOYPAD, "Auto"), d(subclass(JOYPAD, 0), "Gamepad"))
        ),
        "snes9x" to listOf(
            listOf(d(JOYPAD, "SNES Joypad"), d(MOUSE, "SNES Mouse"), d(subclass(JOYPAD, 0), "Multitap"), d(NONE, "None")),
            listOf(
                d(JOYPAD, "SNES Joypad"), d(MOUSE, "SNES Mouse"), d(subclass(JOYPAD, 0), "Multitap"),
                d(subclass(LIGHTGUN, 0), "SuperScope"), d(subclass(LIGHTGUN, 1), "Justifier"),
                d(subclass(LIGHTGUN, 3), "M.A.C.S. Rifle"), d(NONE, "None")
            ),
            listOf(d(JOYPAD, "SNES Joypad"), d(subclass(LIGHTGUN, 2), "Justifier (2P)"), d(NONE, "None")),
            listOf(d(JOYPAD, "SNES Joypad"), d(NONE, "None"))
        ),
        "bsnes" to listOf(
            listOf(d(JOYPAD, "SNES Joypad"), d(MOUSE, "SNES Mouse")),
            listOf(
                d(JOYPAD, "SNES Joypad"), d(MOUSE, "SNES Mouse"), d(subclass(JOYPAD, 0), "Multitap"),
                d(subclass(LIGHTGUN, 0), "SuperScope"), d(subclass(LIGHTGUN, 1), "Justifier"),
                d(subclass(LIGHTGUN, 2), "Justifiers")
            )
        ),
        "parallel_n64" to same(4, listOf(d(JOYPAD, "Controller"), d(MOUSE, "Mouse"), d(ANALOG, "Analog"))),
        "genesis_plus_gx" to listOf(
            GENESIS_PLUS_GX_PORT0,
            GENESIS_PLUS_GX_PORT0 + listOf(d(subclass(LIGHTGUN, 1), "MD Menacer"), d(subclass(LIGHTGUN, 2), "MD Justifiers"))
        ),
        "pcsx_rearmed" to same(MAX_PORTS, PCSX_REARMED),
        "mednafen_psx_hw" to same(2, BEETLE_PSX),
        "mednafen_saturn" to same(2, BEETLE_SATURN),
        "flycast" to same(MAX_PORTS, FLYCAST),
        "stella" to listOf(
            STELLA_PORT0,
            STELLA_PORT0,
            listOf(d(JOYPAD, "Automatic"), d(NONE, "None")),
            listOf(d(JOYPAD, "Automatic"), d(NONE, "None"))
        ),
        "virtualjaguar" to listOf(VIRTUALJAGUAR_PORT0, VIRTUALJAGUAR_PORT1),
        "mednafen_pce" to same(MAX_PORTS, PCE),
        "mednafen_supergrafx" to same(MAX_PORTS, PCE + d(NONE, "None")),
        "mednafen_pcfx" to same(2, listOf(d(JOYPAD, "PCFX Joypad"), d(MOUSE, "PCFX Mouse"))),
        "a5200" to listOf(
            listOf(
                d(JOYPAD, "Atari 5200 Joystick (OSD Keypad)"),
                d(subclass(JOYPAD, 0), "Atari 5200 Joystick (Direct Keypad Buttons)")
            )
        ),
        "gearcoleco" to same(
            2,
            listOf(d(JOYPAD, "Joypad Auto"), d(NONE, "Joypad Port Empty"), d(subclass(JOYPAD, 0), "ColecoVision"))
        ),
        "bluemsx" to listOf(
            listOf(d(JOYPAD, "RetroPad"), d(KEYBOARD, "RetroKeyboard"), d(subclass(JOYPAD, 1), "RetroPad Keyboard Map")),
            listOf(d(JOYPAD, "RetroPad"), d(KEYBOARD, "RetroKeyboard"))
        ),
        "opera" to same(MAX_PORTS, OPERA),
        "fbneo" to same(MAX_PORTS, FBNEO),
        "mame2003_plus" to same(
            MAX_PORTS,
            listOf(
                d(JOYPAD, "RetroPad"), d(subclass(JOYPAD, 0), "Fightstick"),
                d(subclass(JOYPAD, 1), "8-Button"), d(subclass(JOYPAD, 2), "6-Button")
            )
        ),
        "vice_x64" to same(
            MAX_PORTS,
            listOf(d(JOYPAD, "RetroPad"), d(subclass(JOYPAD, 0), "Joystick"), d(subclass(KEYBOARD, 0), "Keyboard"), d(NONE, "None"))
        ),
        "puae" to listOf(
            PUAE_JOYPORT,
            PUAE_JOYPORT,
            listOf(d(subclass(ANALOG, 0), "Joystick"), d(subclass(KEYBOARD, 0), "Keyboard"), d(NONE, "None")),
            listOf(d(subclass(ANALOG, 0), "Joystick"), d(subclass(KEYBOARD, 0), "Keyboard"), d(NONE, "None"))
        ),
        "fuse" to same(3, FUSE),
        "cap32" to same(
            2,
            listOf(
                d(subclass(JOYPAD, 1), "Amstrad Joystick"), d(subclass(KEYBOARD, 0), "Amstrad Keyboard"),
                d(subclass(LIGHTGUN, 0), "Amstrad Lightgun"), d(NONE, "None")
            )
        )
    )

    fun portDevices(coreId: String, platformSlug: String): List<List<CorePortDevice>> =
        when (coreId) {
            "dolphin" -> if (PlatformDefinitions.getCanonicalSlug(platformSlug) == "wii") same(MAX_PORTS, DOLPHIN_WII) else emptyList()
            else -> cores[coreId].orEmpty()
        }.take(MAX_PORTS)
}
