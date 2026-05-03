package com.nendo.argosy.ui.quaypass.avatar

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class AvatarCategory(val prefix: String, val tintable: Boolean) {
    Face("face", tintable = true),
    Wrinkles("wrinkles", tintable = false),
    Makeup("makeup", tintable = false),
    Eyes("eyes", tintable = true),
    Eyebrows("eyebrows", tintable = true),
    Nose("nose", tintable = false),
    Mouth("mouth", tintable = true),
    Mustache("mustache", tintable = true),
    Goatee("goatee", tintable = true),
    Hair("hair", tintable = true),
    Glasses("glasses", tintable = true),
    Hat("hat", tintable = true);

    fun isTintable(): Boolean = tintable
}

fun com.nendo.argosy.data.quaypass.ble.QuayPassAvatar.partIndexFor(category: AvatarCategory): Int = when (category) {
    AvatarCategory.Face -> faceShape
    AvatarCategory.Wrinkles -> wrinkles
    AvatarCategory.Makeup -> makeup
    AvatarCategory.Eyes -> eyeType
    AvatarCategory.Eyebrows -> eyebrowType
    AvatarCategory.Nose -> noseType
    AvatarCategory.Mouth -> mouthType
    AvatarCategory.Mustache -> mustacheType
    AvatarCategory.Goatee -> goateeType
    AvatarCategory.Hair -> hairType
    AvatarCategory.Glasses -> glassesType
    AvatarCategory.Hat -> hatType
}

fun com.nendo.argosy.data.quaypass.ble.QuayPassAvatar.colorIndexFor(category: AvatarCategory): Int = when (category) {
    AvatarCategory.Face -> skinColor
    AvatarCategory.Hair -> hairColor
    AvatarCategory.Eyes -> eyeColor
    AvatarCategory.Eyebrows -> eyebrowColor
    AvatarCategory.Mouth -> mouthColor
    AvatarCategory.Mustache, AvatarCategory.Goatee -> facialHairColor
    AvatarCategory.Glasses -> glassesColor
    AvatarCategory.Hat -> hatColor
    else -> 0
}

fun com.nendo.argosy.data.quaypass.ble.QuayPassAvatar.withPart(category: AvatarCategory, index: Int): com.nendo.argosy.data.quaypass.ble.QuayPassAvatar = when (category) {
    AvatarCategory.Face -> copy(faceShape = index)
    AvatarCategory.Wrinkles -> copy(wrinkles = index)
    AvatarCategory.Makeup -> copy(makeup = index)
    AvatarCategory.Eyes -> copy(eyeType = index)
    AvatarCategory.Eyebrows -> copy(eyebrowType = index)
    AvatarCategory.Nose -> copy(noseType = index)
    AvatarCategory.Mouth -> copy(mouthType = index)
    AvatarCategory.Mustache -> copy(mustacheType = index)
    AvatarCategory.Goatee -> copy(goateeType = index)
    AvatarCategory.Hair -> copy(hairType = index)
    AvatarCategory.Glasses -> copy(glassesType = index)
    AvatarCategory.Hat -> copy(hatType = index)
}

fun com.nendo.argosy.data.quaypass.ble.QuayPassAvatar.withColor(category: AvatarCategory, colorIndex: Int): com.nendo.argosy.data.quaypass.ble.QuayPassAvatar {
    val c = colorIndex.coerceIn(0, 15)
    return when (category) {
        AvatarCategory.Face -> copy(skinColor = c)
        AvatarCategory.Hair -> copy(hairColor = c)
        AvatarCategory.Eyes -> copy(eyeColor = c)
        AvatarCategory.Eyebrows -> copy(eyebrowColor = c)
        AvatarCategory.Mouth -> copy(mouthColor = c)
        AvatarCategory.Mustache, AvatarCategory.Goatee -> copy(facialHairColor = c)
        AvatarCategory.Glasses -> copy(glassesColor = c)
        AvatarCategory.Hat -> copy(hatColor = c)
        else -> this
    }
}

/** Enumerates available SVG part files per category. Cached after first load. */
@Singleton
class QuayPassAvatarPartCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {

    @Volatile
    private var cache: Map<AvatarCategory, List<Int>>? = null

    fun forCategory(category: AvatarCategory): List<Int> = ensureLoaded()[category].orEmpty()

    fun assetPathFor(category: AvatarCategory, index: Int): String =
        "quaypass/avatar/${category.prefix}-${formatIndex(category, index)}.svg"

    private fun ensureLoaded(): Map<AvatarCategory, List<Int>> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val all = runCatching { context.assets.list(ASSET_DIR)?.toList() }.getOrNull().orEmpty()
            val grouped = AvatarCategory.entries.associateWith { category ->
                all.mapNotNull { name ->
                    if (!name.endsWith(".svg")) return@mapNotNull null
                    val base = name.removeSuffix(".svg")
                    val parts = base.split("-", limit = 2)
                    if (parts.size != 2 || parts[0] != category.prefix) return@mapNotNull null
                    parts[1].toIntOrNull()
                }.sorted()
            }
            cache = grouped
            return grouped
        }
    }

    private fun formatIndex(category: AvatarCategory, index: Int): String {
        val sample = forCategory(category).firstOrNull()?.toString().orEmpty()
        return if (sample.length == 2) "%02d".format(index) else index.toString()
    }

    companion object {
        private const val ASSET_DIR = "quaypass/avatar"
    }
}
