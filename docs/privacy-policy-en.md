# Privacy Policy - PlexHubTV

Last updated: February 19, 2026

PlexHubTV is a third-party client for Plex Media Server, designed for Android TV. This app is developed and published by an independent developer and is **not affiliated with Plex Inc.**

## Data We Collect

### 1. Plex Authentication Data
- Plex authentication token
- Plex account ID (from Plex API)
- Plex server identifiers (server ID, IP or hostname)

**Purpose**:
To allow the app to connect to your Plex account and access your media libraries.

### 2. Local Viewing Data
- Media items you play (movie, show, episode)
- Playback progress (position, duration)
- Favorites, watchlist, history

**Storage**:
All this data is stored **locally on your device** in an encrypted database (Room + EncryptedSharedPreferences).

**Purpose**:
To provide features like "Continue Watching", history, recommendations, and offline downloads.

### 3. Crash and Performance Data
If you enable crash reporting (Crashlytics):

- Device model, OS version
- App version and build type
- Stack traces and error details
- Anonymous usage patterns (screen names, events)

**Provider**:
Google Firebase (Crashlytics, Analytics, Performance).

**Purpose**:
To detect crashes and improve app stability and performance.

### 4. Network Requests

PlexHubTV communicates with:
- **Plex Inc.** (plex.tv, Plex servers you configure)
- **The Movie Database (TMDb)** for metadata and ratings
- **OMDb API** for IMDb ratings

These services have their own privacy policies:
- Plex: https://www.plex.tv/about/privacy-legal/
- TMDb: https://www.themoviedb.org/privacy-policy
- OMDb: http://www.omdbapi.com/legal.htm

## Data Storage and Retention

- All sensitive data (Plex token, API keys) is stored locally using Android's encrypted storage (EncryptedSharedPreferences).
- No personal data is sent to any server owned by PlexHubTV.
  **We do not operate any backend server.**
- You can clear all local data by uninstalling the app or using your device's "Clear data" option.

## Children's Privacy

PlexHubTV is not directed at children under 13.
Any content restrictions depend on your Plex server configuration and Plex profiles.

## Your Rights

Because PlexHubTV does not operate any server and does not store your data remotely, your rights mainly concern:

- **On-device data**: you can delete all app data from your Android TV device.
- **Plex account data**: managed by Plex Inc. via https://plex.tv

You can revoke PlexHubTV's access to your Plex account by removing the app from your Plex authorized devices.

## Contact

If you have any questions about this Privacy Policy, you can contact the developer at:

**Email**: chakir.elarram@gmail.com
