package com.nendo.argosy.data.model

import com.nendo.argosy.domain.model.ScreenRole

enum class DisplayKind { BUILT_IN, EXTERNAL, VIRTUAL }

/**
 * One display as the system reports it now. [physicalId] is the stable screen key Argosy stores
 * layouts under, null for a display that is not a screen. [role] is the role the dual-screen
 * manager gives the display, null when it gives none.
 */
data class DisplaySnapshot(
    val displayId: Int,
    val physicalId: String?,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Int,
    val heightDp: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val kind: DisplayKind,
    val role: ScreenRole? = null
)
