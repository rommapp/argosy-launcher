package com.nendo.argosy.util

import android.os.IBinder
import android.os.Parcel
import java.nio.charset.Charset

object PServerExecutor {

    private val binder: IBinder? by lazy {
        runCatching {
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getDeclaredMethod("getService", String::class.java)
            getService.invoke(serviceManager, "PServerBinder") as IBinder
        }.getOrNull()
    }

    val isAvailable: Boolean
        get() = binder != null

    fun execute(cmd: String): Result<String?> {
        val b = binder ?: return Result.failure(IllegalStateException("PServerBinder not available"))

        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeStringArray(arrayOf(cmd, "1"))
            b.transact(0, data, reply, 0)
            val result = reply.createByteArray()?.toString(Charset.defaultCharset())?.trim()
            Result.success(if (result == "null") null else result)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun getSystemSetting(key: String, default: Int): Int {
        return execute("settings get system $key")
            .mapCatching { it?.toIntOrNull() ?: default }
            .getOrDefault(default)
    }

    fun setSystemSetting(key: String, value: Int): Boolean {
        return execute("settings put system $key $value").isSuccess
    }

    fun getSystemSettingFloat(key: String, default: Float): Float {
        return execute("settings get system $key")
            .mapCatching { it?.toFloatOrNull() ?: default }
            .getOrDefault(default)
    }

    fun setSystemSettingFloat(key: String, value: Float): Boolean {
        return execute("settings put system $key $value").isSuccess
    }
}
