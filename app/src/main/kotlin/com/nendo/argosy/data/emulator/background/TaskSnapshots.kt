package com.nendo.argosy.data.emulator.background

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Process
import com.nendo.argosy.util.Logger
import com.nendo.argosy.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A task's last frame at [path]. [screen] is the system's picture of the whole window as it was
 * composited, the same shape as the screen, when one could be read; for an external app it is the
 * same file as [path].
 */
data class TaskSnapshot(val path: String, val takenAtMillis: Long, val screen: ScreenSnapshot?)

data class ScreenSnapshot(val path: String, val takenAtMillis: Long)

/**
 * The last frame of each task as it left the screen. A built-in game writes its own frame through
 * [saveGameFrame]; any other task's frame is copied through the root shell from the system's
 * snapshot store into Argosy's cache, so without root only built-in games have one. With root a
 * built-in game also gets the system's copy as its [TaskSnapshot.screenPath].
 */
@Singleton
class TaskSnapshots @Inject constructor(
    private val application: Application
) {
    private val dir: File get() = File(application.cacheDir, DIR).apply { mkdirs() }

    suspend fun fetch(taskIds: Collection<Int>): Map<Int, TaskSnapshot> = withContext(Dispatchers.IO) {
        val system = copySystemSnapshots(taskIds)
        taskIds.mapNotNull { taskId ->
            val screen = system[taskId]?.let { ScreenSnapshot(it.absolutePath, it.lastModified()) }
            val own = gameFrameFile(application, taskId).takeIf { it.canRead() }
            when {
                own != null && screen != null && screen.takenAtMillis > own.lastModified() ->
                    taskId to TaskSnapshot(screen.path, screen.takenAtMillis, screen)
                own != null -> taskId to TaskSnapshot(own.absolutePath, own.lastModified(), screen)
                screen != null -> taskId to TaskSnapshot(screen.path, screen.takenAtMillis, screen)
                else -> null
            }
        }.toMap()
    }

    private fun copySystemSnapshots(taskIds: Collection<Int>): Map<Int, File> {
        if (!RootShell.isAvailable || taskIds.isEmpty()) return emptyMap()
        val target = dir
        val result = RootShell.run(application, copyScript(target, taskIds))
        if (result?.succeeded != true) {
            Logger.debug(TAG, "snapshot copy failed: ${result?.output?.lastOrNull()}")
            return emptyMap()
        }
        val copied = result.output.mapNotNull { it.trim().toIntOrNull() }.toSet()
        target.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension.toIntOrNull() !in copied) file.delete()
        }
        return copied.mapNotNull { taskId ->
            File(target, "$taskId$EXTENSION").takeIf { it.canRead() }?.let { taskId to it }
        }.toMap()
    }

    private fun copyScript(target: File, taskIds: Collection<Int>): String {
        val source = "/data/system_ce/${Process.myUid() / PER_USER_RANGE}/snapshots"
        val dst = RootShell.quote(target.absolutePath)
        return "ctx=\$(stat -c %C $dst)\n" +
            "for id in ${taskIds.joinToString(" ")}; do\n" +
            "  s=$source/\${id}_reduced$EXTENSION\n" +
            "  [ -f \"\$s\" ] || s=$source/\$id$EXTENSION\n" +
            "  [ -f \"\$s\" ] || continue\n" +
            "  d=$dst/\$id$EXTENSION\n" +
            "  if [ ! -f \"\$d\" ] || [ \"\$s\" -nt \"\$d\" ]; then\n" +
            "    cat \"\$s\" > \"\$d\" && chmod 644 \"\$d\" && chcon \"\$ctx\" \"\$d\" && touch -r \"\$s\" \"\$d\" || continue\n" +
            "  fi\n" +
            "  echo \$id\n" +
            "done\n"
    }

    companion object {
        private const val TAG = "TaskSnapshots"
        private const val DIR = "task_snapshots"
        private const val GAME_FRAME_DIR = "game_frames"
        private const val EXTENSION = ".jpg"
        private const val GAME_FRAME_QUALITY = 90
        private const val PER_USER_RANGE = 100_000

        private fun gameFrameFile(context: Context, taskId: Int): File =
            File(File(context.cacheDir, GAME_FRAME_DIR), "$taskId$EXTENSION")

        /**
         * Stores [frame] as the snapshot of built-in game task [taskId], replacing the previous
         * one. Blocking file I/O.
         */
        fun saveGameFrame(context: Context, taskId: Int, frame: Bitmap) {
            val file = gameFrameFile(context, taskId)
            val partial = File(file.parentFile, "${file.name}.part")
            runCatching {
                file.parentFile?.mkdirs()
                partial.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, GAME_FRAME_QUALITY, it) }
                if (!partial.renameTo(file)) partial.delete()
            }.onFailure { Logger.debug(TAG, "game frame for task $taskId not written: ${it.message}") }
        }

        fun deleteGameFrame(context: Context, taskId: Int) {
            gameFrameFile(context, taskId).delete()
        }
    }
}
