package com.nendo.argosy.data.emulator

import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TitleIdDetector @Inject constructor() {

    companion object {
        private const val TAG = "TitleIdDetector"
        private const val RECENT_THRESHOLD_MS = 5 * 60 * 1000L
    }

    data class DetectedTitleId(
        val titleId: String,
        val modifiedAt: Long,
        val savePath: String
    )

    fun detectRecentTitleId(
        emulatorId: String,
        platformId: String,
        sessionStartTime: Long
    ): DetectedTitleId? {
        val config = SavePathRegistry.getConfigIncludingUnsupported(emulatorId) ?: return null
        if (!config.usesFolderBasedSaves) return null

        for (basePath in config.defaultPaths) {
            val detected = scanForRecentTitleId(basePath, platformId, sessionStartTime)
            if (detected != null) {
                Log.d(TAG, "Detected title ID: ${detected.titleId} at ${detected.savePath}")
                return detected
            }
        }

        return null
    }

    private fun scanForRecentTitleId(
        basePath: String,
        platformId: String,
        sessionStartTime: Long
    ): DetectedTitleId? {
        val baseDir = File(basePath)
        if (!baseDir.exists()) return null

        return when (platformId) {
            "switch" -> scanSwitchSaves(baseDir, sessionStartTime)
            "vita", "psvita" -> scanVitaSaves(baseDir, sessionStartTime)
            "psp" -> scanPspSaves(baseDir, sessionStartTime)
            "3ds" -> scan3dsSaves(baseDir, sessionStartTime)
            "wiiu" -> scanWiiUSaves(baseDir, sessionStartTime)
            else -> null
        }
    }

    private fun scanSwitchSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        baseDir.listFiles()?.forEach { userFolder ->
            if (!userFolder.isDirectory) return@forEach

            userFolder.listFiles()?.forEach { profileFolder ->
                if (!profileFolder.isDirectory) return@forEach

                profileFolder.listFiles()?.forEach { titleFolder ->
                    if (!titleFolder.isDirectory) return@forEach
                    if (!isValidSwitchTitleId(titleFolder.name)) return@forEach

                    val modified = titleFolder.lastModified()
                    if (modified >= sessionStartTime - RECENT_THRESHOLD_MS) {
                        if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                            mostRecent = DetectedTitleId(
                                titleId = titleFolder.name.uppercase(),
                                modifiedAt = modified,
                                savePath = titleFolder.absolutePath
                            )
                        }
                    }
                }
            }
        }

        return mostRecent
    }

    private fun scanVitaSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        baseDir.listFiles()?.forEach { titleFolder ->
            if (!titleFolder.isDirectory) return@forEach
            if (!isValidVitaTitleId(titleFolder.name)) return@forEach

            val modified = titleFolder.lastModified()
            if (modified >= sessionStartTime - RECENT_THRESHOLD_MS) {
                if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                    mostRecent = DetectedTitleId(
                        titleId = titleFolder.name.uppercase(),
                        modifiedAt = modified,
                        savePath = titleFolder.absolutePath
                    )
                }
            }
        }

        return mostRecent
    }

    private fun scanPspSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        baseDir.listFiles()?.forEach { saveFolder ->
            if (!saveFolder.isDirectory) return@forEach

            val titleId = extractPspTitleIdFromFolder(saveFolder.name)
            if (titleId != null) {
                val modified = saveFolder.lastModified()
                if (modified >= sessionStartTime - RECENT_THRESHOLD_MS) {
                    if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                        mostRecent = DetectedTitleId(
                            titleId = titleId,
                            modifiedAt = modified,
                            savePath = saveFolder.absolutePath
                        )
                    }
                }
            }
        }

        return mostRecent
    }

    private fun scan3dsSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        // 3DS structure: Nintendo 3DS/<id0>/<id1>/title/00040000/<titleId>/data
        baseDir.listFiles()?.forEach { id0Folder ->
            if (!id0Folder.isDirectory) return@forEach

            id0Folder.listFiles()?.forEach { id1Folder ->
                if (!id1Folder.isDirectory) return@forEach

                val titleBaseDir = File(id1Folder, "title/00040000")
                if (!titleBaseDir.exists()) return@forEach

                titleBaseDir.listFiles()?.forEach { titleFolder ->
                    if (!titleFolder.isDirectory) return@forEach
                    if (!isValid3dsTitleId(titleFolder.name)) return@forEach

                    val dataFolder = File(titleFolder, "data")
                    val folderToCheck = if (dataFolder.exists()) dataFolder else titleFolder

                    val modified = folderToCheck.lastModified()
                    if (modified >= sessionStartTime - RECENT_THRESHOLD_MS) {
                        if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                            mostRecent = DetectedTitleId(
                                titleId = titleFolder.name.uppercase(),
                                modifiedAt = modified,
                                savePath = folderToCheck.absolutePath
                            )
                        }
                    }
                }
            }
        }

        return mostRecent
    }

    private fun scanWiiUSaves(baseDir: File, sessionStartTime: Long): DetectedTitleId? {
        var mostRecent: DetectedTitleId? = null

        // Wii U structure: 00050000/<titleId>/user/<userId>
        baseDir.listFiles()?.forEach { titleFolder ->
            if (!titleFolder.isDirectory) return@forEach
            if (!isValidWiiUTitleId(titleFolder.name)) return@forEach

            val userFolder = File(titleFolder, "user")
            val folderToCheck = if (userFolder.exists()) userFolder else titleFolder

            val modified = folderToCheck.lastModified()
            if (modified >= sessionStartTime - RECENT_THRESHOLD_MS) {
                if (mostRecent == null || modified > mostRecent!!.modifiedAt) {
                    mostRecent = DetectedTitleId(
                        titleId = titleFolder.name.uppercase(),
                        modifiedAt = modified,
                        savePath = titleFolder.absolutePath
                    )
                }
            }
        }

        return mostRecent
    }

    private fun isValidSwitchTitleId(name: String): Boolean {
        return name.length == 16 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun isValidVitaTitleId(name: String): Boolean {
        return name.length == 9 && name.matches(Regex("[A-Z]{4}\\d{5}"))
    }

    private fun isValid3dsTitleId(name: String): Boolean {
        return (name.length == 8 || name.length == 16) &&
            name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun isValidWiiUTitleId(name: String): Boolean {
        return name.length == 8 && name.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    private fun extractPspTitleIdFromFolder(folderName: String): String? {
        val pattern = Regex("^([A-Z]{4}\\d{5})")
        return pattern.find(folderName)?.groupValues?.get(1)
    }
}
