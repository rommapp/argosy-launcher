package com.nendo.argosy.libretro.touch

import android.view.KeyEvent
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.data.repository.MappingPlatform
import com.nendo.argosy.data.repository.RetroButton

enum class DpadStyle { EightWay, FourWay, AnalogOnly, None }

enum class FaceShape {
    Single,
    HorizontalPair,
    HorizontalTrio,
    Row4,
    Row6,
    Diamond4,
    Stack2x3,
    NbuttonCluster
}

enum class ShoulderShape { None, TopPair, FourCorners, TopPairPlusZ }

enum class AnalogConfig { None, LeftOnly, LeftAndRight }

enum class GroupId {
    DPAD, LEFT_ANALOG, FACE, SHOULDERS, SYSTEM, RIGHT_ANALOG
}

data class TouchSlot(
    val androidKeyCode: Int,
    val label: String,
    val tint: Color? = null
)

data class TouchLayoutSpec(
    val mappingPlatform: MappingPlatform,
    val dpad: DpadStyle,
    val face: FaceShape,
    val faceSlots: List<TouchSlot>,
    val shoulders: ShoulderShape,
    val shoulderSlots: List<TouchSlot>,
    val system: List<TouchSlot>,
    val analog: AnalogConfig,
    val notes: String? = null,
    val sixButtonToggle: Boolean = false
)

internal fun slot(retroBtn: Int, label: String, tint: Color? = null): TouchSlot {
    val keyCode = when (retroBtn) {
        RetroButton.A -> KeyEvent.KEYCODE_BUTTON_A
        RetroButton.B -> KeyEvent.KEYCODE_BUTTON_B
        RetroButton.X -> KeyEvent.KEYCODE_BUTTON_X
        RetroButton.Y -> KeyEvent.KEYCODE_BUTTON_Y
        RetroButton.START -> KeyEvent.KEYCODE_BUTTON_START
        RetroButton.SELECT -> KeyEvent.KEYCODE_BUTTON_SELECT
        RetroButton.L -> KeyEvent.KEYCODE_BUTTON_L1
        RetroButton.R -> KeyEvent.KEYCODE_BUTTON_R1
        RetroButton.L2 -> KeyEvent.KEYCODE_BUTTON_L2
        RetroButton.R2 -> KeyEvent.KEYCODE_BUTTON_R2
        RetroButton.L3 -> KeyEvent.KEYCODE_BUTTON_THUMBL
        RetroButton.R3 -> KeyEvent.KEYCODE_BUTTON_THUMBR
        else -> KeyEvent.KEYCODE_UNKNOWN
    }
    return TouchSlot(keyCode, label, tint)
}
