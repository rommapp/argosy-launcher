package com.nendo.argosy.ui.components.playtime

data class TreemapRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height
    val area: Float get() = width * height
}

object Squarify {

    /**
     * Lays out [areas] inside [bounds] as a squarified treemap (Bruls, Huizing and van Wijk):
     * tiles are laid in runs along the shorter side of the free space, and a run is closed when
     * adding the next tile would make its worst aspect ratio worse. Returns one rect per input,
     * in input order, scaled so the tiles fill [bounds] exactly. A non-positive area gets an empty
     * rect. Sorting [areas] in descending order gives the squarest result.
     */
    fun layout(areas: List<Float>, bounds: TreemapRect): List<TreemapRect> {
        val total = areas.sumOf { it.coerceAtLeast(0f).toDouble() }
        val result = MutableList(areas.size) { TreemapRect(bounds.x, bounds.y, 0f, 0f) }
        if (total <= 0.0 || bounds.area <= 0f) return result
        val scale = bounds.area / total
        val scaled = areas.map { (it.coerceAtLeast(0f) * scale).toFloat() }

        var free = bounds
        val run = mutableListOf<Int>()
        var index = 0
        while (index < scaled.size) {
            if (scaled[index] <= 0f) {
                result[index] = TreemapRect(free.x, free.y, 0f, 0f)
                index++
                continue
            }
            val side = minOf(free.width, free.height)
            if (run.isEmpty() || worst(run + index, scaled, side) <= worst(run, scaled, side)) {
                run.add(index)
                index++
            } else {
                free = placeRun(run, scaled, free, result)
                run.clear()
            }
        }
        if (run.isNotEmpty()) placeRun(run, scaled, free, result)
        return result
    }

    private fun worst(run: List<Int>, scaled: List<Float>, side: Float): Float {
        if (side <= 0f) return Float.MAX_VALUE
        val sum = run.sumOf { scaled[it].toDouble() }.toFloat()
        if (sum <= 0f) return Float.MAX_VALUE
        val sideSquared = side * side
        val sumSquared = sum * sum
        return run.maxOf { maxOf(sideSquared * scaled[it] / sumSquared, sumSquared / (sideSquared * scaled[it])) }
    }

    private fun placeRun(
        run: List<Int>,
        scaled: List<Float>,
        free: TreemapRect,
        result: MutableList<TreemapRect>
    ): TreemapRect {
        val sum = run.sumOf { scaled[it].toDouble() }.toFloat()
        if (free.width <= 0f || free.height <= 0f || sum <= 0f) {
            run.forEach { result[it] = TreemapRect(free.x, free.y, 0f, 0f) }
            return free
        }
        return if (free.width >= free.height) {
            val runWidth = sum / free.height
            var y = free.y
            run.forEach { i ->
                val h = scaled[i] / runWidth
                result[i] = TreemapRect(free.x, y, runWidth, h)
                y += h
            }
            TreemapRect(free.x + runWidth, free.y, free.width - runWidth, free.height)
        } else {
            val runHeight = sum / free.width
            var x = free.x
            run.forEach { i ->
                val w = scaled[i] / runHeight
                result[i] = TreemapRect(x, free.y, w, runHeight)
                x += w
            }
            TreemapRect(free.x, free.y + runHeight, free.width, free.height - runHeight)
        }
    }
}
