package com.nendo.argosy.ui.quaypass.avatar

import android.content.Context
import androidx.compose.ui.graphics.Color
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.nendo.argosy.ui.quaypass.avatar.QuayPassAvatarPalette.toAvatarHex
import okio.Buffer

data class AvatarPartRequest(
    val category: AvatarCategory,
    val index: Int,
    val skin: Color = DEFAULT_SKIN,
    val skinStroke: Color = DEFAULT_SKIN_STROKE,
    val hair: Color = DEFAULT_HAIR,
    val hairStroke: Color = DEFAULT_HAIR_STROKE,
    val eye: Color = DEFAULT_EYE,
    val eyebrow: Color = DEFAULT_HAIR,
    val mouth: Color = DEFAULT_MOUTH,
    val facialHair: Color = DEFAULT_HAIR,
    val glasses: Color = DEFAULT_ACCESSORY,
    val hat: Color = DEFAULT_ACCESSORY
) {
    companion object {
        val DEFAULT_SKIN = Color(0xFFFFE0BD)
        val DEFAULT_SKIN_STROKE = Color(0xFF6F6F6F)
        val DEFAULT_HAIR = Color(0xFF1B1B1B)
        val DEFAULT_HAIR_STROKE = Color(0xFFBFBFBF)
        val DEFAULT_EYE = Color(0xFF1B1B1B)
        val DEFAULT_MOUTH = Color(0xFFC04A4A)
        val DEFAULT_ACCESSORY = Color(0xFF424242)
    }
}

class QuayPassAvatarSvgFetcher(
    private val data: AvatarPartRequest,
    private val context: Context,
    private val catalog: QuayPassAvatarPartCatalog
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val path = catalog.assetPathFor(data.category, data.index) ?: return null
        val raw = runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null

        val rendered = applyTokens(raw)
        val buffer = Buffer().writeUtf8(rendered)

        return SourceResult(
            source = ImageSource(buffer, context),
            mimeType = "image/svg+xml",
            dataSource = DataSource.MEMORY
        )
    }

    private fun applyTokens(svg: String): String {
        var s = svg
        s = s.replace(TOKEN_SKIN_FILL, "#${data.skin.toAvatarHex()}")
        s = s.replace(TOKEN_SKIN_STROKE, "#${data.skinStroke.toAvatarHex()}")
        s = s.replace(TOKEN_HAIR_FILL, "#${data.hair.toAvatarHex()}")
        s = s.replace(TOKEN_HAIR_STROKE, "#${data.hairStroke.toAvatarHex()}")
        s = s.replace(TOKEN_EYE_FILL, "#${data.eye.toAvatarHex()}")
        s = s.replace(TOKEN_EYEBROW_FILL, "#${data.eyebrow.toAvatarHex()}")
        s = s.replace(TOKEN_MOUTH_FILL, "#${data.mouth.toAvatarHex()}")
        s = s.replace(TOKEN_FACIAL_HAIR_FILL, "#${data.facialHair.toAvatarHex()}")
        s = s.replace(TOKEN_GLASSES_FILL, "#${data.glasses.toAvatarHex()}")
        s = s.replace(TOKEN_HAT_FILL, "#${data.hat.toAvatarHex()}")
        return s
    }

    class Factory(
        private val catalog: QuayPassAvatarPartCatalog
    ) : Fetcher.Factory<AvatarPartRequest> {

        override fun create(
            data: AvatarPartRequest,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = QuayPassAvatarSvgFetcher(data, options.context, catalog)
    }

    companion object {
        private const val TOKEN_SKIN_FILL = "{{skin-fill}}"
        private const val TOKEN_SKIN_STROKE = "{{skin-stroke}}"
        private const val TOKEN_HAIR_FILL = "{{hair-fill}}"
        private const val TOKEN_HAIR_STROKE = "{{hair-stroke}}"
        private const val TOKEN_EYE_FILL = "{{eye-fill}}"
        private const val TOKEN_EYEBROW_FILL = "{{eyebrow-fill}}"
        private const val TOKEN_MOUTH_FILL = "{{mouth-fill}}"
        private const val TOKEN_FACIAL_HAIR_FILL = "{{facial-hair-fill}}"
        private const val TOKEN_GLASSES_FILL = "{{glasses-fill}}"
        private const val TOKEN_HAT_FILL = "{{hat-fill}}"
    }
}
