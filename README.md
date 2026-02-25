# PlexHubTV

A modern Android TV client for Plex Media Server, built with Jetpack Compose and Material 3 Design.

## Features

### Core Features

- **Netflix-inspired UI**: Clean, modern interface optimized for TV viewing
- **Multi-server Support**: Connect to multiple Plex servers simultaneously
- **Offline Playback**: Download and watch content without internet connection
- **Cross-server Metadata Enrichment**: Automatically enhance content with metadata from multiple servers
- **Dual Player Support**: ExoPlayer (primary) with MPV fallback
- **Profile Management**: Multiple user profiles with personalized content
- **Smart Search**: Fast, unified search across all connected servers

### Android TV Integration

PlexHubTV integrates with the Android TV launcher to provide quick access to your content:

- **Continue Watching Channel**: Shows up to 15 items from your On Deck/Continue Watching list directly in the Android TV launcher
- **Watch Next Integration**: Single-item card for the most recent content you're watching
- **Automatic Sync**: Updates every 3 hours via background worker, plus immediate updates after playback
- **Deep Linking**: Click any item in the launcher to jump directly to playback
- **Settings Control**: Enable/disable TV Channels integration in Settings > TV Channels

### Library Management

- **Automatic Sync**: Background workers keep your local library up-to-date
- **Smart Caching**: Adaptive image caching optimized for RAM usage
- **Pagination Support**: Efficient handling of large libraries
- **Offline-first Strategy**: Local-first data access with network fallback

## Architecture

PlexHubTV follows Clean Architecture principles with a multi-module structure:

- **:app** - Presentation layer (UI, ViewModels, Workers)
- **:domain** - Business logic and use cases
- **:data** - Repositories, mappers, and data sources
- **:core** - Shared modules (model, network, database, UI components)

For detailed architecture documentation, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose for TV, Material 3
- **Architecture**: Clean Architecture, MVVM + MVI
- **Dependency Injection**: Hilt (Dagger)
- **Database**: Room with WAL mode
- **Networking**: Retrofit, OkHttp
- **Player**: ExoPlayer + MPV
- **Images**: Coil
- **Background Tasks**: WorkManager
- **Testing**: JUnit, MockK, Truth, Turbine

## Requirements

- Android TV device running Android 5.0 (API 21) or higher
- Active Plex Media Server instance
- Network connection (for initial setup and streaming)

## Building

```bash
./gradlew assembleDebug
```

For release builds:

```bash
./gradlew assembleRelease
```

## License

[Add your license information here]

## Privacy

See our privacy policy:
- [English](docs/privacy-policy-en.md)
- [Français](docs/privacy-policy-fr.md)
