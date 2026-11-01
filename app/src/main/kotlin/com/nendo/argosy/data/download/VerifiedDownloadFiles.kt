package com.nendo.argosy.data.download

import com.nendo.argosy.data.download.transfer.TransferCheckpoint
import java.io.File

internal object VerifiedDownloadFiles {
    fun matches(file: File, checkpoint: TransferCheckpoint): Boolean {
        val verified = checkpoint.validatedBytes ?: return false
        return verified > 0 && (checkpoint.totalBytes == null || checkpoint.totalBytes == verified) &&
            file.isFile && file.length() == verified
    }

    fun promote(part: File, target: File, checkpoint: TransferCheckpoint) {
        check(matches(part, checkpoint)) { "Download has no completion marker" }
        check(part.parentFile == target.parentFile)
        check(part.renameTo(target)) { "Verified download promotion failed" }
    }
}
