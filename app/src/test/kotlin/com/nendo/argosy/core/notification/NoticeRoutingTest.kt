package com.nendo.argosy.core.notification

import com.nendo.argosy.data.preferences.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeRoutingTest {

    private val launcher = NoticeStage(argosyVisible = true, builtInGameInFront = false)
    private val launcherBesideBuiltIn = NoticeStage(argosyVisible = true, builtInGameInFront = true)
    private val builtInGame = NoticeStage(argosyVisible = false, builtInGameInFront = true)
    private val otherApp = NoticeStage(argosyVisible = false, builtInGameInFront = false)
    private val allOn = NoticePreferences()
    private val allOff = NoticePreferences(enabled = false)

    private fun route(
        type: NotificationType,
        category: NotificationCategory,
        stage: NoticeStage,
        preferences: NoticePreferences = allOn
    ) = routeNotice(type, category, preferences, stage)

    @Test
    fun `a visible launcher shows every category in the app, even beside a built-in game`() {
        NotificationCategory.entries.forEach { category ->
            assertEquals(NoticeRoute.IN_APP, route(NotificationType.INFO, category, launcher))
            assertEquals(NoticeRoute.IN_APP, route(NotificationType.INFO, category, launcherBesideBuiltIn))
        }
    }

    @Test
    fun `general info and success wait for the launcher while anything else is in front`() {
        listOf(NotificationType.INFO, NotificationType.SUCCESS).forEach { type ->
            assertEquals(NoticeRoute.HOLD, route(type, NotificationCategory.GENERAL, builtInGame))
            assertEquals(NoticeRoute.HOLD, route(type, NotificationCategory.GENERAL, otherApp))
        }
    }

    @Test
    fun `general warnings and errors show over a built-in game and over another app`() {
        listOf(NotificationType.WARNING, NotificationType.ERROR).forEach { type ->
            assertEquals(NoticeRoute.BUILT_IN_GAME, route(type, NotificationCategory.GENERAL, builtInGame))
            assertEquals(NoticeRoute.OVER_APP, route(type, NotificationCategory.GENERAL, otherApp))
        }
    }

    @Test
    fun `every non-general category shows over games whatever its type`() {
        NotificationCategory.entries.filter { it != NotificationCategory.GENERAL }.forEach { category ->
            NotificationType.entries.forEach { type ->
                assertEquals(NoticeRoute.BUILT_IN_GAME, route(type, category, builtInGame))
                assertEquals(NoticeRoute.OVER_APP, route(type, category, otherApp))
            }
        }
    }

    @Test
    fun `notifications off drops everything but battery, wherever Argosy stands`() {
        listOf(launcher, builtInGame, otherApp).forEach { stage ->
            NotificationCategory.entries.filter { it != NotificationCategory.BATTERY }.forEach { category ->
                assertEquals(NoticeRoute.DROP, route(NotificationType.ERROR, category, stage, allOff))
            }
        }
        assertEquals(NoticeRoute.IN_APP, route(NotificationType.WARNING, NotificationCategory.BATTERY, launcher, allOff))
        assertEquals(
            NoticeRoute.BUILT_IN_GAME,
            route(NotificationType.WARNING, NotificationCategory.BATTERY, builtInGame, allOff)
        )
        assertEquals(NoticeRoute.OVER_APP, route(NotificationType.WARNING, NotificationCategory.BATTERY, otherApp, allOff))
    }

    @Test
    fun `a disabled category is dropped and its neighbours are not`() {
        val downloadsOff = NoticePreferences(disabledCategories = setOf(NotificationCategory.DOWNLOAD))
        listOf(launcher, builtInGame, otherApp).forEach { stage ->
            assertEquals(
                NoticeRoute.DROP,
                route(NotificationType.SUCCESS, NotificationCategory.DOWNLOAD, stage, downloadsOff)
            )
        }
        assertEquals(
            NoticeRoute.OVER_APP,
            route(NotificationType.SUCCESS, NotificationCategory.SAVE, otherApp, downloadsOff)
        )
    }

    @Test
    fun `the downloads finished setting maps to the download category`() {
        assertEquals(
            setOf(NotificationCategory.DOWNLOAD),
            NoticePreferences.from(AppPreferences(notifyDownloadsFinished = false)).disabledCategories
        )
        assertEquals(emptySet<NotificationCategory>(), NoticePreferences.from(AppPreferences()).disabledCategories)
        assertEquals(false, NoticePreferences.from(AppPreferences(notificationsEnabled = false)).enabled)
    }

    @Test
    fun `the battery and charging settings map to their own categories`() {
        assertEquals(
            setOf(NotificationCategory.BATTERY),
            NoticePreferences.from(AppPreferences(notifyBattery = false)).disabledCategories
        )
        assertEquals(
            setOf(NotificationCategory.CHARGING),
            NoticePreferences.from(AppPreferences(notifyCharging = false)).disabledCategories
        )
    }

    @Test
    fun `the controllers setting maps to the controller category`() {
        assertEquals(
            setOf(NotificationCategory.CONTROLLER),
            NoticePreferences.from(AppPreferences(notifyControllers = false)).disabledCategories
        )
    }

    @Test
    fun `battery off drops battery cards even with notifications off`() {
        val batteryOffAllOff = NoticePreferences(
            enabled = false,
            disabledCategories = setOf(NotificationCategory.BATTERY)
        )
        listOf(launcher, builtInGame, otherApp).forEach { stage ->
            assertEquals(
                NoticeRoute.DROP,
                route(NotificationType.ERROR, NotificationCategory.BATTERY, stage, batteryOffAllOff)
            )
        }
    }
}
