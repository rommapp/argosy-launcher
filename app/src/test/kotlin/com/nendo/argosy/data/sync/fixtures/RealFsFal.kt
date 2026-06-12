package com.nendo.argosy.data.sync.fixtures

import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.storage.FileInfo
import io.mockk.every
import io.mockk.mockk
import java.io.File

/** A [FileAccessLayer] mock that delegates every op to real File I/O on a temp dir. */
fun realFsFal(): FileAccessLayer = mockk<FileAccessLayer>(relaxed = true).also { fal ->
    every { fal.exists(any()) } answers { File(firstArg<String>()).exists() }
    every { fal.isDirectory(any()) } answers { File(firstArg<String>()).isDirectory }
    every { fal.isFile(any()) } answers { File(firstArg<String>()).isFile }
    every { fal.length(any()) } answers { File(firstArg<String>()).length() }
    every { fal.lastModified(any()) } answers { File(firstArg<String>()).lastModified() }
    every { fal.canRead(any()) } answers { File(firstArg<String>()).canRead() }
    every { fal.canWrite(any()) } answers { File(firstArg<String>()).canWrite() }
    every { fal.mkdirs(any()) } answers { File(firstArg<String>()).let { it.mkdirs() || it.exists() } }
    every { fal.delete(any()) } answers { File(firstArg<String>()).delete() }
    every { fal.deleteRecursively(any()) } answers { File(firstArg<String>()).deleteRecursively() }
    every { fal.getTransformedFile(any()) } answers { File(firstArg<String>()) }
    every { fal.isRestrictedPath(any()) } returns false
    every { fal.getInputStream(any()) } answers {
        val f = File(firstArg<String>())
        if (f.exists() && f.canRead()) f.inputStream() else null
    }
    every { fal.getOutputStream(any()) } answers {
        val f = File(firstArg<String>())
        f.parentFile?.mkdirs()
        f.outputStream()
    }
    every { fal.readBytes(any()) } answers {
        val f = File(firstArg<String>())
        if (f.exists() && f.canRead()) f.readBytes() else null
    }
    every { fal.writeBytes(any(), any()) } answers {
        val f = File(firstArg<String>())
        f.parentFile?.mkdirs()
        f.writeBytes(secondArg())
        true
    }
    every { fal.copyFile(any(), any()) } answers {
        val src = File(firstArg<String>())
        val dst = File(secondArg<String>())
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
        true
    }
    every { fal.copyDirectory(any(), any()) } answers {
        File(firstArg<String>()).copyRecursively(File(secondArg<String>()), overwrite = true)
    }
    every { fal.listFiles(any()) } answers { directListing(firstArg()) }
    every { fal.listFilesUnion(any()) } answers { directListing(firstArg()) ?: emptyList() }
    every { fal.externalStorageRoots() } returns emptyList()
    every { fal.normalizeForDisplay(any()) } answers { firstArg() }
}

private fun directListing(path: String): List<FileInfo>? {
    val dir = File(path)
    if (!dir.exists() || !dir.isDirectory) return null
    return dir.listFiles()?.map {
        FileInfo(
            path = it.absolutePath,
            name = it.name,
            isDirectory = it.isDirectory,
            isFile = it.isFile,
            size = it.length(),
            lastModified = it.lastModified(),
        )
    }
}
