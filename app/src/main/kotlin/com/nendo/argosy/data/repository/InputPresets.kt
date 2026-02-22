package com.nendo.argosy.data.repository

import android.view.KeyEvent
import com.nendo.argosy.ui.input.DetectedLayout

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
    val buttonLabels: Map<Int, String> = emptyMap()
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
            RetroButton.START, RetroButton.SELECT
        ) + DPAD
    )

    val N64 = MappingPlatform(
        id = "n64",
        displayName = "N64",
        buttons = listOf(
            RetroButton.A, RetroButton.B,
            RetroButton.L, RetroButton.R, RetroButton.L2,
            RetroButton.START,
            RetroButton.X, RetroButton.Y, RetroButton.L3, RetroButton.R3
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.L to "L",
            RetroButton.R to "R",
            RetroButton.L2 to "Z",
            RetroButton.X to "C-Left",
            RetroButton.Y to "C-Down",
            RetroButton.L3 to "C-Up",
            RetroButton.R3 to "C-Right"
        )
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
            RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.Y to "A",
            RetroButton.B to "B",
            RetroButton.A to "C",
            RetroButton.L to "X",
            RetroButton.X to "Y",
            RetroButton.R to "Z"
        )
    )

    val THREEDO = MappingPlatform(
        id = "3do",
        displayName = "3DO",
        buttons = listOf(
            RetroButton.A, RetroButton.B, RetroButton.Y,
            RetroButton.L, RetroButton.R,
            RetroButton.X, RetroButton.START
        ) + DPAD,
        buttonLabels = mapOf(
            RetroButton.Y to "C",
            RetroButton.L to "L",
            RetroButton.R to "R",
            RetroButton.X to "Pause",
            RetroButton.START to "Start"
        )
    )

    val ALL = listOf(UNIVERSAL, NES, GB, SNES, GBA, N64, PSX, GENESIS, THREEDO)

    fun getByIndex(index: Int): MappingPlatform = ALL[index.coerceIn(0, ALL.lastIndex)]

    fun getNextIndex(currentIndex: Int): Int = (currentIndex + 1) % ALL.size

    fun getPrevIndex(currentIndex: Int): Int = if (currentIndex <= 0) ALL.lastIndex else currentIndex - 1

    fun dbPlatformId(platformIndex: Int): String? {
        val platform = getByIndex(platformIndex)
        return if (platform.id == "universal") null else platform.id
    }

    fun dbPlatformIdForSlug(slug: String): String? = dbPlatformId(indexForPlatformSlug(slug))

    fun indexForPlatformSlug(slug: String): Int = when (slug) {
        "nes", "fds", "gameandwatch",
        "sg1000", "sms", "gg",
        "atari2600", "atari5200", "atari7800", "lynx",
        "coleco", "intellivision", "odyssey2", "vectrex", "channelf",
        "pokemini", "wonderswan", "wsc",
        "megaduck", "supervision", "arduboy", "uzebox",
        "ngp", "ngpc" -> ALL.indexOf(NES)

        "gb", "gbc" -> ALL.indexOf(GB)

        "gba" -> ALL.indexOf(GBA)

        "snes", "satellaview", "vb",
        "tg16", "supergrafx", "tgcd", "pcfx" -> ALL.indexOf(SNES)

        "n64", "n64dd" -> ALL.indexOf(N64)

        "psx", "ps2", "psp", "vita",
        "gc", "wii", "dc", "saturn",
        "nds", "dsi", "3ds", "n3ds",
        "jaguar", "jaguarcd" -> ALL.indexOf(PSX)

        "3do" -> ALL.indexOf(THREEDO)

        "genesis", "scd", "32x", "pico",
        "neogeo", "neogeocd" -> ALL.indexOf(GENESIS)

        else -> 0
    }
}

data class InputPreset(
    val name: String,
    val displayName: String,
    val mapping: Map<Int, Int>
)

object InputPresets {
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
            else -> KeyEvent.keyCodeToString(keyCode)
        }
    }
}
