package com.nendo.argosy.quaypass

import com.nendo.argosy.ui.quaypass.avatar.AvatarCategory
import com.nendo.argosy.ui.quaypass.avatar.QuayPassPartPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuayPassPartPricingTest {

    @Test
    fun `part key follows the category-index form`() {
        assertEquals("hair-42", QuayPassPartPricing.partKey(AvatarCategory.Hair, 42))
    }

    @Test
    fun `every part is free while pricing is dormant`() {
        for (category in AvatarCategory.entries) {
            for (index in 0..200) {
                assertEquals(0, QuayPassPartPricing.costFor(category, index))
            }
        }
    }

    @Test
    fun `free parts are unlocked with no ownership`() {
        assertTrue(QuayPassPartPricing.isUnlocked(AvatarCategory.Hat, 9, emptySet()))
    }
}
