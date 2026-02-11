# PlexHubTV — Plan d'Action Détaillé P2/P3
> **Date**: 11 février 2026
> **Base**: Audit V2 Complet
> **Contexte**: Actions P0/P1 complétées à 100%
> **Objectif**: Roadmap structurée pour les 35 actions P2/P3

---

## 📊 Vue d'Ensemble

### Distribution Actions

| Priorité | Nombre | Effort Total | Impact Business |
|----------|--------|--------------|-----------------|
| **P2** | 15 actions | 18 semaines | Architecture solide, UX pro, testabilité |
| **P3** | 20 actions | 22 semaines | Polish, features avancées, long terme |
| **TOTAL** | **35 actions** | **40 semaines** | App production-ready |

### Répartition par Catégorie

```
📐 Architecture (9 actions)    ████████░ 26%
🧪 Qualité/Tests (6 actions)   ██████░░░ 17%
🎨 UI/UX (7 actions)           ███████░░ 20%
🔒 Sécurité (3 actions)        ████░░░░░ 9%
⚡ Performance (5 actions)     █████░░░░ 14%
🚀 Features (5 actions)        █████░░░░ 14%
```

---

## 🎯 Priorité 2 — Analyse Détaillée (15 Actions)

### Catégorie: Architecture & Modularisation (5 actions)

#### **P2.1 — Splitter PlayerViewModel en 3 VMs** 🔴 Critique

**Problème Actuel**:
```kotlin
// PlayerViewModel.kt — 696 lignes, 13 dépendances
class PlayerViewModel(
    private val repository: PlaybackRepository,
    private val trackController: PlayerTrackController,
    private val scrobbler: PlayerScrobbler,
    private val statsTracker: PlayerStatsTracker,
    private val chapterManager: ChapterMarkerManager,
    private val settingsRepository: SettingsRepository,
    private val connectionManager: ConnectionManager,
    // ... +6 autres dépendances
) {
    // Gère: ExoPlayer, MPV, tracks, scrobbling, stats, chapitres, UI state
    // 8 responsabilités différentes! (violation SRP)
}
```

**Solution Proposée**:
```
PlayerViewModel (150 lignes)
├── PlayerControlViewModel (180 lignes)
│   ├── Play/Pause/Seek
│   ├── Quality selection
│   └── ExoPlayer/MPV switch
├── PlayerTrackViewModel (120 lignes)
│   ├── Audio tracks
│   ├── Subtitle tracks
│   └── Delay sync
└── PlayerStatsViewModel (80 lignes)
    ├── Performance overlay
    ├── Bitrate monitoring
    └── Buffer stats
```

**Bénéfices**:
- ✅ Chaque VM < 200 lignes → testable facilement
- ✅ Isolation des responsabilités → SRP respecté
- ✅ Réduction dépendances (4 deps max par VM)
- ✅ Tests unitaires possibles (actuellement 2 tests seulement!)

**Étapes d'Implémentation**:
1. Créer `PlayerControlViewModel` avec état minimal
2. Extraire `PlayerTrackViewModel` avec track selection
3. Extraire `PlayerStatsViewModel` avec monitoring
4. Migrer `PlayerViewModel` vers coordinateur léger
5. Ajouter tests unitaires (8 tests par VM)
6. Migrer `VideoPlayerScreen` vers 3 VMs

**Effort**: 5 jours · **Impact**: ⭐⭐⭐⭐⭐ (Maintenabilité critique)

---

#### **P2.2 — Splitter MediaDetailViewModel** 🟡 Important

**Problème Actuel**:
```kotlin
// MediaDetailViewModel.kt — 357 lignes, 10 dépendances
class MediaDetailViewModel(
    private val getMediaDetailUseCase: GetMediaDetailUseCase,
    private val enrichMediaItemUseCase: EnrichMediaItemUseCase,
    private val getSimilarMediaUseCase: GetSimilarMediaUseCase,
    private val getMediaCollectionsUseCase: GetMediaCollectionsUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val toggleWatchStatusUseCase: ToggleWatchStatusUseCase,
    // ... +4 autres use cases
) {
    // Gère: detail loading, enrichment, collections, favoris, watch status, similar media
}
```

**Solution Proposée**:
```
MediaDetailViewModel (180 lignes)
├── Loading de base
├── Favoris/Watch status
└── Navigation

MediaEnrichmentViewModel (120 lignes)
├── Enrichment multi-serveur
├── Similar media
└── Collections
```

