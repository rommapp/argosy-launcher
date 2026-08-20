package com.nendo.argosy.libretro

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
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
 */
class SecondScreenPresentation(
    context: Context,
    display: Display,
    private val onSurfaceChanged: (Surface?) -> Unit
) : Presentation(context, display) {

    private lateinit var surfaceView: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = SurfaceView(context).apply {
            setZOrderOnTop(true)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(surfaceView)

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
