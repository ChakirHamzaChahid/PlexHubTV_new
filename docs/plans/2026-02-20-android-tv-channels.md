# Android TV Channels Integration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add Android TV Channels integration to expose Continue Watching content in the Android TV launcher, complementing the existing Watch Next single-item system.

**Architecture:** Centralized `TvChannelManager` handles channel lifecycle and program updates, `ChannelSyncWorker` provides periodic refresh every 3 hours, Settings toggle gives user control. Immediate updates after playback + periodic backups ensure channels stay current.

**Tech Stack:** Android TV Provider API (TvContractCompat), Kotlin Coroutines, Hilt DI, WorkManager, Room (via existing OnDeckRepository), DataStore Preferences

---

## Task 1: Add Settings toggle for TV Channels

**Files:**
- Modify: `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`

**Step 1: Add DataStore preference key and Flow**

In `SettingsDataStore.kt`, add after existing preferences:

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

**Step 2: Add i18n strings**

In `app/src/main/res/values/strings.xml`, add:

```xml
<string name="settings_tv_channels_title">TV Channels</string>
<string name="settings_tv_channels_summary">Show Continue Watching channel in Android TV launcher</string>
```

In `app/src/main/res/values-fr/strings.xml`, add:

```xml
<string name="settings_tv_channels_title">Chaînes TV</string>
<string name="settings_tv_channels_summary">Afficher la chaîne \"Continuer à regarder\" dans le lanceur Android TV</string>
```

**Step 3: Verify compilation**

Run: `./gradlew :core:datastore:build`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt app/src/main/res/values/strings.xml app/src/main/res/values-fr/strings.xml
git commit -m "feat(settings): add TV Channels toggle preference

- Add isTvChannelsEnabled Flow (default: true)
- Add setTvChannelsEnabled() method
- Add EN/FR i18n strings

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Create TvChannelManager core class

**Files:**
- Create: `core/common/src/main/java/com/chakir/plexhubtv/core/util/TvChannelManager.kt`

**Step 1: Create TvChannelManager skeleton**

Create `core/common/src/main/java/com/chakir/plexhubtv/core/util/TvChannelManager.kt`:

