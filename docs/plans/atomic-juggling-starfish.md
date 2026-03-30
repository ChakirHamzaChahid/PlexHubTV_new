# Plan de Correction des Bloquants Release 1.0

## Context

Suite à l'audit pré-release de PlexHubTV, deux bloquants critiques ont été identifiés avant la soumission au Play Store :

1. **i18n incomplète** : ~21 chaînes hardcodées dans des écrans critiques (ProfileSelectionScreen, AuthScreen, Workers notifications, core/ui) qui cassent l'expérience utilisateur pour les locales non-FR ou avec lecteurs d'écran
2. **LoadingScreen sans actions en cas d'erreur** : Si la synchronisation initiale échoue, l'utilisateur voit un message d'erreur rouge mais ne peut rien faire (pas de boutons Retry/Exit), créant un état bloqué

Ces deux problèmes doivent être corrigés avant release pour garantir une UX acceptable et la conformité i18n EN/FR.

---

## Approche Recommandée

### Partie 1 : Correction i18n (P1 - Bloquants)

**Stratégie** : Externaliser les 21 chaînes critiques en réutilisant les patterns i18n existants de l'app.

#### Étape 1.1 : Ajout des clés strings.xml (app module)

**Fichiers** :
- `app/src/main/res/values/strings.xml` (EN)
- `app/src/main/res/values-fr/strings.xml` (FR)

**Clés à ajouter** (~18 clés) :

```xml
<!-- AuthScreen -->
<string name="auth_title_app">PlexHubTV</string>
<string name="auth_plex_token_label">Plex Token</string>
<string name="auth_plex_token_field_description">Plex token input field</string>
<string name="auth_login_button_description">Login button</string>
<string name="auth_login_button_text">Login with Token</string>
<string name="auth_link_account_title">Link Account</string>
<string name="auth_go_to_url">Go to: %1$s</string>
<string name="auth_pin_code_description">PIN code: %1$s</string>
<string name="auth_cancel_button">Cancel</string>
<string name="auth_retry_button">Retry</string>
<string name="auth_error_description">Authentication error: %1$s</string>
<string name="auth_success_description">Authentication successful</string>
<string name="auth_success_loading">Authentication Successful! Loading…</string>
<string name="auth_screen_description">Authentication screen</string>

<!-- Workers Notifications -->
<string name="sync_notification_title">PlexHubTV</string>
<string name="sync_library_channel_name">Library Synchronization</string>
<string name="sync_library_in_progress">Synchronizing media…</string>
<string name="sync_collection_channel_name">Collection Synchronization</string>
<string name="sync_collection_in_progress">Synchronizing collections…</string>
```

**Note** : `profile_who_is_watching` existe déjà dans strings.xml (ligne 38), pas besoin de la créer.

#### Étape 1.2 : Ajout des clés strings.xml (core/ui module)

**Fichiers** :
- `core/ui/src/main/res/values/strings.xml` (EN)
- `core/ui/src/main/res/values-fr/strings.xml` (FR)

**Clés à ajouter** (~2 clés pour P1) :

```xml
<!-- NetflixSidebar -->
<string name="sidebar_navigation_description">Navigation menu</string>
<string name="sidebar_profile_description">User profile</string>
```

#### Étape 1.3 : Correction ProfileSelectionScreen.kt

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/profile/ProfileSelectionScreen.kt`

**Changement** (ligne 103) :
```kotlin
// AVANT
Text("Who's watching?", ...)

// APRÈS
Text(stringResource(R.string.profile_who_is_watching), ...)
```

#### Étape 1.4 : Correction AuthScreen.kt

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthScreen.kt`

**Changements** (14 occurrences) :
- Ligne 63 : `contentDescription = stringResource(R.string.auth_screen_description)`
- Ligne 89 : `Text(stringResource(R.string.auth_title_app), ...)`
- Ligne 102 : `Text(stringResource(R.string.auth_plex_token_label), ...)`
- Ligne 113 : `contentDescription = stringResource(R.string.auth_plex_token_field_description)`
- Ligne 121 : `contentDescription = stringResource(R.string.auth_login_button_description)`
- Ligne 123 : `Text(stringResource(R.string.auth_login_button_text))`
- Ligne 140 : `Text(stringResource(R.string.auth_link_account_title), ...)`
- Ligne 142 : `Text(stringResource(R.string.auth_go_to_url, state.authUrl), ...)`
- Ligne 150 : `contentDescription = stringResource(R.string.auth_pin_code_description, state.pinCode)`
- Ligne 158 : `Text(stringResource(R.string.auth_cancel_button))`
- Ligne 172 : `contentDescription = stringResource(R.string.auth_error_description, message)`
- Ligne 182 : `Text(stringResource(R.string.auth_retry_button))`
- Ligne 193 : `contentDescription = stringResource(R.string.auth_success_description)`
- Ligne 197 : `Text(stringResource(R.string.auth_success_loading))`

