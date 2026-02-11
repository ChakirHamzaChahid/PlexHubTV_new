# Architecture PlexHubTV — Structure Officielle
> **Date**: 11 février 2026
> **Version**: 1.0
> **Architecture**: Clean Architecture Multi-Modules

---

## Vue d'Ensemble

PlexHubTV suit une **Clean Architecture stricte** avec **4 modules Gradle principaux** à la racine, permettant la compilation incrémentale, l'isolation des responsabilités et une testabilité optimale.

```
PlexHubTV_new/
├── app/                          # ✅ Module UI (Presentation Layer)
├── domain/                       # ✅ Module Business Logic
├── data/                         # ✅ Module Data Layer
└── core/                         # ✅ Modules Partagés (9 sous-modules)
```

---

## 📦 Module 1: `:app` — Presentation Layer

**Responsabilité**: Interface utilisateur Android TV, navigation, injection de dépendances.

### Structure

```
app/
├── src/main/java/com/chakir/plexhubtv/
│   ├── MainActivity.kt           # Point d'entrée Android
│   ├── PlexHubApplication.kt     # Application Hilt
│   ├── di/                       # ⭐ Modules DI Hilt (anciennement "core/")
│   │   ├── datastore/            # Module DI pour DataStore
│   │   ├── designsystem/         # Module DI pour DesignSystem + Thème Compose
│   │   ├── image/                # Module DI pour Coil + PlexImageHelper
│   │   ├── navigation/           # Module DI pour Navigation + Screen sealed class
│   │   └── network/              # Module DI pour ConnectionManager
│   ├── feature/                  # 18 écrans Compose + ViewModels
│   │   ├── auth/                 # Authentification Plex
│   │   ├── home/                 # Accueil Netflix-like
│   │   ├── details/              # Détails média
│   │   ├── player/               # Lecteur vidéo ExoPlayer/MPV
│   │   ├── library/              # Bibliothèque avec filtres
│   │   ├── search/               # Recherche fédérée
│   │   ├── favorites/            # Favoris/Watchlist
│   │   ├── history/              # Historique
│   │   ├── downloads/            # Téléchargements
│   │   ├── iptv/                 # Chaînes IPTV
│   │   ├── settings/             # Paramètres
│   │   ├── profile/              # Profils Plex Home
│   │   ├── collection/           # Collections
│   │   ├── hub/                  # Hubs (sections dynamiques)
│   │   ├── loading/              # Écran de chargement
│   │   └── main/                 # Navigation principale
│   └── work/                     # WorkManager (sync background)
└── build.gradle.kts
```

### Dépendances

```kotlin
dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))

    // Android
    implementation("androidx.compose.tv:tv-foundation")
    implementation("androidx.hilt:hilt-navigation-compose")
    implementation("androidx.media3:media3-exoplayer")

    // Hilt DI
    implementation("com.google.dagger:hilt-android")
    kapt("com.google.dagger:hilt-compiler")
}
```

### Règles

✅ **Peut dépendre de**: `:domain`, `:data`, tous les `:core:*`
❌ **Ne doit PAS contenir**: Logique métier, accès direct à la base de données

---

## 📦 Module 2: `:domain` — Business Logic Layer

**Responsabilité**: Logique métier pure, use cases, interfaces de repositories, services.

### Structure

```
domain/
└── src/main/java/com/chakir/plexhubtv/domain/
    ├── repository/               # 16 interfaces (contrats)
    │   ├── AuthRepository
    │   ├── MediaRepository
    │   ├── MediaDetailRepository
    │   ├── LibraryRepository
    │   ├── SearchRepository
    │   ├── PlaybackRepository
    │   ├── OnDeckRepository
    │   ├── HubsRepository
    │   ├── FavoritesRepository
    │   ├── WatchlistRepository
    │   ├── SyncRepository
    │   ├── DownloadsRepository
    │   ├── CollectionRepository
    │   ├── HistoryRepository
    │   ├── OfflineWatchSyncRepository
    │   └── IptvRepository
    ├── usecase/                  # 25 use cases (logique métier)
    │   ├── GetUnifiedHomeContentUseCase
    │   ├── GetMediaDetailUseCase
    │   ├── SearchAcrossServersUseCase
    │   ├── EnrichMediaItemUseCase
    │   ├── GetSimilarMediaUseCase
    │   ├── GetMediaCollectionsUseCase
    │   ├── PlaybackInitializationUseCase
    │   ├── ResolveEpisodeSourcesUseCase  # ⭐ Consolidé depuis app/
    │   ├── ToggleFavoriteUseCase
    │   ├── SyncWatchlistUseCase
    │   ├── ToggleWatchStatusUseCase
    │   ├── GetWatchHistoryUseCase
    │   ├── GetFavoritesUseCase
    │   └── ... (15+ autres)
    └── service/                  # Services métier
        └── PlaybackManager       # Gestion du playback multi-plateforme
```

### Dépendances

```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    // Coroutines uniquement (pas de dépendances Android)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
}
```

### Règles

