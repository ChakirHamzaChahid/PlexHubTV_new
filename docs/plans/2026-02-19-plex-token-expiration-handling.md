# Plex Token Expiration Handling Implementation Plan (SEC4)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement centralized 401 error detection with user-friendly session expiration handling and automatic re-authentication flow.

**Architecture:** Event-driven architecture using AuthEventBus for reactive propagation of auth events from network layer to UI layer. AuthInterceptor detects 401 responses, MainViewModel coordinates dialog display and token clearing, SessionExpiredDialog provides user feedback.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Kotlin Coroutines/Flow, OkHttp Interceptors, DataStore

---

## Task 1: Create AuthEventBus (Event Channel)

**Files:**
- Create: `core/common/src/main/java/com/chakir/plexhubtv/core/common/auth/AuthEventBus.kt`
- Test: `core/common/src/test/java/com/chakir/plexhubtv/core/common/auth/AuthEventBusTest.kt`

**Step 1: Write the failing test**

Create test file:

```kotlin
package com.chakir.plexhubtv.core.common.auth

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthEventBusTest {

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

    @Test
    fun `multiple emits only buffer latest event`() = runTest {
        val eventBus = AuthEventBus()

        // Emit before collector starts
        eventBus.emitTokenInvalid()
        eventBus.emitTokenInvalid()

        val events = mutableListOf<AuthEvent>()
        val job = launch {
            eventBus.events.collect { events.add(it) }
        }

        advanceUntilIdle()

        // Should only receive one buffered event (DROP_OLDEST behavior)
        assertEquals(1, events.size)
        job.cancel()
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :core:common:test --tests "com.chakir.plexhubtv.core.common.auth.AuthEventBusTest" --rerun-tasks`

Expected: FAIL with "Unresolved reference: AuthEventBus"

**Step 3: Write minimal implementation**

Create directory:
```bash
mkdir -p core/common/src/main/java/com/chakir/plexhubtv/core/common/auth
```

Create file `core/common/src/main/java/com/chakir/plexhubtv/core/common/auth/AuthEventBus.kt`:

```kotlin
package com.chakir.plexhubtv.core.common.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Global event bus for authentication-related events.
 *
 * Provides a reactive channel for auth state changes (e.g., token expiration).
 * Thread-safe for emission from OkHttp interceptors.
 */
@Singleton
class AuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<AuthEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    /**
     * Emits a TokenInvalid event when 401 response detected.
     * Thread-safe, non-blocking.
     */
    fun emitTokenInvalid() {
        _events.tryEmit(AuthEvent.TokenInvalid)
    }
}

/**
 * Authentication events propagated through the app.
 */
sealed interface AuthEvent {
    /**
     * Token is invalid (expired or revoked).
     * Triggered by 401 responses from Plex API.
     */
    data object TokenInvalid : AuthEvent
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :core:common:test --tests "com.chakir.plexhubtv.core.common.auth.AuthEventBusTest" --rerun-tasks`

Expected: PASS (2/2 tests)

**Step 5: Commit**

```bash
git add core/common/src/main/java/com/chakir/plexhubtv/core/common/auth/AuthEventBus.kt
git add core/common/src/test/java/com/chakir/plexhubtv/core/common/auth/AuthEventBusTest.kt
git commit -m "feat(auth): add AuthEventBus for reactive auth event propagation

- Singleton event channel using SharedFlow
- Thread-safe tryEmit() for interceptor usage
- Buffer capacity 1 with DROP_OLDEST for deduplication
- AuthEvent.TokenInvalid for 401 handling

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Add clearToken() to AuthRepository

**Files:**
- Modify: `domain/src/main/java/com/chakir/plexhubtv/domain/repository/AuthRepository.kt:53`
- Modify: `data/src/main/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImpl.kt:276`
- Test: `data/src/test/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImplTest.kt`

**Step 1: Write the failing test**

Check if test file exists, if not create it. Add test:

```kotlin
@Test
fun `clearToken removes token from datastore`() = runTest {
    // Given
    settingsDataStore.saveToken("test-token")
    val tokenBefore = settingsDataStore.plexToken.first()
    assertEquals("test-token", tokenBefore)

    // When
    repository.clearToken()

    // Then
    val tokenAfter = settingsDataStore.plexToken.first()
    assertNull(tokenAfter)
}