#### Étape 1.5 : Correction LibrarySyncWorker.kt

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt`

**Changements** :
- Ligne 216 : `setContentTitle(context.getString(R.string.sync_notification_title))`
- Ligne 234 : `NotificationChannel(..., context.getString(R.string.sync_library_channel_name), ...)`
- Ligne 243 : `setContentTitle(context.getString(R.string.sync_notification_title))`
- Ligne 243 : `setContentText(context.getString(R.string.sync_library_in_progress))`

#### Étape 1.6 : Correction CollectionSyncWorker.kt

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/work/CollectionSyncWorker.kt`

**Changements** :
- Ligne 192 : `setContentTitle(context.getString(R.string.sync_notification_title))`
- Ligne 206 : `NotificationChannel(..., context.getString(R.string.sync_collection_channel_name), ...)`
- Ligne 215 : `setContentText(context.getString(R.string.sync_collection_in_progress))`

#### Étape 1.7 : Correction NetflixSidebar.kt

**Fichier** : `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/components/NetflixSidebar.kt`

**Changements** :
- Ligne 50 : `contentDescription = stringResource(R.string.sidebar_navigation_description)`
- Ligne 232 : `contentDescription = stringResource(R.string.sidebar_profile_description)`

**Note** : Nécessite import `androidx.compose.ui.res.stringResource` dans core/ui.

---

### Partie 2 : LoadingScreen - Ajout Boutons Retry/Exit

**Stratégie** : S'inspirer du pattern PlayerErrorOverlay (callbacks + boutons conditionnels).

#### Étape 2.1 : Extension LoadingNavigationEvent

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingViewModel.kt`

**Changement** : Ajouter event NavigateToAuth dans le sealed interface (ligne ~15) :

```kotlin
sealed interface LoadingNavigationEvent {
    data object NavigateToMain : LoadingNavigationEvent
    data object NavigateToAuth : LoadingNavigationEvent  // NOUVEAU
}
```

#### Étape 2.2 : Ajout méthode onExit() dans LoadingViewModel

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingViewModel.kt`

**Changement** : Ajouter méthode publique (après ligne 72) :

```kotlin
fun onExit() {
    viewModelScope.launch {
        _navigationEvents.emit(LoadingNavigationEvent.NavigateToAuth)
    }
}
```

#### Étape 2.3 : Modification LoadingScreen - Ajout boutons en état Error

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt`

**Changement 1** : Ajouter paramètres callbacks dans LoadingScreen composable (ligne ~65) :

```kotlin
@Composable
internal fun LoadingScreen(
    state: LoadingUiState,
    onRetryClicked: () -> Unit,  // NOUVEAU
    onExitClicked: () -> Unit,   // NOUVEAU
    modifier: Modifier = Modifier,
) { ... }
```

**Changement 2** : Remplacer le bloc Error (lignes 103-117) par :

```kotlin
is LoadingUiState.Error -> {
    Icon(
        imageVector = Icons.Default.Warning,
        contentDescription = stringResource(R.string.loading_error_icon_description),
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = state.message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(32.dp))

    // Boutons d'action
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(horizontal = 48.dp)
    ) {
        // Bouton Réessayer (focus principal)
        val retryFocusRequester = remember { FocusRequester() }
        Button(
            onClick = onRetryClicked,
            modifier = Modifier
                .focusRequester(retryFocusRequester)
                .weight(1f)
        ) {
            Text(stringResource(R.string.action_retry))
        }

        // Bouton Quitter
        OutlinedButton(
            onClick = onExitClicked,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.loading_exit_button))
        }

        LaunchedEffect(Unit) {
            retryFocusRequester.requestFocus()
        }
    }
}
```

**Note** : Nécessite imports `FocusRequester`, `focusRequester`, `remember`, `Button`, `OutlinedButton`, `Row`, `Arrangement`.

#### Étape 2.4 : Modification LoadingRoute - Wiring callbacks

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt` (LoadingRoute composable)

**Changement** : Ajouter callback `onNavigateToAuth` dans signature (ligne ~40) et passer callbacks à LoadingScreen :

```kotlin
@Composable
internal fun LoadingRoute(
    onNavigateToMain: () -> Unit,
    onNavigateToAuth: () -> Unit,  // NOUVEAU
    modifier: Modifier = Modifier,
    viewModel: LoadingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                LoadingNavigationEvent.NavigateToMain -> onNavigateToMain()
                LoadingNavigationEvent.NavigateToAuth -> onNavigateToAuth()  // NOUVEAU
            }
        }
    }

    LoadingScreen(
        state = state,
        onRetryClicked = { viewModel.onRetry() },      // NOUVEAU
        onExitClicked = { viewModel.onExit() },        // NOUVEAU
        modifier = modifier
    )
}
```

#### Étape 2.5 : Modification MainActivity - Ajout callback navigation

**Fichier** : `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt`

**Changement** : Ajouter `onNavigateToAuth` callback dans composable route (ligne ~129) :

```kotlin
composable(Screen.Loading.route) {
    com.chakir.plexhubtv.feature.loading.LoadingRoute(
        onNavigateToMain = {
            navController.navigate(Screen.Main.route) {
                popUpTo(Screen.Loading.route) { inclusive = true }
            }
        },
        onNavigateToAuth = {  // NOUVEAU
            navController.navigate(Screen.Login.route) {
                popUpTo(0) { inclusive = true }  // Clear all back stack
            }
        },
    )
}
```

