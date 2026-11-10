package com.nendo.argosy.util

import android.os.Binder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.File
import kotlin.system.exitProcess

/**
 * The service Shizuku runs in its own process, as shell or root, on Argosy's behalf. It runs
 * scripts and opens files there, so Argosy reads and writes emulator folders through descriptors
 * the privileged process opened.
 */
class ShizukuShellService : Binder() {

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = when (code) {
        RUN -> {
            val result = ShellProcess.run(listOf(SHELL), data.readString().orEmpty(), SCRIPT_TIMEOUT_SECONDS)
            reply?.writeInt(if (result == null) 0 else 1)
            if (result != null) {
                reply?.writeInt(result.exitCode ?: -1)
                reply?.writeStringList(result.output)
            }
            true
        }
        OPEN -> {
            val path = data.readString().orEmpty()
            val mode = data.readInt()
            val descriptor = runCatching { ParcelFileDescriptor.open(File(path), mode) }.getOrNull()
            reply?.writeInt(if (descriptor == null) 0 else 1)
            if (reply != null) descriptor?.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE)
            true
        }
        DESTROY -> exitProcess(0)
        else -> super.onTransact(code, data, reply, flags)
    }

    companion object {
        const val RUN = FIRST_CALL_TRANSACTION
        const val OPEN = FIRST_CALL_TRANSACTION + 1
        const val DESTROY = 16777115
        private const val SHELL = "sh"
        private const val SCRIPT_TIMEOUT_SECONDS = 600L
    }
}
