package com.nendo.argosy.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityHistoryRulesTest {

    private fun notice(
        type: NotificationType,
        category: NotificationCategory,
        key: String? = null,
        subtitle: String? = null
    ) = Notification(
        key = key,
        type = type,
        title = NotificationText.Raw("title"),
        subtitle = subtitle?.let { NotificationText.Raw(it) },
        category = category
    )

    private fun recordAll(vararg notices: Notification): List<ActivityEntry> =
        notices.foldIndexed(emptyList()) { index, entries, notice ->
            ActivityHistoryRules.record(entries, notice, nowMillis = index.toLong())
        }

    @Test
    fun `warnings and errors are kept one by one and unseen`() {
        val entries = recordAll(
            notice(NotificationType.WARNING, NotificationCategory.GENERAL),
            notice(NotificationType.ERROR, NotificationCategory.SAVE)
        )

        assertEquals(2, entries.size)
        assertTrue(entries.all { it.kind == ActivityEntryKind.NOTICE && it.unseen })
        assertEquals(NotificationType.ERROR, entries.first().latest.type)
    }

    @Test
    fun `controller and battery notices are kept whatever their type`() {
        val entries = recordAll(
            notice(NotificationType.INFO, NotificationCategory.CONTROLLER),
            notice(NotificationType.SUCCESS, NotificationCategory.BATTERY)
        )

        assertEquals(listOf(ActivityEntryKind.NOTICE, ActivityEntryKind.NOTICE), entries.map { it.kind })
    }

    @Test
    fun `general info, social and charging notices are left out`() {
        val entries = recordAll(
            notice(NotificationType.INFO, NotificationCategory.GENERAL),
            notice(NotificationType.SUCCESS, NotificationCategory.GENERAL),
            notice(NotificationType.INFO, NotificationCategory.SOCIAL),
            notice(NotificationType.SUCCESS, NotificationCategory.CHARGING)
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `successful saves fold into one line with the latest title and light nothing`() {
        val entries = recordAll(
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE, key = "sync-1", subtitle = "Zelda"),
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE, key = "sync-2", subtitle = "Metroid")
        )

        val line = entries.single()
        assertEquals(ActivityEntryKind.SAVES_SYNCED, line.kind)
        assertEquals(2, line.count)
        assertEquals(NotificationText.Raw("Metroid"), line.latest.subtitle)
        assertEquals(1L, line.atMillis)
        assertFalse(line.unseen)
    }

    @Test
    fun `info save and download notices do not count as finished`() {
        val entries = recordAll(
            notice(NotificationType.INFO, NotificationCategory.SAVE, key = "sync-1"),
            notice(NotificationType.INFO, NotificationCategory.DOWNLOAD)
        )

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `saves and downloads keep separate lines`() {
        val entries = recordAll(
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE),
            notice(NotificationType.SUCCESS, NotificationCategory.DOWNLOAD),
            notice(NotificationType.SUCCESS, NotificationCategory.DOWNLOAD)
        )

        assertEquals(listOf(ActivityEntryKind.DOWNLOADS_FINISHED, ActivityEntryKind.SAVES_SYNCED), entries.map { it.kind })
        assertEquals(listOf(2, 1), entries.map { it.count })
    }

    @Test
    fun `a folded line moves to the top when it grows`() {
        val entries = recordAll(
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE),
            notice(NotificationType.WARNING, NotificationCategory.GENERAL),
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE)
        )

        assertEquals(listOf(ActivityEntryKind.SAVES_SYNCED, ActivityEntryKind.NOTICE), entries.map { it.kind })
        assertEquals(2, entries.first().count)
    }

    @Test
    fun `a keyed notice replaces the earlier one with that key`() {
        val entries = recordAll(
            notice(NotificationType.WARNING, NotificationCategory.GENERAL, key = "pad"),
            notice(NotificationType.ERROR, NotificationCategory.GENERAL),
            notice(NotificationType.ERROR, NotificationCategory.GENERAL, key = "pad")
        )

        assertEquals(2, entries.size)
        assertEquals("pad", entries.first().lastKey)
        assertEquals(NotificationType.ERROR, entries.first().latest.type)
    }

    @Test
    fun `unkeyed notices never replace each other`() {
        val entries = recordAll(
            notice(NotificationType.WARNING, NotificationCategory.GENERAL),
            notice(NotificationType.WARNING, NotificationCategory.GENERAL)
        )

        assertEquals(2, entries.size)
    }

    @Test
    fun `the list keeps the newest entries up to the cap`() {
        val notices = (0 until ActivityHistoryRules.CAP + 5).map {
            notice(NotificationType.WARNING, NotificationCategory.GENERAL, key = "n$it")
        }
        val entries = recordAll(*notices.toTypedArray())

        assertEquals(ActivityHistoryRules.CAP, entries.size)
        assertEquals("n${ActivityHistoryRules.CAP + 4}", entries.first().lastKey)
        assertEquals("n5", entries.last().lastKey)
    }

    @Test
    fun `marking seen clears every unseen entry and keeps the list otherwise`() {
        val entries = recordAll(
            notice(NotificationType.WARNING, NotificationCategory.GENERAL),
            notice(NotificationType.SUCCESS, NotificationCategory.SAVE)
        )

        val seen = ActivityHistoryRules.markSeen(entries)

        assertFalse(ActivityLog(seen).hasUnseen)
        assertEquals(entries.map { it.id }, seen.map { it.id })
        assertTrue(ActivityLog(entries).hasUnseen)
    }

    @Test
    fun `only a kept notice lights the unseen dot`() {
        val folded = recordAll(notice(NotificationType.SUCCESS, NotificationCategory.DOWNLOAD))
        val kept = recordAll(notice(NotificationType.INFO, NotificationCategory.CONTROLLER))

        assertFalse(ActivityLog(folded).hasUnseen)
        assertTrue(ActivityLog(kept).hasUnseen)
    }
}
