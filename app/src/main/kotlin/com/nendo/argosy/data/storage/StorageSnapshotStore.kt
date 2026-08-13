package com.nendo.argosy.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
internal data class PersistedCategoryUsage(
    val bytes: Long,
    val fileCount: Int,
    val perVolume: Map<String, Long>
)

@JsonClass(generateAdapter = true)
internal data class PersistedPlatformUsage(
    val platformId: Long,
    val name: String,
    val sortOrder: Int,
    val downloadedCount: Int,
    val bytes: Long,
    val perVolume: Map<String, Long>
)

@JsonClass(generateAdapter = true)
internal data class PersistedMediaLibraryUsage(
    val libraryId: String,
    val name: String,
    val displayOrder: Int,
    val downloadedCount: Int,
    val bytes: Long,
    val perVolume: Map<String, Long>,
    val offlineCount: Int,
    val offlineBytes: Long,
    val missingCount: Int
)

@JsonClass(generateAdapter = true)
internal data class PersistedMediaLocationUsage(
    val path: String,
    val volumeKey: String?,
    val bytes: Long,
    val fileCount: Int,
    val isCurrentTarget: Boolean,
    val isAvailable: Boolean
)

@JsonClass(generateAdapter = true)
internal data class PersistedVolumeFingerprint(
    val totalBytes: Long,
    val usedBytes: Long
)

@JsonClass(generateAdapter = true)
internal data class PersistedStorageSnapshot(
    val computedAt: Long,
    val categories: Map<String, PersistedCategoryUsage>,
    val gamesPerPlatform: List<PersistedPlatformUsage>,
    val volumeFingerprints: Map<String, PersistedVolumeFingerprint> = emptyMap(),
    val mediaPerLibrary: List<PersistedMediaLibraryUsage> = emptyList(),
    val mediaLocations: List<PersistedMediaLocationUsage> = emptyList()
)

/** Persists the last completed [StorageSnapshot] as Moshi JSON under a single DataStore key. */
@Singleton
class StorageSnapshotStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    moshi: Moshi
) {
    private val adapter = moshi.adapter(PersistedStorageSnapshot::class.java)

    suspend fun load(): StorageSnapshot? {
        val json = dataStore.data.first()[SNAPSHOT_KEY] ?: return null
        val persisted = try {
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return null
        return persisted.toSnapshot()
    }

    suspend fun save(snapshot: StorageSnapshot) {
        val json = adapter.toJson(snapshot.toPersisted())
        dataStore.edit { it[SNAPSHOT_KEY] = json }
    }

    private fun PersistedStorageSnapshot.toSnapshot() = StorageSnapshot(
        computedAt = computedAt,
        categories = categories.mapNotNull { (name, usage) ->
            StorageCategory.entries.firstOrNull { it.name == name }
                ?.let { it to CategoryUsage(usage.bytes, usage.fileCount, usage.perVolume) }
        }.toMap(),
        gamesPerPlatform = gamesPerPlatform.map {
            PlatformUsage(it.platformId, it.name, it.sortOrder, it.downloadedCount, it.bytes, it.perVolume)
        },
        volumeFingerprints = volumeFingerprints.mapValues { (_, fp) ->
            VolumeFingerprint(fp.totalBytes, fp.usedBytes)
        },
        mediaPerLibrary = mediaPerLibrary.map {
            MediaLibraryUsage(
                libraryId = it.libraryId,
                name = it.name,
                displayOrder = it.displayOrder,
                downloadedCount = it.downloadedCount,
                bytes = it.bytes,
                perVolume = it.perVolume,
                offlineCount = it.offlineCount,
                offlineBytes = it.offlineBytes,
                missingCount = it.missingCount
            )
        },
        mediaLocations = mediaLocations.map {
            MediaLocationUsage(
                path = it.path,
                volumeKey = it.volumeKey,
                bytes = it.bytes,
                fileCount = it.fileCount,
                isCurrentTarget = it.isCurrentTarget,
                isAvailable = it.isAvailable
            )
        }
    )

    private fun StorageSnapshot.toPersisted() = PersistedStorageSnapshot(
        computedAt = computedAt,
        categories = categories.entries.associate { (category, usage) ->
            category.name to PersistedCategoryUsage(usage.bytes, usage.fileCount, usage.perVolume)
        },
        gamesPerPlatform = gamesPerPlatform.map {
            PersistedPlatformUsage(it.platformId, it.name, it.sortOrder, it.downloadedCount, it.bytes, it.perVolume)
        },
        volumeFingerprints = volumeFingerprints.mapValues { (_, fp) ->
            PersistedVolumeFingerprint(fp.totalBytes, fp.usedBytes)
        },
        mediaPerLibrary = mediaPerLibrary.map {
            PersistedMediaLibraryUsage(
                libraryId = it.libraryId,
                name = it.name,
                displayOrder = it.displayOrder,
                downloadedCount = it.downloadedCount,
                bytes = it.bytes,
                perVolume = it.perVolume,
                offlineCount = it.offlineCount,
                offlineBytes = it.offlineBytes,
                missingCount = it.missingCount
            )
        },
        mediaLocations = mediaLocations.map {
            PersistedMediaLocationUsage(
                path = it.path,
                volumeKey = it.volumeKey,
                bytes = it.bytes,
                fileCount = it.fileCount,
                isCurrentTarget = it.isCurrentTarget,
                isAvailable = it.isAvailable
            )
        }
    )

    private companion object {
        val SNAPSHOT_KEY = stringPreferencesKey("storage_attribution_snapshot")
    }
}
