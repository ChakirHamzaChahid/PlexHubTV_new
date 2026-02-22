# Profile System Refactor — Two Distinct Features

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Séparer clairement deux fonctionnalités de profils qui coexistent dans le code : le **switch d'utilisateur Plex Home** (authentification serveur) et la **gestion de profils locaux** (préférences app par utilisateur). Renommer, câbler dans la navigation, et rendre les deux accessibles.

**Architecture:** Deux packages distincts avec des noms sans ambiguïté :
- `feature/plexhome/` — Switch d'utilisateur Plex Home (qui regarde sur ce serveur ?)
- `feature/appprofile/` — Profils locaux de l'app (préférences de lecture, mode enfant, qualité vidéo)

Le PlexHome switch est accessible depuis Settings ("Switch Plex User") et aussi dans le flow d'auth initial. Les profils locaux sont accessibles depuis le bouton profil de la TopBar et depuis Settings ("Manage Profiles").

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, Navigation Compose, StateFlow

---

## Vue d'ensemble des deux fonctionnalités

### Feature A : PlexHomeSwitcher (renommage de `feature/auth/profiles/`)

**But fonctionnel :** Quand un compte Plex est partagé en famille (Plex Home), plusieurs utilisateurs existent sur le même serveur. Cet écran permet de choisir "Qui regarde ?" — exactement comme Netflix. Chaque utilisateur peut avoir un PIN de protection. Après le switch, le token d'auth change et tout le contenu se recharge pour cet utilisateur.