@Test
fun `clearAllAuthData removes token and user data`() = runTest {
    // Given
    settingsDataStore.saveToken("test-token")
    settingsDataStore.saveUser("uuid", "name")

    // When
    repository.clearAllAuthData(clearDatabase = false)

    // Then
    assertNull(settingsDataStore.plexToken.first())
    assertNull(settingsDataStore.currentUserUuid.first())
    assertNull(settingsDataStore.currentUserName.first())
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :data:test --tests "*AuthRepositoryImplTest*clearToken*" --rerun-tasks`

Expected: FAIL with "Unresolved reference: clearToken"

**Step 3: Add interface methods**

Edit `domain/src/main/java/com/chakir/plexhubtv/domain/repository/AuthRepository.kt`:

Add after line 52 (after `observeAuthState()`):

```kotlin
    /** Clears only the authentication token from secure storage. */
    suspend fun clearToken()

    /**
     * Clears token + user data + optionally database.
     * @param clearDatabase If true, wipes all cached media data.
     */
    suspend fun clearAllAuthData(clearDatabase: Boolean = true)
```

**Step 4: Implement in AuthRepositoryImpl**

Edit `data/src/main/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImpl.kt`:

Add after line 275 (after `observeAuthState()`):

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

**Step 5: Run test to verify it passes**

Run: `./gradlew :data:test --tests "*AuthRepositoryImplTest*clearToken*" --rerun-tasks`

Expected: PASS

**Step 6: Commit**

```bash
git add domain/src/main/java/com/chakir/plexhubtv/domain/repository/AuthRepository.kt
git add data/src/main/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImpl.kt
git add data/src/test/java/com/chakir/plexhubtv/data/repository/AuthRepositoryImplTest.kt
git commit -m "feat(auth): add clearToken() and clearAllAuthData() methods

- clearToken() removes only auth token (keeps cache)
- clearAllAuthData() removes token + user + optionally DB
- Unit tests verify clearing behavior

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Add core:common dependency to core:network

**Files:**
- Modify: `core/network/build.gradle.kts:40`

**Step 1: Add dependency**

Edit `core/network/build.gradle.kts`, add after existing `implementation` dependencies:

```kotlin
implementation(project(":core:common"))
```

**Step 2: Sync Gradle**

Run: `./gradlew :core:network:build --dry-run`

Expected: Build configuration successful

**Step 3: Commit**

```bash
git add core/network/build.gradle.kts
git commit -m "build(network): add core:common dependency for AuthEventBus

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Enhance AuthInterceptor with 401 detection

**Files:**
- Modify: `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt:22-71`
- Test: `core/network/src/test/java/com/chakir/plexhubtv/core/network/AuthInterceptorTest.kt`

**Step 1: Write the failing test**

Create test file `core/network/src/test/java/com/chakir/plexhubtv/core/network/AuthInterceptorTest.kt`:

```kotlin
package com.chakir.plexhubtv.core.network

import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.core.datastore.SettingsDataStore
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Test

class AuthInterceptorTest {

    @Test
    fun `intercept emits TokenInvalid event on 401 response`() = runTest {
        val mockEventBus = mockk<AuthEventBus>(relaxed = true)
        val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob())

        val interceptor = AuthInterceptor(mockDataStore, scope, mockEventBus)

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://test").build()

            override fun proceed(request: Request): Response = Response.Builder()
                .code(401)
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("Unauthorized")
                .build()

            override fun connection() = null
            override fun call() = mockk()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(mockChain)

        verify(exactly = 1) { mockEventBus.emitTokenInvalid() }
    }

    @Test
    fun `intercept does not emit event on 200 response`() = runTest {
        val mockEventBus = mockk<AuthEventBus>(relaxed = true)
        val mockDataStore = mockk<SettingsDataStore>(relaxed = true)
        val scope = CoroutineScope(SupervisorJob())

        val interceptor = AuthInterceptor(mockDataStore, scope, mockEventBus)

        val mockChain = object : Interceptor.Chain {
            override fun request(): Request = Request.Builder().url("http://test").build()

            override fun proceed(request: Request): Response = Response.Builder()
                .code(200)
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .message("OK")
                .build()

            override fun connection() = null
            override fun call() = mockk()
            override fun connectTimeoutMillis() = 0
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 0
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 0
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }

        interceptor.intercept(mockChain)

        verify(exactly = 0) { mockEventBus.emitTokenInvalid() }
    }
}
```

**Step 2: Add test dependencies to core:network/build.gradle.kts**

Add to test dependencies section:

```kotlin
testImplementation(libs.junit)
testImplementation(libs.mockk)
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.okhttp)
```

**Step 3: Run test to verify it fails**

Run: `./gradlew :core:network:test --tests "AuthInterceptorTest" --rerun-tasks`

Expected: FAIL with "Too many arguments for constructor" (AuthEventBus not injected yet)

**Step 4: Modify AuthInterceptor**

Edit `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt`:

Add import:
```kotlin
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
```

Modify constructor (line 22-27):
```kotlin
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val settingsDataStore: SettingsDataStore,
        @ApplicationScope private val scope: CoroutineScope,
        private val authEventBus: AuthEventBus,
    ) : Interceptor {
```

Modify `intercept()` method (add after line 68, before return):

```kotlin
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val token = cachedToken.get()
        val clientId = cachedClientId.get()

        if (clientId != null && originalRequest.header("X-Plex-Client-Identifier") == null) {
            requestBuilder.addHeader("X-Plex-Client-Identifier", clientId)
        }

        if (token != null && originalRequest.header("X-Plex-Token") == null && !originalRequest.url.toString().contains("X-Plex-Token")) {
            requestBuilder.addHeader("X-Plex-Token", token)
        }

        if (originalRequest.header("Accept") == null) {
            requestBuilder.addHeader("Accept", "application/json")
        }

        requestBuilder.header("X-Plex-Platform", "Android")
        requestBuilder.header("X-Plex-Platform-Version", Build.VERSION.RELEASE)
        requestBuilder.header("X-Plex-Provides", "player")
        requestBuilder.header("X-Plex-Product", "Plex for Android (TV)")
        requestBuilder.header("X-Plex-Version", "1.0.0")
        requestBuilder.header("X-Plex-Device", Build.MODEL)
        requestBuilder.header("X-Plex-Model", Build.MODEL)

        val response = chain.proceed(requestBuilder.build())

        // Detect 401 and signal token invalidation
        if (response.code == 401) {
            authEventBus.emitTokenInvalid()
        }

        return response
    }