**Bénéfices**:
- ✅ Chargement initial plus rapide (pas d'enrichment bloquant)
- ✅ Parallélisation possible (detail + enrichment async)
- ✅ Testabilité améliorée
- ✅ Code métier isolé

**Effort**: 3 jours · **Impact**: ⭐⭐⭐⭐ (Architecture)

---

#### **P2.3 — Extraire `:data` Module Séparé** 🔴 Critique

**Problème Actuel**:
```
app/
└── src/main/java/com/chakir/plexhubtv/
    ├── data/                  # ❌ Couplé avec app
    │   ├── repository/
    │   ├── mapper/
    │   └── paging/
    └── feature/
```

**Architecture Cible**:
```
PlexHubTV_new/
├── app/                       # ✅ Point d'entrée seulement
├── data/                      # ✅ Module séparé
│   ├── repository/
│   ├── mapper/
│   └── paging/
├── domain/
└── core/
```

**Bénéfices**:
- ✅ **Compilation incrémentale**: Modifier `MediaMapper` ne recompile pas l'UI
- ✅ **Isolation**: `:data` ne peut pas importer `:app`
- ✅ **Testabilité**: Tests unitaires data layer indépendants
- ✅ **Parallélisation build**: Gradle compile `:data` et `:app` en parallèle

**Métriques Performance Build**:
| Scénario | Avant | Après | Gain |
|----------|-------|-------|------|
| Modifier MediaMapper | Full rebuild (3min) | `:data` rebuild (45s) | **75% ⬇️** |
| Modifier HomeScreen | Full rebuild (3min) | `:app` rebuild (1min) | **67% ⬇️** |

**Étapes d'Implémentation**:
1. Créer module `:data` avec `build.gradle.kts`
2. Déplacer `app/data/` → `:data/src/main/java/`
3. Configurer dépendances (`:domain`, `:core:*`)
4. Mettre à jour imports dans `:app`
5. Tester compilation incrémentale
6. Documenter dans ARCHITECTURE.md

**Effort**: 2 jours · **Impact**: ⭐⭐⭐⭐⭐ (Build speed critical)

---

#### **P2.4 — Extraire `:core:ui` Module Partagé** 🟢 Recommandé

**Problème Actuel**:
```
app/src/main/java/com/chakir/plexhubtv/
├── home/components/
│   ├── NetflixMediaCard.kt       # ❌ Réutilisé partout
│   ├── NetflixContentRow.kt      # ❌ Réutilisé partout
│   └── NetflixHeroBillboard.kt   # ❌ Spécifique home
└── main/
    └── NetflixTopBar.kt           # ❌ Réutilisé partout
```

**Architecture Cible**:
```
core/ui/
├── card/
│   └── NetflixMediaCard.kt        # ✅ Composant réutilisable
├── row/
│   └── NetflixContentRow.kt       # ✅ Composant réutilisable
├── navigation/
│   └── NetflixTopBar.kt           # ✅ Composant réutilisable
└── dialogs/
    └── SourceSelectionDialog.kt   # ✅ Composant réutilisable
```

**Bénéfices**:
- ✅ Réutilisabilité maximale
- ✅ Tests UI isolés (screenshot tests par composant)
- ✅ Documentation centralisée (Composable previews)
- ✅ Compilations parallèles

**Composants à Extraire** (12 total):
1. ✅ NetflixMediaCard (utilisé dans 8 écrans)
2. ✅ NetflixContentRow (utilisé dans 6 écrans)
3. ✅ NetflixTopBar (utilisé dans 4 écrans)
4. ✅ NetflixOnScreenKeyboard (utilisé dans 2 écrans)
5. ✅ NetflixHeroBillboard (utilisé dans Home)
6. ✅ SourceSelectionDialog (utilisé dans Details)
7. ⚠️ FilterDialog (bibliothèque seulement, garder dans feature/)
8. ⚠️ PlayerSettingsDialog (player seulement, garder dans feature/)

**Effort**: 3 jours · **Impact**: ⭐⭐⭐ (Réutilisabilité)

---

#### **P2.6 — Éliminer Duplication HubsRepository** 🟡 Important

**Problème Actuel**:
```kotlin
// HubsRepositoryImpl.kt — 228 lignes, duplication 95%
override fun getHubs(serverId: String): Flow<List<Hub>> {
    return if (cacheEnabled) {
        // Chemin CACHE — 80 lignes
        hubDao.getHubs(serverId).map { entities ->
            entities.map { entity ->
                // Mapping identique au chemin réseau...
            }
        }
    } else {
        // Chemin RÉSEAU — 80 lignes (copie presque exacte!)
        flow {
            val result = api.getHubs(serverId)
            emit(result.map { dto ->
                // Mapping identique au chemin cache...
            })
        }
    }
}
```

**Solution Proposée**:
```kotlin
// Stratégie unifiée — 120 lignes (-46%)
override fun getHubs(serverId: String): Flow<List<Hub>> {
    return flow {
        // Source stratégie
        val source = if (cacheEnabled) {
            hubDao.getHubs(serverId).first()
        } else {
            api.getHubs(serverId)
        }

        // Mapping commun (une seule fois!)
        emit(source.map { item -> mapToHub(item) })
    }.flowOn(ioDispatcher)
}

private fun mapToHub(item: Any): Hub {
    // Mapping unique réutilisé par cache ET réseau
}
```

**Bénéfices**:
- ✅ -160 lignes de code dupliqué
- ✅ Maintenance simplifiée (1 seul endroit à modifier)
- ✅ Tests réduits (tester une stratégie, pas deux chemins)

**Effort**: 1 jour · **Impact**: ⭐⭐⭐ (Maintenabilité)

---

### Catégorie: Sécurité (1 action)

#### **P2.5 — Chiffrer Tokens avec EncryptedSharedPreferences** 🔴 Critique

**Problème Actuel**:
```kotlin
// SettingsDataStore.kt — Tokens en CLAIR!
private val PLEX_TOKEN = stringPreferencesKey("plex_token")      // ❌ Plaintext
private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")  // ❌ Plaintext
private val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")  // ❌ Plaintext
```

**Risques**:
- 🔴 Appareil rooté → tokens lisibles
- 🔴 Backup non chiffré → tokens exposés
- 🔴 Malware avec permissions storage → tokens volés

**Solution Proposée**:
```kotlin
// SecureSettingsDataStore.kt — Chiffrement AES-256
class SecureSettingsDataStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun setPlexToken(token: String) {
        encryptedPrefs.edit().putString("plex_token", token).apply()
        // ✅ Chiffré avec AES-256-GCM
    }

    fun getPlexToken(): String? {
        return encryptedPrefs.getString("plex_token", null)
        // ✅ Déchiffré automatiquement
    }
}
```

**Migration Strategy**:
```kotlin
// Migration depuis DataStore plaintext
suspend fun migrateToEncrypted() {
    val oldToken = settingsDataStore.plexToken.first()
    if (oldToken != null) {
        secureSettingsDataStore.setPlexToken(oldToken)
        settingsDataStore.clearPlexToken() // Supprimer plaintext
    }
}
```

**Bénéfices**:
- ✅ **Sécurité**: Tokens chiffrés au repos (AES-256-GCM)
- ✅ **Conformité**: Best practices Android Security
- ✅ **Automatique**: Chiffrement/déchiffrement transparent
- ✅ **Backup safe**: Android Auto Backup chiffre aussi

**Étapes d'Implémentation**:
1. Ajouter dépendance `androidx.security:security-crypto`
2. Créer `SecureSettingsDataStore`
3. Implémenter migration depuis DataStore plaintext
4. Migrer `AuthRepository` vers `SecureSettingsDataStore`
5. Ajouter tests de migration
6. Documenter dans SECURITY.md

**Effort**: 2 jours · **Impact**: ⭐⭐⭐⭐⭐ (Sécurité critique)

---

### Catégorie: Performance & Optimisation (3 actions)

#### **P2.7 — Consolider Optimisation Image** 🟢 Recommandé

**Problème Actuel**:
```kotlin
// ImageUtil.kt — 80 lignes
fun optimizeImageUrl(url: String, width: Int, height: Int): String {
    // Logique optimisation Plex
}

// PlexImageHelper.kt — 60 lignes (DUPLICATION!)
fun optimizePlexImage(url: String, width: Int, height: Int): String {
    // Logique optimisation Plex (copie presque identique!)
}
```

**Solution Proposée**:
```kotlin
// PlexImageHelper.kt — Unique source of truth
object PlexImageHelper {
    fun optimizeUrl(
        url: String,
        width: Int,
        height: Int,
        quality: Int = 90,
        format: ImageFormat = ImageFormat.WEBP
    ): String {
        // Logique unifiée
    }
}

// ✅ Supprimer ImageUtil.kt complètement
```

**Bénéfices**:
- ✅ -60 lignes de code dupliqué
- ✅ Tests réduits (1 classe au lieu de 2)
- ✅ Maintenance simplifiée

**Effort**: 0.5 jour · **Impact**: ⭐⭐ (Maintenabilité)

---

#### **P2.8 — Adapter Cache Mémoire au RAM Disponible** 🟡 Important

**Problème Actuel**:
```kotlin
// ImageModule.kt — Cache FIXE 200 MB!
@Provides
fun provideImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizeBytes(200 * 1024 * 1024) // ❌ 200 MB fixe
                .build()
        }
        .build()
}
```

**Problèmes**:
- 🔴 Appareil 1 GB RAM → 20% mémoire utilisée par cache images
- 🔴 Risque `OutOfMemoryError` sur appareils bas de gamme
- 🔴 Pas d'adaptation dynamique

**Solution Proposée**:
```kotlin
// ImageModule.kt — Cache ADAPTATIF
@Provides
fun provideImageLoader(context: Context): ImageLoader {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    // Utiliser 10-15% de la RAM disponible
    val totalRam = memoryInfo.totalMem
    val cacheSize = when {
        totalRam < 2_000_000_000 -> (totalRam * 0.10).toLong() // 1-2 GB: 10%
        totalRam < 4_000_000_000 -> (totalRam * 0.12).toLong() // 2-4 GB: 12%
        else -> (totalRam * 0.15).toLong()                      // 4+ GB: 15%
    }.coerceIn(50 * 1024 * 1024L, 400 * 1024 * 1024L) // Min 50 MB, Max 400 MB

    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizeBytes(cacheSize.toInt())
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(512 * 1024 * 1024) // 512 MB disk
                .build()
        }
        .build()
}
```

**Bénéfices**:
- ✅ Stabilité sur appareils bas de gamme (1-2 GB RAM)
- ✅ Performance maximale sur haut de gamme (4+ GB RAM)
- ✅ Adaptation automatique
- ✅ Logs pour debug

**Métriques**:
| Appareil | RAM Total | Cache Avant | Cache Après |
|----------|-----------|-------------|-------------|
| Xiaomi Mi Box (1 GB) | 1 GB | 200 MB (20%) ❌ | 100 MB (10%) ✅ |
| Nvidia Shield (2 GB) | 2 GB | 200 MB (10%) ⚠️ | 240 MB (12%) ✅ |
| Chromecast 4K (4 GB) | 4 GB | 200 MB (5%) ⚠️ | 400 MB (10%) ✅ |

**Effort**: 0.5 jour · **Impact**: ⭐⭐⭐⭐ (Stabilité bas de gamme)

---

#### **P2.9 — Ajouter Clés Composites à tous les `items {}`** 🟡 Important

**Problème Actuel**:
```kotlin
// FavoritesScreen.kt — PAS DE CLÉ!
items(favorites) { media ->  // ❌ Pas de key
    MediaCard(media = media)
}

// OU clé simple fragile
items(favorites, key = { it.ratingKey }) { media ->  // ⚠️ Pas unique multi-serveur!
    MediaCard(media = media)
}
```

**Problèmes**:
- 🔴 State loss sur recomposition
- 🔴 Focus jumps quand liste change
- 🔴 Animations incorrectes
- 🔴 Duplicates cross-server (même ratingKey, serveurs différents)

**Solution Proposée**:
```kotlin
// ✅ Clé composite unique
items(
    items = favorites,
    key = { media -> "${media.ratingKey}_${media.serverId}" }
) { media ->
    MediaCard(media = media)
}
```

**Fichiers à Corriger** (14 total):
1. FavoritesScreen.kt
2. HistoryScreen.kt
3. DownloadsScreen.kt
4. IptvScreen.kt
5. SearchScreen.kt
6. LibrariesScreen.kt
7. CollectionDetailScreen.kt
8. HubDetailScreen.kt
9. SeasonDetailScreen.kt
10. NetflixHomeScreen.kt (vérifier)
11. NetflixContentRow.kt (vérifier)
12. MediaDetailScreen.kt (vérifier)
13. NetflixSearchScreen.kt (vérifier)
14. NetflixDetailTabs.kt (vérifier)

**Effort**: 1 jour · **Impact**: ⭐⭐⭐ (Stabilité focus/state)

---

### Catégorie: Qualité & Tests (3 actions)

#### **P2.10 — Implémenter GitHub Actions CI** 🔴 Critique

**Objectif**: Pipeline CI/CD automatique pour non-régression

**Configuration Proposée**:
```yaml
# .github/workflows/ci.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Gradle packages
        uses: actions/cache@v3
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Lint
        run: ./gradlew detekt

      - name: Unit Tests
        run: ./gradlew testDebugUnitTest

      - name: Build APK
        run: ./gradlew assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/app-debug.apk
```

**Bénéfices**:
- ✅ **Non-régression automatique**: Tests run sur chaque PR
- ✅ **Qualité forcée**: Lint + Tests obligatoires
- ✅ **Builds automatiques**: APK disponible sur chaque commit
- ✅ **Feedback rapide**: Erreurs détectées avant merge

**Étapes d'Implémentation**:
1. Créer `.github/workflows/ci.yml`
2. Configurer secrets (keystore si signing)
3. Tester sur branche test
4. Activer branch protection (require CI pass)
5. Ajouter badge README.md

**Effort**: 1 jour · **Impact**: ⭐⭐⭐⭐⭐ (Qualité continue)

---

#### **P2.11 — Augmenter Couverture Tests ViewModel** 🟡 Important

**État Actuel**:
| ViewModel | Tests Actuels | Tests Manquants | Objectif |
|-----------|---------------|-----------------|----------|
| PlayerViewModel (696 lignes) | 2 | Track selection, MPV fallback, stats, chapitres, pause/resume | 10 |
| MediaDetailViewModel (357 lignes) | 3 | Similar media, collections, enrichment, source selection | 10 |
| HomeViewModel | 2 | Prefetch, errors, sync, pagination | 7 |
| LibraryViewModel (385 lignes) | 4 | Filter combos, letter jump, errors, WorkManager | 8 |
| SearchViewModel | (OK) | — | — |

**Stratégie de Tests**:

```kotlin
// Exemple: MediaDetailViewModelTest.kt
@Test
fun `loadDetail success should update UI state with data`() = runTest {
    // Arrange
    val mockDetail = createMockMediaDetail()
    coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.success(mockDetail))

    // Act
    viewModel.onAction(MediaDetailAction.LoadDetail("key123", "server1"))
    advanceUntilIdle()

    // Assert
    assertThat(viewModel.uiState.value.isLoading).isFalse()
    assertThat(viewModel.uiState.value.media).isEqualTo(mockDetail.item)
}

@Test
fun `loadDetail network error should show error state`() = runTest {
    // Arrange
    coEvery { getMediaDetailUseCase(any(), any()) } returns flowOf(Result.failure(IOException()))

    // Act
    viewModel.onAction(MediaDetailAction.LoadDetail("key123", "server1"))
    advanceUntilIdle()

    // Assert
    assertThat(viewModel.uiState.value.isLoading).isFalse()
    assertThat(viewModel.uiState.value.error).isNotNull()
}

@Test
fun `enrichMediaItem should load similar media and collections in parallel`() = runTest {
    // Test enrichment parallèle
}

@Test
fun `toggleFavorite should update state optimistically`() = runTest {
    // Test optimistic updates
}
```

**Tests à Ajouter** (29 total):
- PlayerViewModel: +8 tests
- MediaDetailViewModel: +7 tests
- HomeViewModel: +5 tests
- LibraryViewModel: +4 tests
- MediaDetailRepositoryImpl: +5 tests

**Effort**: 4 jours · **Impact**: ⭐⭐⭐⭐ (Confiance refactors)

---

#### **P2.12 — Supprimer Use Cases Pass-Through** 🟢 Recommandé

**Problème Actuel**:
```kotlin
// ToggleFavoriteUseCase.kt — PASS-THROUGH inutile
class ToggleFavoriteUseCase @Inject constructor(
    private val repository: FavoritesRepository
) {
    suspend operator fun invoke(mediaItem: MediaItem) {
        return repository.toggleFavorite(mediaItem)  // ❌ Delegation triviale
    }
}

// ViewModel utilise le use case
class MediaDetailViewModel @Inject constructor(
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase  // ❌ Indirection inutile
)
```

**Use Cases à Supprimer** (8 identifiés):
1. `ToggleFavoriteUseCase` → Appeler `FavoritesRepository` direct
2. `ToggleWatchStatusUseCase` → Appeler `PlaybackRepository` direct
3. `SyncWatchlistUseCase` → Appeler `FavoritesRepository` direct
4. `GetWatchHistoryUseCase` → Appeler `HistoryRepository` direct
5. Autres pass-through simples sans logique métier

**Use Cases à GARDER** (logique métier):
- ✅ `SearchAcrossServersUseCase` (parallélisation, déduplication)
- ✅ `EnrichMediaItemUseCase` (multi-serveur, fallback)
- ✅ `GetSimilarMediaUseCase` (algorithme matching)
- ✅ `ResolveEpisodeSourcesUseCase` (tree traversal complexe)

**Règle de Décision**:
```
Garder Use Case SI:
✅ Logique métier > 10 lignes
✅ Coordination multi-repository
✅ Algorithme complexe
✅ Transformation de données

Supprimer Use Case SI:
❌ Delegation triviale 1:1
❌ Pas de logique métier
❌ Juste un wrapper
```

**Bénéfices**:
- ✅ Moins d'indirection inutile
- ✅ Code plus lisible (path direct)
- ✅ Moins de classes à maintenir

**Effort**: 1 jour · **Impact**: ⭐⭐ (Simplicité code)

---

### Catégorie: UX/Features (3 actions)

#### **P2.13 — Gestion d'Erreur Centralisée** 🟡 Important

**Problème Actuel**:
```kotlin
// Chaque écran gère les erreurs différemment
// HomeScreen.kt
if (uiState.error != null) {
    Text("Error: ${uiState.error}")  // ❌ Inconsistent
}

// SearchScreen.kt
LaunchedEffect(uiState.error) {
    // ❌ Toast custom
}

// LibraryScreen.kt
// ❌ Pas de gestion d'erreur du tout!
```

**Solution Proposée**:
```kotlin
// ErrorHandler.kt — Source of truth
sealed class AppError {
    data class Network(val message: String) : AppError()
    data class Server(val code: Int, val message: String) : AppError()
    data class NoServers(val reason: String) : AppError()
    data class Auth(val reason: String) : AppError()
    data class Unknown(val throwable: Throwable) : AppError()
}

@Composable
fun ErrorSnackbarHost(
    errorState: State<AppError?>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val error = errorState.value

    AnimatedVisibility(visible = error != null) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = onRetry) {
                    Text("RETRY")
                }
            },
            dismissAction = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Dismiss")
                }
            }
        ) {
            Text(error?.toUserMessage() ?: "")
        }
    }
}

fun AppError.toUserMessage(): String = when (this) {
    is AppError.Network -> "Network error. Check connection."
    is AppError.Server -> "Server error ($code): $message"
    is AppError.NoServers -> "No servers available: $reason"
    is AppError.Auth -> "Authentication failed: $reason"
    is AppError.Unknown -> "Unexpected error occurred"
}
```

**Bénéfices**:
- ✅ UX cohérente sur tous les écrans
- ✅ Messages d'erreur user-friendly
- ✅ Retry automatique possible
- ✅ Logs centralisés

**Effort**: 2 jours · **Impact**: ⭐⭐⭐⭐ (UX robuste)

---

#### **P2.14 — Continue Watching Amélioré** 🟢 Recommandé

**Problème Actuel**:
```kotlin
// NetflixHomeScreen.kt — Pas de progression visuelle
NetflixContentRow(
    title = "Continue Watching",
    items = onDeckItems,  // ❌ Pas de barre de progression
    onItemClick = onNavigateToPlayer
)
```

**Solution Proposée**:
```kotlin
// EnhancedMediaCard.kt — Avec progression
@Composable
fun EnhancedMediaCard(
    media: MediaItem,
    progress: Float? = null,  // 0.0 - 1.0
    onClick: () -> Unit
) {
    Box {
        // Image de fond
        AsyncImage(...)

        // Barre de progression en bas
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Color.Red)
                )
            }
        }

        // Temps restant
        if (progress != null) {
            Text(
                text = "${((1f - progress) * media.duration).toInt()} min left",
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            )
        }
    }
}
```

**Tri par Dernière Vue**:
```kotlin
// OnDeckRepositoryImpl.kt
override fun getOnDeck(): Flow<List<MediaItem>> {
    return onDeckDao.getOnDeck()
        .map { entities ->
            entities
                .map { mapper.mapEntityToDomain(it) }
                .sortedByDescending { it.viewedAt }  // ✅ Tri par dernière vue
        }
}
```

**Bénéfices**:
- ✅ UX Netflix pro (barre progression visible)
- ✅ Temps restant affiché
- ✅ Tri intelligent
- ✅ Engagement ++

**Effort**: 2 jours · **Impact**: ⭐⭐⭐⭐ (UX polish)

---

#### **P2.15 — Préchargement Prochain Épisode** 🟡 Important

**Objectif**: Buffer prochain épisode pendant visionnage

**Solution Proposée**:
```kotlin
// PlayerViewModel.kt
private fun startNextEpisodePrefetch(currentMedia: MediaItem) {
    viewModelScope.launch {
        if (currentMedia.type != MediaType.Episode) return@launch

        // Chercher prochain épisode
        val nextEpisode = findNextEpisode(currentMedia)
        if (nextEpisode == null) return@launch

        // Attendre 80% de lecture
        playerState.collect { state ->
            if (state.position >= state.duration * 0.8f) {
                // Précharger prochain épisode
                prefetchEpisode(nextEpisode)
            }
        }
    }
}

private suspend fun prefetchEpisode(episode: MediaItem) {
    val streamUrl = buildStreamUrl(episode)

    // Précharger dans cache ExoPlayer
    val prefetchRequest = Request.Builder()
        .setUri(Uri.parse(streamUrl))
        .setData(episode.ratingKey.toByteArray())
        .build()

    simpleCache.startContentDownload(prefetchRequest)
}
```

**Bénéfices**:
- ✅ Transition fluide entre épisodes
- ✅ Pas de buffering au démarrage
- ✅ UX Netflix-like

**Effort**: 2 jours · **Impact**: ⭐⭐⭐ (UX fluide)

---

## 🎯 Priorité 3 — Analyse Détaillée (20 Actions)

### Catégorie: Tests Avancés (3 actions)

#### **P3.1 — Tests Screenshot Compose (Roborazzi)** 🟢 Long Terme

**Objectif**: Détection automatique des régressions visuelles

**Setup**:
```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.7.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.7.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.7.0")
}
```

**Exemple Test**:
```kotlin
// NetflixMediaCardTest.kt
@Test
fun mediaCard_focused_screenshot() {
    composeTestRule.setContent {
        NetflixMediaCard(
            media = createMockMedia(),
            focused = true,
            onClick = {}
        )
    }

    composeTestRule.onRoot()
        .captureRoboImage("screenshots/media_card_focused.png")
}

@Test
fun mediaCard_unfocused_screenshot() {
    composeTestRule.setContent {
        NetflixMediaCard(
            media = createMockMedia(),
            focused = false,
            onClick = {}
        )
    }

    composeTestRule.onRoot()
        .captureRoboImage("screenshots/media_card_unfocused.png")
}
```

**Bénéfices**:
- ✅ Détection régressions visuelles automatique
- ✅ Review visuel dans PRs (GitHub UI)
- ✅ Baseline pour nouveaux composants

**Effort**: 5 jours · **Impact**: ⭐⭐⭐ (Qualité long terme)

---

#### **P3.2 — Tests Intégration Multi-Couche** 🟢 Long Terme

**Objectif**: Tests end-to-end API → Mapper → Repository → ViewModel

**Exemple**:
```kotlin
// MediaDetailIntegrationTest.kt
@Test
fun completeFlow_loadMediaDetail_success() = runTest {
    // Arrange: Mock API response
    mockWebServer.enqueue(MockResponse().setBody(mockPlexDetailResponse))

    // Act: Trigger ViewModel action
    viewModel.onAction(MediaDetailAction.LoadDetail("key123", "server1"))
    advanceUntilIdle()

    // Assert: Verify UI state
    val uiState = viewModel.uiState.value
    assertThat(uiState.isLoading).isFalse()
    assertThat(uiState.media?.title).isEqualTo("Breaking Bad")

    // Verify DB cached
    val cached = database.mediaDao().getMedia("key123", "server1")
    assertThat(cached).isNotNull()
}
```

**Effort**: 6 jours · **Impact**: ⭐⭐⭐⭐ (Confiance système)

---

#### **P3.3 — Écran de Debug** 🟡 Utile

**Features**:
```kotlin
// DebugScreen.kt
@Composable
fun DebugScreen() {
    Column {
        // Version info
        Text("App Version: ${BuildConfig.VERSION_NAME}")
        Text("Build Type: ${BuildConfig.BUILD_TYPE}")

        // Server status
        Text("Connected Servers: $serverCount")

        // Cache stats
        Text("Image Cache: ${cacheSize.formatBytes()}")
        Text("DB Size: ${dbSize.formatBytes()}")

        // Logs export
        Button(onClick = { exportLogs() }) {
            Text("Export Logs")
        }

        // Force sync
        Button(onClick = { triggerSync() }) {
            Text("Force Sync")
        }
    }
}
```

**Effort**: 1 jour · **Impact**: ⭐⭐⭐ (Support utilisateur)

---

### Catégorie: Features Avancées (5 actions)

#### **P3.4 — Recherche Vocale** 🟢 Standard TV

**Implementation**:
```kotlin
// VoiceSearchHelper.kt
class VoiceSearchHelper(private val activity: Activity) {
    private val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)

    fun startVoiceSearch(onResult: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val query = matches?.firstOrNull()
                if (query != null) {
                    onResult(query)
                }
            }

            override fun onError(error: Int) {
                // Handle error
            }

            // Other callbacks...
        })

        speechRecognizer.startListening(intent)
    }
}
```

**Effort**: 1 jour · **Impact**: ⭐⭐⭐ (UX TV standard)

---

#### **P3.5 — Mode Picture-in-Picture** 🟡 Standard TV

**Implementation**:
```kotlin
// VideoPlayerScreen.kt
@Composable
fun VideoPlayerScreen() {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        val pipParams = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()

        activity?.enterPictureInPictureMode(pipParams)

        onDispose {
            // Cleanup
        }
    }

    // Player UI
}
```

**Effort**: 2 jours · **Impact**: ⭐⭐⭐ (Standard Android TV)

---

#### **P3.7 — Profils avec Avatar et Restrictions** 🔴 Feature Importante

**Features**:
- Avatar personnalisé par profil
- Restrictions par âge (PG, PG-13, R, etc.)
- Historique séparé par profil
- Favoris séparés par profil

**Effort**: 7 jours · **Impact**: ⭐⭐⭐⭐⭐ (Multi-user pro)

---

#### **P3.8 — Sections Home Configurables** 🟡 Personnalisation

**Features**:
- Drag & drop sections
- Pin/Unpin sections
- Cacher sections

**Effort**: 5 jours · **Impact**: ⭐⭐⭐ (Personnalisation)

---

#### **P3.9 — Télémétrie / Crashlytics** 🟡 Données Produit

**Implementation**:
```kotlin
// Firebase Crashlytics + Analytics
dependencies {
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
}

// Events
analytics.logEvent("media_played") {
    param("media_type", media.type.name)
    param("duration_minutes", (media.duration / 60).toString())
}
```

**Effort**: 1 jour · **Impact**: ⭐⭐⭐⭐ (Données produit)

---

### Catégorie: Architecture Avancée (4 actions)

#### **P3.14 — Simplifier LibraryRepositoryImpl SQL Dynamique** 🔴 Complexité

**Problème Actuel**:
```kotlin
// LibraryRepositoryImpl.kt — SQL dynamique illisible
val sql = """
    SELECT m.*,
    GROUP_CONCAT(DISTINCT genre.name) as genres,
    GROUP_CONCAT(DISTINCT director.name) as directors,
    COALESCE(NULLIF(GROUP_CONCAT(...), ''), NULL) as ...
    FROM media m
    LEFT JOIN media_genre_cross_ref ...
    WHERE ${buildDynamicFilters()}  // ❌ 6 filtres dynamiques
    ORDER BY ${buildDynamicSort()}  // ❌ 8 tris possibles
"""
```

**Solution Proposée**:
```kotlin
// Utiliser coroutines + Kotlin filtering au lieu de SQL dynamique
override fun getLibraryContent(
    libraryId: String,
    filters: LibraryFilters,
    sortBy: SortBy
): Flow<PagingData<MediaItem>> {
    return Pager(
        config = PagingConfig(pageSize = 50),
        pagingSourceFactory = {
            // Query simple sans filtres
            val allMedia = mediaDao.getMediaByLibrary(libraryId)

            // Filtrage en Kotlin (plus lisible, plus testable)
            allMedia
                .filter { applyFilters(it, filters) }
                .sortedWith(getSortComparator(sortBy))
        }
    ).flow
}

private fun applyFilters(media: MediaEntity, filters: LibraryFilters): Boolean {
    if (filters.genres.isNotEmpty() && !media.genres.any { it in filters.genres }) return false
    if (filters.year != null && media.year != filters.year) return false
    if (filters.minRating != null && (media.rating ?: 0f) < filters.minRating) return false
    // ... autres filtres
    return true
}
```

**Bénéfices**:
- ✅ Code lisible et testable
- ✅ Pas de SQL string concatenation
- ✅ Debuggable facilement

**Trade-off**: Performance (SQL est plus rapide), mais OK pour bibliothèques < 10k items

**Effort**: 4 jours · **Impact**: ⭐⭐⭐ (Maintenabilité)

---

#### **P3.15 — Convention Plugins Gradle** 🟡 Build Config DRY

**Objectif**: Centraliser configuration Gradle commune

**Structure**:
```
build-logic/
├── convention/
│   ├── src/main/kotlin/
│   │   ├── AndroidApplicationConventionPlugin.kt
│   │   ├── AndroidLibraryConventionPlugin.kt
│   │   ├── ComposeConventionPlugin.kt
│   │   └── HiltConventionPlugin.kt
│   └── build.gradle.kts
└── settings.gradle.kts
```

**Exemple**:
```kotlin
// ComposeConventionPlugin.kt
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            android {
                buildFeatures {
                    compose = true
                }

                composeOptions {
                    kotlinCompilerExtensionVersion = "1.5.3"
                }
            }

            dependencies {
                implementation(libs.androidx.compose.runtime)
                implementation(libs.androidx.compose.ui)
                implementation(libs.androidx.compose.material3)
            }
        }
    }
}
```

**Usage**:
```kotlin
// app/build.gradle.kts
plugins {
    id("plexhubtv.android.application")
    id("plexhubtv.android.compose")
    id("plexhubtv.android.hilt")
}
```

**Effort**: 3 jours · **Impact**: ⭐⭐⭐ (DRY build config)

---

#### **P3.16 — Retirer Clés API de l'APK** 🔴 Sécurité Production

**Problème Actuel**:
```kotlin
// BuildConfig — Clés API compilées dans APK!
buildConfigField("String", "TMDB_API_KEY", "\"${properties["tmdb_api_key"]}\"")
buildConfigField("String", "OMDB_API_KEY", "\"${properties["omdb_api_key"]}\"")

// ❌ Clés extractables avec APK decompile
```

**Solution Proposée**:
```
[App Client] → [Backend Proxy] → [TMDb/OMDb APIs]
                   ↑
               (Clés sécurisées)
```

**Backend Proxy** (Firebase Functions):
```javascript
// functions/src/index.ts
export const getTmdbData = functions.https.onRequest(async (req, res) => {
    const query = req.query.q;
    const tmdbKey = functions.config().tmdb.key; // ✅ Secret server-side

    const response = await fetch(`https://api.themoviedb.org/3/search?api_key=${tmdbKey}&query=${query}`);
    const data = await response.json();

    res.json(data);
});
```

**App Client**:
```kotlin
// TmdbApiService.kt — Appel via proxy
@GET("/api/tmdb/search")
suspend fun searchMovies(@Query("q") query: String): TmdbSearchResponse
// ✅ Pas de clé API exposée
```

**Effort**: 5 jours · **Impact**: ⭐⭐⭐⭐⭐ (Sécurité production)

---

### Catégorie: Polish & UX (5 actions)

#### **P3.6 — Animations de Transition** 🟡 Polish

**Shared Element Transitions**:
```kotlin
// Navigation avec shared element
SharedTransitionLayout {
    AnimatedContent(targetState = screen) { currentScreen ->
        when (currentScreen) {
            is Screen.Home -> HomeScreen(
                onItemClick = { media ->
                    sharedTransitionScope.animateSharedElement(
                        state = rememberSharedContentState(key = media.ratingKey),
                        boundsTransform = { _, _ -> tween(500) }
                    )
                }
            )

            is Screen.Detail -> DetailScreen(
                sharedTransitionScope = this@SharedTransitionLayout
            )
        }
    }
}
```

**Effort**: 3 jours · **Impact**: ⭐⭐⭐ (Polish UX)

---

#### **P3.10 — Accessibilité TV** 🟡 Conformité

**ContentDescription Systematic**:
```kotlin
// Avant
Image(painter = ..., contentDescription = null)  // ❌ Inaccessible

