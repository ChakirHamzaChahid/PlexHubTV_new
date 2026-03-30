# Complete App Profiles Feature

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

## Context

The App Profiles system has complete backend (Repository, DAO, Entity, ViewModel actions) but incomplete UI. Profile CRUD buttons/dialogs are missing, and the startup flow doesn't show profile selection when multiple profiles exist. This plan wires the existing backend to a complete Netflix-style profile experience.

**What exists:**
- `ProfileRepository` + `ProfileRepositoryImpl` — full CRUD (create, update, delete, switch, ensureDefault)
- `ProfileDao` + `ProfileEntity` — Room persistence with `isActive` flag
- `AppProfileViewModel` — handles all actions, dialog state flags ready
- `AppProfileSelectionScreen` — profile grid with focus/animation (working)
- `AppProfileSwitchScreen` — profile list (skeleton, no CRUD buttons)
- `AppProfileUiState` — has `showCreateDialog`, `showEditDialog`, `profileToEdit` flags

**What's missing:**
1. Create/Edit profile form dialog (name + emoji picker + kids toggle)
2. Delete confirmation dialog
3. Edit/Delete buttons on profile items in Switch screen
4. "Add Profile" button in Switch screen
5. Startup navigation: show profile selection when 2+ profiles exist
6. New actions: `SubmitCreate`, `SubmitEdit`, `ConfirmDelete`

## Files to Modify

1. `app/.../feature/appprofile/AppProfileUiState.kt` — Add new actions + delete confirmation state
2. `app/.../feature/appprofile/AppProfileViewModel.kt` — Handle new submit/confirm actions
3. `app/.../feature/appprofile/ProfileFormDialog.kt` — **NEW** Create/Edit form dialog
4. `app/.../feature/appprofile/AppProfileSwitchScreen.kt` — Wire CRUD buttons + dialogs
5. `app/.../feature/appprofile/AppProfileSelectionScreen.kt` — Wire "+" card to create dialog
6. `app/.../feature/loading/LoadingViewModel.kt` — Profile count check at startup
7. `app/.../feature/loading/LoadingUiState.kt` — Add NavigateToProfileSelection event
8. `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt` — Wire new navigation event

**Reference patterns:**
- `RemoveFromOnDeckDialog.kt` — TV-friendly delete confirmation (dark overlay, focus handling)
- `XtreamSetupScreen.kt` — `OutlinedTextField` colors for TV
- `PlexHomeSwitcherScreen.kt` — `Dialog` with Card pattern

---

## Step-by-Step

### Step 1: Extend UiState + Actions

**File**: `app/.../feature/appprofile/AppProfileUiState.kt`

Add delete confirmation state fields:
```kotlin
data class AppProfileUiState(
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val showCreateDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val profileToEdit: Profile? = null,
    val showDeleteConfirmation: Boolean = false,  // NEW
    val profileToDelete: Profile? = null,          // NEW
)
```

Add new actions for form submission:
```kotlin
sealed interface AppProfileAction {
    // existing...
    data class SubmitCreateProfile(
        val name: String,
        val avatarEmoji: String,
        val isKidsProfile: Boolean,
    ) : AppProfileAction  // NEW
    data class SubmitEditProfile(
        val id: String,
        val name: String,
        val avatarEmoji: String,
        val isKidsProfile: Boolean,
    ) : AppProfileAction  // NEW
    data class ConfirmDeleteProfile(val profile: Profile) : AppProfileAction  // NEW (shows dialog)
    // DeleteProfile(profileId) stays — executes actual delete after confirmation
}
```

### Step 2: Handle new actions in ViewModel

**File**: `app/.../feature/appprofile/AppProfileViewModel.kt`

Add handlers in `onAction()`:
```kotlin
is AppProfileAction.SubmitCreateProfile -> submitCreateProfile(action)
is AppProfileAction.SubmitEditProfile -> submitEditProfile(action)
is AppProfileAction.ConfirmDeleteProfile -> showDeleteConfirmation(action.profile)
```

Add methods:
```kotlin
private fun submitCreateProfile(action: AppProfileAction.SubmitCreateProfile) {
    viewModelScope.launch {
        val profile = Profile(
            id = java.util.UUID.randomUUID().toString(),
            name = action.name.trim(),
            avatarEmoji = action.avatarEmoji,
            isKidsProfile = action.isKidsProfile,
        )
        val result = profileRepository.createProfile(profile)
        if (result.isFailure) {
            _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
        }
        dismissDialog()
    }
}

private fun submitEditProfile(action: AppProfileAction.SubmitEditProfile) {
    viewModelScope.launch {
        val existing = profileRepository.getProfileById(action.id) ?: return@launch
        val updated = existing.copy(
            name = action.name.trim(),
            avatarEmoji = action.avatarEmoji,
            isKidsProfile = action.isKidsProfile,
        )
        val result = profileRepository.updateProfile(updated)
        if (result.isFailure) {
            _uiState.update { it.copy(error = result.exceptionOrNull()?.message) }
        }
        dismissDialog()
    }
}

private fun showDeleteConfirmation(profile: Profile) {
    _uiState.update { it.copy(showDeleteConfirmation = true, profileToDelete = profile) }
}
```

