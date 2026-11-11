package com.nendo.argosy.data.emulator.background

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.view.Display
import com.nendo.argosy.data.emulator.ActiveSession
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.GameLauncher
import com.nendo.argosy.data.emulator.PlaySessionTracker
import com.nendo.argosy.data.repository.AppsRepository
import com.nendo.argosy.hardware.builtInGameAppTask
import com.nendo.argosy.libretro.LibretroActivity
import com.nendo.argosy.libretro.slot.SlotRegistry
import java.io.File
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import com.nendo.argosy.util.SafeCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An app with a task on screen or in the background. [game] is set when the app is an emulator
 * still holding the game a session of ours ended in. [runStartedAtMillis] is when the process
 * holding it started, the start of the run its play time is counted from; [hardcore] is true
 * only for a built-in game running under RetroAchievements hardcore rules. [platformSlug] is set
 * for a game slot, whose pool weight it decides.
 */
data class RunningApp(
    val taskId: Int,
    val packageName: String,
    val displayId: Int,
    val rssKb: Long?,
    val game: BackgroundGame?,
    val runStartedAtMillis: Long?,
    val hardcore: Boolean,
    val slotIndex: Int?,
    val platformSlug: String? = null
)

/**
 * The apps and games left running. External apps are listed only on a device with a root shell,
 * where the task list and process table can be read; the built-in game left open in Argosy's own
 * task is listed everywhere. [KeepAlivePolicy] decides which games stay alive.
 */
