package com.nendo.argosy.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

internal fun interface DownloadConnectivity {
    fun availability(): Flow<Boolean>

    companion object {
        val AlwaysAvailable = DownloadConnectivity { flowOf(true) }
    }
}

internal class AndroidDownloadConnectivity(context: Context) : DownloadConnectivity {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    override fun availability(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(manager.activeNetwork != null)
            }
        }
        val request = NetworkRequest.Builder().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                clearCapabilities()
            } else {
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            }
        }.build()
        manager.registerNetworkCallback(request, callback)
        trySend(manager.activeNetwork != null)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }
}
