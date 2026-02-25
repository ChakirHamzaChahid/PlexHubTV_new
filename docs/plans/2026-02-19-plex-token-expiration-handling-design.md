# Plex Token Expiration Handling Design (SEC4)

**Date:** 2026-02-19
**Status:** Approved
**Priority:** P1 (Security)

## Overview

This design implements graceful handling of Plex authentication token expiration (401 errors) in PlexHubTV. When a token becomes invalid, the system will detect it centrally, clear the invalid token, show a user-friendly message, and redirect to the authentication flow.

## Problem Statement

Currently, PlexHubTV has no centralized mechanism to handle token expiration:
- When Plex returns 401 (token expired/revoked), users see confusing errors
- No automatic token cleanup occurs
- No clear path to re-authenticate
- User experience is poor when session expires during active usage

## Requirements

### Functional Requirements
- **FR1:** Detect all 401 responses from Plex API centrally
- **FR2:** Clear invalid token from encrypted storage immediately
- **FR3:** Show user-friendly dialog explaining session expiration
- **FR4:** Redirect user to authentication screen after dialog dismissal
- **FR5:** Support French and English localization
- **FR6:** Prevent multiple dialogs from showing for simultaneous 401s

### Non-Functional Requirements
- **NFR1:** Thread-safe event emission from OkHttp interceptor
- **NFR2:** No infinite retry loops on 401
- **NFR3:** Clean architecture with separation of concerns
- **NFR4:** Testable components with clear interfaces

### User Experience Requirements
- **UX1:** Show dialog first, then redirect (not immediate navigation)
- **UX2:** Treat all 401s uniformly (simple approach)
- **UX3:** Cancel ongoing requests when 401 detected
- **UX4:** Clear back stack when navigating to auth screen

## Design Approach

**Selected Approach:** Interceptor + StateFlow Event Bus

This approach provides clean separation between network layer and UI layer using a reactive event bus pattern.

### Alternative Approaches Considered

1. **Interceptor + Repository Direct Action:** Simpler but violates single responsibility principle
2. **Custom OkHttp Authenticator:** Over-engineered for Plex (no refresh token support)

## Architecture

### Component Overview

```
┌─────────────────┐
│  Plex API       │
│  (returns 401)  │
└────────┬────────┘
         │
         ▼
┌─────────────────────┐
│  AuthInterceptor    │ ◄── Detects 401, emits event
└─────────┬───────────┘
          │
          │ emits
          ▼
┌─────────────────────┐
│  AuthEventBus       │ ◄── Singleton event channel
└─────────┬───────────┘
          │
          │ collects
          ▼
┌─────────────────────┐
│  MainViewModel      │ ◄── Clears token, shows dialog
└─────────┬───────────┘
          │
          │ updates state
          ▼
┌─────────────────────┐
│  MainActivity       │ ◄── Shows dialog, navigates
└─────────────────────┘
```

### Component Details

#### 1. AuthEventBus (New)

**Location:** `core/common/src/main/java/com/chakir/plexhubtv/core/common/auth/AuthEventBus.kt`

**Responsibility:** Provides a global event channel for authentication state changes.

**Interface:**
```kotlin
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun emitTokenInvalid() {
        _events.tryEmit(AuthEvent.TokenInvalid)
    }
}

sealed interface AuthEvent {
    data object TokenInvalid : AuthEvent
}
```

**Key Design Decisions:**
- `extraBufferCapacity = 1`: Buffer one event if no collector is active
- `DROP_OLDEST`: Only care about most recent 401
- `tryEmit()`: Non-blocking, thread-safe for interceptor usage
- `sealed interface`: Extensible for future auth events

#### 2. Enhanced AuthInterceptor

**Location:** `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt`

**Changes:**
- Inject `AuthEventBus` dependency
- After `chain.proceed()`, check response code
- Emit event if 401 detected
- Return response normally (no retry logic)

```kotlin
override fun intercept(chain: Interceptor.Chain): Response {
    // ... existing header injection logic ...

    val response = chain.proceed(requestBuilder.build())

    // NEW: Detect 401 and signal token invalidation
    if (response.code == 401) {
        authEventBus.emitTokenInvalid()
    }

    return response
}
```

**Design Rationale:**
- Interceptor remains simple: detect and emit
- No business logic about clearing tokens or navigation
- Original response returned so caller can handle error appropriately

#### 3. Enhanced AuthRepository

