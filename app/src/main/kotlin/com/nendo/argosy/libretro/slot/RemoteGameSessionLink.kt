package com.nendo.argosy.libretro.slot

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.os.bundleOf
import com.nendo.argosy.core.notification.ResolvedNotice
import com.nendo.argosy.libretro.slot.SlotProtocol.optionalLong
import com.nendo.argosy.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * A slot process's [GameSessionLink]: every call is a message to [SlotHostService] in Argosy's
 * main process, sent in order. Calls made before the service is bound wait in a queue.
 */
class RemoteGameSessionLink(
    private val context: Context,
    private val slotIndex: Int,
    private val onCloseRequested: () -> Unit,
    private val onGuideKey: (pressed: Boolean) -> Unit,
    private val onNotice: (ResolvedNotice) -> Unit
) : GameSessionLink {

    private val handler = Handler(Looper.getMainLooper()) { message -> receive(message) }
    private val replyTo = Messenger(handler)
    private val pending = ArrayDeque<Message>()
    private val awaitingCache = ConcurrentHashMap<Int, CompletableDeferred<Unit>>()
    private val requestIds = AtomicInteger()
    private var host: Messenger? = null
    private var bound = false
    private var resumeOverlayClosed: ((Boolean) -> Unit)? = null

    private val _sessionStart = MutableStateFlow<Instant?>(null)
    override val sessionStart: StateFlow<Instant?> = _sessionStart.asStateFlow()

    private val _saveWrites = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val saveWrites: Flow<Unit> = _saveWrites

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            host = Messenger(service)
            while (pending.isNotEmpty()) deliver(pending.removeFirst())
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            host = null
            Logger.warn(TAG, "slot $slotIndex lost Argosy's session host")
            finishResumeOverlay(shown = true)
        }
    }

    fun connect() {
        if (bound) return
        bound = context.bindService(Intent(context, SlotHostService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (!bound) Logger.error(TAG, "slot $slotIndex could not bind Argosy's session host")
    }

    /**
     * Tells the host which game this slot holds, so the launcher lists it and can resume or close it.
     */
    fun register(
        taskId: Int,
        gameId: Long,
        variantFileId: Long?,
        platformSlug: String?,
        startedAtMillis: Long,
        hardcore: Boolean
    ) {
        send(
            SlotProtocol.REGISTER,
            bundleOf(
                SlotProtocol.KEY_PID to android.os.Process.myPid(),
                SlotProtocol.KEY_TASK_ID to taskId,
                SlotProtocol.KEY_GAME_ID to gameId,
                SlotProtocol.KEY_VARIANT_FILE_ID to (variantFileId ?: SlotProtocol.NONE),
                SlotProtocol.KEY_PLATFORM to platformSlug,
                SlotProtocol.KEY_STARTED_AT to startedAtMillis,
                SlotProtocol.KEY_HARDCORE to hardcore
            )
        )
    }

    override fun startSession(request: SessionStartRequest) {
        _sessionStart.value = Instant.now()
        send(
            SlotProtocol.START_SESSION,
            bundleOf(
                SlotProtocol.KEY_GAME_ID to request.gameId,
                SlotProtocol.KEY_CORE_NAME to request.coreName,
                SlotProtocol.KEY_HARDCORE to request.isHardcore,
                SlotProtocol.KEY_NEW_GAME to request.isNewGame,
                SlotProtocol.KEY_NETPLAY_GUEST to request.isNetplayGuest,
                SlotProtocol.KEY_VARIANT_FILE_ID to (request.variantFileId ?: SlotProtocol.NONE),
                SlotProtocol.KEY_ORIGIN to request.origin.name
            )
        )
    }

    override fun hasActiveSession(): Boolean = _sessionStart.value != null

    override fun onResumed() = send(SlotProtocol.RESUMED, Bundle())

    override fun onPaused() = send(SlotProtocol.PAUSED, Bundle())

    override fun updateHardcore(gameId: Long, isHardcore: Boolean) = send(
        SlotProtocol.UPDATE_HARDCORE,
        bundleOf(SlotProtocol.KEY_GAME_ID to gameId, SlotProtocol.KEY_HARDCORE to isHardcore)
    )

    override fun reportSaveWritten() = send(SlotProtocol.SAVE_WRITTEN, Bundle())

    override suspend fun cacheForQuit() {
        val requestId = requestIds.incrementAndGet()
        val done = CompletableDeferred<Unit>()
        awaitingCache[requestId] = done
        send(SlotProtocol.CACHE_FOR_QUIT, bundleOf(SlotProtocol.KEY_REQUEST_ID to requestId))
        if (withTimeoutOrNull(CACHE_TIMEOUT_MS) { done.await() } == null) {
            Logger.warn(TAG, "slot $slotIndex quit cache timed out")
        }
        awaitingCache.remove(requestId)
    }

    override fun endSessionIfOwned(gameId: Long, skipSaveSync: Boolean) = send(
        SlotProtocol.END_SESSION,
        bundleOf(SlotProtocol.KEY_GAME_ID to gameId, SlotProtocol.KEY_SKIP_SAVE_SYNC to skipSaveSync)
    )

    override fun showResumeOverlay(displayId: Int, onClosed: (shown: Boolean) -> Unit) {
        finishResumeOverlay(shown = true)
        if (!bound) {
            onClosed(false)
            return
        }
        resumeOverlayClosed = onClosed
        send(SlotProtocol.SHOW_RESUME, bundleOf(SlotProtocol.KEY_DISPLAY_ID to displayId))
    }

    private fun finishResumeOverlay(shown: Boolean) {
        val callback = resumeOverlayClosed ?: return
        resumeOverlayClosed = null
        callback(shown)
    }

    override fun release() {
        if (!bound) return
        bound = false
        if (host != null) while (pending.isNotEmpty()) deliver(pending.removeFirst())
        runCatching { context.unbindService(connection) }
        host = null
    }

    private fun send(what: Int, data: Bundle) {
        data.putInt(SlotProtocol.KEY_SLOT, slotIndex)
        val message = Message.obtain(null, what).apply {
            this.data = data
            this.replyTo = this@RemoteGameSessionLink.replyTo
        }
        if (Looper.myLooper() == Looper.getMainLooper()) enqueue(message) else handler.post { enqueue(message) }
    }

    private fun enqueue(message: Message) {
        if (host == null) pending.addLast(message) else deliver(message)
    }

    private fun deliver(message: Message) {
        try {
            host?.send(message) ?: pending.addFirst(message)
        } catch (e: RemoteException) {
            Logger.warn(TAG, "slot $slotIndex message ${message.what} not delivered", e)
        }
    }

    private fun receive(message: Message): Boolean {
        val data = message.data
        when (message.what) {
            SlotProtocol.SESSION_STATE ->
                _sessionStart.value = data.optionalLong(SlotProtocol.KEY_SESSION_START)?.let(Instant::ofEpochMilli)
            SlotProtocol.SAVE_WRITTEN_PUSH -> _saveWrites.tryEmit(Unit)
            SlotProtocol.CACHE_DONE -> awaitingCache[data.getInt(SlotProtocol.KEY_REQUEST_ID)]?.complete(Unit)
            SlotProtocol.SAVE_AND_CLOSE -> onCloseRequested()
            SlotProtocol.GUIDE_KEY -> onGuideKey(data.getBoolean(SlotProtocol.KEY_PRESSED))
            SlotProtocol.NOTICE -> ResolvedNotice.fromBundle(data)?.let(onNotice)
            SlotProtocol.RESUME_CLOSED -> finishResumeOverlay(data.getBoolean(SlotProtocol.KEY_SHOWN))
            else -> return false
        }
        return true
    }

    private companion object {
        const val TAG = "RemoteGameSessionLink"
        const val CACHE_TIMEOUT_MS = 15_000L
    }
}
