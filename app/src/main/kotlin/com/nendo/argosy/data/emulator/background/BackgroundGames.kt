package com.nendo.argosy.data.emulator.background

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An app with a task on screen or in the background. [game] is set when the app is an emulator
 * still holding the game a session of ours ended in.
 */
data class RunningApp(
    val taskId: Int,
    val packageName: String,
    val displayId: Int,
    val rssKb: Long?,
    val game: BackgroundGame?
)

/**
 * The apps and games left running on a device with a root shell, where the task list and process
 * table can be read. Off root every query answers empty; [KeepAlivePolicy] decides which
 * emulators stay alive.
 */
@Singleton
class BackgroundGames @Inject constructor(
    private val application: Application,
    private val store: BackgroundGameStore,
    private val playSessionTracker: PlaySessionTracker,
    private val keepAlivePolicy: KeepAlivePolicy,
    private val gameLauncher: GameLauncher
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "BackgroundGames")
    private var started = false

    val isSupported: Boolean get() = keepAlivePolicy.isSupported

    suspend fun keepsAlive(emulatorPackage: String): Boolean = keepAlivePolicy.keepsAlive(emulatorPackage)

    fun start() {
        if (started) return
        started = true
        scope.launch {
            var previous: ActiveSession? = null
            playSessionTracker.activeSession.collect { current ->
                val ended = previous
                previous = current
                if (current == null && ended != null) remember(ended)
                if (current != null) store.remove(current.emulatorPackage)
            }
        }
    }

    /**
     * Records the game [session] leaves running in its emulator, when that emulator stays alive.
     * Every session end is recorded on its own; a caller that needs the record at once can call
     * this directly.
     */
    suspend fun remember(session: ActiveSession) {
        if (!keepsAlive(session.emulatorPackage)) return
        val process = read()?.processes?.get(session.emulatorPackage) ?: return
        store.put(
            BackgroundGame(
                emulatorPackage = session.emulatorPackage,
                gameId = session.gameId,
                variantFileId = session.variantFileId,
                pid = process.pid,
                endedAtMillis = System.currentTimeMillis()
            )
        )
        Logger.debug(TAG, "kept ${session.emulatorPackage} pid=${process.pid} for gameId=${session.gameId}")
    }

    /**
     * Every app with a task, Argosy excluded, joined to the game its emulator holds. Records whose
     * emulator process is gone or was restarted are dropped from the store here.
     */
    suspend fun refresh(): List<RunningApp> {
        if (!isSupported) return emptyList()
        val snapshot = read() ?: return emptyList()
        val games = liveRecords(snapshot)
        return snapshot.tasks
            .filter { it.packageName != application.packageName }
            .distinctBy { it.packageName }
            .map { task ->
                RunningApp(
                    taskId = task.taskId,
                    packageName = task.packageName,
                    displayId = task.displayId,
                    rssKb = snapshot.processes[task.packageName]?.rssKb,
                    game = games[task.packageName]
                )
            }
    }

    /**
     * The running app holding [gameId] (and [variantFileId]) in [emulatorPackage], or null when
     * that emulator is not running it.
     */
    suspend fun liveGame(gameId: Long, variantFileId: Long?, emulatorPackage: String): RunningApp? =
        liveApp(emulatorPackage)?.takeIf { it.game?.gameId == gameId && it.game.variantFileId == variantFileId }

    suspend fun liveApp(packageName: String): RunningApp? =
        if (!isSupported) null else refresh().firstOrNull { it.packageName == packageName }

    /**
     * Brings [taskId] to the front without sending its app an intent, so the app resumes where it
     * was. False when the system refused.
     */
    fun bringToFront(taskId: Int): Boolean = try {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.moveTaskToFront(taskId, 0)
        true
    } catch (e: SecurityException) {
        Logger.warn(TAG, "moveTaskToFront refused for task $taskId", e)
        false
    }

    suspend fun close(packageName: String) {
        store.remove(packageName)
        gameLauncher.forceStopEmulator(packageName)
    }

    private fun liveRecords(snapshot: Snapshot): Map<String, BackgroundGame> {
        val live = mutableMapOf<String, BackgroundGame>()
        for (record in store.all()) {
            if (snapshot.processes[record.emulatorPackage]?.pid == record.pid) {
                live[record.emulatorPackage] = record
            } else {
                store.remove(record.emulatorPackage)
            }
        }
        return live
    }

    private data class Snapshot(val tasks: List<RunningTask>, val processes: Map<String, ProcessStat>)

    private suspend fun read(): Snapshot? = withContext(Dispatchers.IO) {
        val result = RootShell.run(application, "am stack list\necho $PS_MARKER\nps -A -o PID,RSS,NAME")
            ?.takeIf { it.succeeded }
            ?: return@withContext null
        val split = result.output.indexOf(PS_MARKER)
        if (split < 0) return@withContext null
        Snapshot(
            tasks = RunningTaskParser.parseTasks(result.output.subList(0, split)),
            processes = RunningTaskParser.parseProcesses(result.output.subList(split + 1, result.output.size))
        )
    }

    private companion object {
        const val TAG = "BackgroundGames"
        const val PS_MARKER = "--argosy-ps--"
    }
}
