package com.nendo.argosy.libretro.slot

/**
 * The resumed built-in game that receives the system Home key as its guide button. Main process
 * only.
 */
object GuideKeyRouter {

    private class Claim(val owner: Any, val deliver: (Boolean) -> Unit)

    @Volatile
    private var claim: Claim? = null

    fun claim(owner: Any, deliver: (pressed: Boolean) -> Unit) {
        claim = Claim(owner, deliver)
    }

    fun release(owner: Any) {
        if (claim?.owner == owner) claim = null
    }

    /**
     * Hands a guide press or release to the claimant; false when no built-in game holds the key.
     */
    fun deliver(pressed: Boolean): Boolean {
        val current = claim ?: return false
        current.deliver(pressed)
        return true
    }
}
