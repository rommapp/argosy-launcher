package com.nendo.argosy.ui.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AmbientAudio"

@Singleton
class AmbientAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioManager.OnAudioFocusChangeListener {

    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null
    private var enabled = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var hasAudioFocus = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener(this)
        .setAcceptsDelayedFocusGain(false)
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost: $focusChange")
                hasAudioFocus = false
                pauseInternal()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained")
                hasAudioFocus = true
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        Log.d(TAG, "setEnabled=$enabled")
        if (!enabled) {
            stopAndRelease()
        }
    }

    fun setVolume(volume: Int) {
        this.targetVolume = (volume / 100f).coerceIn(0f, 1f)
        Log.d(TAG, "setVolume=$volume (${this.targetVolume})")
        mediaPlayer?.setVolume(targetVolume, targetVolume)
    }

    fun setAudioUri(uri: String?) {
        if (uri == currentUri) return
        Log.d(TAG, "setAudioUri=$uri")

        stopAndRelease()
        currentUri = uri

        if (uri != null && enabled) {
            preparePlayer(uri)
        }
    }

    private fun preparePlayer(uri: String) {
        if (!validateUri(uri)) {
            Log.w(TAG, "Audio file not accessible: $uri")
            currentUri = null
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(audioAttributes)
                setDataSource(context, Uri.parse(uri))
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            mediaPlayer = null
        }
    }

    private fun validateUri(uri: String): Boolean {
        return try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.close()
            true
        } catch (e: Exception) {
            Log.w(TAG, "URI validation failed: ${e.message}")
            false
        }
    }

    fun fadeIn(durationMs: Long = 500) {
        if (!enabled || currentUri == null) {
            Log.d(TAG, "fadeIn skipped: enabled=$enabled, uri=$currentUri")
            return
        }

        if (audioManager.isMusicActive) {
            Log.d(TAG, "fadeIn skipped: other music is playing")
            return
        }

        if (mediaPlayer == null) {
            currentUri?.let { preparePlayer(it) }
        }

        val player = mediaPlayer ?: return

        val result = audioManager.requestAudioFocus(focusRequest)
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus request denied: $result")
            return
        }
        hasAudioFocus = true

        fadeAnimator?.cancel()

        try {
            player.setVolume(0f, 0f)
            if (!player.isPlaying) {
                player.start()
            }
            _isPlaying.value = true

            fadeAnimator = ValueAnimator.ofFloat(0f, targetVolume).apply {
                duration = durationMs
                addUpdateListener { animator ->
                    val vol = animator.animatedValue as Float
                    mediaPlayer?.setVolume(vol, vol)
                }
                start()
            }
            Log.d(TAG, "fadeIn started")
        } catch (e: Exception) {
            Log.e(TAG, "fadeIn failed", e)
        }
    }

    fun fadeOut(durationMs: Long = 500, onComplete: () -> Unit = {}) {
        val player = mediaPlayer
        if (player == null || !player.isPlaying) {
            onComplete()
            return
        }

        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(targetVolume, 0f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                mediaPlayer?.setVolume(vol, vol)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    pauseInternal()
                    onComplete()
                }
            })
            start()
        }
        Log.d(TAG, "fadeOut started")
    }

    private fun pauseInternal() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
            Log.d(TAG, "paused")
        } catch (e: Exception) {
            Log.e(TAG, "pause failed", e)
        }
    }

    private fun stopAndRelease() {
        fadeAnimator?.cancel()
        fadeAnimator = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "stopAndRelease error", e)
        }
        mediaPlayer = null
        _isPlaying.value = false

        if (hasAudioFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasAudioFocus = false
        }
        Log.d(TAG, "stopped and released")
    }

    fun release() {
        stopAndRelease()
        currentUri = null
        enabled = false
    }
}
