package com.nendo.argosy.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Glyph for a player-count range such as "1-8": one head for a solo game, two for a pair, a
 * group for three or more or an open-ended range.
 */
fun playerCountGlyph(players: String): ImageVector {
    val most = Regex("\\d+").findAll(players).map { it.value.toInt() }.maxOrNull()
    return when {
        most == null || most > 2 || players.contains('+') -> Icons.Default.Groups
        most == 2 -> Icons.Default.People
        else -> Icons.Default.Person
    }
}