#### Étape 2.6 : Ajout clés strings.xml pour boutons LoadingScreen

**Fichier** : `app/src/main/res/values/strings.xml` (EN)

**Clés à ajouter** :

```xml
<!-- LoadingScreen -->
<string name="loading_exit_button">Exit</string>
```

**Note** : `action_retry` existe probablement déjà (utilisé dans PlayerErrorOverlay).

**Fichier** : `app/src/main/res/values-fr/strings.xml` (FR)

```xml
<!-- LoadingScreen -->
<string name="loading_exit_button">Quitter</string>
```

---

## Fichiers Critiques à Modifier

### Partie 1 : i18n
1. `app/src/main/res/values/strings.xml` - Ajouter 18 clés
2. `app/src/main/res/values-fr/strings.xml` - Ajouter 18 traductions
3. `core/ui/src/main/res/values/strings.xml` - Ajouter 2 clés
4. `core/ui/src/main/res/values-fr/strings.xml` - Ajouter 2 traductions
5. `app/src/main/java/com/chakir/plexhubtv/feature/profile/ProfileSelectionScreen.kt` - 1 ligne
6. `app/src/main/java/com/chakir/plexhubtv/feature/auth/AuthScreen.kt` - 14 lignes
7. `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt` - 4 lignes
8. `app/src/main/java/com/chakir/plexhubtv/work/CollectionSyncWorker.kt` - 3 lignes
9. `core/ui/src/main/java/com/chakir/plexhubtv/core/ui/components/NetflixSidebar.kt` - 2 lignes

### Partie 2 : LoadingScreen UX
10. `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingViewModel.kt` - Ajouter NavigateToAuth event + onExit()
11. `app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt` - Ajouter callbacks + boutons Error state
12. `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt` - Ajouter onNavigateToAuth callback

---

## Vérification (Tests End-to-End)

### Test 1 : i18n EN/FR
1. Build debug APK
2. **Locale EN** :
   - Ouvrir app → écran Auth doit afficher "PlexHubTV", "Login with Token", "Plex Token", etc. en anglais
   - Naviguer vers ProfileSelection → "Who's watching?" en anglais
   - Déclencher sync (Workers) → notification "Synchronizing media…" en anglais
3. **Locale FR** (changer device Settings → Langue → Français) :
   - Relancer app → écran Auth doit afficher "PlexHubTV" (nom propre), boutons en français
   - ProfileSelection → "Qui regarde ?" en français
   - Notification sync → "Synchronisation des médias en cours…" en français
4. **TalkBack/Lecteur d'écran** :
   - Activer TalkBack
   - Naviguer écrans → contentDescription doivent être dans la langue correcte (pas de mélange FR/EN)

### Test 2 : LoadingScreen Error UX
1. **Simuler erreur sync** :
   - Option A : Couper réseau avant lancement app
   - Option B : Modifier LibrarySyncWorker pour forcer `Result.failure()` temporairement
2. **Vérifier UI** :
   - LoadingScreen affiche icône Warning + message d'erreur rouge
   - **2 boutons visibles** : "Réessayer" (focused par défaut) + "Quitter"
3. **Test bouton Réessayer** :
   - Cliquer "Réessayer" → LoadingScreen retourne en état Loading
   - Si réseau rétabli → sync réussit → navigation vers Main
   - Si réseau toujours down → retour état Error avec boutons
4. **Test bouton Quitter** :
   - Cliquer "Quitter" → navigation vers Screen.Login
   - Back stack cleared (`popUpTo(0)`) → impossible retour à LoadingScreen
5. **Test D-Pad TV** :
   - Focus initial sur "Réessayer"
   - D-Pad gauche/droite → switch entre boutons
   - OK/Enter → déclenche action

### Test 3 : Build Release & Vérifications
1. Build release APK
2. Vérifier menu Debug absent (déjà validé dans audit)
3. Vérifier aucune régression des P0 précédents (SEC3, SEC4, PlayerController, etc.)
4. Test smoke global : Login → Sync → Home → Play média

---

## Dépendances & Risques

**Dépendances** :
- Aucune nouvelle bibliothèque requise (tout utilise Compose + Material3 existants)
- core/ui doit déjà avoir accès à `stringResource` (vérifier imports)

**Risques** :
- **Workers Context** : `context.getString()` fonctionne dans Workers car Context disponible (pas de risque)
- **Focus FocusRequester** : Si `retryFocusRequester.requestFocus()` échoue silencieusement, pas critique (UX dégradée mais pas bloquante)
- **Navigation popUpTo(0)** : Clear all back stack → s'assurer que c'est bien le comportement désiré (pas de retour possible vers écrans précédents)

---

## Estimation Effort

- **Partie 1 (i18n)** : 2-3h (ajout clés strings.xml + remplacement 26 lignes de code)
- **Partie 2 (LoadingScreen UX)** : 1-2h (ajout event + boutons + wiring)
- **Tests E2E** : 1h (vérifications manuelles EN/FR + error states)

**Total** : 4-6h dev + 1h QA = **~1 jour de travail**
