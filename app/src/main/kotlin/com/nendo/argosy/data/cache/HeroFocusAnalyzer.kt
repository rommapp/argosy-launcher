package com.nendo.argosy.data.cache

import com.nendo.argosy.data.model.HeroFocus
import com.nendo.argosy.data.model.HeroFocusSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds the detail in wide hero art and frames it for both column widths. [pixels] are ARGB,
 * row-major, [width] by [height], already downscaled to about [HeroFocusSpec.analysisSize] on the
 * long side. Art no wider than the focused column, and art with no contrast to rank, comes back
 * [HeroFocus.CENTERED] without a scan.
 */
object HeroFocusAnalyzer {

    private const val FFT_SIZE = 64
    private const val LOG_FLOOR = 1e-6
    private const val SALIENCY_SIGMA = 2.5f
    private const val DETAIL_SIGMA_RATIO = 6f / 256f
    private const val SALIENCY_WEIGHT = 0.5f
    private const val EDGE_WEIGHT = 0.35f
    private const val SATURATION_WEIGHT = 0.05f
    private const val SKIN_WEIGHT = 0.35f
    private const val PRIOR_WEIGHT = 0.15f
    private const val PRIOR_SPREAD = 0.18f
    private const val MIN_CONTRAST = 4f / 255f
    private const val SCORE_TIE = 1e-9
    private const val GAIN_FLOOR = 1.08
    private const val GAIN_FULL = 1.35
    private const val MAX_SHIFT = 0.65f
    private const val SKIN_HUE_LOW = 28f
    private const val SKIN_HUE_HIGH = 345f
    private const val SKIN_SATURATION_MIN = 0.18f
    private const val SKIN_SATURATION_MAX = 0.68f
    private const val SKIN_VALUE_MIN = 0.35f

