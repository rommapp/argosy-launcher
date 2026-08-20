#ifndef LIBRETRODROID_SECONDARYOUTPUT_H
#define LIBRETRODROID_SECONDARYOUTPUT_H

#include <EGL/egl.h>
#include <android/native_window.h>
#include <atomic>
#include <mutex>

namespace libretrodroid {

/**
 * A second window for the frame the core already produced, presented on another display.
 *
 * The window it draws into belongs to a Presentation, which the system tears down whenever that
 * display sleeps or the activity leaves. An EGLSurface outlives its ANativeWindow silently: swaps
 * keep returning without drawing and the second screen freezes on the last frame. So the surface
 * is derived from the window every time the window changes, never cached across one, and any EGL
 * error drops it for rebuild on the next frame rather than being retried against a dead handle.
 *
 * Everything except [setWindow] runs on the GL thread that owns the emulator's context; the
 * secondary surface is made current only for the duration of one draw.
 */
class SecondaryOutput {
public:
    ~SecondaryOutput();

    /** Hands over the Presentation's window, or nullptr when it goes away. Any thread. */
    void setWindow(ANativeWindow* window);

    bool hasWindow() const { return pendingWindow != nullptr || window != nullptr; }

    /**
     * Makes the secondary surface current, building it if the window changed. Returns false when
     * there is nothing to draw into, in which case the caller must not draw or restore.
     */
    bool beginFrame();

    /** Presents the frame and restores the primary surface. Drops the surface if the swap fails. */
    void endFrame();

    unsigned getWidth() const { return width; }

    unsigned getHeight() const { return height; }

    /** Releases GL and window resources. GL thread. */
    void destroy();

private:
    void adoptPendingWindow();
    void releaseSurface();
    void releaseWindow();

    std::mutex windowMutex;
    ANativeWindow* pendingWindow = nullptr;
    std::atomic<bool> windowChanged { false };

    ANativeWindow* window = nullptr;
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    EGLSurface primaryDraw = EGL_NO_SURFACE;
    EGLSurface primaryRead = EGL_NO_SURFACE;
    unsigned width = 0;
    unsigned height = 0;
};

}

#endif //LIBRETRODROID_SECONDARYOUTPUT_H
