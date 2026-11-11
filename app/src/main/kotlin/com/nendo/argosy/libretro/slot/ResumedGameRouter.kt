package com.nendo.argosy.libretro.slot

import com.nendo.argosy.core.notification.ResolvedNotice

/**
 * The built-in game resumed in front, main process only. It receives the system Home key as its
 * guide button and the launcher notices that play over it, drawn by its own in-game host.
 */
object ResumedGameRouter {

    private class Claim(
        val owner: Any,
        val onGuideKey: (Boolean) -> Unit,
        val onNotice: (ResolvedNotice) -> Unit
    )

    private val lock = Any()

    @Volatile
    private var claim: Claim? = null

    val hasGameInFront: Boolean get() = claim != null

    fun claim(owner: Any, onGuideKey: (pressed: Boolean) -> Unit, onNotice: (ResolvedNotice) -> Unit) {
        synchronized(lock) { claim = Claim(owner, onGuideKey, onNotice) }
    }

    fun release(owner: Any) {
        synchronized(lock) { if (claim?.owner == owner) claim = null }
    }

    /**
     * Hands a guide press or release to the claimant; false when no built-in game holds the key.
     */
    fun deliverGuideKey(pressed: Boolean): Boolean {
        val current = claim ?: return false
        current.onGuideKey(pressed)
        return true
    }

    /**
     * Hands a notice to the resumed game's own host; false when no built-in game is in front.
     */
    fun deliverNotice(notice: ResolvedNotice): Boolean {
        val current = claim ?: return false
        current.onNotice(notice)
        return true
    }
}
