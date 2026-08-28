package com.nendo.argosy.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AudioTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TEST -> {
                Log.i(TAG, "=== Audio Analyzer Test Started ===")
                val results = AudioAnalyzerTest.runDiagnostics(context)
                results.lines().forEach { line ->
                    if (line.isNotBlank()) Log.i(TAG, line)
                }
                Log.i(TAG, "=== Audio Analyzer Test Complete ===")
            }
            ACTION_TOGGLE_LED -> {
                val running = AudioReactiveLEDTest.toggle(context)
                Log.i(TAG, "Audio reactive LED: ${if (running) "STARTED" else "STOPPED"}")
            }
        }
    }

    companion object {
        private const val TAG = "AudioTest"
        const val ACTION_TEST = "com.nendo.argosy.TEST_AUDIO"
        const val ACTION_TOGGLE_LED = "com.nendo.argosy.TOGGLE_AUDIO_LED"
    }
}
