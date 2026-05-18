package com.nendo.argosy.data.sync

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

enum class QueueSource { NEGOTIATE, DIRECT, CHANNEL_CACHE }

@JsonClass(generateAdapter = true)
data class SaveFilePayload(
    val emulatorId: String,
    val channelName: String? = null,
    val source: QueueSource = QueueSource.DIRECT
)

@JsonClass(generateAdapter = true)
data class SaveStatePayload(
    val stateCacheId: Long,
    val emulatorId: String
)

@JsonClass(generateAdapter = true)
data class PropertyPayload(
    val intValue: Int? = null,
    val stringValue: String? = null
)

@JsonClass(generateAdapter = true)
data class AchievementPayload(
    val achievementRaId: Long,
    val forHardcoreMode: Boolean,
    val earnedAt: Long
)

@Singleton
class SyncPayloadCodec @Inject constructor(moshi: Moshi) {
    private val saveFileAdapter = moshi.adapter(SaveFilePayload::class.java)
    private val saveStateAdapter = moshi.adapter(SaveStatePayload::class.java)
    private val propertyAdapter = moshi.adapter(PropertyPayload::class.java)
    private val achievementAdapter = moshi.adapter(AchievementPayload::class.java)

    fun encode(p: SaveFilePayload): String = saveFileAdapter.toJson(p)
    fun encode(p: SaveStatePayload): String = saveStateAdapter.toJson(p)
    fun encode(p: PropertyPayload): String = propertyAdapter.toJson(p)
    fun encode(p: AchievementPayload): String = achievementAdapter.toJson(p)

    fun decodeSaveFile(json: String): SaveFilePayload? =
        runCatching { saveFileAdapter.fromJson(json) }.getOrNull()

    fun decodeSaveState(json: String): SaveStatePayload? =
        runCatching { saveStateAdapter.fromJson(json) }.getOrNull()

    fun decodeProperty(json: String): PropertyPayload? =
        runCatching { propertyAdapter.fromJson(json) }.getOrNull()

    fun decodeAchievement(json: String): AchievementPayload? =
        runCatching { achievementAdapter.fromJson(json) }.getOrNull()
}
