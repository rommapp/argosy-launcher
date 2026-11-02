package com.nendo.argosy.data.netplay.external

/**
 * Holds a reported detection until [threshold] consecutive observations agree on a different one.
 * Both a new session and a cleared one need [threshold] matching observations to take effect.
 */
class DetectionDebouncer(private val threshold: Int = 2) {
    private var reported: ExternalNetplay? = null
    private var candidate: ExternalNetplay? = null
    private var streak = 0

    fun observe(observation: ExternalNetplay?): ExternalNetplay? {
        if (observation == reported) {
            candidate = null
            streak = 0
            return reported
        }
        if (observation == candidate && streak > 0) {
            streak++
        } else {
            candidate = observation
            streak = 1
        }
        if (streak >= threshold) {
            reported = observation
            candidate = null
            streak = 0
        }
        return reported
    }

    fun reset() {
        reported = null
        candidate = null
        streak = 0
    }
}