**Location:** `domain/src/main/java/com/chakir/plexhubtv/domain/repository/AuthRepository.kt`

**New Methods:**
```kotlin
interface AuthRepository {
    // ... existing methods ...

    /** Clears only the authentication token */
    suspend fun clearToken()

    /** Clears token + user data + optionally database */
    suspend fun clearAllAuthData(clearDatabase: Boolean = true)
}
```

**Implementation:**
```kotlin
override suspend fun clearToken() {
    settingsDataStore.clearToken()
}

override suspend fun clearAllAuthData(clearDatabase: Boolean) {
    settingsDataStore.clearToken()
    settingsDataStore.clearUser()
    if (clearDatabase) {
        database.clearAllTables()
    }
}
```

**Design Decision:** For 401 handling, use `clearToken()` only. Keep cached data for offline viewing unless compliance requires full wipe.

#### 4. MainViewModel (New)

**Location:** `app/src/main/java/com/chakir/plexhubtv/MainViewModel.kt`

**Responsibility:** Application-level coordinator for auth events. Lives in MainActivity scope.

```kotlin
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authEventBus: AuthEventBus,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _showSessionExpiredDialog = MutableStateFlow(false)
    val showSessionExpiredDialog = _showSessionExpiredDialog.asStateFlow()

    init {
        viewModelScope.launch {
            authEventBus.events.collect { event ->
                when (event) {
                    AuthEvent.TokenInvalid -> handleTokenInvalid()
                }
            }
        }
    }

    private suspend fun handleTokenInvalid() {
        // Prevent dialog spam
        if (!_showSessionExpiredDialog.value) {
            authRepository.clearToken()
            _showSessionExpiredDialog.value = true

            // Optional: Analytics
            Firebase.analytics.logEvent("session_expired") {
                param("source", "401_interceptor")
            }
        }
    }

    fun onSessionExpiredDialogDismissed(navigateToAuth: () -> Unit) {
        _showSessionExpiredDialog.value = false
        navigateToAuth()
    }
}
```

**Design Rationale:**
- Survives configuration changes (ViewModel)
- Single source of truth for dialog state
- Prevents duplicate dialogs with flag check
- Delegates navigation to caller (loose coupling)

#### 5. SessionExpiredDialog (New)

**Location:** `app/src/main/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialog.kt`

**Responsibility:** Display session expiration message with reconnect action.

```kotlin
@Composable
fun SessionExpiredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_expired_title)) },
        text = { Text(stringResource(R.string.session_expired_message)) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.reconnect))
            }
        },
        modifier = modifier
    )
}
```

**Localization Strings:**
- French: "Session expirée", "Votre session Plex a expiré. Veuillez vous reconnecter.", "Se reconnecter"
- English: "Session Expired", "Your Plex session has expired. Please sign in again.", "Reconnect"

## Data Flow

### Normal Flow (End-to-End)

1. **User action triggers API call**
   - User tries to load library, play media, etc.
   - Repository calls PlexApiService method
   - Request flows through AuthInterceptor

2. **Server returns 401**
   - AuthInterceptor receives Response with code 401
   - Calls `authEventBus.emitTokenInvalid()`
   - Returns 401 response to caller (caller handles error gracefully)

3. **MainViewModel reacts**
   - Collects event from `authEventBus.events` flow
   - Checks if dialog already showing (prevents spam)
   - Calls `authRepository.clearToken()` to remove invalid token
   - Sets `_showSessionExpiredDialog.value = true`

4. **UI shows dialog**
   - MainActivity observes `mainViewModel.showSessionExpiredDialog`
   - When true, shows `SessionExpiredDialog` composable
   - Dialog overlays current screen

5. **User dismisses dialog**
   - User taps "Se reconnecter" button
   - Calls `mainViewModel.onSessionExpiredDialogDismissed { ... }`
   - Dialog state set to false
   - Navigation lambda executed with `popUpTo(0)` to clear back stack

6. **User re-authenticates**
   - Lands on Auth screen with PIN flow
   - Goes through existing AuthViewModel logic
   - New token saved via `authRepository.loginWithToken()` or `checkPin()`
   - App resumes normal operation

### Concurrency & Thread Safety

**AuthInterceptor Thread:**
- Runs on OkHttp dispatcher threads (background)
- `tryEmit()` is non-blocking and thread-safe
- No suspend functions called on interceptor thread

