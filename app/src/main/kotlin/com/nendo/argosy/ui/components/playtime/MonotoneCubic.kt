package com.nendo.argosy.ui.components.playtime

import kotlin.math.sqrt

object MonotoneCubic {

    /**
     * Samples the Fritsch-Carlson monotone cubic through the knots ([xs], [ys]) at [count]
     * evenly spaced positions from [from] to [to] inclusive. The curve never overshoots a knot,
     * so a run of empty hours stays flat instead of dipping below the baseline.
     */
    fun sample(xs: FloatArray, ys: FloatArray, count: Int, from: Float, to: Float): FloatArray {
        val n = xs.size
        if (n == 0 || count <= 0) return FloatArray(0)
        if (n == 1) return FloatArray(count) { ys[0] }
        val slopes = FloatArray(n - 1) { k -> (ys[k + 1] - ys[k]) / (xs[k + 1] - xs[k]) }
        val tangents = FloatArray(n)
        tangents[0] = slopes[0]
        tangents[n - 1] = slopes[n - 2]
        for (k in 1 until n - 1) {
            tangents[k] = if (slopes[k - 1] * slopes[k] <= 0f) 0f else (slopes[k - 1] + slopes[k]) / 2f
        }
        for (k in 0 until n - 1) {
            if (slopes[k] == 0f) {
                tangents[k] = 0f
                tangents[k + 1] = 0f
                continue
            }
            val alpha = tangents[k] / slopes[k]
            val beta = tangents[k + 1] / slopes[k]
            val radius = alpha * alpha + beta * beta
            if (radius > LIMIT_SQUARED) {
                val tau = LIMIT / sqrt(radius)
                tangents[k] = tau * alpha * slopes[k]
                tangents[k + 1] = tau * beta * slopes[k]
            }
        }
        val step = if (count == 1) 0f else (to - from) / (count - 1)
        var segment = 0
        return FloatArray(count) { i ->
            val x = from + step * i
            while (segment < n - 2 && x > xs[segment + 1]) segment++
            val x0 = xs[segment]
            val x1 = xs[segment + 1]
            val h = x1 - x0
            val t = ((x - x0) / h).coerceIn(0f, 1f)
            val t2 = t * t
            val t3 = t2 * t
            val h00 = 2f * t3 - 3f * t2 + 1f
            val h10 = t3 - 2f * t2 + t
            val h01 = -2f * t3 + 3f * t2
            val h11 = t3 - t2
            h00 * ys[segment] + h10 * h * tangents[segment] + h01 * ys[segment + 1] + h11 * h * tangents[segment + 1]
        }
    }

    private const val LIMIT = 3f
    private const val LIMIT_SQUARED = LIMIT * LIMIT
}
