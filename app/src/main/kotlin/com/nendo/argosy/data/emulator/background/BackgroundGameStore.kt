package com.nendo.argosy.data.emulator.background

import android.app.Application
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A game whose session ended while its emulator kept running. [pid] is the emulator process the
 * game was played in; a different pid under the same package is another run of the emulator.
 */
data class BackgroundGame(
    val emulatorPackage: String,
    val gameId: Long,
    val variantFileId: Long?,
    val pid: Int,
    val endedAtMillis: Long
)

@Singleton
class BackgroundGameStore @Inject constructor(application: Application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun all(): List<BackgroundGame> =
        prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_PREFIX)) return@mapNotNull null
            decode(key.removePrefix(KEY_PREFIX), value as? String ?: return@mapNotNull null)
        }

    fun put(game: BackgroundGame) {
        prefs.edit().putString(KEY_PREFIX + game.emulatorPackage, encode(game)).apply()
    }

    fun remove(emulatorPackage: String) {
        prefs.edit().remove(KEY_PREFIX + emulatorPackage).apply()
    }

    private fun encode(game: BackgroundGame): String =
        listOf(game.gameId, game.variantFileId ?: "", game.pid, game.endedAtMillis).joinToString(SEPARATOR)

    private fun decode(emulatorPackage: String, value: String): BackgroundGame? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 4) return null
        return BackgroundGame(
            emulatorPackage = emulatorPackage,
            gameId = parts[0].toLongOrNull() ?: return null,
            variantFileId = parts[1].toLongOrNull(),
            pid = parts[2].toIntOrNull() ?: return null,
            endedAtMillis = parts[3].toLongOrNull() ?: return null
        )
    }

    private companion object {
        const val PREFS_NAME = "background_games"
        const val KEY_PREFIX = "game:"
        const val SEPARATOR = "|"
    }
}
