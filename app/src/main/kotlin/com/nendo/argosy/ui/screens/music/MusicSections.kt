package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicColumnKind
import java.util.Locale

/**
 * Jump sections for the Music columns: the first letter of a column's title, or the decade of a
 * year column. Recent and the unknown columns have no section, so a jump never lands on them.
 */
object MusicSections {
    private const val OTHER = "#"
    private const val DECADE = 10

    fun sectionOf(column: MusicColumn): String? {
        if (column.kind == MusicColumnKind.RECENT || column.title.isBlank()) return null
        if (column.kind == MusicColumnKind.YEAR) {
            val year = column.title.toIntOrNull() ?: return null
            return "${year / DECADE * DECADE}s"
        }
        val first = column.title.trim().first()
        return when {
            first.isDigit() -> OTHER
            first.isLetter() -> first.toString().uppercase(Locale.ROOT)
            else -> OTHER
        }
    }

    /**
     * The index of the first column of the section [delta] sections away from the one holding
     * [focus], wrapping around; null when the columns hold fewer than two sections.
     */
    fun jumpTarget(columns: List<MusicColumn>, focus: Int, delta: Int): Int? {
        val starts = mutableListOf<Pair<String, Int>>()
        columns.forEachIndexed { index, column ->
            val section = sectionOf(column) ?: return@forEachIndexed
            if (starts.lastOrNull()?.first != section) starts += section to index
        }
        if (starts.size < 2) return null
        val current = sectionOf(columns.getOrNull(focus) ?: return null)
        val at = starts.indexOfLast { (section, start) -> section == current && start <= focus }
            .takeIf { it >= 0 }
            ?: starts.indexOfLast { (_, start) -> start <= focus }
        val base = if (at < 0) (if (delta > 0) -1 else 0) else at
        return starts[(base + delta).mod(starts.size)].second
    }
}