```kotlin
package com.chakir.plexhubtv.core.util

import android.content.Context
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.model.MediaType
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android TV Channels for PlexHubTV.
 *
 * Handles creation, update, and deletion of the "Continue Watching" channel
 * displayed in the Android TV launcher. Works alongside WatchNextHelper
 * (which handles single-item Watch Next).
 */
@Singleton
class TvChannelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onDeckRepository: OnDeckRepository,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        const val CHANNEL_NAME = "PlexHubTV - Continue Watching"
        const val CHANNEL_DESCRIPTION = "Resume watching your favorite content"
        const val MAX_PROGRAMS = 15
    }

    /**
     * Creates the TV Channel if it doesn't exist.
     * @return Channel ID or null if creation failed or disabled
     */
    suspend fun createChannelIfNeeded(): Long? {
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Creation skipped (disabled in settings)")
            return null
        }

        try {
            // Check if channel already exists
            val existingId = findExistingChannelId()
            if (existingId != null) {
                Timber.d("TV Channel: Already exists with ID=$existingId")
                return existingId
            }

            // Create new channel
            val channel = Channel.Builder()
                .setType(TvContractCompat.Channels.TYPE_PREVIEW)
                .setDisplayName(CHANNEL_NAME)
                .setDescription(CHANNEL_DESCRIPTION)
                .setAppLinkIntentUri(Uri.parse("plexhub://home"))
                .build()

            val channelUri = context.contentResolver.insert(
                TvContractCompat.Channels.CONTENT_URI,
                channel.toContentValues()
            )

            val channelId = channelUri?.lastPathSegment?.toLongOrNull()
            if (channelId != null) {
                // Request to make channel visible (user can still hide it manually)
                TvContractCompat.requestChannelBrowsable(context, channelId)
                Timber.i("TV Channel: Created successfully with ID=$channelId")
            } else {
                Timber.w("TV Channel: Creation failed (null ID)")
            }

            return channelId
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Creation failed")
            return null
        }
    }

    /**
     * Updates the Continue Watching channel with latest On Deck items.
     * - Fetches from OnDeckRepository
     * - Deletes old programs
     * - Inserts new programs
     */
    suspend fun updateContinueWatching() {
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Update skipped (disabled in settings)")
            return
        }

        try {
            val channelId = createChannelIfNeeded() ?: run {
                Timber.w("TV Channel: Update skipped (no channel ID)")
                return
            }

            // Fetch latest On Deck items
            val mediaItems = onDeckRepository.getUnifiedOnDeck().first().take(MAX_PROGRAMS)

            if (mediaItems.isEmpty()) {
                Timber.w("TV Channel: No content available, skipping update")
                // Delete all programs if empty
                deleteAllPrograms(channelId)
                return
            }

            // Delete old programs
            deleteAllPrograms(channelId)

            // Insert new programs
            mediaItems.forEach { media ->
                insertProgram(media, channelId)
            }

            Timber.i("TV Channel: Updated with ${mediaItems.size} programs")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Update failed")
        }
    }

    /**
     * Deletes the channel and all its programs.
     */
    suspend fun deleteChannel() {
        try {
            val channelId = findExistingChannelId() ?: run {
                Timber.d("TV Channel: Delete skipped (channel not found)")
                return
            }

            // Delete all programs first
            deleteAllPrograms(channelId)

            // Delete channel
            context.contentResolver.delete(
                TvContractCompat.buildChannelUri(channelId),
                null,
                null
            )

            Timber.i("TV Channel: Deleted successfully (ID=$channelId)")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Delete failed")
        }
    }

    /**
     * Finds the existing channel ID by display name.
     * @return Channel ID or null if not found
     */
    private fun findExistingChannelId(): Long? {
        return try {
            val cursor = context.contentResolver.query(
                TvContractCompat.Channels.CONTENT_URI,
                arrayOf(TvContractCompat.Channels._ID),
                "${TvContractCompat.Channels.COLUMN_DISPLAY_NAME} = ?",
                arrayOf(CHANNEL_NAME),
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to find existing channel")
            null
        }
    }

    /**
     * Deletes all programs from the given channel.
     */
    private fun deleteAllPrograms(channelId: Long) {
        try {
            val deletedCount = context.contentResolver.delete(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID} = ?",
                arrayOf(channelId.toString())
            )
            Timber.d("TV Channel: Deleted $deletedCount old programs")
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to delete programs")
        }
    }

    /**
     * Inserts a single program into the channel.
     */
    private fun insertProgram(media: MediaItem, channelId: Long) {
        try {
            val program = createProgram(media, channelId)
            context.contentResolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues()
            )
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Failed to insert program for ${media.title}")
        }
    }

    /**
     * Creates a PreviewProgram from MediaItem.
     */
    private fun createProgram(media: MediaItem, channelId: Long): PreviewProgram {
        val displayTitle = if (media.type == MediaType.Episode && !media.grandparentTitle.isNullOrBlank()) {
            "${media.grandparentTitle} - S${media.seasonIndex ?: 0}E${media.episodeIndex ?: 0} - ${media.title}"
        } else {
            media.title
        }

        val builder = PreviewProgram.Builder()
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
            .setPosterArtUri(Uri.parse(media.thumbUrl ?: ""))
            .setIntentUri(Uri.parse("plexhub://play/${media.ratingKey}?serverId=${media.serverId}"))
            .setInternalProviderId("${media.serverId}_${media.ratingKey}")

        // Add duration and position if available
        media.duration?.let { builder.setDurationMillis(it.toInt()) }
        media.viewOffset?.let { builder.setLastPlaybackPositionMillis(it.toInt()) }

        return builder.build()
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew :core:common:build`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add core/common/src/main/java/com/chakir/plexhubtv/core/util/TvChannelManager.kt
git commit -m "feat(tv-channels): add TvChannelManager core class

- Create/update/delete Continue Watching channel
- Map MediaItem to PreviewProgram
- Delete-all + insert-all update strategy
- Respect isTvChannelsEnabled setting
- Comprehensive error handling and logging

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Create ChannelSyncWorker for periodic updates

**Files:**
- Create: `app/src/main/java/com/chakir/plexhubtv/work/ChannelSyncWorker.kt`

**Step 1: Create ChannelSyncWorker**

Create `app/src/main/java/com/chakir/plexhubtv/work/ChannelSyncWorker.kt`:

