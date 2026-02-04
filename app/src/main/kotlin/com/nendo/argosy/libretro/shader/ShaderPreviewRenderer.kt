package com.nendo.argosy.libretro.shader

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.swordfish.libretrodroid.ShaderConfig
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.util.concurrent.Executors

class ShaderPreviewRenderer {

    private val glDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ShaderPreviewGL").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: EGLConfig? = null
    private var contextWidth = 0
    private var contextHeight = 0

    suspend fun render(
        inputBitmap: Bitmap,
        shaderConfig: ShaderConfig.Custom,
        passParams: List<Map<String, Float>> = emptyList(),
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap? {
        return withContext(glDispatcher) {
            try {
                ensureContext(outputWidth, outputHeight)
                renderInternal(inputBitmap, shaderConfig, passParams, outputWidth, outputHeight)
            } catch (e: Exception) {
                Log.e(TAG, "Shader preview render failed", e)
                null
            }
        }
    }

    private fun renderInternal(
        inputBitmap: Bitmap,
        shaderConfig: ShaderConfig.Custom,
        passParams: List<Map<String, Float>>,
        outputWidth: Int,
        outputHeight: Int
    ): Bitmap {
        val inputTexture = createTexture(inputBitmap)
        val programs = mutableListOf<CompiledPass>()
        val fbos = mutableListOf<FramebufferObject>()

        try {
            for (pass in shaderConfig.passes) {
                programs.add(compilePass(pass))
            }

            for (i in 0 until shaderConfig.passes.size - 1) {
                val pass = shaderConfig.passes[i]
                val fboWidth = (inputBitmap.width * pass.scale).toInt().coerceAtLeast(1)
                val fboHeight = (inputBitmap.height * pass.scale).toInt().coerceAtLeast(1)
                fbos.add(createFBO(fboWidth, fboHeight, pass.linear))
            }

            val texW = inputBitmap.width.toFloat()
            val texH = inputBitmap.height.toFloat()

            for (i in programs.indices) {
                val compiled = programs[i]
                val isLast = i == programs.lastIndex

                if (isLast) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    GLES20.glViewport(0, 0, outputWidth, outputHeight)
                } else {
                    val fbo = fbos[i]
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.framebuffer)
                    GLES20.glViewport(0, 0, fbo.width, fbo.height)
                }

                GLES20.glClearColor(0f, 0f, 0f, 1f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                GLES20.glUseProgram(compiled.program)

                if (compiled.positionHandle != -1) {
                    GLES20.glVertexAttribPointer(compiled.positionHandle, 2, GLES20.GL_FLOAT, false, 0, POSITION_BUFFER)
                    GLES20.glEnableVertexAttribArray(compiled.positionHandle)
                }

                if (compiled.coordHandle != -1) {
                    GLES20.glVertexAttribPointer(compiled.coordHandle, 2, GLES20.GL_FLOAT, false, 0, TEX_COORD_BUFFER)
                    GLES20.glEnableVertexAttribArray(compiled.coordHandle)
                }

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
                GLES20.glUniform1i(compiled.textureHandle, 0)

                if (i > 0 && compiled.previousPassHandle != -1) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbos[i - 1].texture)
                    GLES20.glUniform1i(compiled.previousPassHandle, 1)
                }

                val outW: Float
                val outH: Float
                if (isLast) {
                    outW = outputWidth.toFloat()
                    outH = outputHeight.toFloat()
                } else {
                    outW = fbos[i].width.toFloat()
                    outH = fbos[i].height.toFloat()
                }

                setUniformVec2(compiled.textureSizeHandle, texW, texH)
                setUniformVec2(compiled.inputSizeHandle, texW, texH)
                setUniformVec2(compiled.outputSizeHandle, outW, outH)
                setUniformInt(compiled.frameCountHandle, 0)
                setUniformInt(compiled.frameDirectionHandle, 1)
                setUniformFloat(compiled.screenDensityHandle, outH / texH)
                if (compiled.mvpMatrixHandle != -1) {
                    GLES20.glUniformMatrix4fv(compiled.mvpMatrixHandle, 1, false, IDENTITY_MATRIX, 0)
                }

                val paramMap = passParams.getOrElse(i) { emptyMap() }
                for ((name, value) in paramMap) {
                    val handle = GLES20.glGetUniformLocation(compiled.program, name)
                    if (handle != -1) GLES20.glUniform1f(handle, value)
                }

                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

                if (compiled.positionHandle != -1) GLES20.glDisableVertexAttribArray(compiled.positionHandle)
                if (compiled.coordHandle != -1) GLES20.glDisableVertexAttribArray(compiled.coordHandle)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                GLES20.glUseProgram(0)
            }

