package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.domain.usecase.PurgeResult

/**
 * `PurgeResult` lives in `domain/` and must not import `R`, so the completion notification
 * text is composed here from its raw counts.
 */
fun PurgeResult.toNotificationText(@StringRes resId: Int): NotificationText =
    NotificationText.Res(resId, listOf(gamesDeleted, bytesFree / (1024 * 1024)))
