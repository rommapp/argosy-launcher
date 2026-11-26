package com.nendo.argosy.data.repository

import com.nendo.argosy.data.local.dao.GameChangeDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface GameScopeSource<T> {
    val order: Comparator<in T>?

    fun idOf(row: T): Long

    suspend fun load(): List<T>

    suspend fun changesSince(seq: Long): GameScopeDelta<T>
}

/**
 * One scope's rows held in memory. The first read loads the scope whole; every later read applies
 * only the rows stamped since, and reloads when the patched rows' tally disagrees with the
 * database's after dropping deleted ids.
 */
class GameScopeCursor<T>(
    private val source: GameScopeSource<T>,
    private val changeDao: GameChangeDao
) {
    private val mutex = Mutex()
    private var rows: List<T>? = null
    private var seq = 0L

    suspend fun current(): List<T> = mutex.withLock {
        val next = rows?.let { advance(it) } ?: load()
        rows = next
        next
    }

    fun observeEachChange(): Flow<List<T>> = flow {
        changeDao.observeScopeTick().conflate().collect {
            emit(current())
            delay(GAME_SCOPE_PACING_MS)
        }
    }

    fun observe(): Flow<List<T>> = observeEachChange().distinctUntilChanged { old, new -> old === new }

    private suspend fun load(): List<T> {
        seq = changeDao.maxChangeSeq()
        return source.load()
    }

    private suspend fun advance(previous: List<T>): List<T>? {
        val delta = source.changesSince(seq)
        if (delta.maxSeq < seq) return null
        val patched = patchGameRows(previous, delta.changes, source::idOf, source.order)
        val settled = if (tallyOf(patched, source::idOf) == delta.tally) {
            patched
        } else {
            retainGameRows(patched, changeDao.getAllIds().toHashSet(), source::idOf)
                .takeIf { tallyOf(it, source::idOf) == delta.tally }
                ?: return null
        }
        seq = delta.maxSeq
        return settled
    }

    private companion object {
        const val GAME_SCOPE_PACING_MS = 1_000L
    }
}
