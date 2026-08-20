package com.nendo.argosy.libretro

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager

/**
 * The console's other screen, on the other display.
 *
 * The window this owns is created and destroyed by the display's own lifecycle, so the surface is
 * handed out and withdrawn through [onSurfaceChanged] rather than held: a surface that outlives
 * its window is drawn into forever without ever reaching the screen, which is what leaves a second
 * display frozen on its last frame after a suspend.
 *
 * The surface is ordered above its window and neither carries a background, because a SurfaceView
 * composites behind the window it lives in: an opaque background on either paints over the hole
 * the surface shows through, and the display stays black no matter what is drawn into it.
 *
 * It is also not cancelable, and never takes window focus. A Presentation is a dialog: Back would
 * otherwise dismiss it mid-game, and touching it would move focus off the emulator so the pad went
 * dead until the game's own display was tapped again. Not being focusable still delivers touches
 * inside its bounds; only the keys go elsewhere, which is where they belong.
 */
class SecondScreenPresentation(
    context: Context,
    display: Display,
    private val onSurfaceChanged: (Surface?) -> Unit,
    private val onTouch: (x: Float, y: Float, width: Int, height: Int) -> Unit,
    private val onTouchReleased: () -> Unit
) : Presentation(context, display) {

    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(false)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        surfaceView = SurfaceView(context).apply {
            setZOrderOnTop(true)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(surfaceView)

        surfaceView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                    onTouch(event.x, event.y, view.width, view.height)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchReleased()
                else -> Unit
            }
            true
        }

        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceChanged(holder.surface)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceChanged(null)
            }
        })
    }

    override fun onStop() {
        onSurfaceChanged(null)
        super.onStop()
    }
}
