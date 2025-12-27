package com.nendo.argosy.ui.audio

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.media.AudioAttributes
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
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null
    private var enabled = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var fadeOutCancelled = false

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

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
                if (uri.startsWith("/")) {
                    setDataSource(uri)
                } else {
                    setDataSource(context, Uri.parse(uri))
                }
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
            if (uri.startsWith("/")) {
                java.io.File(uri).canRead()
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))?.close()
                true
            }
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

        if (mediaPlayer == null) {
            currentUri?.let { preparePlayer(it) }
        }

        val player = mediaPlayer ?: return

        fadeOutCancelled = true
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

        fadeOutCancelled = false
        fadeAnimator?.cancel()

        fadeAnimator = ValueAnimator.ofFloat(targetVolume, 0f).apply {
            duration = durationMs
            addUpdateListener { animator ->
                val vol = animator.animatedValue as Float
                mediaPlayer?.setVolume(vol, vol)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!fadeOutCancelled) {
                        pauseInternal()
                    }
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
        Log.d(TAG, "stopped and released")
    }

    fun release() {
        stopAndRelease()
        currentUri = null
        enabled = false
    }
}
