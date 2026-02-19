# Firebase Crashlytics + Analytics + Performance Monitoring Integration

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Integrate Firebase Crashlytics, Analytics, and Performance Monitoring to gain full visibility on crashes, user behavior, and performance in production.

**Architecture:** Add Firebase BOM + 3 modules (Crashlytics, Analytics, Perf) via the existing Gradle version catalog. Initialize in `PlexHubApplication.onCreate()` with a DEBUG gate. Add custom keys in PlayerController, analytics events in key ViewModels, and a test crash action in DebugViewModel.

**Tech Stack:** Firebase BOM 33.7.0, Crashlytics KTX, Analytics KTX, Performance KTX, Gradle version catalog (`libs.versions.toml`)

---

## IMPORTANT: Manual Prerequisite (User Must Do)

Before starting any task below, the user must:

1. Go to https://console.firebase.google.com/
2. Create project "PlexHubTV" (or use existing)
3. Add Android app with package name `com.chakir.plexhubtv`
4. Download `google-services.json`
5. Place it at `app/google-services.json`

**Without this file, the build will fail.** All tasks below assume this file exists.

---

### Task 1: Add Firebase plugin + library entries to version catalog

**Files:**
- Modify: `gradle/libs.versions.toml`

**Step 1: Add versions**

Add after `conscrypt = "2.5.2"` (line 36):

```toml
firebaseBom = "33.7.0"
googleServices = "4.4.2"
firebaseCrashlytics = "3.0.3"
firebasePerf = "1.4.2"
```

**Step 2: Add library entries**

Add after the `conscrypt-android` library entry (line 114):

```toml
# Firebase
firebase-bom = { group = "com.google.firebase", name = "firebase-bom", version.ref = "firebaseBom" }
firebase-crashlytics-ktx = { group = "com.google.firebase", name = "firebase-crashlytics-ktx" }
firebase-analytics-ktx = { group = "com.google.firebase", name = "firebase-analytics-ktx" }
firebase-perf-ktx = { group = "com.google.firebase", name = "firebase-perf-ktx" }
```

**Step 3: Add plugin entries**

Add after the `ktlint` plugin entry (line 130):

```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
firebase-crashlytics = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlytics" }
firebase-perf = { id = "com.google.firebase.firebase-perf", version.ref = "firebasePerf" }
```

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add Firebase BOM, Crashlytics, Analytics, Perf to version catalog"
```

---

### Task 2: Add Firebase plugins to root build.gradle.kts

**Files:**
- Modify: `build.gradle.kts` (root)

**Step 1: Add plugin declarations**

After line 15 (`alias(libs.plugins.ktlint) apply false`), add:

```kotlin
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.firebase.perf) apply false
```

**Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "build: declare Firebase plugins in root build.gradle.kts"
```

---

### Task 3: Apply Firebase plugins and dependencies in app/build.gradle.kts

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Apply plugins**

After line 13 (`alias(libs.plugins.ktlint)`), add:

```kotlin
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
```

**Step 2: Add dependencies**

After the `// --- Security Resilience ---` block (after line 217), add:

```kotlin
    // --- Firebase ---
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.perf.ktx)
```

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: apply Firebase plugins and add dependencies in app module"
```

---

### Task 4: Initialize Firebase in PlexHubApplication

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt`

**Step 1: Add imports**

Add after the existing imports (after `import javax.inject.Inject`, line 34):

```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.perf.FirebasePerformance
```

**Step 2: Add Firebase initialization in onCreate()**

After `installSecurityProviders()` (line 75) and before `initializeAppInParallel()` (line 78), add:

```kotlin
        // Firebase
        initializeFirebase()
```

**Step 3: Add the initializeFirebase() method**

Add this private method after `installSecurityProviders()` method (after line 274):

```kotlin
    /**
     * Initializes Firebase services with a DEBUG gate.
     * Collection is disabled in debug builds to avoid noise during development.
     */
    private fun initializeFirebase() {
        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("build_type", BuildConfig.BUILD_TYPE)
        }

        FirebaseAnalytics.getInstance(this).apply {
            setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        }

        FirebasePerformance.getInstance().apply {
            isPerformanceCollectionEnabled = !BuildConfig.DEBUG
        }

        Timber.i("Firebase initialized (collection=${!BuildConfig.DEBUG})")
    }
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt
git commit -m "feat: initialize Firebase Crashlytics, Analytics, Perf in Application"
```

