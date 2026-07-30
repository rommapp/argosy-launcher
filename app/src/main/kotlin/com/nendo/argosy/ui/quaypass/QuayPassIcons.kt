package com.nendo.argosy.ui.quaypass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.generated.DimensionTokens

/**
 * Vesica mark for QuayPass per design-handoff/QUAYPASS-ICON.md. Two circles
 * are two people; the filled lens between them is an encounter. Tint at the
 * usage site; white is the tintable base.
 */
object QuayPassIcons {

    val On: ImageVector by lazy {
        builder("QuayPassOn").apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.6f, 12f)
                arcToRelative(5f, 5f, 0f, true, false, 10f, 0f)
                arcToRelative(5f, 5f, 0f, true, false, -10f, 0f)
                moveTo(11.4f, 12f)
                arcToRelative(5f, 5f, 0f, true, false, 10f, 0f)
                arcToRelative(5f, 5f, 0f, true, false, -10f, 0f)
            }
        }.build()
    }

    val Encounter: ImageVector by lazy {
        builder("QuayPassEncounter").apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4f, 12f)
                arcToRelative(5.5f, 5.5f, 0f, true, false, 11f, 0f)
                arcToRelative(5.5f, 5.5f, 0f, true, false, -11f, 0f)
                moveTo(9f, 12f)
                arcToRelative(5.5f, 5.5f, 0f, true, false, 11f, 0f)
                arcToRelative(5.5f, 5.5f, 0f, true, false, -11f, 0f)
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 7.1f)
                arcTo(5.5f, 5.5f, 0f, false, true, 12f, 16.9f)
                arcTo(5.5f, 5.5f, 0f, false, true, 12f, 7.1f)
                close()
            }
        }.build()
    }

    val Off: ImageVector by lazy {
        builder("QuayPassOff").apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                strokeAlpha = 0.45f
            ) {
                moveTo(1.5f, 12f)
                arcToRelative(5f, 5f, 0f, true, false, 10f, 0f)
                arcToRelative(5f, 5f, 0f, true, false, -10f, 0f)
                moveTo(12.5f, 12f)
                arcToRelative(5f, 5f, 0f, true, false, 10f, 0f)
                arcToRelative(5f, 5f, 0f, true, false, -10f, 0f)
            }
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4.5f, 3.5f)
                lineTo(19.5f, 20.5f)
            }
        }.build()
    }

    private fun builder(name: String) = ImageVector.Builder(
        name = name,
        defaultWidth = DimensionTokens.Icon.md.dp,
        defaultHeight = DimensionTokens.Icon.md.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
}
