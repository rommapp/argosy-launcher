package com.nendo.argosy.libretro

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.swordfish.libretrodroid.GLRetroView
import java.io.File
import java.security.MessageDigest

class SaveStateManager(
    private val savesDir: File,
    private val statesDir: File,
    private val romPath: String,
    private val gameId: Long,
    private val gameDao: GameDao,
    private val saveCacheManager: SaveCacheManager
) {
    private var lastSramHash: String? = null
    var hasQuickSave by mutableStateOf(false)
        private set

    data class RestoreResult(
        val sramData: ByteArray?,
        val switchToHardcore: Boolean = false
    )

    fun initializeFromExistingSave(existingSram: ByteArray?) {
        lastSramHash = existingSram?.let { hashBytes(it) }
        hasQuickSave = getQuickSaveFile().exists()
    }

    fun getQuickSaveFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(statesDir, "$romName.state")
    }

    fun getSramFile(): File {
        val romName = File(romPath).nameWithoutExtension
        return File(savesDir, "$romName.srm")
    }

    suspend fun restoreSaveForLaunchMode(launchMode: LaunchMode): RestoreResult {
        if (gameId < 0) {
            Log.w(TAG, "No valid gameId, using existing save")
            val bytes = getSramFile().takeIf { it.exists() }?.readBytes()
            return RestoreResult(bytes)
        }

        return when (launchMode) {
            LaunchMode.NEW_HARDCORE, LaunchMode.NEW_CASUAL -> {
                Log.d(TAG, "New game mode - starting fresh (no save)")
                val sramFile = getSramFile()
                if (sramFile.exists()) {
                    val result = saveCacheManager.cacheAsRollback(
                        gameId,
                        EmulatorRegistry.BUILTIN_PACKAGE,
                        sramFile.absolutePath
                    )
                    when (result) {
                        is SaveCacheManager.CacheResult.Created ->
                            Log.d(TAG, "Created rollback backup before fresh start")
                        is SaveCacheManager.CacheResult.Duplicate ->
                            Log.d(TAG, "Rollback skipped - identical save already cached")
                        is SaveCacheManager.CacheResult.Failed ->
                            Log.w(TAG, "Failed to create rollback backup")
                    }
                    sramFile.delete()
                    Log.d(TAG, "Deleted existing save file for fresh start")
                }
                RestoreResult(null)
            }
            LaunchMode.RESUME_HARDCORE -> {
                Log.d(TAG, "Resuming hardcore - restoring hardcore save")
                val hardcoreSave = saveCacheManager.getLatestHardcoreSave(gameId)
                if (hardcoreSave != null) {
                    val isValid = saveCacheManager.isValidHardcoreSave(hardcoreSave)
                    if (!isValid) {
                        Log.w(TAG, "Hardcore save missing trailer - save may have been modified externally")
                    }
                    val bytes = saveCacheManager.getSaveBytesFromEntity(hardcoreSave)
                    if (bytes != null) {
                        getSramFile().writeBytes(bytes)
                        Log.d(TAG, "Restored hardcore save (${bytes.size} bytes, valid=$isValid)")
                    }
                    RestoreResult(bytes)
                } else {
                    Log.w(TAG, "No hardcore save found, starting fresh")
                    RestoreResult(null)
                }
            }
            LaunchMode.RESUME -> restoreResumeSave()
        }
    }

    private suspend fun restoreResumeSave(): RestoreResult {
        val game = gameDao.getById(gameId)
        val activeSaveTimestamp = game?.activeSaveTimestamp
        val activeChannel = game?.activeSaveChannel

        val targetSave = when {
            activeSaveTimestamp != null -> {
                Log.d(TAG, "RESUME: Looking for activated save at timestamp $activeSaveTimestamp")
                saveCacheManager.getByTimestamp(gameId, activeSaveTimestamp)
            }
            activeChannel != null -> {
                Log.d(TAG, "RESUME: Looking for most recent save in channel '$activeChannel'")
                saveCacheManager.getMostRecentInChannel(gameId, activeChannel)
            }
            else -> {
                Log.d(TAG, "RESUME: Looking for most recent save overall")
                saveCacheManager.getMostRecentSave(gameId)
            }
        }

        if (targetSave != null) {
            var switchToHardcore = false
            if (targetSave.isHardcore) {
                val isValid = saveCacheManager.isValidHardcoreSave(targetSave)
                if (isValid) {
                    switchToHardcore = true
                    Log.d(TAG, "RESUME: Loading hardcore save, switching to hardcore mode")
                } else {
                    Log.w(TAG, "RESUME: Hardcore save missing trailer, loading as casual")
                }
            }
            val bytes = saveCacheManager.getSaveBytesFromEntity(targetSave)
            if (bytes != null) {
                getSramFile().writeBytes(bytes)
                Log.d(TAG, "RESUME: Restored save (${bytes.size} bytes, hardcore=${targetSave.isHardcore})")
            }
            return RestoreResult(bytes, switchToHardcore)
        } else {
            Log.d(TAG, "RESUME: No cached saves, using existing .srm if present")
            val bytes = getSramFile().takeIf { it.exists() }?.readBytes()
            return RestoreResult(bytes)
        }
    }

    fun saveSram(retroView: GLRetroView) {
        try {
            val sramData = retroView.serializeSRAM()
            if (sramData.isEmpty()) return

            val currentHash = hashBytes(sramData)
            if (currentHash == lastSramHash) return

            getSramFile().writeBytes(sramData)
            lastSramHash = currentHash
        } catch (_: Exception) {
        }
    }

    fun performQuickSave(retroView: GLRetroView): Boolean {
        return try {
            val stateData = retroView.serializeState()
            getQuickSaveFile().writeBytes(stateData)
            hasQuickSave = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun performQuickLoad(retroView: GLRetroView): Boolean {
        return try {
            val stateFile = getQuickSaveFile()
            if (stateFile.exists()) {
                val stateData = stateFile.readBytes()
                retroView.unserializeState(stateData)
                true
            } else false
        } catch (_: Exception) {
            false
        }
    }

    private fun hashBytes(data: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "SaveStateManager"
    }
}
