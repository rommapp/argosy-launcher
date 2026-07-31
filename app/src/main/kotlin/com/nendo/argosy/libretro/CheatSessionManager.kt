package com.nendo.argosy.libretro

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nendo.argosy.data.cheats.CheatsRepository
import com.nendo.argosy.data.local.dao.CheatDao
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.local.entity.CheatEntity
import com.nendo.argosy.data.local.entity.CheatVariantTuple
import com.nendo.argosy.libretro.scanner.MemoryScanner
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CheatSessionManager(
    private val gameId: Long,
    private val cheatDao: CheatDao,
    private val gameDao: GameDao,
    private val cheatsRepository: CheatsRepository,
    private val canSerialize: () -> Boolean,
    private val scope: CoroutineScope
) {
    val memoryScanner = MemoryScanner()

    var cheats by mutableStateOf<List<CheatEntity>>(emptyList())
        private set
    var variants by mutableStateOf<List<CheatVariantTuple>>(emptyList())
        private set
    var selectedVariant by mutableStateOf<Pair<String, String>?>(null)
        private set
    var sessionTainted: Boolean = false
        private set

    private var cheatsNeedReset = false
    private var retroView: GLRetroView? = null

    fun setRetroView(view: GLRetroView) {
        retroView = view
    }

    fun loadCheats(hardcoreMode: Boolean) {
        scope.launch {
            val game = gameDao.getById(gameId)
            Log.d(TAG, "loadCheats: game=$game, cheatsFetchedAt=${game?.cheatsFetchedAt}, configured=${cheatsRepository.isConfigured()}")

            if (game != null && game.cheatsFetchedAt == null && cheatsRepository.isConfigured()) {
                Log.d(TAG, "Fetching cheats from server for game $gameId (${game.title})")
                try {
                    val success = cheatsRepository.syncCheatsForGame(game)
                    Log.d(TAG, "Cheats sync result: $success")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch cheats: ${e.message}", e)
                }
            }

            variants = cheatsRepository.getVariantsForGame(gameId)
            selectedVariant = cheatsRepository.getSelectedVariant(gameId)
            cheats = cheatsRepository.getCheatsForGame(gameId)
            Log.d(TAG, "Loaded ${cheats.size} cheats for game $gameId, ${variants.size} variants, selected=$selectedVariant")
            if (cheats.any { it.enabled }) {
                applyAllEnabledCheats(hardcoreMode)
            }
        }
    }

    fun selectVariant(region: String, version: String, hardcoreMode: Boolean) {
        scope.launch {
            cheatsRepository.selectVariant(gameId, region, version)
            selectedVariant = region to version
            cheats = cheatsRepository.getCheatsForGame(gameId)
            Log.d(TAG, "Selected variant: region=$region, version=$version, cheats=${cheats.size}")

            val view = retroView
            if (view != null) {
                view.resetCheat()
                applyAllEnabledCheats(hardcoreMode)
            }
        }
    }

    fun handleToggleCheat(cheatId: Long, enabled: Boolean) {
        scope.launch {
            if (enabled) {
                sessionTainted = true
                Log.d(TAG, "Session marked as tainted (cheats enabled)")
            }
            cheatDao.setEnabled(cheatId, enabled, System.currentTimeMillis())
            cheats = cheatsRepository.getCheatsForGame(gameId)
            applyCheat(cheatId, enabled)
        }
    }

    fun handleCreateCheat(address: Int, value: Int, description: String) {
        scope.launch {
            val maxIndex = cheatDao.getMaxCheatIndex(gameId) ?: -1
            val code = String.format("%06X:%02X", address, value)
            val newCheat = CheatEntity(
                gameId = gameId,
                cheatIndex = maxIndex + 1,
                description = description,
                code = code,
                enabled = true,
                isUserCreated = true,
                lastUsedAt = System.currentTimeMillis()
            )
            val newId = cheatDao.insert(newCheat)
            cheats = cheatsRepository.getCheatsForGame(gameId)
            applyCheat(newId, true)
            Log.d(TAG, "Created custom cheat: $description -> $code")
        }
    }

    fun handleUpdateCheat(cheatId: Long, description: String, code: String) {
        scope.launch {
            cheatDao.updateDescription(cheatId, description)
            cheatDao.updateCode(cheatId, code)
            val cheat = cheats.find { it.id == cheatId }
            cheats = cheatsRepository.getCheatsForGame(gameId)
            if (cheat?.enabled == true) {
                val updatedCheat = cheats.find { it.id == cheatId }
                if (updatedCheat != null) {
                    retroView?.setCheat(updatedCheat.cheatIndex, true, updatedCheat.code)
                }
            }
            Log.d(TAG, "Updated cheat $cheatId: $description -> $code")
        }
    }

    fun handleDeleteCheat(cheatId: Long) {
        scope.launch {
            val cheat = cheats.find { it.id == cheatId }
            if (cheat?.enabled == true) {
                retroView?.setCheat(cheat.cheatIndex, false, cheat.code)
            }
            cheatDao.deleteById(cheatId)
            cheats = cheatsRepository.getCheatsForGame(gameId)
            Log.d(TAG, "Deleted cheat $cheatId")
        }
    }

    fun applyAllEnabledCheats(hardcoreMode: Boolean) {
        if (hardcoreMode) return
        val view = retroView ?: return
        cheats.filter { it.enabled }.forEach { cheat ->
            view.setCheat(cheat.cheatIndex, true, cheat.code)
            Log.d(TAG, "Applied enabled cheat ${cheat.cheatIndex}: ${cheat.description}")
        }
    }

    val hasAnyEnabledCheats: Boolean
        get() = cheats.any { it.enabled }

    suspend fun disableAllAndCycleForNetplay() {
        val hadEnabled = cheats.any { it.enabled }
        if (!hadEnabled) {
            Log.d(TAG, "Netplay cheats: no enabled cheats, no-op")
            return
        }
        val now = System.currentTimeMillis()
        cheats.filter { it.enabled }.forEach { cheat ->
            cheatDao.setEnabled(cheat.id, false, now)
        }
        cheats = cheatsRepository.getCheatsForGame(gameId)
        val view = retroView
        if (view != null) {
            cycleCheatsThroughState(view)
            Log.d(TAG, "Netplay cheats: disabled all enabled cheats and cycled state")
        } else {
            Log.w(TAG, "Netplay cheats: retroView null during disable-and-cycle")
        }
    }

    fun flushCheatReset() {
        if (!cheatsNeedReset) return
        val view = retroView ?: return
        cheatsNeedReset = false
        cycleCheatsThroughState(view)
        applyAllEnabledCheats(false)
        Log.d(TAG, "Flushed cheat reset cycle")
    }

    /**
     * Clearing a cheat only takes effect once the core reloads a state, so the round trip is the
     * point. Where the core cannot serialize the loaded content the attempt can abort the process
     * rather than fail, so the cheats are dropped without the round trip and the removal lands on
     * the next launch instead.
     */
    private fun cycleCheatsThroughState(view: GLRetroView) {
        if (!canSerialize()) {
            view.resetCheat()
            Log.w(TAG, "Cheat reset without state cycle: core cannot serialize this content")
            return
        }
        val stateData = view.serializeState()
        view.resetCheat()
        view.unserializeState(stateData)
    }

    private fun applyCheat(cheatId: Long, enabled: Boolean) {
        val view = retroView ?: return
        val cheat = cheats.find { it.id == cheatId } ?: return
        if (enabled) {
            view.setCheat(cheat.cheatIndex, true, cheat.code)
        } else {
            view.setCheat(cheat.cheatIndex, false, cheat.code)
            cheatsNeedReset = true
        }
        Log.d(TAG, "Applied cheat ${cheat.cheatIndex}: $enabled - ${cheat.description}")
    }

    companion object {
        private const val TAG = "CheatSessionManager"
    }
}