            return readPixels(outputWidth, outputHeight)
        } finally {
            deleteTexture(inputTexture)
            programs.forEach { GLES20.glDeleteProgram(it.program) }
            fbos.forEach { deleteFBO(it) }
        }
    }

    private fun ensureContext(width: Int, height: Int) {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && contextWidth == width && contextHeight == height) {
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            return
        }
        destroyContext()

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0)
        eglConfig = configs[0]!!

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, surfaceAttribs, 0)

        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        contextWidth = width
        contextHeight = height
    }

    private fun destroyContext() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
            if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
        eglConfig = null
        contextWidth = 0
        contextHeight = 0
    }

    fun destroy() {
        destroyContext()
        glDispatcher.close()
    }

    private fun createTexture(bitmap: Bitmap): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return textures[0]
    }

    private fun deleteTexture(texture: Int) {
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    private fun createFBO(width: Int, height: Int, linear: Boolean): FramebufferObject {
        val filter = if (linear) GLES20.GL_LINEAR else GLES20.GL_NEAREST

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        val fbos = IntArray(1)
        GLES20.glGenFramebuffers(1, fbos, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[0])
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, textures[0], 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        return FramebufferObject(fbos[0], textures[0], width, height)
    }

    private fun deleteFBO(fbo: FramebufferObject) {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo.framebuffer), 0)
        GLES20.glDeleteTextures(1, intArrayOf(fbo.texture), 0)
    }

    private fun compilePass(pass: ShaderConfig.Custom.ShaderPass): CompiledPass {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, pass.vertex)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, pass.fragment)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            throw RuntimeException("Program link failed: $log")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        var textureHandle = GLES20.glGetUniformLocation(program, "texture")
        if (textureHandle == -1) textureHandle = GLES20.glGetUniformLocation(program, "Texture")

        var textureSizeHandle = GLES20.glGetUniformLocation(program, "textureSize")
        if (textureSizeHandle == -1) textureSizeHandle = GLES20.glGetUniformLocation(program, "TextureSize")

        var positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        if (positionHandle == -1) positionHandle = GLES20.glGetAttribLocation(program, "VertexCoord")

        var coordHandle = GLES20.glGetAttribLocation(program, "vCoordinate")
        if (coordHandle == -1) coordHandle = GLES20.glGetAttribLocation(program, "TexCoord")

        return CompiledPass(
            program = program,
            positionHandle = positionHandle,
            coordHandle = coordHandle,
            textureHandle = textureHandle,
            previousPassHandle = GLES20.glGetUniformLocation(program, "previousPass"),
            textureSizeHandle = textureSizeHandle,
            inputSizeHandle = GLES20.glGetUniformLocation(program, "InputSize"),
            outputSizeHandle = GLES20.glGetUniformLocation(program, "OutputSize"),
            frameCountHandle = GLES20.glGetUniformLocation(program, "FrameCount"),
            frameDirectionHandle = GLES20.glGetUniformLocation(program, "FrameDirection"),
            screenDensityHandle = GLES20.glGetUniformLocation(program, "screenDensity"),
            mvpMatrixHandle = GLES20.glGetUniformLocation(program, "MVPMatrix")
        )
    }

    private fun compileShader(type: Int, source: String): Int {
        val typeStr = if (type == GLES20.GL_VERTEX_SHADER) "vertex" else "fragment"
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "glCreateShader returned 0, glError=${GLES20.glGetError()}")
            throw RuntimeException("$typeStr glCreateShader returned 0")
        }
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "compileShader failed: $typeStr, log='$log', glError=${GLES20.glGetError()}")
            GLES20.glDeleteShader(shader)
            throw RuntimeException("$typeStr shader compile failed: $log")
        }
        return shader
    }

    private fun readPixels(width: Int, height: Int): Bitmap {
        val buffer = IntBuffer.allocate(width * height)
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)

        val pixels = buffer.array()
        val result = IntArray(width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = pixel and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = (pixel shr 16) and 0xFF
            val a = (pixel shr 24) and 0xFF
            result[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(result, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun setUniformVec2(handle: Int, x: Float, y: Float) {
        if (handle != -1) GLES20.glUniform2f(handle, x, y)
    }

    private fun setUniformInt(handle: Int, value: Int) {
        if (handle != -1) GLES20.glUniform1i(handle, value)
    }

    private fun setUniformFloat(handle: Int, value: Float) {
        if (handle != -1) GLES20.glUniform1f(handle, value)
    }

    private data class CompiledPass(
        val program: Int,
        val positionHandle: Int,
        val coordHandle: Int,
        val textureHandle: Int,
        val previousPassHandle: Int,
        val textureSizeHandle: Int,
        val inputSizeHandle: Int,
        val outputSizeHandle: Int,
        val frameCountHandle: Int,
        val frameDirectionHandle: Int,
        val screenDensityHandle: Int,
        val mvpMatrixHandle: Int
    )

    private data class FramebufferObject(
        val framebuffer: Int,
        val texture: Int,
        val width: Int,
        val height: Int
    )

    companion object {
        private const val TAG = "ShaderPreview"
        private const val EGL_OPENGL_ES3_BIT = 0x40

        private val POSITIONS = floatArrayOf(
            -1f, -1f,   1f, -1f,  -1f,  1f,
            -1f,  1f,   1f, -1f,   1f,  1f
        )

        private val TEX_COORDS = floatArrayOf(
            0f, 0f,  1f, 0f,  0f, 1f,
            0f, 1f,  1f, 0f,  1f, 1f
        )

        private val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        private val POSITION_BUFFER: FloatBuffer = ByteBuffer
            .allocateDirect(POSITIONS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(POSITIONS); position(0) }

        private val TEX_COORD_BUFFER: FloatBuffer = ByteBuffer
            .allocateDirect(TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(TEX_COORDS); position(0) }
    }
}
