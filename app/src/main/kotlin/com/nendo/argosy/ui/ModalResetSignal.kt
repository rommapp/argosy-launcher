package com.nendo.argosy.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModalResetSignal @Inject constructor() {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal: SharedFlow<Unit> = _signal.asSharedFlow()

    fun emit() {
        _signal.tryEmit(Unit)
    }
}
