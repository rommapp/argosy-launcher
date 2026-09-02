package com.nendo.argosy.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nendo.argosy.R
import com.nendo.argosy.core.game.AchievementType
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens

/**
 * Label for a RetroAchievements type token, or null for the untyped majority so no badge draws.
 */
@StringRes
fun achievementTypeLabelRes(type: String?): Int? = when (type) {
    AchievementType.MISSABLE -> R.string.gamedetail_achievement_type_missable
    AchievementType.PROGRESSION -> R.string.gamedetail_achievement_type_progression
    AchievementType.WIN_CONDITION -> R.string.gamedetail_achievement_type_win_condition
    else -> null
}

@Composable
fun achievementTypeColor(type: String?): Color = when (type) {
    AchievementType.MISSABLE -> LocalLauncherTheme.current.semanticColors.warning
    AchievementType.PROGRESSION -> LocalLauncherTheme.current.semanticColors.info
    AchievementType.WIN_CONDITION -> ColorTokens.Domain.trophyAmber
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
