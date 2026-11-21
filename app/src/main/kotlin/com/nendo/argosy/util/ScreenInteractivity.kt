package com.nendo.argosy.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the device is awake and interactive, following screen on and off broadcasts for the
 * life of the process.
 */
@Singleton
class ScreenInteractivity @Inject constructor(@ApplicationContext context: Context) {

    private val _screenOn = MutableStateFlow(
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive ?: true
    )
    val screenOn: StateFlow<Boolean> = _screenOn.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> _screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> _screenOn.value = false
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(receiver, filter)
    }
}
