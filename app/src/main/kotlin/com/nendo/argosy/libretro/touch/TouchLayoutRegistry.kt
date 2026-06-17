package com.nendo.argosy.libretro.touch

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.platform.PlatformDefinitions
import com.nendo.argosy.data.repository.MappingPlatforms
import com.nendo.argosy.data.repository.RetroButton

object TouchLayoutRegistry {

    private val PSX_TRIANGLE = Color(0xFF6BC36F)
    private val PSX_SQUARE = Color(0xFFF59FB0)
    private val PSX_CIRCLE = Color(0xFFE15A5A)
    private val PSX_CROSS = Color(0xFF5C8FE0)

    fun forPlatform(slug: String, genesis6Button: Boolean = false, colouredPsx: Boolean = false): TouchLayoutSpec {
        val canonical = PlatformDefinitions.getCanonicalSlug(slug).lowercase()
        val layout = when (canonical) {
            "nes", "fds" -> nesLike(label1 = "B", label2 = "A")
            "gb", "gbc" -> nesLike(label1 = "B", label2 = "A")
            "gba" -> gba()
            "sms", "gg" -> nesLike(label1 = "1", label2 = "2", systemSelectLabel = null)
            "gameandwatch", "pokemini" -> nesLike(label1 = "A", label2 = "B")
            "ngp", "ngpc" -> nesLike(label1 = "B", label2 = "A", systemStartLabel = "Option", systemSelectLabel = null)
            "wonderswan", "wsc" -> nesLike(label1 = "B", label2 = "A", systemSelectLabel = null)
            "coleco", "channelf", "odyssey2" -> nesLike(label1 = "1", label2 = "2")
            "pico8" -> nesLike(label1 = "X", label2 = "O")
            "c64", "amiga", "amigacd32", "cdtv", "msx", "msx2", "amstradcpc", "zx" ->
                nesLike(label1 = "1", label2 = "2")
            "lynx" -> lynx()
            "snes" -> snes()
            "tg16", "pce", "turbografx16", "pcengine", "tgcd", "supergrafx", "pcfx" -> pcEngine()
            "saturn" -> saturn()
            "vb", "virtualboy" -> snes()
            "psx", "ps1", "playstation" -> psxLike(true, colouredPsx)
            "ps2" -> psxLike(true, colouredPsx)
            "psp" -> psxLike(false, false, shoulderPair = true)
            "vita" -> psxLike(true, false, shoulderPair = true)
            "dreamcast", "dc" -> dreamcast()
            "n64", "n64dd" -> n64()
            "gc", "ngc", "gamecube" -> gamecube()
            "wii" -> wii()
            "nds", "ds", "dsi" -> snes()
            "arcade", "cps1", "cps2", "cps3", "neogeocd" -> arcade6()
            "neogeo" -> neoGeo()
            "genesis", "megadrive", "scd", "segacd", "32x", "pico" -> genesis(genesis6Button)
            "vectrex" -> vectrex()
            "intellivision" -> intellivision()
            "atari2600", "2600" -> atari2600()
            "atari5200" -> atari5200()
            "atari7800" -> atari7800()
            "3do" -> threedo()
            "dos", "pc9800" -> generic()
            else -> generic()
        }
        return layout.copy(mappingPlatform = MappingPlatforms.profileForSlug(canonical))
    }

