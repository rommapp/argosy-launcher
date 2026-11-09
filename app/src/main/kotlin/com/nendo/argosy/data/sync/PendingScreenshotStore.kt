package com.nendo.argosy.data.sync

import android.content.Context
import java.io.File

/**
 * Argosy's private copies of screenshots waiting to upload: one folder per source file under
 * filesDir/pending_screenshots, holding the copy under the source's own file name.
 */
object PendingScreenshotStore {
    private const val DIR_NAME = "pending_screenshots"

    fun root(context: Context): File = File(context.filesDir, DIR_NAME)

    fun slotFor(context: Context, sourcePath: String, sourceModifiedAt: Long): File =
        File(root(context), "${sourceModifiedAt}_${sourcePath.hashCode().toUInt().toString(16)}")

    fun discard(context: Context, copy: File) {
        copy.delete()
        val parent = copy.parentFile ?: return
        if (parent.parentFile?.absolutePath == root(context).absolutePath) parent.delete()
    }
}
