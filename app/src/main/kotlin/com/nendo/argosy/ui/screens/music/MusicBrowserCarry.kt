package com.nendo.argosy.ui.screens.music

import com.nendo.argosy.domain.model.MusicColumn
import com.nendo.argosy.domain.model.MusicGrouping
import java.util.concurrent.atomic.AtomicReference

/**
 * Where the Music browser stands: the [grouping], the focused column, the column opened on
 * ([openColumnKey], null on the strip) with its focused album and track.
 */
data class MusicBrowserPosition(
    val grouping: MusicGrouping,
    val columnKey: String?,
    val openColumnKey: String? = null,
    val albumKey: String? = null,
    val trackIndex: Int = 0
) {
    companion object {
        fun of(state: MusicUiState): MusicBrowserPosition {
            val contents = state.contents
            return MusicBrowserPosition(
                grouping = state.grouping,
                columnKey = state.focusedColumn?.key,
                openColumnKey = contents?.column?.key,
                albumKey = contents?.focusedAlbum?.key,
                trackIndex = contents?.trackIndex ?: 0
            )
        }
    }
}

/**
 * Carries the Music browser position across a role swap. A browser records its position under
 * the arrangement it was created in; [take] hands a position only to a browser created under the
 * other arrangement, so reopening Music without a swap starts fresh.
 */
class MusicBrowserCarrier {
    private class Entry(val owner: Any, val swapped: Boolean, val position: MusicBrowserPosition)

    private val entry = AtomicReference<Entry?>(null)

    fun record(owner: Any, swapped: Boolean, position: MusicBrowserPosition) {
        entry.set(Entry(owner, swapped, position))
    }

    fun take(swapped: Boolean): MusicBrowserPosition? =
        entry.get()?.takeIf { it.swapped != swapped }?.position

    fun release(owner: Any) {
        entry.updateAndGet { held -> if (held?.owner === owner) null else held }
    }
}

internal fun restoredColumnIndex(columns: List<MusicColumn>, columnKey: String?): Int? =
    columnKey?.let { key -> columns.indexOfFirst { it.key == key }.takeIf { it >= 0 } }
