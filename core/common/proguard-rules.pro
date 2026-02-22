# Proguard rules for core:common

# ============================================================================
# Keep Dagger/Hilt qualifier annotations
# ============================================================================

# Keep all Dagger/Hilt qualifier annotations (critical for dependency injection)
-keep @interface com.chakir.plexhubtv.core.di.IoDispatcher
-keep @interface com.chakir.plexhubtv.core.di.DefaultDispatcher
-keep @interface com.chakir.plexhubtv.core.di.MainDispatcher
-keep @interface com.chakir.plexhubtv.core.di.ApplicationScope

# Keep all classes/interfaces annotated with these qualifiers
-keepclassmembers class * {
    @com.chakir.plexhubtv.core.di.IoDispatcher *;
    @com.chakir.plexhubtv.core.di.DefaultDispatcher *;
    @com.chakir.plexhubtv.core.di.MainDispatcher *;
    @com.chakir.plexhubtv.core.di.ApplicationScope *;
}

# Preserve annotation parameter values
-keepattributes *Annotation*,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
