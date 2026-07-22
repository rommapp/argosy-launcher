package com.nendo.argosy.hardware

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.data.preferences.AmbientLedColorMode
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

data class SideColors(
    val low: Color,
    val mid: Color,
    val high: Color
)

private data class LedFrame(
    val left: Color,
    val right: Color,
    val brightness: Float
)

data class AmbientLedState(
    val context: AmbientLedContext = AmbientLedContext.ARGOSY_UI,
    val uiLeftColor: Color = Color.White,
    val uiRightColor: Color = Color.White,
    val hoverLeftColor: Color? = null,
    val hoverRightColor: Color? = null,
    val leftSideColors: SideColors? = null,
    val rightSideColors: SideColors? = null,
    val brightness: Float = 1f,
    val intensity: Float = 0f
)

@Singleton
class AmbientLedManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ledController: LEDController,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _state = MutableStateFlow(AmbientLedState())
    val state: StateFlow<AmbientLedState> = _state.asStateFlow()

    private var audioJob: Job? = null
    private var visualizer: Visualizer? = null

    private var isEnabled = false
    private var brightnessScalar = 1f
    private var audioBrightnessEnabled = true
    private var audioColorsEnabled = false
    private var colorMode = AmbientLedColorMode.DOMINANT_3
    @Volatile var coverArtEnabled = true
        private set
    private var screenEnabled = false
    private var transitionMs = 250L
    private var achievementFlashEnabled = true

    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedHigh = 0f

    private val alphaRise = 0.92f
    private val alphaDecaySmooth = 0.85f

    private var targetLeftColors: SideColors? = null
    private var targetRightColors: SideColors? = null
    private var currentLeftColors: SideColors? = null
    private var currentRightColors: SideColors? = null
    private var transitionJob: Job? = null
    private var hoverTransitionJob: Job? = null
    private var targetHoverLeft: Color? = null
    private var targetHoverRight: Color? = null
    private var uiColorsInitialized = false

    private val ledFrames = Channel<LedFrame>(Channel.CONFLATED)

    init {
        observePreferences()
        startLedWriter()
    }

    private fun observePreferences() {
        scope.launch {
            preferencesRepository.userPreferences.collectLatest { prefs ->
                val wasEnabled = isEnabled
                val oldBrightness = brightnessScalar

                isEnabled = prefs.ambientLedEnabled
                brightnessScalar = prefs.ambientLedBrightness / 100f
                audioBrightnessEnabled = prefs.ambientLedAudioBrightness
                audioColorsEnabled = prefs.ambientLedAudioColors
                colorMode = prefs.ambientLedColorMode
                coverArtEnabled = prefs.ambientLedCoverArtEnabled
                screenEnabled = prefs.ambientLedScreenEnabled
                transitionMs = prefs.ambientLedTransitionMs.toLong()
                achievementFlashEnabled = prefs.ambientLedAchievementFlash

                if (isEnabled && !wasEnabled) {
                    start()
                } else if (!isEnabled && wasEnabled) {
                    stop()
                } else if (isEnabled && oldBrightness != brightnessScalar) {
                    updateLeds()
                }
            }
        }
    }

    fun start() {
        if (!ledController.isAvailable) {
            Log.w(TAG, "LED controller not available")
            return
        }

        Log.i(TAG, "Starting ambient LED manager")
        ledController.setEnabled(true)

        if (audioBrightnessEnabled) {
            startAudioCapture()
        }

        updateLeds()
    }

    fun stop() {
        Log.i(TAG, "Stopping ambient LED manager")
        stopAudioCapture()
        flashJob?.cancel()
        flashActive = false
        ledController.setEnabled(false)
    }

    private fun shouldUpdateLeds(): Boolean {
        val ctx = _state.value.context
        if ((ctx == AmbientLedContext.ARGOSY_UI || ctx == AmbientLedContext.GAME_HOVER)
            && _state.value.hoverLeftColor == null) {
            return false
        }
        return true
    }

    fun setContext(newContext: AmbientLedContext) {
        Log.d(TAG, "setContext: $newContext, uiColorsInitialized=$uiColorsInitialized, hoverColors=${_state.value.hoverLeftColor != null}")
        _state.value = _state.value.copy(context = newContext)
        if (isEnabled && shouldUpdateLeds()) {
            updateLeds()
        }
    }

    fun setUiColors(primary: Color, secondary: Color?) {
        _state.value = _state.value.copy(
            uiLeftColor = primary,
            uiRightColor = secondary ?: primary
        )
        uiColorsInitialized = true
        if (isEnabled && _state.value.context == AmbientLedContext.ARGOSY_UI) {
            updateLeds()
        }
    }

    fun setHoverColors(primary: Color, secondary: Color?) {
        val left = primary
        val right = secondary ?: primary
        targetHoverLeft = left
        targetHoverRight = right

        val ctx = _state.value.context
        val active = isEnabled && (ctx == AmbientLedContext.ARGOSY_UI || ctx == AmbientLedContext.GAME_HOVER)

        if (_state.value.hoverLeftColor == null || !active) {
            hoverTransitionJob?.cancel()
            _state.value = _state.value.copy(hoverLeftColor = left, hoverRightColor = right)
            if (active) updateLeds()
            return
        }

        startHoverTransition()
    }

    private fun startHoverTransition() {
        val startLeft = _state.value.hoverLeftColor ?: return
        val startRight = _state.value.hoverRightColor ?: startLeft
        hoverTransitionJob?.cancel()
        hoverTransitionJob = launchTransition(HOVER_FRAME_MS) { progress ->
            val endLeft = targetHoverLeft ?: return@launchTransition
            val endRight = targetHoverRight ?: endLeft
            _state.value = _state.value.copy(
                hoverLeftColor = lerp(startLeft, endLeft, progress),
                hoverRightColor = lerp(startRight, endRight, progress)
            )
            val ctx = _state.value.context
            if (isEnabled && (ctx == AmbientLedContext.ARGOSY_UI || ctx == AmbientLedContext.GAME_HOVER)) {
                updateLeds()
            }
        }
    }

    fun clearHoverColors() {
        hoverTransitionJob?.cancel()
        hoverTransitionJob = null
        targetHoverLeft = null
        targetHoverRight = null
        _state.value = _state.value.copy(
            hoverLeftColor = null,
            hoverRightColor = null
        )
        if (isEnabled) {
            updateLeds()
        }
    }

    fun setInGameColors(leftColors: SideColors, rightColors: SideColors) {
        targetLeftColors = leftColors
        targetRightColors = rightColors

        if (currentLeftColors == null) {
            currentLeftColors = leftColors
            currentRightColors = rightColors
            _state.value = _state.value.copy(
                leftSideColors = leftColors,
                rightSideColors = rightColors
            )
            if (isEnabled && _state.value.context == AmbientLedContext.IN_GAME) {
                updateLeds()
            }
            return
        }

        if (transitionJob == null || transitionJob?.isCompleted == true) {
            startColorTransition()
        }
    }

    private fun startColorTransition() {
        val startLeft = currentLeftColors ?: return
        val startRight = currentRightColors ?: return
        val endLeft = targetLeftColors ?: return
        val endRight = targetRightColors ?: return
        transitionJob = launchTransition { progress ->
            currentLeftColors = lerpSideColors(startLeft, endLeft, progress)
            currentRightColors = lerpSideColors(startRight, endRight, progress)
            _state.value = _state.value.copy(
                leftSideColors = currentLeftColors,
                rightSideColors = currentRightColors
            )
            if (isEnabled && _state.value.context == AmbientLedContext.IN_GAME) {
                updateLeds()
            }
        }
    }

    private fun launchTransition(frameMs: Long = FRAME_MS, onProgress: (Float) -> Unit): Job = scope.launch {
        val startTime = System.currentTimeMillis()
        val duration = transitionMs
        while (isActive) {
            val elapsed = System.currentTimeMillis() - startTime
            val progress = if (duration <= 0) 1f else (elapsed.toFloat() / duration).coerceIn(0f, 1f)
            onProgress(progress)
            if (progress >= 1f) break
            delay(frameMs)
        }
    }

    private fun lerpSideColors(start: SideColors, end: SideColors, t: Float): SideColors {
        return SideColors(
            low = lerp(start.low, end.low, t),
            mid = lerp(start.mid, end.mid, t),
            high = lerp(start.high, end.high, t)
        )
    }

    fun clearInGameColors() {
        transitionJob?.cancel()
        transitionJob = null
        targetLeftColors = null
        targetRightColors = null
        currentLeftColors = null
        currentRightColors = null
        _state.value = _state.value.copy(
            leftSideColors = null,
            rightSideColors = null
        )
    }

    private var flashJob: Job? = null
    @Volatile private var flashActive = false

    fun flashAchievement(isHardcore: Boolean) {
        if (!ledController.isAvailable || !isEnabled || !achievementFlashEnabled) return

        flashJob?.cancel()
        flashJob = scope.launch {
            flashActive = true
            val flashColor = if (isHardcore) HARDCORE_FLASH_COLOR else CASUAL_FLASH_COLOR
            val peak = brightnessScalar
            val dim = 0.2f * brightnessScalar

            repeat(3) {
                ledController.setColor(flashColor)
                ledController.setBrightness(peak)
                delay(150)
                ledController.setBrightness(dim)
                delay(100)
            }

            flashActive = false
            writerCacheStale = true
            if (isEnabled) {
                updateLeds()
            } else {
                ledController.setEnabled(false)
            }
        }
    }

    private fun updateLeds() {
        if (flashActive) return
        val currentState = _state.value
        val (leftColor, rightColor) = when (currentState.context) {
            AmbientLedContext.ARGOSY_UI, AmbientLedContext.GAME_HOVER -> {
                val left = currentState.hoverLeftColor ?: currentState.uiLeftColor
                val right = currentState.hoverRightColor ?: currentState.uiRightColor
                Pair(left, right)
            }
            AmbientLedContext.IN_GAME -> {
                if (audioColorsEnabled && currentState.leftSideColors != null && currentState.rightSideColors != null) {
                    val intensity = currentState.intensity
                    Pair(
                        blendForIntensity(intensity, currentState.leftSideColors),
                        blendForIntensity(intensity, currentState.rightSideColors)
                    )
                } else if (currentState.leftSideColors != null && currentState.rightSideColors != null) {
                    Pair(currentState.leftSideColors.mid, currentState.rightSideColors.mid)
                } else {
                    Pair(currentState.uiLeftColor, currentState.uiRightColor)
                }
            }
        }

        val brightness = currentState.brightness * brightnessScalar
        ledFrames.trySend(LedFrame(leftColor, rightColor, brightness))
    }

    @Volatile private var writerCacheStale = false

    private fun startLedWriter() {
        scope.launch(Dispatchers.IO) {
            var lastLeft: Color? = null
            var lastRight: Color? = null
            var lastBrightness = -1f
            for (frame in ledFrames) {
                if (writerCacheStale) {
                    writerCacheStale = false
                    lastLeft = null
                    lastRight = null
                    lastBrightness = -1f
                }
                val colorChanged = lastLeft == null || lastRight == null ||
                    colorDelta(lastLeft, frame.left) > COLOR_EPSILON ||
                    colorDelta(lastRight, frame.right) > COLOR_EPSILON
                val brightnessChanged = abs(lastBrightness - frame.brightness) > BRIGHTNESS_EPSILON
                if (!colorChanged && !brightnessChanged) continue
                ledController.setColor(frame.left, frame.right)
                ledController.setBrightness(frame.brightness)
                lastLeft = frame.left
                lastRight = frame.right
                lastBrightness = frame.brightness
                delay(MIN_WRITE_INTERVAL_MS)
            }
        }
    }

    private fun colorDelta(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private fun blendForIntensity(intensity: Float, colors: SideColors): Color {
        return when {
            intensity < 0.30f -> {
                lerp(colors.low, colors.mid, intensity / 0.30f)
            }
            intensity < 0.75f -> {
                val t = (intensity - 0.30f) / 0.45f
                lerp(colors.mid, colors.high, t)
            }
            else -> {
                val t = (intensity - 0.75f) / 0.25f
                lerp(colors.high, Color.White, t * 0.3f)
            }
        }
    }

    private fun startAudioCapture() {
        if (audioJob != null) return

        try {
            visualizer = Visualizer(0).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                enabled = true
            }

            val captureSize = visualizer?.captureSize ?: 1024
            val fftData = ByteArray(captureSize)

            val actualSampleRate = (visualizer?.samplingRate ?: 48000000) / 1000f
            val binWidth = actualSampleRate / captureSize

            val bassStartBin = (60 / binWidth).toInt()
            val bassEndBin = (250 / binWidth).toInt()
            val midEndBin = (1000 / binWidth).toInt()
            val highEndBin = (8000 / binWidth).toInt()

            smoothedBass = 0f
            smoothedMid = 0f
            smoothedHigh = 0f

            audioJob = scope.launch {
                while (true) {
                    val status = visualizer?.getFft(fftData)
                    if (status == Visualizer.SUCCESS) {
                        val bassMag = computeBandMagnitude(fftData, bassStartBin, bassEndBin).pow(2)
                        val midMag = computeBandMagnitude(fftData, bassEndBin, midEndBin).pow(2)
                        val highMag = computeBandMagnitude(fftData, midEndBin, highEndBin).pow(2)

                        smoothedBass = smoothBassOrMid(smoothedBass, bassMag)
                        smoothedMid = smoothBassOrMid(smoothedMid, midMag)
                        smoothedHigh = highMag

                        val normBass = (smoothedBass / 800f).coerceIn(0f, 1f)
                        val normMid = (smoothedMid / 2000f).coerceIn(0f, 1f)
                        val normHigh = (smoothedHigh / 15f).coerceIn(0f, 1f)

                        val rawNormHigh = normHigh
                        val isDramaticPeak = rawNormHigh > 0.8f

                        val floor = 0.05f + (normBass * 0.15f)
                        val rawMidVolume = (midMag / 2000f).coerceIn(0f, 1f)
                        val midCeiling = if (rawMidVolume > 0.8f) 0.85f else 0.70f
                        val midContrib = (normMid * 0.60f).coerceAtMost(midCeiling - floor)

                        val highContrib = if (isDramaticPeak) {
                            (rawNormHigh - 0.5f) * 0.40f
                        } else if (normHigh > 0.5f) {
                            (normHigh - 0.5f) * 0.25f
                        } else {
                            0f
                        }

                        val brightness = (floor + midContrib + highContrib).coerceIn(0.05f, 1f)
                        val intensity = (normMid * 0.6f + normHigh * 0.4f).coerceIn(0f, 1f)

                        _state.value = _state.value.copy(
                            brightness = brightness,
                            intensity = intensity
                        )

                        if (isEnabled && shouldUpdateLeds()) {
                            updateLeds()
                        }
                    }
                    delay(50)
                }
            }

            Log.i(TAG, "Audio capture started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
        }
    }

    private fun stopAudioCapture() {
        audioJob?.cancel()
        audioJob = null
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        Log.i(TAG, "Audio capture stopped")
    }

    private fun smoothBassOrMid(current: Float, newValue: Float): Float {
        val alpha = if (newValue > current) alphaRise else alphaDecaySmooth
        return alpha * current + (1 - alpha) * newValue
    }

    private fun computeBandMagnitude(fftData: ByteArray, startBin: Int, endBin: Int): Float {
        var sum = 0f
        var count = 0
        for (bin in startBin until endBin) {
            val realIdx = bin * 2
            val imagIdx = realIdx + 1
            if (imagIdx < fftData.size) {
                val real = fftData[realIdx].toFloat()
                val imag = fftData[imagIdx].toFloat()
                val magnitude = sqrt(real * real + imag * imag)
                sum += magnitude
                count++
            }
        }
        return if (count > 0) sum / count else 0f
    }

    companion object {
        private const val TAG = "AmbientLedManager"
        private const val FRAME_MS = 16L
        private const val HOVER_FRAME_MS = 83L
        private const val MIN_WRITE_INTERVAL_MS = 33L
        private const val COLOR_EPSILON = 0.012f
        private const val BRIGHTNESS_EPSILON = 0.01f
        private val HARDCORE_FLASH_COLOR = Color(0xFFFFE566)
        private val CASUAL_FLASH_COLOR = Color(0xFFFFAA44)
    }
}
