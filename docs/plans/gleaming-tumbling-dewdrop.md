# Plan: Backend Xtream Sync Trigger + Health Check Buttons

## Context

The Settings screen's "PlexHub Backend" section currently has server management (add/remove) and a "Sync from Backend" button that downloads media FROM the backend INTO the app. The user wants two new buttons:

1. **Trigger Backend Xtream Sync** — tells the backend server to update its own DB with new Xtream content (`POST /api/sync/xtream/all`)
2. **Check Backend Health** — displays full health info from the backend (`GET /api/health`: status, version, accounts, totalMedia, enrichedMedia, brokenStreams, lastSyncAt)

Both API endpoints already exist in `BackendApiService.kt` (`triggerSyncAll()`, `getHealth()`). The repository method `syncAll(backendId)` already exists. We mainly need UI + wiring + extending the health info model.

---

## Changes

### 1. Extend `BackendConnectionInfo` domain model
**File**: [BackendConnectionInfo.kt](core/model/src/main/java/com/chakir/plexhubtv/core/model/BackendConnectionInfo.kt)

Add missing fields from BackendHealthResponse:
```kotlin
data class BackendConnectionInfo(
    val status: String = "ok",
    val totalMedia: Int,
    val enrichedMedia: Int,
    val brokenStreams: Int = 0,
    val accounts: Int = 0,
    val version: String,
    val lastSyncAt: Long? = null,
)
```

### 2. Update `BackendRepositoryImpl.testConnection()` mapping
**File**: [BackendRepositoryImpl.kt:80-91](data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt#L80-L91)

Map all fields from `BackendHealthResponse`:
```kotlin
BackendConnectionInfo(
    status = health.status,
    totalMedia = health.totalMedia,
    enrichedMedia = health.enrichedMedia,
    brokenStreams = health.brokenStreams,
    accounts = health.accounts,
    version = health.version,
    lastSyncAt = health.lastSyncAt,
)
```

### 3. Add `getHealthInfo(backendId)` to BackendRepository
**File**: [BackendRepository.kt](domain/src/main/java/com/chakir/plexhubtv/domain/repository/BackendRepository.kt)

New method:
```kotlin
suspend fun getHealthInfo(backendId: String): Result<BackendConnectionInfo>
```

**File**: [BackendRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt)

Implementation — resolve backend by ID, call `getHealth()`, map to `BackendConnectionInfo`.

### 4. Add new SettingsActions
**File**: [SettingsUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt)

New actions:
```kotlin
data object TriggerBackendXtreamSync : SettingsAction
data object CheckBackendHealth : SettingsAction
```

New state fields:
```kotlin
val isTriggeringBackendSync: Boolean = false,
val backendTriggerSyncMessage: String? = null,
val isCheckingBackendHealth: Boolean = false,
val backendHealthMessage: String? = null,
```

### 5. Add ViewModel handlers
**File**: [SettingsViewModel.kt:414-441](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt#L414-L441)

**TriggerBackendXtreamSync**: Iterate active backend servers, call `backendRepository.syncAll(server.id)` for each, collect jobIds, show result message. Pattern matches existing `SyncBackend` handler.

**CheckBackendHealth**: Iterate active backend servers, call `backendRepository.getHealthInfo(server.id)` for each, format a multi-line status message showing all health fields. Auto-clear after 10s (longer than usual since it's informational).

### 6. Add UI buttons in SettingsScreen
**File**: [SettingsScreen.kt:503-519](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt#L503-L519)

Place after the existing "Sync from Backend" button (line 519), before the config message (line 522):

- **"Trigger Xtream Sync"** SettingsTile with `Icons.Filled.CloudSync` (or `Sync`), loading spinner, follows exact same pattern as existing sync buttons
- **"Backend Status"** SettingsTile with `Icons.Filled.Info`, loading spinner, shows formatted health info as subtitle text

### 7. Add string resources
**File**: [strings.xml](app/src/main/res/values/strings.xml)

```xml
<string name="settings_backend_trigger_sync">Trigger Xtream Sync</string>
<string name="settings_backend_trigger_sync_subtitle">Update backend DB with new Xtream content</string>
<string name="settings_backend_health">Backend Status</string>
<string name="settings_backend_health_subtitle">Check backend server health</string>
```

---

## Verification

1. Build: `./gradlew :app:assembleDebug`
2. Visual: Open Settings, scroll to "PlexHub Backend" section, verify 2 new buttons appear after "Sync from Backend"
3. Functional: Click "Trigger Xtream Sync" — spinner shows, then success message with jobId(s)
4. Functional: Click "Backend Status" — spinner shows, then formatted health info (status, version, accounts, media counts, broken streams, last sync time)
