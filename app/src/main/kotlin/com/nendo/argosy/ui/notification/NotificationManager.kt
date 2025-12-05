package com.nendo.argosy.ui.notification

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "NotificationManager"
private const val DEBOUNCE_DELAY_MS = 500L

@Singleton
class NotificationManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pendingByKey = mutableMapOf<String, Job>()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications.asStateFlow()

    private val _persistentNotification = MutableStateFlow<Notification?>(null)
    val persistentNotification: StateFlow<Notification?> = _persistentNotification.asStateFlow()

    fun show(
        title: String,
        subtitle: String? = null,
        type: NotificationType = NotificationType.INFO,
        imagePath: String? = null,
        duration: NotificationDuration = NotificationDuration.SHORT,
        action: NotificationAction? = null,
        key: String? = null,
        immediate: Boolean = false
    ): String {
        val notification = Notification(
            key = key,
            type = type,
            title = title,
            subtitle = subtitle,
            imagePath = imagePath,
            duration = duration,
            action = action,
            immediate = immediate
        )

        if (key != null) {
            pendingByKey[key]?.cancel()

            if (immediate) {
                pendingByKey.remove(key)
                removeByKey(key)
                addNotification(notification)
            } else {
                pendingByKey[key] = scope.launch {
                    delay(DEBOUNCE_DELAY_MS)
                    pendingByKey.remove(key)
                    removeByKey(key)
                    addNotification(notification)
                }
            }
        } else {
            addNotification(notification)
        }

        return notification.id
    }

    private fun addNotification(notification: Notification) {
        _notifications.update { current ->
            current + notification
        }
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
        pendingByKey[key]?.cancel()
        pendingByKey.remove(key)
        removeByKey(key)
    }

    fun clear() {
        pendingByKey.values.forEach { it.cancel() }
        pendingByKey.clear()
        _notifications.value = emptyList()
        _persistentNotification.value = null
    }

    fun showPersistent(
        title: String,
        subtitle: String? = null,
        key: String,
        progress: NotificationProgress? = null
    ): Boolean {
        val current = _persistentNotification.value
        if (current != null && current.key != key) {
            return false
        }

        _persistentNotification.value = Notification(
            key = key,
            type = NotificationType.INFO,
            title = title,
            subtitle = subtitle,
            progress = progress
        )
        return true
    }

    fun updatePersistent(
        key: String,
        title: String? = null,
        subtitle: String? = null,
        progress: NotificationProgress? = null
    ) {
        val current = _persistentNotification.value ?: return
        if (current.key != key) return

        _persistentNotification.value = current.copy(
            title = title ?: current.title,
            subtitle = subtitle ?: current.subtitle,
            progress = progress ?: current.progress
        )
    }

    fun completePersistent(
        key: String,
        title: String,
        subtitle: String? = null,
        type: NotificationType
    ) {
        Log.d(TAG, "completePersistent: key=$key, title=$title, subtitle=$subtitle, type=$type")
        val current = _persistentNotification.value
        Log.d(TAG, "completePersistent: current persistent key=${current?.key}")
        if (current == null) {
            Log.d(TAG, "completePersistent: no current persistent notification, returning")
            return
        }
        if (current.key != key) {
            Log.d(TAG, "completePersistent: key mismatch (current=${current.key}, requested=$key), returning")
            return
        }

        Log.d(TAG, "completePersistent: clearing persistent and showing completion notification")
        _persistentNotification.value = null

        show(
            title = title,
            subtitle = subtitle,
            type = type,
            duration = NotificationDuration.SHORT,
            immediate = true
        )
        Log.d(TAG, "completePersistent: done")
    }
}