```

**Step 5: Update NetworkModule to inject AuthEventBus**

Edit `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt`:

Modify `provideAuthInterceptor` (lines 80-85):

```kotlin
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        settingsDataStore: com.chakir.plexhubtv.core.datastore.SettingsDataStore,
        @ApplicationScope scope: CoroutineScope,
        authEventBus: com.chakir.plexhubtv.core.common.auth.AuthEventBus
    ): AuthInterceptor {
        return AuthInterceptor(settingsDataStore, scope, authEventBus)
    }
```

**Step 6: Run test to verify it passes**

Run: `./gradlew :core:network:test --tests "AuthInterceptorTest" --rerun-tasks`

Expected: PASS (2/2 tests)

**Step 7: Commit**

```bash
git add core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt
git add core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt
git add core/network/src/test/java/com/chakir/plexhubtv/core/network/AuthInterceptorTest.kt
git add core/network/build.gradle.kts
git commit -m "feat(network): detect 401 responses and emit TokenInvalid events

- AuthInterceptor now injects AuthEventBus
- Emits TokenInvalid on any 401 response
- Returns original response (no retry logic)
- Unit tests verify emission behavior

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Add localized strings for session expiration

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-fr/strings.xml`

**Step 1: Add English strings**

Edit `app/src/main/res/values/strings.xml`, add at the end before `</resources>`:

```xml
    <!-- Session Expiration -->
    <string name="session_expired_title">Session Expired</string>
    <string name="session_expired_message">Your Plex session has expired. Please sign in again.</string>
    <string name="reconnect">Reconnect</string>
```

**Step 2: Add French strings**

Edit `app/src/main/res/values-fr/strings.xml`, add at the end before `</resources>`:

```xml
    <!-- Session Expiration -->
    <string name="session_expired_title">Session expirée</string>
    <string name="session_expired_message">Votre session Plex a expiré. Veuillez vous reconnecter.</string>
    <string name="reconnect">Se reconnecter</string>
