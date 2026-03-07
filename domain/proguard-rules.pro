# Proguard rules for the domain module

# Keep all repository interfaces (implemented by data layer with Dagger)
-keep interface com.chakir.plexhubtv.domain.repository.** { *; }

# Keep all use cases (injected by Dagger)
-keep class com.chakir.plexhubtv.domain.usecase.** { *; }

# Keep all source handler interfaces (implemented by data layer with @IntoSet)
-keep interface com.chakir.plexhubtv.domain.source.** { *; }
