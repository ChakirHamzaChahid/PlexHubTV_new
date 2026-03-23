# Issue #91 — AGENT-6-001: Mode Enfant ZERO filtrage de contenu

## Context

The Kids Mode profile system is **cosmetic only**: users can create a profile with `isKidsProfile = true` and an `AgeRating`, but **no content filtering is applied anywhere**. All ViewModels (Home, Library, Search, MediaDetail) load and display content without checking the active profile. This means children on a "kids profile" see the same R-rated, 18+, and XXX content as adults.

**Risk**: COPPA/GDPR non-compliance — the feature implies parental control but delivers none.

**What already exists**:
- `Profile` model with `isKidsProfile` and `ageRating` fields (core/model)
- `ProfileRepository` with `getActiveProfile()` / `getActiveProfileFlow()` (domain)
- `FilterContentByAgeUseCase` — filters `List<MediaItem>` by age rating (just created in #105)
- `ContentRatingHelper` — normalizes Plex ratings to "TP", "6+", "12+", "18+", etc.
- `SecurePreferencesManager` — EncryptedSharedPreferences for sensitive data

**What's missing**:
- No ViewModel calls `FilterContentByAgeUseCase`
- No parental PIN protection
- No guard preventing kids from switching to adult profiles

---

## Implementation Plan

### Step 1: Parental PIN storage in SecurePreferencesManager

**File**: `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SecurePreferencesManager.kt`

Add 3 methods following the existing pattern (savePlexToken/getPlexToken):

```kotlin
fun saveParentalPin(pin: String) {
    prefs.edit().putString("parental_pin", pin).apply()
}

fun getParentalPin(): String? =
    prefs.getString("parental_pin", null)

fun clearParentalPin() {
    prefs.edit().remove("parental_pin").apply()
}
```

Store the PIN as plaintext in EncryptedSharedPreferences (the file itself is AES-256 encrypted — same approach used for Plex tokens).

### Step 2: Parental PIN in SettingsRepository

**File**: `domain/src/main/java/com/chakir/plexhubtv/domain/repository/SettingsRepository.kt`

Add interface methods:
```kotlin
fun getParentalPin(): String?
fun setParentalPin(pin: String?)
fun hasParentalPin(): Boolean
fun verifyParentalPin(input: String): Boolean
```

**File**: `data/src/main/java/com/chakir/plexhubtv/data/repository/SettingsRepositoryImpl.kt`

Implement using SecurePreferencesManager.

### Step 3: ParentalPinDialog composable

**New file**: `app/src/main/java/com/chakir/plexhubtv/core/ui/ParentalPinDialog.kt`

A reusable PIN input dialog with:
- 4-digit numeric PIN input (D-Pad friendly — grid of number buttons like Netflix)
- Title: "Enter Parental PIN" / "Set Parental PIN"
- Error state for wrong PIN
- Cancel button

Two modes:
- **SetPin**: For setting/changing the PIN (confirm entry twice)
- **VerifyPin**: For verifying before switching profiles

### Step 4: Filter content in HomeViewModel

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/home/HomeViewModel.kt`

Inject `ProfileRepository` + `FilterContentByAgeUseCase`. In `collectSharedContent()`, after receiving `content.onDeck`, filter items:

```kotlin
// In constructor: add profileRepository and filterContentByAgeUseCase
// In collectSharedContent(), after onSuccess:
val activeProfile = profileRepository.getActiveProfile()
val filtered = if (activeProfile != null) {
    filterContentByAgeUseCase(content.onDeck, activeProfile)
} else content.onDeck

_uiState.update { it.copy(isLoading = false, onDeck = filtered.toImmutableList()) }
```

### Step 5: Filter content in SearchViewModel

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/search/SearchViewModel.kt`

Inject `ProfileRepository` + `FilterContentByAgeUseCase`. In `performSearch()`, after `onSuccess`:

```kotlin
val activeProfile = profileRepository.getActiveProfile()
val filtered = if (activeProfile != null) {
    filterContentByAgeUseCase(items, activeProfile)
} else items
_uiState.update { it.copy(results = filtered, searchState = ...) }
```

### Step 6: Filter content in LibraryViewModel

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt`

Inject `ProfileRepository` + `FilterContentByAgeUseCase`. Apply PagingData filter in the `pagedItems` flow pipeline:

```kotlin
// After .cachedIn(viewModelScope), add:
.map { pagingData ->
    val profile = profileRepository.getActiveProfile()
    if (profile != null && (profile.isKidsProfile || profile.ageRating != AgeRating.ADULT)) {
        pagingData.filter { item -> filterContentByAgeUseCase.isItemAllowed(item, profile) }
    } else pagingData
}
```

This requires exposing a public `isItemAllowed(item, profile): Boolean` method on `FilterContentByAgeUseCase` (extract from the private `isAllowed` + `getMaxAge` logic).

### Step 7: Filter similar media in MediaDetailViewModel

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt`

Inject `ProfileRepository` + `FilterContentByAgeUseCase`. Filter `similarItems` after loading (line 449):

```kotlin
val activeProfile = profileRepository.getActiveProfile()
val filtered = if (activeProfile != null) filterContentByAgeUseCase(items, activeProfile) else items
_uiState.update { it.copy(similarItems = filtered) }
```

### Step 8: PIN guard on profile switching

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileViewModel.kt`

Modify `selectProfile()` (line 104):
- If current active profile `isKidsProfile` AND the target profile is NOT kids → require PIN verification
- Add new UI state fields: `showPinDialog: Boolean`, `pinError: String?`, `pendingProfileSwitch: Profile?`
- Add new action: `AppProfileAction.VerifyPin(pin: String)`

Flow:
1. User clicks non-kids profile while on kids profile → `showPinDialog = true`, `pendingProfileSwitch = target`
2. User enters PIN → `VerifyPin(pin)` → verify against `settingsRepository.verifyParentalPin(pin)`
3. If correct → proceed with `switchProfile(pendingProfileSwitch)` → navigate to home
4. If wrong → `pinError = "Wrong PIN"`

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileUiState.kt`

Add to `AppProfileUiState`:
```kotlin
val showPinDialog: Boolean = false,
val pinError: String? = null,
val pendingProfileSwitch: Profile? = null,
```

Add to `AppProfileAction`:
```kotlin
data class VerifyPin(val pin: String) : AppProfileAction
data object DismissPin : AppProfileAction
```

### Step 9: PIN setup in Settings

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`

Add a "Parental PIN" section with:
- "Set Parental PIN" / "Change Parental PIN" button
- Show `ParentalPinDialog` in SetPin mode when clicked
- Save via `settingsRepository.setParentalPin(pin)`

### Step 10: Integrate ParentalPinDialog in profile screens

**File**: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSelectionScreen.kt`
**File**: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSwitchScreen.kt`

Show `ParentalPinDialog` when `uiState.showPinDialog == true`.

---

## Files to Create

1. `app/src/main/java/com/chakir/plexhubtv/core/ui/ParentalPinDialog.kt` — PIN input dialog

## Files to Modify

2. `core/datastore/.../SecurePreferencesManager.kt` — Add PIN storage methods
3. `domain/.../repository/SettingsRepository.kt` — Add PIN interface methods
4. `data/.../repository/SettingsRepositoryImpl.kt` — Implement PIN methods
5. `domain/.../usecase/FilterContentByAgeUseCase.kt` — Expose `isItemAllowed()` for PagingData
6. `app/.../feature/home/HomeViewModel.kt` — Filter onDeck content
7. `app/.../feature/search/SearchViewModel.kt` — Filter search results
8. `app/.../feature/library/LibraryViewModel.kt` — Filter paged library content
9. `app/.../feature/details/MediaDetailViewModel.kt` — Filter similar media
10. `app/.../feature/appprofile/AppProfileViewModel.kt` — PIN guard on profile switch
11. `app/.../feature/appprofile/AppProfileUiState.kt` — Add PIN UI state + actions
12. `app/.../feature/appprofile/AppProfileSelectionScreen.kt` — Show PIN dialog
13. `app/.../feature/appprofile/AppProfileSwitchScreen.kt` — Show PIN dialog
14. `app/.../feature/settings/SettingsScreen.kt` — Add "Set Parental PIN" option
15. `app/src/main/res/values/strings.xml` — PIN-related strings (EN)
16. `app/src/main/res/values-fr/strings.xml` — PIN-related strings (FR)

## Verification

1. **Build**: `.\gradlew.bat :app:compileDebugKotlin` passes
2. **Existing tests**: `.\gradlew.bat :domain:testDebugUnitTest --tests '*.FilterContentByAgeUseCaseTest'` still passes
3. **Manual test on Android TV**:
   - Create a kids profile (isKidsProfile = true) → switch to it → Home/Library/Search should show only TP/6+ content
   - Create an adult profile → switch to it → all content visible
   - Set parental PIN in Settings → switch to kids profile → try switching back → PIN dialog appears
   - Wrong PIN → error shown, switch blocked
   - Correct PIN → profile switches successfully
