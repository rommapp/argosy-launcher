package com.nendo.argosy.hardware

import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

class AudioLevelDebugActivity : ComponentActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start audio LED if not running
        if (!AudioReactiveLEDTest.isRunning()) {
            AudioReactiveLEDTest.toggle(this)
        }

        // Setup media player with the test file
        val musicFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "43. System Settings - Main Theme.mp3"
        )
        if (musicFile.exists()) {
            mediaPlayer = MediaPlayer.create(this, Uri.fromFile(musicFile))?.apply {
                isLooping = true
            }
        }

        setContent {
            AudioLevelDebugScreen(
                isPlaying = mediaPlayer?.isPlaying == true,
                onTogglePlay = { togglePlayback() }
            )
        }
    }

    private fun togglePlayback() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.start()
            }
            // Force recomposition
            setContent {
                AudioLevelDebugScreen(
                    isPlaying = player.isPlaying,
                    onTogglePlay = { togglePlayback() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun AudioLevelDebugScreen(
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {}
) {
    val levels by AudioReactiveLEDTest.levels.collectAsState()

    // Smoothed values with easing
    var smoothedMid by remember { mutableFloatStateOf(0f) }
    var smoothedHigh by remember { mutableFloatStateOf(0f) }

    // Apply smoothing each frame
    LaunchedEffect(levels) {
        val smoothingFactor = 0.15f // Lower = smoother
        smoothedMid += (levels.mid - smoothedMid) * smoothingFactor

        // High spikes (>80% or big jump) bypass easing
        val isHighSpike = levels.high > 0.8f || (levels.high - smoothedHigh) > 0.3f
        if (isHighSpike) {
            smoothedHigh = levels.high // instant
        } else {
            smoothedHigh += (levels.high - smoothedHigh) * smoothingFactor
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left bars - Mid levels (raw + smoothed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBar(
                    label = "MID",
                    level = levels.mid,
                    color = Color(0xFF4CAF50)
                )
                LevelBar(
                    label = "SMOOTH",
                    level = smoothedMid,
                    color = Color(0xFF81C784).copy(alpha = 0.7f)
                )
            }

            // Center - Output brightness + play button
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${(levels.output * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 48.sp
                )
                Text(
                    text = "OUTPUT",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Play/Pause button
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (isPlaying) Color(0xFF4CAF50) else Color(0xFF2196F3),
                            shape = androidx.compose.foundation.shape.CircleShape
                        )
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isPlaying) "||" else ">",
                        color = Color.White,
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPlaying) "PLAYING" else "TAP TO PLAY",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            // Right bars - High levels (raw + smoothed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelBar(
                    label = "HIGH",
                    level = levels.high,
                    color = Color(0xFFFF9800)
                )
                LevelBar(
                    label = "SMOOTH",
                    level = smoothedHigh,
                    color = Color(0xFFFFCC80).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun LevelBar(
    label: String,
    level: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${(level * 100).toInt()}%",
            color = Color.White,
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .width(40.dp)
                .background(Color.DarkGray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(level.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .background(color)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
