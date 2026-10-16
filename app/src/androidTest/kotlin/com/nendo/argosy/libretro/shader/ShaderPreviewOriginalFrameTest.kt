package com.nendo.argosy.libretro.shader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShaderPreviewOriginalFrameTest {

    @Test
    fun originalFrameUniformsWorkInSinglePass() = runBlocking {
        verifyOriginalFrame(emptyList(), "vec2(2.0, 3.0)", Color.YELLOW)
    }

    @Test
    fun originalFrameSurvivesScaledPreviousPass() = runBlocking {
        val recolorPass = ShaderConfig.Custom.ShaderPass(
            vertex = VERTEX,
            fragment = """
                precision highp float;
                void main() { gl_FragColor = vec4(0.0, 0.0, 1.0, 1.0); }
            """.trimIndent(),
            scale = 2f
        )
        verifyOriginalFrame(listOf(recolorPass), "vec2(4.0, 6.0)", Color.WHITE)
    }

    private suspend fun verifyOriginalFrame(
        precedingPasses: List<ShaderConfig.Custom.ShaderPass>,
        expectedInputSize: String,
        expectedColor: Int
    ) {
        val input = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        input.eraseColor(Color.RED)
        val probePass = ShaderConfig.Custom.ShaderPass(
            vertex = VERTEX,
            fragment = """
                precision highp float;
                varying vec2 uv;
                uniform sampler2D Texture;
                uniform sampler2D OrigTexture;
                uniform sampler2D previousPass;
                uniform vec2 TextureSize;
                uniform vec2 InputSize;
                uniform vec2 OrigTextureSize;
                uniform vec2 OrigInputSize;
                void main() {
                    vec2 origCoord = uv * (TextureSize / InputSize)
                                        * (OrigInputSize / OrigTextureSize);
                    vec3 original = texture2D(OrigTexture, origCoord).rgb;
                    vec3 legacyOriginal = texture2D(previousPass, origCoord).rgb;
                    vec3 current = texture2D(Texture, uv).rgb;
                    bool sizesMatch = all(equal(OrigTextureSize, vec2(2.0, 3.0)))
                        && all(equal(OrigInputSize, vec2(2.0, 3.0)))
                        && all(equal(InputSize, $expectedInputSize))
                        && all(equal(TextureSize, $expectedInputSize));
                    gl_FragColor = vec4(min(original.r, legacyOriginal.r),
                                        sizesMatch ? 1.0 : 0.0, current.b, 1.0);
                }
            """.trimIndent()
        )
        val renderer = ShaderPreviewRenderer()
        val result = renderer.render(
            inputBitmap = input,
            shaderConfig = ShaderConfig.Custom(precedingPasses + probePass),
            outputWidth = 8,
            outputHeight = 12
        )
        try {
            assertNotNull(result)
            assertEquals(expectedColor, result!!.getPixel(4, 6))
        } finally {
            result?.recycle()
            input.recycle()
            renderer.destroy()
        }
    }

    companion object {
        private val VERTEX = """
            attribute vec4 VertexCoord;
            attribute vec2 TexCoord;
            varying vec2 uv;
            void main() {
                gl_Position = VertexCoord;
                uv = TexCoord;
            }
        """.trimIndent()
    }
}
