package com.nendo.argosy.data.storage

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

/**
 * FileProvider that also answers DocumentsContract-style projections
 * (mime_type, _size, last_modified, document_id) in addition to OpenableColumns.
 *
 * Some emulators (e.g. NetherSX2's FileHelper.statFile) query content URIs with a
 * DocumentsContract projection and expect rows in that shape. The stock androidx
 * FileProvider silently drops unknown columns, producing a cursor with fewer
 * columns than the caller expects and crashing on index access.
 */
class ArgosyFileProvider : FileProvider() {

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        if (projection == null || projection.all { it in OPENABLE_COLUMNS }) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder)
        }

        val file = resolveFile(uri) ?: return super.query(uri, projection, selection, selectionArgs, sortOrder)
        val row = projection.map { column -> valueFor(column, file, uri) }.toTypedArray()
        return MatrixCursor(projection, 1).apply { addRow(row) }
    }

    private fun resolveFile(uri: Uri): File? {
        val rawPath = uri.encodedPath ?: return null
        val decoded = Uri.decode(rawPath) ?: return null
        val segments = decoded.trimStart('/').split('/', limit = 2)
        if (segments.size < 2) return null
        val absolute = "/" + segments[1]
        return File(absolute).takeIf { it.exists() }
    }

    private fun valueFor(column: String, file: File, uri: Uri): Any? = when (column) {
        OpenableColumns.DISPLAY_NAME -> file.name
        OpenableColumns.SIZE, COLUMN_SIZE -> file.length()
        COLUMN_MIME_TYPE -> {
            val ext = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }
        COLUMN_LAST_MODIFIED -> file.lastModified()
        COLUMN_DOCUMENT_ID -> uri.toString()
        COLUMN_FLAGS -> 0
        else -> null
    }

    companion object {
        private const val COLUMN_MIME_TYPE = "mime_type"
        private const val COLUMN_SIZE = "_size"
        private const val COLUMN_LAST_MODIFIED = "last_modified"
        private const val COLUMN_DOCUMENT_ID = "document_id"
        private const val COLUMN_FLAGS = "flags"

        private val OPENABLE_COLUMNS = setOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}