✅ **Peut dépendre de**: `:core:model`, `:core:common` uniquement
❌ **Ne doit PAS dépendre de**: `:app`, `:data`, Android Framework
✅ **Contient**: Logique métier pure, interfaces de repositories
✅ **Principe**: Inversion de dépendance (Dependency Inversion Principle)

---

## 📦 Module 3: `:data` — Data Layer

**Responsabilité**: Implémentation des repositories, accès aux données (réseau, database, cache).

### Structure

```
data/
└── src/main/java/com/chakir/plexhubtv/data/
    ├── repository/               # 17 implémentations
    │   ├── AuthRepositoryImpl
    │   ├── MediaRepositoryImpl
    │   ├── MediaDetailRepositoryImpl
    │   ├── LibraryRepositoryImpl
    │   ├── SearchRepositoryImpl
    │   ├── PlaybackRepositoryImpl
    │   ├── OnDeckRepositoryImpl
    │   ├── HubsRepositoryImpl
    │   ├── FavoritesRepositoryImpl
    │   ├── WatchlistRepositoryImpl
    │   ├── SyncRepositoryImpl
    │   ├── DownloadsRepositoryImpl
    │   ├── CollectionRepositoryImpl
    │   ├── HistoryRepositoryImpl
    │   ├── OfflineWatchSyncRepositoryImpl
    │   ├── IptvRepositoryImpl
    │   └── aggregation/
    │       └── MediaDeduplicator  # Fusion multi-serveur
    ├── mapper/                   # Conversion DTO → Domain
    │   ├── MediaMapper           # DTO Plex → MediaItem
    │   ├── UserMapper
    │   └── ServerMapper
    ├── paging/                   # Pagination Paging 3
    │   └── MediaRemoteMediator   # Pagination avec cache
    └── di/                       # Modules DI pour Data Layer
        └── RepositoryModule      # @Binds repositories
```

### Dépendances

```kotlin
dependencies {
    implementation(project(":domain"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))

    // Coroutines + Room + Retrofit
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("androidx.room:room-ktx")
    implementation("com.squareup.retrofit2:retrofit")
    implementation("androidx.paging:paging-common-ktx")
}
```

### Règles

✅ **Peut dépendre de**: `:domain`, tous les `:core:*`
❌ **Ne doit PAS dépendre de**: `:app`
✅ **Implémente**: Toutes les interfaces de `:domain/repository/`
✅ **Principe**: Séparation Data/Business Logic

---

## 📦 Module 4: `:core` — Shared Modules

**Responsabilité**: Code partagé entre toutes les couches (models, network, database, UI components).

### Sous-Modules (9)

```
core/
├── model/                        # Entités métier & DTOs
│   ├── MediaItem                 # 46 champs, agnostique serveur
│   ├── Hub, Server, User
│   ├── MediaType, MediaSource
│   ├── Stream, Chapter, Marker
│   ├── PlaybackState, Quality
│   └── ... (19 data classes)
├── common/                       # Utilitaires communs
│   ├── util/                     # StringNormalizer, ContentRating, etc.
│   ├── exception/                # Exceptions métier
│   └── di/                       # Modules DI communs
├── network/                      # Retrofit + OkHttp
│   ├── PlexApiService            # API Plex
│   ├── TmdbApiService            # API TMDb
│   ├── OmdbApiService            # API OMDb
│   ├── AuthInterceptor           # Injection token
│   ├── ConnectionManager         # Race multi-URL
│   └── model/                    # DTOs réseau (MetadataDTO, etc.)
├── database/                     # Room DB
│   ├── PlexDatabase              # Base de données locale
│   ├── dao/                      # 11 DAOs
│   ├── entity/                   # 12 entités Room
│   └── migrations/               # 6 migrations (v11→22)
├── datastore/                    # DataStore Preferences
│   └── SettingsDataStore         # Préférences utilisateur
├── navigation/                   # Navigation Compose
│   └── Route definitions
├── designsystem/                 # Design System
│   ├── Theme                     # Thème Material3
│   ├── Color                     # Palette Netflix
│   └── Typography                # Typographie
├── ui/                           # Composants UI réutilisables
│   ├── NetflixMediaCard          # Carte média avec focus
│   ├── NetflixContentRow         # Rangée horizontale TV
│   ├── NetflixHeroBillboard      # Hero carousel
│   ├── NetflixTopBar             # Barre de navigation
│   ├── NetflixOnScreenKeyboard   # Clavier TV
│   └── ... (15+ composables)
└── util/                         # Utilitaires spécifiques
```

### Dépendances

Chaque sous-module est **indépendant** et peut avoir ses propres dépendances.

**Exemple `:core:network`**:
```kotlin
dependencies {
    implementation(project(":core:model"))
    implementation("com.squareup.retrofit2:retrofit")
    implementation("com.squareup.okhttp3:okhttp")
}
```

### Règles

✅ **Peut être utilisé par**: `:app`, `:domain`, `:data`
❌ **Ne doit PAS dépendre de**: `:app`, `:domain`, `:data`
✅ **Principe**: Réutilisabilité maximale, pas de dépendances circulaires

