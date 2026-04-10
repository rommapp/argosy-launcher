package com.nendo.argosy.data.netplay

sealed interface NetplayDriver {
    val lastRttNanos: Long
    val lastIncomingNanos: Long
    fun tick()
    fun stop()
}