**Quand y accéder :**
- Au login initial (flow d'auth existant)
- Depuis Settings > "Switch Plex User" (nouveau)

**Ce qui existe déjà (à renommer) :**
- `feature/auth/profiles/ProfileScreen.kt` → `feature/plexhome/PlexHomeSwitcherScreen.kt`
- `feature/auth/profiles/ProfileViewModel.kt` → `feature/plexhome/PlexHomeSwitcherViewModel.kt`
- `feature/auth/profiles/ProfileUiState.kt` → `feature/plexhome/PlexHomeSwitcherUiState.kt`

---

### Feature B : AppProfiles (renommage de `feature/profile/`)

**But fonctionnel :** Profils **locaux à l'application**, indépendants de Plex. Comme les profils Netflix, chaque profil stocke ses propres préférences : qualité vidéo préférée, langues audio/sous-titres, mode enfant (filtrage par âge), autoplay. Un appareil peut avoir jusqu'à 5 profils. Le profil actif détermine les préférences de lecture appliquées.

**Quand y accéder :**
- Depuis le bouton profil (avatar) dans la TopBar (nouveau — remplace la nav vers Settings)
- Depuis Settings > "Manage Profiles" (nouveau)

**Ce qui existe déjà (à renommer) :**
- `feature/profile/ProfileSelectionScreen.kt` → `feature/appprofile/AppProfileSelectionScreen.kt`
- `feature/profile/ProfileSwitchScreen.kt` → `feature/appprofile/AppProfileSwitchScreen.kt`
- `feature/profile/ProfileViewModel.kt` → `feature/appprofile/AppProfileViewModel.kt`
- `feature/profile/ProfileUiState.kt` → `feature/appprofile/AppProfileUiState.kt`

**Infrastructure existante (déjà fonctionnelle) :**
- `ProfileRepository` + `ProfileRepositoryImpl` (CRUD complet, DI câblé)
- `ProfileDao` (Room, toutes les requêtes)
- `ProfileEntity` + `Profile` (domain model avec AgeRating, VideoQuality, etc.)

---

## Plan d'implémentation détaillé

---

### Task 1 : Renommer le package PlexHome Switcher

**Files:**
- Rename: `app/src/main/java/com/chakir/plexhubtv/feature/auth/profiles/` → `app/src/main/java/com/chakir/plexhubtv/feature/plexhome/`
- Modify: `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt` (import update)

**Step 1: Créer le nouveau package et déplacer les fichiers**

Créer `feature/plexhome/` et y déplacer les 3 fichiers en les renommant :

| Ancien | Nouveau |
|--------|---------|
| `auth/profiles/ProfileScreen.kt` | `plexhome/PlexHomeSwitcherScreen.kt` |
| `auth/profiles/ProfileViewModel.kt` | `plexhome/PlexHomeSwitcherViewModel.kt` |
| `auth/profiles/ProfileUiState.kt` | `plexhome/PlexHomeSwitcherUiState.kt` |

**Step 2: Renommer les classes internes**

Dans `PlexHomeSwitcherScreen.kt` :
```kotlin
package com.chakir.plexhubtv.feature.plexhome

// Renommer les composables :
// ProfileRoute → PlexHomeSwitcherRoute
// ProfileScreen → PlexHomeSwitcherScreen
// UserProfileCard → PlexHomeUserCard
// PinEntryDialog → PlexHomePinDialog
```

Dans `PlexHomeSwitcherViewModel.kt` :
```kotlin
package com.chakir.plexhubtv.feature.plexhome

// Renommer : ProfileViewModel → PlexHomeSwitcherViewModel
```

Dans `PlexHomeSwitcherUiState.kt` :
```kotlin
package com.chakir.plexhubtv.feature.plexhome

// Renommer :
// ProfileUiState → PlexHomeSwitcherUiState
// ProfileAction → PlexHomeSwitcherAction
```

**Step 3: Mettre à jour l'import dans MainActivity.kt**

```kotlin
// Avant :
import com.chakir.plexhubtv.feature.auth.profiles.ProfileRoute
// Après :
import com.chakir.plexhubtv.feature.plexhome.PlexHomeSwitcherRoute
```

Et dans le composable :
```kotlin
composable(Screen.Profiles.route) {
    PlexHomeSwitcherRoute(  // était ProfileRoute
        onSwitchSuccess = { ... },
        onBack = { ... },
    )
}
```

**Step 4: Renommer la route Screen**

Dans `Screen.kt` :
```kotlin
// Avant :
data object Profiles : Screen("profiles")
// Après :
data object PlexHomeSwitch : Screen("plex_home_switch")
```

Mettre à jour toutes les références `Screen.Profiles` → `Screen.PlexHomeSwitch` dans `MainActivity.kt`.

**Step 5: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "refactor: rename auth/profiles → plexhome/PlexHomeSwitcher for clarity"
```

---

### Task 2 : Renommer le package AppProfile

**Files:**
- Rename: `app/src/main/java/com/chakir/plexhubtv/feature/profile/` → `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/`

**Step 1: Créer le nouveau package et déplacer les fichiers**

| Ancien | Nouveau |
|--------|---------|
| `profile/ProfileSelectionScreen.kt` | `appprofile/AppProfileSelectionScreen.kt` |
| `profile/ProfileSwitchScreen.kt` | `appprofile/AppProfileSwitchScreen.kt` |
| `profile/ProfileViewModel.kt` | `appprofile/AppProfileViewModel.kt` |
| `profile/ProfileUiState.kt` | `appprofile/AppProfileUiState.kt` |

**Step 2: Renommer les classes internes**

Dans `AppProfileSelectionScreen.kt` :
```kotlin
package com.chakir.plexhubtv.feature.appprofile

// Renommer :
// ProfileSelectionRoute → AppProfileSelectionRoute
// ProfileSelectionScreen → AppProfileSelectionScreen
// ProfileCard → AppProfileCard
// AddProfileCard → AddAppProfileCard
```

Dans `AppProfileSwitchScreen.kt` :
```kotlin
package com.chakir.plexhubtv.feature.appprofile

// Renommer :
// ProfileSwitchRoute → AppProfileSwitchRoute
// ProfileSwitchScreen → AppProfileSwitchScreen
// ProfileListItem → AppProfileListItem
```

Dans `AppProfileViewModel.kt` :
```kotlin
package com.chakir.plexhubtv.feature.appprofile

// Renommer : ProfileViewModel → AppProfileViewModel
```

Dans `AppProfileUiState.kt` :
```kotlin
package com.chakir.plexhubtv.feature.appprofile

// Renommer :
// ProfileUiState → AppProfileUiState
// ProfileAction → AppProfileAction
// ProfileNavigationEvent → AppProfileNavigationEvent
```

**Step 3: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "refactor: rename profile → appprofile/AppProfile for clarity"
```

---

### Task 3 : Ajouter les routes de navigation pour AppProfile

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/di/navigation/Screen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`

**Step 1: Ajouter les routes dans Screen.kt**

```kotlin
// --- Main Graph --- (ajouter après Settings)
data object AppProfileSelection : Screen("app_profile_selection")
data object AppProfileSwitch : Screen("app_profile_switch")
```

**Step 2: Ajouter les composables dans MainActivity NavHost**

Dans `MainActivity.kt`, ajouter après le composable `Screen.PlexHomeSwitch` :

```kotlin
composable(Screen.AppProfileSelection.route) {
    com.chakir.plexhubtv.feature.appprofile.AppProfileSelectionRoute(
        onProfileSelected = {
            navController.navigate(Screen.Main.route) {
                popUpTo(Screen.AppProfileSelection.route) { inclusive = true }
            }
        },
        onManageProfiles = {
            // Future: navigate to profile management screen
        },
        onBack = {
            navController.popBackStack()
        },
    )
}

composable(Screen.AppProfileSwitch.route) {
    com.chakir.plexhubtv.feature.appprofile.AppProfileSwitchRoute(
        onProfileSwitched = {
            navController.popBackStack()
        },
        onBack = {
            navController.popBackStack()
        },
    )
}
```

> **Note :** Les callbacks `onProfileSelected`, `onManageProfiles`, `onBack` doivent correspondre aux paramètres de `AppProfileSelectionRoute`. Si les signatures existantes diffèrent, adapter les noms de paramètres dans l'écran pour correspondre (voir Task 4).

**Step 3: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: add navigation routes for AppProfile selection and switch"
```

---

### Task 4 : Adapter les signatures de AppProfileSelectionRoute et AppProfileSwitchRoute

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSelectionScreen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/appprofile/AppProfileSwitchScreen.kt`

**Step 1: Adapter AppProfileSelectionRoute**

La route existante utilise probablement des `NavigationEvent` via Channel. Il faut s'assurer que les callbacks de navigation correspondent à ce que `MainActivity` attend :

```kotlin
@Composable
fun AppProfileSelectionRoute(
    viewModel: AppProfileViewModel = hiltViewModel(),
    onProfileSelected: () -> Unit,
    onManageProfiles: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is AppProfileNavigationEvent.NavigateToHome -> onProfileSelected()
                is AppProfileNavigationEvent.NavigateToManageProfiles -> onManageProfiles()
                is AppProfileNavigationEvent.NavigateBack -> onBack()
            }
        }
    }

    AppProfileSelectionScreen(
        state = uiState,
        onAction = viewModel::onAction,
    )
}
```

**Step 2: Adapter AppProfileSwitchRoute**

```kotlin
@Composable
fun AppProfileSwitchRoute(
    viewModel: AppProfileViewModel = hiltViewModel(),
    onProfileSwitched: () -> Unit,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is AppProfileNavigationEvent.NavigateToHome -> onProfileSwitched()
                is AppProfileNavigationEvent.NavigateBack -> onBack()
                else -> {}
            }
        }
    }

    AppProfileSwitchScreen(
        state = uiState,
        onAction = viewModel::onAction,
        onBack = onBack,
    )
}
```

**Step 3: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add -A && git commit -m "feat: adapt AppProfile route signatures for MainActivity integration"
```

