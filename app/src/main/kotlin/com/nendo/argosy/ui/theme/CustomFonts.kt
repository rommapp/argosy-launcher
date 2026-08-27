package com.nendo.argosy.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.nendo.argosy.ui.theme.generated.TypographyTokens
import java.io.File

data class CustomFontFamilies(
    val display: FontFamily? = null,
    val body: FontFamily? = null
)

object CustomFontLoader {

    fun fontsDir(context: Context): File = File(context.filesDir, "fonts")

    fun loadFamily(path: String): FontFamily? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            validate(file)
            FontFamily(typefaceWithSystemFallback(file))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A user-supplied font covers the scripts its designer drew and nothing else, so a family
     * built from it alone renders tofu for every other script. Chaining the system fallback
     * behind it keeps the chosen face for the glyphs it has and borrows the rest.
     */
    private fun typefaceWithSystemFallback(file: File): Typeface {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Typeface.createFromFile(file)
        return try {
            val font = android.graphics.fonts.Font.Builder(file).build()
            val family = android.graphics.fonts.FontFamily.Builder(font).build()
            Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif")
                .build()
        } catch (e: Exception) {
            Typeface.createFromFile(file)
        }
    }

    /** Throws if the file is not a parseable font. */
    fun validate(file: File) {
        val typeface = Typeface.createFromFile(file)
        check(typeface != null && typeface != Typeface.DEFAULT) { "Typeface could not be loaded" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            android.graphics.fonts.Font.Builder(file).build()
        }
    }
}

private fun TextStyle.withSlot(family: FontFamily?, scale: Int): TextStyle {
    val styled = if (family != null) copy(fontFamily = family) else this
    if (scale == 100) return styled
    val factor = scale / 100f
    return styled.copy(fontSize = styled.fontSize * factor, lineHeight = styled.lineHeight * factor)
}

/** Material typography with DISPLAY driving display/headline/title styles and BODY driving body/label styles; per-slot scale (percent) multiplies fontSize and lineHeight; defaults pass through untouched. */
fun argosyTypography(fonts: CustomFontFamilies, displayScale: Int = 100, bodyScale: Int = 100): Typography {
    if (fonts.display == null && fonts.body == null && displayScale == 100 && bodyScale == 100) {
        return TypographyTokens.Material3
    }
    return Typography(
        displayLarge = TypographyTokens.displayLarge.withSlot(fonts.display, displayScale),
        displayMedium = TypographyTokens.displayMedium.withSlot(fonts.display, displayScale),
        displaySmall = TypographyTokens.displaySmall.withSlot(fonts.display, displayScale),
        headlineLarge = TypographyTokens.headlineLarge.withSlot(fonts.display, displayScale),
        headlineMedium = TypographyTokens.headlineMedium.withSlot(fonts.display, displayScale),
        headlineSmall = TypographyTokens.headlineSmall.withSlot(fonts.display, displayScale),
        titleLarge = TypographyTokens.titleLarge.withSlot(fonts.display, displayScale),
        titleMedium = TypographyTokens.titleMedium.withSlot(fonts.display, displayScale),
        titleSmall = TypographyTokens.titleSmall.withSlot(fonts.display, displayScale),
        bodyLarge = TypographyTokens.bodyLarge.withSlot(fonts.body, bodyScale),
        bodyMedium = TypographyTokens.bodyMedium.withSlot(fonts.body, bodyScale),
        bodySmall = TypographyTokens.bodySmall.withSlot(fonts.body, bodyScale),
        labelLarge = TypographyTokens.labelLarge.withSlot(fonts.body, bodyScale),
        labelMedium = TypographyTokens.labelMedium.withSlot(fonts.body, bodyScale),
        labelSmall = TypographyTokens.labelSmall.withSlot(fonts.body, bodyScale)
    )
}