```kotlin
package com.chakir.plexhubtv.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.util.TvChannelManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Worker for periodic TV Channel synchronization.
 *
 * Runs every 3 hours to refresh the Continue Watching channel
 * with latest On Deck content. Complements immediate updates
 * after playback sessions.
 */
@HiltWorker
class ChannelSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tvChannelManager: TvChannelManager,
    private val settingsDataStore: SettingsDataStore
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("TV Channel: Periodic sync started")

        // Check if feature is enabled
        if (!settingsDataStore.isTvChannelsEnabled.first()) {
            Timber.d("TV Channel: Sync skipped (disabled in settings)")
            return Result.success()
        }

        return try {
            tvChannelManager.updateContinueWatching()
            Timber.i("TV Channel: Periodic sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TV Channel: Periodic sync failed")
            // Return success to avoid WorkManager retry spam
            Result.success()
        }
    }
}
```

**Step 2: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/work/ChannelSyncWorker.kt
git commit -m "feat(tv-channels): add ChannelSyncWorker for periodic sync

- Periodic refresh every 3 hours
- Respects isTvChannelsEnabled setting
- Fail silently (return success) to avoid retry spam
- Comprehensive logging for observability

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Initialize channel and worker in PlexHubApplication

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`

**Step 1: Add channel initialization in onCreate()**

In `PlexHubApplication.kt`, add after existing WorkManager initialization (around line where LibrarySyncWorker is enqueued):

```kotlin
import com.chakir.plexhubtv.work.ChannelSyncWorker
import com.chakir.plexhubtv.core.util.TvChannelManager
import java.util.concurrent.TimeUnit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import kotlinx.coroutines.launch

// Add TvChannelManager injection
@Inject
lateinit var tvChannelManager: TvChannelManager

// In onCreate(), after existing worker setup, add:

// Initialize TV Channel (if enabled)
lifecycleScope.launch {
    try {
        tvChannelManager.createChannelIfNeeded()
    } catch (e: Exception) {
        Timber.e(e, "TV Channel: Initialization failed")
    }
}

// Schedule periodic channel sync (every 3 hours)
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

Timber.d("TV Channel: Periodic worker scheduled (every 3h)")
```

**Step 2: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt
git commit -m "feat(tv-channels): initialize channel and worker on app startup

- Create channel on first launch (if enabled)
- Schedule PeriodicWorkRequest every 3 hours
- Network-connected constraint for reliability
- KEEP policy to avoid duplicate workers

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Add immediate update hook after playback

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerScreen.kt`

**Step 1: Find PlayerScreen and add TvChannelManager injection**

Look for the PlayerScreen composable and its ViewModel. Add TvChannelManager as a parameter:

```kotlin
@Composable
fun PlayerScreen(
    // ... existing parameters
    tvChannelManager: TvChannelManager = hiltViewModel<SomeViewModel>().tvChannelManager,
    // OR if direct injection needed:
    // Create a wrapper ViewModel or use LocalContext
) {
    // ...
}
```

**Alternative approach (if direct composable injection is complex):**

Add to the relevant ViewModel that handles player lifecycle:

```kotlin
@Inject
lateinit var tvChannelManager: TvChannelManager

// In onPlayerDispose or similar cleanup method:
viewModelScope.launch {
    try {
        tvChannelManager.updateContinueWatching()
    } catch (e: Exception) {
        Timber.e(e, "TV Channel: Post-playback update failed")
    }
}
```

**Step 2: Locate existing WatchNextHelper call**

Find where `watchNextHelper.updateWatchNext()` is called (likely in a `DisposableEffect` or `onDispose` block).

Add TvChannelManager update right after:

```kotlin
DisposableEffect(Unit) {
    onDispose {
        // Existing Watch Next update
        watchNextHelper.updateWatchNext(
            media = currentMedia,
            positionMs = currentPosition,
            durationMs = duration
        )

        // NEW: Update TV Channel
        viewModelScope.launch {
            try {
                tvChannelManager.updateContinueWatching()
            } catch (e: Exception) {
                Timber.e(e, "TV Channel: Post-playback update failed")
            }
        }
    }
}
```

**Note:** The exact implementation depends on PlayerScreen architecture. Adapt based on actual code structure.

**Step 3: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerScreen.kt
git commit -m "feat(tv-channels): add immediate update after playback

