package com.nendo.argosy.hardware

/**
 * Which app is in front, from accessibility window-state events. A window from another package
 * marks another app as in front; one of [inputSurfaces] in [ownPackage] clears it. System UI and
 * the keyboard [imePackage] names open windows over whatever is in front, so they change nothing.
 */
class ForegroundAppTracker(
    private val ownPackage: String,
    private val inputSurfaces: Set<String>,
    private val imePackage: () -> String?
) {
    var foregroundPackage: String? = null
        private set

    var otherAppInFront: Boolean = false
        private set

    fun onWindowStateChanged(source: String, className: String?) {
        if (source == SYSTEM_UI_PACKAGE || source == imePackage()) return
        if (source != ownPackage) {
            foregroundPackage = source
            otherAppInFront = true
        } else if (className in inputSurfaces) {
            otherAppInFront = false
        }
    }

    companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