Update `dismissDialog()` to also clear delete confirmation:
```kotlin
private fun dismissDialog() {
    _uiState.update {
        it.copy(
            showCreateDialog = false,
            showEditDialog = false,
            profileToEdit = null,
            showDeleteConfirmation = false,
            profileToDelete = null,
        )
    }
}
```

### Step 3: Create ProfileFormDialog

**File**: `app/.../feature/appprofile/ProfileFormDialog.kt` — **NEW FILE**

TV-optimized dialog with:
- `Dialog(onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false))`
- Dark overlay (`Color.Black.copy(alpha = 0.7f)`)
- `Surface` with `RoundedCornerShape(12.dp)`, color `Color(0xFF1A1A1A)`
- Name `OutlinedTextField` (TV colors from `XtreamSetupScreen` pattern)
- Emoji picker: `FlowRow` of 12 emoji options, each a clickable `Surface` with focus border
- Kids toggle: `Switch` component
- Buttons: Save (primary, gets `FocusRequester`) | Cancel (gray)

Emoji list:
```kotlin
private val AVATAR_EMOJIS = listOf(
    "😊", "😎", "🎬", "🎮", "🎯", "👤",
    "🦊", "🐱", "🎨", "🌟", "🚀", "🎵",
)
```

Parameters:
```kotlin
@Composable
fun ProfileFormDialog(
    isEdit: Boolean,
    initialName: String = "",
    initialEmoji: String = "😊",
    initialIsKids: Boolean = false,
    onSubmit: (name: String, emoji: String, isKids: Boolean) -> Unit,
    onDismiss: () -> Unit,
)
```

### Step 4: Create DeleteProfileConfirmDialog

**File**: Same `ProfileFormDialog.kt` (colocated)

Follows `RemoveFromOnDeckDialog` pattern exactly:
- Dark overlay, Surface 400dp wide
- "Delete profile [Name]?" title
- "This cannot be undone." subtitle
- Delete (red, focus) | Cancel (gray) buttons

```kotlin
@Composable
fun DeleteProfileConfirmDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
)
```

### Step 5: Wire dialogs into AppProfileSwitchScreen

**File**: `app/.../feature/appprofile/AppProfileSwitchScreen.kt`

Changes:
1. Add "Add Profile" button in TopAppBar actions area
2. Add Edit (pencil) + Delete (trash) icon buttons to `AppProfileListItem`
3. Show `ProfileFormDialog` when `state.showCreateDialog` or `state.showEditDialog`
4. Show `DeleteProfileConfirmDialog` when `state.showDeleteConfirmation`

In `AppProfileSwitchScreen` composable, after the Scaffold:
```kotlin
// Dialogs
if (state.showCreateDialog) {
    ProfileFormDialog(
        isEdit = false,
        onSubmit = { name, emoji, isKids ->
            onAction(AppProfileAction.SubmitCreateProfile(name, emoji, isKids))
        },
        onDismiss = { onAction(AppProfileAction.DismissDialog) },
    )
}

if (state.showEditDialog && state.profileToEdit != null) {
    ProfileFormDialog(
        isEdit = true,
        initialName = state.profileToEdit.name,
        initialEmoji = state.profileToEdit.avatarEmoji ?: "😊",
        initialIsKids = state.profileToEdit.isKidsProfile,
        onSubmit = { name, emoji, isKids ->
            onAction(AppProfileAction.SubmitEditProfile(
                state.profileToEdit.id, name, emoji, isKids
            ))
        },
        onDismiss = { onAction(AppProfileAction.DismissDialog) },
    )
}

if (state.showDeleteConfirmation && state.profileToDelete != null) {
    DeleteProfileConfirmDialog(
        profileName = state.profileToDelete.name,
        onConfirm = { onAction(AppProfileAction.DeleteProfile(state.profileToDelete.id)) },
        onDismiss = { onAction(AppProfileAction.DismissDialog) },
    )
}
```

Update `AppProfileListItem` signature to add edit/delete callbacks:
```kotlin
@Composable
fun AppProfileListItem(
    profile: Profile,
    isCurrentProfile: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,    // NEW
    onDelete: () -> Unit,  // NEW
)
```

Add edit/delete icons in the Row after the checkmark:
```kotlin
IconButton(onClick = onEdit) {
    Icon(Icons.Filled.Edit, contentDescription = "Edit")
}
// Only show delete if not current profile
if (!isCurrentProfile) {
    IconButton(onClick = onDelete) {
        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
    }
}
```

