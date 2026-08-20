#include "secondaryoutput.h"

#include "log.h"

namespace libretrodroid {

SecondaryOutput::~SecondaryOutput() {
    destroy();
}

void SecondaryOutput::setWindow(ANativeWindow* newWindow) {
    std::lock_guard<std::mutex> lock(windowMutex);
    if (pendingWindow != nullptr) {
        ANativeWindow_release(pendingWindow);
    }
    pendingWindow = newWindow;
    windowChanged = true;
}

void SecondaryOutput::adoptPendingWindow() {
    ANativeWindow* incoming = nullptr;
    {
        std::lock_guard<std::mutex> lock(windowMutex);
        incoming = pendingWindow;
        pendingWindow = nullptr;
        windowChanged = false;
    }

    releaseSurface();
    releaseWindow();
    window = incoming;
}

bool SecondaryOutput::beginFrame() {
    if (windowChanged) {
        adoptPendingWindow();
    }

    if (window == nullptr) {
        return false;
    }

    if (surface == EGL_NO_SURFACE) {
        display = eglGetCurrentDisplay();
        context = eglGetCurrentContext();
        if (display == EGL_NO_DISPLAY || context == EGL_NO_CONTEXT) {
            return false;
        }

        EGLint configId = 0;
        if (!eglQueryContext(display, context, EGL_CONFIG_ID, &configId)) {
            LOGE("Secondary output: cannot read the context config");
            return false;
        }

        EGLint attribs[] = { EGL_CONFIG_ID, configId, EGL_NONE };
        EGLConfig config;
        EGLint matched = 0;
        if (!eglChooseConfig(display, attribs, &config, 1, &matched) || matched < 1) {
            LOGE("Secondary output: no config matching the emulator context");
            return false;
        }

        surface = eglCreateWindowSurface(display, config, window, nullptr);
        if (surface == EGL_NO_SURFACE) {
            LOGE("Secondary output: eglCreateWindowSurface failed (0x%x)", eglGetError());
            return false;
        }
        LOGI("Secondary output: surface created");
    }

    primaryDraw = eglGetCurrentSurface(EGL_DRAW);
    primaryRead = eglGetCurrentSurface(EGL_READ);

    if (!eglMakeCurrent(display, surface, surface, context)) {
        LOGE("Secondary output: eglMakeCurrent failed (0x%x), dropping surface", eglGetError());
        releaseSurface();
        eglMakeCurrent(display, primaryDraw, primaryRead, context);
        return false;
    }

    EGLint surfaceWidth = 0;
    EGLint surfaceHeight = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &surfaceWidth);
    eglQuerySurface(display, surface, EGL_HEIGHT, &surfaceHeight);
    width = surfaceWidth > 0 ? static_cast<unsigned>(surfaceWidth) : 0;
    height = surfaceHeight > 0 ? static_cast<unsigned>(surfaceHeight) : 0;

    if (width == 0 || height == 0) {
        eglMakeCurrent(display, primaryDraw, primaryRead, context);
        primaryDraw = EGL_NO_SURFACE;
        primaryRead = EGL_NO_SURFACE;
        return false;
    }

    return true;
}

void SecondaryOutput::endFrame() {
    if (surface != EGL_NO_SURFACE && !eglSwapBuffers(display, surface)) {
        LOGE("Secondary output: eglSwapBuffers failed (0x%x), dropping surface", eglGetError());
        releaseSurface();
    }
    eglMakeCurrent(display, primaryDraw, primaryRead, context);
    primaryDraw = EGL_NO_SURFACE;
    primaryRead = EGL_NO_SURFACE;
}

void SecondaryOutput::releaseSurface() {
    if (surface != EGL_NO_SURFACE && display != EGL_NO_DISPLAY) {
        eglDestroySurface(display, surface);
    }
    surface = EGL_NO_SURFACE;
    width = 0;
    height = 0;
}

void SecondaryOutput::releaseWindow() {
    if (window != nullptr) {
        ANativeWindow_release(window);
        window = nullptr;
    }
}

void SecondaryOutput::destroy() {
    releaseSurface();
    releaseWindow();
    std::lock_guard<std::mutex> lock(windowMutex);
    if (pendingWindow != nullptr) {
        ANativeWindow_release(pendingWindow);
        pendingWindow = nullptr;
    }
    windowChanged = false;
}

}
