package com.nendo.argosy.data.quaypass.ble

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

fun QuayPassAvatar.partIndexFor(category: AvatarCategory): Int = when (category) {
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

fun QuayPassAvatar.colorIndexFor(category: AvatarCategory): Int = when (category) {
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

fun QuayPassAvatar.withPart(category: AvatarCategory, index: Int): QuayPassAvatar = when (category) {
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

fun QuayPassAvatar.withColor(category: AvatarCategory, colorIndex: Int): QuayPassAvatar {
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
