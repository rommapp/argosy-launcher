package com.nendo.argosy.data.repository

import android.util.Log
import com.nendo.argosy.data.remote.romm.RomMConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ScreenshotUploader"

@Singleton
class ScreenshotUploader @Inject constructor(
    private val connectionManager: RomMConnectionManager
) {
    sealed class Result {
        data object Success : Result()
        data object NotSupported : Result()
        data object NotConnected : Result()
        data class Rejected(val reason: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun upload(file: File, rommId: Long): Result = withContext(Dispatchers.IO) {
        val mediaType = MIME_BY_EXTENSION[file.extension.lowercase()]
            ?: return@withContext Result.Rejected("Unsupported image type .${file.extension}")
        val api = connectionManager.getApi() ?: return@withContext Result.NotConnected
        if (!connectionManager.isConnected()) return@withContext Result.NotConnected
        if (!connectionManager.getCapabilities().supportsScreenshotUpload) {
            return@withContext Result.NotSupported
        }

        try {
            val body = file.asRequestBody(mediaType.toMediaType())
            val part = MultipartBody.Part.createFormData("screenshotFile", file.name, body)
            val response = api.uploadScreenshot(rommId, part)
            val code = response.code()
            when {
                response.isSuccessful -> {
                    Log.i(TAG, "Uploaded screenshot ${file.name} for rommId=$rommId (id=${response.body()?.id})")
                    Result.Success
                }
                code in PERMANENT_REJECTION_CODES -> {
                    Log.w(TAG, "Screenshot ${file.name} rejected for rommId=$rommId: HTTP $code")
                    Result.Rejected("HTTP $code")
                }
                else -> {
                    Log.w(TAG, "Screenshot upload failed for rommId=$rommId: HTTP $code")
                    Result.Failed("HTTP $code")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Screenshot upload exception for rommId=$rommId", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        val MIME_BY_EXTENSION: Map<String, String> = mapOf(
            "png" to "image/png",
            "jpg" to "image/jpeg",
            "jpeg" to "image/jpeg",
            "webp" to "image/webp",
            "gif" to "image/gif",
            "bmp" to "image/bmp"
        )

        private val PERMANENT_REJECTION_CODES = setOf(400, 403, 404, 413, 415)
    }
}
