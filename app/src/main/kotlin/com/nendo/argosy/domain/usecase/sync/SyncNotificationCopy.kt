package com.nendo.argosy.domain.usecase.sync

import com.nendo.argosy.core.notification.NotificationText

/**
 * The words the sync notifications use. The use cases decide when a notification fires and how
 * its persistent lifecycle is paired; this decides what it says, so the copy can live in string
 * resources without a domain class importing R.
 *
 * Library and platform sync have separate members even where their English matches today.
 * Identical text at two usage sites is two strings, because a single shared one cannot be
 * reworded for one caller without silently rewording the other.
 */
interface SyncNotificationCopy {

    fun libraryStartFailedTitle(): NotificationText

    fun libraryFailureDetail(rawMessage: String?): NotificationText

    fun libraryProgressTitle(): NotificationText

    fun libraryProgressStarting(): NotificationText

    fun libraryProgressPlatform(platformName: String, gamesDone: Int, gamesTotal: Int): NotificationText

    fun libraryCompleteTitle(): NotificationText

    fun libraryCompleteCounts(added: Int, updated: Int, removed: Int): NotificationText

    fun libraryCompletedWithErrorsTitle(): NotificationText

    fun libraryFailedPlatforms(count: Int): NotificationText

    fun libraryFailedTitle(): NotificationText

    fun platformProgressTitle(platformName: String): NotificationText

    fun platformProgressFetching(): NotificationText

    fun platformCompleteTitle(platformName: String): NotificationText

    fun platformCompleteCounts(added: Int, updated: Int, removed: Int): NotificationText

    fun platformCompletedWithErrorsTitle(): NotificationText

    fun platformErrorDetail(firstError: String?): NotificationText

    fun platformFailedTitle(): NotificationText
}
