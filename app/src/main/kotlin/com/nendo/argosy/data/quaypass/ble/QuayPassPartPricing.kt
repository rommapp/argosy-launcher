package com.nendo.argosy.data.quaypass.ble

/**
 * Single source of truth for avatar part costs. Dormant for now: every part is
 * priced at 0 so everything is unlocked. Turning pricing on means populating
 * [categoryCost] (costs vary by category) and, if desired, raising
 * [freeBaselinePerCategory] so the first few indices in each category stay free.
 */
object QuayPassPartPricing {

    private val categoryCost: Map<AvatarCategory, Int> = emptyMap()

    private const val freeBaselinePerCategory: Int = 0

    fun partKey(category: AvatarCategory, index: Int): String = "${category.prefix}-$index"

    fun costFor(category: AvatarCategory, index: Int): Int {
        val base = categoryCost[category] ?: 0
        if (base == 0 || index < freeBaselinePerCategory) return 0
        return base
    }

    fun isUnlocked(category: AvatarCategory, index: Int, owned: Set<String>): Boolean =
        costFor(category, index) == 0 || partKey(category, index) in owned
}
