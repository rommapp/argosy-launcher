package com.nendo.argosy.libretro.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.ui.notification.InGameNotice
import com.nendo.argosy.ui.notification.InGameNoticeIcon
import com.nendo.argosy.ui.notification.NotificationSeverity
import com.nendo.argosy.ui.theme.generated.ColorTokens

data class AchievementUnlock(
    val id: Long,
    val title: String,
    val description: String?,
    val points: Int,
    val badgeUrl: String?,
    val isHardcore: Boolean
)

fun AchievementUnlock.toInGameNotice(): InGameNotice = InGameNotice(
    severity = NotificationSeverity.SUCCESS,
    title = NotificationText.Plural(
        id = if (isHardcore) {
            R.plurals.ingame_achievement_notice_hardcore_title
        } else {
            R.plurals.ingame_achievement_notice_unlocked_title
        },
        count = points,
        args = listOf(points)
    ),
    body = NotificationText.Raw(title),
    durationMs = NotificationDuration.MEDIUM.ms,
    icon = badgeUrl?.let { InGameNoticeIcon.Image(it) } ?: InGameNoticeIcon.Glyph(Icons.Filled.EmojiEvents),
    accent = if (isHardcore) {
        ColorTokens.Domain.AchievementTier.hardcore
    } else {
        ColorTokens.Domain.AchievementTier.softcore
    }
)
