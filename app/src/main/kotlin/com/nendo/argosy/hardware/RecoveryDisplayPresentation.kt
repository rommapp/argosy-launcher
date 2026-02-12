package com.nendo.argosy.hardware

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class RecoveryDisplayPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var gameNameText: TextView? = null
    private var platformText: TextView? = null
    private var statusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
        }

        val footer = createFooterBar()
        val footerParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            dpToPx(56)
        ).apply {
            gravity = Gravity.BOTTOM
        }
        container.addView(footer, footerParams)

        setContentView(container)
        setCancelable(false)
    }

    private fun createFooterBar(): View {
        val footerBg = GradientDrawable().apply {
            setColor(Color.argb(200, 30, 30, 30))
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = footerBg
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), dpToPx(8))

            // Left side: Game info
            val leftSection = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                gameNameText = TextView(context).apply {
                    text = "Playing..."
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                addView(gameNameText)

                platformText = TextView(context).apply {
                    text = ""
                    setTextColor(Color.argb(180, 255, 255, 255))
                    textSize = 12f
                    maxLines = 1
                    visibility = View.GONE
                }
                addView(platformText)
            }
            addView(leftSection)

            // Right side: Status
            statusText = TextView(context).apply {
                text = "In Game"
                setTextColor(Color.argb(180, 255, 255, 255))
                textSize = 14f
            }
            addView(statusText)
        }
    }

    fun updateGameInfo(gameName: String?, platformName: String?) {
        gameNameText?.text = gameName ?: "Playing..."
        if (platformName != null) {
            platformText?.text = platformName
            platformText?.visibility = View.VISIBLE
        } else {
            platformText?.visibility = View.GONE
        }
    }

    fun updateStatus(status: String) {
        statusText?.text = status
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