```

**Step 3: Verify resources compile**

Run: `./gradlew :app:assembleDebug --dry-run`

Expected: Configuration successful

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-fr/strings.xml
git commit -m "feat(i18n): add session expiration strings (EN/FR)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Create SessionExpiredDialog composable

**Files:**
- Create: `app/src/main/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialog.kt`
- Test: `app/src/test/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialogTest.kt`

**Step 1: Write the UI test**

Create test file:

```kotlin
package com.chakir.plexhubtv.feature.auth.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SessionExpiredDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dialog displays title and message`() {
        composeTestRule.setContent {
            SessionExpiredDialog(onDismiss = {})
        }

        composeTestRule.onNodeWithText("Session Expired").assertIsDisplayed()
        composeTestRule.onNodeWithText("Your Plex session has expired. Please sign in again.").assertIsDisplayed()
    }

    @Test
    fun `clicking reconnect triggers onDismiss`() {
        var dismissed = false

        composeTestRule.setContent {
            SessionExpiredDialog(onDismiss = { dismissed = true })
        }

        composeTestRule.onNodeWithText("Reconnect").performClick()

        assertTrue(dismissed)
    }
}
```

**Step 2: Add Compose test dependencies**

Edit `app/build.gradle.kts`, add to androidTestImplementation:

```kotlin
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

**Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebug --tests "SessionExpiredDialogTest" --rerun-tasks`

Expected: FAIL with "Unresolved reference: SessionExpiredDialog"

**Step 4: Create the composable**

Create directory:
```bash
mkdir -p app/src/main/java/com/chakir/plexhubtv/feature/auth/components
```

Create file `app/src/main/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialog.kt`:

```kotlin
package com.chakir.plexhubtv.feature.auth.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chakir.plexhubtv.R

/**
 * Dialog displayed when user's Plex session has expired (401 error).
 *
 * Shows localized message explaining session expiration and provides
 * a "Reconnect" button to navigate back to authentication flow.
 *
 * @param onDismiss Callback invoked when user dismisses dialog (by clicking Reconnect)
 * @param modifier Optional modifier for styling
 */
@Composable
fun SessionExpiredDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.session_expired_title))
        },
        text = {
            Text(text = stringResource(R.string.session_expired_message))
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(R.string.reconnect))
            }
        },
        modifier = modifier
    )
}
```

**Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebug --tests "SessionExpiredDialogTest" --rerun-tasks`

Expected: PASS (2/2 tests)

**Step 6: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialog.kt
git add app/src/test/java/com/chakir/plexhubtv/feature/auth/components/SessionExpiredDialogTest.kt
git add app/build.gradle.kts
git commit -m "feat(ui): add SessionExpiredDialog composable

- AlertDialog with localized title/message
- Reconnect button triggers navigation
- Compose UI tests verify display and interaction

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 7: Create MainViewModel

**Files:**
- Create: `app/src/main/java/com/chakir/plexhubtv/MainViewModel.kt`
- Test: `app/src/test/java/com/chakir/plexhubtv/MainViewModelTest.kt`

**Step 1: Write the failing test**

Create test file:

```kotlin
package com.chakir.plexhubtv

