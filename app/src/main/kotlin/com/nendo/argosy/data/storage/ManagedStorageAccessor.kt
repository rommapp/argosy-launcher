package com.nendo.argosy.data.storage

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides access to /Android/data/ and /Android/obb/ directories using
 * DocumentsContract API with the manage=true query parameter.
 *
 * On Android 11+, direct File I/O to these directories returns null even with
 * MANAGE_EXTERNAL_STORAGE permission. The DocumentsContract API with manage=true
 * is the legitimate way to access these restricted scopes.
 */
@Singleton
class ManagedStorageAccessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: dagger.Lazy<com.nendo.argosy.data.preferences.UserPreferencesRepository>
) {
    private val contentResolver: ContentResolver = context.contentResolver

    @Volatile
    private var cachedTreeUri: Uri? = null

    @Volatile
    private var initialized = false

    suspend fun ensureInitialized() {
        if (!initialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prefs = preferencesRepository.get().preferences.first()
            cachedTreeUri = prefs.androidDataSafUri?.let { Uri.parse(it) }
            initialized = true
        }
    }

    fun initializeBlocking() {
        if (!initialized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            kotlinx.coroutines.runBlocking {
                val prefs = preferencesRepository.get().preferences.first()
                cachedTreeUri = prefs.androidDataSafUri?.let { Uri.parse(it) }
                initialized = true
            }
        }
    }

    companion object {
        private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        private const val PRIMARY_VOLUME_ID = "primary"

        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_FLAGS
        )
    }

    fun setTreeUri(uriString: String?) {
        cachedTreeUri = uriString?.let { Uri.parse(it) }
    }

    fun hasValidSafGrant(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        initializeBlocking()
        val treeUri = cachedTreeUri ?: return false
        return contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }
    }

    data class DocumentFile(
        val documentId: String,
        val displayName: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long,
        val flags: Int,
        val isDirectory: Boolean
    ) {
        val uri: Uri
            get() = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
    }

    /**
     * Lists files in an Android/data or Android/obb directory.
     *
     * @param packageName The package name (e.g., "com.retroarch")
     * @param subPath Optional sub-path within the package's data directory
     * @param volumeId Volume ID (default: "primary" for internal storage)
     * @return List of files, or null if access denied or directory doesn't exist
     */
    fun listAndroidDataFiles(
        packageName: String,
        subPath: String? = null,
        volumeId: String = PRIMARY_VOLUME_ID
    ): List<DocumentFile>? {
        val relativePath = buildString {
            append("Android/data/$packageName")
            if (!subPath.isNullOrEmpty()) {
                append("/${subPath.trimStart('/')}")
            }
        }
        return listFiles(volumeId, relativePath)
    }

    /**
     * Lists files at a specific path using DocumentsContract.
     *
     * @param volumeId Volume ID (e.g., "primary", or SD card UUID)
     * @param relativePath Path relative to volume root (e.g., "Android/data/com.retroarch/files")
     * @return List of files, or null if access denied or directory doesn't exist
     */
    fun listFiles(volumeId: String, relativePath: String): List<DocumentFile>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return listFilesLegacy(volumeId, relativePath)
        }

        initializeBlocking()

        val treeUri = cachedTreeUri
        if (treeUri != null) {
            val result = listFilesWithTreeUri(treeUri, volumeId, relativePath)
            if (result != null) return result
        }

        return listFilesWithManagedParameter(volumeId, relativePath)
    }

    private fun listFilesWithTreeUri(treeUri: Uri, volumeId: String, relativePath: String): List<DocumentFile>? {
        val targetDocId = "$volumeId:$relativePath"
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, targetDocId)
        val queryUri = applyManagedParameter(childrenUri, relativePath)

        return try {
            android.util.Log.d("ManagedStorageAccessor", "listFiles with tree URI: $queryUri")
            contentResolver.query(queryUri, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
                if (cursor.count > 0) {
                    android.util.Log.d("ManagedStorageAccessor", "Tree URI succeeded with ${cursor.count} files")
                    parseDocumentCursor(cursor)
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.d("ManagedStorageAccessor", "Tree URI approach failed: ${e.message}")
            null
        }
    }

    private fun listFilesWithManagedParameter(volumeId: String, relativePath: String): List<DocumentFile>? {
        val approaches = listOf(
            {
                val treeDocId = "$volumeId:$relativePath"
                val treeUri = Uri.Builder()
                    .scheme("content")
                    .authority(EXTERNAL_STORAGE_AUTHORITY)
                    .appendPath("tree")
                    .appendPath(treeDocId)
                    .build()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
                applyManagedParameter(childrenUri, relativePath)
            },
            {
                val documentId = "$volumeId:$relativePath"
                val childrenUri = DocumentsContract.buildChildDocumentsUri(
                    EXTERNAL_STORAGE_AUTHORITY,
                    documentId
                )
                applyManagedParameter(childrenUri, relativePath)
            }
        )

        for ((index, buildUri) in approaches.withIndex()) {
            try {
                val queryUri = buildUri()
                android.util.Log.d("ManagedStorageAccessor", "listFiles fallback ${index + 1}: $queryUri")
                val result = contentResolver.query(queryUri, DOCUMENT_COLUMNS, null, null, null)?.use { cursor ->
                    if (cursor.count > 0) parseDocumentCursor(cursor) else null
                }
                if (result != null) return result
            } catch (e: Exception) {
                android.util.Log.d("ManagedStorageAccessor", "Fallback ${index + 1} failed: ${e.message}")
            }
        }
        return null
    }

    /**
     * Checks if a file or directory exists in Android/data.
     */
    fun exists(packageName: String, subPath: String? = null, volumeId: String = PRIMARY_VOLUME_ID): Boolean {
        val relativePath = buildString {
            append("Android/data/$packageName")
            if (!subPath.isNullOrEmpty()) {
                append("/${subPath.trimStart('/')}")
            }
        }
        return existsAtPath(volumeId, relativePath)
    }

    /**
     * Checks if a file or directory exists at the given path.
     */
    fun existsAtPath(volumeId: String, relativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val file = File(getVolumeRoot(volumeId), relativePath)
            return file.exists()
        }

        initializeBlocking()

        val treeUri = cachedTreeUri
        if (treeUri != null) {
            try {
                val targetDocId = "$volumeId:$relativePath"
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
                val queryUri = applyManagedParameter(documentUri, relativePath)
                val exists = contentResolver.query(
                    queryUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                    null, null, null
                )?.use { it.moveToFirst() } ?: false
                if (exists) return true
            } catch (e: Exception) {
                android.util.Log.d("ManagedStorageAccessor", "existsAtPath tree URI failed: ${e.message}")
            }
        }

        val documentId = "$volumeId:$relativePath"
        val documentUri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        val queryUri = applyManagedParameter(documentUri, relativePath)

        return try {
            contentResolver.query(
                queryUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Opens an input stream for reading a file in Android/data.
     */
    fun openInputStream(
        packageName: String,
        filePath: String,
        volumeId: String = PRIMARY_VOLUME_ID
    ): InputStream? {
        val relativePath = "Android/data/$packageName/${filePath.trimStart('/')}"
        return openInputStreamAtPath(volumeId, relativePath)
    }

    /**
     * Opens an input stream for reading a file at the given path.
     */
    fun openInputStreamAtPath(volumeId: String, relativePath: String): InputStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val file = File(getVolumeRoot(volumeId), relativePath)
            return if (file.exists() && file.canRead()) file.inputStream() else null
        }

        initializeBlocking()

        val treeUri = cachedTreeUri
        if (treeUri != null) {
            try {
                val targetDocId = "$volumeId:$relativePath"
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
                val stream = contentResolver.openInputStream(documentUri)
                if (stream != null) {
                    android.util.Log.d("ManagedStorageAccessor", "openInputStream with tree URI succeeded")
                    return stream
                }
            } catch (e: Exception) {
                android.util.Log.d("ManagedStorageAccessor", "openInputStream tree URI failed: ${e.message}")
            }
        }

        val documentId = "$volumeId:$relativePath"
        val baseUri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        val documentUri = applyManagedParameter(baseUri, relativePath)

        return try {
            contentResolver.openInputStream(documentUri)
        } catch (e: Exception) {
            android.util.Log.e("ManagedStorageAccessor", "openInputStreamAtPath failed: ${e.message}")
            null
        }
    }

    /**
     * Opens an output stream for writing to a file in Android/data.
     * Creates the file if it doesn't exist.
     */
    fun openOutputStream(
        packageName: String,
        filePath: String,
        volumeId: String = PRIMARY_VOLUME_ID
    ): OutputStream? {
        val relativePath = "Android/data/$packageName/${filePath.trimStart('/')}"
        return openOutputStreamAtPath(volumeId, relativePath)
    }

    /**
     * Opens an output stream for writing to a file at the given path.
     */
    fun openOutputStreamAtPath(volumeId: String, relativePath: String): OutputStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val file = File(getVolumeRoot(volumeId), relativePath)
            file.parentFile?.mkdirs()
            return file.outputStream()
        }

        initializeBlocking()

        val treeUri = cachedTreeUri
        if (treeUri != null) {
            try {
                val targetDocId = "$volumeId:$relativePath"
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
                val stream = contentResolver.openOutputStream(documentUri, "wt")
                if (stream != null) {
                    android.util.Log.d("ManagedStorageAccessor", "openOutputStream with tree URI succeeded")
                    return stream
                }
            } catch (e: Exception) {
                android.util.Log.d("ManagedStorageAccessor", "openOutputStream tree URI failed: ${e.message}")
            }
        }

        val documentId = "$volumeId:$relativePath"
        val baseUri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        val documentUri = applyManagedParameter(baseUri, relativePath)

        return try {
            android.util.Log.d("ManagedStorageAccessor", "openOutputStream fallback: $documentUri")
            contentResolver.openOutputStream(documentUri, "wt")
        } catch (e: Exception) {
            android.util.Log.e("ManagedStorageAccessor", "openOutputStream failed: ${e.message}")
            null
        }
    }

    /**
     * Deletes a file in Android/data.
     */
    fun delete(
        packageName: String,
        filePath: String,
        volumeId: String = PRIMARY_VOLUME_ID
    ): Boolean {
        val relativePath = "Android/data/$packageName/${filePath.trimStart('/')}"
        return deleteAtPath(volumeId, relativePath)
    }

    /**
     * Deletes a file at the given path.
     */
    fun deleteAtPath(volumeId: String, relativePath: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val file = File(getVolumeRoot(volumeId), relativePath)
            return file.delete()
        }

        initializeBlocking()

        val treeUri = cachedTreeUri
        if (treeUri != null) {
            try {
                val targetDocId = "$volumeId:$relativePath"
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetDocId)
                val deleted = DocumentsContract.deleteDocument(contentResolver, documentUri)
                if (deleted) {
                    android.util.Log.d("ManagedStorageAccessor", "deleteAtPath with tree URI succeeded")
                    return true
                }
            } catch (e: Exception) {
                android.util.Log.d("ManagedStorageAccessor", "deleteAtPath tree URI failed: ${e.message}")
            }
        }

        val documentId = "$volumeId:$relativePath"
        val documentUri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)

        return try {
            DocumentsContract.deleteDocument(contentResolver, documentUri)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copies a file from one location to another within Android/data.
     */
    fun copyFile(
        sourcePackage: String,
        sourcePath: String,
        destPackage: String,
        destPath: String,
        volumeId: String = PRIMARY_VOLUME_ID
    ): Boolean {
        val input = openInputStream(sourcePackage, sourcePath, volumeId) ?: return false
        val output = openOutputStream(destPackage, destPath, volumeId)
        if (output == null) {
            input.close()
            return false
        }

        return try {
            input.use { ins ->
                output.use { outs ->
                    ins.copyTo(outs)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the absolute file path for Android/data access (for display purposes).
     * Note: This path may not be directly accessible via File I/O on Android 11+.
     */
    fun getAbsolutePath(
        packageName: String,
        subPath: String? = null,
        volumeId: String = PRIMARY_VOLUME_ID
    ): String {
        val volumeRoot = getVolumeRoot(volumeId)
        return buildString {
            append(volumeRoot)
            append("/Android/data/")
            append(packageName)
            if (!subPath.isNullOrEmpty()) {
                append("/")
                append(subPath.trimStart('/'))
            }
        }
    }

    /**
     * Checks if the manage=true approach is supported (Android 11+).
     */
    fun isManagedAccessSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    private fun applyManagedParameter(uri: Uri, relativePath: String): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return uri
        }

        val needsManaged = relativePath.startsWith("Android/data") ||
            relativePath.startsWith("Android/obb") ||
            relativePath == "Android"

        return if (needsManaged) {
            uri.buildUpon()
                .appendQueryParameter("manage", "true")
                .build()
        } else {
            uri
        }
    }

    private fun parseDocumentCursor(cursor: Cursor): List<DocumentFile> {
        val files = mutableListOf<DocumentFile>()

        val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val flagsIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)

        while (cursor.moveToNext()) {
            val mimeType = cursor.getString(mimeIndex) ?: ""
            files.add(
                DocumentFile(
                    documentId = cursor.getString(idIndex) ?: "",
                    displayName = cursor.getString(nameIndex) ?: "",
                    mimeType = mimeType,
                    lastModified = cursor.getLong(modifiedIndex),
                    size = cursor.getLong(sizeIndex),
                    flags = cursor.getInt(flagsIndex),
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
                )
            )
        }

        return files
    }

    private fun listFilesLegacy(volumeId: String, relativePath: String): List<DocumentFile>? {
        val dir = File(getVolumeRoot(volumeId), relativePath)
        if (!dir.exists() || !dir.isDirectory) return null

        return dir.listFiles()?.map { file ->
            DocumentFile(
                documentId = "$volumeId:$relativePath/${file.name}",
                displayName = file.name,
                mimeType = if (file.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream",
                lastModified = file.lastModified(),
                size = file.length(),
                flags = 0,
                isDirectory = file.isDirectory
            )
        }
    }

    private fun getVolumeRoot(volumeId: String): String {
        return if (volumeId == PRIMARY_VOLUME_ID) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volumeId"
        }
    }
}
