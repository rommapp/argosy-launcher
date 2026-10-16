#include <jni.h>
#include "video.h"
#include "libretro/libretro-common/include/libretro.h"

namespace {

struct OffscreenContext {
    EGLDisplay display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface surface = EGL_NO_SURFACE;

    bool initialize() {
        if (!eglInitialize(display, nullptr, nullptr)) return false;
        const EGLint attributes[] = {
            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
            EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
            EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
            EGL_NONE
        };
        EGLConfig config;
        EGLint count = 0;
        if (!eglChooseConfig(display, attributes, &config, 1, &count) || count != 1) return false;
        const EGLint contextAttributes[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
        context = eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttributes);
        const EGLint surfaceAttributes[] = {EGL_WIDTH, 8, EGL_HEIGHT, 12, EGL_NONE};
        surface = eglCreatePbufferSurface(display, config, surfaceAttributes);
        return context != EGL_NO_CONTEXT && surface != EGL_NO_SURFACE &&
            eglMakeCurrent(display, surface, surface, context);
    }

    ~OffscreenContext() {
        eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface != EGL_NO_SURFACE) eglDestroySurface(display, surface);
        if (context != EGL_NO_CONTEXT) eglDestroyContext(display, context);
        eglTerminate(display);
    }
};

}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_swordfish_libretrodroid_VideoOriginalFrameNativeTest_renderPaddedHardwareFrame(
    JNIEnv*, jobject
) {
    OffscreenContext context;
    if (!context.initialize()) return JNI_FALSE;

    using namespace libretrodroid;
    const std::string vertex = R"(
        attribute vec4 VertexCoord;
        attribute vec2 TexCoord;
        varying vec2 uv;
        void main() {
            gl_Position = VertexCoord;
            uv = TexCoord;
        }
    )";
    const std::string fragment = R"(
        precision highp float;
        varying vec2 uv;
        uniform sampler2D Texture;
        uniform sampler2D OrigTexture;
        uniform vec2 TextureSize;
        uniform vec2 InputSize;
        uniform vec2 OrigTextureSize;
        uniform vec2 OrigInputSize;
        void main() {
            vec2 originalCoord = uv * (TextureSize / InputSize)
                                    * (OrigInputSize / OrigTextureSize);
            bool valid = all(equal(TextureSize, vec2(4.0, 6.0)))
                && all(equal(OrigTextureSize, vec2(4.0, 6.0)))
                && all(equal(InputSize, vec2(2.0, 3.0)))
                && all(equal(OrigInputSize, vec2(2.0, 3.0)))
                && texture2D(Texture, uv).r > 0.9
                && texture2D(OrigTexture, originalCoord).r > 0.9;
            gl_FragColor = valid ? vec4(0.0, 1.0, 0.0, 1.0) : vec4(1.0, 0.0, 0.0, 1.0);
        }
    )";
    Video::RenderingOptions options {true, 4, 6, false, false, 3, RETRO_PIXEL_FORMAT_XRGB8888};
    ShaderManager::Config shader {
        ShaderManager::Type::SHADER_CUSTOM, {}, {{vertex, fragment, false, 1.0f}}
    };
    Video video(options, shader, true, 0.0f, false, false, Rect(0, 0, 1, 1), {});
    video.updateScreenSize(8, 12);
    video.updateAspectRatio(2.0f / 3.0f);
    video.bindHWContext();
    glBindFramebuffer(GL_FRAMEBUFFER, video.getCurrentFramebuffer());
    glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glFinish();
    video.bindMainContext();
    video.onNewFrame(RETRO_HW_FRAME_BUFFER_VALID, 2, 3, 0);
    video.renderFrame(true);
    unsigned char pixel[4] = {};
    glReadPixels(4, 6, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
    return glGetError() == GL_NO_ERROR && pixel[0] == 0 && pixel[1] == 255 && pixel[2] == 0;
}