---

### Task 5: Add Crashlytics custom keys in PlayerController

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt`

**Step 1: Add import**

Add after existing imports (after `import javax.inject.Singleton`, line 26):

```kotlin
import com.google.firebase.crashlytics.FirebaseCrashlytics
```

**Step 2: Add Crashlytics context in initialize()**

After line 68 (`this.startOffset = offset`) and before `initializePlayer(application)` (line 70), add:

```kotlin
        // Set Crashlytics context for crash diagnostics
        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("player_engine", "ExoPlayer")
            setCustomKey("media_rating_key", startRatingKey ?: "unknown")
            setCustomKey("server_id", startServerId ?: "unknown")
            setCustomKey("is_direct_url", (startDirectUrl != null).toString())
        }
```

**Step 3: Update Crashlytics key when switching to MPV**

Find the `switchToMpv` function (search for `fun switchToMpv` or `isMpvMode = true`). After `isMpvMode = true` is set, add:

```kotlin
        FirebaseCrashlytics.getInstance().setCustomKey("player_engine", "MPV")
```

> Note: If `switchToMpv` doesn't exist as a separate function, find where `isMpvMode = true` is set (likely in the `onPlayerError` listener) and add the line there.

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt
git commit -m "feat: add Crashlytics custom keys in PlayerController"
```

---

### Task 6: Track analytics events in AuthViewModel

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthViewModel.kt`

**Step 1: Add import**

Add after existing imports:

```kotlin
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
```

**Step 2: Track successful auth in fetchServers()**

In the `fetchServers()` function (line 122), inside `.onSuccess { servers -> }`, add before `_uiState.value = AuthUiState.Success(servers)`:

```kotlin
                    FirebaseAnalytics.getInstance(com.google.firebase.Firebase.app.applicationContext)
                        .logEvent("auth_success") {
                            param("server_count", servers.size.toLong())
                        }
```

Wait — `FirebaseAnalytics.getInstance()` requires a Context. In a ViewModel with no Application injection, use `FirebaseAnalytics.getInstance(FirebaseApp.getInstance().applicationContext)`. BUT simpler: inject `Application` into the ViewModel constructor.

**Revised approach**: Since `AuthViewModel` currently only injects `AuthRepository`, the cleanest approach without adding constructor params is to use `Firebase.analytics` (the KTX extension):

```kotlin
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
```

Then in `fetchServers()` `.onSuccess`:

```kotlin
                    Firebase.analytics.logEvent("auth_success") {
                        param("server_count", servers.size.toLong())
                    }
```

And in `loginWithToken()` `.onFailure`:

```kotlin
                    Firebase.analytics.logEvent("auth_failed") {
                        param("method", "token")
                    }
```

And in `startPolling()` timeout case (line 119, before `_uiState.value = AuthUiState.Error(...)`):

```kotlin
            Firebase.analytics.logEvent("auth_timeout") {}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthViewModel.kt
git commit -m "feat: track auth analytics events (success, failed, timeout)"
```

---

### Task 7: Track play event in MediaDetailViewModel

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt`

**Step 1: Add imports**

```kotlin
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
```

**Step 2: Track video_play event**

In the `PlayClicked` handler, after `val media = _uiState.value.media ?: return` (line 78), add:

```kotlin
                    Firebase.analytics.logEvent("video_play") {
                        param("media_type", media.type.name)
                        param("title", media.title.take(100))
                        param("server_id", media.serverId)
                    }
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt
git commit -m "feat: track video_play analytics event"
```

---

### Task 8: Track search event in SearchViewModel

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt`

**Step 1: Add imports**

```kotlin
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
```

**Step 2: Track search event**

In `performSearch()`, after `_uiState.update { it.copy(searchState = SearchState.Searching) }` (line 80), add:

```kotlin
                    Firebase.analytics.logEvent("search") {
                        param(FirebaseAnalytics.Param.SEARCH_TERM, query.take(100))
                    }