---

### Task 5 : Câbler le bouton profil TopBar → AppProfileSelection

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/main/MainScreen.kt`
- Modify: `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/NetflixTopBar.kt`

**Step 1: Ajouter un callback `onNavigateToProfiles` dans MainScreen**

Dans la signature de `MainScreen` :
```kotlin
@Composable
fun MainScreen(
    // ... paramètres existants ...
    onNavigateToProfiles: () -> Unit,  // NOUVEAU
    onLogout: () -> Unit,
) {
```

**Step 2: Changer onProfileClick dans MainScreen**

```kotlin
// Avant :
onProfileClick = { navController.navigate(Screen.Settings.route) },
// Après :
onProfileClick = { onNavigateToProfiles() },
```

**Step 3: Câbler dans MainActivity**

Dans `MainActivity.kt`, composable `Screen.Main.route` :
```kotlin
com.chakir.plexhubtv.feature.main.MainScreen(
    // ... existants ...
    onNavigateToProfiles = {
        navController.navigate(Screen.AppProfileSelection.route)
    },
    onLogout = { ... },
)
```

**Step 4: Mettre à jour l'icône du profil dans NetflixTopBar**

Dans `NetflixProfileAvatar`, changer l'icône Settings par une icône profil :
```kotlin
Icon(
    imageVector = Icons.Default.AccountCircle,  // était Icons.Default.Settings
    contentDescription = stringResource(R.string.topbar_profile_description),  // nouvelle string
    tint = Color.White,
    modifier = Modifier.size(20.dp)
)
```

Ajouter la string resource :
```xml
<!-- core/ui/src/main/res/values/strings.xml -->
<string name="topbar_profile_description">Switch profile</string>
```

**Step 5: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add -A && git commit -m "feat: wire TopBar profile button to AppProfileSelection screen"
```

---

### Task 6 : Ajouter les entrées PlexHome et AppProfile dans Settings

**Files:**
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Ajouter les actions dans SettingsAction**

Dans `SettingsUiState.kt` :
```kotlin
sealed interface SettingsAction {
    // ... existants ...
    data object SwitchPlexUser : SettingsAction      // NOUVEAU
    data object ManageAppProfiles : SettingsAction    // NOUVEAU
}
```

**Step 2: Ajouter les events de navigation**

Dans `SettingsViewModel.kt`, `SettingsNavigationEvent` :
```kotlin
sealed interface SettingsNavigationEvent {
    data object NavigateBack : SettingsNavigationEvent
    data object NavigateToLogin : SettingsNavigationEvent
    data object NavigateToServerStatus : SettingsNavigationEvent
    data object NavigateToPlexHomeSwitch : SettingsNavigationEvent    // NOUVEAU
    data object NavigateToAppProfiles : SettingsNavigationEvent       // NOUVEAU
}
```

**Step 3: Gérer les actions dans le ViewModel**

Dans `SettingsViewModel.onAction()` :
```kotlin
is SettingsAction.SwitchPlexUser -> {
    viewModelScope.launch {
        _navigationEvents.send(SettingsNavigationEvent.NavigateToPlexHomeSwitch)
    }
}
is SettingsAction.ManageAppProfiles -> {
    viewModelScope.launch {
        _navigationEvents.send(SettingsNavigationEvent.NavigateToAppProfiles)
    }
}
```

**Step 4: Ajouter la section "Profiles & Users" dans SettingsScreen**

Ajouter en **premier** dans la liste des sections (avant Appearance) :

```kotlin
// ══════════ Profiles & Users ══════════
item {
    Text(
        text = stringResource(R.string.settings_section_profiles),
        style = MaterialTheme.typography.titleMedium,
        color = NetflixRed,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

// Switch Plex Home User
item {
    SettingsClickableRow(
        icon = Icons.Default.SwitchAccount,
        title = stringResource(R.string.settings_switch_plex_user),
        subtitle = stringResource(R.string.settings_switch_plex_user_desc),
        onClick = { onAction(SettingsAction.SwitchPlexUser) },
    )
}

// Manage App Profiles
item {
    SettingsClickableRow(
        icon = Icons.Default.ManageAccounts,
        title = stringResource(R.string.settings_manage_profiles),
        subtitle = stringResource(R.string.settings_manage_profiles_desc),
        onClick = { onAction(SettingsAction.ManageAppProfiles) },
    )
}
```

**Step 5: Ajouter les strings**

Dans `app/src/main/res/values/strings.xml` :
```xml
<!-- Settings - Profiles & Users -->
<string name="settings_section_profiles">Profiles &amp; Users</string>
<string name="settings_switch_plex_user">Switch Plex User</string>
<string name="settings_switch_plex_user_desc">Change who\'s watching on this Plex Home</string>
<string name="settings_manage_profiles">Manage App Profiles</string>
<string name="settings_manage_profiles_desc">Add, edit, or switch between local app profiles</string>
```

**Step 6: Gérer la navigation dans SettingsRoute**

Dans `SettingsRoute`, ajouter les callbacks et les gérer dans le `LaunchedEffect` :

```kotlin
@Composable
fun SettingsRoute(
    // ... existants ...
    onNavigateToPlexHomeSwitch: () -> Unit,    // NOUVEAU
    onNavigateToAppProfiles: () -> Unit,        // NOUVEAU
) {
    // Dans le LaunchedEffect :
    is SettingsNavigationEvent.NavigateToPlexHomeSwitch -> onNavigateToPlexHomeSwitch()
    is SettingsNavigationEvent.NavigateToAppProfiles -> onNavigateToAppProfiles()
}
```

**Step 7: Câbler dans MainScreen**

Dans `MainScreen.kt`, composable Settings :
```kotlin
composable(Screen.Settings.route) {
    SettingsRoute(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToLogin = onLogout,
        onNavigateToServerStatus = { navController.navigate(Screen.ServerStatus.route) },
        onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
        onNavigateToPlexHomeSwitch = { onNavigateToPlexHomeSwitch() },  // NOUVEAU
        onNavigateToAppProfiles = { onNavigateToProfiles() },            // NOUVEAU
    )
}
```

Et ajouter `onNavigateToPlexHomeSwitch` dans la signature de `MainScreen` :
```kotlin
@Composable
fun MainScreen(
    // ... existants ...
    onNavigateToProfiles: () -> Unit,
    onNavigateToPlexHomeSwitch: () -> Unit,   // NOUVEAU
    onLogout: () -> Unit,
)
```

Et dans `MainActivity.kt` :
```kotlin
com.chakir.plexhubtv.feature.main.MainScreen(
    // ... existants ...
    onNavigateToProfiles = {
        navController.navigate(Screen.AppProfileSelection.route)
    },
    onNavigateToPlexHomeSwitch = {
        navController.navigate(Screen.PlexHomeSwitch.route)
    },
    onLogout = { ... },
)
```

**Step 8: Vérifier la compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add -A && git commit -m "feat: add PlexHome switch and AppProfile management entries in Settings"
```

---

### Task 7 : Supprimer l'ancien package `feature/auth/profiles/` vide

**Files:**
- Delete: `app/src/main/java/com/chakir/plexhubtv/feature/auth/profiles/` (package vide après Task 1)
- Delete: `app/src/main/java/com/chakir/plexhubtv/feature/profile/` (package vide après Task 2)

**Step 1: Supprimer les anciens répertoires**

```bash
rm -rf app/src/main/java/com/chakir/plexhubtv/feature/auth/profiles/
rm -rf app/src/main/java/com/chakir/plexhubtv/feature/profile/
```

**Step 2: Vérifier qu'aucune référence ne reste**

```bash
grep -r "feature.auth.profiles" app/src/ --include="*.kt"
grep -r "feature.profile" app/src/ --include="*.kt" | grep -v "feature.appprofile"
```

Expected: Aucun résultat.

**Step 3: Commit**

```bash
git add -A && git commit -m "chore: remove old profile packages after rename"
```

---

### Task 8 : Tests — Vérifier que les ViewModels fonctionnent

**Files:**
- Create: `app/src/test/java/com/chakir/plexhubtv/feature/plexhome/PlexHomeSwitcherViewModelTest.kt`
- Create: `app/src/test/java/com/chakir/plexhubtv/feature/appprofile/AppProfileViewModelTest.kt`

**Step 1: Écrire le test PlexHomeSwitcherViewModel**

```kotlin
package com.chakir.plexhubtv.feature.plexhome

import com.chakir.plexhubtv.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlexHomeSwitcherViewModelTest {
    private lateinit var viewModel: PlexHomeSwitcherViewModel
    private val authRepository: AuthRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @Test
    fun `loadUsers updates state with user list`() = runTest {
        val users = listOf(
            com.chakir.plexhubtv.core.model.PlexHomeUser(
                id = 1, uuid = "u1", title = "Alice",
                username = "alice", email = "", friendlyName = "Alice",
                thumb = "", hasPassword = false, restricted = false,
                protected = false, admin = true, guest = false,
            )
        )
        coEvery { authRepository.getHomeUsers() } returns Result.success(users)

        viewModel = PlexHomeSwitcherViewModel(authRepository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.users.size)
        assertEquals("Alice", state.users[0].title)
    }

    @Test
    fun `loadUsers handles error`() = runTest {
        coEvery { authRepository.getHomeUsers() } returns Result.failure(Exception("Network error"))

        viewModel = PlexHomeSwitcherViewModel(authRepository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }
}
```

**Step 2: Écrire le test AppProfileViewModel**

```kotlin
package com.chakir.plexhubtv.feature.appprofile

import com.chakir.plexhubtv.core.model.Profile
import com.chakir.plexhubtv.domain.repository.ProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppProfileViewModelTest {
    private lateinit var viewModel: AppProfileViewModel
    private val profileRepository: ProfileRepository = mockk()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val defaultProfile = Profile(
        id = "default",
        name = "Default",
        isActive = true,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { profileRepository.getAllProfiles() } returns flowOf(listOf(defaultProfile))
        coEvery { profileRepository.getActiveProfile() } returns defaultProfile
        coEvery { profileRepository.ensureDefaultProfile() } returns defaultProfile
    }

    @Test
    fun `init loads profiles and sets active profile`() = runTest {
        coEvery { profileRepository.getActiveProfileFlow() } returns flowOf(defaultProfile)

        viewModel = AppProfileViewModel(profileRepository)

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.profiles.size)
        assertEquals("Default", state.activeProfile?.name)
    }

    @Test
    fun `selectProfile switches active profile`() = runTest {
        val newProfile = Profile(id = "p2", name = "Kids", isKidsProfile = true)
        coEvery { profileRepository.getActiveProfileFlow() } returns flowOf(defaultProfile)
        coEvery { profileRepository.switchProfile("p2") } returns Result.success(newProfile)

        viewModel = AppProfileViewModel(profileRepository)
        viewModel.onAction(AppProfileAction.SelectProfile(newProfile))

        // Verify switchProfile was called (via coEvery setup)
    }
}
```

**Step 3: Lancer les tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*.plexhome.*" --tests "*.appprofile.*"`
Expected: PASS

**Step 4: Commit**

```bash
git add -A && git commit -m "test: add unit tests for PlexHomeSwitcher and AppProfile ViewModels"
```

---

### Task 9 : Build final et validation

**Step 1: Build complet**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Lancer tous les tests unitaires**

Run: `./gradlew testDebugUnitTest`
Expected: All tests PASS

**Step 3: Vérifier les imports orphelins**

```bash
grep -r "import com.chakir.plexhubtv.feature.auth.profiles" app/src/ --include="*.kt"
grep -r "import com.chakir.plexhubtv.feature.profile\." app/src/ --include="*.kt" | grep -v "feature.appprofile"
```

Expected: Aucun résultat.

**Step 4: Commit final si corrections**

```bash
git add -A && git commit -m "chore: final cleanup and validation of profile system refactor"
```

---

## Résumé de l'architecture finale

```
feature/
├── plexhome/                          ← Feature A : Switch d'utilisateur Plex Home
│   ├── PlexHomeSwitcherScreen.kt      (UI : "Qui regarde ?")
│   ├── PlexHomeSwitcherViewModel.kt   (Logic : load users, switch, PIN)
│   └── PlexHomeSwitcherUiState.kt     (State + Actions)
│
├── appprofile/                        ← Feature B : Profils locaux de l'app
│   ├── AppProfileSelectionScreen.kt   (UI : grille de profils)
│   ├── AppProfileSwitchScreen.kt      (UI : liste pour switcher)
│   ├── AppProfileViewModel.kt         (Logic : CRUD, switch)
│   └── AppProfileUiState.kt           (State + Actions + NavEvents)
│
├── settings/                          ← Points d'entrée Settings
│   └── SettingsScreen.kt              (Section "Profiles & Users" ajoutée)
│
└── main/
    └── MainScreen.kt                  (TopBar → AppProfileSelection)
```

**Points d'accès utilisateur :**

| Action | Depuis | Destination |
|--------|--------|-------------|
| Clic avatar TopBar | MainScreen | `AppProfileSelectionScreen` |
| Settings > "Switch Plex User" | SettingsScreen | `PlexHomeSwitcherScreen` |
| Settings > "Manage Profiles" | SettingsScreen | `AppProfileSelectionScreen` |
| Flow d'auth initial | MainActivity | `PlexHomeSwitcherScreen` |