---

## 🔄 Flux de Dépendances

```
┌─────────────────────────────────────────────────────────┐
│                         :app                            │
│  (Presentation Layer — UI, ViewModels, DI, Navigation)  │
└────────────┬────────────────────────────────────────────┘
             │
             ├────────────> :domain
             │               (Use Cases, Repository Interfaces)
             │
             └────────────> :data
                             (Repository Implementations)
                             │
                             └────────────> :core:*
                                             (Shared Code)
```

**Règle d'Or**: Les dépendances vont **toujours vers le bas** (jamais de dépendance inverse).

---

## 🧹 Clarifications sur `app/di/`

### Historique

Anciennement nommé `app/core/`, ce dossier causait une **confusion** avec les modules `:core:*` à la racine.

### Rôle Actuel

`app/di/` contient **uniquement des modules d'injection de dépendances Hilt**:

```kotlin
// app/di/image/ImageModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    @Provides
    @Singleton
    fun provideImageLoader(context: Context): ImageLoader = ...
}
```

**Ce n'est PAS du code métier**, juste du câblage DI.

### Packages dans `app/di/`

| Package | Contenu | Rôle |
|---------|---------|------|
| `app/di/datastore/` | DataStoreModule, Extensions | Configuration DataStore + DI |
| `app/di/designsystem/` | Theme, Color, Type | Thème Compose (pas un module séparé car app-specific) |
| `app/di/image/` | ImageModule, PlexImageHelper, PlexImageKeyer | Configuration Coil + transformation d'images |
| `app/di/navigation/` | Screen sealed class | Définition des routes de navigation |
| `app/di/network/` | PlexImageHelper (duplication?) | Helpers réseau app-specific |

---

## 📊 Métriques du Projet

| Métrique | Valeur |
|----------|--------|
| **Modules Gradle** | 13 (1 app + 1 domain + 1 data + 9 core + 1 config) |
| **Fichiers Kotlin** | 265 |
| **Fichiers de tests** | 22 (67 cas) |
| **ViewModels** | 19 |
| **Écrans Compose** | 18 |
| **Use Cases** | 25 |
| **Repository Interfaces** | 16 |
| **Repository Implémentations** | 17 |
| **DAOs** | 11 |
| **Entities Room** | 12 |

---

## ✅ Validation de la Structure

### Checklist Conformité

- [x] 4 modules principaux séparés (app, domain, data, core)
- [x] Inversion de dépendance respectée (domain ne dépend pas de data)
- [x] Pas de duplication de code entre app/ et modules racine
- [x] Use cases consolidés dans :domain
- [x] app/core/ renommé en app/di/ pour clarté
- [x] Séparation Presentation/Business/Data respectée

### Tests de Conformité

```bash
# Vérifier que :domain ne dépend pas de :data ou :app
./gradlew :domain:dependencies | grep -E "(data|app)"
# Doit retourner vide

# Vérifier que :data dépend de :domain
./gradlew :data:dependencies | grep "domain"
# Doit trouver des résultats

# Vérifier que :app dépend de :domain et :data
./gradlew :app:dependencies | grep -E "(domain|data)"
# Doit trouver des résultats
```

---

## 🚀 Avantages de cette Architecture

### 1. Compilation Incrémentale
Seul le module modifié recompile. Exemple: modifier une entité dans `:core:model` ne recompile pas `:app`.

### 2. Testabilité
Chaque couche est testable indépendamment:
- `:domain` → Tests unitaires purs (pas de dépendances Android)
- `:data` → Tests avec mocks Retrofit/Room
- `:app` → Tests UI Compose

### 3. Réutilisabilité
Les modules `:core:*` peuvent être réutilisés dans d'autres projets (ex: widget Android TV).

### 4. Parallélisation Build
Gradle peut compiler les modules indépendants en parallèle → **build 2-3x plus rapide**.

### 5. Isolation des Responsabilités
Chaque module a un rôle clair → **maintenabilité** à long terme.

---

## 📚 Ressources

- [Android Developers — Multi-Module Guide](https://developer.android.com/topic/modularization)
- [Google Now in Android Architecture](https://github.com/android/nowinandroid)
- [Netflix Falcor Architecture](https://netflixtechblog.com/)
- [Clean Architecture by Uncle Bob](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

## 📝 Notes de Migration

### Changements Effectués (11 février 2026)

1. ✅ `app/core/` renommé en `app/di/` pour clarifier le rôle
2. ✅ `app/domain/usecase/ResolveEpisodeSourcesUseCase` déplacé vers `:domain`
3. ✅ Tous les imports mis à jour (`core.` → `di.`)
4. ✅ Dossiers vides `app/domain/` supprimés

### Impacts

- ✅ Aucun impact sur la logique métier
- ✅ Structure plus claire pour les nouveaux développeurs
- ✅ Conforme aux standards Android modernes

---

**Dernière mise à jour**: 11 février 2026
**Mainteneur**: PlexHubTV Team
**Statut**: ✅ Structure finale validée et documentée