@Singleton
class BackgroundGames @Inject constructor(
    private val application: Application,
    private val store: BackgroundGameStore,
    private val playSessionTracker: PlaySessionTracker,
    private val keepAlivePolicy: KeepAlivePolicy,
    private val gameLauncher: GameLauncher,
    private val slotRegistry: SlotRegistry,
    private val appsRepository: AppsRepository
) {
    private val scope = SafeCoroutineScope(Dispatchers.IO, "BackgroundGames")
    private var started = false

    private val _recorded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recorded: SharedFlow<Unit> = _recorded.asSharedFlow()

    val isSupported: Boolean get() = keepAlivePolicy.isSupported

    suspend fun keepsAlive(emulatorPackage: String): Boolean = keepAlivePolicy.keepsAlive(emulatorPackage)

    fun start() {
        if (started) return
        started = true
        scope.launch { keepAlivePolicy.grantUsageAccessOnRoot() }
        scope.launch { restoreTransitionScale() }
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
        if (session.emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) return
        if (!keepsAlive(session.emulatorPackage)) return
        val pid = read()?.processes?.get(session.emulatorPackage)?.pid ?: return
        store.put(
            BackgroundGame(
                emulatorPackage = session.emulatorPackage,
                gameId = session.gameId,
                variantFileId = session.variantFileId,
                pid = pid,
                endedAtMillis = System.currentTimeMillis()
            )
        )
        Logger.debug(TAG, "kept ${session.emulatorPackage} pid=$pid for gameId=${session.gameId}")
        _recorded.tryEmit(Unit)
    }

    /**
     * Every app with a task that the player could open from a launcher, every Argosy install excluded, joined
     * to the game its emulator holds, then every built-in game left open: the one in Argosy's own
     * task and one per game slot. Off root only the built-in games are listed.
     * Records whose emulator process is gone or was restarted are dropped from the store here.
     */
    suspend fun refresh(): List<RunningApp> = externalApps() + builtInApps()

    private suspend fun externalApps(): List<RunningApp> {
        if (!isSupported) return emptyList()
        val snapshot = read() ?: return emptyList()
        val games = liveRecords(snapshot)
        val now = System.currentTimeMillis()
        return snapshot.tasks
            .filter { !appsRepository.isArgosy(it.packageName) && isLaunchable(it.packageName) }
            .distinctBy { it.packageName }
            .map { task ->
                val process = snapshot.processes[task.packageName]
                RunningApp(
                    taskId = task.taskId,
                    packageName = task.packageName,
                    displayId = task.displayId,
                    rssKb = process?.rssKb,
                    game = games[task.packageName],
                    runStartedAtMillis = process?.let { now - it.elapsedMs },
                    hardcore = false,
                    slotIndex = null
                )
            }
    }

    private fun isLaunchable(packageName: String): Boolean {
        val packageManager = application.packageManager
        return packageManager.getLaunchIntentForPackage(packageName) != null ||
            packageManager.getLeanbackLaunchIntentForPackage(packageName) != null
    }

    fun builtInApps(): List<RunningApp> = listOfNotNull(mainProcessGame()) + liveSlots()
        .sortedBy { it.slotIndex }
        .map { slot ->
            builtInRunningApp(
                taskId = slot.taskId,
                gameId = slot.gameId,
                variantFileId = slot.variantFileId,
                pid = slot.pid,
                rssKb = residentKb(slot.pid),
                startedAtMillis = slot.startedAtMillis,
                hardcore = slot.hardcore,
                slotIndex = slot.slotIndex,
                platformSlug = slot.platformSlug
            )
        }

    /**
     * Whether a built-in [platformSlug] game can start without closing another: a slot is free and
     * the games already open leave room for its weight.
     */
    fun hasRoomFor(platformSlug: String?): Boolean = slotRegistry.freeSlotFor(platformSlug) != null

    private fun liveSlots(): List<com.nendo.argosy.libretro.slot.LiveSlot> {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskIds = activityManager.appTasks.mapNotNull { task -> runCatching { task.taskInfo.idCompat() }.getOrNull() }.toSet()
        val (live, gone) = slotRegistry.slots.value.values.partition { it.taskId in taskIds }
        gone.forEach { slot ->
            Logger.debug(TAG, "slot ${slot.slotIndex} task ${slot.taskId} is gone, dropping it")
            slotRegistry.remove(slot.slotIndex)
        }
        return live
    }

    private fun mainProcessGame(): RunningApp? {
        val live = LibretroActivity.liveGame ?: return null
        val task = builtInTask() ?: return null
        val taskId = runCatching { task.taskInfo.idCompat() }.getOrNull() ?: return null
        return builtInRunningApp(
            taskId = taskId,
            gameId = live.gameId,
            variantFileId = live.variantFileId,
            pid = Process.myPid(),
            rssKb = null,
            startedAtMillis = live.startedAtMillis,
            hardcore = live.hardcore,
            slotIndex = 0
        )
    }

    private fun builtInRunningApp(
        taskId: Int,
        gameId: Long,
        variantFileId: Long?,
        pid: Int,
        rssKb: Long?,
        startedAtMillis: Long,
        hardcore: Boolean,
        slotIndex: Int,
        platformSlug: String? = null
    ) = RunningApp(
        taskId = taskId,
        packageName = EmulatorRegistry.BUILTIN_PACKAGE,
        displayId = Display.INVALID_DISPLAY,
        rssKb = rssKb,
        game = BackgroundGame(
            emulatorPackage = EmulatorRegistry.BUILTIN_PACKAGE,
            gameId = gameId,
            variantFileId = variantFileId,
            pid = pid,
            endedAtMillis = 0L
        ),
        runStartedAtMillis = startedAtMillis,
        hardcore = hardcore,
        slotIndex = slotIndex,
        platformSlug = platformSlug
    )

    private fun residentKb(pid: Int): Long? = runCatching {
        File("/proc/$pid/status").useLines { lines ->
            lines.firstOrNull { it.startsWith(VM_RSS) }
                ?.removePrefix(VM_RSS)?.trim()?.substringBefore(' ')?.toLongOrNull()
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun ActivityManager.RecentTaskInfo.idCompat(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) taskId else persistentId

    private fun builtInTask(): ActivityManager.AppTask? {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return builtInGameAppTask(activityManager)
    }

    /**
     * The running app holding [gameId] (and [variantFileId]) in [emulatorPackage], or null when
     * that emulator is not running it.
     */
    suspend fun liveGame(gameId: Long, variantFileId: Long?, emulatorPackage: String): RunningApp? {
        val candidates = if (emulatorPackage == EmulatorRegistry.BUILTIN_PACKAGE) {
            builtInApps()
        } else {
            listOfNotNull(liveApp(emulatorPackage))
        }
        return candidates.firstOrNull { it.game?.gameId == gameId && it.game.variantFileId == variantFileId }
    }

    /**
     * The app [packageName] runs, for an external emulator; built-in games are listed by
     * [builtInApps], one per slot.
     */
    suspend fun liveApp(packageName: String): RunningApp? =
        if (packageName == EmulatorRegistry.BUILTIN_PACKAGE || !isSupported) null
        else externalApps().firstOrNull { it.packageName == packageName }

    /**
     * Brings [taskId] to the front so the app resumes where it was, with no transition: it replaces
     * whatever Argosy last drew. Another app's task falls back to the system's transition when the
     * root shell cannot switch transitions off. False when the system refused.
     */
    suspend fun bringToFront(taskId: Int): Boolean = withContext(Dispatchers.IO) {
        val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val own = ownTask(activityManager, taskId)
        if (own != null && startWithoutTransition(own, taskId)) return@withContext true
        if (own == null && RootShell.isAvailable) return@withContext moveWithTransitionsOff(activityManager, taskId)
        moveTaskToFront(activityManager, taskId)
    }

    private fun moveTaskToFront(activityManager: ActivityManager, taskId: Int): Boolean = try {
        activityManager.moveTaskToFront(taskId, 0)
        true
    } catch (e: SecurityException) {
        Logger.warn(TAG, "moveTaskToFront refused for task $taskId", e)
        false
    }

    private fun moveWithTransitionsOff(activityManager: ActivityManager, taskId: Int): Boolean {
        val result = RootShell.run(
            application,
            "settings get global $TRANSITION_SCALE && settings put global $TRANSITION_SCALE 0"
        )
        if (result?.succeeded != true) return moveTaskToFront(activityManager, taskId)
        val previous = result.output.firstOrNull()?.trim()?.toFloatOrNull() ?: DEFAULT_TRANSITION_SCALE
        if (previous == 0f) return moveTaskToFront(activityManager, taskId)
        if (!transitionScaleRestore.exists()) transitionScaleRestore.writeText(previous.toString())
        val moved = moveTaskToFront(activityManager, taskId)
        scope.launch {
            delay(TRANSITION_RESTORE_DELAY_MS)
            restoreTransitionScale()
        }
        return moved
    }

    private val transitionScaleRestore: File get() = File(application.filesDir, TRANSITION_RESTORE_FILE)

    private fun restoreTransitionScale() {
        val previous = transitionScaleRestore.takeIf { it.exists() }?.readText()?.trim()?.toFloatOrNull() ?: return
        if (RootShell.run(application, "settings put global $TRANSITION_SCALE $previous")?.succeeded == true) {
            transitionScaleRestore.delete()
        }
    }

    private fun ownTask(activityManager: ActivityManager, taskId: Int): ActivityManager.AppTask? =
        activityManager.appTasks.firstOrNull { task -> runCatching { task.taskInfo.taskId }.getOrNull() == taskId }

    private fun startWithoutTransition(task: ActivityManager.AppTask, taskId: Int): Boolean = try {
        val root = Intent(task.taskInfo.baseIntent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        application.startActivity(root)
        true
    } catch (e: RuntimeException) {
        Logger.warn(TAG, "task $taskId would not restart in place", e)
        false
    }

    /**
     * Closes [app]. A game slot is asked to save and quit on its own; the built-in game in Argosy's
     * process has its task removed; an external app is force-stopped.
     */
    suspend fun close(app: RunningApp) {
        when (app.slotIndex) {
            null -> {
                store.remove(app.packageName)
                gameLauncher.forceStopEmulator(app.packageName)
            }
            0 -> withContext(Dispatchers.Main) { builtInTask()?.let { runCatching { it.finishAndRemoveTask() } } }
            else -> if (!slotRegistry.requestClose(app.slotIndex)) {
                app.game?.pid?.let { Process.killProcess(it) }
                slotRegistry.remove(app.slotIndex)
            }
        }
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
        val result = RootShell.run(application, "am stack list\necho $PS_MARKER\nps -A -o PID,RSS,ETIME,NAME")
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
        const val TRANSITION_SCALE = "transition_animation_scale"
        const val TRANSITION_RESTORE_FILE = "transition_scale_restore"
        const val TRANSITION_RESTORE_DELAY_MS = 1_000L
        const val DEFAULT_TRANSITION_SCALE = 1f
        const val VM_RSS = "VmRSS:"
        const val PS_MARKER = "--argosy-ps--"
    }
}
