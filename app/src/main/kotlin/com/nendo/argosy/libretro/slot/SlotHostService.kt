package com.nendo.argosy.libretro.slot

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.os.bundleOf
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.LaunchOrigin
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.emulator.background.TaskSnapshots
import com.nendo.argosy.libretro.slot.SlotProtocol.optionalLong
import com.nendo.argosy.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Argosy's main process end of every game slot. It applies a slot's session calls to the one
 * [PlaySessionTracker], keeps [SlotRegistry] current, and ends the session of a slot whose process
 * dies while it owns it, so the save on disk is still cached and synced.
 */
@AndroidEntryPoint
class SlotHostService : Service() {

    @Inject lateinit var tracker: PlaySessionTracker
    @Inject lateinit var registry: SlotRegistry

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message -> receive(message) })

    override fun onCreate() {
        super.onCreate()
        scope.launch { tracker.activeSession.collect { pushSessionState() } }
        scope.launch {
            tracker.saveWrites.collect {
                ownerSlot()?.let { send(it, SlotProtocol.SAVE_WRITTEN_PUSH, Bundle()) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun receive(message: Message): Boolean {
        val data = message.data ?: return false
        val slotIndex = data.getInt(SlotProtocol.KEY_SLOT, -1)
        if (slotIndex < 1) return false
        when (message.what) {
            SlotProtocol.REGISTER -> register(slotIndex, data, message.replyTo ?: return false)
            SlotProtocol.START_SESSION -> {
                val hardcore = data.getBoolean(SlotProtocol.KEY_HARDCORE)
                registry.update(slotIndex) { it.copy(hardcore = hardcore) }
                startSession(data)
            }
            SlotProtocol.RESUMED -> {
                GuideKeyRouter.claim(guideOwner(slotIndex)) { pressed ->
                    send(slotIndex, SlotProtocol.GUIDE_KEY, bundleOf(SlotProtocol.KEY_PRESSED to pressed))
                }
                if (owns(slotIndex)) tracker.onBuiltInGameResumed()
            }
            SlotProtocol.PAUSED -> {
                GuideKeyRouter.release(guideOwner(slotIndex))
                if (owns(slotIndex)) tracker.onBuiltInGamePaused()
            }
            SlotProtocol.UPDATE_HARDCORE -> {
                val hardcore = data.getBoolean(SlotProtocol.KEY_HARDCORE)
                registry.update(slotIndex) { it.copy(hardcore = hardcore) }
                tracker.updateSessionHardcore(data.getLong(SlotProtocol.KEY_GAME_ID), hardcore)
            }
            SlotProtocol.SAVE_WRITTEN -> if (owns(slotIndex)) tracker.reportSaveWritten()
            SlotProtocol.CACHE_FOR_QUIT -> {
                val requestId = data.getInt(SlotProtocol.KEY_REQUEST_ID)
                scope.launch {
                    if (owns(slotIndex)) cacheSessionForQuit(tracker)
                    send(slotIndex, SlotProtocol.CACHE_DONE, bundleOf(SlotProtocol.KEY_REQUEST_ID to requestId))
                }
            }
            SlotProtocol.END_SESSION -> {
                endBuiltInSessionIfOwned(
                    tracker,
                    data.getLong(SlotProtocol.KEY_GAME_ID),
                    data.getBoolean(SlotProtocol.KEY_SKIP_SAVE_SYNC)
                )
                registry.slots.value[slotIndex]?.let { forgetFrame(it.taskId) }
                registry.remove(slotIndex)
            }
            else -> return false
        }
        return true
    }

    private fun register(slotIndex: Int, data: Bundle, client: Messenger) {
        val slot = LiveSlot(
            slotIndex = slotIndex,
            pid = data.getInt(SlotProtocol.KEY_PID),
            taskId = data.getInt(SlotProtocol.KEY_TASK_ID),
            gameId = data.getLong(SlotProtocol.KEY_GAME_ID),
            variantFileId = data.optionalLong(SlotProtocol.KEY_VARIANT_FILE_ID),
            platformSlug = data.getString(SlotProtocol.KEY_PLATFORM),
            startedAtMillis = data.getLong(SlotProtocol.KEY_STARTED_AT),
            hardcore = data.getBoolean(SlotProtocol.KEY_HARDCORE)
        )
        registry.put(slot, client)
        try {
            client.binder.linkToDeath({ onSlotDied(slot) }, 0)
        } catch (e: RemoteException) {
            onSlotDied(slot)
            return
        }
        pushSessionState()
        Logger.debug(TAG, "slot $slotIndex holds game ${slot.gameId} in pid ${slot.pid}")
    }

    private fun onSlotDied(slot: LiveSlot) {
        scope.launch {
            Logger.warn(TAG, "slot ${slot.slotIndex} process ${slot.pid} died holding game ${slot.gameId}")
            if (registry.slots.value[slot.slotIndex]?.pid == slot.pid) {
                registry.remove(slot.slotIndex)
                GuideKeyRouter.release(guideOwner(slot.slotIndex))
            }
            endBuiltInSessionIfOwned(tracker, slot.gameId, skipSaveSync = false)
            removeTask(slot.taskId)
            forgetFrame(slot.taskId)
        }
    }

    private fun guideOwner(slotIndex: Int): String = "slot:$slotIndex"

    private fun forgetFrame(taskId: Int) {
        val context = applicationContext
        scope.launch(Dispatchers.IO) { TaskSnapshots.deleteGameFrame(context, taskId) }
    }

    private fun removeTask(taskId: Int) {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        activityManager.appTasks
            .firstOrNull { task -> runCatching { task.taskInfo.taskId }.getOrNull() == taskId }
            ?.let { task -> runCatching { task.finishAndRemoveTask() } }
    }

    private fun startSession(data: Bundle) {
        tracker.startSession(
            gameId = data.getLong(SlotProtocol.KEY_GAME_ID),
            emulatorPackage = EmulatorRegistry.BUILTIN_PACKAGE,
            coreName = data.getString(SlotProtocol.KEY_CORE_NAME),
            isHardcore = data.getBoolean(SlotProtocol.KEY_HARDCORE),
            isNewGame = data.getBoolean(SlotProtocol.KEY_NEW_GAME),
            isNetplayGuest = data.getBoolean(SlotProtocol.KEY_NETPLAY_GUEST),
            variantFileId = data.optionalLong(SlotProtocol.KEY_VARIANT_FILE_ID),
            origin = runCatching { LaunchOrigin.valueOf(data.getString(SlotProtocol.KEY_ORIGIN).orEmpty()) }
                .getOrDefault(LaunchOrigin.INTERNAL)
        )
    }

    private fun owns(slotIndex: Int): Boolean = ownerSlot() == slotIndex

    private fun ownerSlot(): Int? {
        val session = tracker.activeSession.value ?: return null
        if (session.emulatorPackage != EmulatorRegistry.BUILTIN_PACKAGE) return null
        return registry.slots.value.values
            .firstOrNull { it.gameId == session.gameId && it.variantFileId == session.variantFileId }
            ?.slotIndex
    }

    private fun pushSessionState() {
        val session = tracker.activeSession.value
        val owner = ownerSlot()
        registry.slots.value.keys.forEach { slotIndex ->
            val start = if (slotIndex == owner) session?.startTime?.toEpochMilli() else null
            send(slotIndex, SlotProtocol.SESSION_STATE, bundleOf(SlotProtocol.KEY_SESSION_START to (start ?: SlotProtocol.NONE)))
        }
    }

    private fun send(slotIndex: Int, what: Int, data: Bundle) {
        val client = registry.client(slotIndex) ?: return
        try {
            client.send(Message.obtain(null, what).apply { this.data = data })
        } catch (e: RemoteException) {
            Logger.warn(TAG, "slot $slotIndex unreachable for message $what", e)
        }
    }

    private companion object {
        const val TAG = "SlotHostService"
    }
}
