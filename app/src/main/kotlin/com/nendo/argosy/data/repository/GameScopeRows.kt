package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameScopeTally

data class GameRowChange<T>(
    val id: Long,
    val rowInScope: T?
)

data class GameScopeDelta<T>(
    val changes: List<GameRowChange<T>>,
    val tally: GameScopeTally,
    val maxSeq: Long
)

fun <T> patchGameRows(
    current: List<T>,
    changes: List<GameRowChange<T>>,
    idOf: (T) -> Long,
    order: Comparator<in T>?
): List<T> {
    if (changes.isEmpty()) return current
    val latest = LinkedHashMap<Long, GameRowChange<T>>(changes.size)
    changes.forEach { latest[it.id] = it }
    val unchanged = HashSet<Long>()
    val kept = ArrayList<T>(current.size)
    var removedAny = false
    for (row in current) {
        val id = idOf(row)
        val change = latest[id]
        when {
            change == null -> kept += row
            change.rowInScope == row -> {
                kept += row
                unchanged += id
            }
            else -> removedAny = true
        }
    }
    val incoming = latest.values.mapNotNull { change -> change.rowInScope?.takeIf { change.id !in unchanged } }
    if (!removedAny && incoming.isEmpty()) return current
    if (incoming.isEmpty()) return kept
    if (order == null) return kept + incoming
    return mergeOrdered(kept, incoming.sortedWith(order), order)
}

fun <T> tallyOf(rows: List<T>, idOf: (T) -> Long): GameScopeTally {
    var idSum = 0L
    var idSquareSum = 0L
    rows.forEach { row ->
        val id = idOf(row)
        idSum += id
        idSquareSum += id * id
    }
    return GameScopeTally(rows.size, idSum, idSquareSum)
}

fun <T> retainGameRows(rows: List<T>, presentIds: Set<Long>, idOf: (T) -> Long): List<T> {
    val retained = rows.filter { idOf(it) in presentIds }
    return if (retained.size == rows.size) rows else retained
}

private fun <T> mergeOrdered(first: List<T>, second: List<T>, order: Comparator<in T>): List<T> {
    val merged = ArrayList<T>(first.size + second.size)
    var i = 0
    var j = 0
    while (i < first.size && j < second.size) {
        if (order.compare(second[j], first[i]) < 0) merged += second[j++] else merged += first[i++]
    }
    while (i < first.size) merged += first[i++]
    while (j < second.size) merged += second[j++]
    return merged
}

/**
 * SQLite's BINARY collation: strings compare by code point, the order of their UTF-8 bytes.
 */
fun compareBinaryCollation(a: String, b: String): Int {
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val ca = a.codePointAt(i)
        val cb = b.codePointAt(j)
        if (ca != cb) return ca.compareTo(cb)
        i += Character.charCount(ca)
        j += Character.charCount(cb)
    }
    return (a.length - i).compareTo(b.length - j)
}
