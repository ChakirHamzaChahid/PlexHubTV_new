# Android TV Channels Integration - Design Document

**Date:** 2026-02-20
**Feature:** Android TV Channels for Continue Watching
**Status:** Approved Design

---

## Executive Summary

Add Android TV Channels integration to PlexHubTV, exposing a "Continue Watching" channel in the Android TV launcher. This complements the existing Watch Next implementation by providing a full channel row with multiple items, while keeping Watch Next for the most recent single item.

---

## Requirements

### Functional Requirements

1. **TV Channel "Continue Watching"**
   - Display up to 15 media items from On Deck
   - Show as a channel row in Android TV launcher
   - Deep link to direct playback when clicked

2. **Dual Integration**
   - Keep existing `WatchNextHelper` for single-item Watch Next
   - Add new TV Channel for multi-item Continue Watching row
   - Both systems work independently

3. **Update Strategy**
   - Immediate update after media playback
   - Periodic refresh every 3 hours via WorkManager
   - Ensure channels are always up-to-date

4. **User Control**
   - Enabled by default for better discoverability
   - Settings toggle to enable/disable channels
   - Disabling removes channel and stops worker

5. **Extensibility**
   - Architecture supports adding future channels (Recommended, Favorites, etc.)
   - Clean separation for easy extension

### Non-Functional Requirements

- No crashes if On Deck is empty
- Fail silently if ContentProvider permissions denied
- Performance: < 500ms for channel update
- Respect user privacy (no external analytics for channel interactions)

---

## Design Decisions

### Decision 1: Service Centralized Architecture

**Chosen Approach:** Centralized `TvChannelManager` with dedicated `ChannelSyncWorker`

**Rationale:**
- Clear separation of responsibilities (Watch Next ≠ Channels)
- Follows existing patterns (`LibrarySyncWorker`, repository pattern)
- Easy to extend with new channels
- Testable in isolation

**Alternatives Considered:**
- Extending `WatchNextHelper`: Rejected (violates SRP, mixes two Android APIs)
- Reactive Flow architecture: Rejected (too complex, potential over-sync)

### Decision 2: Delete-All + Insert-All Update Strategy

**Chosen Approach:** Remove all programs, then insert fresh ones

**Rationale:**
- Simple implementation (no diff logic needed)
- Avoids duplicate programs
- Acceptable performance for 15 items
- No state management complexity

**Alternative Considered:**
- Diff-based updates: Rejected (unnecessary complexity for small dataset)

### Decision 3: Reuse Existing Deep Links

**Chosen Approach:** Use existing `plexhub://play/{ratingKey}?serverId={serverId}` scheme

**Rationale:**
- Already implemented in `AndroidManifest.xml` (lines 48-53)
- Already handled in `MainActivity`
- No duplicate code needed
- Consistent with Watch Next implementation

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     PlexHubApplication                       │
│  onCreate() → Initialize Channel + Enqueue ChannelSyncWorker│
└────────────────────────┬────────────────────────────────────┘
                         │
         ┌───────────────┴───────────────┐
         │                               │
         ▼                               ▼
┌──────────────────┐           ┌──────────────────┐
│ TvChannelManager │           │ChannelSyncWorker │
│  (Singleton)     │◄──────────│  (Periodic 3h)   │
└────────┬─────────┘           └──────────────────┘
         │
         │ uses
         ▼
┌──────────────────┐
│ OnDeckRepository │
│  (existing)      │
└──────────────────┘
```

### New Components

#### 1. TvChannelManager (`core/common/util/TvChannelManager.kt`)

```kotlin
@Singleton
class TvChannelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onDeckRepository: OnDeckRepository,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        const val CHANNEL_NAME = "PlexHubTV - Continue Watching"
        const val MAX_PROGRAMS = 15
    }

    /**
     * Creates the TV Channel if it doesn't exist
     * @return Channel ID or null if creation failed
     */
    suspend fun createChannelIfNeeded(): Long?

    /**
     * Updates the Continue Watching channel with latest On Deck items
     * - Fetches from OnDeckRepository
     * - Deletes old programs
     * - Inserts new programs
     */
    suspend fun updateContinueWatching()

    /**
     * Deletes the channel and all its programs
     */
    suspend fun deleteChannel()

    private suspend fun findExistingChannelId(): Long?
    private fun createProgram(media: MediaItem): PreviewProgram
}
```

**Key Responsibilities:**
- Manage channel lifecycle (create/delete)
- Convert `MediaItem` → `PreviewProgram`
- Interact with `TvContractCompat` APIs
- Check `isTvChannelsEnabled` before operations

#### 2. ChannelSyncWorker (`app/work/ChannelSyncWorker.kt`)

```kotlin
@HiltWorker
class ChannelSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tvChannelManager: TvChannelManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Sync skipped (disabled in settings)")
            return Result.success()
        }

        try {
            tvChannelManager.updateContinueWatching()
            Timber.i("TV Channel: Periodic sync completed")
            return Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Periodic sync failed")
            return Result.success() // Fail silently
        }
    }
}
```

**Scheduling:**
```kotlin
// In PlexHubApplication.onCreate()
val channelSyncRequest = PeriodicWorkRequestBuilder<ChannelSyncWorker>(
    repeatInterval = 3,
    repeatIntervalTimeUnit = TimeUnit.HOURS
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).build()

WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "ChannelSync",
    ExistingPeriodicWorkPolicy.KEEP,
    channelSyncRequest
)
```

### Modified Components

#### 1. SettingsDataStore

**Add:**
```kotlin
private val TV_CHANNELS_ENABLED = booleanPreferencesKey("tv_channels_enabled")

val isTvChannelsEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
    preferences[TV_CHANNELS_ENABLED] ?: true // Default: enabled
}

suspend fun setTvChannelsEnabled(enabled: Boolean) {
    dataStore.edit { preferences ->
        preferences[TV_CHANNELS_ENABLED] = enabled
    }
}
```

#### 2. PlayerScreen

**Add hook in `onDispose`:**
```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Existing WatchNextHelper call
        watchNextHelper.updateWatchNext(...)

        // NEW: Update TV Channel
        viewModelScope.launch {
            if (settingsDataStore.isTvChannelsEnabled.first()) {
                tvChannelManager.updateContinueWatching()
            }
        }
    }
}
```

#### 3. SettingsScreen

**Add toggle:**
```kotlin
SwitchPreference(
    title = stringResource(R.string.settings_tv_channels_title),
    summary = stringResource(R.string.settings_tv_channels_summary),
    checked = isTvChannelsEnabled,
    onCheckedChange = { enabled ->
        viewModel.setTvChannelsEnabled(enabled)
        if (!enabled) {
            // Delete channel immediately
            tvChannelManager.deleteChannel()
        } else {
            // Re-create and sync
            tvChannelManager.createChannelIfNeeded()
            tvChannelManager.updateContinueWatching()
        }
    }
)
```

---

## Data Flow

### Update Flow

```
OnDeckRepository.getUnifiedOnDeck()
        ↓
   Flow<List<MediaItem>>
        ↓
TvChannelManager.updateContinueWatching()
        ↓
   1. Check isTvChannelsEnabled
   2. Collect first 15 MediaItems
   3. Find existing channelId
   4. Delete all old programs
   5. Map MediaItem → PreviewProgram
   6. Insert new programs
        ↓
   Android TV Launcher (displays channel)
```

### MediaItem → PreviewProgram Mapping

```kotlin
private fun createProgram(media: MediaItem, channelId: Long): PreviewProgram {
    val displayTitle = if (media.type == MediaType.Episode) {
        "${media.grandparentTitle} - S${media.seasonIndex}E${media.episodeIndex} - ${media.title}"
    } else {
        media.title
    }

    return PreviewProgram.Builder()
        .setChannelId(channelId)
        .setType(
            when (media.type) {
                MediaType.Movie -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                MediaType.Episode -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                else -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
            }
        )
        .setTitle(displayTitle)
        .setDescription(media.summary ?: "")
        .setPosterArtUri(Uri.parse(media.thumbUrl))
        .setIntentUri(Uri.parse("plexhub://play/${media.ratingKey}?serverId=${media.serverId}"))
        .setInternalProviderId("${media.serverId}_${media.ratingKey}")
        .setDurationMillis(media.duration?.toInt() ?: 0)
        .setLastPlaybackPositionMillis(media.viewOffset?.toInt() ?: 0)
        .build()
}
```

---

## Error Handling

### Failure Scenarios

| Scenario | Handling Strategy | Implementation |
|----------|------------------|----------------|
| Empty On Deck list | Skip channel creation/update | `if (items.isEmpty()) return` |
| ContentProvider permissions denied | Log warning, fail silently | `try-catch` around all `contentResolver` calls |
| Worker timeout/crash | Return `Result.success()` | Same pattern as `LibrarySyncWorker` |
| Deep link to deleted media | Show error toast in MainActivity | Existing error handling (no changes) |
| Channel manually deleted by user | Auto-recreate on next sync | `createChannelIfNeeded()` checks existence |
| Settings toggle during update | Check flag at start of operation | `if (!isTvChannelsEnabled.first()) return` |

### Logging Strategy

**Prefix all logs with `"TV Channel:"`** for easy filtering:

```kotlin
Timber.d("TV Channel: Creating channel...")
Timber.i("TV Channel: Updated with ${programs.size} programs")
Timber.w("TV Channel: No content available, skipping update")
Timber.e(e, "TV Channel: Failed to update")
```

**Log Levels:**
- `DEBUG`: Operation start/steps
- `INFO`: Successful completions
- `WARN`: Skipped operations (disabled, empty)
- `ERROR`: Exceptions (with stacktrace)

---

## Testing Strategy

### Unit Tests

**TvChannelManagerTest:**
```kotlin
@Test
fun `createChannelIfNeeded creates channel only once`() {
    // Mock contentResolver.insert() to return channelId
    // Call twice, verify insert() called only once
}

