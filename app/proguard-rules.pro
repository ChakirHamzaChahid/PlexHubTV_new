# ============================================================================
# PlexHubTV ProGuard Rules
# ============================================================================

# --- Stack Traces ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Annotations ---
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ============================================================================
# Retrofit
# ============================================================================
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep interface com.chakir.plexhubtv.core.network.PlexApiService { *; }

# ============================================================================
# Gson
# ============================================================================
-keep class com.chakir.plexhubtv.core.network.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
}

# ============================================================================
# Room
# ============================================================================
-keep class com.chakir.plexhubtv.core.database.** { *; }

# ============================================================================
# Hilt / Dagger
# ============================================================================
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ============================================================================
# Media3 / ExoPlayer
# ============================================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================================
# MPV (libmpv)
# ============================================================================
-keep class dev.jdtech.mpv.** { *; }
-dontwarn dev.jdtech.mpv.**

# ============================================================================
# OkHttp
# ============================================================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# ============================================================================
# Kotlinx Serialization
# ============================================================================
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ============================================================================
# Coil
# ============================================================================
-dontwarn coil.**

# ============================================================================
# FFmpeg decoder (Jellyfin)
# ============================================================================
-keep class org.jellyfin.media3.** { *; }
-dontwarn org.jellyfin.media3.**

# ============================================================================
# ASS/SSA subtitle renderer
# ============================================================================
-keep class io.github.peerless2012.** { *; }
-dontwarn io.github.peerless2012.**

# ============================================================================
# AndroidX TV
# ============================================================================
-keep class androidx.tv.** { *; }
-dontwarn androidx.tv.**
-keep class androidx.leanback.** { *; }
-dontwarn androidx.leanback.**

# ============================================================================
# DataStore
# ============================================================================
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ============================================================================
# Coroutines
# ============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ============================================================================
# Enum classes
# ============================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================================
# Parcelable
# ============================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