// Après
Image(
    painter = ...,
    contentDescription = "Poster for ${media.title}"  // ✅ Accessible
)
```

**Effort**: 3 jours · **Impact**: ⭐⭐⭐ (Conformité Google Play)

---

#### **P3.11 — i18n Complète** 🟢 Marché International

**Structure**:
```
res/
├── values/
│   └── strings.xml           # English (default)
├── values-fr/
│   └── strings.xml           # Français
├── values-es/
│   └── strings.xml           # Español
└── values-de/
    └── strings.xml           # Deutsch
```

**Effort**: 4 jours · **Impact**: ⭐⭐⭐⭐ (Marché international)

---

#### **P3.17 — Onboarding Guidé** 🟢 Réduction Friction

**Tutoriel Première Utilisation**:
```kotlin
@Composable
fun OnboardingScreen() {
    var step by remember { mutableStateOf(0) }

    when (step) {
        0 -> OnboardingStep("Welcome to PlexHubTV")
        1 -> OnboardingStep("Connect your Plex server")
        2 -> OnboardingStep("Navigate with D-Pad")
        3 -> OnboardingStep("Enjoy!")
    }
}
```

**Effort**: 1 jour · **Impact**: ⭐⭐ (First-time UX)

---

#### **P3.19 — Bandes-Annonces (YouTube/Plex)** 🟡 Engagement

**Features**:
- Afficher trailers dans détail média
- Play trailer avant décision de visionnage
- Intégration YouTube API

**Effort**: 3 jours · **Impact**: ⭐⭐⭐ (Engagement)

---

### Catégorie: Optimisations (3 actions)

#### **P3.12 — Activer `ksp.incremental`** 🟢 Build Speed

**Configuration**:
```properties
# gradle.properties
ksp.incremental=true
ksp.incremental.log=true
```

**Bénéfices**:
- ✅ Build 30-40% plus rapide
- ✅ Only process changed files

**Effort**: 0.25 jour · **Impact**: ⭐⭐⭐⭐ (Build speed)

---

#### **P3.13 — Detekt Strict Mode** 🟡 Qualité Forcée

**Configuration**:
```kotlin
// build.gradle.kts
detekt {
    ignoreFailures = false  // ✅ Bloquer build si violations
    allRules = true
}
```

**Effort**: 0.5 jour · **Impact**: ⭐⭐⭐ (Qualité forcée)

---

#### **P3.20 — Fix Thread-Safety AuthInterceptor** 🟡 Race Condition

**Problème**:
```kotlin
// AuthInterceptor.kt — Race condition
val token = cachedToken      // Lecture 1
val clientId = cachedClientId // Lecture 2
// Token peut changer entre les deux lectures!
```

**Solution**:
```kotlin
// Thread-safe avec synchronized ou AtomicReference
private val authData = AtomicReference<AuthData>()