@Test
fun `updateContinueWatching skips when empty list`() {
    // Mock onDeckRepository to return emptyList()
    // Verify no contentResolver.insert() calls
}

@Test
fun `deleteChannel removes all programs`() {
    // Mock contentResolver.delete()
    // Verify correct URI and selection args
}
```

**ChannelSyncWorkerTest:**
```kotlin
@Test
fun `worker skips when channels disabled`() {
    // Mock settingsDataStore.isTvChannelsEnabled = false
    // Verify tvChannelManager not called
}

@Test
fun `worker returns success even on error`() {
    // Mock tvChannelManager to throw exception
    // Verify Result.success() returned
}
```

### Manual Testing (Android TV Device)

**Test Cases:**

1. ✅ **Fresh Install**
   - Install app → Launcher shows "PlexHubTV - Continue Watching" channel
   - Channel contains On Deck items (if available)

2. ✅ **After Playback**
   - Watch 5 minutes of a movie/episode
   - Return to launcher → Item appears in channel immediately

3. ✅ **Settings Toggle**
   - Disable "TV Channels" in Settings → Channel disappears from launcher
   - Re-enable → Channel reappears with updated content

4. ✅ **Empty State**
   - Clear all watch history → Channel hidden or empty
   - No crashes, no visible errors

5. ✅ **Deep Link**
   - Click on channel item → App opens to player
   - Playback starts at saved position (viewOffset)

6. ✅ **Watch Next Non-Regression**
   - Verify single-item Watch Next still works
   - Both systems coexist without conflicts

---

## Acceptance Criteria

- ✅ Channel "PlexHubTV - Continue Watching" visible in Android TV launcher
- ✅ Channel displays up to 15 items from On Deck
- ✅ Items match actual watch progress (titles, thumbnails, positions)
- ✅ Clicking item launches PlexHubTV and starts playback
- ✅ Settings toggle enables/disables channel functionality
- ✅ No crashes when On Deck is empty
- ✅ WatchNextHelper continues to work (no regression)
- ✅ Periodic sync updates channel every 3 hours
- ✅ Immediate update after playback session ends

---

## Future Extensions

This architecture supports adding additional channels:

**Possible Future Channels:**
- "Recommended" (based on watch history + ratings)
- "Recently Added" (new content from Plex servers)
- "Favorites" (synced from Plex Watchlist)
- "Top Rated" (highest-rated content)

**Implementation Pattern:**
1. Add method to `TvChannelManager`: `updateRecommendedChannel()`
2. Add new worker or extend existing one
3. Add settings toggle for new channel
4. No changes to existing Continue Watching logic

---

## Implementation Notes

### Dependencies

Already available:
- `androidx.tvprovider:tvprovider:1.0.0` (line 151 of app/build.gradle.kts)

No new dependencies needed.

### Permissions

Already declared in `AndroidManifest.xml`:
- `android.permission.INTERNET` (line 12)
- `android.permission.FOREGROUND_SERVICE` (line 13)

No new permissions needed. TV Channels use ContentProvider (no special permission required).

### Deep Link Handling

Already implemented in `AndroidManifest.xml` (lines 48-53):
```xml
<intent-filter>
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="plexhub" android:host="play" />
</intent-filter>
```

No changes needed to deep link handling.

---

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|-----------|
| User disables channels via launcher (not Settings) | Channel recreated on next sync | Acceptable behavior; respects Settings toggle |
| ContentProvider rate limiting | Updates fail silently | Periodic sync acts as backup |
| OnDeckRepository network failures | Channel shows stale data | Acceptable; shows cached data until next successful sync |
| Channel clutter (too many items) | Poor UX | Limit to 15 items (already decided) |

---

## Timeline Estimate

**Implementation:** 1-2 days
- Day 1: `TvChannelManager` + `ChannelSyncWorker` + Settings
- Day 2: Testing + bug fixes + polish

**Testing:** 0.5 days
- Unit tests + manual TV testing

**Total:** ~2-3 days

---

## References

- [Android TV Channels Documentation](https://developer.android.com/training/tv/discovery/recommendations-channel)
- [TvContractCompat API](https://developer.android.com/reference/androidx/tvprovider/media/tv/TvContractCompat)
- Existing implementation: `WatchNextHelper.kt` (Watch Next integration)
- Existing pattern: `LibrarySyncWorker.kt` (Worker pattern reference)
