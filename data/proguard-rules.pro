# ============================================================================
# Data Module ProGuard Rules
# ============================================================================

# Keep all repository implementations (injected by Dagger)
-keep class com.chakir.plexhubtv.data.repository.** { *; }

# Keep all mappers (injected by Dagger)
-keep class com.chakir.plexhubtv.data.mapper.** { *; }

# Keep all use cases (injected by Dagger)
-keep class com.chakir.plexhubtv.data.usecase.** { *; }

# Keep all utilities (injected by Dagger)
-keep class com.chakir.plexhubtv.data.util.** { *; }

# Keep all cache implementations (injected by Dagger)
-keep class com.chakir.plexhubtv.data.cache.** { *; }

# Keep MediaUrlResolver (in core.util, injected by Dagger)
-keep interface com.chakir.plexhubtv.core.util.MediaUrlResolver { *; }
-keep class com.chakir.plexhubtv.core.util.DefaultMediaUrlResolver { *; }
