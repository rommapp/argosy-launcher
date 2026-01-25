# LibretroDroid Black/Grey Screen Debug Log

## Problem
Built-in libretro emulator shows black (SNES) or grey (NES) screen with no audio. Native library reports initialization success but nothing actually renders.

## Symptoms
- Native library loads: OK
- GL context created: OpenGL ES 3.2, Adreno 830
- Audio initializes: 48kHz
- Game "starts": 60fps reported
- Screen output: BLACK/GREY
- Audio output: NONE
- No crash or error logs

## ROOT CAUSE (SOLVED)
The issue was `RENDERMODE_WHEN_DIRTY` vs `RENDERMODE_CONTINUOUSLY`.

GLSurfaceView has two render modes:
- `RENDERMODE_CONTINUOUSLY`: Calls `onDrawFrame()` at vsync rate (~60/120fps)
- `RENDERMODE_WHEN_DIRTY`: Only calls `onDrawFrame()` when `requestRender()` is called

Libretro cores expect the host to call `retro_run()` continuously at the target frame rate. With `RENDERMODE_WHEN_DIRTY`, `onDrawFrame()` was only called ONCE (on surface creation), so only ONE frame was ever rendered.

The `blackFrameInsertion` property had logic that set `renderMode = RENDERMODE_WHEN_DIRTY` when BFI was disabled (the default). When `LibretroActivity` set `retroView.blackFrameInsertion = false`, this triggered the property observer which reset renderMode.

## Fix Applied
1. Added `renderMode = RENDERMODE_CONTINUOUSLY` in GLRetroView's `init` block
2. Removed the renderMode override from `blackFrameInsertion` property observer

```kotlin
// In init block:
renderMode = RENDERMODE_CONTINUOUSLY

// In blackFrameInsertion property - removed this line:
// renderMode = if (value) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
```

## Previous Hypotheses (Incorrect)
### Hypothesis 1: Lifecycle Observer Not Firing
`isEmulationReady` flag in GLRetroView stays `false`, so `onDrawFrame()` never calls `LibretroDroid.step()`.

**Result**: Lifecycle observer WAS firing correctly. The issue was renderMode, not lifecycle.

## Attempts Made

### 1. ProGuard Rules for Lifecycle Observers
**Files:** `libretrodroid/consumer-rules.pro`, `app/proguard-rules.pro`
**Change:** Added `-keepclassmembers` for `@OnLifecycleEvent` annotated methods
**Result:** Not the issue - DEBUG builds (no R8) have same problem

### 2. Manual Resume Check in initializeCore()
**File:** `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`
**Change:** Check `lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED)` and call `observer.manualResume()`
**Result:** ANR - was calling LibretroDroid.resume() on UI thread

### 3. Queue Manual Resume to GL Thread
**File:** `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`
**Change:** Wrap `observer.manualResume()` in `queueEvent { }`
**Result:** Caused double-resume (lifecycle event fires synchronously when observer added to already-RESUMED lifecycle)

### 4. Remove Manual Resume Check (Lifecycle Event Fires Synchronously)
**File:** `libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt`
**Change:** Removed manual resume check - lifecycle event fires automatically when observer is added
**Result:** PENDING TEST

### 5. Added LOGI Logging to Native Code
**File:** `libretrodroid/src/main/cpp/libretrodroid.cpp`
**Changes:**
- `resume()` now logs with LOGI instead of LOGD
- `step()` now logs with LOGI instead of LOGD
- `callback_hw_video_refresh()` now logs: `VIDEO CALLBACK: data=%p width=%u height=%u pitch=%zu`
**Result:** SUCCESS - Video callback IS being called with valid data (256x224 NES resolution)

## Key Finding
The core IS producing video frames. The VIDEO CALLBACK receives:
- data=0xb40000797d79f380 (valid pointer)
- width=256, height=224 (correct NES resolution)
- pitch=512

**The bug is in the rendering pipeline AFTER the callback**, not in core execution or callback delivery.

### 6. Next: Trace Rendering Pipeline
Need to add logging to:
- `handleVideoRefresh()` - does it call `video->onNewFrame()`?
- `video->onNewFrame()` - does it process the data?
- `video->renderFrame()` - is it being called?
- Shader compilation - any errors?

## Code Changes Made

### GLRetroView.kt - RenderLifecycleObserver
```kotlin
private inner class RenderLifecycleObserver : LifecycleObserver {
    fun manualResume() = catchExceptions {
        Log.d(TAG_LOG, "RenderLifecycleObserver.manualResume() called")
        LibretroDroid.resume()
        onResume()
        isEmulationReady = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun resume() = catchExceptions {
        Log.d(TAG_LOG, "RenderLifecycleObserver.resume() lifecycle event")
        if (!isEmulationReady) {
            LibretroDroid.resume()
            onResume()
            isEmulationReady = true
        }
    }
    // ... pause unchanged
}
```

### GLRetroView.kt - initializeCore()
```kotlin
KtUtils.runOnUIThread {
    val observer = RenderLifecycleObserver()
    lifecycle?.addObserver(observer)
    if (lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
        Log.d(TAG_LOG, "Lifecycle already RESUMED, manually triggering resume")
        queueEvent { observer.manualResume() }
    }
}
```

### consumer-rules.pro
```proguard
# Keep ALL libretrodroid classes - heavily JNI-dependent
-keep class com.swordfish.libretrodroid.** { *; }
-keepclassmembers class com.swordfish.libretrodroid.** { *; }

# Keep lifecycle observer methods (uses reflection to find annotated methods)
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}

# Keep the method names in inner classes that implement LifecycleObserver
-keepclassmembers class com.swordfish.libretrodroid.GLRetroView$* {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}
```

## Key Log Lines to Look For
- `"Lifecycle already RESUMED, manually triggering resume"` - manual check fired
- `"RenderLifecycleObserver.manualResume() called"` - manual resume executed
- `"RenderLifecycleObserver.resume() lifecycle event"` - normal lifecycle fired
- `"Performing libretrodroid resume"` - native resume called
- `"Stepping into retro_run()"` - frames actually running

## User Context
- User says this was fixed in a previous session but not committed
- User mentioned "imports/refs being pruned" as the original issue
- This affects BOTH debug and release builds
- Device: Odin3 (Adreno 830)

## Next Steps to Try
1. Verify queueEvent fix works
2. If still broken, add more logging to trace exact execution path
3. Check if the issue is actually in native code (video callback not firing)
4. Compare against original libretrodroid library to see what might have changed
