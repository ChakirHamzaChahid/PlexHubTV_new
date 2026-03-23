# Consumer proguard rules for the core:network module

# Keep Jellyfin API service and DTOs (Retrofit + Gson)
-keep class com.chakir.plexhubtv.core.network.jellyfin.** { *; }
