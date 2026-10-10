package com.nendo.argosy.ui.components

internal data class DockWidths(
    val width: Float,
    val appsWidth: Float,
    val padding: Float,
    val groupGap: Float
)

internal fun dockWidths(
    maximum: Float,
    appCount: Int,
    slot: Float,
    gap: Float,
    padding: Float,
    appIconSize: Float,
    controlIconSize: Float
): DockWidths {
    val usable = maximum.coerceAtLeast(slot * 2)
    val groupCount = if (appCount > 0) 2 else 1
    val appInset = (slot - appIconSize).coerceAtLeast(0f) / 2f
    val controlInset = (slot - controlIconSize).coerceAtLeast(0f) / 2f
    val visibleAppGap = gap + appInset * 2
    val desiredGroupGap = if (appCount > 0) {
        (visibleAppGap * 2 - appInset - controlInset).coerceAtLeast(0f)
    } else gap * 2
    val groupGap = desiredGroupGap.coerceAtMost((usable - slot * 2) / groupCount)
    val controlsAndGaps = slot * 2 + groupGap * groupCount
    val minimumApps = if (appCount > 0) slot.coerceAtMost(usable - controlsAndGaps) else 0f
    val resolvedPadding = padding.coerceIn(0f, (usable - controlsAndGaps - minimumApps) / 2f)
    val fixed = controlsAndGaps + resolvedPadding * 2
    val desiredApps = appCount * slot + (appCount - 1).coerceAtLeast(0) * gap
    val apps = desiredApps.coerceAtMost((usable - fixed).coerceAtLeast(0f))
    return DockWidths(fixed + apps, apps, resolvedPadding, groupGap)
}

internal fun dockToolFocusMove(index: Int, delta: Int, enabled: List<Boolean>): Int {
    if (enabled.isEmpty() || enabled.none { it } || delta == 0) return index
    val direction = if (delta < 0) -1 else 1
    var next = index.coerceIn(enabled.indices)
    repeat(enabled.size) {
        next = (next + direction).mod(enabled.size)
        if (enabled[next]) return next
    }
    return index
}

internal fun dockPopupLeft(center: Int, width: Int, window: Int, inset: Int): Int {
    val minimum = inset.coerceAtMost((window - width).coerceAtLeast(0) / 2)
    return (center - width / 2).coerceIn(minimum, (window - width - minimum).coerceAtLeast(minimum))
}
