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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AmbientAudio"

@Singleton
class AmbientAudioManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "ogg", "wav", "flac", "m4a", "aac", "opus", "wma")
    }

    private var mediaPlayer: MediaPlayer? = null
    private var currentUri: String? = null
    private var enabled = false
    private var targetVolume = 0.5f
    private var fadeAnimator: ValueAnimator? = null
    private var fadeOutCancelled = false
    private var suspended = false

    private var isPlaylistMode = false
    private var folderPath: String? = null
    private var playlist: List<String> = emptyList()
    private var playlistIndex = 0
    private var shuffle = false

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrackName = MutableStateFlow<String?>(null)
    val currentTrackName: StateFlow<String?> = _currentTrackName.asStateFlow()

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

    fun setShuffle(shuffle: Boolean) {
        this.shuffle = shuffle
        Log.d(TAG, "setShuffle=$shuffle")
        if (isPlaylistMode && playlist.isNotEmpty()) {
            reshufflePlaylist()
        }
    }

    fun setAudioSource(path: String?) {
        if (path == null) {
            clearSource()
            return
        }

        val file = File(path)
        if (file.isDirectory) {
            setAudioFolder(path)
        } else {
            setAudioFile(path)
        }
    }

    private fun setAudioFile(uri: String) {
        if (uri == currentUri && !isPlaylistMode) return
        Log.d(TAG, "setAudioFile=$uri")

        stopAndRelease()
        isPlaylistMode = false
        folderPath = null
        playlist = emptyList()
        playlistIndex = 0
        currentUri = uri
        _currentTrackName.value = null

        if (enabled) {
            preparePlayer(uri, looping = true)
        }
    }

    private fun setAudioFolder(path: String) {
        Log.d(TAG, "setAudioFolder=$path")

        stopAndRelease()
        isPlaylistMode = true
        folderPath = path
        currentUri = path

        scanAndPreparePlaylist()

        if (enabled && playlist.isNotEmpty()) {
            preparePlayer(playlist[playlistIndex], looping = false)
        }
    }

    private fun scanAndPreparePlaylist() {
        val folder = folderPath?.let { File(it) } ?: return
        if (!folder.isDirectory) {
            Log.w(TAG, "Not a directory: $folderPath")
            playlist = emptyList()
            return
        }

        val audioFiles = folder.listFiles { file ->
            file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS
        }?.sortedBy { it.name.lowercase() }?.map { it.absolutePath } ?: emptyList()

        Log.d(TAG, "Found ${audioFiles.size} audio files in folder")
        playlist = audioFiles
        playlistIndex = 0

        if (shuffle && playlist.isNotEmpty()) {
            reshufflePlaylist()
        }

        updateCurrentTrackName()
    }

    private fun reshufflePlaylist() {
        if (playlist.isEmpty()) return
        playlist = playlist.shuffled()
        playlistIndex = 0
        updateCurrentTrackName()
        Log.d(TAG, "Reshuffled playlist")
    }

    private fun updateCurrentTrackName() {
        _currentTrackName.value = if (isPlaylistMode && playlist.isNotEmpty()) {
            playlist.getOrNull(playlistIndex)?.substringAfterLast("/")
        } else {
            null
        }
    }

    private fun clearSource() {
        Log.d(TAG, "clearSource")
        stopAndRelease()
        isPlaylistMode = false
        folderPath = null
        playlist = emptyList()
        playlistIndex = 0
        currentUri = null
        _currentTrackName.value = null
    }

    @Deprecated("Use setAudioSource instead", ReplaceWith("setAudioSource(uri)"))
    fun setAudioUri(uri: String?) {
        setAudioSource(uri)
    }

    private fun preparePlayer(uri: String, looping: Boolean) {
        if (!validateUri(uri)) {
            Log.w(TAG, "Audio file not accessible: $uri")
            if (isPlaylistMode) {
                playNextTrack()
            } else {
                currentUri = null
            }
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
                isLooping = looping
                setVolume(0f, 0f)
                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared: ${uri.substringAfterLast("/")}")
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    if (isPlaylistMode) {
                        playNextTrack()
                    }
                    true
                }
                if (!looping) {
                    setOnCompletionListener {
                        Log.d(TAG, "Track completed")
                        playNextTrack()
                    }
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare MediaPlayer", e)
            mediaPlayer = null
            if (isPlaylistMode) {
                playNextTrack()
            }
        }
    }

    private fun playNextTrack() {
        if (!isPlaylistMode || playlist.isEmpty()) return

        val wasPlaying = _isPlaying.value
        mediaPlayer?.release()
        mediaPlayer = null

        playlistIndex++
        if (playlistIndex >= playlist.size) {
            scanAndPreparePlaylist()
            if (playlist.isEmpty()) {
                Log.w(TAG, "No more tracks in playlist")
                _isPlaying.value = false
                return
            }
        }

        updateCurrentTrackName()
        val nextTrack = playlist.getOrNull(playlistIndex)
        if (nextTrack != null) {
            Log.d(TAG, "Playing next track: ${nextTrack.substringAfterLast("/")}")
            preparePlayer(nextTrack, looping = false)
            if (wasPlaying && enabled && !suspended) {
                mediaPlayer?.setOnPreparedListener {
                    try {
                        it.setVolume(targetVolume, targetVolume)
                        it.start()
                        _isPlaying.value = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start next track", e)
                    }
                }
            }
        }
    }

    private fun validateUri(uri: String): Boolean {
        return try {
            if (uri.startsWith("/")) {
                File(uri).canRead()
            } else {
                context.contentResolver.openInputStream(Uri.parse(uri))?.close()
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "URI validation failed: ${e.message}")
            false
        }
    }

    fun suspend() {
        suspended = true
        fadeOut()
        Log.d(TAG, "suspended - awaiting user input to resume")
    }

    fun resumeFromSuspend() {
        if (suspended) {
            suspended = false
            Log.d(TAG, "resumed from suspend")
            fadeIn()
        }
    }

    fun fadeIn(durationMs: Long = 500) {
        if (suspended) {
            Log.d(TAG, "fadeIn skipped: suspended (awaiting user input)")
            return
        }
        if (!enabled || currentUri == null) {
            Log.d(TAG, "fadeIn skipped: enabled=$enabled, uri=$currentUri")
            return
        }

        if (mediaPlayer == null) {
            if (isPlaylistMode && playlist.isNotEmpty()) {
                preparePlayer(playlist[playlistIndex], looping = false)
            } else if (!isPlaylistMode) {
                currentUri?.let { preparePlayer(it, looping = true) }
            }
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
        isPlaylistMode = false
        folderPath = null
        playlist = emptyList()
        _currentTrackName.value = null
    }
}
