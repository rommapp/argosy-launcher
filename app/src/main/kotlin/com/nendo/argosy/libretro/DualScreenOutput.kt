package com.nendo.argosy.libretro

import android.graphics.PointF
import android.graphics.RectF

/**
 * How a two-screen console's single framebuffer is split across two displays.
 *
 * The core composites both screens into one frame, so which part belongs on which display is a
 * crop of that frame. [layoutOptionValue] is the core option that produces the arrangement these
 * crops assume - the crops are only correct while it is set, so it is applied with them.
 *
 * Crop values are the fraction trimmed from each edge, written as the picture reads: the console's
 * upper screen is the upper part. A hardware-rendered frame arrives bottom row first and the
 * vertical trims are swapped for it further down, so a core that can switch renderer at runtime
 * needs no second table.
 */
data class DualScreenOutput(
    val coreId: String,
    val coreOptions: Map<String, String>,
    val primary: RectF,
    val secondary: RectF,
    val primaryAspect: Float,
    val secondaryAspect: Float
) {
    /**
     * Where a touch on the second display lands in the whole frame, or null when it lands on the
     * letterboxing beside the picture.
     *
     * [touchX] and [touchY] are pixels within a surface of [surfaceWidth] x [surfaceHeight]. The
     * picture is centred there at its own aspect ratio, so the bars around it are mapped out first
     * and what remains is placed inside the part of the frame this display is showing.
     */
    fun frameTouchAt(
        touchX: Float,
        touchY: Float,
        surfaceWidth: Int,
        surfaceHeight: Int
    ): PointF? {
        if (surfaceWidth <= 0 || surfaceHeight <= 0 || secondaryAspect <= 0f) return null

        val surfaceAspect = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val imageWidth: Float
        val imageHeight: Float
        if (surfaceAspect > secondaryAspect) {
            imageHeight = surfaceHeight.toFloat()
            imageWidth = imageHeight * secondaryAspect
        } else {
            imageWidth = surfaceWidth.toFloat()
            imageHeight = imageWidth / secondaryAspect
        }

        val left = (surfaceWidth - imageWidth) / 2f
        val top = (surfaceHeight - imageHeight) / 2f
        val u = (touchX - left) / imageWidth
        val v = (touchY - top) / imageHeight
        if (u < 0f || u > 1f || v < 0f || v > 1f) return null

        return PointF(
            secondary.left + u * (1f - secondary.left - secondary.right),
            secondary.top + v * (1f - secondary.top - secondary.bottom)
        )
    }

    companion object {
        /**
         * Azahar stacks the 400x240 top screen over the 320x240 bottom screen in a 400x480 frame,
         * so the lower screen is the bottom half inset by 40px on each side.
         */
        private val AZAHAR = DualScreenOutput(
            coreId = "azahar",
            coreOptions = mapOf("citra_layout_option" to "default"),
            primary = RectF(0f, 0f, 0f, 0.5f),
            secondary = RectF(0.1f, 0.5f, 0.1f, 0f),
            primaryAspect = 400f / 240f,
            secondaryAspect = 320f / 240f
        )

        /**
         * melonDS stacks two 256x192 screens into a 256x384 frame - a clean half each.
         *
         * Its touch mode is set with the layout because it defaults to Mouse, which reads relative
         * movement and ignores the pointer a tap on the other display arrives as.
         */
        private val MELONDS = DualScreenOutput(
            coreId = "melonds",
            coreOptions = mapOf(
                "melonds_screen_layout" to "Top/Bottom",
                "melonds_touch_mode" to "Touch"
            ),
            primary = RectF(0f, 0f, 0f, 0.5f),
            secondary = RectF(0f, 0.5f, 0f, 0f),
            primaryAspect = 256f / 192f,
            secondaryAspect = 256f / 192f
        )

        private val byCore = listOf(AZAHAR, MELONDS).associateBy { it.coreId }

        fun forCore(coreId: String?): DualScreenOutput? = coreId?.let { byCore[it] }
    }
}