### Step 6: Wire "+" card in AppProfileSelectionScreen

**File**: `app/.../feature/appprofile/AppProfileSelectionScreen.kt`

The "+" card currently calls `onManageProfiles` which navigates to `AppProfileSwitch`. Instead, make it trigger `CreateProfile` action directly.

In `AppProfileSelectionRoute`:
- Pass `showCreateDialog` flag from uiState
- Show `ProfileFormDialog` when `showCreateDialog` is true

```kotlin
// In AppProfileSelectionRoute, after the if/else block:
if (uiState.showCreateDialog) {
    ProfileFormDialog(
        isEdit = false,
        onSubmit = { name, emoji, isKids ->
            viewModel.onAction(AppProfileAction.SubmitCreateProfile(name, emoji, isKids))
        },
        onDismiss = { viewModel.onAction(AppProfileAction.DismissDialog) },
    )
}
```

Change `AddAppProfileCard` callback:
```kotlin
AddAppProfileCard(onClick = { viewModel.onAction(AppProfileAction.CreateProfile) })
```

### Step 7: Startup navigation — show profile selection for 2+ profiles

**File**: `app/.../feature/loading/LoadingViewModel.kt`

Inject `ProfileRepository` and check profile count when navigating to Main:

```kotlin
@HiltViewModel
class LoadingViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val workManager: WorkManager,
    private val profileRepository: ProfileRepository,  // NEW
) : ViewModel() {
```

Replace `_navigationEvent.emit(NavigateToMain)` calls with:
```kotlin
private suspend fun navigateAfterSync() {
    profileRepository.ensureDefaultProfile()
    val profileCount = profileRepository.getProfileCount()
    if (profileCount > 1) {
        _navigationEvent.emit(LoadingNavigationEvent.NavigateToProfileSelection)
    } else {
        _navigationEvent.emit(LoadingNavigationEvent.NavigateToMain)
    }
}
```

Call `navigateAfterSync()` in both locations where `NavigateToMain` was emitted (line 45 and line 76).

**File**: `app/.../feature/loading/LoadingViewModel.kt` — Add navigation event:
```kotlin
sealed class LoadingNavigationEvent {
    data object NavigateToMain : LoadingNavigationEvent()
    data object NavigateToAuth : LoadingNavigationEvent()
    data object NavigateToLibrarySelection : LoadingNavigationEvent()
    data object NavigateToProfileSelection : LoadingNavigationEvent()  // NEW
}
```

**File**: `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt` line ~160

In the `LoadingRoute` composable, add handler:
```kotlin
onNavigateToProfileSelection = {
    navController.navigate(Screen.AppProfileSelection.route) {
        popUpTo(Screen.Loading.route) { inclusive = true }
    }
},
```

And update `LoadingRoute` to accept this callback (in loading feature).

### Step 8: Build and verify

```bash
./gradlew assembleDebug
./gradlew compileDebugUnitTestKotlin
```

### Step 9: Commit

```bash
git commit -m "feat: complete App Profiles CRUD with startup selection

- Add ProfileFormDialog with name input, emoji picker, kids toggle
- Add DeleteProfileConfirmDialog with confirmation
- Wire edit/delete buttons in AppProfileSwitchScreen
- Wire add profile in AppProfileSelectionScreen
- Show profile selection on startup when 2+ profiles exist
- Add SubmitCreateProfile, SubmitEditProfile, ConfirmDeleteProfile actions"
```

## Key Design Decisions

- **No DataStore needed**: Room's `isActive` flag already persists across restarts. Profile count check at startup is sufficient.
- **Startup check in LoadingViewModel**: Centralized — runs after sync, before Main. Avoids modifying SplashViewModel.
- **Shared ProfileFormDialog**: Single dialog handles both create and edit via `isEdit` flag + initial values.
- **Delete safety**: Repository already guards against deleting active/last profile. UI adds confirmation dialog as extra layer.
- **12 emoji grid**: Simple, hardcoded list. No massive picker — this is a TV app with D-pad navigation.
- **"+" card creates directly**: Selection screen's "+" shows create dialog immediately instead of navigating to manage screen.

## Verification

1. `./gradlew assembleDebug` — build passes
2. **Create profile**: Selection screen → "+" → fill form → Save → profile appears in grid
3. **Edit profile**: Manage Profiles → Edit icon → change name/emoji → Save → updated
4. **Delete profile**: Manage Profiles → Delete icon → Confirm → profile removed
5. **Delete guard**: Active profile delete icon hidden. Last profile cannot be deleted (error from repository).
6. **Startup flow (1 profile)**: Launch app → straight to Main (no selection)
7. **Startup flow (2+ profiles)**: Launch app → profile selection → pick → Main
8. **TV navigation**: All dialogs focusable via D-pad, first button auto-focused