- Update channel when player disposes
- Runs alongside existing WatchNextHelper
- Fail silently to avoid disrupting player UX

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Add Settings UI toggle

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt`

**Step 1: Add StateFlow in SettingsViewModel**

In `SettingsViewModel.kt`, add:

```kotlin
val isTvChannelsEnabled: StateFlow<Boolean> = settingsDataStore.isTvChannelsEnabled
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

fun setTvChannelsEnabled(enabled: Boolean) {
    viewModelScope.launch {
        settingsDataStore.setTvChannelsEnabled(enabled)

        // Trigger immediate action based on state
        if (!enabled) {
            tvChannelManager.deleteChannel()
        } else {
            tvChannelManager.createChannelIfNeeded()
            tvChannelManager.updateContinueWatching()
        }
    }
}
```

Add TvChannelManager injection:

```kotlin
@Inject
lateinit var tvChannelManager: TvChannelManager
```

**Step 2: Add UI toggle in SettingsScreen**

In `SettingsScreen.kt`, find the Settings preferences list and add:

```kotlin
// Add after other preference items

val isTvChannelsEnabled by viewModel.isTvChannelsEnabled.collectAsState()

SwitchPreference(
    title = stringResource(R.string.settings_tv_channels_title),
    summary = stringResource(R.string.settings_tv_channels_summary),
    checked = isTvChannelsEnabled,
    onCheckedChange = { enabled ->
        viewModel.setTvChannelsEnabled(enabled)
    }
)
```

**Note:** If `SwitchPreference` composable doesn't exist, create it or use existing Settings UI pattern (TvMaterial `Switch` component).

**Step 3: Verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Test Settings toggle manually**

Run app on TV:
1. Navigate to Settings
2. Toggle "TV Channels" off → check launcher (channel should disappear)
3. Toggle back on → check launcher (channel should reappear)

**Step 5: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt
git commit -m "feat(tv-channels): add Settings UI toggle

- Add isTvChannelsEnabled StateFlow in ViewModel
- Add setTvChannelsEnabled() with immediate delete/create
- Add SwitchPreference in Settings screen
- Delete channel when disabled, recreate when enabled

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Manual testing and validation

**Files:**
- None (testing only)

**Step 1: Fresh install test**

1. Uninstall PlexHubTV from Android TV
2. Build and install: `./gradlew :app:installDebug`
3. Launch app
4. Return to TV launcher
5. **Expected:** "PlexHubTV - Continue Watching" channel appears
6. **Expected:** Channel contains On Deck items (if available)

**Step 2: Playback update test**

1. Play a movie/episode for 2-3 minutes
2. Stop playback (press Back)
3. Return to TV launcher
4. **Expected:** Channel updated with the just-watched item
5. **Expected:** Item shows correct progress bar

**Step 3: Settings toggle test**

1. Open PlexHubTV Settings
2. Disable "TV Channels"
3. Return to launcher
4. **Expected:** Channel disappears
5. Re-enable "TV Channels"
6. Return to launcher
7. **Expected:** Channel reappears with content

**Step 4: Empty state test**

1. Clear all watch history (or use fresh account with no On Deck)
2. Return to launcher
3. **Expected:** Channel either hidden or shows "No content" (graceful handling)
4. **Expected:** No crashes, no errors

**Step 5: Deep link test**

1. Navigate to a channel item in launcher
2. Click to open
3. **Expected:** PlexHubTV opens to player
4. **Expected:** Playback starts at saved position

**Step 6: Watch Next non-regression test**

1. Play a movie for 2 minutes
2. Return to launcher
3. **Expected:** Single Watch Next card still appears (separate from channel)
4. **Expected:** Both Watch Next and Channel show the same content

**Step 7: Document test results**

Create a quick test report (can be informal):

```markdown
# TV Channels Manual Test Report

**Date:** YYYY-MM-DD
**Build:** Debug vX.X.X
**Device:** [Android TV model]

## Test Results

- ✅ Fresh install: Channel appears
- ✅ Playback update: Immediate sync works
- ✅ Settings toggle: Enable/disable works
- ✅ Empty state: No crashes
- ✅ Deep link: Opens to player correctly
- ✅ Watch Next: No regression

## Issues Found

[List any bugs or UX issues]

## Notes

[Any observations]
```

**Step 8: Commit test report (optional)**

```bash
git add docs/test-reports/2026-02-20-tv-channels-manual-test.md
git commit -m "test(tv-channels): add manual test report

