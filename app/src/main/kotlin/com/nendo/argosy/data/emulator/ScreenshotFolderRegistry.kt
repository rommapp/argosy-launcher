package com.nendo.argosy.data.emulator

import android.os.Environment
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenshotFolderRegistry @Inject constructor() {

    fun folders(): List<String> = RELATIVE_FOLDERS.map(::absolute)

    fun builtinFolder(): String = absolute(BUILTIN_RELATIVE_FOLDER)

    private fun absolute(relative: String): String =
        "${Environment.getExternalStorageDirectory().absolutePath}/$relative"

    companion object {
        const val BUILTIN_RELATIVE_FOLDER = "Pictures/Argosy"

        val RELATIVE_FOLDERS: List<String> = listOf(
            "DCIM/Screenshots",
            "Pictures/Screenshots",
            BUILTIN_RELATIVE_FOLDER
        )
    }
}
