package com.nendo.argosy.data.wallpaper

import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.app.WallpaperColors
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.Choreographer
import android.view.SurfaceHolder
import com.nendo.argosy.data.preferences.DisplayPreferencesRepository
import com.nendo.argosy.domain.model.isReducedMotion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val SURFACE_RELEASE_WAIT_MS = 500L

@AndroidEntryPoint
class LockScreenWallpaperService : WallpaperService() {

    @Inject lateinit var scenes: LockScreenScenes
    @Inject lateinit var artManager: LockScreenArtManager
    @Inject lateinit var displayPrefs: DisplayPreferencesRepository

    override fun onCreateEngine(): Engine = MosaicEngine()

    private inner class MosaicEngine : Engine(), Choreographer.FrameCallback {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val renderThread = HandlerThread("LockScreenWallpaper").apply { start() }
        private val renderer = Handler(renderThread.looper)
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val keyguard = getSystemService(KeyguardManager::class.java)
        private var preview: Job? = null
        private var scene: LockScreenScene? = null
        private var visible = false
        private var reduceAnimations = false
        private var animating = false
        private var startedAt = SystemClock.uptimeMillis()

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            scope.launch {
                scenes.scene.collect { next ->
                    renderer.post {
                        scene = next
                        startedAt = SystemClock.uptimeMillis()
                        update()
                    }
                }
            }
            scope.launch {
                displayPrefs.preferences
                    .map { it.reduceAnimations }
                    .distinctUntilChanged()
                    .collect { reduce ->
                        renderer.post {
                            reduceAnimations = reduce
                            update()
                        }
                    }
            }
            if (isPreview) preview = artManager.prepareLivePreview() else artManager.onLiveWallpaperBound()
        }

        override fun onComputeColors(): WallpaperColors? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                WallpaperColors(Color.valueOf(Color.BLACK), null, null)
            } else {
                null
            }

        override fun onDestroy() {
            preview?.cancel()
            scope.cancel()
            renderer.post { stop() }
            renderThread.quitSafely()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            renderer.post {
                this.visible = visible
                update()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.post { update() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            val released = CountDownLatch(1)
            val posted = renderer.post {
                visible = false
                stop()
                released.countDown()
            }
            if (posted) released.await(SURFACE_RELEASE_WAIT_MS, TimeUnit.MILLISECONDS)
            super.onSurfaceDestroyed(holder)
        }

        override fun doFrame(frameTimeNanos: Long) {
            animating = false
            draw()
            if (shouldAnimate()) start()
        }

        private fun update() {
            draw()
            if (shouldAnimate()) start() else stop()
        }

        private fun shouldAnimate(): Boolean =
            visible && scene is LockScreenScene.Mosaic &&
                !isReducedMotion(reduceAnimations, ValueAnimator.areAnimatorsEnabled()) &&
                (isPreview || keyguard?.isKeyguardLocked == true)

        private fun start() {
            if (animating) return
            animating = true
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun stop() {
            if (!animating) return
            animating = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            val canvas = runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: return
            try {
                render(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun render(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            when (val current = scene) {
                null -> Unit
                is LockScreenScene.Hero -> canvas.drawBitmap(current.bitmap, 0f, 0f, paint)
                is LockScreenScene.Mosaic -> {
                    val seconds = (SystemClock.uptimeMillis() - startedAt) / 1000f
                    for (row in current.rows) {
                        var x = -((seconds * row.pixelsPerSecond) % row.period)
                        while (x < canvas.width) {
                            canvas.drawBitmap(row.strip, x, row.top, paint)
                            x += row.period
                        }
                    }
                    canvas.drawColor(Color.argb(current.dimAlpha, 0, 0, 0))
                }
            }
        }
    }
}
