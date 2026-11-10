package com.nendo.argosy.util

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import com.nendo.argosy.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Commands and file access through Shizuku, which runs [ShizukuShellService] as root or as shell
 * depending on how the user started it. [requestGrant], [execute], [runScript] and [open] block
 * on Shizuku, so they run off the main thread; on the main thread they refuse.
 */
object ShizukuExecutor : RootCommandRunner, AppDataShell {

    private const val TAG = "ShizukuExecutor"
    private const val REQUEST_CODE = 0x5A1
    private const val PERMISSION_TIMEOUT_SECONDS = 120L
    private const val BIND_TIMEOUT_SECONDS = 15L
    private const val ROOT_UID = 0
    private const val PROCESS_SUFFIX = "shell"

    @Volatile
    private var service: IBinder? = null
    private val bindLock = Any()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            connected?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    @Volatile
    private var connected: CountDownLatch? = null

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShizukuShellService::class.java.name))
            .daemon(false)
            .processNameSuffix(PROCESS_SUFFIX)
            .version(BuildConfig.VERSION_CODE)
    }

    override val isInstalled: Boolean
        get() = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)

    override val isGranted: Boolean
        get() = isInstalled &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)

    val runsAsRoot: Boolean
        get() = isGranted && runCatching { Shizuku.getUid() == ROOT_UID }.getOrDefault(false)

    override fun requestGrant(): Boolean {
        if (!isInstalled) return false
        if (isGranted) return true
        if (Looper.myLooper() == Looper.getMainLooper()) return false
        val answered = CountDownLatch(1)
        var granted = false
        val listener = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code != REQUEST_CODE) return@OnRequestPermissionResultListener
            granted = result == PackageManager.PERMISSION_GRANTED
            answered.countDown()
        }
        Shizuku.addRequestPermissionResultListener(listener)
        try {
            Shizuku.requestPermission(REQUEST_CODE)
            answered.await(PERMISSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: RuntimeException) {
            Logger.warn(TAG, "permission request failed: ${e.message}")
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
        return granted
    }

    override fun execute(command: String): Result<String> = runCatching {
        val result = runScript(command) ?: error("Shizuku refused the command")
        check(result.succeeded) { "Shizuku command exited ${result.exitCode}: ${result.output.joinToString("\n")}" }
        result.output.joinToString("\n")
    }

    override fun runScript(script: String): RootShell.Result? =
        transact(ShizukuShellService.RUN, { writeString(script) }) { reply ->
            if (reply.readInt() == 0) return@transact null
            val exit = reply.readInt()
            RootShell.Result(exit, reply.createStringArrayList().orEmpty())
        }

    override fun open(path: String, mode: Int): ParcelFileDescriptor? =
        transact(ShizukuShellService.OPEN, { writeString(path); writeInt(mode) }) { reply ->
            if (reply.readInt() == 0) null else ParcelFileDescriptor.CREATOR.createFromParcel(reply)
        }

    private fun <T> transact(code: Int, write: Parcel.() -> Unit, read: (Parcel) -> T?): T? {
        repeat(2) {
            val binder = boundService() ?: return null
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.write()
                binder.transact(code, data, reply, 0)
                return read(reply)
            } catch (e: RemoteException) {
                Logger.warn(TAG, "shell service call failed, rebinding: ${e.message}")
                service = null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
        return null
    }

    private fun boundService(): IBinder? {
        service?.takeIf { it.pingBinder() }?.let { return it }
        if (!isGranted || Looper.myLooper() == Looper.getMainLooper()) return null
        synchronized(bindLock) {
            service?.takeIf { it.pingBinder() }?.let { return it }
            val latch = CountDownLatch(1)
            connected = latch
            val bound = runCatching { Shizuku.bindUserService(serviceArgs, connection) }
                .onFailure { Logger.warn(TAG, "binding the shell service failed: ${it.message}") }
                .isSuccess
            if (!bound) return null
            latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return service
        }
    }
}