data class AuthData(val token: String, val clientId: String)

override fun intercept(chain: Interceptor.Chain): Response {
    val data = authData.get()  // ✅ Atomic read
    val request = chain.request().newBuilder()
        .addHeader("X-Plex-Token", data.token)
        .addHeader("X-Plex-Client-Identifier", data.clientId)
        .build()
    return chain.proceed(request)
}
```

**Effort**: 0.5 jour · **Impact**: ⭐⭐ (Correctness)

---

## 📅 Plan d'Exécution par Sprints

### Sprint 1 (2 semaines) — Architecture Critique

**Objectif**: Modularisation + Build Speed

| Action | Effort | Priorité |
|--------|--------|----------|
| P2.3 — Module `:data` séparé | 2j | P2 🔴 |
| P2.1 — Splitter PlayerViewModel | 5j | P2 🔴 |
| P2.5 — Chiffrer tokens | 2j | P2 🔴 |
| P2.10 — GitHub Actions CI | 1j | P2 🔴 |

**Livrables**:
- ✅ Module `:data` indépendant
- ✅ PlayerViewModel < 200 lignes
- ✅ Tokens chiffrés AES-256
- ✅ CI/CD automatique

---

### Sprint 2 (2 semaines) — Qualité & Tests

**Objectif**: Augmenter confiance code

| Action | Effort | Priorité |
|--------|--------|----------|
| P2.11 — Tests ViewModel (+29 tests) | 4j | P2 🟡 |
| P2.8 — Cache adaptatif RAM | 0.5j | P2 🟡 |
| P2.9 — Clés composites items | 1j | P2 🟡 |
| P2.12 — Supprimer use cases pass-through | 1j | P2 🟢 |
| P3.3 — Écran de debug | 1j | P3 🟡 |

**Livrables**:
- ✅ 29 tests unitaires ajoutés
- ✅ Cache adaptatif au RAM
- ✅ Focus/state stability
- ✅ Écran debug pour support

---

### Sprint 3 (2 semaines) — UX Pro

**Objectif**: Polish UI/UX

| Action | Effort | Priorité |
|--------|--------|----------|
| P2.13 — Gestion erreur centralisée | 2j | P2 🟡 |
| P2.14 — Continue Watching amélioré | 2j | P2 🟢 |
| P2.15 — Prefetch prochain épisode | 2j | P2 🟡 |
| P2.4 — Module `:core:ui` | 3j | P2 🟢 |

**Livrables**:
- ✅ Snackbar global erreurs
- ✅ Barre progression Continue Watching
- ✅ Transition fluide épisodes
- ✅ Composants UI partagés

---

### Sprint 4 (2 semaines) — Optimisations

**Objectif**: Code cleanup

| Action | Effort | Priorité |
|--------|--------|----------|
| P2.2 — Splitter MediaDetailViewModel | 3j | P2 🟡 |
| P2.6 — Éliminer duplication Hubs | 1j | P2 🟡 |
| P2.7 — Consolider optimisation image | 0.5j | P2 🟢 |
| P3.12 — Activer ksp.incremental | 0.25j | P3 🟢 |
| P3.13 — Detekt strict | 0.5j | P3 🟡 |
| P3.20 — Fix thread-safety Auth | 0.5j | P3 🟡 |

**Livrables**:
- ✅ ViewModels < 200 lignes
- ✅ -160 lignes duplication
- ✅ Build 30% plus rapide
- ✅ Detekt bloquant

---

### Sprint 5 (3 semaines) — Features Avancées

**Objectif**: Features pro

| Action | Effort | Priorité |
|--------|--------|----------|
| P3.7 — Profils avec avatar/restrictions | 7j | P3 🔴 |
| P3.8 — Sections home configurables | 5j | P3 🟡 |
| P3.9 — Crashlytics/Analytics | 1j | P3 🟡 |

**Livrables**:
- ✅ Multi-user pro
- ✅ Home personnalisable
- ✅ Télémétrie active

---

### Sprint 6+ (Long Terme) — Polish & Scale

**Objectif**: Production-ready

| Action | Effort | Priorité |
|--------|--------|----------|
| P3.1 — Screenshot tests | 5j | P3 🟢 |
| P3.2 — Tests intégration | 6j | P3 🟢 |
| P3.4 — Recherche vocale | 1j | P3 🟢 |
| P3.5 — Mode PiP | 2j | P3 🟡 |
| P3.6 — Animations transition | 3j | P3 🟡 |
| P3.10 — Accessibilité TV | 3j | P3 🟡 |
| P3.11 — i18n complète | 4j | P3 🟢 |
| P3.14 — Simplifier SQL dynamique | 4j | P3 🔴 |
| P3.15 — Convention plugins | 3j | P3 🟡 |
| P3.16 — Backend proxy API keys | 5j | P3 🔴 |
| P3.17 — Onboarding guidé | 1j | P3 🟢 |
| P3.18 — Notifications contenu | 3j | P3 🟡 |
| P3.19 — Bandes-annonces | 3j | P3 🟡 |

---

## 🎯 Matrice Effort/Impact

### Priorité Absolue (Quick Wins P2)

| Action | Effort | Impact | ROI |
|--------|--------|--------|-----|
| P2.5 — Chiffrer tokens | 2j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥🔥🔥 |
| P2.3 — Module `:data` | 2j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥🔥🔥 |
| P2.10 — GitHub Actions CI | 1j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥🔥🔥 |
| P2.8 — Cache adaptatif | 0.5j | ⭐⭐⭐⭐ | 🔥🔥🔥🔥 |
| P2.7 — Consolider image | 0.5j | ⭐⭐ | 🔥🔥 |

### Investissement Moyen (Core P2)

| Action | Effort | Impact | ROI |
|--------|--------|--------|-----|
| P2.1 — Splitter PlayerVM | 5j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥🔥 |
| P2.11 — Tests VM (+29) | 4j | ⭐⭐⭐⭐ | 🔥🔥🔥🔥 |
| P2.4 — Module `:core:ui` | 3j | ⭐⭐⭐ | 🔥🔥🔥 |
| P2.2 — Splitter DetailVM | 3j | ⭐⭐⭐⭐ | 🔥🔥🔥 |

### Long Terme (P3 Strategic)

| Action | Effort | Impact | ROI |
|--------|--------|--------|-----|
| P3.7 — Profils avancés | 7j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥 |
| P3.2 — Tests intégration | 6j | ⭐⭐⭐⭐ | 🔥🔥🔥 |
| P3.16 — Backend proxy | 5j | ⭐⭐⭐⭐⭐ | 🔥🔥🔥 |
| P3.1 — Screenshot tests | 5j | ⭐⭐⭐ | 🔥🔥 |

---

## 📊 Résumé Exécutif

### Métriques Globales

| Métrique | P2 | P3 | Total |
|----------|----|----|-------|
| **Actions** | 15 | 20 | 35 |
| **Effort Total** | 18 sem | 22 sem | 40 sem |
| **Quick Wins** | 6 | 4 | 10 |
| **Critiques** | 4 | 3 | 7 |

### Timeline Optimiste

```
Mois 1-2:  Sprints 1-2 (Architecture + Tests)     ████████░░░░░░░░░░░░░░░░ 20%
Mois 3-4:  Sprints 3-4 (UX + Optimisations)       ████████████░░░░░░░░░░░░ 40%
Mois 5-7:  Sprint 5 (Features)                    ████████████████░░░░░░░░ 60%
Mois 8-12: Sprint 6+ (Polish & Scale)             ████████████████████████ 100%
```

### Impact Business par Phase

| Phase | Livrables | Impact Business |
|-------|-----------|-----------------|
| **Phase 1** | Architecture solide, CI/CD, sécurité | 🏗️ Foundation technique robuste |
| **Phase 2** | Tests augmentés, cache optimisé, focus stable | 🧪 Confiance pour refactors |
| **Phase 3** | UX cohérente, erreurs gérées, prefetch | 🎨 App pro, polished |
| **Phase 4** | Code clean, build rapide, detekt strict | ⚡ Vélocité dev augmentée |
| **Phase 5** | Profils, personnalisation, analytics | 🚀 Features différenciantes |
| **Phase 6** | Production-ready, i18n, accessibilité | 🌍 Marché global, conformité |

---

## ✅ Checklist de Validation

### Par Sprint

- [ ] **Sprint 1**: Module `:data` compile indépendamment
- [ ] **Sprint 1**: PlayerViewModel < 200 lignes
- [ ] **Sprint 1**: Tokens chiffrés (test rooté)
- [ ] **Sprint 1**: CI/CD green sur PR test
- [ ] **Sprint 2**: Couverture tests VM > 70%
- [ ] **Sprint 2**: Cache adapté < 15% RAM
- [ ] **Sprint 2**: Focus stable sur tous écrans
- [ ] **Sprint 3**: Erreurs affichées uniformément
- [ ] **Sprint 3**: Continue Watching avec barre
- [ ] **Sprint 3**: Transition épisodes < 1s
- [ ] **Sprint 4**: Aucune duplication > 20 lignes
- [ ] **Sprint 4**: Build < 2min (cold)
- [ ] **Sprint 5**: Multi-profil fonctionnel
- [ ] **Sprint 5**: Analytics trackant events clés

### Global

- [ ] Toutes les actions P2 complétées
- [ ] 70% des actions P3 complétées
- [ ] Aucune régression fonctionnelle
- [ ] Tests passent à 100%
- [ ] Detekt strict green
- [ ] Build < 2min (cold), < 30s (incrémental)
- [ ] App stable sur Mi Box 1GB RAM
- [ ] Aucune fuite mémoire détectée
- [ ] Tokens chiffrés validé par audit sécurité

---

## 📚 Ressources & Références

### Documentation

- [Android TV Best Practices](https://developer.android.com/training/tv)
- [Compose for TV](https://developer.android.com/jetpack/compose/tv)
- [Clean Architecture Guide](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Android Security Best Practices](https://developer.android.com/topic/security/best-practices)

### Tools

- [Detekt](https://detekt.dev/) — Static analysis
- [Roborazzi](https://github.com/takahirom/roborazzi) — Screenshot testing
- [LeakCanary](https://square.github.io/leakcanary/) — Memory leak detection
- [Android Studio Profiler](https://developer.android.com/studio/profile) — Performance

---

**Document généré le**: 11 février 2026
**Auteur**: Claude Code AI
**Base**: Audit V2 Complet PlexHubTV
**Session**: https://claude.ai/code/session_01JD5RFnbNGp3u4CUCAoQ7p3
