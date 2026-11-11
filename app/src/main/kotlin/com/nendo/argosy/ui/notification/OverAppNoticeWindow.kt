package com.nendo.argosy.ui.notification

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import coil.compose.AsyncImage
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.core.input.ConnectedControllerTracker
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.ResolvedNotice
import com.nendo.argosy.core.notification.severity
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.hardware.AmbientLedManager
import com.nendo.argosy.hardware.ComposeWindowOwner
import com.nendo.argosy.ui.quickpanel.QuickPanelOverlay
import com.nendo.argosy.ui.screens.running.ResumeOverlay
import com.nendo.argosy.ui.theme.ArgosyDarkTheme
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.ThemeViewModel
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.theme.generated.MotionTokens
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws [NotificationManager.overAppNotices] one at a time in a window above whatever app is on
 * the game's display, never focusable or touchable. The window exists only while a card shows,
 * waits while the quick panel or the Resume overlay is open, and needs the draw-over-apps
 * permission.
 */
@Singleton
class OverAppNoticeWindow @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    quickPanelOverlay: QuickPanelOverlay,
    resumeOverlay: ResumeOverlay,
    private val preferencesRepository: UserPreferencesRepository,
    private val ambientLedManager: AmbientLedManager,
    private val controllerTracker: ConnectedControllerTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val overlayOpen: StateFlow<Boolean> =
        combine(quickPanelOverlay.isOpen, resumeOverlay.isOpen) { panel, resume -> panel || resume }
            .stateIn(scope, SharingStarted.Eagerly, quickPanelOverlay.isOpen.value || resumeOverlay.isOpen.value)
    private val pending = ArrayDeque<ResolvedNotice>()
    private var collector: Job? = null
    private var drain: Job? = null

    fun start() {
        if (collector != null) return
        collector = scope.launch {
            notificationManager.overAppNotices.collect { notice -> enqueue(notice) }
        }
    }

    private fun enqueue(notice: ResolvedNotice) {
        val queuedIndex = notice.key?.let { key -> pending.indexOfFirst { it.key == key } } ?: -1
        if (queuedIndex >= 0) pending[queuedIndex] = notice else pending.addLast(notice)
        if (drain?.isActive != true) drain = scope.launch { drainQueue() }
    }

    private suspend fun drainQueue() {
        while (pending.isNotEmpty()) {
            overlayOpen.first { open -> !open }
            if (!Settings.canDrawOverlays(context)) {
                pending.clear()
                return
            }
            present(pending.removeFirst())
        }
    }

    private suspend fun present(notice: ResolvedNotice) {
        val windowContext = windowContextFor(gameDisplayId()) ?: return
        val windowManager = windowContext.getSystemService(WindowManager::class.java) ?: return
        val owner = ComposeWindowOwner()
        val theme = ViewModelProvider(owner.viewModelStore, ThemeFactory())[ThemeViewModel::class.java]
        val finished = CompletableDeferred<Unit>()
        owner.start()
        val view = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent {
                ArgosyDarkTheme(viewModel = theme) {
                    OverAppNoticeCard(
                        notice = notice,
                        panelOpen = overlayOpen,
                        onFinished = { finished.complete(Unit) }
                    )
                }
            }
        }
        val added = runCatching { windowManager.addView(view, layoutParams(windowContext)) }
            .onFailure { Log.w(TAG, "addView failed", it) }
            .isSuccess
        if (added) finished.await()
        removeView(windowManager, view)
        owner.stop()
    }

    private fun removeView(windowManager: WindowManager, view: View) {
        runCatching { if (view.isAttachedToWindow) windowManager.removeView(view) }
            .onFailure { Log.w(TAG, "removeView failed", it) }
    }

    private fun layoutParams(windowContext: Context): WindowManager.LayoutParams {
        val density = windowContext.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = (DimensionTokens.Layout.noticeOverAppEndInset * density).toInt()
            y = (DimensionTokens.Layout.noticeOverAppBottomInset * density).toInt()
        }
    }

    private fun gameDisplayId(): Int =
        DualScreenManagerHolder.instance?.emulatorDisplayId ?: Display.DEFAULT_DISPLAY

    private fun windowContextFor(displayId: Int): Context? {
        val displayManager = context.getSystemService(DisplayManager::class.java) ?: return null
        val display = displayManager.getDisplay(displayId)
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return null
        val displayContext = context.createDisplayContext(display)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
    }

    private inner class ThemeFactory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
            ThemeViewModel::class.java -> ThemeViewModel(
                preferencesRepository = preferencesRepository,
                ambientLedManager = ambientLedManager,
                connectedControllerTracker = controllerTracker
            )
            else -> throw IllegalArgumentException("No notice window factory for ${modelClass.name}")
        } as T
    }

    private companion object {
        const val TAG = "OverAppNoticeWindow"
    }
}

@Composable
private fun OverAppNoticeCard(
    notice: ResolvedNotice,
    panelOpen: StateFlow<Boolean>,
    onFinished: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(notice) {
        visible = true
        withTimeoutOrNull(notice.duration.ms) { panelOpen.first { open -> open } }
        visible = false
        delay(maxOf(MotionTokens.Tween.noticeRevealMs, Motion.durationContent).toLong())
        onFinished()
    }
    val imagePath = notice.imagePath
    val icon: (@Composable (Color) -> Unit)? = if (imagePath == null) {
        null
    } else {
        { _ ->
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(Dimens.avatarMd)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
            )
        }
    }
    NotificationCard(
        visible = visible,
        severity = notice.type.severity(),
        title = notice.title,
        body = notice.body,
        accentOverride = notice.accentColor?.let { Color(it) },
        icon = icon
    )
}
