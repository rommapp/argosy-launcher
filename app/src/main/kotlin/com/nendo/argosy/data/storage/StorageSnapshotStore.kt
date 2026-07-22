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
internal data class PersistedVolumeFingerprint(
    val totalBytes: Long,
    val usedBytes: Long
)

@JsonClass(generateAdapter = true)
internal data class PersistedStorageSnapshot(
    val computedAt: Long,
    val categories: Map<String, PersistedCategoryUsage>,
    val gamesPerPlatform: List<PersistedPlatformUsage>,
    val volumeFingerprints: Map<String, PersistedVolumeFingerprint> = emptyMap()
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
        }
    )

    private companion object {
        val SNAPSHOT_KEY = stringPreferencesKey("storage_attribution_snapshot")
    }
}
