package com.nendo.argosy.ui.quaypass.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
import com.nendo.argosy.data.quaypass.ble.partIndexFor
import coil.request.ImageRequest
import com.nendo.argosy.data.quaypass.ble.QuayPassAvatar

@Composable
fun QuayPassAvatarRenderer(
    avatar: QuayPassAvatar,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val palette = remember(avatar) { avatar.toPaletteRequest() }
    Box(modifier = modifier.size(size)) {
        CategoryLayers(AvatarCategory.Face, avatar, palette)
        CategoryLayers(AvatarCategory.Wrinkles, avatar, palette)
        CategoryLayers(AvatarCategory.Makeup, avatar, palette)
        CategoryLayers(AvatarCategory.Mouth, avatar, palette)
        CategoryLayers(AvatarCategory.Mustache, avatar, palette)
        CategoryLayers(AvatarCategory.Goatee, avatar, palette)
        CategoryLayers(AvatarCategory.Nose, avatar, palette)
        CategoryLayers(AvatarCategory.Eyes, avatar, palette)
        CategoryLayers(AvatarCategory.Eyebrows, avatar, palette)
        CategoryLayers(AvatarCategory.Glasses, avatar, palette)
        CategoryLayers(AvatarCategory.Hair, avatar, palette)
        CategoryLayers(AvatarCategory.Hat, avatar, palette)
    }
}

@Composable
private fun BoxScope.CategoryLayers(
    category: AvatarCategory,
    avatar: QuayPassAvatar,
    palette: AvatarPartRequest
) {
    when (category) {
        AvatarCategory.Eyes -> {
            val s = scaleOf(avatar.eyeScale, EYE_SCALE)
            val y = yOf(avatar.eyeYPosition, EYE_Y)
            val sp = spreadOf(avatar.eyeSpacing, EYE_SPREAD)
            AvatarLayer(category, avatar.eyeType.atLeastOne(), palette, scale = s, offsetXFraction = -sp, offsetYFraction = y)
            AvatarLayer(category, avatar.eyeType.atLeastOne(), palette, scale = s, offsetXFraction = sp, offsetYFraction = y, mirrored = true)
        }
        AvatarCategory.Eyebrows -> {
            val s = scaleOf(avatar.eyebrowScale, BROW_SCALE)
            val y = yOf(avatar.eyebrowYPosition, BROW_Y)
            val sp = spreadOf(avatar.eyebrowSpacing, EYE_SPREAD)
            AvatarLayer(category, avatar.eyebrowType.atLeastOne(), palette, scale = s, offsetXFraction = -sp, offsetYFraction = y)
            AvatarLayer(category, avatar.eyebrowType.atLeastOne(), palette, scale = s, offsetXFraction = sp, offsetYFraction = y, mirrored = true)
        }
        AvatarCategory.Nose ->
            AvatarLayer(category, avatar.noseType.atLeastOne(), palette, scale = scaleOf(avatar.noseScale, NOSE_SCALE), offsetYFraction = yOf(avatar.noseYPosition, NOSE_Y))
        AvatarCategory.Mouth ->
            AvatarLayer(category, avatar.mouthType.atLeastOne(), palette, scale = scaleOf(avatar.mouthScale, MOUTH_SCALE), offsetYFraction = yOf(avatar.mouthYPosition, MOUTH_Y))
        AvatarCategory.Mustache ->
            AvatarLayer(category, avatar.mustacheType, palette, scale = scaleOf(avatar.mustacheScale, FACIAL_SCALE), offsetYFraction = yOf(avatar.mustacheYPosition, MUSTACHE_Y))
        AvatarCategory.Goatee ->
            AvatarLayer(category, avatar.goateeType, palette, scale = scaleOf(avatar.mustacheScale, FACIAL_SCALE), offsetYFraction = GOATEE_Y)
        AvatarCategory.Glasses ->
            AvatarLayer(category, avatar.glassesType, palette, scale = scaleOf(avatar.glassesScale, GLASSES_SCALE), offsetYFraction = yOf(avatar.glassesYPosition, GLASSES_Y))
        AvatarCategory.Hair ->
            AvatarLayer(category, avatar.hairType, palette, offsetYFraction = HAIR_Y)
        else -> AvatarLayer(category, avatar.partIndexFor(category).orAtLeastOne(category), palette)
    }
}

