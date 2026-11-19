package com.nendo.argosy.data.model

import java.time.Instant

/**
 * One game's recent play for the signed-in account: active time per week, oldest first and
 * ending this week, plus when and on which device its latest session ended.
 */
data class GamePlayActivity(
    val weekActiveMs: List<Long>,
    val lastPlayedAt: Instant?,
    val lastPlayedDevice: String?
)
