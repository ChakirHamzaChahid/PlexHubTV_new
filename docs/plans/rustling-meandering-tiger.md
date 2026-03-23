# Plan d'implémentation : Global CoroutineExceptionHandler (EH1)

## Context

PlexHubTV n'a actuellement **aucun handler global** pour les exceptions coroutines. L'audit a révélé que les exceptions non catchées peuvent :
- Crasher silencieusement sans logging
- Disparaître sans trace dans les environnements de production
- Ne pas être remontées à Crashlytics pour l'analyse des erreurs

Cette implémentation vise à :
1. **Fiabiliser l'application** en capturant toutes les exceptions coroutines non gérées
2. **Améliorer l'observabilité** via Timber (DEBUG) et Crashlytics (RELEASE)
3. **Maintenir la visibilité des erreurs** en développement (crash visible en DEBUG)

### Vulnérabilités identifiées

| Composant | Scope actuel | Risque |
|-----------|--------------|--------|
| **ApplicationScope** | `SupervisorJob() + Default` | Pas de handler - exceptions swallowed |
| **PlayerController** | Custom `SupervisorJob() + Main` | Pas de handler - player crashes silencieux |
| **ConnectionManager** | Utilise `@ApplicationScope` | Héritera du handler global |
| **AuthInterceptor** | Utilise `@ApplicationScope` | Héritera du handler global |

---

## Architecture cible

```
GlobalCoroutineExceptionHandler (@Singleton)
    ↓
    ├─→ ApplicationScope (via CoroutineModule)
    │       ├─→ ConnectionManager
    │       └─→ AuthInterceptor
    │
    └─→ PlayerController (injection directe)
```

---

## Plan d'implémentation détaillé

### Étape 1 : Créer GlobalCoroutineExceptionHandler
**Fichier** : `core/common/src/main/java/com/chakir/plexhubtv/core/common/handler/GlobalCoroutineExceptionHandler.kt` (nouveau)

**Implémentation** :
```kotlin
package com.chakir.plexhubtv.core.common.handler

import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineExceptionHandler
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class GlobalCoroutineExceptionHandler @Inject constructor(
    private val crashlytics: FirebaseCrashlytics
) : CoroutineExceptionHandler {

    override val key: CoroutineContext.Key<*>
        get() = CoroutineExceptionHandler

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        // En DEBUG : log et laisser crasher pour visibilité
        Timber.e(exception, "[COROUTINE-CRASH] Uncaught exception in context: $context")

        // En RELEASE : enregistrer dans Crashlytics
        crashlytics.recordException(exception)
    }
}
```

**Rationale** :
- `@Singleton` : une seule instance pour toute l'application
- Inject `FirebaseCrashlytics` : déjà initialisé dans PlexHubApplication
- **Pas de condition `if (BuildConfig.DEBUG)`** dans le handler : Crashlytics est déjà configuré pour ne collecter qu'en RELEASE (`setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)` ligne 287)
- Timber log dans tous les cas (DEBUG + RELEASE) pour la traçabilité

---

### Étape 2 : Fournir FirebaseCrashlytics via Hilt
**Fichier** : `core/common/src/main/java/com/chakir/plexhubtv/core/di/FirebaseModule.kt` (nouveau)

**Implémentation** :
```kotlin
package com.chakir.plexhubtv.core.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseCrashlytics(): FirebaseCrashlytics {
        return FirebaseCrashlytics.getInstance()
    }
}
```

**Rationale** :
- Permet l'injection de `FirebaseCrashlytics` dans `GlobalCoroutineExceptionHandler`
- Singleton : réutilise l'instance déjà initialisée dans PlexHubApplication
- Placé dans `core:common` pour être accessible partout

---

### Étape 3 : Injecter le handler dans ApplicationScope
**Fichier** : `core/common/src/main/java/com/chakir/plexhubtv/core/di/CoroutineModule.kt` (modification)

**Changement** :
```kotlin
@Provides
@Singleton
@ApplicationScope
fun provideApplicationScope(
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
    globalHandler: GlobalCoroutineExceptionHandler  // NOUVEAU
): CoroutineScope = CoroutineScope(SupervisorJob() + defaultDispatcher + globalHandler)
```

**Impact** :
- ✅ **ConnectionManager** utilisera automatiquement le handler (utilise `@ApplicationScope`)
- ✅ **AuthInterceptor** utilisera automatiquement le handler (utilise `@ApplicationScope`)
- Tous les futurs composants qui injectent `@ApplicationScope` bénéficieront du handler

---