    fun analyze(pixels: IntArray, width: Int, height: Int, spec: HeroFocusSpec): HeroFocus {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return HeroFocus.CENTERED
        if (width.toFloat() / height <= spec.wideAspect) return HeroFocus.CENTERED
        val gray = FloatArray(width * height)
        val saturation = FloatArray(width * height)
        val skin = FloatArray(width * height)
        var grayMin = 1f
        var grayMax = 0f
        var saturationMin = 1f
        var saturationMax = 0f
        for (i in 0 until width * height) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val high = max(r, max(g, b))
            val low = min(r, min(g, b))
            val luma = 0.299f * r + 0.587f * g + 0.114f * b
            val chroma = if (high > 0f) (high - low) / high else 0f
            gray[i] = luma
            saturation[i] = chroma
            skin[i] = if (isSkin(hue(r, g, b, high, low), chroma, high)) 1f else 0f
            grayMin = min(grayMin, luma)
            grayMax = max(grayMax, luma)
            saturationMin = min(saturationMin, chroma)
            saturationMax = max(saturationMax, chroma)
        }
        if (grayMax - grayMin < MIN_CONTRAST && saturationMax - saturationMin < MIN_CONTRAST) {
            return HeroFocus.CENTERED
        }
        val importance = importance(gray, saturation, skin, width, height)
        val integral = integralOf(importance, width, height)
        return HeroFocus(
            narrowX = horizontalFocus(integral, width, height, spec.narrowAspect),
            wideX = horizontalFocus(integral, width, height, spec.wideAspect),
            y = 0f,
            zoom = 1f
        )
    }

    private class Window(val x: Float, val score: Double, val centerScore: Double)

    private fun hue(r: Float, g: Float, b: Float, high: Float, low: Float): Float {
        val span = high - low
        if (span <= 0f) return 0f
        val sector = when (high) {
            r -> ((g - b) / span).mod(6f)
            g -> (b - r) / span + 2f
            else -> (r - g) / span + 4f
        }
        return sector * 60f
    }

    private fun isSkin(hue: Float, saturation: Float, value: Float): Boolean =
        (hue <= SKIN_HUE_LOW || hue >= SKIN_HUE_HIGH) &&
            saturation in SKIN_SATURATION_MIN..SKIN_SATURATION_MAX &&
            value >= SKIN_VALUE_MIN

    private fun horizontalFocus(integral: DoubleArray, width: Int, height: Int, aspect: Float): Float {
        val window = bestWindow(integral, width, height, aspect)
        val gain = window.score / window.centerScore.coerceAtLeast(SCORE_TIE)
        val confidence = ((gain - GAIN_FLOOR) / (GAIN_FULL - GAIN_FLOOR)).coerceIn(0.0, 1.0).toFloat()
        return (window.x * confidence).coerceIn(-MAX_SHIFT, MAX_SHIFT)
    }

    private fun importance(
        gray: FloatArray,
        saturation: FloatArray,
        skinMask: FloatArray,
        width: Int,
        height: Int
    ): FloatArray {
        val sigma = max(width, height) * DETAIL_SIGMA_RATIO
        val saliency = resize(
            spectralResidual(resize(gray, width, height, FFT_SIZE, FFT_SIZE)),
            FFT_SIZE, FFT_SIZE, width, height
        )
        val edges = normalized(gaussianBlur(sobel(gray, width, height), width, height, sigma))
        val color = gaussianBlur(saturation, width, height, sigma)
        val skinBlur = gaussianBlur(skinMask, width, height, sigma)
        val skin = normalized(FloatArray(width * height) { skinBlur[it] * edges[it] })
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val i = y * width + x
                val dx = (x + 0.5f) / width - 0.5f
                val prior = exp(-dx * dx / PRIOR_SPREAD)
                val detail = SALIENCY_WEIGHT * saliency[i] + EDGE_WEIGHT * edges[i] +
                    SATURATION_WEIGHT * color[i] + SKIN_WEIGHT * skin[i]
                result[i] = detail * (1f - PRIOR_WEIGHT + PRIOR_WEIGHT * prior)
            }
        }
        return result
    }

    private fun spectralResidual(gray: FloatArray): FloatArray {
        val n = FFT_SIZE
        val re = DoubleArray(n * n) { gray[it].toDouble() }
        val im = DoubleArray(n * n)
        fft2(re, im, n, inverse = false)
        val logAmplitude = DoubleArray(n * n) { ln(hypot(re[it], im[it]) + LOG_FLOOR) }
        val phase = DoubleArray(n * n) { atan2(im[it], re[it]) }
        val smoothed = boxBlur3(logAmplitude, n)
        for (i in 0 until n * n) {
            val magnitude = exp(logAmplitude[i] - smoothed[i])
            re[i] = magnitude * cos(phase[i])
            im[i] = magnitude * sin(phase[i])
        }
        fft2(re, im, n, inverse = true)
        val power = FloatArray(n * n) { (re[it] * re[it] + im[it] * im[it]).toFloat() }
        return normalized(gaussianBlur(power, n, n, SALIENCY_SIGMA))
    }

    private fun fft2(re: DoubleArray, im: DoubleArray, n: Int, inverse: Boolean) {
        val lineRe = DoubleArray(n)
        val lineIm = DoubleArray(n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                lineRe[x] = re[y * n + x]
                lineIm[x] = im[y * n + x]
            }
            fft(lineRe, lineIm, inverse)
            for (x in 0 until n) {
                re[y * n + x] = lineRe[x]
                im[y * n + x] = lineIm[x]
            }
        }
        for (x in 0 until n) {
            for (y in 0 until n) {
                lineRe[y] = re[y * n + x]
                lineIm[y] = im[y * n + x]
            }
            fft(lineRe, lineIm, inverse)
            for (y in 0 until n) {
                re[y * n + x] = lineRe[y]
                im[y * n + x] = lineIm[y]
            }
        }
    }

    private fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val swapRe = re[i]
                re[i] = re[j]
                re[j] = swapRe
                val swapIm = im[i]
                im[i] = im[j]
                im[j] = swapIm
            }
        }
        val sign = if (inverse) 1.0 else -1.0
        var length = 2
        while (length <= n) {
            val angle = sign * 2.0 * PI / length
            val stepRe = cos(angle)
            val stepIm = sin(angle)
            val half = length / 2
            for (start in 0 until n step length) {
                var turnRe = 1.0
                var turnIm = 0.0
                for (k in 0 until half) {
                    val a = start + k
                    val b = a + half
                    val tRe = re[b] * turnRe - im[b] * turnIm
                    val tIm = re[b] * turnIm + im[b] * turnRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = turnRe * stepRe - turnIm * stepIm
                    turnIm = turnRe * stepIm + turnIm * stepRe
                    turnRe = nextRe
                }
            }
            length = length shl 1
        }
    }

    private fun boxBlur3(values: DoubleArray, n: Int): DoubleArray {
        val result = DoubleArray(n * n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                var sum = 0.0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        sum += values[(y + dy).coerceIn(0, n - 1) * n + (x + dx).coerceIn(0, n - 1)]
                    }
                }
                result[y * n + x] = sum / 9.0
            }
        }
        return result
    }

    private fun sobel(gray: FloatArray, width: Int, height: Int): FloatArray {
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val gx = sample(gray, width, height, x + 1, y - 1) + 2f * sample(gray, width, height, x + 1, y) +
                    sample(gray, width, height, x + 1, y + 1) - sample(gray, width, height, x - 1, y - 1) -
                    2f * sample(gray, width, height, x - 1, y) - sample(gray, width, height, x - 1, y + 1)
                val gy = sample(gray, width, height, x - 1, y + 1) + 2f * sample(gray, width, height, x, y + 1) +
                    sample(gray, width, height, x + 1, y + 1) - sample(gray, width, height, x - 1, y - 1) -
                    2f * sample(gray, width, height, x, y - 1) - sample(gray, width, height, x + 1, y - 1)
                result[y * width + x] = sqrt(gx * gx + gy * gy)
            }
        }
        return result
    }

    private fun sample(values: FloatArray, width: Int, height: Int, x: Int, y: Int): Float =
        values[y.coerceIn(0, height - 1) * width + x.coerceIn(0, width - 1)]

    private fun gaussianBlur(values: FloatArray, width: Int, height: Int, sigma: Float): FloatArray {
        val radius = ceil(sigma * 3f).toInt().coerceAtLeast(1)
        val kernel = FloatArray(radius * 2 + 1) { k ->
            val d = (k - radius).toFloat()
            exp(-d * d / (2f * sigma * sigma))
        }
        val total = kernel.sum()
        for (k in kernel.indices) kernel[k] /= total
        val horizontal = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) sum += kernel[k] * sample(values, width, height, x + k - radius, y)
                horizontal[y * width + x] = sum
            }
        }
        val result = FloatArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0f
                for (k in kernel.indices) sum += kernel[k] * sample(horizontal, width, height, x, y + k - radius)
                result[y * width + x] = sum
            }
        }
        return result
    }

    private fun resize(values: FloatArray, fromWidth: Int, fromHeight: Int, toWidth: Int, toHeight: Int): FloatArray {
        val result = FloatArray(toWidth * toHeight)
        for (y in 0 until toHeight) {
            val sourceY = ((y + 0.5f) * fromHeight / toHeight - 0.5f).coerceIn(0f, (fromHeight - 1).toFloat())
            val y0 = sourceY.toInt()
            val y1 = min(y0 + 1, fromHeight - 1)
            val ty = sourceY - y0
            for (x in 0 until toWidth) {
                val sourceX = ((x + 0.5f) * fromWidth / toWidth - 0.5f).coerceIn(0f, (fromWidth - 1).toFloat())
                val x0 = sourceX.toInt()
                val x1 = min(x0 + 1, fromWidth - 1)
                val tx = sourceX - x0
                val top = mix(values[y0 * fromWidth + x0], values[y0 * fromWidth + x1], tx)
                val bottom = mix(values[y1 * fromWidth + x0], values[y1 * fromWidth + x1], tx)
                result[y * toWidth + x] = mix(top, bottom, ty)
            }
        }
        return result
    }

    private fun mix(from: Float, to: Float, fraction: Float): Float = from + (to - from) * fraction

    private fun normalized(values: FloatArray): FloatArray {
        val peak = values.maxOrNull() ?: return values
        if (peak <= 0f) return values
        for (i in values.indices) values[i] /= peak
        return values
    }

    private fun integralOf(values: FloatArray, width: Int, height: Int): DoubleArray {
        val stride = width + 1
        val integral = DoubleArray(stride * (height + 1))
        for (y in 0 until height) {
            for (x in 0 until width) {
                integral[(y + 1) * stride + x + 1] = values[y * width + x] +
                    integral[y * stride + x + 1] + integral[(y + 1) * stride + x] - integral[y * stride + x]
            }
        }
        return integral
    }

    private fun bestWindow(integral: DoubleArray, width: Int, height: Int, aspect: Float): Window {
        val stride = width + 1
        val windowWidth = (height * aspect).roundToInt().coerceIn(1, width)
        val area = (windowWidth * height).toDouble()
        fun scoreAt(left: Int): Double {
            val right = left + windowWidth
            return (integral[height * stride + right] - integral[right] -
                integral[height * stride + left] + integral[left]) / area
        }
        val centerLeft = (width - windowWidth) / 2
        val centerScore = scoreAt(centerLeft)
        var bestLeft = centerLeft
        var bestScore = centerScore
        var bestDistance = 0f
        for (left in 0..width - windowWidth) {
            val score = scoreAt(left)
            val distance = abs(left + windowWidth / 2f - width / 2f)
            val better = score > bestScore + SCORE_TIE
            val tiedAndCloser = score >= bestScore - SCORE_TIE && distance < bestDistance
            if (better || tiedAndCloser) {
                bestScore = score
                bestDistance = distance
                bestLeft = left
            }
        }
        return Window(x = bias(bestLeft, width - windowWidth), score = bestScore, centerScore = centerScore)
    }

    private fun bias(start: Int, slack: Int): Float = if (slack <= 0) 0f else 2f * start / slack - 1f
}