    private fun nesLike(
        label1: String,
        label2: String,
        systemStartLabel: String? = "Start",
        systemSelectLabel: String? = "Select"
    ): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.B, label1), slot(RetroButton.A, label2)),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = buildList {
            if (systemSelectLabel != null) add(slot(RetroButton.SELECT, systemSelectLabel))
            if (systemStartLabel != null) add(slot(RetroButton.START, systemStartLabel))
        },
        analog = AnalogConfig.None
    )

    private fun gba(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.B, "B"), slot(RetroButton.A, "A")),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R")),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun lynx(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.B, "B"), slot(RetroButton.A, "A")),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "Opt 1"), slot(RetroButton.R, "Opt 2")),
        system = listOf(slot(RetroButton.START, "Pause")),
        analog = AnalogConfig.None
    )

    private fun snes(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "Y"),
            slot(RetroButton.X, "X"),
            slot(RetroButton.B, "B"),
            slot(RetroButton.A, "A")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R")),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun pcEngine(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.Y, "II"), slot(RetroButton.B, "I")),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Run")),
        analog = AnalogConfig.None
    )

    private fun saturn(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Stack2x3,
        faceSlots = listOf(
            slot(RetroButton.Y, "X"),
            slot(RetroButton.X, "Y"),
            slot(RetroButton.L, "Z"),
            slot(RetroButton.B, "A"),
            slot(RetroButton.A, "B"),
            slot(RetroButton.R, "C")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L2, "L"), slot(RetroButton.R2, "R")),
        system = listOf(slot(RetroButton.SELECT, "Mode"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun psxLike(
        dualStick: Boolean,
        coloured: Boolean,
        shoulderPair: Boolean = false
    ): TouchLayoutSpec {
        val triTint = if (coloured) PSX_TRIANGLE else null
        val sqTint = if (coloured) PSX_SQUARE else null
        val ciTint = if (coloured) PSX_CIRCLE else null
        val crTint = if (coloured) PSX_CROSS else null
        return TouchLayoutSpec(
            dpad = DpadStyle.EightWay,
            face = FaceShape.Diamond4,
            faceSlots = listOf(
                slot(RetroButton.Y, "□", sqTint),
                slot(RetroButton.X, "△", triTint),
                slot(RetroButton.B, "✕", crTint),
                slot(RetroButton.A, "○", ciTint)
            ),
            shoulders = if (shoulderPair) ShoulderShape.TopPair else ShoulderShape.FourCorners,
            shoulderSlots = if (shoulderPair) {
                listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R"))
            } else {
                listOf(
                    slot(RetroButton.L, "L1"), slot(RetroButton.L2, "L2"),
                    slot(RetroButton.R, "R1"), slot(RetroButton.R2, "R2")
                )
            },
            system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
            analog = if (dualStick) AnalogConfig.LeftAndRight else AnalogConfig.LeftOnly
        )
    }

    private fun gamecube(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "Y"),
            slot(RetroButton.X, "X"),
            slot(RetroButton.B, "B"),
            slot(RetroButton.A, "A")
        ),
        shoulders = ShoulderShape.TopPairPlusZ,
        shoulderSlots = listOf(
            slot(RetroButton.L, "L"),
            slot(RetroButton.R, "R"),
            slot(RetroButton.L2, "Z")
        ),
        system = listOf(slot(RetroButton.START, "Start")),
        analog = AnalogConfig.LeftAndRight
    )

    private fun wii(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "2"),
            slot(RetroButton.X, "1"),
            slot(RetroButton.B, "B"),
            slot(RetroButton.A, "A")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "C"), slot(RetroButton.R, "Z")),
        system = listOf(slot(RetroButton.SELECT, "-"), slot(RetroButton.START, "+")),
        analog = AnalogConfig.LeftOnly
    )

    private fun neoGeo(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "C"),
            slot(RetroButton.X, "D"),
            slot(RetroButton.B, "A"),
            slot(RetroButton.A, "B")
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun dreamcast(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "X"),
            slot(RetroButton.X, "Y"),
            slot(RetroButton.B, "A"),
            slot(RetroButton.A, "B")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R")),
        system = listOf(slot(RetroButton.START, "Start")),
        analog = AnalogConfig.LeftOnly
    )

    private fun n64(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, "A"),
            slot(RetroButton.Y, "B")
        ),
        shoulders = ShoulderShape.TopPairPlusZ,
        shoulderSlots = listOf(
            slot(RetroButton.L, "L"),
            slot(RetroButton.R, "R"),
            slot(RetroButton.L2, "Z")
        ),
        system = listOf(slot(RetroButton.START, "Start")),
        analog = AnalogConfig.LeftAndRight,
        notes = "Right analog stick maps to C-buttons natively in libretro Mupen64Plus"
    )

    private fun arcade6(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Row6,
        faceSlots = listOf(
            slot(RetroButton.Y, "A"),
            slot(RetroButton.X, "B"),
            slot(RetroButton.L, "C"),
            slot(RetroButton.B, "D"),
            slot(RetroButton.A, "E"),
            slot(RetroButton.R, "F")
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.SELECT, "Coin"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun genesis(sixButton: Boolean): TouchLayoutSpec {
        val faceSlots = if (sixButton) {
            listOf(
                slot(RetroButton.L, "X"),
                slot(RetroButton.X, "Y"),
                slot(RetroButton.R, "Z"),
                slot(RetroButton.Y, "A"),
                slot(RetroButton.B, "B"),
                slot(RetroButton.A, "C")
            )
        } else {
            listOf(
                slot(RetroButton.Y, "A"),
                slot(RetroButton.B, "B"),
                slot(RetroButton.A, "C")
            )
        }
        return TouchLayoutSpec(
            dpad = DpadStyle.EightWay,
            face = if (sixButton) FaceShape.Stack2x3 else FaceShape.HorizontalTrio,
            faceSlots = faceSlots,
            shoulders = ShoulderShape.None,
            shoulderSlots = emptyList(),
            system = listOf(slot(RetroButton.SELECT, "Mode"), slot(RetroButton.START, "Start")),
            analog = AnalogConfig.None,
            sixButtonToggle = true
        )
    }

    private fun vectrex(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.AnalogOnly,
        face = FaceShape.Row4,
        faceSlots = listOf(
            slot(RetroButton.Y, "1"),
            slot(RetroButton.B, "2"),
            slot(RetroButton.A, "3"),
            slot(RetroButton.X, "4")
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = emptyList(),
        analog = AnalogConfig.LeftOnly
    )

    private fun intellivision(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.None,
        face = FaceShape.NbuttonCluster,
        faceSlots = listOf(
            slot(RetroButton.Y, "Top"),
            slot(RetroButton.B, "Left"),
            slot(RetroButton.A, "Right"),
            slot(RetroButton.X, "Last KP")
        ),
        shoulders = ShoulderShape.FourCorners,
        shoulderSlots = listOf(
            slot(RetroButton.L, "MK"),
            slot(RetroButton.L2, "Clear"),
            slot(RetroButton.R, "MK"),
            slot(RetroButton.R2, "Enter")
        ),
        system = listOf(slot(RetroButton.SELECT, "Swap"), slot(RetroButton.START, "Pause")),
        analog = AnalogConfig.LeftAndRight,
        notes = "Left analog = 16-way disc; right analog = keypad 1-9 sectors"
    )

    private fun atari2600(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Single,
        faceSlots = listOf(slot(RetroButton.B, "Fire")),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Reset")),
        analog = AnalogConfig.None
    )

    private fun atari5200(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.AnalogOnly,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.B, "A"), slot(RetroButton.A, "B")),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "Pause"), slot(RetroButton.R, "Reset")),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.LeftOnly
    )

    private fun atari7800(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(slot(RetroButton.B, "A"), slot(RetroButton.A, "B")),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Reset")),
        analog = AnalogConfig.None
    )

    private fun threedo(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalTrio,
        faceSlots = listOf(
            slot(RetroButton.A, "A"),
            slot(RetroButton.B, "B"),
            slot(RetroButton.Y, "C")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R")),
        system = listOf(slot(RetroButton.X, "Pause"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.None
    )

    private fun generic(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, "Y"),
            slot(RetroButton.X, "X"),
            slot(RetroButton.B, "B"),
            slot(RetroButton.A, "A")
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(slot(RetroButton.L, "L"), slot(RetroButton.R, "R")),
        system = listOf(slot(RetroButton.SELECT, "Select"), slot(RetroButton.START, "Start")),
        analog = AnalogConfig.LeftOnly
    )
}
