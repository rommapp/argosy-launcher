package com.nendo.argosy.data.sync.platform

import android.content.Context
import com.nendo.argosy.data.storage.FileAccessLayer
import com.nendo.argosy.data.sync.SaveArchiver
import java.io.File

internal open class CategorySplitFolderHandler(
    context: Context,
    fal: FileAccessLayer,
    saveArchiver: SaveArchiver,
    platformSlug: String,
    private val categoryDir: Regex,
    private val categoryParents: List<String> = emptyList()
) : FolderSaveHandler(context, fal, saveArchiver, platformSlug = platformSlug) {

    private val access = fal

    companion object {
        private const val ID_HEX_LENGTH = 8
        private val FULL_ID = Regex("[0-9a-fA-F]{16}")

        fun lowId(saveId: String): String =
            saveId.trimEnd('/').substringAfterLast('/').takeLast(ID_HEX_LENGTH).lowercase()

        fun categoryOf(saveId: String): String? =
            saveId.replace("/", "").takeIf { FULL_ID.matches(it) }?.take(ID_HEX_LENGTH)?.lowercase()
    }

    /**
     * The category directory [saveId] lives in. [basePath] is normally that directory or a sibling
     * category; a folder above it, such as a save path override set to the emulator's `Wii` or
     * `User` folder, is followed down through [categoryParents] to the category that exists there.
     */
    fun categoryBase(basePath: String, saveId: String): String {
        val base = basePath.trimEnd('/')
        val category = categoryOf(saveId) ?: return base
        val lastSegment = base.substringAfterLast('/')
        if (categoryDir.matches(lastSegment)) {
            return if (lastSegment.equals(category, ignoreCase = true)) base else "${base.substringBeforeLast('/')}/$category"
        }
        return categoryParents
            .map { parent -> if (parent.isEmpty()) "$base/$category" else "$base/$parent/$category" }
            .firstOrNull { access.isDirectory(it) }
            ?: base
    }

    override fun folderMatches(folderName: String, saveId: String): Boolean =
        folderName.equals(lowId(saveId), ignoreCase = true)

    override fun constructSavePath(baseDir: String, saveId: String): String? =
        "${categoryBase(baseDir, saveId)}/${lowId(saveId)}"

    override fun findSaveFolderBySaveId(basePath: String, saveId: String): String? =
        super.findSaveFolderBySaveId(categoryBase(basePath, saveId), saveId)

    override fun findAllSaveFoldersBySaveId(basePath: String, saveId: String): List<String> =
        super.findAllSaveFoldersBySaveId(categoryBase(basePath, saveId), saveId)

    override fun matchArchive(tempFile: File, saveId: String): ArchiveRootMatch? =
        if (saveArchiver.peekRootEntryNames(tempFile).any { it.equals(lowId(saveId), ignoreCase = true) }) {
            ArchiveRootMatch.EXACT
        } else {
            super.matchArchive(tempFile, saveId)
        }
}
