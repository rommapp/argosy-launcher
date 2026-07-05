package com.nendo.argosy.data.steam

import com.nendo.argosy.data.local.entity.SteamAccountEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FOSS-flavor no-op stub. The `full` flavor implements Steam QR authentication via the
 * JavaSteam library; the FOSS build ships without Steam, so this exposes the same public
 * surface consumed by shared code while doing nothing.
 */
@Singleton
class SteamAuthManager @Inject constructor() {
    val qrAuthState: StateFlow<QrAuthState> = MutableStateFlow(QrAuthState.Idle)
    val authEvents: SharedFlow<SteamAuthEvent> = MutableSharedFlow()
    val isLoggedIn: StateFlow<Boolean> = MutableStateFlow(false)

    @Volatile
    var sessionDead: Boolean = false

    var connectingForAuth: Boolean = false

    suspend fun getActiveAccount(): SteamAccountEntity? = null

    suspend fun deleteAccount(accountId: Long) {}

    fun startQrAuth() {}

    fun cancelQrAuth() {}

    fun logout() {}
}
