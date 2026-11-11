package com.nendo.argosy.core.notification

import android.content.Context
import com.nendo.argosy.core.locale.LocaleHelper
import com.nendo.argosy.data.preferences.AppPreferencesRepository
import com.nendo.argosy.data.preferences.SessionStateStore
import com.nendo.argosy.libretro.slot.ResumedGameRouter
import com.nendo.argosy.util.SafeCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val DEBOUNCE_DELAY_MS = 500L
private const val OVER_APP_BUFFER = 16

/**
 * Every launcher notice passes [routeNotice] once, when it is posted. In-app and held notices wait
 * in [notifications] until a started surface draws them; notices for a resumed built-in game go to
 * that game's own host; the rest are emitted on [overAppNotices] for the window drawn above other
 * apps.
 */
@Singleton
class NotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    appPreferencesRepository: AppPreferencesRepository
) {

    private val scope = SafeCoroutineScope(Dispatchers.Main, "NotificationManager")
    private val pendingByKey = mutableMapOf<String, Job>()

    val preferences: StateFlow<NoticePreferences> = appPreferencesRepository.preferences
        .map { NoticePreferences.from(it) }
        .stateIn(scope, SharingStarted.Eagerly, NoticePreferences())

    private val startedSurfaces = MutableStateFlow(0)

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _persistentNotifications = MutableStateFlow<List<Notification>>(emptyList())
    val persistentNotifications: StateFlow<List<Notification>> = _persistentNotifications.asStateFlow()

    private val _overAppNotices = MutableSharedFlow<ResolvedNotice>(
        extraBufferCapacity = OVER_APP_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val overAppNotices: SharedFlow<ResolvedNotice> = _overAppNotices.asSharedFlow()

    fun onSurfaceStarted() {
        startedSurfaces.update { it + 1 }
    }

    fun onSurfaceStopped() {
        startedSurfaces.update { (it - 1).coerceAtLeast(0) }
    }

    fun show(
        title: NotificationText,
        subtitle: NotificationText? = null,
        type: NotificationType = NotificationType.INFO,
        imagePath: String? = null,
        platformSlug: String? = null,
        duration: NotificationDuration = NotificationDuration.SHORT,
        key: String? = null,
        immediate: Boolean = false,
        accentColor: Int? = null,
        category: NotificationCategory = NotificationCategory.GENERAL
    ): String {
        val notification = Notification(
            key = key,
            type = type,
            title = title,
            subtitle = subtitle,
            imagePath = imagePath,
            platformSlug = platformSlug,
            duration = duration,
            immediate = immediate,
            accentColor = accentColor,
            category = category
        )

        if (key == null) {
            post(notification)
            return notification.id
        }

        scope.launch {
            pendingByKey.remove(key)?.cancel()
            if (immediate) {
                removeByKey(key)
                post(notification)
            } else {
                pendingByKey[key] = scope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    pendingByKey.remove(key)
                    removeByKey(key)
                    post(notification)
                }
            }
        }

        return notification.id
    }

    private fun post(notification: Notification) {
        val stage = NoticeStage(
            argosyVisible = startedSurfaces.value > 0,
            builtInGameInFront = ResumedGameRouter.hasGameInFront
        )
        when (routeNotice(notification.type, notification.category, preferences.value, stage)) {
            NoticeRoute.DROP -> Unit
            NoticeRoute.IN_APP, NoticeRoute.HOLD -> _notifications.update { current -> current + notification }
            NoticeRoute.BUILT_IN_GAME -> scope.launch {
                val resolved = resolve(notification)
                if (!ResumedGameRouter.deliverNotice(resolved)) _overAppNotices.tryEmit(resolved)
            }
            NoticeRoute.OVER_APP -> scope.launch { _overAppNotices.tryEmit(resolve(notification)) }
        }
    }

    private fun resolve(notification: Notification): ResolvedNotice {
        val localized = LocaleHelper.wrap(context, SessionStateStore(context).getAppLanguage())
        return ResolvedNotice(
            title = notification.title.resolve(localized),
            body = notification.subtitle?.resolve(localized),
            type = notification.type,
            category = notification.category,
            duration = notification.duration,
            key = notification.key,
            imagePath = notification.imagePath,
            accentColor = notification.accentColor
        )
    }

    private fun removeByKey(key: String) {
        _notifications.update { current ->
            current.filterNot { it.key == key }
        }
    }

    fun dismiss(id: String) {
        _notifications.update { current ->
            current.filterNot { it.id == id }
        }
    }

    fun dismissByKey(key: String) {
        scope.launch { pendingByKey.remove(key)?.cancel() }
        removeByKey(key)
        removePersistent(key)
    }

    fun clear() {
        scope.launch {
            pendingByKey.values.forEach { it.cancel() }
            pendingByKey.clear()
        }
        _notifications.value = emptyList()
        _persistentNotifications.value = emptyList()
    }

    fun showPersistent(
        title: NotificationText,
        subtitle: NotificationText? = null,
        key: String,
        progress: NotificationProgress? = null,
        platformSlug: String? = null
    ) {
        val notification = Notification(
            key = key,
            type = NotificationType.INFO,
            title = title,
            subtitle = subtitle,
            progress = progress,
            platformSlug = platformSlug
        )
        _persistentNotifications.update { current ->
            current.filterNot { it.key == key } + notification
        }
    }

    fun updatePersistent(
        key: String,
        title: NotificationText? = null,
        subtitle: NotificationText? = null,
        progress: NotificationProgress? = null,
        platformSlug: String? = null
    ) {
        _persistentNotifications.update { current ->
            current.map { existing ->
                if (existing.key != key) {
                    existing
                } else {
                    existing.copy(
                        title = title ?: existing.title,
                        subtitle = subtitle ?: existing.subtitle,
                        progress = progress ?: existing.progress,
                        platformSlug = platformSlug ?: existing.platformSlug
                    )
                }
            }
        }
    }

    fun completePersistent(
        key: String,
        title: NotificationText,
        subtitle: NotificationText? = null,
        type: NotificationType,
        platformSlug: String? = null,
        duration: NotificationDuration = NotificationDuration.SHORT
    ) {
        removePersistent(key)
        show(
            title = title,
            subtitle = subtitle,
            type = type,
            platformSlug = platformSlug,
            duration = duration,
            immediate = true
        )
    }

    private fun removePersistent(key: String) {
        _persistentNotifications.update { current ->
            current.filterNot { it.key == key }
        }
    }
}
