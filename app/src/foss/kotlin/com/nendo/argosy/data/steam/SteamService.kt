package com.nendo.argosy.data.steam

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * FOSS-flavor no-op stub. The `full` flavor runs a foreground Service that maintains the
 * Steam client connection via the JavaSteam library. The FOSS build ships without Steam, so
 * this Service does nothing: it never connects and stops itself immediately if started. It
 * keeps the same public surface (state flow, disconnect, LocalBinder, extras) so shared code
 * and the manifest entry continue to compile and bind.
 */
class SteamService : Service() {
    val state: StateFlow<SteamServiceState> = MutableStateFlow(SteamServiceState())

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): SteamService = this@SteamService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    fun disconnect() {}

    companion object {
        const val EXTRA_AUTO_CONNECT = "auto_connect"
        const val EXTRA_FORCE_CONNECT = "force_connect"
        const val EXTRA_CONNECT_FOR_AUTH = "connect_for_auth"
    }
}
