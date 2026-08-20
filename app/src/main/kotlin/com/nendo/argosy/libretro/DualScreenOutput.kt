package com.nendo.argosy.libretro

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
    val layoutOptionKey: String,
    val layoutOptionValue: String,
    val primary: RectF,
    val secondary: RectF,
    val primaryAspect: Float,
    val secondaryAspect: Float
) {
    companion object {
        /**
         * Azahar stacks the 400x240 top screen over the 320x240 bottom screen in a 400x480 frame,
         * so the lower screen is the bottom half inset by 40px on each side.
         */
        private val AZAHAR = DualScreenOutput(
            coreId = "azahar",
            layoutOptionKey = "citra_layout_option",
            layoutOptionValue = "default",
            primary = RectF(0f, 0f, 0f, 0.5f),
            secondary = RectF(0.1f, 0.5f, 0.1f, 0f),
            primaryAspect = 400f / 240f,
            secondaryAspect = 320f / 240f
        )

        /**
         * melonDS stacks two 256x192 screens into a 256x384 frame - a clean half each. It renders
         * in software, so its frame is the way up the crop reads it, unlike azahar's.
         */
        private val MELONDS = DualScreenOutput(
            coreId = "melonds",
            layoutOptionKey = "melonds_screen_layout",
            layoutOptionValue = "Top/Bottom",
            primary = RectF(0f, 0f, 0f, 0.5f),
            secondary = RectF(0f, 0.5f, 0f, 0f),
            primaryAspect = 256f / 192f,
            secondaryAspect = 256f / 192f
        )

        private val byCore = listOf(AZAHAR, MELONDS).associateBy { it.coreId }

        fun forCore(coreId: String?): DualScreenOutput? = coreId?.let { byCore[it] }
    }
}