**MainViewModel Thread:**
- Collects events on `viewModelScope` (Main dispatcher by default)
- Token clearing uses DataStore (coroutine-safe, uses IO dispatcher internally)
- State updates on Main thread (safe for Compose)

**Race Conditions:**
- Multiple simultaneous 401s: `tryEmit()` + buffer size 1 + DROP_OLDEST ensures only latest event matters
- Dialog already showing: Flag check in `handleTokenInvalid()` prevents duplicates
- Token clearing in progress: DataStore serializes writes internally

## Error Handling

### No Infinite Retry Loops

**Problem:** If interceptor retried 401s, it would loop forever (token still invalid).

**Solution:** AuthInterceptor emits event but does NOT retry. Original 401 response returned to caller. Caller's error handling logic remains unchanged.

### Preventing Dialog Spam

**Problem:** Rapid API calls could trigger multiple 401s before token is cleared.

**Solution:**
```kotlin
if (!_showSessionExpiredDialog.value) {
    // Only proceed if dialog not already showing
    authRepository.clearToken()
    _showSessionExpiredDialog.value = true
}
```

### Graceful Degradation

**Scenario:** MainViewModel not attached (rare edge case, e.g., process restarting).

**Behavior:**
- Event emitted but no collector active
- `extraBufferCapacity = 1` buffers the event
- When MainViewModel initializes, it collects buffered event
- If buffer overflows, next API call will trigger new 401

**Scenario:** Token clearing fails (disk full, encryption error).

**Behavior:**
- Dialog still shows
- User navigates to auth screen
- Fresh authentication will overwrite failed token
- No app crash

### Database & Cache Handling

**Decision:** Keep cached data on 401 (offline viewing still works).

**Rationale:**
- User's library data is still valid
- Faster re-sync after re-authentication
- Better offline experience

**Alternative:** Use `clearAllAuthData(clearDatabase = true)` for compliance scenarios (e.g., shared devices).

## Testing Strategy

### Unit Tests

**AuthEventBusTest:**
```kotlin
@Test
fun `emitTokenInvalid emits event to collectors`() = runTest {
    val eventBus = AuthEventBus()
    val events = mutableListOf<AuthEvent>()

    val job = launch {
        eventBus.events.collect { events.add(it) }
    }

    eventBus.emitTokenInvalid()
    advanceUntilIdle()

    assertEquals(1, events.size)
    assertTrue(events[0] is AuthEvent.TokenInvalid)
    job.cancel()
}
```

**AuthInterceptorTest:**
```kotlin
@Test
fun `intercept emits TokenInvalid event on 401 response`() = runTest {
    val mockEventBus = mock<AuthEventBus>()
    val interceptor = AuthInterceptor(settingsDataStore, scope, mockEventBus)

    val mockChain = mock<Interceptor.Chain> {
        on { proceed(any()) } doReturn Response.Builder()
            .code(401)
            .request(Request.Builder().url("http://test").build())
            .protocol(Protocol.HTTP_1_1)
            .message("Unauthorized")
            .build()
    }

    interceptor.intercept(mockChain)

    verify(mockEventBus).emitTokenInvalid()
}
```

**MainViewModelTest:**
```kotlin
@Test
fun `handleTokenInvalid clears token and shows dialog`() = runTest {
    val mockAuthRepository = mock<AuthRepository>()
    val eventBus = AuthEventBus()
    val viewModel = MainViewModel(eventBus, mockAuthRepository)

    eventBus.emitTokenInvalid()
    advanceUntilIdle()

    verify(mockAuthRepository).clearToken()
    assertTrue(viewModel.showSessionExpiredDialog.value)
}

@Test
fun `multiple events only show dialog once`() = runTest {
    val mockAuthRepository = mock<AuthRepository>()
    val eventBus = AuthEventBus()
    val viewModel = MainViewModel(eventBus, mockAuthRepository)

    eventBus.emitTokenInvalid()
    eventBus.emitTokenInvalid()
    eventBus.emitTokenInvalid()
    advanceUntilIdle()

    verify(mockAuthRepository, times(1)).clearToken()
}
```

### Integration Tests

**AuthFlowIntegrationTest:**
- Mock PlexApiService to return 401
- Verify event emitted → token cleared → dialog shown
- Verify navigation triggered on dialog dismiss

### Manual Testing Checklist