```

This also needs:

```kotlin
import com.google.firebase.analytics.FirebaseAnalytics
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt
git commit -m "feat: track search analytics event"
```

---

### Task 9: Add test crash action in DebugViewModel

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/debug/DebugUiState.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/debug/DebugViewModel.kt`

**Step 1: Add TestCrash action to DebugAction sealed interface**

In `DebugUiState.kt`, add after `data object ForceSync : DebugAction` (line 118):

```kotlin
    data object TestCrash : DebugAction
```

**Step 2: Handle action in DebugViewModel**

In `DebugViewModel.kt`, add import:

```kotlin
import com.chakir.plexhubtv.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
```

In the `onAction()` `when` block (after line 57), add:

```kotlin
            is DebugAction.TestCrash -> testCrash()
```

Add the method after `onAction()`:

```kotlin
    private fun testCrash() {
        if (BuildConfig.DEBUG) {
            FirebaseCrashlytics.getInstance().log("Test crash triggered from Debug screen")
            throw RuntimeException("Test crash from PlexHubTV Debug screen")
        }
    }
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/debug/DebugUiState.kt
git add app/src/main/java/com/chakir/plexhubtv/feature/debug/DebugViewModel.kt
git commit -m "feat: add test crash action in DebugViewModel (DEBUG only)"
```

---

### Task 10: Add Firebase ProGuard rules

**Files:**
- Modify: `app/proguard-rules.pro`

**Step 1: Add Firebase rules**

Append at the end of the file (after line 133):

```
# ============================================================================
# Firebase Crashlytics
# ============================================================================
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keep public class * extends java.lang.Exception

# ============================================================================
# Firebase Analytics
# ============================================================================
-keep class com.google.android.gms.measurement.** { *; }
-dontwarn com.google.android.gms.measurement.**

# ============================================================================
# Firebase Performance
# ============================================================================
-keep class com.google.firebase.perf.** { *; }
-dontwarn com.google.firebase.perf.**
```

**Step 2: Commit**

```bash
git add app/proguard-rules.pro
git commit -m "build: add Firebase ProGuard rules for Crashlytics, Analytics, Perf"
```

---

### Task 11: Verify build compiles

**Step 1: Run debug build**

```bash
cd C:\Users\chakir\AndroidStudioProjects\PlexHubTV
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

> Note: This will fail if `google-services.json` is not present in `app/`. If it fails for this reason, that's expected — the user needs to complete the manual Firebase setup step.

**Step 2: If build succeeds, create final commit**

```bash
git add .
git commit -m "feat(P0): integrate Firebase Crashlytics + Analytics + Performance (#3)

- Add Firebase BOM 33.7.0 (Crashlytics, Analytics, Performance)
- Initialize in PlexHubApplication with DEBUG gate
- Add custom keys: app_version, build_type, player_engine, server_id
- Track analytics events: auth_success/failed/timeout, video_play, search
- Add test crash action in DebugViewModel (DEBUG only)
- Configure ProGuard rules for Firebase
- Requires google-services.json from Firebase Console

Fixes #3"
```

---

## Summary of Modified Files

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add Firebase versions, libraries, plugins |
| `build.gradle.kts` (root) | Declare 3 Firebase plugins |
| `app/build.gradle.kts` | Apply plugins + add dependencies |
| `app/src/.../PlexHubApplication.kt` | Initialize Firebase with DEBUG gate |
| `app/src/.../PlayerController.kt` | Crashlytics custom keys (engine, server, media) |
| `app/src/.../AuthViewModel.kt` | Analytics: auth_success, auth_failed, auth_timeout |
| `app/src/.../MediaDetailViewModel.kt` | Analytics: video_play |
| `app/src/.../SearchViewModel.kt` | Analytics: search |
| `app/src/.../DebugUiState.kt` | Add TestCrash action |
| `app/src/.../DebugViewModel.kt` | Implement test crash (DEBUG only) |
| `app/proguard-rules.pro` | Firebase ProGuard rules |