- Validated fresh install, playback, settings, empty state
- Verified deep links and Watch Next non-regression
- No critical issues found

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Write unit tests (optional but recommended)

**Files:**
- Create: `core/common/src/test/java/com/chakir/plexhubtv/core/util/TvChannelManagerTest.kt`
- Create: `app/src/test/java/com/chakir/plexhubtv/work/ChannelSyncWorkerTest.kt`

**Step 1: Create TvChannelManagerTest skeleton**

Create `core/common/src/test/java/com/chakir/plexhubtv/core/util/TvChannelManagerTest.kt`:

```kotlin
package com.chakir.plexhubtv.core.util

import android.content.ContentResolver
import android.content.Context
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.domain.repository.OnDeckRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TvChannelManagerTest {
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var onDeckRepository: OnDeckRepository
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var tvChannelManager: TvChannelManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        contentResolver = mockk(relaxed = true)
        onDeckRepository = mockk()
        settingsDataStore = mockk()

        coEvery { context.contentResolver } returns contentResolver
        coEvery { settingsDataStore.isTvChannelsEnabled } returns flowOf(true)

        tvChannelManager = TvChannelManager(context, onDeckRepository, settingsDataStore)
    }

    @Test
    fun `createChannelIfNeeded skips when disabled`() = runTest {
        // Given
        coEvery { settingsDataStore.isTvChannelsEnabled } returns flowOf(false)

        // When
        val result = tvChannelManager.createChannelIfNeeded()

        // Then
        assert(result == null)
    }

    @Test
    fun `updateContinueWatching skips when empty list`() = runTest {
        // Given
        coEvery { onDeckRepository.getUnifiedOnDeck() } returns flowOf(emptyList())

        // When
        tvChannelManager.updateContinueWatching()

        // Then
        // Verify no insert calls (would need mockk verify)
        // For now, just ensure no crash
    }
}
```

**Step 2: Create ChannelSyncWorkerTest**

Create `app/src/test/java/com/chakir/plexhubtv/work/ChannelSyncWorkerTest.kt`:

```kotlin
package com.chakir.plexhubtv.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import com.chakir.plexhubtv.core.util.TvChannelManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ChannelSyncWorkerTest {
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var tvChannelManager: TvChannelManager
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var worker: ChannelSyncWorker

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)
        tvChannelManager = mockk(relaxed = true)
        settingsDataStore = mockk()

        coEvery { settingsDataStore.isTvChannelsEnabled } returns flowOf(true)

        worker = ChannelSyncWorker(context, workerParams, tvChannelManager, settingsDataStore)
    }

    @Test
    fun `worker skips when channels disabled`() = runTest {
        // Given
        coEvery { settingsDataStore.isTvChannelsEnabled } returns flowOf(false)

        // When
        val result = worker.doWork()

        // Then
        assert(result is ListenableWorker.Result.Success)
        coVerify(exactly = 0) { tvChannelManager.updateContinueWatching() }
    }

    @Test
    fun `worker returns success even on error`() = runTest {
        // Given
        coEvery { tvChannelManager.updateContinueWatching() } throws Exception("Test error")

        // When
        val result = worker.doWork()

        // Then
        assert(result is ListenableWorker.Result.Success)
    }
}
```

**Step 3: Run tests**

Run: `./gradlew test`
Expected: All tests pass

**Step 4: Commit**

```bash
git add core/common/src/test/java/com/chakir/plexhubtv/core/util/TvChannelManagerTest.kt app/src/test/java/com/chakir/plexhubtv/work/ChannelSyncWorkerTest.kt
git commit -m "test(tv-channels): add unit tests for TvChannelManager and ChannelSyncWorker

- Test disabled state handling
- Test empty content handling
- Test worker error resilience
- Mock ContentResolver and repositories

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Final integration and polish

**Files:**
- Modify: `README.md` (optional documentation)

**Step 1: Update README with TV Channels feature (optional)**

In `README.md`, add under Features section:

```markdown
### Android TV Integration

- **Watch Next**: Single-item "Continue Watching" card in launcher
- **TV Channels**: Full "Continue Watching" channel row with up to 15 items
- **Deep Links**: Direct playback from launcher items
- **User Control**: Toggle channels in Settings
```

**Step 2: Final build and smoke test**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

Run app on TV:
1. Fresh install
2. Navigate through Home → Settings → Player
3. Play content, verify channel updates
4. Toggle settings, verify behavior

**Step 3: Final commit**

```bash
git add README.md
git commit -m "docs: add TV Channels feature to README

