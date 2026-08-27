package com.nendo.argosy.libretro.touch

import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
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
            "nes", "fds" -> nes()
            "gb", "gbc" -> gameBoy()
            "gba" -> gba()
            "sms", "gg" -> sms()
            "gameandwatch", "pokemini" -> gameAndWatch()
            "ngp", "ngpc" -> ngp()
            "wonderswan", "wsc" -> wonderswan()
            "coleco", "channelf", "odyssey2" -> coleco()
            "pico8" -> pico8()
            "c64", "amiga", "amigacd32", "cdtv", "msx", "msx2", "amstradcpc", "zx" -> c64()
            "lynx" -> lynx()
            "snes" -> snes()
            "tg16", "pce", "turbografx16", "pcengine", "tgcd", "supergrafx" -> pcEngine()
            "pcfx" -> pcfx()
            "saturn" -> saturn()
            "vb", "virtualboy" -> virtualBoy()
            "psx", "ps1", "playstation" -> psx(colouredPsx)
            "ps2" -> ps2(colouredPsx)
            "psp" -> psp()
            "vita" -> vita()
            "dreamcast", "dc" -> dreamcast()
            "n64", "n64dd" -> n64()
            "gc", "ngc", "gamecube" -> gamecube()
            "wii" -> wii()
            "nds", "ds", "dsi" -> nds()
            "arcade", "fbneo", "mame", "cps1", "cps2", "cps3", "neogeocd",
            "naomi", "atomiswave" -> arcade6()
            "neogeo" -> neoGeo()
            "genesis", "megadrive", "scd", "segacd", "32x", "pico", "nomad" -> genesis(genesis6Button)
            "vectrex" -> vectrex()
            "intellivision" -> intellivision()
            "atari2600", "2600" -> atari2600()
            "atari5200" -> atari5200()
            "atari7800" -> atari7800()
            "jaguar" -> jaguar()
            "3do" -> threedo()
            "cdi" -> cdi()
            "dos", "pc9800" -> generic()
            else -> generic()
        }
        return layout.copy(mappingPlatform = MappingPlatforms.profileForSlug(canonical))
    }

    private fun nes(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_nes_face_b),
            slot(RetroButton.A, R.string.ingame_touch_nes_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_nes_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_nes_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun gameBoy(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_gb_face_b),
            slot(RetroButton.A, R.string.ingame_touch_gb_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_gb_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_gb_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun gba(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_gba_face_b),
            slot(RetroButton.A, R.string.ingame_touch_gba_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_gba_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_gba_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_gba_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_gba_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun sms(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_sms_face_b),
            slot(RetroButton.A, R.string.ingame_touch_sms_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_sms_sys_start)),
        analog = AnalogConfig.None
    )

    private fun gameAndWatch(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_gameandwatch_face_b),
            slot(RetroButton.A, R.string.ingame_touch_gameandwatch_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_gameandwatch_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_gameandwatch_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun ngp(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_ngp_face_b),
            slot(RetroButton.A, R.string.ingame_touch_ngp_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_ngp_sys_start)),
        analog = AnalogConfig.None
    )

    private fun wonderswan(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_wonderswan_face_b),
            slot(RetroButton.A, R.string.ingame_touch_wonderswan_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_wonderswan_sys_start)),
        analog = AnalogConfig.None
    )

    private fun coleco(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_coleco_face_b),
            slot(RetroButton.A, R.string.ingame_touch_coleco_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_coleco_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_coleco_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun pico8(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_pico8_face_b),
            slot(RetroButton.A, R.string.ingame_touch_pico8_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_pico8_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_pico8_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun c64(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_c64_face_b),
            slot(RetroButton.A, R.string.ingame_touch_c64_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_c64_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_c64_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun lynx(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_lynx_face_b),
            slot(RetroButton.A, R.string.ingame_touch_lynx_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_lynx_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_lynx_shoulder_r)
        ),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_lynx_sys_start)),
        analog = AnalogConfig.None
    )

    private fun snes(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_snes_face_y),
            slot(RetroButton.X, R.string.ingame_touch_snes_face_x),
            slot(RetroButton.B, R.string.ingame_touch_snes_face_b),
            slot(RetroButton.A, R.string.ingame_touch_snes_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_snes_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_snes_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_snes_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_snes_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun pcEngine(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_pcengine_face_b),
            slot(RetroButton.A, R.string.ingame_touch_pcengine_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_pcengine_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_pcengine_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun pcfx(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Stack2x3,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_pcfx_face_y),
            slot(RetroButton.L, R.string.ingame_touch_pcfx_face_l),
            slot(RetroButton.R, R.string.ingame_touch_pcfx_face_r),
            slot(RetroButton.X, R.string.ingame_touch_pcfx_face_x),
            slot(RetroButton.B, R.string.ingame_touch_pcfx_face_b),
            slot(RetroButton.A, R.string.ingame_touch_pcfx_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L2, R.string.ingame_touch_pcfx_shoulder_l2),
            slot(RetroButton.R2, R.string.ingame_touch_pcfx_shoulder_r2)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_pcfx_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_pcfx_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun jaguar(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalTrio,
        faceSlots = listOf(
            slot(RetroButton.A, R.string.ingame_touch_jaguar_face_a),
            slot(RetroButton.B, R.string.ingame_touch_jaguar_face_b),
            slot(RetroButton.Y, R.string.ingame_touch_jaguar_face_y)
        ),
        shoulders = ShoulderShape.FourCorners,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_jaguar_shoulder_l),
            slot(RetroButton.L2, R.string.ingame_touch_jaguar_shoulder_l2),
            slot(RetroButton.R, R.string.ingame_touch_jaguar_shoulder_r),
            slot(RetroButton.R2, R.string.ingame_touch_jaguar_shoulder_r2)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_jaguar_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_jaguar_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun cdi(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalTrio,
        faceSlots = listOf(
            slot(RetroButton.A, R.string.ingame_touch_cdi_face_a),
            slot(RetroButton.B, R.string.ingame_touch_cdi_face_b),
            slot(RetroButton.X, R.string.ingame_touch_cdi_face_x)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = emptyList(),
        analog = AnalogConfig.LeftOnly
    )

    private fun saturn(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Stack2x3,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_saturn_face_y),
            slot(RetroButton.X, R.string.ingame_touch_saturn_face_x),
            slot(RetroButton.L, R.string.ingame_touch_saturn_face_l),
            slot(RetroButton.B, R.string.ingame_touch_saturn_face_b),
            slot(RetroButton.A, R.string.ingame_touch_saturn_face_a),
            slot(RetroButton.R, R.string.ingame_touch_saturn_face_r)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L2, R.string.ingame_touch_saturn_shoulder_l2),
            slot(RetroButton.R2, R.string.ingame_touch_saturn_shoulder_r2)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_saturn_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_saturn_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun virtualBoy(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_vb_face_y),
            slot(RetroButton.X, R.string.ingame_touch_vb_face_x),
            slot(RetroButton.B, R.string.ingame_touch_vb_face_b),
            slot(RetroButton.A, R.string.ingame_touch_vb_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_vb_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_vb_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_vb_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_vb_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun nds(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_nds_face_y),
            slot(RetroButton.X, R.string.ingame_touch_nds_face_x),
            slot(RetroButton.B, R.string.ingame_touch_nds_face_b),
            slot(RetroButton.A, R.string.ingame_touch_nds_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_nds_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_nds_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_nds_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_nds_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun psx(coloured: Boolean): TouchLayoutSpec {
        val sqTint = if (coloured) PSX_SQUARE else null
        val triTint = if (coloured) PSX_TRIANGLE else null
        val crTint = if (coloured) PSX_CROSS else null
        val ciTint = if (coloured) PSX_CIRCLE else null
        return TouchLayoutSpec(
            dpad = DpadStyle.EightWay,
            face = FaceShape.Diamond4,
            faceSlots = listOf(
                slot(RetroButton.Y, R.string.ingame_touch_psx_face_y, sqTint),
                slot(RetroButton.X, R.string.ingame_touch_psx_face_x, triTint),
                slot(RetroButton.B, R.string.ingame_touch_psx_face_b, crTint),
                slot(RetroButton.A, R.string.ingame_touch_psx_face_a, ciTint)
            ),
            shoulders = ShoulderShape.FourCorners,
            shoulderSlots = listOf(
                slot(RetroButton.L, R.string.ingame_touch_psx_shoulder_l),
                slot(RetroButton.L2, R.string.ingame_touch_psx_shoulder_l2),
                slot(RetroButton.R, R.string.ingame_touch_psx_shoulder_r),
                slot(RetroButton.R2, R.string.ingame_touch_psx_shoulder_r2)
            ),
            system = listOf(
                slot(RetroButton.SELECT, R.string.ingame_touch_psx_sys_select),
                slot(RetroButton.START, R.string.ingame_touch_psx_sys_start)
            ),
            analog = AnalogConfig.LeftAndRight
        )
    }

    private fun ps2(coloured: Boolean): TouchLayoutSpec {
        val sqTint = if (coloured) PSX_SQUARE else null
        val triTint = if (coloured) PSX_TRIANGLE else null
        val crTint = if (coloured) PSX_CROSS else null
        val ciTint = if (coloured) PSX_CIRCLE else null
        return TouchLayoutSpec(
            dpad = DpadStyle.EightWay,
            face = FaceShape.Diamond4,
            faceSlots = listOf(
                slot(RetroButton.Y, R.string.ingame_touch_ps2_face_y, sqTint),
                slot(RetroButton.X, R.string.ingame_touch_ps2_face_x, triTint),
                slot(RetroButton.B, R.string.ingame_touch_ps2_face_b, crTint),
                slot(RetroButton.A, R.string.ingame_touch_ps2_face_a, ciTint)
            ),
            shoulders = ShoulderShape.FourCorners,
            shoulderSlots = listOf(
                slot(RetroButton.L, R.string.ingame_touch_ps2_shoulder_l),
                slot(RetroButton.L2, R.string.ingame_touch_ps2_shoulder_l2),
                slot(RetroButton.R, R.string.ingame_touch_ps2_shoulder_r),
                slot(RetroButton.R2, R.string.ingame_touch_ps2_shoulder_r2)
            ),
            system = listOf(
                slot(RetroButton.SELECT, R.string.ingame_touch_ps2_sys_select),
                slot(RetroButton.START, R.string.ingame_touch_ps2_sys_start)
            ),
            analog = AnalogConfig.LeftAndRight
        )
    }

    private fun psp(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_psp_face_y),
            slot(RetroButton.X, R.string.ingame_touch_psp_face_x),
            slot(RetroButton.B, R.string.ingame_touch_psp_face_b),
            slot(RetroButton.A, R.string.ingame_touch_psp_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_psp_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_psp_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_psp_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_psp_sys_start)
        ),
        analog = AnalogConfig.LeftOnly
    )

    private fun vita(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_vita_face_y),
            slot(RetroButton.X, R.string.ingame_touch_vita_face_x),
            slot(RetroButton.B, R.string.ingame_touch_vita_face_b),
            slot(RetroButton.A, R.string.ingame_touch_vita_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_vita_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_vita_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_vita_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_vita_sys_start)
        ),
        analog = AnalogConfig.LeftAndRight
    )

    private fun gamecube(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_gamecube_face_y),
            slot(RetroButton.X, R.string.ingame_touch_gamecube_face_x),
            slot(RetroButton.B, R.string.ingame_touch_gamecube_face_b),
            slot(RetroButton.A, R.string.ingame_touch_gamecube_face_a)
        ),
        shoulders = ShoulderShape.TopPairPlusZ,
        shoulderSlots = listOf(
            slot(RetroButton.L2, R.string.ingame_touch_gamecube_shoulder_l2),
            slot(RetroButton.R2, R.string.ingame_touch_gamecube_shoulder_r2),
            slot(RetroButton.R, R.string.ingame_touch_gamecube_shoulder_r)
        ),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_gamecube_sys_start)),
        analog = AnalogConfig.LeftAndRight
    )

    private fun wii(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_wii_face_y),
            slot(RetroButton.X, R.string.ingame_touch_wii_face_x),
            slot(RetroButton.B, R.string.ingame_touch_wii_face_b),
            slot(RetroButton.A, R.string.ingame_touch_wii_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_wii_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_wii_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_wii_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_wii_sys_start)
        ),
        analog = AnalogConfig.LeftOnly
    )

    private fun neoGeo(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_neogeo_face_y),
            slot(RetroButton.X, R.string.ingame_touch_neogeo_face_x),
            slot(RetroButton.B, R.string.ingame_touch_neogeo_face_b),
            slot(RetroButton.A, R.string.ingame_touch_neogeo_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_neogeo_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_neogeo_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun dreamcast(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_dreamcast_face_y),
            slot(RetroButton.X, R.string.ingame_touch_dreamcast_face_x),
            slot(RetroButton.B, R.string.ingame_touch_dreamcast_face_b),
            slot(RetroButton.A, R.string.ingame_touch_dreamcast_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_dreamcast_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_dreamcast_shoulder_r)
        ),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_dreamcast_sys_start)),
        analog = AnalogConfig.LeftOnly
    )

    private fun n64(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_n64_face_b),
            slot(RetroButton.Y, R.string.ingame_touch_n64_face_y)
        ),
        shoulders = ShoulderShape.TopPairPlusZ,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_n64_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_n64_shoulder_r),
            slot(RetroButton.L2, R.string.ingame_touch_n64_shoulder_l2)
        ),
        system = listOf(slot(RetroButton.START, R.string.ingame_touch_n64_sys_start)),
        analog = AnalogConfig.LeftAndRight
    )

    private fun arcade6(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Row6,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_arcade_face_y),
            slot(RetroButton.X, R.string.ingame_touch_arcade_face_x),
            slot(RetroButton.L, R.string.ingame_touch_arcade_face_l),
            slot(RetroButton.B, R.string.ingame_touch_arcade_face_b),
            slot(RetroButton.A, R.string.ingame_touch_arcade_face_a),
            slot(RetroButton.R, R.string.ingame_touch_arcade_face_r)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_arcade_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_arcade_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun genesis(sixButton: Boolean): TouchLayoutSpec {
        val faceSlots = if (sixButton) {
            listOf(
                slot(RetroButton.L, R.string.ingame_touch_genesis_face_l),
                slot(RetroButton.X, R.string.ingame_touch_genesis_face_x),
                slot(RetroButton.R, R.string.ingame_touch_genesis_face_r),
                slot(RetroButton.Y, R.string.ingame_touch_genesis_face_y),
                slot(RetroButton.B, R.string.ingame_touch_genesis_face_b),
                slot(RetroButton.A, R.string.ingame_touch_genesis_face_a)
            )
        } else {
            listOf(
                slot(RetroButton.Y, R.string.ingame_touch_genesis_face_y),
                slot(RetroButton.B, R.string.ingame_touch_genesis_face_b),
                slot(RetroButton.A, R.string.ingame_touch_genesis_face_a)
            )
        }
        return TouchLayoutSpec(
            dpad = DpadStyle.EightWay,
            face = if (sixButton) FaceShape.Stack2x3 else FaceShape.HorizontalTrio,
            faceSlots = faceSlots,
            shoulders = ShoulderShape.None,
            shoulderSlots = emptyList(),
            system = listOf(
                slot(RetroButton.SELECT, R.string.ingame_touch_genesis_sys_select),
                slot(RetroButton.START, R.string.ingame_touch_genesis_sys_start)
            ),
            analog = AnalogConfig.None,
            sixButtonToggle = true
        )
    }

    private fun vectrex(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.AnalogOnly,
        face = FaceShape.Row4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_vectrex_face_y),
            slot(RetroButton.B, R.string.ingame_touch_vectrex_face_b),
            slot(RetroButton.A, R.string.ingame_touch_vectrex_face_a),
            slot(RetroButton.X, R.string.ingame_touch_vectrex_face_x)
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
            slot(RetroButton.Y, R.string.ingame_touch_intellivision_face_y),
            slot(RetroButton.B, R.string.ingame_touch_intellivision_face_b),
            slot(RetroButton.A, R.string.ingame_touch_intellivision_face_a),
            slot(RetroButton.X, R.string.ingame_touch_intellivision_face_x)
        ),
        shoulders = ShoulderShape.FourCorners,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_intellivision_shoulder_l),
            slot(RetroButton.L2, R.string.ingame_touch_intellivision_shoulder_l2),
            slot(RetroButton.R, R.string.ingame_touch_intellivision_shoulder_r),
            slot(RetroButton.R2, R.string.ingame_touch_intellivision_shoulder_r2)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_intellivision_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_intellivision_sys_start)
        ),
        analog = AnalogConfig.LeftAndRight
    )

    private fun atari2600(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Single,
        faceSlots = listOf(slot(RetroButton.B, R.string.ingame_touch_atari2600_face_b)),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_atari2600_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_atari2600_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun atari5200(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.AnalogOnly,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_atari5200_face_b),
            slot(RetroButton.A, R.string.ingame_touch_atari5200_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_atari5200_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_atari5200_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_atari5200_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_atari5200_sys_start)
        ),
        analog = AnalogConfig.LeftOnly
    )

    private fun atari7800(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalPair,
        faceSlots = listOf(
            slot(RetroButton.B, R.string.ingame_touch_atari7800_face_b),
            slot(RetroButton.A, R.string.ingame_touch_atari7800_face_a)
        ),
        shoulders = ShoulderShape.None,
        shoulderSlots = emptyList(),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_atari7800_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_atari7800_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun threedo(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.HorizontalTrio,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_threedo_face_y),
            slot(RetroButton.B, R.string.ingame_touch_threedo_face_b),
            slot(RetroButton.A, R.string.ingame_touch_threedo_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_threedo_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_threedo_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_threedo_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_threedo_sys_start)
        ),
        analog = AnalogConfig.None
    )

    private fun generic(): TouchLayoutSpec = TouchLayoutSpec(
        dpad = DpadStyle.EightWay,
        face = FaceShape.Diamond4,
        faceSlots = listOf(
            slot(RetroButton.Y, R.string.ingame_touch_generic_face_y),
            slot(RetroButton.X, R.string.ingame_touch_generic_face_x),
            slot(RetroButton.B, R.string.ingame_touch_generic_face_b),
            slot(RetroButton.A, R.string.ingame_touch_generic_face_a)
        ),
        shoulders = ShoulderShape.TopPair,
        shoulderSlots = listOf(
            slot(RetroButton.L, R.string.ingame_touch_generic_shoulder_l),
            slot(RetroButton.R, R.string.ingame_touch_generic_shoulder_r)
        ),
        system = listOf(
            slot(RetroButton.SELECT, R.string.ingame_touch_generic_sys_select),
            slot(RetroButton.START, R.string.ingame_touch_generic_sys_start)
        ),
        analog = AnalogConfig.LeftOnly
    )
}
