package com.nendo.argosy.core.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCriticalTest {

    private fun notice(type: NotificationType, category: NotificationCategory) =
        Notification(title = NotificationText.Raw("title"), type = type, category = category)

    @Test
    fun `only errors and battery notices are critical`() {
        for (type in NotificationType.entries) {
            for (category in NotificationCategory.entries) {
                val expected = type == NotificationType.ERROR || category == NotificationCategory.BATTERY
                assertEquals("$type/$category", expected, notice(type, category).isCritical)
            }
        }
    }

    @Test
    fun `a save synced notice is not critical`() {
        assertEquals(false, notice(NotificationType.SUCCESS, NotificationCategory.SAVE).isCritical)
    }
}