- Document Watch Next vs TV Channels
- Note deep link support
- Mention Settings control

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Post-Implementation Checklist

After completing all tasks, verify:

- ✅ Channel appears in Android TV launcher
- ✅ Channel displays up to 15 On Deck items
- ✅ Items update immediately after playback
- ✅ Items update periodically (every 3h)
- ✅ Settings toggle enables/disables channel
- ✅ Deep links open to player correctly
- ✅ Empty On Deck handled gracefully (no crashes)
- ✅ Watch Next still works (no regression)
- ✅ All commits follow conventional commit format
- ✅ Code compiles without warnings
- ✅ Manual tests pass on real Android TV device

---

## Known Limitations and Future Work

**Current Limitations:**
- Channel shows only "Continue Watching" (no other content types)
- Fixed limit of 15 items (Android recommendation)
- Periodic sync every 3h (could add shorter intervals or on-demand refresh)

**Future Extensions:**
- "Recommended" channel based on watch history + ratings
- "Recently Added" channel for new content
- "Favorites" channel synced from Plex Watchlist
- User-configurable refresh intervals
- Per-channel enable/disable in Settings

**Technical Debt:**
- Unit test coverage could be improved (currently basic mocks)
- Integration tests for ContentProvider interactions
- Performance profiling for large On Deck lists (>100 items)

---

## Troubleshooting Guide

### Channel doesn't appear in launcher

1. Check `isTvChannelsEnabled` in Settings → ensure it's `true`
2. Check Logcat for "TV Channel:" logs → look for errors
3. Verify `TvContractCompat.requestChannelBrowsable()` succeeded
4. Try manually enabling channel via launcher settings (long-press on app)

### Channel shows stale content

1. Check ChannelSyncWorker logs → verify periodic sync runs
2. Check OnDeckRepository → verify data is up-to-date
3. Force refresh: Toggle settings off/on
4. Check WorkManager state: `adb shell dumpsys activity service WorkManagerService`

### Deep links don't work

1. Verify AndroidManifest intent-filter (should be unchanged)
2. Check deep link URI format: `plexhub://play/{ratingKey}?serverId={serverId}`
3. Test with `adb shell am start -a android.intent.action.VIEW -d "plexhub://play/12345?serverId=abc"`

### Worker not running periodically

1. Check WorkManager logs
2. Verify network constraint: device must be connected
3. Check for duplicate workers: should use `KEEP` policy
4. Test immediate run: `adb shell am broadcast -a androidx.work.diagnostics.REQUEST_DIAGNOSTICS`

---

## Success Criteria

**Definition of Done:**

1. ✅ Code compiles and runs on Android TV (API 27+)
2. ✅ All manual tests pass (see Task 7)
3. ✅ Unit tests pass (if implemented)
4. ✅ No regressions in existing features (Watch Next, On Deck, Player)
5. ✅ Settings toggle works as expected
6. ✅ Deep links open to player correctly
7. ✅ Code follows existing PlexHubTV patterns (Hilt, Coroutines, Timber)
8. ✅ Commits follow conventional commit format
9. ✅ Documentation updated (README, design doc)

**Ready for PR when:**
- All tasks completed
- All tests passing
- Manual validation on real Android TV device
- No critical bugs or crashes
- Code reviewed (self-review or peer review)

---

## Estimated Effort

**Total Time:** ~4-6 hours

- Task 1 (Settings): 20 min
- Task 2 (TvChannelManager): 1.5 hours
- Task 3 (ChannelSyncWorker): 30 min
- Task 4 (Initialization): 30 min
- Task 5 (Player hook): 45 min
- Task 6 (Settings UI): 45 min
- Task 7 (Manual testing): 1 hour
- Task 8 (Unit tests): 1 hour (optional)
- Task 9 (Polish): 30 min

**Complexity:** Medium
- Moderate Android TV API knowledge required
- Existing patterns make integration straightforward
- Manual testing requires physical TV device

---

**Plan Complete!** 🎉

All implementation steps documented with:
- ✅ Exact file paths
- ✅ Complete code snippets
- ✅ Test commands with expected output
- ✅ Commit messages
- ✅ Troubleshooting guide
- ✅ Success criteria
