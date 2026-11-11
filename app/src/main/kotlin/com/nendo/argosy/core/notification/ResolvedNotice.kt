package com.nendo.argosy.core.notification

import android.os.Bundle

/**
 * A notice with its text already in the user's language, so it can cross into a game slot process
 * or an overlay window that resolves no resources of its own.
 */
data class ResolvedNotice(
    val title: String,
    val body: String? = null,
    val type: NotificationType = NotificationType.INFO,
    val category: NotificationCategory = NotificationCategory.GENERAL,
    val duration: NotificationDuration = NotificationDuration.SHORT,
    val key: String? = null,
    val imagePath: String? = null,
    val accentColor: Int? = null
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_TITLE, title)
        putString(KEY_BODY, body)
        putString(KEY_TYPE, type.name)
        putString(KEY_CATEGORY, category.name)
        putString(KEY_DURATION, duration.name)
        putString(KEY_NOTICE_KEY, key)
        putString(KEY_IMAGE_PATH, imagePath)
        accentColor?.let { putInt(KEY_ACCENT_COLOR, it) }
    }

    companion object {
        private const val KEY_TITLE = "noticeTitle"
        private const val KEY_BODY = "noticeBody"
        private const val KEY_TYPE = "noticeType"
        private const val KEY_CATEGORY = "noticeCategory"
        private const val KEY_DURATION = "noticeDuration"
        private const val KEY_NOTICE_KEY = "noticeKey"
        private const val KEY_IMAGE_PATH = "noticeImagePath"
        private const val KEY_ACCENT_COLOR = "noticeAccentColor"

        fun fromBundle(bundle: Bundle?): ResolvedNotice? {
            val title = bundle?.getString(KEY_TITLE) ?: return null
            return ResolvedNotice(
                title = title,
                body = bundle.getString(KEY_BODY),
                type = enumOr(bundle.getString(KEY_TYPE), NotificationType.INFO),
                category = enumOr(bundle.getString(KEY_CATEGORY), NotificationCategory.GENERAL),
                duration = enumOr(bundle.getString(KEY_DURATION), NotificationDuration.SHORT),
                key = bundle.getString(KEY_NOTICE_KEY),
                imagePath = bundle.getString(KEY_IMAGE_PATH),
                accentColor = if (bundle.containsKey(KEY_ACCENT_COLOR)) bundle.getInt(KEY_ACCENT_COLOR) else null
            )
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
    }
}
