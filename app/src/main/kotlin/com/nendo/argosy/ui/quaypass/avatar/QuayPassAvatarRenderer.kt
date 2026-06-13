package com.nendo.argosy.ui.quaypass.avatar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.quaypass.ble.AvatarCategory
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
        AvatarLayer(AvatarCategory.Face, avatar.faceShape.atLeastOne(), palette)
        AvatarLayer(AvatarCategory.Wrinkles, avatar.wrinkles, palette)
        AvatarLayer(AvatarCategory.Makeup, avatar.makeup, palette)
        AvatarLayer(AvatarCategory.Mouth, avatar.mouthType.atLeastOne(), palette)
        AvatarLayer(AvatarCategory.Mustache, avatar.mustacheType, palette)
        AvatarLayer(AvatarCategory.Goatee, avatar.goateeType, palette)
        AvatarLayer(AvatarCategory.Nose, avatar.noseType.atLeastOne(), palette)
        AvatarLayer(AvatarCategory.Eyes, avatar.eyeType.atLeastOne(), palette)
        AvatarLayer(AvatarCategory.Eyes, avatar.eyeType.atLeastOne(), palette, mirrored = true)
        AvatarLayer(AvatarCategory.Eyebrows, avatar.eyebrowType.atLeastOne(), palette)
        AvatarLayer(AvatarCategory.Eyebrows, avatar.eyebrowType.atLeastOne(), palette, mirrored = true)
        AvatarLayer(AvatarCategory.Glasses, avatar.glassesType, palette)
        AvatarLayer(AvatarCategory.Hair, avatar.hairType, palette)
        AvatarLayer(AvatarCategory.Hat, avatar.hatType, palette)
    }
}

@Composable
private fun BoxScope.AvatarLayer(
    category: AvatarCategory,
    index: Int,
    palette: AvatarPartRequest,
    mirrored: Boolean = false
) {
    if (index <= 0 && category in OPTIONAL_ZERO_CATEGORIES) return
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(palette.copy(category = category, index = index))
        .crossfade(false)
        .build()
    val mirrorModifier = if (mirrored) Modifier.scale(scaleX = -1f, scaleY = 1f) else Modifier
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier.matchParentSize().then(mirrorModifier)
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