import com.chakir.plexhubtv.core.common.auth.AuthEvent
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `handleTokenInvalid clears token and shows dialog`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockAuthRepository.clearToken() }
        assertTrue(viewModel.showSessionExpiredDialog.value)
    }

    @Test
    fun `multiple TokenInvalid events only clear token once`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        eventBus.emitTokenInvalid()
        eventBus.emitTokenInvalid()
        eventBus.emitTokenInvalid()
        advanceUntilIdle()

        coVerify(exactly = 1) { mockAuthRepository.clearToken() }
        assertTrue(viewModel.showSessionExpiredDialog.value)
    }

    @Test
    fun `onSessionExpiredDialogDismissed hides dialog and triggers navigation`() = runTest(testDispatcher) {
        val mockAuthRepository = mockk<AuthRepository>(relaxed = true)
        val eventBus = AuthEventBus()
        val viewModel = MainViewModel(eventBus, mockAuthRepository)

        // Show dialog first
        eventBus.emitTokenInvalid()
        advanceUntilIdle()
        assertTrue(viewModel.showSessionExpiredDialog.value)

        // Dismiss
        var navigated = false
        viewModel.onSessionExpiredDialogDismissed { navigated = true }

        assertFalse(viewModel.showSessionExpiredDialog.value)
        assertTrue(navigated)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebug --tests "MainViewModelTest" --rerun-tasks`

Expected: FAIL with "Unresolved reference: MainViewModel"

**Step 3: Write minimal implementation**

Create file `app/src/main/java/com/chakir/plexhubtv/MainViewModel.kt`:

```kotlin
package com.chakir.plexhubtv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.auth.AuthEvent
import com.chakir.plexhubtv.core.common.auth.AuthEventBus
import com.chakir.plexhubtv.domain.repository.AuthRepository
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application-level ViewModel for global auth coordination.
 *
 * Lives in MainActivity scope, survives navigation. Collects auth events
 * from AuthEventBus and coordinates dialog display and token clearing.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authEventBus: AuthEventBus,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _showSessionExpiredDialog = MutableStateFlow(false)
    val showSessionExpiredDialog: StateFlow<Boolean> = _showSessionExpiredDialog.asStateFlow()

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
        // Prevent dialog spam from multiple simultaneous 401s
        if (!_showSessionExpiredDialog.value) {
            Timber.w("Token invalidated (401 detected), clearing token and showing dialog")

            try {
                authRepository.clearToken()
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear token, but continuing to show dialog")
            }

            _showSessionExpiredDialog.value = true

            // Track session expiration for monitoring
            Firebase.analytics.logEvent("session_expired") {
                param("source", "401_interceptor")
            }
        }
    }

    /**
     * Called when user dismisses the session expired dialog.
     * Hides dialog and invokes navigation callback.
     *
     * @param navigateToAuth Lambda to navigate to auth screen (provided by caller)
     */
    fun onSessionExpiredDialogDismissed(navigateToAuth: () -> Unit) {
        _showSessionExpiredDialog.value = false
        navigateToAuth()
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebug --tests "MainViewModelTest" --rerun-tasks`

Expected: PASS (3/3 tests)

**Step 5: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/MainViewModel.kt
git add app/src/test/java/com/chakir/plexhubtv/MainViewModelTest.kt
git commit -m "feat(app): add MainViewModel for global auth coordination

- Collects TokenInvalid events from AuthEventBus
- Clears token and shows dialog on 401
- Prevents dialog spam with state flag
- Firebase Analytics tracking for session expiration
- Unit tests verify event handling and deduplication

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 8: Wire MainActivity to show dialog and navigate

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`

**Step 1: Read current MainActivity implementation**

Run: Read the file to understand current structure

**Step 2: Add MainViewModel injection and dialog observation**

Edit `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`:

Add ViewModel injection in MainActivity class:

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    // ... rest of existing code ...
```

Add dialog observation in `setContent` block (inside the composable, after NavHost or at top level):

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ... existing setup code ...

        setContent {
            // ... theme and other wrappers ...

            val showSessionExpiredDialog by mainViewModel.showSessionExpiredDialog.collectAsState()

            // Show session expired dialog if token invalidated
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

            // ... rest of UI (NavHost, etc.) ...
        }
    }
```

Add import:
```kotlin
import com.chakir.plexhubtv.feature.auth.components.SessionExpiredDialog
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.viewModels
```

**Step 3: Build and verify no compilation errors**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/chakir/plexhubtv/MainActivity.kt
git commit -m "feat(app): wire MainViewModel to show session expired dialog

- Inject MainViewModel in MainActivity
- Observe showSessionExpiredDialog state
- Display SessionExpiredDialog when true
- Navigate to auth with cleared back stack on dismiss

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 9: Manual Testing & Verification

**Files:**
- None (manual testing only)

**Step 1: Build and install app**

Run: `./gradlew :app:installDebug`

Expected: App installed successfully

**Step 2: Manual test - Trigger 401**

1. Launch app and authenticate with valid token
2. Invalidate token (via Plex web interface: revoke device access)
3. Trigger API call (browse library, play media)
4. Verify dialog appears with French/English text (based on device locale)
5. Tap "Se reconnecter" / "Reconnect"
6. Verify navigation to auth screen
7. Verify back button doesn't return to previous screen
8. Complete PIN authentication
9. Verify app works normally after re-auth

**Step 3: Manual test - Multiple 401s**

1. While dialog is showing, trigger another API call that returns 401
2. Verify only ONE dialog is shown (no duplicates)

**Step 4: Manual test - Analytics**

1. Check Firebase Console
2. Verify "session_expired" event logged with "source=401_interceptor"

**Step 5: Document results**

Create manual test report: `docs/testing/2026-02-19-session-expiration-manual-test-results.md`

```markdown
# Session Expiration Manual Test Results

**Date:** 2026-02-19
**Tester:** [Your Name]
**Build:** Debug

## Test Results

- [x] Dialog appears on 401
- [x] French localization correct
- [x] English localization correct
- [x] Navigation to auth screen works
- [x] Back stack cleared (back button doesn't return)
- [x] Re-authentication works
- [x] Only one dialog for multiple 401s
- [x] Firebase event logged

## Issues Found

[None / List any issues]

## Notes

[Any additional observations]
```

**Step 6: Commit test results**

```bash
git add docs/testing/2026-02-19-session-expiration-manual-test-results.md
git commit -m "test: manual verification of session expiration flow

All test cases passed. See report for details.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 10: Update MEMORY.md with new pattern

**Files:**
- Modify: `.claude/memory/MEMORY.md`

**Step 1: Add session expiration section**

Edit `.claude/memory/MEMORY.md`, add at the end:

```markdown

## Session Expiration Handling (SEC4)
- **AuthEventBus**: Global event channel for auth state changes (MutableSharedFlow)
- **401 Detection**: AuthInterceptor emits TokenInvalid event on any 401 response
- **MainViewModel**: App-level coordinator, survives navigation, shows dialog
- **No retry logic**: Interceptor returns 401 to caller, prevents infinite loops
- **Dialog deduplication**: Flag check prevents multiple dialogs from simultaneous 401s
- **Back stack clearing**: `popUpTo(0)` on navigation to auth prevents returning to authenticated screens
```

**Step 2: Commit**

```bash
git add .claude/memory/MEMORY.md
git commit -m "docs(memory): document session expiration pattern

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 11: Final verification and cleanup

**Files:**
- None (verification only)

**Step 1: Run all tests**

Run: `./gradlew test --rerun-tasks`

Expected: All tests pass

**Step 2: Run detekt**

Run: `./gradlew detekt`

Expected: No violations (or only pre-existing)

**Step 3: Run ktlint**

Run: `./gradlew ktlintCheck`

Expected: No formatting issues

**Step 4: Build release variant**

Run: `./gradlew :app:assembleRelease`

Expected: BUILD SUCCESSFUL

**Step 5: Verify implementation against design doc**

Check each success criterion from design doc:

- ✅ All 401 responses trigger AuthEvent.TokenInvalid emission
- ✅ Token is cleared from encrypted storage on 401
- ✅ Dialog displays with correct localized text (FR/EN)
- ✅ Dismissing dialog navigates to auth screen with cleared back stack
- ✅ Re-authentication via PIN flow works normally
- ✅ Multiple simultaneous 401s show only one dialog
- ✅ No infinite retry loops occur
- ✅ Unit tests pass with >80% coverage on new code
- ✅ Manual testing checklist completed

**Step 6: Final commit**

```bash
git commit --allow-empty -m "feat(P1): complete Plex token expiration handling (SEC4)

Implemented centralized 401 detection with user-friendly session
expiration handling:

- AuthEventBus for reactive event propagation
- AuthInterceptor detects 401 and emits TokenInvalid
- MainViewModel coordinates dialog + token clearing
- SessionExpiredDialog with FR/EN localization
- Navigation to auth with back stack clearing
- Firebase Analytics tracking
- Comprehensive unit tests
- Manual testing verified

All success criteria met. Ready for production.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Completion

**Total Tasks:** 11
**Estimated Time:** 2-3 hours for skilled Android developer
**Test Coverage:** Unit tests + Compose UI tests + Manual verification
**Documentation:** Design doc + Implementation plan + Test results + MEMORY.md

The implementation follows TDD principles with failing tests written first, minimal code to pass, and frequent commits. Each component is independently testable and follows clean architecture principles.
