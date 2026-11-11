package com.nendo.argosy.ui.notification

import com.nendo.argosy.R
import com.nendo.argosy.core.notification.ControllerBurstCard
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.core.notification.PadNotice
import com.nendo.argosy.core.notification.PadNoticeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerNoticeContentTest {

    private fun pad(id: String, kind: PadNoticeKind) = PadNotice(id, "Pad $id", kind)

    @Test
    fun `one connect names the pad in the body with no seat`() {
        val content = controllerNoticeFor(ControllerBurstCard(listOf(pad("a", PadNoticeKind.CONNECTED)), 4_000))

        assertEquals(NotificationText.Res(R.string.notif_controller_connected_title), content?.title)
        assertEquals(NotificationText.Raw("Pad a"), content?.subtitle)
        assertEquals(NotificationType.INFO, content?.type)
        assertEquals(NotificationDuration.MEDIUM, content?.duration)
    }

    @Test
    fun `one disconnect is a warning naming the pad`() {
        val content = controllerNoticeFor(ControllerBurstCard(listOf(pad("a", PadNoticeKind.DISCONNECTED)), 4_000))

        assertEquals(NotificationText.Res(R.string.notif_controller_disconnected_title), content?.title)
        assertEquals(NotificationType.WARNING, content?.type)
    }

    @Test
    fun `a burst counts its pads and drops the names`() {
        val pads = listOf("a", "b", "c").map { pad(it, PadNoticeKind.CONNECTED) }

        val content = controllerNoticeFor(ControllerBurstCard(pads, 2_500))

        assertEquals(NotificationText.Plural(R.plurals.notif_controller_burst_connected_title, 3, listOf(3)), content?.title)
        assertNull(content?.subtitle)
        assertEquals(NotificationDuration.SHORT, content?.duration)
    }

    @Test
    fun `an empty card earns nothing`() {
        assertNull(controllerNoticeFor(ControllerBurstCard(emptyList(), 4_000)))
    }
}