private fun Int.orAtLeastOne(category: AvatarCategory): Int =
    if (category in OPTIONAL_ZERO_CATEGORIES) this else atLeastOne()

private const val NEUTRAL_POS = 16
private const val NEUTRAL_SCALE = 8
private const val Y_STEP = 0.010f
private const val SCALE_STEP = 0.06f
private const val SPREAD_STEP = 0.012f

private const val EYE_SCALE = 0.34f
private const val EYE_SPREAD = 0.15f
private const val EYE_Y = -0.10f
private const val BROW_SCALE = 0.34f
private const val BROW_Y = -0.23f
private const val NOSE_SCALE = 0.36f
private const val NOSE_Y = 0.0f
private const val MOUTH_SCALE = 0.5f
private const val MOUTH_Y = 0.17f
private const val FACIAL_SCALE = 0.5f
private const val MUSTACHE_Y = 0.14f
private const val GOATEE_Y = 0.28f
private const val GLASSES_SCALE = 0.7f
private const val GLASSES_Y = -0.04f
private const val HAIR_Y = -0.06f

private fun scaleOf(field: Int, base: Float): Float {
    val f = if (field == 0) NEUTRAL_SCALE else field
    return base * (1f + (f - NEUTRAL_SCALE) * SCALE_STEP)
}

private fun yOf(field: Int, base: Float): Float {
    val f = if (field == 0) NEUTRAL_POS else field
    return base + (f - NEUTRAL_POS) * Y_STEP
}

private fun spreadOf(field: Int, base: Float): Float {
    val f = if (field == 0) NEUTRAL_SCALE else field
    return base + (f - NEUTRAL_SCALE) * SPREAD_STEP
}

@Composable
private fun BoxScope.AvatarLayer(
    category: AvatarCategory,
    index: Int,
    palette: AvatarPartRequest,
    mirrored: Boolean = false,
    scale: Float = 1f,
    offsetXFraction: Float = 0f,
    offsetYFraction: Float = 0f
) {
    if (index <= 0 && category in OPTIONAL_ZERO_CATEGORIES) return
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(palette.copy(category = category, index = index))
        .crossfade(false)
        .build()
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = if (mirrored) -scale else scale
                scaleY = scale
                translationX = size.width * offsetXFraction
                translationY = size.height * offsetYFraction
            }
    )
}

private fun Int.atLeastOne(): Int = if (this <= 0) 1 else this

private val OPTIONAL_ZERO_CATEGORIES = setOf(
    AvatarCategory.Wrinkles,
    AvatarCategory.Makeup,
    AvatarCategory.Mustache,
    AvatarCategory.Goatee,
    AvatarCategory.Glasses,
    AvatarCategory.Hat
)

private fun QuayPassAvatar.toPaletteRequest(): AvatarPartRequest = AvatarPartRequest(
    category = AvatarCategory.Face,
    index = 1,
    skin = QuayPassAvatarPalette.skinAt(skinColor),
    hair = QuayPassAvatarPalette.hairAt(hairColor),
    eyebrow = QuayPassAvatarPalette.hairAt(eyebrowColor),
    eye = QuayPassAvatarPalette.eyeAt(eyeColor),
    mouth = QuayPassAvatarPalette.mouthAt(mouthColor),
    facialHair = QuayPassAvatarPalette.hairAt(facialHairColor),
    glasses = QuayPassAvatarPalette.accessoryAt(glassesColor),
    hat = QuayPassAvatarPalette.accessoryAt(hatColor)
)
