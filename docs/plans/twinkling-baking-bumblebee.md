# Plan: 4 correctifs PlexHubTV (v2 — révisé après review)

## Contexte
Quatre bugs à corriger. Ce plan a été révisé après une review critique qui a identifié des causes racines incorrectes et des race conditions dans la v1.

---

## Tâche 1 : Corriger le masquage de media

### Problème
Le dialog s'affiche, l'utilisateur confirme, mais le media reste visible dans la grille.

### Analyse
- Les `ratingKey`/`serverId` passés au DAO sont cohérents (nav args = valeurs Room = API response — Plex utilise des IDs stables).
- Le vrai problème : `deleteMedia()` retourne toujours `Result.success(Unit)` même si l'UPDATE affecte 0 lignes. Aucune visibilité sur l'échec silencieux.
- Room `@RawQuery(observedEntities = [MediaEntity::class])` déclenche bien l'InvalidationTracker — si l'UPDATE touche >= 1 ligne, le PagingSource se rafraîchit.
- **Conclusion** : l'UPDATE n'affecte probablement aucune ligne. Cause possible : `unificationId` vide/null dans l'entité, `getMedia()` retourne null, ou condition WHERE trop restrictive.

### Fichiers à modifier
- [MediaDao.kt](core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaDao.kt) — retour `Int` pour les méthodes hide
- [MediaDetailRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt) — diagnostic + gestion du count=0
- [strings.xml:206](app/src/main/res/values/strings.xml#L206) — corriger le message
- [strings.xml FR:206](app/src/main/res/values-fr/strings.xml#L206) — corriger le message

### Implémentation

**1.1** Modifier les DAO pour retourner `Int` :
```kotlin
suspend fun hideMediaByUnificationId(...): Int
suspend fun hideMedia(...): Int
```

**1.2** Ajouter diagnostic + fallback dans `deleteMedia()` :
```kotlin
override suspend fun deleteMedia(ratingKey: String, serverId: String): Result<Unit> {
    val entity = mediaDao.getMedia(ratingKey, serverId)
    Timber.d("hideMedia: entity found=${entity != null}, uid=${entity?.unificationId}")

    val count = if (!entity?.unificationId.isNullOrBlank()) {
        mediaDao.hideMediaByUnificationId(entity!!.unificationId)
    } else {
        // Fallback direct par ratingKey+serverId
        mediaDao.hideMedia(ratingKey, serverId)
    }

    if (count == 0) {
        Timber.w("hideMedia: 0 rows affected! rk=$ratingKey sid=$serverId uid=${entity?.unificationId}")
        // Tentative de fallback si hideByUnificationId a échoué
        if (!entity?.unificationId.isNullOrBlank()) {
            val fallbackCount = mediaDao.hideMedia(ratingKey, serverId)
            Timber.d("hideMedia: fallback by rk+sid → $fallbackCount rows")
        }
    }
    return Result.success(Unit)
}
```

**1.3** Corriger les messages de confirmation :
- EN: `"Hide \"%1$s\" from your library? This media will no longer appear, even after resync."`
- FR: `"Masquer « %1$s » de votre bibliothèque ? Ce média n\u2019apparaîtra plus, même après une resynchronisation."`

### Risques
- Faible. Le retour `Int` est un changement non-breaking. Le fallback ajoute de la résilience sans changer la logique principale.

---

## Tâche 2 : Réorganiser l'écran de chargement sync

### Problème
L'utilisateur veut voir à gauche la progression des bibliothèques et à droite le suivi serveur.

### Analyse (review critique)
- Pour 1 serveur, forcer un sidebar est redondant avec `CurrentSyncBlock` et risque un overflow sur 720p (Mi Box S : 1280px, 0.55 + 0.35 + margins > 1280).
- `SyncServerState.libraries` EST pré-rempli au démarrage du worker (depuis Room cache) — les données existent.
- Meilleure approche : afficher la liste de bibliothèques **sous** le bloc principal pour 1 serveur, garder le sidebar pour 2+ serveurs.

### Fichier à modifier
- [LoadingScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/loading/LoadingScreen.kt)

### Implémentation

**2.1** Dans `SyncProgressContent`, garder la condition `servers.size >= 2` pour le sidebar.

**2.2** Pour 1 serveur, ajouter une liste de bibliothèques SOUS le `CurrentSyncBlock` :
```kotlin
@Composable
private fun SyncProgressContent(syncState: SyncGlobalState, globalProgress: Float) {
    if (syncState.servers.size >= 2) {
        // Layout existant : Row avec CurrentSyncBlock + ServerRecapSidebar
        Row(...) { ... }
    } else {
        // 1 serveur : layout vertical, bloc principal + liste de libs en dessous
        Column(
            modifier = Modifier.fillMaxWidth(0.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CurrentSyncBlock(syncState, globalProgress)

            val libs = syncState.currentServer?.libraries.orEmpty()
            if (libs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LibraryProgressList(libraries = libs)
            }
        }
    }
}
```

**2.3** Créer `LibraryProgressList` — composable simple affichant chaque bibliothèque avec :
- Icône de statut (CheckCircle / Spinner / Clock / Error — réutiliser les icônes de `ServerRecapItem`)
- Nom de la bibliothèque
- Badge `itemsSynced / itemsTotal` (quand status != Pending)

### Risques
- Faible. Pas de changement de layout pour multi-serveurs. Le nouveau composable est isolé.

---

## Tâche 3 : Restauration du focus dans la bibliothèque

### Problème
Back depuis le detail screen → focus revient en haut au lieu de l'item précédemment sélectionné.

### Analyse (review critique — cause racine corrigée)
La v1 du plan disait que `hasRestoredFocus` n'était jamais réinitialisé. **C'est faux** — le composable `LibraryContent` est recréé lors du back navigation (NavHost détruit/recrée), donc `remember { false }` est bien réinitialisé.

**Le VRAI bug est `LaunchedEffect(Unit)`** (clé statique) :
- Quand Paging3 réutilise des items du cache, les items ne sont PAS recréés en composition
- `LaunchedEffect(Unit)` ne se re-déclenche pas pour les items déjà composés
- Résultat : même si `shouldRestoreFocus` est vrai et que `hasRestoredFocus` est faux, le `LaunchedEffect` ne s'exécute pas

### Fichiers à modifier
- [LibrariesScreen.kt:549-558](app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt#L549)
- [LibraryViewModel.kt:504-508](app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt#L504)

### Implémentation

**3.1** Décommenter la mise à jour `_uiState` dans `OnItemFocused` :
```kotlin
is LibraryAction.OnItemFocused -> {
    savedStateHandle["lastFocusedId"] = action.item.ratingKey
    _uiState.update { it.copy(scroll = it.scroll.copy(lastFocusedId = action.item.ratingKey)) }
}
```

**3.2** Remplacer `LaunchedEffect(Unit)` par `LaunchedEffect(shouldRestoreFocus)` :
```kotlin
if (shouldRestoreFocus) {
    LaunchedEffect(shouldRestoreFocus) {  // ← clé dynamique
        delay(100)  // Attendre la stabilisation du layout
        try {
            focusRestorationRequester.requestFocus()
            hasRestoredFocus = true
        } catch (_: Exception) { }
    }
}
```

Avec `shouldRestoreFocus` comme clé, le `LaunchedEffect` se re-déclenche quand la condition passe de `false` à `true`, même si l'item est déjà composé par le cache Paging.

### Risques
- Moyen. Le `delay(100)` est un workaround pour le timing composition/layout. Si l'item n'est pas dans le viewport visible, `requestFocus()` échouera silencieusement. Mitigation : le `gridState` devrait déjà être scrollé à la bonne position via `rememberLazyGridState()`.

---

## Tâche 4 : Focus D-pad dans le player après fermeture de popup

### Problème
Fermeture d'un popup (More, Settings, etc.) → le focus disparaît au lieu de revenir sur les contrôles.

### Analyse (review critique — race condition identifiée)
La v1 proposait `focusRequester.requestFocus()` immédiatement après `controlsVisible = true`. **Cela échouera** car :
- `AnimatedVisibility(fadeIn)` prend ~300ms pour composer le bouton play/pause
- `requestFocus()` est appelé AVANT que le bouton ne soit dans l'arbre de composition
- Le `try/catch` avale l'erreur silencieusement

Note : quand un dialog (MoreMenu, Settings, etc.) est visible, les contrôles du player sont **toujours visibles en arrière-plan** (pas cachés). Donc le `focusRequester` est probablement attaché. Le problème est plutôt que le focus reste "coincé" sur l'emplacement du dialog fermé.

### Fichier à modifier
- [VideoPlayerScreen.kt:217](app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt#L217)

### Implémentation

**4.1** Ajouter la détection de fermeture de dialog avec délai pour l'animation :
```kotlin
var wasDialogVisible by remember { mutableStateOf(false) }

LaunchedEffect(isDialogVisible) {
    if (wasDialogVisible && !isDialogVisible) {
        // Le dialog vient de se fermer — maintenir les contrôles visibles
        controlsVisible = true
        lastInteractionTime = System.currentTimeMillis()
        // Attendre que l'animation de disparition du dialog se termine
        // et que les contrôles soient pleinement composés
        delay(350)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) { }
    }
    wasDialogVisible = isDialogVisible
}
```

**4.2** Placer ce code APRÈS la définition de `isDialogVisible` (ligne 218) et AVANT le `BackHandler` (ligne 220).

### Risques
- Faible. Le `delay(350)` couvre la durée de `fadeIn()` (~300ms) + marge. Si les contrôles étaient déjà visibles (cas normal avec les dialogs), le `delay` est excessif mais inoffensif — le focus arrive juste 350ms après la fermeture.

---

## Ordre d'implémentation

1. **T1** — Masquage media (diagnostic + strings) — le plus critique fonctionnellement
2. **T4** — Focus player (1 fichier, isolé, rapide)
3. **T3** — Focus bibliothèque (2 fichiers, nécessite test D-pad)
4. **T2** — Loading screen (1 fichier, UI pure)

## Vérification

| Tâche | Test |
|-------|------|
| T1 | Masquer un media → Back → vérifier disparu de la grille. Relancer sync → vérifier qu'il reste masqué. Checker les logs Timber pour le count. |
| T2 | Sync avec 1 serveur → vérifier la liste de bibliothèques sous le bloc principal. Sync multi-serveurs → vérifier sidebar inchangé. |
| T3 | Scroller loin dans la grille → cliquer un media → Back → vérifier focus sur l'item cliqué à la bonne position. |
| T4 | Player → More menu → fermer → vérifier focus sur play/pause. Tester aussi Settings, Audio, Subtitles. |
