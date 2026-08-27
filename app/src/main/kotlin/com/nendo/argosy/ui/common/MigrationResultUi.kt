package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.domain.usecase.MigrationResult

/**
 * `MigrationResult` lives in `domain/` and must not import `R`, so the completion notification
 * text is composed here from its raw counts. [resId] lets each call site (global migration,
 * per-platform migration) supply its own string so identical English is never shared between
 * usage sites.
 */
fun MigrationResult.toNotificationText(@StringRes resId: Int): NotificationText =
    NotificationText.Res(resId, listOf(migrated, skipped, failed))

fun MigrationResult.notificationType(): NotificationType =
    if (failed > 0) NotificationType.WARNING else NotificationType.SUCCESS
