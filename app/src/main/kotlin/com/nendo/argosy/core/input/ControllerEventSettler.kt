package com.nendo.argosy.core.input

/**
 * Holds each connect for [settleMs] before it is announced. A pad that goes away, or stops
 * counting as a pad, inside that window was never announced, so neither its connect nor its
 * disconnect is: the kernel device a pad driver mutes moments after plug-in, or a device that only
 * flickers, stays silent. A disconnect of an announced pad passes at once.
 */
class ControllerEventSettler(private val settleMs: Long) {
    private val pending = LinkedHashMap<String, Pair<ConnectedPad, Long>>()

    fun offer(events: List<ControllerEvent>, now: Long): List<ControllerEvent> = buildList {
        events.forEach { event ->
            val id = event.controller.controllerId
            when (event.change) {
                ControllerChange.CONNECTED -> pending[id] = event.controller to now
                ControllerChange.DISCONNECTED -> if (pending.remove(id) == null) add(event)
            }
        }
        addAll(due(now))
    }

    fun due(now: Long): List<ControllerEvent> {
        val ready = pending.filterValues { (_, at) -> now - at >= settleMs }
        ready.keys.forEach { pending.remove(it) }
        return ready.values.map { (pad, _) -> ControllerEvent(ControllerChange.CONNECTED, pad) }
    }

    fun nextDueIn(now: Long): Long? =
        pending.values.minOfOrNull { (_, at) -> (at + settleMs - now).coerceAtLeast(0) }
}
