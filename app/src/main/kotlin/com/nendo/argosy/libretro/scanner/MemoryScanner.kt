package com.nendo.argosy.libretro.scanner

data class MemoryMatch(
    val address: Int,
    val currentValue: Int,
    val previousValue: Int? = null
)

sealed class NarrowResult {
    data class Success(val matches: List<MemoryMatch>) : NarrowResult()
    data object NoChanges : NarrowResult()
    data object NotReady : NarrowResult()
}

class MemoryScanner {
    private var snapshot: ByteArray? = null
    private var candidates: Set<Int>? = null
    private var canCompare: Boolean = true
    private var lastDisplayResults: List<MemoryMatch> = emptyList()
    private var lastValueFilter: Int? = null

    fun takeSnapshot(ram: ByteArray): Boolean {
        if (!canCompare) return false
        snapshot = ram.copyOf()
        candidates = (0 until ram.size).toSet()
        lastDisplayResults = emptyList()
        lastValueFilter = null
        canCompare = false
        return true
    }

    fun compareChanged(freshRam: ByteArray): List<MemoryMatch> {
        if (!canCompare) return lastDisplayResults
        val prevSnapshot = snapshot ?: return emptyList()
        val searchSet = candidates ?: return emptyList()

        val matches = searchSet.filter { addr ->
            if (addr >= freshRam.size || addr >= prevSnapshot.size) return@filter false
            val current = freshRam[addr].toInt() and 0xFF
            val previous = prevSnapshot[addr].toInt() and 0xFF
            current != previous
        }.toSet()

        candidates = matches
        snapshot = freshRam.copyOf()
        canCompare = false
        lastValueFilter = null
        lastDisplayResults = buildResults(freshRam, prevSnapshot, matches)
        return lastDisplayResults
    }

    fun compareSame(freshRam: ByteArray): List<MemoryMatch> {
        if (!canCompare) return lastDisplayResults
        val prevSnapshot = snapshot ?: return emptyList()
        val searchSet = candidates ?: return emptyList()

        val matches = searchSet.filter { addr ->
            if (addr >= freshRam.size || addr >= prevSnapshot.size) return@filter false
            val current = freshRam[addr].toInt() and 0xFF
            val previous = prevSnapshot[addr].toInt() and 0xFF
            current == previous
        }.toSet()

        candidates = matches
        snapshot = freshRam.copyOf()
        canCompare = false
        lastValueFilter = null
        lastDisplayResults = buildResults(freshRam, prevSnapshot, matches)
        return lastDisplayResults
    }

    fun filterByValue(freshRam: ByteArray, value: Int): List<MemoryMatch> {
        val searchSet = candidates ?: return emptyList()
        val prevSnapshot = snapshot

        val matches = searchSet.filter { addr ->
            if (addr >= freshRam.size) return@filter false
            val current = freshRam[addr].toInt() and 0xFF
            current == value
        }

        lastValueFilter = value
        lastDisplayResults = matches.take(MAX_RESULTS).map { addr ->
            MemoryMatch(
                address = addr,
                currentValue = freshRam[addr].toInt() and 0xFF,
                previousValue = prevSnapshot?.getOrNull(addr)?.toInt()?.and(0xFF)
            )
        }
        return lastDisplayResults
    }

    fun refreshResults(freshRam: ByteArray): List<MemoryMatch> {
        val searchSet = candidates ?: return emptyList()
        val prevSnapshot = snapshot

        val filter = lastValueFilter
        val toDisplay = if (filter != null) {
            searchSet.filter { addr ->
                if (addr >= freshRam.size) return@filter false
                val current = freshRam[addr].toInt() and 0xFF
                current == filter
            }
        } else {
            searchSet.toList()
        }

        lastDisplayResults = toDisplay.take(MAX_RESULTS).map { addr ->
            MemoryMatch(
                address = addr,
                currentValue = freshRam[addr].toInt() and 0xFF,
                previousValue = prevSnapshot?.getOrNull(addr)?.toInt()?.and(0xFF)
            )
        }
        return lastDisplayResults
    }

    private fun buildResults(freshRam: ByteArray, prevSnapshot: ByteArray, matches: Set<Int>): List<MemoryMatch> {
        return matches.take(MAX_RESULTS).map { addr ->
            MemoryMatch(
                address = addr,
                currentValue = freshRam[addr].toInt() and 0xFF,
                previousValue = prevSnapshot.getOrNull(addr)?.toInt()?.and(0xFF)
            )
        }
    }

    fun narrowChanged(freshRam: ByteArray): NarrowResult {
        if (!canCompare) return NarrowResult.NotReady
        val prevSnapshot = snapshot ?: return NarrowResult.NotReady
        val searchSet = candidates ?: return NarrowResult.NotReady

        val matches = searchSet.filter { addr ->
            if (addr >= freshRam.size || addr >= prevSnapshot.size) return@filter false
            val current = freshRam[addr].toInt() and 0xFF
            val previous = prevSnapshot[addr].toInt() and 0xFF
            current != previous
        }.toSet()

        if (matches.isEmpty()) {
            return NarrowResult.NoChanges
        }

        candidates = matches
        snapshot = freshRam.copyOf()
        canCompare = false
        lastValueFilter = null
        lastDisplayResults = buildResults(freshRam, prevSnapshot, matches)
        return NarrowResult.Success(lastDisplayResults)
    }

    fun narrowSame(freshRam: ByteArray): NarrowResult {
        if (!canCompare) return NarrowResult.NotReady
        val prevSnapshot = snapshot ?: return NarrowResult.NotReady
        val searchSet = candidates ?: return NarrowResult.NotReady

        val matches = searchSet.filter { addr ->
            if (addr >= freshRam.size || addr >= prevSnapshot.size) return@filter false
            val current = freshRam[addr].toInt() and 0xFF
            val previous = prevSnapshot[addr].toInt() and 0xFF
            current == previous
        }.toSet()

        if (matches.isEmpty()) {
            return NarrowResult.NoChanges
        }

        candidates = matches
        snapshot = freshRam.copyOf()
        canCompare = false
        lastValueFilter = null
        lastDisplayResults = buildResults(freshRam, prevSnapshot, matches)
        return NarrowResult.Success(lastDisplayResults)
    }

    fun markGameRan() {
        canCompare = true
    }

    fun canCompare(): Boolean = canCompare && snapshot != null

    fun canSnapshot(): Boolean = canCompare

    fun hasSnapshot(): Boolean = snapshot != null

    fun getCandidateCount(): Int = candidates?.size ?: 0

    fun getResults(): List<MemoryMatch> = lastDisplayResults

    fun reset() {
        snapshot = null
        candidates = null
        canCompare = true
        lastDisplayResults = emptyList()
        lastValueFilter = null
    }

    companion object {
        private const val MAX_RESULTS = 500
    }
}
