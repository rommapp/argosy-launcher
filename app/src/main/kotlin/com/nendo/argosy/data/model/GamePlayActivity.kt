package com.nendo.argosy.data.model

import java.time.Instant

/**
 * One game's recent play for the signed-in account: active time per week, oldest first and
 * ending this week, plus when and on which device its latest counted session ended. A session in a
 * week whose total reads as zero is not counted.
 */
data class GamePlayActivity(
    val weekActiveMs: List<Long>,
    val lastPlayedAt: Instant?,
    val lastPlayedDevice: String?
)