1. ✅ Invalidate token on Plex server (revoke access)
2. ✅ Trigger API call in app (load library, play media)
3. ✅ Verify dialog appears with correct French text
4. ✅ Verify dismissing dialog navigates to auth screen
5. ✅ Verify re-authentication via PIN flow works
6. ✅ Verify app resumes normally after re-auth
7. ✅ Verify multiple rapid 401s only show one dialog
8. ✅ Verify back button doesn't return to authenticated screens

## Implementation Notes

### Module Dependencies

**Add to `core/network/build.gradle.kts`:**
```kotlin
dependencies {
    implementation(project(":core:common"))
    // ... existing dependencies ...
}
```

**Add to `core/common/build.gradle.kts`:**
```kotlin
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    // ... existing dependencies ...
}
```

### Navigation Back Stack Clearing

**In MainActivity:**
```kotlin
LaunchedEffect(showSessionExpiredDialog) {
    if (showSessionExpiredDialog) {
        SessionExpiredDialog(
            onDismiss = {
                mainViewModel.onSessionExpiredDialogDismissed {
                    navController.navigate("auth") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        )
    }
}
```

**Rationale:** `popUpTo(0)` clears entire back stack, preventing user from pressing back to authenticated screens.

### Localization

**Add to `app/src/main/res/values/strings.xml` (English):**
```xml
<string name="session_expired_title">Session Expired</string>
<string name="session_expired_message">Your Plex session has expired. Please sign in again.</string>
<string name="reconnect">Reconnect</string>
```

**Add to `app/src/main/res/values-fr/strings.xml` (French):**
```xml
<string name="session_expired_title">Session expirée</string>
<string name="session_expired_message">Votre session Plex a expiré. Veuillez vous reconnecter.</string>
<string name="reconnect">Se reconnecter</string>
```

### Firebase Analytics (Optional)

Track session expiration frequency for monitoring:
```kotlin
Firebase.analytics.logEvent("session_expired") {
    param("source", "401_interceptor")
    param("timestamp", System.currentTimeMillis())
}
```

Useful for detecting:
- Unusually high expiration rates (server issues)
- Token lifetime problems
- User behavior patterns

## Implementation Order

Recommended sequence to minimize breaking changes:

1. **Create AuthEventBus** (`core/common`)
   - New file, no dependencies on existing code
   - Can be tested independently

2. **Add clearToken() to AuthRepository** (`domain`, `data`)
   - Simple wrapper, low risk
   - Already has `SettingsDataStore.clearToken()` internally

3. **Enhance AuthInterceptor** (`core/network`)
   - Inject AuthEventBus
   - Add 401 detection logic
   - Update NetworkModule provider

4. **Create SessionExpiredDialog** (`app`)
   - UI component, no business logic
   - Add localized strings

5. **Create MainViewModel** (`app`)
   - Wire up event collection
   - Add dialog state management

6. **Update MainActivity** (`app`)
   - Inject MainViewModel
   - Observe dialog state
   - Show dialog and handle navigation

7. **Add Unit Tests**
   - Test each component in isolation

8. **Manual Testing**
   - Invalidate token, verify end-to-end flow

## Success Criteria

The implementation is complete when:

1. ✅ All 401 responses trigger `AuthEvent.TokenInvalid` emission
2. ✅ Token is cleared from encrypted storage on 401
3. ✅ Dialog displays with correct localized text (FR/EN)
4. ✅ Dismissing dialog navigates to auth screen with cleared back stack
5. ✅ Re-authentication via PIN flow works normally
6. ✅ Multiple simultaneous 401s show only one dialog
7. ✅ No infinite retry loops occur
8. ✅ Unit tests pass with >80% coverage on new code
9. ✅ Manual testing checklist completed

## Future Enhancements

**Out of scope for this design, but worth considering:**

1. **Proactive token refresh:** Plex doesn't support refresh tokens, but could check expiration proactively
2. **Offline mode detection:** Distinguish 401 from network errors, show different messages
3. **Session timeout warning:** Warn user before token expires (if expiration time is known)
4. **Multi-user session handling:** If switching users, clear session data more aggressively

## References

- [Plex API Documentation](https://www.plexopedia.com/plex-media-server/api/)
- [OkHttp Interceptors](https://square.github.io/okhttp/features/interceptors/)
- [Kotlin SharedFlow](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-shared-flow/)
- [Android ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel)

---

**Document Version:** 1.0
**Last Updated:** 2026-02-19
**Approved By:** User (brainstorming session)
