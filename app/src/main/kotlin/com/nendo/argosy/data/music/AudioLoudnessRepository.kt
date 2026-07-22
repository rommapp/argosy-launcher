package com.nendo.argosy.data.music

import com.nendo.argosy.data.local.dao.AudioLoudnessDao
import com.nendo.argosy.data.local.entity.AudioLoudnessEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Stores measured RMS loudness per local audio file, invalidating rows whose fileKey no longer matches the file on disk. */
@Singleton
class AudioLoudnessRepository @Inject constructor(
    private val audioLoudnessDao: AudioLoudnessDao
) {
    private val cache = ConcurrentHashMap<String, Double>()

    fun fileKeyFor(file: File): String = "${file.lastModified()}|${file.length()}"

    /** Stored meanDb after re-validating the fileKey against disk; deletes and misses on mismatch. */
    suspend fun validatedMeanDb(filePath: String): Double? = withContext(Dispatchers.IO) {
        val row = audioLoudnessDao.get(filePath) ?: return@withContext null
        val file = File(filePath)
        if (!file.isFile || fileKeyFor(file) != row.fileKey) {
            audioLoudnessDao.delete(filePath)
            cache.remove(filePath)
            return@withContext null
        }
        cache[filePath] = row.meanDb
        row.meanDb
    }

    /** Main-safe lookup for playback: in-memory cache first, then validated storage. */
    suspend fun playbackMeanDb(filePath: String): Double? =
        cache[filePath] ?: validatedMeanDb(filePath)

    suspend fun store(filePath: String, fileKey: String, meanDb: Double) =
        withContext(Dispatchers.IO) {
            audioLoudnessDao.upsert(
                AudioLoudnessEntity(
                    filePath = filePath,
                    fileKey = fileKey,
                    meanDb = meanDb,
                    measuredAt = System.currentTimeMillis()
                )
            )
            cache[filePath] = meanDb
        }
}
