package com.nendo.argosy.ui.components.friends

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.nendo.argosy.ui.screens.doodle.DecodedDoodle
import com.nendo.argosy.ui.screens.doodle.DoodlePreview
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.screens.doodle.rememberDecodedDoodle

/**
 * Local user's avatar identity, provided once at the app root so any
 * [SocialAvatar] given a userId resolves the doodle without per-screen plumbing.
 */
data class LocalUserAvatarInfo(
    val userId: String? = null,
    val doodle: String? = null
)

val LocalUserAvatarState = androidx.compose.runtime.compositionLocalOf { LocalUserAvatarInfo() }

/**
 * Circular avatar used for social users; optional presence dot. A [avatarPngBase64]
 * raster wins when it decodes (the canonical form for anyone other than the local
 * user); otherwise the [avatarDoodle] is rendered, then the initial letter. When
 * [userId] matches the local user, their editable doodle is applied automatically
 * via [LocalUserAvatarState].
 */
@Composable
fun SocialAvatar(
    displayName: String,
    avatarColor: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    showOnlineDot: Boolean = false,
    avatarDoodle: String? = null,
    avatarPngBase64: String? = null,
    userId: String? = null
) {
    val localAvatar = LocalUserAvatarState.current
    val effectiveDoodle = avatarDoodle
        ?: localAvatar.doodle.takeIf { userId != null && userId == localAvatar.userId }
    val fallbackColor = MaterialTheme.colorScheme.primary
    val circleColor = try {
        avatarColor?.let { Color(it.toColorInt()) } ?: fallbackColor
    } catch (_: Exception) {
        fallbackColor
    }

    val decodedPng: ImageBitmap? = remember(avatarPngBase64) { decodeAvatarPng(avatarPngBase64) }
    val decodedDoodle: DecodedDoodle? = rememberDecodedDoodle(effectiveDoodle)

    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        if (decodedPng != null) {
            Image(
                bitmap = decodedPng,
                contentDescription = null,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else if (decodedDoodle != null) {
            DoodlePreview(
                canvasSize = decodedDoodle.size,
                pixels = decodedDoodle.pixels,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = (size.value * 0.4f).sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(Dimens.dotLg)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimens.dotSm)
                        .clip(CircleShape)
                        .background(ColorTokens.Domain.Presence.online)
                )
            }
        }
    }
}

private fun decodeAvatarPng(base64: String?): ImageBitmap? {
    if (base64.isNullOrEmpty()) return null
    return runCatching {
        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
