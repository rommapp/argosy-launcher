package com.nendo.argosy.data.cheats

import android.content.Context
import android.provider.Settings
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.util.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CheatsRepository"

@Singleton
class CheatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cheatsService: CheatsService,
    private val cheatDao: CheatDao,
    private val gameDao: GameDao
) {
    private val deviceToken: String by lazy {
        getOrCreateDeviceToken()
    }

    fun isConfigured(): Boolean = cheatsService.isConfigured()

    suspend fun syncCheatsForGame(game: GameEntity): Boolean {
        if (!isConfigured()) {
            Logger.debug(TAG, "CheatsDB not configured, skipping sync for game=${game.id}")
            return false
        }

        if (game.cheatsFetched) {
            Logger.debug(TAG, "Cheats already fetched for game=${game.id}")
            return true
        }

        val mappedPlatform = mapPlatformSlug(game.platformSlug)
        if (mappedPlatform == null) {
            Logger.debug(TAG, "Platform ${game.platformSlug} not supported for cheats lookup")
            gameDao.updateCheatsFetched(game.id, true)
            return true
        }

        Logger.debug(TAG, "Fetching cheats for game=${game.id}, name='${game.title}', platform=$mappedPlatform")

        return try {
            val result = cheatsService.lookupByName(game.title, mappedPlatform, deviceToken)

            if (result != null && result.cheats.isNotEmpty()) {
                val entities = result.cheats.map { cheat ->
                    CheatEntity(
                        gameId = game.id,
                        cheatIndex = cheat.index,
                        description = cheat.description,
                        code = cheat.code,
                        enabled = false
                    )
                }

                cheatDao.deleteByGameId(game.id)
                cheatDao.insertAll(entities)

                Logger.info(TAG, "Stored ${entities.size} cheats for game=${game.id} (${result.gameName})")
            } else {
                Logger.debug(TAG, "No cheats found for game=${game.id}")
            }

            gameDao.updateCheatsFetched(game.id, true)
            true
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to sync cheats for game=${game.id}: ${e.message}")
            false
        }
    }

    suspend fun getCheatsForGame(gameId: Long): List<CheatEntity> {
        return cheatDao.getCheatsForGame(gameId)
    }

    suspend fun getEnabledCheats(gameId: Long): List<CheatEntity> {
        return cheatDao.getEnabledCheats(gameId)
    }

    suspend fun setCheatEnabled(cheatId: Long, enabled: Boolean) {
        cheatDao.setEnabled(cheatId, enabled)
    }

    suspend fun setAllCheatsEnabled(gameId: Long, enabled: Boolean) {
        cheatDao.setAllEnabled(gameId, enabled)
    }

    suspend fun getCheatCount(gameId: Long): Int {
        return cheatDao.getCheatCount(gameId)
    }

    private fun mapPlatformSlug(platform: String): String? {
        return when (platform.lowercase()) {
            "nes", "nintendo_nes", "famicom" -> "nes"
            "snes", "super_nes", "sfc" -> "snes"
            "gb", "game_boy", "gameboy" -> "gb"
            "gbc", "game_boy_color" -> "gbc"
            "gba", "game_boy_advance" -> "gba"
            "n64", "nintendo_64" -> "n64"
            "nds", "nintendo_ds" -> "nds"
            "fds", "famicom_disk_system" -> "fds"
            "genesis", "mega_drive", "megadrive", "sega_genesis", "sega_megadrive" -> "genesis"
            "sms", "master_system", "sega_master_system" -> "sms"
            "gg", "game_gear", "sega_game_gear" -> "gg"
            "sega32x", "32x" -> "32x"
            "segacd", "mega_cd", "sega_cd" -> "segacd"
            "dc", "dreamcast", "sega_dreamcast" -> "dc"
            "saturn", "sega_saturn" -> "saturn"
            "psx", "ps1", "playstation", "sony_playstation" -> "psx"
            "psp", "playstation_portable", "sony_psp" -> "psp"
            "tg16", "pc_engine", "turbografx_16", "pce" -> "tg16"
            "tgcd", "pce_cd", "pc_engine_cd" -> "tgcd"
            "atari2600", "atari_2600" -> "atari2600"
            "atari7800", "atari_7800" -> "atari7800"
            "lynx", "atari_lynx" -> "lynx"
            else -> null
        }
    }

    private fun getOrCreateDeviceToken(): String {
        val prefs = context.getSharedPreferences("cheats_prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_token", null)
        if (existing != null) return existing

        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }

        val token = if (androidId.length >= 8) {
            androidId
        } else {
            UUID.randomUUID().toString().replace("-", "")
        }

        prefs.edit().putString("device_token", token).apply()
        return token
    }
}
