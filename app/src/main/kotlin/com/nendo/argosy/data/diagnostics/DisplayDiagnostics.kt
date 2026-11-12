package com.nendo.argosy.data.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import com.nendo.argosy.data.model.DisplayKind
import com.nendo.argosy.data.model.DisplaySnapshot
import com.nendo.argosy.util.ScreenCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every display the system reports, with its panel size from the active display mode. Roles are
 * not read here; the caller attaches them from the dual-screen manager.
 */
@Singleton
class DisplayDiagnostics @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenCatalog: ScreenCatalog
) {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    suspend fun read(): List<DisplaySnapshot> = withContext(Dispatchers.IO) {
        val screens = screenCatalog.attachedScreens().associateBy { it.displayId }
        displayManager.displays
            .sortedBy { it.displayId }
            .map { display ->
                val screen = screens[display.displayId]
                val kind = when {
                    screen == null -> DisplayKind.VIRTUAL
                    screen.builtIn -> DisplayKind.BUILT_IN
                    else -> DisplayKind.EXTERNAL
                }
                snapshotOf(display, screen?.key, kind)
            }
    }

    private fun snapshotOf(display: Display, physicalId: String?, kind: DisplayKind): DisplaySnapshot {
        val mode = display.mode
        val density = runCatching {
            context.createDisplayContext(display).resources.displayMetrics.densityDpi
        }.getOrNull() ?: DisplayMetrics.DENSITY_DEFAULT
        return DisplaySnapshot(
            displayId = display.displayId,
            physicalId = physicalId,
            name = display.name.orEmpty(),
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
            widthDp = toDp(mode.physicalWidth, density),
            heightDp = toDp(mode.physicalHeight, density),
            densityDpi = density,
            refreshRateHz = display.refreshRate,
            kind = kind
        )
    }

    private fun toDp(px: Int, densityDpi: Int): Int =
        if (densityDpi <= 0) px else px * DisplayMetrics.DENSITY_DEFAULT / densityDpi
}
