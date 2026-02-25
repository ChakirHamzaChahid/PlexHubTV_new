# Tests Manquants - PlexHubTV Refactor

## Vue d'ensemble

La branche refactor `claude/continue-plexhubtv-refactor-YO43N` a supprimé **11 fichiers de tests** par rapport à la branche `main`. Ce document liste ces tests manquants et fournit des recommandations pour les restaurer.

## Tests Actuellement Présents

✅ **4 fichiers de tests conservés** :
- `AppErrorTest.kt` - Tests du système d'erreurs centralisé
- `ProfileRepositoryImplTest.kt` - Tests du repository de profils
- `PrefetchNextEpisodeUseCaseTest.kt` - Tests du prefetch d'épisodes
- `MediaUrlResolverTest.kt` - Tests de résolution d'URLs média

## Tests Supprimés (11 fichiers)

### 1. Tests de Helper/Mapper (3 fichiers)

#### `ContentRatingHelperTest.kt`
- **Priorité** : P2 (Nice-to-have)
- **Fonction** : Teste la transformation des ratings (PG-13, TV-MA, etc.)
- **Recommandation** : Restaurer si le helper existe toujours, sinon créer des tests pour le nouveau système de ratings
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/core/util/`

#### `MediaMapperTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste la conversion entre entités Room et modèles de domaine
- **Recommandation** : **Critique** - Les mappers sont essentiels pour l'intégrité des données. Restaurer immédiatement.
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/data/mapper/`
- **Note** : Vérifier que les mappers n'ont pas été fusionnés dans d'autres fichiers

#### `MediaDeduplicatorTest.kt`
- **Priorité** : P2 (Nice-to-have)
- **Fonction** : Teste la déduplication des médias multi-serveurs
- **Recommandation** : Restaurer si la déduplication est toujours utilisée
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/data/util/`

### 2. Tests de Repository (2 fichiers)

#### `MediaDetailRepositoryImplTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le repository de détails média (fetch, cache, enrichment)
- **Recommandation** : **Important** - Le repository est central pour l'architecture offline-first. Restaurer avec mocks pour Room + API.
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/data/repository/`
- **Nouveaux cas à tester** :
  - Cache-first strategy (Room 5ms vs API fallback)
  - Enrichment avec `EnrichMediaItemUseCase`
  - Gestion des erreurs réseau

#### `PlaybackRepositoryImplTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le repository de playback (résolution d'URL, transcoding, scrobbling)
- **Recommandation** : **Important** - Tester la résolution d'URL et le transcoding. Restaurer avec focus sur les nouveaux use cases.
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/data/repository/`

### 3. Tests de ViewModel (5 fichiers)

#### `MediaDetailViewModelTest.kt`
- **Priorité** : P0 (Critique)
- **Statut** : **Remplacé par nouveau test** selon l'audit
- **Fonction** : Teste le ViewModel de détails média (PlayClicked, enrichment, source selection)
- **Recommandation** : **Vérifier** que le nouveau test couvre :
  - `PlayClicked` event avec enrichment
  - Sélection de source multi-serveur (`showSourceSelection`)
  - Gestion de `isPlayButtonLoading`
  - États d'erreur avec retry
- **Action** : Comparer avec l'ancien test de `main` pour vérifier la couverture

#### `HomeViewModelTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le ViewModel de l'écran Home (fetch hubs, refresh, cache)
- **Recommandation** : **Restaurer** avec focus sur la stratégie offline-first (HomeContentDao)
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/feature/home/`
- **Nouveaux cas à tester** :
  - Affichage des données cachées en mode offline
  - Refresh en arrière-plan

#### `LibraryViewModelTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le ViewModel de la bibliothèque (Paging3, filtres, tri)
- **Recommandation** : **Restaurer** avec tests de RemoteMediator pour le cache-first
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/feature/library/`

#### `PlayerViewModelTest.kt`
- **Priorité** : P0 (Critique)
- **Fonction** : Teste le ViewModel du player (lecture, pause, seek, scrobbling)
- **Recommandation** : **CRITIQUE** - Player refactorisé en 3 ViewModels (`PlayerControlViewModel`, `TrackSelectionViewModel`, `PlaybackStatsViewModel`). Créer des tests pour chacun.
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/feature/player/`
- **Nouveaux tests nécessaires** :
  - `PlayerControlViewModelTest.kt` (play/pause/seek)
  - `TrackSelectionViewModelTest.kt` (audio/sous-titres)
  - `PlaybackStatsViewModelTest.kt` (stats)

#### `SearchViewModelTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le ViewModel de recherche (query, cache, offline)
- **Recommandation** : **Restaurer** avec tests de SearchCacheEntity pour recherche offline
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/feature/search/`
- **Nouveaux cas à tester** :
  - Recherche locale en mode offline (MediaDao + SearchCacheDao)
  - Cache des résultats

### 4. Tests de Service (1 fichier)

#### `PlayerScrobblerTest.kt`
- **Priorité** : P1 (Important)
- **Fonction** : Teste le scrobbling Plex (timeline updates, marquage as watched)
- **Recommandation** : **Important** - Le scrobbling est crucial pour la synchronisation. Restaurer.
- **Localisation attendue** : `app/src/test/java/com/chakir/plexhubtv/feature/player/service/`

## Plan de Restauration

### Phase 1 : Tests Critiques (P0)
1. ✅ Vérifier `MediaDetailViewModelTest.kt` (déjà remplacé ?)
2. ❌ Créer `PlayerControlViewModelTest.kt`
3. ❌ Créer `TrackSelectionViewModelTest.kt`
4. ❌ Créer `PlaybackStatsViewModelTest.kt`

### Phase 2 : Tests Importants (P1)
1. ❌ Restaurer `MediaMapperTest.kt`
2. ❌ Restaurer `MediaDetailRepositoryImplTest.kt`
3. ❌ Restaurer `PlaybackRepositoryImplTest.kt`
4. ❌ Restaurer `HomeViewModelTest.kt`
5. ❌ Restaurer `LibraryViewModelTest.kt`
6. ❌ Restaurer `SearchViewModelTest.kt`
7. ❌ Restaurer `PlayerScrobblerTest.kt`

### Phase 3 : Tests Nice-to-have (P2)
1. ❌ Restaurer `ContentRatingHelperTest.kt` (si helper existe)
2. ❌ Restaurer `MediaDeduplicatorTest.kt` (si déduplication utilisée)

## Statistiques

- **Total tests supprimés** : 11 fichiers
- **Tests conservés** : 4 fichiers
- **Réduction de couverture** : ~73% de tests en moins
- **Tests à créer** : 3 nouveaux fichiers (Player refactor)
- **Tests à restaurer** : 8-11 fichiers (selon architecture actuelle)

## Commandes Utiles

```bash
# Lancer tous les tests
./gradlew test

# Lancer un test spécifique
./gradlew test --tests MediaMapperTest

# Rapport de couverture
./gradlew testDebugUnitTestCoverage
```

## Notes

- **Architecture refactor** : Certains tests nécessitent une réécriture complète pour s'adapter à la nouvelle architecture (Player, Cache-first, etc.)
- **Priorité** : Focus sur les tests critiques (P0) et importants (P1) avant les nice-to-have (P2)
- **Couverture actuelle** : À vérifier avec `./gradlew testDebugUnitTestCoverage`
