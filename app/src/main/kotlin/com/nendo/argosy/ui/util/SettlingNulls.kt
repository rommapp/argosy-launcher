@file:OptIn(ExperimentalCoroutinesApi::class)

package com.nendo.argosy.ui.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

private object Unset

/**
 * Emits distinct values, holding a null that follows a value for [settleMillis].
 * A value arriving inside that window replaces the null, so a list that empties
 * while it reloads never reports an absent selection. A null with no value
 * before it passes through at once.
 */
fun <T : Any> Flow<T?>.settlingNulls(settleMillis: Long): Flow<T?> {
    val upstream = this
    return flow {
        var last: Any? = Unset
        emitAll(
            upstream.transformLatest { value ->
                if (value == last) return@transformLatest
                if (value == null && last !== Unset) delay(settleMillis)
                last = value
                emit(value)
            }
        )
    }
}
