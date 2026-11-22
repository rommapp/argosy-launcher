package com.nendo.argosy.data.social.uploader

import android.content.Context
import android.provider.Settings
import androidx.room.withTransaction
import com.nendo.argosy.data.local.ALauncherDatabase
import com.nendo.argosy.data.local.dao.GameUserOverlayDao
import com.nendo.argosy.data.local.dao.PlaySessionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds the other devices' play sessions to each game's play time total, once per session, so every
 * screen that shows a game's play time counts every device. A session whose game is not in the
 * library yet waits until it is.
 */
@Singleton
class RemotePlayTimeFolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ALauncherDatabase,
    private val playSessionDao: PlaySessionDao,
    private val overlayDao: GameUserOverlayDao
) {
    suspend fun fold(ownerUserId: Long): Int {
        val localDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return database.withTransaction {
            val pending = playSessionDao.getUnfoldedRemoteMinutes(ownerUserId, localDeviceId)
            pending.filter { it.minutes > 0 }.forEach { overlayDao.addPlayTime(ownerUserId, it.gameId, it.minutes) }
            playSessionDao.markRemoteFolded(ownerUserId, localDeviceId)
        }
    }
}
