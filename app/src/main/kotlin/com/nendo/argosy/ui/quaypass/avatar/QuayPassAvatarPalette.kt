package com.nendo.argosy.ui.quaypass.avatar

import androidx.compose.ui.graphics.Color

/** Shared 16-entry palette indexed by the wire-format `*Color` 4-bit fields. */
object QuayPassAvatarPalette {

    val skin: List<Color> = listOf(
        Color(0xFFFFE0BD), Color(0xFFFFCD94), Color(0xFFEAC086), Color(0xFFFFAD60),
        Color(0xFFD8AB7F), Color(0xFFC68642), Color(0xFFAE7E59), Color(0xFF8D5524),
        Color(0xFF6F4226), Color(0xFF4E2A14), Color(0xFFF4D9C0), Color(0xFFE5B299),
        Color(0xFFD2A98A), Color(0xFFA67651), Color(0xFF7A4F2D), Color(0xFF3E2014),
    )

    val hair: List<Color> = listOf(
        Color(0xFF1B1B1B), Color(0xFF3D2317), Color(0xFF5C2C0C), Color(0xFF8B4513),
        Color(0xFFA0522D), Color(0xFFC68642), Color(0xFFD2691E), Color(0xFFDAA520),
        Color(0xFFE9C46A), Color(0xFFFFE39F), Color(0xFFE63946), Color(0xFFB1361E),
        Color(0xFF8E44AD), Color(0xFF2D6A8E), Color(0xFF7CB342), Color(0xFFCFCFCF),
    )

    val eye: List<Color> = listOf(
        Color(0xFF1B1B1B), Color(0xFF3E2723), Color(0xFF5D4037), Color(0xFF795548),
        Color(0xFF8D6E63), Color(0xFFA1887F), Color(0xFF1565C0), Color(0xFF1976D2),
        Color(0xFF2E7D32), Color(0xFF388E3C), Color(0xFF6D4C41), Color(0xFF455A64),
        Color(0xFF7E57C2), Color(0xFF5E35B1), Color(0xFF00838F), Color(0xFF424242),
    )

    val mouth: List<Color> = listOf(
        Color(0xFFC04A4A), Color(0xFFD96868), Color(0xFFB23B3B), Color(0xFFE07A7A),
        Color(0xFFCC5C5C), Color(0xFFA82E2E), Color(0xFF8B1A1A), Color(0xFFEAA0A0),
        Color(0xFFB85C5C), Color(0xFF6E0F0F), Color(0xFFD46A6A), Color(0xFFB04848),
        Color(0xFF903030), Color(0xFFE89090), Color(0xFFC75252), Color(0xFFAA3838),
    )

    val accessory: List<Color> = listOf(
        Color(0xFF1B1B1B), Color(0xFF424242), Color(0xFF6D6D6D), Color(0xFFAAAAAA),
        Color(0xFFE0E0E0), Color(0xFFFFFFFF), Color(0xFF8B4513), Color(0xFFC68642),
        Color(0xFFB71C1C), Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFFF8F00),
        Color(0xFFAD1457), Color(0xFF6A1B9A), Color(0xFF455A64), Color(0xFF000000),
    )

    val favorite: List<Color> = listOf(
        Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8), Color(0xFF9575CD),
        Color(0xFF7986CB), Color(0xFF64B5F6), Color(0xFF4FC3F7), Color(0xFF4DD0E1),
        Color(0xFF4DB6AC), Color(0xFF81C784), Color(0xFFAED581), Color(0xFFDCE775),
        Color(0xFFFFD54F), Color(0xFFFFB74D), Color(0xFFFF8A65), Color(0xFFA1887F),
    )

    fun skinAt(index: Int): Color = skin[index.coerceIn(0, skin.lastIndex)]
    fun hairAt(index: Int): Color = hair[index.coerceIn(0, hair.lastIndex)]
    fun eyeAt(index: Int): Color = eye[index.coerceIn(0, eye.lastIndex)]
    fun mouthAt(index: Int): Color = mouth[index.coerceIn(0, mouth.lastIndex)]
    fun accessoryAt(index: Int): Color = accessory[index.coerceIn(0, accessory.lastIndex)]
    fun favoriteAt(index: Int): Color = favorite[index.coerceIn(0, favorite.lastIndex)]

    fun Color.toAvatarHex(): String {
        val argb = (value shr 32).toInt()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return "%02X%02X%02X".format(r, g, b)
    }
}
