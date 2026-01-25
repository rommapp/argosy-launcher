# Keep ALL libretrodroid classes - heavily JNI-dependent
-keep class com.swordfish.libretrodroid.** { *; }
-keepclassmembers class com.swordfish.libretrodroid.** { *; }

# Keep lifecycle observer methods (uses reflection to find annotated methods)
# Without this, R8 may rename methods and break lifecycle event delivery
-keepclassmembers class * implements androidx.lifecycle.LifecycleObserver {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}

# Keep the method names in inner classes that implement LifecycleObserver
-keepclassmembers class com.swordfish.libretrodroid.GLRetroView$* {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}