### Étape 4 : Injecter le handler dans PlayerController
**Fichier** : `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (modification)

**Changements** :

1. **Ajouter l'injection dans le constructeur** (ligne ~45) :
```kotlin
class PlayerController @Inject constructor(
    private val application: Application,
    private val playerFactory: PlayerFactory,
    private val playerScrobbler: PlayerScrobbler,
    private val playerStatsTracker: PlayerStatsTracker,
    private val playerTrackController: PlayerTrackController,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,  // NOUVEAU
    private val globalHandler: GlobalCoroutineExceptionHandler  // NOUVEAU
) {
```

2. **Remplacer la création du scope** (ligne ~46) :
```kotlin
// AVANT
private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// APRÈS
private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + globalHandler)
```

3. **Mettre à jour la méthode release()** (ligne ~134) :
```kotlin
fun release() {
    scope.cancel()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + globalHandler)  // AJOUTER globalHandler
    // ...
}
```

**Rationale** :
- PlayerController est critique (gère la lecture vidéo)
- Scope recréé dynamiquement → doit inclure le handler à chaque recréation
- Utilise `Dispatchers.Main` (pas `defaultDispatcher`) pour garder le comportement actuel

---

### Étape 5 : Vérifier que les Workers ne nécessitent pas de modification
**Fichiers** :
- `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt`
- `app/src/main/java/com/chakir/plexhubtv/work/RatingSyncWorker.kt`
- `app/src/main/java/com/chakir/plexhubtv/work/CollectionSyncWorker.kt`

**Décision** : **Aucune modification nécessaire**

**Rationale** :
- Tous les Workers étendent `CoroutineWorker` (framework WorkManager)
- Exception handling déjà présent : try-catch dans `doWork()` retournant `Result.failure()`
- Les exceptions non catchées dans `CoroutineWorker` sont gérées par WorkManager (logged + work failed)
- Ajouter un handler custom pourrait interférer avec le mécanisme de retry de WorkManager

---

### Étape 6 : Tests - Vérifier la non-régression
**Actions** :
1. Compiler le projet : `./gradlew assembleDebug`
2. Exécuter les tests unitaires : `./gradlew testDebugUnitTest`
3. Vérifier qu'aucun test n'est cassé par le nouveau handler

**Gestion des tests qui créent des scopes** :
- Les tests utilisent généralement `TestCoroutineDispatcher` ou `UnconfinedTestDispatcher`
- Si un test échoue à cause du handler, créer une version test :
  ```kotlin
  class NoOpCoroutineExceptionHandler : CoroutineExceptionHandler {
      override val key = CoroutineExceptionHandler
      override fun handleException(context: CoroutineContext, exception: Throwable) {
          // No-op for tests
      }
  }
  ```

---

## Fichiers à créer/modifier

### Nouveaux fichiers
1. ✨ `core/common/src/main/java/com/chakir/plexhubtv/core/common/handler/GlobalCoroutineExceptionHandler.kt`
2. ✨ `core/common/src/main/java/com/chakir/plexhubtv/core/di/FirebaseModule.kt`

### Fichiers à modifier
1. 📝 `core/common/src/main/java/com/chakir/plexhubtv/core/di/CoroutineModule.kt`
   - Ligne 38 : ajouter `globalHandler` au scope
2. 📝 `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt`
   - Ligne ~45 : ajouter paramètres constructor
   - Ligne ~46 : ajouter handler au scope
   - Ligne ~134 : ajouter handler au scope recreated

### Fichiers à vérifier (pas de modification)
- ❌ Workers (LibrarySyncWorker, RatingSyncWorker, CollectionSyncWorker) : déjà gérés par WorkManager
- ❌ ViewModels : utilisent `viewModelScope` (framework handling)

---

## Vérification finale

### Checklist de validation
- [ ] `GlobalCoroutineExceptionHandler` créé dans `core:common`
- [ ] `FirebaseModule` créé pour injecter Crashlytics
- [ ] `ApplicationScope` inclut le handler dans son contexte
- [ ] `PlayerController` injecte et utilise le handler
- [ ] Compilation réussie : `./gradlew assembleDebug`
- [ ] Tests unitaires passent : `./gradlew testDebugUnitTest`
- [ ] Vérification manuelle : provoquer une exception dans une coroutine → log visible

### Test manuel de validation
```kotlin
// Dans PlexHubApplication.kt, après initializeAppInParallel()
appScope.launch {
    throw RuntimeException("Test global handler")
}
```
**Résultat attendu** :
- En DEBUG : log Timber visible + exception non masquée
- En RELEASE : exception enregistrée dans Crashlytics

---

## Commit final
```bash
git add core/common/src/main/java/com/chakir/plexhubtv/core/common/handler/
git add core/common/src/main/java/com/chakir/plexhubtv/core/di/
git add app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt
git commit -m "feat(P1): add global CoroutineExceptionHandler with Crashlytics logging (EH1)

- Create GlobalCoroutineExceptionHandler singleton with Firebase Crashlytics
- Inject handler into ApplicationScope (covers ConnectionManager, AuthInterceptor)
- Inject handler into PlayerController custom scope
- Add FirebaseModule to provide Crashlytics instance via Hilt
- Preserve crash visibility in DEBUG, log to Crashlytics in RELEASE"
```
