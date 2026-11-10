# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep class * extends com.squareup.moshi.JsonAdapter
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
-keep class **JsonAdapter {
    <init>(...);
}
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers enum * { <fields>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# JavaSteam - prevent R8 from reordering/stripping lateinit field initialization
-keep class in.dragonbra.javasteam.** { *; }
-keepclassmembers class in.dragonbra.javasteam.** { *; }

# zstd-jni - native code accesses Java fields by name via JNI
-keep class com.github.luben.zstd.** { *; }

# JNA - native code reads Pointer.peer and Structure fields by name; lazysodium binds through JNA proxies
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
-keep class com.goterl.lazysodium.** { *; }

# SpongyCastle - loaded via reflection by JavaSteam's CryptoHelper for AES/ECB depot decryption
-keep class org.spongycastle.** { *; }
-dontwarn org.spongycastle.**

# Result types whose class names appear in exported logs
-keepnames class com.nendo.argosy.data.repository.PreLaunchSyncResult$*
-keepnames class com.nendo.argosy.data.repository.SaveSyncResult$*
-keepnames class com.nendo.argosy.data.repository.SaveSyncOrchestrator$DiskCheck$*
-keepnames class com.nendo.argosy.data.repository.SaveCacheManager$CacheResult$*
-keepnames class com.nendo.argosy.data.emulator.SessionEndResult$*
-keepnames class com.nendo.argosy.data.emulator.SessionSaveOutcome$*
-keepnames class com.nendo.argosy.data.emulator.LaunchResult$*
-keepnames class com.nendo.argosy.data.steam.SteamDownloadState$*

# Shizuku instantiates the user service by name in its own process
-keep class com.nendo.argosy.util.ShizukuShellService { <init>(...); }

# Discord Social SDK (optional, may not be present)
-keep class com.discord.socialsdk.** { *; }
-keep class discordpp.** { *; }
-dontwarn com.discord.socialsdk.**
-dontwarn discordpp.**
