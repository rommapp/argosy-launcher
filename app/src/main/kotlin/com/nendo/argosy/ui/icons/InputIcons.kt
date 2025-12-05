package com.nendo.argosy.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.nendo.argosy.R

object InputIcons {
    val FaceTop: Painter
        @Composable get() = painterResource(R.drawable.ic_input_face_top)

    val FaceBottom: Painter
        @Composable get() = painterResource(R.drawable.ic_input_face_bottom)

    val FaceLeft: Painter
        @Composable get() = painterResource(R.drawable.ic_input_face_left)

    val FaceRight: Painter
        @Composable get() = painterResource(R.drawable.ic_input_face_right)

    val Dpad: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad)

    val DpadUp: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_up)

    val DpadDown: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_down)

    val DpadLeft: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_left)

    val DpadRight: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_right)

    val DpadHorizontal: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_horizontal)

    val DpadVertical: Painter
        @Composable get() = painterResource(R.drawable.ic_input_dpad_vertical)

    val BumperLeft: Painter
        @Composable get() = painterResource(R.drawable.ic_input_bumper_left)

    val BumperRight: Painter
        @Composable get() = painterResource(R.drawable.ic_input_bumper_right)

    val TriggerLeft: Painter
        @Composable get() = painterResource(R.drawable.ic_input_trigger_left)

    val TriggerRight: Painter
        @Composable get() = painterResource(R.drawable.ic_input_trigger_right)

    val Menu: Painter
        @Composable get() = painterResource(R.drawable.ic_input_menu)

    val Options: Painter
        @Composable get() = painterResource(R.drawable.ic_input_options)
}
