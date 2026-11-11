package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nendo.argosy.hardware.pad.PadDriverLauncher
import com.nendo.argosy.util.PServerExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest

/**
 * Debug-only proof harness for root-tier save backup and restore. Trigger with
 * `adb shell am broadcast -n com.nendo.argosy.debug/com.nendo.argosy.debugtools.RootFileHarnessReceiver`
 * and `--es op stat|backup|restore|regroup|mode|delete|stacks` plus `--es path`, `--es name`, `--es group`.
 * `stacks` writes this process's native thread stacks to the harness result file.
 */
class RootFileHarnessReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val op = intent.getStringExtra("op") ?: return
        val path = intent.getStringExtra("path")
        val name = intent.getStringExtra("name")
        val group = intent.getStringExtra("group")
        val mode = intent.getStringExtra("mode")
        if (name != null && !isSafeName(name)) {
            Log.w(TAG, "rejected name $name")
            return
        }
        if (group != null && !GROUP_PATTERN.matches(group)) {
            Log.w(TAG, "rejected group $group")
            return
        }
        if (mode != null && !OCTAL_MODE_PATTERN.matches(mode) && !SYMBOLIC_MODE_PATTERN.matches(mode)) {
            Log.w(TAG, "rejected mode $mode")
            return
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val work = File(context.cacheDir, WORK_DIR).apply { mkdirs() }
                work.setExecutable(true, false)
                when (op) {
                    "stat" -> stat(work, path ?: return@launch)
                    "backup" -> backup(work, path ?: return@launch, name ?: return@launch)
                    "restore" -> restore(work, name ?: return@launch, path ?: return@launch)
                    "regroup" -> regroup(work, path ?: return@launch, group ?: return@launch)
                    "mode" -> runRoot(work, "p=${q(path ?: return@launch)}\nchmod ${q(mode ?: return@launch)} \"\$p\" && ls -lnd \"\$p\"")
                    "delete" -> runRoot(work, "p=${q(path ?: return@launch)}\nrm -f \"\$p\" && echo deleted; ls -ln \"\$(dirname \"\$p\")\"")
                    "move" -> runRoot(
                        work,
                        "p=${q(path ?: return@launch)}\nd=\"\$(dirname \"\$p\")/${name ?: return@launch}\"\n" +
                            "[ -e \"\$d\" ] && { echo exists; exit 1; }\nmv \"\$p\" \"\$d\" && ls -ln \"\$(dirname \"\$p\")\""
                    )
                    "dropaside" -> runRoot(
                        work,
                        "p=${q(path ?: return@launch)}\ncase \"\$p\" in *-aside) rm -rf \"\$p\" && echo dropped ;; " +
                            "*) echo refused; exit 1 ;; esac"
                    )
                    "stacks" -> runRoot(work, "debuggerd -b ${android.os.Process.myPid()}", echoOutput = false)
                    "paddriver" -> {
                        val started = PadDriverLauncher.restart(context)
                        Thread.sleep(PAD_DRIVER_SETTLE_MS)
                        Log.i(TAG, "paddriver started=$started running=${PadDriverLauncher.running(context)}")
                    }
                    "paddriverstop" -> Log.i(TAG, "paddriverstop stopped=${PadDriverLauncher.stop(context)}")
                    else -> Log.w(TAG, "unknown op $op")
                }
            } catch (e: Exception) {
                Log.e(TAG, "op=$op failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun stat(work: File, path: String) {
        runRoot(work, "p=${q(path)}\nls -lnd \"\$p\"; ls -ln \"\$p\" 2>/dev/null | head -40")
    }

    private fun backup(work: File, source: String, name: String) {
        val staged = File(work, name)
        staged.delete()
        runRoot(
            work,
            """
            src=${q(source)}
            dst=${q(staged.absolutePath)}
            ls -ln "${'$'}src"
            cp "${'$'}src" "${'$'}dst" || exit 1
            chmod 644 "${'$'}dst"
            """.trimIndent()
        )
        if (staged.canRead()) {
            Log.i(TAG, "backup ok: ${staged.length()} bytes md5=${md5(staged)} read by uid ${android.os.Process.myUid()}")
        } else {
            Log.w(TAG, "backup FAILED: staged copy not readable")
        }
    }

    private fun restore(work: File, name: String, target: String) {
        val staged = File(work, name)
        if (!staged.canRead()) {
            Log.w(TAG, "restore FAILED: nothing staged as $name")
            return
        }
        Log.i(TAG, "restore source md5=${md5(staged)}")
        runRoot(
            work,
            """
            t=${q(target)}
            src=${q(staged.absolutePath)}
            parent=${'$'}(dirname "${'$'}t")
            if [ -e "${'$'}t" ]; then ref="${'$'}t"; else ref="${'$'}parent"; fi
            owner=${'$'}(stat -c '%u' "${'$'}ref"); grp=${'$'}(stat -c '%g' "${'$'}ref")
            if [ -e "${'$'}t" ]; then mode=${'$'}(stat -c '%a' "${'$'}t"); else mode=660; fi
            echo "before:"; ls -ln "${'$'}t" 2>/dev/null
            cat "${'$'}src" > "${'$'}t" || exit 1
            chown "${'$'}owner:${'$'}grp" "${'$'}t" && chmod "${'$'}mode" "${'$'}t"
            echo "after:"; ls -ln "${'$'}t"
            md5sum "${'$'}t"
            """.trimIndent()
        )
    }

    private fun regroup(work: File, path: String, group: String) {
        runRoot(work, "p=${q(path)}\nchgrp -R ${q(group)} \"\$p\" && ls -lnd \"\$p\"")
    }

    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && '/' !in name && ".." !in name

    private fun q(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun runRoot(work: File, body: String, echoOutput: Boolean = true) {
        val script = File(work, SCRIPT_NAME).apply { writeText(body + "\n") }
        script.setReadable(true, false)
        val output = File(work, OUTPUT_NAME).apply { delete() }
        val command = "sh ${script.absolutePath} > ${output.absolutePath} 2>&1; rc=\$?; chmod 644 ${output.absolutePath}; echo \$rc"
        val wrapper = File(work, WRAPPER_NAME).apply { writeText(command + "\n") }
        wrapper.setReadable(true, false)
        val exit = PServerExecutor.execute("sh ${wrapper.absolutePath}").getOrNull()
        Log.i(TAG, "exit=$exit output=${output.absolutePath}")
        if (echoOutput && output.canRead()) output.readLines().forEach { Log.i(TAG, "  $it") }
    }

    private fun md5(file: File): String =
        MessageDigest.getInstance("MD5").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "RootHarness"
        const val WORK_DIR = "root-harness"
        const val SCRIPT_NAME = "harness-op.sh"
        const val WRAPPER_NAME = "harness-run.sh"
        const val OUTPUT_NAME = "harness-result.txt"
        const val PAD_DRIVER_SETTLE_MS = 1_000L
        val OCTAL_MODE_PATTERN = Regex("^[0-7]{3,4}$")
        val SYMBOLIC_MODE_PATTERN = Regex("^[ugoa]*[-+=][rwxX]+$")
        val GROUP_PATTERN = Regex("^[0-9]+$")
    }
}
