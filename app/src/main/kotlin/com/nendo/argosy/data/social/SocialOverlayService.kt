package com.nendo.argosy.data.social

import android.app.Service
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nendo.argosy.R

class SocialOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var textWrapper: FrameLayout? = null
    private var overlayText: TextView? = null
    private var subtitleText: TextView? = null
    private var textFullWidth = 0
    private var isOverlayVisible = false
    private var introAnimator: AnimatorSet? = null
    private var exitAnimator: AnimatorSet? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: return START_NOT_STICKY
        val subtitle = intent.getStringExtra(EXTRA_SUBTITLE)
        showOverlay(title, subtitle)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay(title: String, subtitle: String?) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (isOverlayVisible) {
            removeOverlay()
        }

        val dp = { value: Int ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
        }

        val isDark = isDarkTheme()
        val backgroundColor = if (isDark) Color.parseColor("#DD1E1E1E") else Color.parseColor("#DDF5F5F5")
        val textColor = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")
        val helmTint = if (isDark) Color.WHITE else Color.parseColor("#1E1E1E")

        val iconSize = dp(18)

        val helmIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setImageResource(R.drawable.ic_helm)
            setColorFilter(helmTint)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        overlayText = TextView(this).apply {
            text = title
            setTextColor(textColor)
            textSize = 12f
            maxLines = 1
        }
        textColumn.addView(overlayText)

        if (subtitle != null) {
            subtitleText = TextView(this).apply {
                text = subtitle
                setTextColor(Color.argb(179, Color.red(textColor), Color.green(textColor), Color.blue(textColor)))
                textSize = 10f
                maxLines = 1
            }
            textColumn.addView(subtitleText)
        }

        textColumn.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        textFullWidth = textColumn.measuredWidth

        textColumn.layoutParams = FrameLayout.LayoutParams(
            textFullWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        textWrapper = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(6)
            }
            clipChildren = true
            clipToPadding = true
            addView(textColumn)
        }

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(backgroundColor)
            }
            addView(helmIcon)
            addView(textWrapper)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dp(24)
            y = dp(24)
        }

        try {
            windowManager?.addView(overlayView, params)
            isOverlayVisible = true
            startIntroAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show social overlay", e)
            stopSelf()
        }
    }

    private fun startIntroAnimation() {
        val overlay = overlayView ?: return
        val wrapper = textWrapper ?: return

        overlay.alpha = 0f

        val fadeIn = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 200
        }

        val textReveal = ValueAnimator.ofInt(0, textFullWidth).apply {
            duration = 400
            startDelay = 100
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val lp = wrapper.layoutParams
                lp.width = animator.animatedValue as Int
                wrapper.layoutParams = lp
            }
        }

        introAnimator = AnimatorSet().apply {
            playTogether(fadeIn, textReveal)
            start()
        }

        handler.postDelayed({ hideOverlay() }, DISPLAY_DURATION_MS)
    }

    private fun hideOverlay() {
        if (!isOverlayVisible) return

        introAnimator?.cancel()
        introAnimator = null

        val overlay = overlayView ?: return
        val wrapper = textWrapper ?: return

        val textCollapse = ValueAnimator.ofInt(textFullWidth, 0).apply {
            duration = 200
            interpolator = AccelerateInterpolator()
            addUpdateListener { animator ->
                val lp = wrapper.layoutParams
                lp.width = animator.animatedValue as Int
                wrapper.layoutParams = lp
            }
        }

        val fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 150
            startDelay = 150
        }

        exitAnimator = AnimatorSet().apply {
            playTogether(textCollapse, fadeOut)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    removeOverlay()
                    stopSelf()
                }
            })
            start()
        }
    }

    private fun removeOverlay() {
        introAnimator?.cancel()
        exitAnimator?.cancel()
        introAnimator = null
        exitAnimator = null
        handler.removeCallbacksAndMessages(null)

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove social overlay", e)
            }
        }
        overlayView = null
        overlayText = null
        subtitleText = null
        textWrapper = null
        isOverlayVisible = false
    }

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
               Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val TAG = "SocialOverlayService"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SUBTITLE = "subtitle"
        private const val DISPLAY_DURATION_MS = 2000L

        fun show(context: Context, title: String, subtitle: String? = null) {
            val intent = Intent(context, SocialOverlayService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot start overlay service from background: ${e.message}")
            }
        }
    }
}
