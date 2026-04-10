# Phase 4 — Qualité de code & Architecture

> **Rôle** : Architecture Agent (Agent 3) / Phase 4 de l'audit production-readiness v3.
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Ground truth** : `docs/audit/v3/phase0-cartography.md`
> **Scope** : 13 axes (Clean archi, boundaries, duplication, naming, dead code, deps, errors, tests, mappers, DI scope, caching, use cases, gradle perf).

---

## Résumé

### Compte des findings
- **P0** : 5 (défauts bloquant tests/crashes/build cassé)
- **P1** : 14 (dette majeure de maintenabilité)
- **P2** : 6 (polish / cleanup)
- **Total** : 25 findings

### Module health scorecard (A = pristine, F = at risk)

| Module | Score | Justification |
|---|---|---|
| `:app` | **C** | 201 fichiers, 2 `MainViewModel` classes coexistent, un UseCase orphelin en dehors de `:domain`, package `handler/` plat, features cohérentes mais hétérogènes (17 VMs sur 37 étendent `BaseViewModel`). |
| `:domain` | **C+** | Pas d'import Android/Compose dans le code, mais `build.gradle.kts` déclare `android-library` + `androidx.core.ktx` + `timber` (leak potentiel). `androidx.paging.PagingData` exposé dans 4 fichiers. 10–12 use cases pass-through pur. `@Singleton` sur use cases stateless. |
| `:data` | **C-** | Mélange `try/catch` (72 occ.) + `Result` (165 occ.) inconsistant. Impl de use case dans `:data/usecase/` (`ResolveEpisodeSourcesUseCaseImpl`). `MediaUrlResolver` + `DefaultMediaUrlResolver` en package `core.util` (pas `data.`). Repositories 250–800 LOC, grosse surface. |
| `:core:model` | **D** | Dépendance sur `androidx.compose.runtime.runtime-annotation` et usage de `@Immutable` sur `MediaItem`, `Hub`, `LiveChannel`. Domain model contaminé par Compose. |
| `:core:common` | **B-** | Contient `CoroutineModule` sous package `core.di` (pas `core.common.di`) — package/module drift. Présence de `CacheManager`, `ContentRatingHelper` OK. |
| `:core:network` | **D** | `build.gradle.kts` contient un bloc `android { }` imbriqué dans `android { }` (les `buildConfigField` ne sont pas appliqués comme prévu). Pas de module Jellyfin séparé malgré la doc. |
| `:core:database` | **B** | Propre ; 47 migrations + schemas exportés. Absence totale de tests unitaires (0 test). `kotlinOptions` manquants (remplacé par `kotlin { compilerOptions }`, OK). |
| `:core:datastore` | **B** | Clean, mais 0 tests (incluant `SecurePreferencesManager` qui est critique sécurité). |
| `:core:designsystem` | **B+** | Propre, uniquement Compose. 0 tests mais normal pour du theming. Pas de Detekt/Ktlint appliqués (plugin Ktlint absent dans le fichier). |
| `:core:ui` | **B** | Propre, Compose-only. Pas de Detekt/Ktlint appliqués non plus. 0 tests. |
| `:core:navigation` | **B+** | Minimaliste (2 fichiers). Pas de Detekt/Ktlint. Pas de tests. |

### Zones non auditées
- Build performance empirique (aucun `build scan` lancé, config cache non testé — constats basés lecture statique).
- `core/*/consumer-rules.pro` et `proguard-rules.pro` (hors scope archi pure).
- Contenu détaillé des 34 repositories et mappers (échantillonné à ~30 %).
- Tests manquants : vérifiés via inventaire Phase 0, pas via exécution.

---

## Findings

### AUDIT-4-001 — `:core:network/build.gradle.kts` : bloc `android { }` imbriqué, `buildConfigField` fantômes
**Phase** : 4 Architecture — **Sévérité** : **P0** — **Confiance** : Élevée

**Impact** : stabilité | build | team velocity
**Fichier(s)** : `core/network/build.gradle.kts:23-39`
**Dépendances** : aucune

**Preuve** :
```kotlin
android {
    namespace = "com.chakir.plexhubtv.core.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 27
        ...
    }
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localProperties.load(FileInputStream(localPropertiesFile))
    }

    val tmdbApiKey = localProperties.getProperty("TMDB_API_KEY") ?: ""
    val omdbApiKey = localProperties.getProperty("OMDB_API_KEY") ?: ""
    val openSubtitlesApiKey = localProperties.getProperty("OPENSUBTITLES_API_KEY") ?: ""

    android {                                    // <-- NESTED android { }
        defaultConfig {
            buildConfigField("String", "TMDB_API_KEY", "\"$tmdbApiKey\"")
            buildConfigField("String", "OMDB_API_KEY", "\"$omdbApiKey\"")
            buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"$openSubtitlesApiKey\"")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le second bloc `android { }` imbriqué fonctionne par accident grâce à la référence implicite `Project.android` accessible depuis DSL scope — mais ce n'est PAS la même chose qu'étendre le bloc parent. Les `buildConfigField` ainsi déclarés ne passent pas toujours par la pipeline Android normale (et peuvent être silencieusement ignorés selon l'ordre d'évaluation DSL AGP 9.0.1). Résultat : `BuildConfig.TMDB_API_KEY` dans `:core:network` peut être vide en runtime alors que la toml et le code Kotlin lisent des valeurs "lookup-key".

**Risque concret si non corrigé** :
- Rotation de clés API TMDb/OMDb/OpenSubtitles cassée silencieusement (fallback empty string).
- `RatingSyncWorker` et `SubtitleSearchService` interrogent l'API sans clé → 401/403 silencieux, pas de notes ni de sous-titres.
- Upgrade AGP → le DSL pourrait rejeter le bloc imbriqué et casser le build.

**Correctif recommandé** :
Aplatir : déplacer les `buildConfigField` directement dans le `defaultConfig` parent, avant la fermeture du premier bloc `android { }`.

**Architecture cible** :
```
core/network/build.gradle.kts
└── android { }
    ├── namespace, compileSdk
    ├── defaultConfig {
    │     minSdk, testInstrumentationRunner, consumerProguardFiles,
    │     buildConfigField("TMDB_API_KEY"),
    │     buildConfigField("OMDB_API_KEY"),
    │     buildConfigField("OPENSUBTITLES_API_KEY"),
    │   }
    ├── buildFeatures { buildConfig = true }
    └── buildTypes, compileOptions, testOptions
```

**Patch proposé** :
```kotlin
android {
    namespace = "com.chakir.plexhubtv.core.network"
    compileSdk = 36

    val localProperties = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(FileInputStream(f))
    }

    defaultConfig {
        minSdk = 27
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "TMDB_API_KEY",
            "\"${localProperties.getProperty("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "OMDB_API_KEY",
            "\"${localProperties.getProperty("OMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "OPENSUBTITLES_API_KEY",
            "\"${localProperties.getProperty("OPENSUBTITLES_API_KEY") ?: ""}\"")
    }

    buildFeatures { buildConfig = true }
    // ... rest unchanged
}
```

**Validation du fix** :
- `./gradlew :core:network:assembleDebug` et vérifier `core/network/build/generated/source/buildConfig/.../BuildConfig.java`.
- Un test unitaire léger (Robolectric) qui lit `BuildConfig.TMDB_API_KEY` et assert `.isNotBlank()` en debug.
- Detekt rule custom pour interdire les blocs `android { }` imbriqués.

---

### AUDIT-4-002 — `:domain` déclaré comme Android library + dépend de `androidx.core.ktx` et `timber`
**Phase** : 4 Architecture — **Sévérité** : **P0** — **Confiance** : Élevée

**Impact** : maintainability | testability | team velocity
**Fichier(s)** : `domain/build.gradle.kts:1-59`
**Dépendances** : aucune

**Preuve** :
```kotlin
plugins {
    alias(libs.plugins.android.library)      // <-- should be kotlin("jvm") or java-library
    alias(libs.plugins.kotlin.android)       // <-- should be kotlin("jvm")
    ...
}

android {
    namespace = "com.chakir.plexhubtv.domain"
    compileSdk = 36
    defaultConfig { minSdk = 27 ... }
    ...
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.paging.common)
    implementation("javax.inject:javax.inject:1")
    implementation(libs.androidx.core.ktx)   // <-- Android framework !
    implementation(libs.timber)              // <-- Android-only logger
    ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
`:domain` doit être une pure module Kotlin (JVM) pour garantir l'isolation du framework Android : c'est la promesse de Clean Architecture. En étant `android-library` :
1. Le module tire tout le SDK Android (`compileSdk=36`), ralentissant le build et la configuration.
2. Les use cases peuvent à tout moment importer `android.content.Context`, `androidx.lifecycle.*` etc. sans erreur de compilation. Aujourd'hui la grep confirme qu'il n'y a aucun `import android.` / `import androidx.` (sauf paging — voir AUDIT-4-005), mais la barrière est sociale, pas technique.
3. Les tests du domain requièrent Robolectric ou AndroidJUnitRunner pour tout ce qui dépend de `:core:common` (qui est aussi `android-library`), alors qu'ils devraient s'exécuter sur JVM pur.
4. `timber` n'est pas disponible sur pure JVM → empêche de migrer le module en JVM sans refactor.

**Risque concret si non corrigé** :
- Dérive progressive : tout développeur peut ajouter `Context.getString(R.string.x)` dans un UseCase sans review qui le bloque.
- Les tests unitaires de `:domain` ne peuvent pas exploiter `kotlinx-coroutines-test` sans framework Android warm-up (ralentissement 3-5x).
- Baseline profiles / R8 gagneraient à avoir `:domain` en JVM pur pour éliminer aggressivement les classes inutilisées.

**Correctif recommandé** :
Migrer `:domain` vers `kotlin("jvm")` + `java-library` ; remplacer `timber` par `kotlin-logging` ou `println`-gated (ou garder une interface de logging dans `:core:model`) ; retirer `androidx.core.ktx`.

**Architecture cible** :
```
:domain (pure JVM, kotlin("jvm"))
 ├── api(:core:model)
 ├── api(kotlinx.coroutines.core)
 ├── api(kotlinx.coroutines.paging.common)   // paging-common est JVM-compat
 ├── implementation(javax.inject)
 └── implementation(kotlin-logging)          // JVM logger
```

**Patch proposé** (sketch) :
```kotlin
// domain/build.gradle.kts
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)           // needs adding to libs.versions.toml
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.paging.common)       // JVM-compat
    implementation("javax.inject:javax.inject:1")
    // no core.ktx, no timber — inject a LoggerPort if needed
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
Supprimer les `import timber.log.Timber` des 6 fichiers `:domain` qui en utilisent (EnrichMediaItemUseCase, PreparePlaybackUseCase, GetMediaDetailUseCase, etc.) et les remplacer par un `Logger` injecté (interface dans `:domain`, impl timber dans `:app`).

**Validation du fix** :
- Konsist rule : `Konsist.scopeFromModule("domain").classes().assertTrue { it.fqnType.startsWith("com.chakir.plexhubtv.domain") || it.fqnType.startsWith("com.chakir.plexhubtv.core.model") || it.fqnType.startsWith("kotlin") || it.fqnType.startsWith("kotlinx.coroutines") }`.
- Build `:domain:test` doit passer sans initialisation Robolectric.
- `./gradlew :domain:dependencies` ne doit plus lister `androidx.core:core-ktx`.

---

### AUDIT-4-003 — `GetSuggestionsUseCase` dans `:app`, accède directement à `MediaDao` + `ServerClientResolver` + `MediaMapper`
**Phase** : 4 Architecture — **Sévérité** : **P0** — **Confiance** : Élevée

**Impact** : maintainability | test coverage | Clean Archi integrity
**Fichier(s)** : `app/src/main/java/com/chakir/plexhubtv/domain/usecase/GetSuggestionsUseCase.kt:1-108`
**Dépendances** : dépend de AUDIT-4-004 (MediaUrlResolver placement)

**Preuve** :
```kotlin
package com.chakir.plexhubtv.domain.usecase    // <-- declares :domain package
                                                // but file lives in :app !

import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.core.util.MediaUrlResolver
import com.chakir.plexhubtv.data.mapper.MediaMapper
import com.chakir.plexhubtv.data.repository.ServerClientResolver

@Singleton
class GetSuggestionsUseCase @Inject constructor(
    private val mediaDao: MediaDao,                         // data infra
    private val mediaMapper: MediaMapper,                   // data mapper
    private val mediaUrlResolver: MediaUrlResolver,         // data util
    private val serverClientResolver: ServerClientResolver, // data infra
) {
    suspend operator fun invoke(limit: Int = 20): List<MediaItem> {
        // uses mediaDao.getRecentWatchedGenres(), .getUnwatchedByGenre(),
        // .getRandomUnwatched(), .getFreshUnwatched()
        // mixes entity→domain mapping + URL resolution + server lookup
    }
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
1. Violation Clean Archi : un "use case" consomme directement des classes data-layer (`MediaDao`, `MediaMapper`, `ServerClientResolver`) et infrastructure-layer (`MediaUrlResolver`).
2. Le package déclaré (`com.chakir.plexhubtv.domain.usecase`) ment sur l'emplacement physique (`:app/src/main/java/.../domain/usecase/`) — IDE de recherche et Konsist échouent.
3. C'est un orphelin : tous les autres use cases sont dans `:domain/src/main/java/.../domain/usecase/`. Cette incohérence dit clairement "on n'a pas pu le mettre dans :domain sans grosse refacto".
4. Impossible à tester sans warm-up Room complet (Robolectric), contrairement aux autres use cases.

**Risque concret si non corrigé** :
- Nouveau développeur cherche `GetSuggestionsUseCase.kt` dans `:domain` → ne le trouve pas, dupliquer ou le casser.
- Impossible de migrer `:domain` en pure JVM (voir AUDIT-4-002) tant que ce use case existe sous la même convention de package.
- Les responsabilités (3 queries Room + dedup + url resolution) mélangées dans un fichier = impossible à refactoriser sans tout réécrire.

**Correctif recommandé** :
Créer `SuggestionsRepository` (interface dans `:domain`, impl dans `:data`), déplacer la logique DAO + mapping + URL resolution dans l'impl, exposer une méthode simple `suspend fun getSuggestions(limit: Int): List<MediaItem>`. Le use case devient un vrai pass-through de 3 lignes dans `:domain`.

**Architecture cible** :
```
:domain
 └── usecase/GetSuggestionsUseCase          // 3 lines: suggestionsRepository.getSuggestions(limit)
 └── repository/SuggestionsRepository       // interface

:data
 └── repository/SuggestionsRepositoryImpl   // holds the 108 lines of logic today
     ↓
     mediaDao (infra)
     mediaMapper (mapper)
     serverClientResolver (infra)
     mediaUrlResolver (util)
```

**Patch proposé** :
```kotlin
// :domain/repository/SuggestionsRepository.kt
package com.chakir.plexhubtv.domain.repository
import com.chakir.plexhubtv.core.model.MediaItem
interface SuggestionsRepository {
    suspend fun getSuggestions(limit: Int = 20): List<MediaItem>
}

// :domain/usecase/GetSuggestionsUseCase.kt (moved to :domain)
package com.chakir.plexhubtv.domain.usecase
class GetSuggestionsUseCase @Inject constructor(
    private val repo: SuggestionsRepository,
) {
    suspend operator fun invoke(limit: Int = 20): List<MediaItem> = repo.getSuggestions(limit)
}

// :data/repository/SuggestionsRepositoryImpl.kt — move existing 108-line body here

// :data/di/RepositoryModule.kt — add @Binds @Singleton
```

**Validation du fix** :
- Supprimer `app/src/main/java/com/chakir/plexhubtv/domain/usecase/GetSuggestionsUseCase.kt`.
- Konsist : aucune classe dans `:app` ne doit être déclarée en package `com.chakir.plexhubtv.domain.*`.
- Grep `"GetSuggestionsUseCase"` doit pointer exclusivement vers `:domain` et les ViewModels consommateurs.

---

### AUDIT-4-004 — `MediaUrlResolver` en package `core.util` mais vit dans `:data` (package/module drift)
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | team velocity | searchability
**Fichier(s)** : `data/src/main/java/com/chakir/plexhubtv/core/util/MediaUrlResolver.kt`, `data/src/test/java/com/chakir/plexhubtv/core/util/MediaUrlResolverTest.kt`
**Dépendances** : aucune

**Preuve** :
```kotlin
// File: data/src/main/java/com/chakir/plexhubtv/core/util/MediaUrlResolver.kt
package com.chakir.plexhubtv.core.util   // <-- :core package, but in :data module

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.network.util.getOptimizedImageUrl
import com.chakir.plexhubtv.core.model.MediaItem

interface MediaUrlResolver { ... }
class DefaultMediaUrlResolver @Inject constructor() : MediaUrlResolver { ... }
```

Le package `com.chakir.plexhubtv.core.util` existe aussi dans `:core:common` (`CacheManager`, `ContentRatingHelper`, `ContentUtils`) et dans `:core:common` sous `core.di.CoroutineModule`. Ce pattern multi-modules → même package est une source de confusion pour le refactoring.

**Pourquoi c'est un problème dans PlexHubTV** :
1. Le nom suggère que la classe est dans `:core:common` (partagée, utilitaire pur), alors qu'elle dépend de `MediaEntity` (Room → `:core:database`) ET `MediaItem` (`:core:model`) ET `:core:network` — elle ne pourrait PAS vivre dans `:core:common` même si on voulait.
2. Un développeur qui cherche "où est MediaUrlResolver ?" par package name va d'abord explorer `:core:common`, ne le trouvera pas, puis devra grep tout le repo.
3. Le test `MediaUrlResolverTest` vit dans `data/src/test/java/.../core/util/` — deux drift combinés (package/module + test/impl).
4. Fait miroir avec AUDIT-4-003 : `GetSuggestionsUseCase` utilise `core.util.MediaUrlResolver` qu'il importe depuis `:data`. C'est un trou qu'un Konsist rule repérerait.

**Risque concret si non corrigé** :
- Futur refactor : tentative de déplacer `MediaUrlResolver` dans `:core:common` → erreurs de dépendances cycliques (`:core:common` ne peut dépendre de `:core:database`). Le développeur abandonnera et laissera l'incohérence croître.
- Konsist / Detekt / tooling de module boundaries ne peut pas distinguer les violations réelles des faux positifs.

**Correctif recommandé** :
Renommer le package en `com.chakir.plexhubtv.data.util.MediaUrlResolver`. Mettre à jour les 5 imports dans `:data` et `:app` (Hilt binding, GetSuggestionsUseCase, HubsRepositoryImpl, LibraryRepositoryImpl, MediaDetailRepositoryImpl).

**Architecture cible** :
```
:data
 └── util/
     ├── MediaUrlResolver.kt         // package com.chakir.plexhubtv.data.util
     └── DefaultMediaUrlResolver.kt  // (can stay in same file)
```

**Patch proposé** :
```kotlin
// data/src/main/java/com/chakir/plexhubtv/data/util/MediaUrlResolver.kt
package com.chakir.plexhubtv.data.util

import com.chakir.plexhubtv.core.database.MediaEntity
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.core.network.util.getOptimizedImageUrl
import javax.inject.Inject

interface MediaUrlResolver { /* unchanged */ }
class DefaultMediaUrlResolver @Inject constructor() : MediaUrlResolver { /* unchanged */ }
```

Même opération sur `CoroutineModule` (déplacer de `core.di` vers `core.common.di`).

**Validation du fix** :
- Konsist rule : `files with package starting with "com.chakir.plexhubtv.core."` must reside in `:core:*` modules only.
- Build vert sur tous les modules.
- Git grep `com.chakir.plexhubtv.core.util` retourne uniquement des fichiers dans `:core:common` ou `:core:ui`.

---

### AUDIT-4-005 — `androidx.paging.PagingData` exposé dans l'API `:domain`
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | testability | portability
**Fichier(s)** :
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/GetLibraryContentUseCase.kt:53`
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/GetWatchHistoryUseCase.kt:17`
- `domain/src/main/java/com/chakir/plexhubtv/domain/repository/LibraryRepository.kt:41`
- `domain/src/main/java/com/chakir/plexhubtv/domain/repository/PlaybackRepository.kt`

**Dépendances** : AUDIT-4-002

**Preuve** :
```kotlin
// GetLibraryContentUseCase.kt
operator fun invoke(...): Flow<androidx.paging.PagingData<MediaItem>> =
    libraryRepository.getLibraryContent(...)

// LibraryRepository.kt
fun getLibraryContent(...): Flow<androidx.paging.PagingData<MediaItem>>
```

**Pourquoi c'est un problème dans PlexHubTV** :
`PagingData` est un type `androidx` — il transporte implicitement des contrats framework (`RemoteMediator`, `PagingSource`, `DiffingStrategy`, annotations Lifecycle). Même si `paging-common` existe côté JVM (utilisé par Ktor/etc.), la cohérence Clean Architecture veut que le domain expose des types natifs Kotlin ou des abstractions propres : `Flow<List<MediaItem>>`, `Channel<Page<MediaItem>>`, ou une interface `PagedFlow<T>` maison.

**Risque concret si non corrigé** :
- Impossible de migrer `:domain` en pure JVM strict sans inclure `paging-common` (acceptable aujourd'hui mais la surface augmente à chaque version).
- Tests unitaires doivent mocker `PagingData` qui est non-trivial (utilisait `PagingData.from(list)` dans les tests — couplage fort).
- Un futur consommateur KMP (Kotlin Multiplatform) ne pourrait pas réutiliser le domain sans embarquer paging-common.

**Correctif recommandé** :
Conserver `PagingData` dans l'API pragmatiquement (c'est OK pour une app mono-plateforme), mais AU MINIMUM :
1. Isoler dans un type alias domain : `typealias DomainPagedFlow<T> = Flow<androidx.paging.PagingData<T>>` dans `:domain/paging/PagingTypes.kt`.
2. Documenter l'exception dans `ARCHITECTURE.md`.
3. Ajouter un commentaire `// TODO(KMP): replace with PagedFlow abstraction when going multiplatform`.

**Architecture cible** :
```
:domain
 └── paging/
     └── DomainPagedFlow.kt   // typealias DomainPagedFlow<T> = Flow<PagingData<T>>
                              // or sealed interface PagedResult<T>
```

**Patch proposé** :
```kotlin
// domain/src/main/java/com/chakir/plexhubtv/domain/paging/DomainPagedFlow.kt
package com.chakir.plexhubtv.domain.paging
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

typealias DomainPagedFlow<T> = Flow<PagingData<T>>
```

**Validation du fix** :
- Konsist rule : `domain.repository.*` methods returning `Flow<*>` must use `DomainPagedFlow` when paginated.
- Code review checklist : "new domain API must not import androidx.paging directly".

---

### AUDIT-4-006 — `:core:model` dépend de `androidx.compose.runtime.runtime-annotation` et ses modèles sont `@Immutable`
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | portability | Clean Archi
**Fichier(s)** :
- `core/model/build.gradle.kts:30` (`implementation(libs.androidx.compose.runtime.annotation)`)
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/MediaItem.kt:3,27`
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/Hub.kt:3,18`
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/LiveChannel.kt:3,5,24`

**Dépendances** : aucune

**Preuve** :
```kotlin
// MediaItem.kt
package com.chakir.plexhubtv.core.model
import androidx.compose.runtime.Immutable

@Immutable
data class MediaItem(
    val id: String,
    val ratingKey: String,
    ...
)
```

**Pourquoi c'est un problème dans PlexHubTV** :
`:core:model` est censé être le module le plus bas et le plus neutre de la pyramide — consommé par `:domain`, `:data`, `:core:network`, `:core:database`. Le forcer à dépendre de Compose runtime :
1. Transporte Compose dans `:core:database` et `:core:network` qui n'en ont aucun usage légitime.
2. Empêche `:core:model` d'être un module KMP ou pure JVM.
3. `@Immutable` est uniquement utile pour la recomposition Compose — c'est un détail UI, pas un invariant du domain model.
4. L'annotation est appliquée de manière sporadique (3 classes sur ~40 modèles) → inconsistance.

**Risque concret si non corrigé** :
- Upgrade Compose BOM → cassure potentielle dans `:core:model`, rebuild de toute la chaîne.
- Un futur serveur Kotlin/JVM (Ktor API pour backup/restore) ne pourra pas réutiliser `:core:model`.

**Correctif recommandé** :
Deux options :
- **Option A (recommandée)** : retirer les `@Immutable` de `:core:model`, créer une couche `core/model-ui` OU annoter les state-holders au niveau des ViewModels/UiState. Retirer la dep Compose de `:core:model`.
- **Option B** : utiliser une annotation maison `@DomainImmutable` dans `:core:model`, mapper vers Compose `@Immutable` via compiler plugin ou laisser Compose inférer la stability.

**Architecture cible** :
```
:core:model (pure JVM, kotlinx.serialization + immutable-collections only)
   ├── MediaItem (data class, no Compose annotation)
   ├── Hub
   └── LiveChannel

:core:ui (annotations Compose sont appliquées sur les UiState wrappers)
   └── home/HomeUiState(@Immutable) { val items: ImmutableList<MediaItem> }
```

**Patch proposé** :
```diff
// core/model/build.gradle.kts
 dependencies {
     implementation(libs.kotlinx.serialization.json)
     api(libs.kotlinx.collections.immutable)
-    implementation(libs.androidx.compose.runtime.annotation)
     testImplementation(libs.junit)
     testImplementation(libs.truth)
 }
```
```diff
// core/model/src/main/java/com/chakir/plexhubtv/core/model/MediaItem.kt
-import androidx.compose.runtime.Immutable
-
-@Immutable
 data class MediaItem(...)
```
Idem Hub.kt, LiveChannel.kt. Puis dans les UiState wrappers, marquer les collections comme `ImmutableList<MediaItem>` (déjà fait) — Compose infère la stability automatiquement.

**Validation du fix** :
- `./gradlew :core:model:dependencies` ne liste plus `androidx.compose.runtime:runtime-annotation`.
- Compose runtime `strong-skipping` reste actif car `MediaItem` est une `data class` avec des champs primitifs et `ImmutableList`.
- Konsist rule : `:core:model` ne doit pas dépendre de `androidx.*`.

---

### AUDIT-4-007 — Interface de use case avec impl dans `:data` (`ResolveEpisodeSourcesUseCaseImpl`)
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | team velocity | consistency
**Fichier(s)** :
- `domain/src/main/java/com/chakir/plexhubtv/domain/usecase/ResolveEpisodeSourcesUseCase.kt` (interface)
- `data/src/main/java/com/chakir/plexhubtv/data/usecase/ResolveEpisodeSourcesUseCaseImpl.kt`
- `data/src/main/java/com/chakir/plexhubtv/data/di/RepositoryModule.kt:143-147` (binding)

**Dépendances** : aucune

**Preuve** :
```kotlin
// :domain
interface ResolveEpisodeSourcesUseCase {
    suspend operator fun invoke(episode: MediaItem): List<MediaSource>
}

// :data
class ResolveEpisodeSourcesUseCaseImpl @Inject constructor(...) : ResolveEpisodeSourcesUseCase

// :data/di/RepositoryModule
@Binds @Singleton
abstract fun bindResolveEpisodeSourcesUseCase(
    impl: com.chakir.plexhubtv.data.usecase.ResolveEpisodeSourcesUseCaseImpl,
): com.chakir.plexhubtv.domain.usecase.ResolveEpisodeSourcesUseCase
```

**Pourquoi c'est un problème dans PlexHubTV** :
Un use case est, dans Clean Architecture, un orchestrateur de domain logic — il ne devrait PAS avoir besoin d'une interface + impl split. Les 28 autres use cases du projet sont des classes concrètes dans `:domain`. Cette exception isolée signale qu'il y avait soit :
1. Un besoin d'accès direct à un infra (DAO, client HTTP), auquel cas c'était un repository mal nommé.
2. Une tentative de respecter la règle des dépendances, sans passer par un repository proprement dit.

Résultat : incohérence, un seul binding Hilt `@Binds` pour un use case, et les développeurs ne savent plus où chercher la logique.

**Risque concret si non corrigé** :
- Précédent qui légitime de créer d'autres use case interfaces → duplication croissante, complexité DI accrue.
- Tests plus compliqués : deux fichiers à maintenir pour chaque use case.

**Correctif recommandé** :
Soit :
- **A** : Si la classe est vraiment un use case (orchestration stateless) → déplacer `ResolveEpisodeSourcesUseCaseImpl` dans `:domain` comme classe concrète, supprimer l'interface et le `@Binds`.
- **B** : Si elle accède à DAO/HTTP → renommer en `EpisodeSourcesRepository` (interface dans `:domain`, impl dans `:data`), aligner avec le pattern existant.

**Architecture cible (option A, la plus simple)** :
```
:domain
 └── usecase/ResolveEpisodeSourcesUseCase (classe concrète, comme les 28 autres)
```

**Patch proposé** :
Inspecter le corps de `ResolveEpisodeSourcesUseCaseImpl` pour déterminer s'il touche à Dao/Api directement ou s'il orchestre d'autres repositories. Si orchestration pure → merge interface + impl dans `:domain`. Sinon → repository pattern.

**Validation du fix** :
- Konsist rule : `:domain/usecase` classes are concrete (no interface/impl split).
- `:data/usecase/` package doit être vide.

---

### AUDIT-4-008 — Use cases pass-through (≥10 fichiers) : wrapper inutile sans logique métier
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | team velocity | code review noise
**Fichier(s)** :
- `GetFavoritesUseCase.kt` — 1 ligne `repo.getFavorites()`
- `IsFavoriteUseCase.kt` — 2 méthodes pass-through
- `ToggleFavoriteUseCase.kt` — 1 ligne `repo.toggleFavorite(media)`
- `DeleteMediaUseCase.kt` — 1 ligne `repo.deleteMedia(...)`
- `GetWatchHistoryUseCase.kt` — 1 ligne `repo.getWatchHistoryPaged()`
- `GetMediaCollectionsUseCase.kt` — 1 ligne
- `GetCollectionUseCase.kt` — 1 ligne
- `GetFavoriteActorsUseCase.kt` — 1 ligne
- `GetLibraryContentUseCase.kt` — paramètres forwardés (cosmétique)
- `GetXtreamCategoriesUseCase.kt` — 2 méthodes pass-through
- `GetSimilarMediaUseCase.kt` (à vérifier)
- `GetWatchHistoryUseCase.kt` — pass-through paging

**Dépendances** : aucune

**Preuve** :
```kotlin
// IsFavoriteUseCase.kt
class IsFavoriteUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
) {
    operator fun invoke(ratingKey: String, serverId: String): Flow<Boolean> =
        favoritesRepository.isFavorite(ratingKey, serverId)
    fun anyOf(ratingKeys: List<String>): Flow<Boolean> =
        favoritesRepository.isFavoriteAny(ratingKeys)
}

// GetCollectionUseCase.kt
class GetCollectionUseCase @Inject constructor(
    private val mediaDetailRepository: MediaDetailRepository,
) {
    operator fun invoke(collectionId: String, serverId: String) =
        mediaDetailRepository.getCollection(collectionId, serverId)
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le pattern "1 use case par endpoint du repository" double artificiellement la surface : ~10 fichiers dans `:domain`, ~10 bindings d'injection, ~10 mocks dans les tests ViewModel, ~10 imports. Aucune logique métier n'est ajoutée. Chaque renommage d'une méthode de repository entraîne le renommage du use case, puis du ViewModel.

Par contraste, les use cases qui contiennent de la vraie logique métier sont précieux :
- `EnrichMediaItemUseCase` (642 LOC, Room-first + network fallback + cache)
- `PreparePlaybackUseCase` (pipeline enrichment → source selection → URL build)
- `SortOnDeckUseCase` (tri multi-critères)
- `FilterContentByAgeUseCase` (règles parental control)
- `SearchAcrossServersUseCase` (agrégation multi-source)

**Risque concret si non corrigé** :
- Coût maintenance : chaque modification de signature repository ripple sur 2 couches.
- Temps de compile accru (fichiers supplémentaires à compiler/KSP).
- Tests ViewModel verbeux : chaque VM doit `mockk<GetFavoritesUseCase>()` au lieu d'injecter directement `FavoritesRepository`.

**Correctif recommandé** :
**Politique : "Use case = au moins 1 opération métier observable".**
1. Supprimer les use cases pass-through purs (~10 fichiers).
2. ViewModels injectent directement le repository quand il n'y a pas d'orchestration.
3. Conserver les use cases avec vraie logique (enrichment, playback prep, filters, sort, search, sync).
4. Ajouter une règle Konsist : un use case doit contenir soit (a) ≥2 dépendances repository, soit (b) ≥10 LOC de logique hors appel forward.

**Architecture cible** :
```
Before:
 VM --> IsFavoriteUseCase --> FavoritesRepository

After:
 VM --> FavoritesRepository.isFavorite(ratingKey, serverId)
```

**Patch proposé** (exemple pour `IsFavoriteUseCase`) :
```kotlin
// Supprimer domain/usecase/IsFavoriteUseCase.kt

// Dans MediaDetailViewModel:
-class MediaDetailViewModel @Inject constructor(
-    private val isFavoriteUseCase: IsFavoriteUseCase,
+class MediaDetailViewModel @Inject constructor(
+    private val favoritesRepository: FavoritesRepository,
     ...
 ) {
-    val isFavorite = isFavoriteUseCase(ratingKey, serverId)
+    val isFavorite = favoritesRepository.isFavorite(ratingKey, serverId)
 }
```

**Validation du fix** :
- Konsist : `usecase` class must have `≥2 constructor params` OR `≥10 body LOC`.
- Les tests VM doivent être simplifiés d'autant.
- Code review : PR description liste les use cases supprimés.

---

### AUDIT-4-009 — `@Singleton` sur use cases stateless (`SortOnDeckUseCase`, `FilterContentByAgeUseCase`, `GetEnabledServerIdsUseCase`, `GetUnifiedHomeContentUseCase`)
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : maintainability | micro-performance | team velocity
**Fichier(s)** :
- `domain/usecase/SortOnDeckUseCase.kt:17` (`@Singleton`) — pure function, 0 state
- `domain/usecase/FilterContentByAgeUseCase.kt:15` (`@Singleton`) — pure function, 0 state
- `domain/usecase/GetEnabledServerIdsUseCase.kt:9` (`@Singleton`) — stateless
- `domain/usecase/GetUnifiedHomeContentUseCase.kt` — à vérifier

**Dépendances** : aucune

**Preuve** :
```kotlin
@Singleton
class SortOnDeckUseCase @Inject constructor() {
    operator fun invoke(items: List<MediaItem>): List<MediaItem> =
        items.sortedWith(compareByDescending<MediaItem> { effectiveTimestamp(it) }...)
    private fun effectiveTimestamp(...) = ...
    private fun priorityScore(...) = ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
`@Singleton` est utilisé ici comme "j'ai lu qu'il fallait annoter les use cases" plutôt que pour une raison précise. Pour un use case vraiment stateful (cache, coordinator, in-flight dedup comme `EnrichMediaItemUseCase`) c'est correct. Pour une fonction pure, `@Singleton` :
1. Force Hilt à générer et retenir une référence pendant toute la vie de l'app.
2. Confond "stateless fn" avec "coordinator singleton", masquant les vrais singletons critiques.
3. Empêche de simplifier en top-level `fun sortOnDeck(items: List<MediaItem>)` dans un fichier `:domain/logic/OnDeckSorting.kt`.

**Risque concret si non corrigé** :
- Faible impact fonctionnel, mais dérive culturelle : chaque nouveau dev suit le pattern et annote ses pures-fn use cases `@Singleton`.
- Difficile de distinguer les "vraies" singletons dangereux (EnrichMediaItemUseCase avec cache) des faux singletons.

**Correctif recommandé** :
Supprimer `@Singleton` de tous les use cases stateless. Pour les use cases purs vraiment triviaux (`SortOnDeckUseCase`, `FilterContentByAgeUseCase`), envisager de les transformer en top-level extension functions dans `:domain/logic/`.

**Architecture cible** :
```
:domain/usecase/
  ├── EnrichMediaItemUseCase         @Singleton (stateful: cache, in-flight dedup)
  ├── PreparePlaybackUseCase         (stateless, no @Singleton)
  ├── SortOnDeckUseCase              (stateless → consider top-level fn)
  └── FilterContentByAgeUseCase      (stateless → consider top-level fn)
```

**Patch proposé** :
```diff
-@Singleton
 class SortOnDeckUseCase @Inject constructor() { ... }

-@Singleton
 class FilterContentByAgeUseCase @Inject constructor() { ... }
```

**Validation du fix** :
- Code review checklist : `@Singleton` sur use case = justification obligatoire dans le KDoc ("caches ..., deduplicates ...").
- Optional Konsist rule : `:domain/usecase/*` avec `@Singleton` doit contenir au moins un `@Volatile`, `ConcurrentHashMap`, `MutableStateFlow` ou `CompletableDeferred`.

---

### AUDIT-4-010 — `BaseViewModel` adopté de manière incohérente (17 / 37 ViewModels)
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | error UX | consistency
**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/common/BaseViewModel.kt`
- 17 VMs l'étendent, 20 ne l'étendent pas (MainViewModel root, AuthViewModel, SplashViewModel, LoadingViewModel, LibrarySelectionViewModel, PlexHomeSwitcherViewModel, AppProfileViewModel, ScreensaverViewModel, IptvViewModel, JellyfinSetupViewModel, XtreamSetupViewModel, XtreamCategorySelectionViewModel, DebugViewModel, MediaEnrichmentViewModel, PlaybackStatsViewModel, TrackSelectionViewModel, ServerStatusViewModel, SubtitleStyleViewModel, etc.)

**Dépendances** : aucune

**Preuve** :
```kotlin
// BaseViewModel.kt
abstract class BaseViewModel : ViewModel() {
    protected val _errorEvents = Channel<AppError>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()
    protected suspend fun emitError(error: AppError) {
        _errorEvents.send(error)
    }
}

// LibraryViewModel extends it — OK
class LibraryViewModel @Inject constructor(...) : BaseViewModel()

// MainViewModel (root) does not extend it:
class MainViewModel @Inject constructor(...) : ViewModel()

// AuthViewModel does not extend it either
```

**Pourquoi c'est un problème dans PlexHubTV** :
La convention "émettre les erreurs via `errorEvents` pour affichage snackbar uniforme" est partiellement appliquée. Les 20 VMs qui n'étendent pas `BaseViewModel` inventent leur propre mécanisme :
- `StateFlow<String?>` pour error message
- `MutableStateFlow<AuthState.Error>` sealed
- `try/catch` silencieux + Timber
- Pas de channel → perte d'événements single-shot (toast montré deux fois sur rotation).

Les écrans correspondants doivent adapter leur `HandleErrors(errorEvents)` en conséquence — incohérence UX.

**Risque concret si non corrigé** :
- Crash rapportés par les users : "j'ai pas vu le message d'erreur" → en réalité le VM ne l'émet pas.
- Duplicate code dans les VMs non-Base : 3-5 lignes de boilerplate error state par VM.
- Impossible de greffer un handler global (ex. emitter vers Firebase Crashlytics) depuis la Base car 20 VMs le contournent.

**Correctif recommandé** :
Normaliser : **tous** les VMs étendent `BaseViewModel`, sauf les 2-3 cas spéciaux (SplashViewModel, MainViewModel root qui fait de la coordination app-level).

**Architecture cible** :
```
app/feature/common/
 └── BaseViewModel              abstract class
      ├── errorEvents: Flow<AppError>
      ├── emitError(AppError)
      └── launchSafe { } helper (wraps viewModelScope.launch + try/catch → emitError)

Concrete VMs:
 └── 35 VMs extend BaseViewModel
 └── 2 exceptions:
      ├── SplashViewModel (stateless navigation only)
      └── MainViewModel (app-level, AuthEventBus consumer, uses AppError differently)
```

**Patch proposé** :
Une passe mécanique :
```diff
-class AuthViewModel @Inject constructor(...) : ViewModel() {
+class AuthViewModel @Inject constructor(...) : BaseViewModel() {
     ...
-    _uiState.update { it.copy(error = e.toAppError()) }
+    emitError(e.toAppError())
 }
```

Côté `BaseViewModel`, ajouter un helper :
```kotlin
protected fun launchSafe(block: suspend CoroutineScope.() -> Unit) {
    viewModelScope.launch {
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { emitError(e.toAppError()) }
    }
}
```

**Validation du fix** :
- Konsist rule : `feature.**.*ViewModel` must extend `BaseViewModel` (with allowlist for SplashViewModel, MainViewModel).
- Audit UX confirme `HandleErrors(vm.errorEvents)` présent dans chaque Route composable.

---

### AUDIT-4-011 — Deux classes `MainViewModel` dans packages différents (naming collision cognitive)
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : maintainability | onboarding | IDE search
**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/MainViewModel.kt` (root package, app-level auth + update coordinator, 150+ LOC)
- `app/src/main/java/com/chakir/plexhubtv/feature/main/MainViewModel.kt` (50 LOC, juste offline state)

**Dépendances** : aucune

**Preuve** :
```kotlin
// Root MainViewModel — used by MainActivity
package com.chakir.plexhubtv
@HiltViewModel
class MainViewModel @Inject constructor(
    private val authEventBus: AuthEventBus,
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val updateChecker: UpdateChecker,
    ...
) : ViewModel()

// Feature MainViewModel — used by MainScreen
package com.chakir.plexhubtv.feature.main
@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Hilt ne confond pas les deux (packages différents), mais :
1. `Ctrl+N MainViewModel` renvoie deux résultats — développeur confus.
2. Stack traces Crashlytics montrent "MainViewModel$1" sans différencier.
3. Les deux ont `connectionManager` → duplication partielle.
4. L'un observe `isOffline`, l'autre coordonne auth/update — responsabilités entrelacées qui auraient pu/dû être fusionnées ou renommées.

**Risque concret si non corrigé** :
- Nouveau dev modifie le mauvais VM → bug invisible.
- Gradle refactor (rename) tombe sur les deux fichiers.

**Correctif recommandé** :
Renommer :
- `feature/main/MainViewModel` → `MainScreenViewModel` (ou fusionner avec HomeViewModel si sidebar state).
- `com.chakir.plexhubtv.MainViewModel` → `AppSessionViewModel` ou `AppCoordinatorViewModel` (rôle : session, update, auth).

**Architecture cible** :
```
:app/src/main/java/com/chakir/plexhubtv/
 ├── AppSessionViewModel                         // formerly MainViewModel root
 │    └── auth events, update check, app-level state
 └── feature/main/MainScreenViewModel            // formerly feature.main.MainViewModel
      └── sidebar/offline state
```

**Patch proposé** :
Rename via IDE refactor (File → Rename), puis mise à jour des :
- `MainActivity.kt` : `private val appSessionViewModel: AppSessionViewModel by viewModels()`
- `feature/main/MainScreen.kt` : `viewModel: MainScreenViewModel = hiltViewModel()`
- Tests : `MainViewModelTest` → split en `AppSessionViewModelTest` + `MainScreenViewModelTest`.

**Validation du fix** :
- `Ctrl+N MainViewModel` : 0 résultats.
- Tests unitaires passent inchangés (logique identique, seul le nom change).

---

### AUDIT-4-012 — Mélange `try/catch` + `Result` sans politique claire dans `:data`
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : maintainability | error handling | testability
**Fichier(s)** :
- `data/src/main/java/com/chakir/plexhubtv/data/repository/**/*.kt` — 72 occurrences `try/catch`, 165 occurrences `Result.*` / `runCatching` (grep brut)

**Dépendances** : lien avec AUDIT-4-010 (error handling VM)

**Preuve** :
```kotlin
// LibraryRepositoryImpl.kt
override suspend fun getLibraries(serverId: String): Result<List<LibrarySection>> {
    try {
        val client = serverClientResolver.getClient(serverId) ?: run {
            val cached = database.librarySectionDao().getLibrarySections(serverId).first()
            if (cached.isNotEmpty()) {
                return Result.success(cached.map { ... })
            }
            return Result.failure(AppError.Network.NoConnection("..."))
        }
        val response = client.getSections()
        ...
    } catch (e: Exception) { ... }
}

// FavoritesRepositoryImpl.kt — Result-heavy
override suspend fun toggleFavorite(media: MediaItem): Result<Boolean> = runCatching {
    ...
}

// HubsRepositoryImpl.kt — try/catch nested 4 deep
flow {
    try { emit(getCachedHubs()) } catch (e: Exception) { Timber.e(e, ...) }
    ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Pas de politique cohérente :
- Certains repos retournent `Result<T>`, d'autres `T` et lèvent les exceptions.
- `AppError` existe dans `:core:model` mais n'est utilisé que dans ~5 repos.
- `.toAppError()` est importé sporadiquement.
- Les `Flow<T>` ne peuvent pas retourner `Result` facilement → swallowed exceptions dans `catch { }` sans remontée.
- `try { } catch (e: Exception) { Timber.w(e); return emptyList() }` silencieux : user voit un écran vide sans message d'erreur.

**Risque concret si non corrigé** :
- Erreurs silencieuses : user ne sait pas pourquoi son écran est vide.
- Tests mocks : certains repos doivent stubber `Result.success(...)`, d'autres `flow { emit(x) }` + gestion exception.
- Crashes en production : un `.getOrNull()` manquant sur un `Result.failure` peut propager une exception non wrappée dans un `Flow` → crash du collecteur UI.

**Correctif recommandé** :
Définir et documenter une politique explicite :
1. **Suspend fonctions qui peuvent échouer** → retourner `Result<T>`, wrapping fait dans le repository impl.
2. **Flow fonctions** → exposer `Flow<DataState<T>>` (sealed : `Loading | Success(T) | Error(AppError)`) OU utiliser `.catch { emit(empty/error state) }` avec émission explicite.
3. **Tout catch doit soit** : (a) émettre un `AppError` via `errorEvents`, (b) retourner `Result.failure(e.toAppError())`, (c) logger ET rethrow (jamais swallow silencieux).
4. **Aucune exception raw** ne doit propager au-delà d'un repository — le boundary est toujours `Result<T>` ou `DataState<T>`.

**Architecture cible** :
```
:core:model/DataState.kt  (sealed interface)
 ├── Loading
 ├── Success(data: T)
 └── Error(error: AppError)

Repository conventions:
 ├── suspend fun getX(): Result<X>                        (one-shot)
 └── fun observeX(): Flow<DataState<X>>                   (stream)

Never:
 ├── suspend fun getX(): X                                (throws unchecked)
 └── fun observeX(): Flow<X> { emit(...) } .catch { ... } (swallows silently)
```

**Patch proposé** (extrait — politique à appliquer) :
```kotlin
// :core:model/DataState.kt
sealed interface DataState<out T> {
    data object Loading : DataState<Nothing>
    data class Success<T>(val data: T) : DataState<T>
    data class Error(val error: AppError) : DataState<Nothing>
}

// Example repo migration
override fun observeHubs(): Flow<DataState<List<Hub>>> = flow {
    emit(DataState.Loading)
    try {
        emit(DataState.Success(fetchHubs()))
    } catch (e: CancellationException) { throw e }
      catch (e: Throwable) { emit(DataState.Error(e.toAppError())) }
}
```

**Validation du fix** :
- Konsist rule : pas de `Result<Unit>` sans `Result.success`/`Result.failure`.
- Detekt rule : `TooGenericExceptionCaught` activé avec exception whitelist vide.
- Audit grep : `catch (e: Exception)` → chaque cas justifié par un commentaire ou émission `AppError`.

---

### AUDIT-4-013 — Stratégie de caching non documentée, 2 niveaux co-existent (Room entity + ApiCache blob)
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Moyenne

**Impact** : maintainability | performance | correctness
**Fichier(s)** :
- `core/database/ApiCacheEntity.kt` + `ApiCacheDao.kt`
- `core/network/ApiCache.kt` (interface)
- `data/cache/RoomApiCache.kt` (impl)
- `data/repository/HubsRepositoryImpl.kt` — seul repo consommateur
- `core/common/util/CacheManager.kt` — cache en mémoire, consommé par `SettingsRepositoryImpl`

**Dépendances** : aucune

**Preuve** :
Grep `apiCache\.get|apiCache\.put|cacheManager` :
```
data/repository/HubsRepositoryImpl.kt:2
data/repository/SettingsRepositoryImpl.kt:3
```
Seuls **2 repos sur 24** consomment le système de cache générique. Les autres utilisent :
- Room entity direct (`MediaDao`, `HomeContentDao`, `SearchCacheEntity`)
- Flow + `.stateIn` SharingStarted
- Pas de cache du tout

**Pourquoi c'est un problème dans PlexHubTV** :
Trois stratégies de cache coexistent sans plan :
1. **Room SSOT** : `LibrarySyncWorker` écrit `MediaEntity`, repositories lisent directement.
2. **ApiCache HTTP blob** : `HubsRepositoryImpl` sérialise le DTO JSON brut en Room, parse en lecture.
3. **CacheManager mémoire** : `SettingsRepositoryImpl` cache des settings peu changeants.

Plus un 4e sous-jacent : `PlexCacheInterceptor` qui fait du cache HTTP OkHttp 5 min.

Conséquences :
- Aucune politique TTL uniforme (HubsRepositoryImpl ignore le TTL de ApiCache, recharge à chaque collect).
- Pas de cache invalidation coordonnée : `RatingSyncWorker` qui change `displayRating` n'invalide pas le cache HTTP des hubs (qui contient un snapshot stale).
- Les développeurs ne savent pas laquelle utiliser pour une nouvelle feature → copier le plus proche.

**Risque concret si non corrigé** :
- Stale data : user ajoute un favori, va dans Home, le rail "On Deck" montre le vieil état (ApiCache hub non invalidé).
- Complexité d'invalidation : CachePurgeWorker purge l'ApiCache, mais les Room caches (MediaEntity, HomeContentEntity, SearchCacheEntity) suivent leurs propres règles.

**Correctif recommandé** :
1. Documenter explicitement dans `ARCHITECTURE.md` la hiérarchie de cache : "Room SSOT est primaire, HTTP cache est secondaire et optionnel, CacheManager mémoire est pour les settings app-level".
2. Supprimer `ApiCache`/`RoomApiCache` s'il n'est utilisé que par 1 repo (HubsRepositoryImpl) → simplifier.
3. Écrire un `CacheInvalidationBus` (SharedFlow) que les repos et workers observent pour invalider.

**Architecture cible** :
```
Cache tiers (documentés):
 Tier 1 — SSOT Room (MediaEntity, HomeContentEntity, SearchCacheEntity, etc.)
          TTL: implicit via LibrarySyncWorker (6h periodic)
          Invalidation: worker replace

 Tier 2 — HTTP OkHttp cache (PlexCacheInterceptor, 5min)
          For: stable endpoints (server list, library sections)

 Tier 3 — In-memory CacheManager (core/common)
          For: immutable settings, lookup maps

 Deprecated: ApiCache (RoomApiCache) — remove or document precise role
```

**Patch proposé** :
- Option A : Créer `docs/ARCHITECTURE-CACHING.md` expliquant la hiérarchie et les règles d'invalidation.
- Option B : Supprimer `ApiCache` si HubsRepositoryImpl peut être migré vers Room SSOT (`HomeContentEntity` existe déjà).

**Validation du fix** :
- Code review checklist : "for every new cache usage, document in ARCHITECTURE.md which tier".
- PR description : "invalidation path" obligatoire pour nouveau cache.

---

### AUDIT-4-014 — `Turbine` référencé dans ARCHITECTURE.md mais absent de `libs.versions.toml`
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : test infrastructure | documentation | onboarding
**Fichier(s)** :
- `docs/ARCHITECTURE.md` §19 (mentionne Turbine)
- `gradle/libs.versions.toml` (aucune entrée `turbine`)
- `domain/src/test/**/*.kt` (les 9 tests de `:domain` n'utilisent pas Turbine — ils utilisent `runTest` + `first()`)

**Dépendances** : aucune

**Preuve** :
```bash
$ grep -r "turbine" gradle/libs.versions.toml
# (no match)
$ grep -r "app.cash.turbine" **/src/test/**/*.kt
# (no match)
```

**Pourquoi c'est un problème dans PlexHubTV** :
1. La doc ARCHITECTURE.md ment sur l'outillage disponible → nouveaux devs ajoutent `import app.cash.turbine.test` et cassent le build.
2. Les tests Flow sans Turbine sont verbeux et fragiles (collect manuels, timeouts, race conditions).
3. L'absence d'une dépendance "testing de premier plan" signale une infra test incomplète.

**Risque concret si non corrigé** :
- Drift doc/code → perte de confiance dans ARCHITECTURE.md.
- Couverture Flow tests limitée : les 9 tests domain testent des flows via `.first()` au lieu de `.test { awaitItem() }`.

**Correctif recommandé** :
Ajouter Turbine dans `libs.versions.toml` et le wire dans les modules avec des tests Flow (`:domain`, `:data`, `:app`).

**Patch proposé** :
```toml
# libs.versions.toml
[versions]
turbine = "1.2.1"
[libraries]
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```
```kotlin
// domain/build.gradle.kts
testImplementation(libs.turbine)
// data/build.gradle.kts
testImplementation(libs.turbine)
// app/build.gradle.kts
testImplementation(libs.turbine)
```
Puis migrer 2-3 tests existants pour l'utiliser (ex : `EnrichMediaItemUseCaseTest`).

**Validation du fix** :
- `./gradlew :domain:test` vert avec un test utilisant `.test { ... }`.
- ARCHITECTURE.md reflète la réalité.

---

### AUDIT-4-015 — 0 tests dans `:core:database`, `:core:datastore`, `:core:designsystem`, `:core:ui`, `:core:navigation`
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : stability | regression safety | release risk
**Fichier(s)** :
- `core/database/src/test/` — **absent**
- `core/datastore/src/test/` — **absent** (crucial : `SecurePreferencesManager` gère les tokens)
- `core/designsystem/src/test/` — absent (OK, theming)
- `core/ui/src/test/` — absent
- `core/navigation/src/test/` — absent

**Dépendances** : AUDIT-4-014 (Turbine)

**Preuve** :
Phase 0 §14.1 confirme "Modules sans test unitaire : `:core:database`, `:core:datastore`, `:core:designsystem`, `:core:ui`, `:core:navigation`."

**Pourquoi c'est un problème dans PlexHubTV** :
- **`:core:database`** : 24 entités, 21 DAOs, 54 `@Query` dans `MediaDao`, 36 migrations. **Aucune migration testée.** Le MEMORY.md documente plusieurs traps (historyGroupKey caused LoadState.Error silently, correlated MAX pitfalls). Ces bugs ne peuvent être prévenus que par tests instrumentés Room.
- **`:core:datastore`** : `SecurePreferencesManager` gère Plex token, Jellyfin tokens, TMDb/OMDb API keys via EncryptedSharedPreferences. Corruption ou migration cassée = logout forcé de tous les users.
- **`:core:ui`** : composants critiques (NetflixMediaCard, NetflixContentRow, OverscanSafeArea, FocusUtils) utilisés partout ; régressions invisibles.
- **`:core:navigation`** : Screen routes sealed class, si une route est cassée → crash.

**Risque concret si non corrigé** :
- Migration Room 47→48 casse silencieusement → data loss à la prochaine release.
- SecurePreferencesManager introduit un bug de timing (synchronized) → crash sporadique en onboarding.
- Focus TV perdu sur une row Netflix → UX TV inutilisable (P0 ressenti user).

**Correctif recommandé** :
Plan de restauration des tests priorisé (cf. section finale). Commencer par `:core:database` (migrations), puis `:core:datastore` (secure prefs), puis `:core:ui` (focus TV).

**Architecture cible** :
```
core/database/
 └── src/androidTest/     MigrationTest, MediaDaoTest, UnificationQueriesTest
 └── src/test/            MediaLibraryQueryBuilderLogicTest (déjà dans :data, à déplacer ?)

core/datastore/
 └── src/test/            SecurePreferencesManagerTest (Robolectric + AndroidKeystore fake)

core/ui/
 └── src/androidTest/     NetflixMediaCardFocusTest, OverscanSafeAreaTest
```

**Patch proposé** :
Créer les répertoires `src/test/` et `src/androidTest/` dans chaque module, ajouter les dépendances test :
```kotlin
// core/database/build.gradle.kts
androidTestImplementation(libs.room.testing)         // new entry in libs.versions.toml
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.test.ext.junit)
androidTestImplementation(libs.junit)
```

**Validation du fix** :
- CI CI checkpoint : chaque module a au moins 1 test.
- Room migration test : chaque migration 46→47, 47→48 exercée.

---

### AUDIT-4-016 — Plugin Ktlint/Detekt non appliqué dans `:core:ui`, `:core:designsystem`, `:core:navigation`
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : code quality | consistency
**Fichier(s)** :
- `core/ui/build.gradle.kts:1-5` (ne déclare ni detekt ni ktlint)
- `core/designsystem/build.gradle.kts:1-5` (idem)
- `core/navigation/build.gradle.kts:1-6` (inclut detekt/ktlint, OK)

**Dépendances** : aucune

**Preuve** :
```kotlin
// core/ui/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // no detekt, no ktlint
}
```

Toutefois, le root `build.gradle.kts` applique via `subprojects { apply(plugin = "org.jlleitschuh.gradle.ktlint") }` — donc Ktlint est auto-appliqué. Mais l'absence d'alias direct masque l'intention et peut causer des warnings.

**Pourquoi c'est un problème dans PlexHubTV** :
- Inconsistance : certains modules déclarent explicitement, d'autres non.
- `core/ui` contient les composants Compose critiques ; pas de check consistence formatage.
- Si un jour `subprojects { }` est retiré, ces modules passent silencieusement sans lint.

**Correctif recommandé** :
Ajouter les alias explicites dans les 3 modules concernés pour cohérence.

**Patch proposé** :
```diff
 plugins {
     alias(libs.plugins.android.library)
     alias(libs.plugins.kotlin.android)
     alias(libs.plugins.kotlin.compose)
+    alias(libs.plugins.detekt)
+    alias(libs.plugins.ktlint)
 }
```

**Validation du fix** :
- `./gradlew :core:ui:ktlintCheck` retourne des issues (pas une "task not found").

---

### AUDIT-4-017 — `Ktlint ignoreFailures = true` : lint non-bloquant en CI
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : code quality | technical debt accumulation
**Fichier(s)** : `build.gradle.kts:30` (`ignoreFailures.set(true)`)

**Dépendances** : aucune

**Preuve** :
```kotlin
configure<KtlintExtension> {
    debug.set(true)
    verbose.set(true)
    android.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(true)   // <-- lint non-bloquant
    enableExperimentalRules.set(false)
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
`ignoreFailures = true` signifie que Ktlint rapporte les violations mais `./gradlew check` passe quand même. En pratique, cela transforme Ktlint en "outil de référence" plutôt qu'en "gate". Les violations s'accumulent silencieusement.

**Risque concret si non corrigé** :
- Style inconsistant entre PRs, code reviews encombrées de remarques de formatage qui auraient dû être automatisées.
- Dette cosmétique croissante, rend un fix futur (turn on `ignoreFailures = false`) impossible sans une passe de reformatage massive.

**Correctif recommandé** :
Phase 1 : lancer `./gradlew ktlintFormat` une fois, commit, puis `ignoreFailures.set(false)`.

**Patch proposé** :
```diff
 configure<KtlintExtension> {
-    ignoreFailures.set(true)
+    ignoreFailures.set(false)
 }
```
Précédé d'un commit dédié `chore: reformat all modules with ktlintFormat`.

**Validation du fix** :
- `./gradlew ktlintCheck` vert sur main.
- Branche avec violation délibérée doit faire échouer `./gradlew check`.

---

### AUDIT-4-018 — Package `com.chakir.plexhubtv.core.di` dans `:core:common` (drift package/module)
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : maintainability | IDE search | consistency
**Fichier(s)** : `core/common/src/main/java/com/chakir/plexhubtv/core/di/CoroutineModule.kt`

**Dépendances** : AUDIT-4-004

**Preuve** :
```kotlin
// File: core/common/src/main/java/com/chakir/plexhubtv/core/di/CoroutineModule.kt
package com.chakir.plexhubtv.core.di   // <-- expected: core.common.di
```

**Pourquoi c'est un problème dans PlexHubTV** :
Même pattern que AUDIT-4-004 : le package ne reflète pas le module. Un développeur qui cherche les modules DI peut grep `core.di` et trouver celui-ci ET celui hypothétique d'autres modules (`core.util` idem). Résultat : ambiguïté.

**Correctif recommandé** :
Renommer le package en `com.chakir.plexhubtv.core.common.di`. Mettre à jour les ~30 imports de `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher`, `@ApplicationScope`.

**Patch proposé** :
- Déplacer le fichier vers `core/common/src/main/java/com/chakir/plexhubtv/core/common/di/CoroutineModule.kt`
- Mettre à jour le package declaration
- Rechercher/remplacer `com.chakir.plexhubtv.core.di` → `com.chakir.plexhubtv.core.common.di` dans tous les imports.

**Validation du fix** :
- Konsist : aucune classe dans `:core:common` ne doit déclarer un package ne commençant pas par `com.chakir.plexhubtv.core.common`.

---

### AUDIT-4-019 — 25 repos tous `@Singleton` sans analyse fine des scopes
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Moyenne

**Impact** : memory | testability | team velocity
**Fichier(s)** : `data/src/main/java/com/chakir/plexhubtv/data/di/RepositoryModule.kt:30-196`

**Dépendances** : aucune

**Preuve** :
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindAuthRepository(...): AuthRepository
    @Binds @Singleton
    abstract fun bindSettingsRepository(...): SettingsRepository
    // ... 25 @Singleton bindings
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Tous les repositories sont annotés `@Singleton` par défaut. En soi, c'est le pattern Android le plus courant, mais le projet ne distingue pas les repos stateful (nécessitent singleton : caches, flows hot) des stateless (peuvent être ViewModelScoped ou ActivityRetainedScoped).

Exemples :
- `SettingsRepositoryImpl` : 100 % pass-through DataStore. Pas besoin de Singleton strict.
- `XtreamAccountRepositoryImpl`, `JellyfinServerRepositoryImpl` : probablement hot flows → Singleton OK.
- `TrackPreferenceRepositoryImpl` : dépend de l'usage, à vérifier.

**Risque concret si non corrigé** :
- Faible : Hilt Singleton est "lazy", coût mémoire minime.
- Mais : empêche le `WorkerComponent` d'injecter des repos différemment, empêche aussi le test ViewModel d'utiliser un binding in-memory replaceable via `@TestInstallIn`.

**Correctif recommandé** :
Audit ponctuel (pas urgent) : pour chaque repo, se demander "contient-il du state (cache, flow hot, connection pool) ?". Si oui → `@Singleton`. Sinon → envisager de retirer `@Singleton` du `@Binds` (Hilt utilisera `@Reusable`).

**Patch proposé** : passe manuelle, aucune action immédiate requise, à inclure dans une PR de cleanup DI.

**Validation du fix** :
- Code review pattern : tout ajout de `@Singleton` sur repo nécessite justification dans KDoc.

---

### AUDIT-4-020 — `tests/unit` : 38 tests, 0 dans :core:database et :core:datastore, stratégie de restauration absente
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : release confidence | regression risk
**Fichier(s)** :
- 38 tests répartis : 18 `:app`, 4 `:data`, 9 `:domain`, 4 `:core:network`, 2 `:core:model`, 1 `:core:common`.
- `docs/MISSING_TESTS.md` — obsolète selon Phase 0.

**Dépendances** : AUDIT-4-014, AUDIT-4-015

**Preuve** : Phase 0 §14.

**Pourquoi c'est un problème dans PlexHubTV** :
Le projet a 496 fichiers Kotlin (hors build/) → ~7.6 % taux de fichiers testés. Les trous critiques :
- Migrations Room (MEMORY.md documente plusieurs traps fatals).
- SecurePreferencesManager (tokens !).
- WorkManager workers (0 instrumented test).
- Repositories : seulement 1 sur 24 testé (ProfileRepositoryImpl).
- Paging : MediaRemoteMediator pas testé.
- Player controllers : quelques tests mais pas de coverage e2e.

**Risque concret si non corrigé** :
- Chaque release = loterie. Phase 0 memo des pitfalls confirme que des régressions silencieuses ont déjà eu lieu (`historyGroupKey` LoadState.Error).
- Refactors (migration `:domain` en JVM, fix AUDIT-4-002) impossibles sans tests de non-régression.

**Correctif recommandé** : voir section "Test restoration plan".

---

### AUDIT-4-021 — `libs.versions.toml` : AGP 9.0.1 + Kotlin 2.2.10 + Compose BOM 2026.01.00 (bleeding edge)
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Moyenne

**Impact** : stability | build tooling
**Fichier(s)** : `gradle/libs.versions.toml:2-13`

**Dépendances** : aucune

**Preuve** :
```toml
agp = "9.0.1"
kotlin = "2.2.10"
ksp = "2.3.2"
composeBom = "2026.01.00"
```

**Pourquoi c'est un problème dans PlexHubTV** :
- **AGP 9.0.1** : version majeure récente, connue pour breaking changes DSL (notamment sur `ndk.abiFilters`, `packaging`, `signingConfig`).
- **Kotlin 2.2.10** vs **KSP 2.3.2** : mismatch de version (KSP devrait suivre Kotlin de près — 2.2.x.y normalement). Potentiellement KSP 2.3.2 est associé à Kotlin 2.3.x (à vérifier).
- **Compose BOM 2026.01.00** pinné avec Compose foundation 1.10.2 et material3 1.3.1 → divergence (BOM 2026.x sort des versions 1.8+).
- **TV Foundation alpha12** et **TV Material alpha01** → alpha libraries.

**Risque concret si non corrigé** :
- Builds flaky (KSP mismatch), erreurs `ClassNotFoundException` au runtime.
- Upgrade Android Studio / Intellij cassé.
- Pas de "LTS" → chaque `./gradlew` peut dl une nouvelle version alpha.

**Correctif recommandé** :
Gel des versions sur une combinaison stable vérifiée :
- AGP 8.7.x (LTS 2025) ou AGP 9.0.x avec vérification explicite des plugins Firebase compatibles.
- Kotlin 2.1.x avec KSP 2.1.x.y (aligné).
- Compose BOM 2024.12.01 + material3 1.3.1 (déjà pinné).
- TV libraries : rester en alpha mais documenter la tolérance.

**Patch proposé** :
```toml
[versions]
agp = "8.7.3"            # LTS 2025
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
composeBom = "2024.12.01"
```

**Validation du fix** :
- `./gradlew clean build` vert en ~3-5 min.
- `./gradlew :app:assembleRelease` + R8 sans warnings nouveaux.

---

### AUDIT-4-022 — Commentaire build.gradle.kts contradictoire ("x86 excluded" vs `abiFilters +=`)
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Élevée

**Impact** : build | documentation | APK size
**Fichier(s)** : `app/build.gradle.kts:30-31,131`

**Dépendances** : aucune

**Preuve** :
```kotlin
ndk {
    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
}
// ...
// No ABI splits — single universal APK (ARM only, x86 excluded via ndk.abiFilters)
```
Le commentaire dit "x86 excluded" alors que la ligne 31 inclut bien `x86` et `x86_64`.

**Pourquoi c'est un problème dans PlexHubTV** :
- Documentation inline mensongère.
- Next dev fait confiance au commentaire et tente de retirer x86 via `abiSplits` → erreur.
- APK release contient du code natif pour 4 ABIs alors que Android TV cible est ARM seul (99% des Chromecast/Shield/Fire TV).

**Correctif recommandé** :
Décider : soit les 4 ABIs sont nécessaires (fire TV x86 ?), soit on retire x86/x86_64 pour réduire la taille APK de ~30%.

**Patch proposé (option conservatrice)** :
```diff
 ndk {
-    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
+    // Android TV : x86 marginal (certains émulateurs + Chromebook TV apps).
+    // Garder pour compat large, ou retirer pour gain APK ~30% si stats Play < 1% x86.
+    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
 }
// ...
-// No ABI splits — single universal APK (ARM only, x86 excluded via ndk.abiFilters)
+// Single universal APK — all 4 ABIs bundled, ARM primary target.
```

**Validation du fix** : commentaire aligné avec code.

---

### AUDIT-4-023 — Repositories lourds (`LibraryRepositoryImpl` 12 deps, `HubsRepositoryImpl` 12 deps, `EnrichMediaItemUseCase` 9 deps)
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Moyenne

**Impact** : maintainability | testability | Single Responsibility
**Fichier(s)** :
- `data/repository/LibraryRepositoryImpl.kt` — 12 dépendances injectées
- `data/repository/HubsRepositoryImpl.kt` — 12 dépendances injectées
- `domain/usecase/EnrichMediaItemUseCase.kt` — 9 dépendances injectées (642 LOC)

**Dépendances** : aucune

**Preuve** :
```kotlin
class LibraryRepositoryImpl @Inject constructor(
    private val serverClientResolver: ServerClientResolver,
    private val authRepository: AuthRepository,
    private val connectionManager: ConnectionManager,
    private val api: PlexApiService,
    private val mapper: MediaMapper,
    private val mediaDao: MediaDao,
    private val mediaUnifiedDao: MediaUnifiedDao,
    private val database: PlexDatabase,
    private val settingsRepository: SettingsRepository,
    private val serverNameResolver: ServerNameResolver,
    private val mediaUrlResolver: MediaUrlResolver,
    private val jellyfinClientResolver: JellyfinClientResolver,
) : LibraryRepository
```

**Pourquoi c'est un problème dans PlexHubTV** :
Signe clair que la classe fait trop : résolution de serveurs Plex ET Jellyfin, paging, conversion DTO, lecture/écriture Room, coordination avec `SettingsRepository`. Au-delà de 5-6 dépendances, une classe devient difficile à comprendre, tester, et modifier.

`EnrichMediaItemUseCase` (642 LOC) est un monstre : single operation complexity + 9 deps. Il mélange Room-first, network fallback, tree traversal, show caching, server name resolution, in-flight dedup, performance tracking.

**Risque concret si non corrigé** :
- Bugs de fusion Plex/Jellyfin à cause du couplage dans LibraryRepositoryImpl.
- Impossible de tester EnrichMediaItemUseCase exhaustivement (trop de branchements). Les tests existants (`EnrichMediaItemUseCaseTest`) couvrent seulement ~20% du code (grep "fun \`" dans le test).
- Refactor = réécriture.

**Correctif recommandé** :
**LibraryRepositoryImpl** : extraire les parties Jellyfin-spécifiques dans un `JellyfinLibraryRepository` séparé ; extraire le paging dans un `LibraryPagingSourceFactory`.

**EnrichMediaItemUseCase** : extraire :
- `ServerNameResolver` (déjà existe, mais duplication dans use case)
- `EpisodeTreeTraverser` (logique de traversal séparée)
- `EnrichmentCache` (le LinkedHashMap + in-flight dedup devient sa propre classe testable)
- `EnrichmentNetworkFallback` (le ~200 LOC de network fallback)

**Architecture cible (EnrichMediaItemUseCase)** :
```
domain/usecase/EnrichMediaItemUseCase
 ├── EnrichmentCache (stateful, @Singleton)
 ├── EpisodeTreeTraverser (stateless, or @Reusable)
 ├── EnrichmentServerMap (provides serverId→name)
 └── EnrichmentNetworkFallback (network search orchestration)
```

**Patch proposé** : refactor scope > 500 LOC, à planifier en milestone dédié. Conserver les tests existants comme non-regression safety net.

**Validation du fix** :
- Chaque sous-classe extraite : ≤ 5 dépendances et ≤ 150 LOC.
- Tests séparés par sous-classe.
- Test d'intégration E2E (`EnrichMediaItemUseCaseIntegrationTest`) qui vérifie le happy path après refacto.

---

### AUDIT-4-024 — Absence de Konsist / ArchUnit : règles d'architecture non enforced
**Phase** : 4 Architecture — **Sévérité** : **P1** — **Confiance** : Élevée

**Impact** : long-term maintainability | Clean Archi drift
**Fichier(s)** : aucun konsist rule / test

**Dépendances** : plusieurs (AUDIT-4-002, 003, 004, 008, 010, 018)

**Preuve** :
- Aucune dépendance `konsist` dans `libs.versions.toml`.
- Aucun fichier `**/*ArchTest.kt` ou `**/konsist/*.kt` dans le repo.

**Pourquoi c'est un problème dans PlexHubTV** :
Les 11 findings ci-dessus concernent tous des règles architecturales qui auraient dû être détectées automatiquement : "domain ne doit pas importer androidx", "use cases ne doivent pas accéder aux DAOs directement", "un package doit correspondre à un module", "VMs doivent étendre BaseViewModel", "core.model ne doit pas dépendre de compose". Sans automatisation, chaque review humaine est la seule gate, et les violations s'accumulent.

**Correctif recommandé** :
Ajouter `konsist` (Kotlin-friendly, cousin d'ArchUnit) avec un set de règles explicites :

```kotlin
// :app/src/test/java/.../ArchitectureTest.kt
class ArchitectureTest {
    @Test fun `domain does not depend on android framework`() =
        Konsist.scopeFromModule("domain")
            .classes()
            .assertTrue { c -> c.containingFile.imports.none { it.name.startsWith("android.") || it.name.startsWith("androidx.") || it.name.startsWith("dagger.hilt") } }

    @Test fun `use cases must be in domain module`() =
        Konsist.scopeFromProduction()
            .classes()
            .withNameEndingWith("UseCase")
            .assertTrue { it.residesInModule("domain") }

    @Test fun `data-layer repositories must implement domain interface`() =
        Konsist.scopeFromModule("data")
            .classes()
            .withNameEndingWith("RepositoryImpl")
            .assertTrue { c -> c.hasParentWithName(c.name.removeSuffix("Impl")) }

    @Test fun `feature viewmodels must extend BaseViewModel`() =
        Konsist.scopeFromModule("app")
            .classes()
            .withNameEndingWith("ViewModel")
            .filter { it.residesInPackage("com.chakir.plexhubtv.feature..") }
            .filter { it.name !in allowList }
            .assertTrue { it.hasParentWithName("BaseViewModel") }

    @Test fun `core-model must not depend on compose`() =
        Konsist.scopeFromModule("core:model")
            .classes()
            .assertTrue { c -> c.containingFile.imports.none { it.name.startsWith("androidx.compose") } }
}
```

**Patch proposé** :
```toml
[versions]
konsist = "0.17.3"
[libraries]
konsist = { group = "com.lemonappdev", name = "konsist", version.ref = "konsist" }
```
```kotlin
// :app/build.gradle.kts
testImplementation(libs.konsist)
```

**Validation du fix** :
- `./gradlew :app:test` rouge tant que les violations AUDIT-4-002/003/004/006/008/010/018 ne sont pas fixes.
- CI gate : konsist test doit être vert pour merger.

---

### AUDIT-4-025 — Gradle config-cache non testé ; tasks custom absents
**Phase** : 4 Architecture — **Sévérité** : **P2** — **Confiance** : Faible

**Impact** : build performance | developer productivity
**Fichier(s)** : `gradle.properties`, `settings.gradle.kts`, root `build.gradle.kts`

**Dépendances** : AUDIT-4-001, AUDIT-4-021

**Preuve** :
- Aucun `gradle.properties` setting visible pour `org.gradle.configuration-cache=true`.
- Aucun `buildSrc/` ou `build-logic/` convention plugin.
- Les 11 modules dupliquent les mêmes `plugins { }` blocks (ktlint, detekt, kotlin.android).

**Pourquoi c'est un problème dans PlexHubTV** :
Avec 11 modules + Hilt KSP + Room KSP, `./gradlew assembleDebug` est probablement > 60s. Config cache aurait gagné ~20-30%. Convention plugins (`build-logic/`) éviteraient la duplication de ~50 lignes par module.

**Correctif recommandé** :
Phase 1 : tester `--configuration-cache` sur une build propre, identifier et fix les incompatibilités (probablement le nested `android { }` de AUDIT-4-001 + les `FileInputStream` lus à la configuration).

Phase 2 : créer `build-logic/convention/` avec des `AndroidLibraryConventionPlugin`, `AndroidApplicationConventionPlugin`, etc.

**Patch proposé** :
```properties
# gradle.properties
org.gradle.configuration-cache=true
org.gradle.parallel=true
org.gradle.caching=true
```

**Validation du fix** :
- `./gradlew --configuration-cache assembleDebug` ok.
- Second run `./gradlew --configuration-cache assembleDebug` < 20s.

---

## Test restoration plan (plan de restauration ordonné)

Le projet a **38 tests unitaires + 4 tests instrumentés sur ~496 fichiers Kotlin** (~7.6% coverage by file count). Les zones critiques vides sont documentées en AUDIT-4-015 et AUDIT-4-020. Voici l'ordre de restauration basé sur le **risque × impact × effort**.

### Phase 1 — **Barrière contre régressions DB** (priorité critique)
**Module visé** : `:core:database`
**Tests à créer** :
1. `MigrationTest.kt` (androidTest) — teste les migrations 11→47 avec données synthétiques. Critique car MEMORY.md documente 3+ traps fatals (`historyGroupKey`, correlated MAX, page offset unique index).
2. `MediaDaoUnificationTest.kt` (androidTest) — exerce les `@RawQuery` de `MediaLibraryQueryBuilder` avec dataset multi-serveur pour non-régresser les AUDIT-ROOM traps.
3. `MediaUnifiedDaoTest.kt` (androidTest).
4. `FavoriteDaoTest.kt`, `HomeContentDaoTest.kt`, `SearchCacheDaoTest.kt` (androidTest).

**Rationale** : Les Room migrations cassées = data loss users. Sans MigrationTest, chaque release = russian roulette. Le quartier DB est le plus fragile (cf. MEMORY.md) et le moins testé.

**Effort** : 3-5 jours dev (infra Room testing existe, il suffit de créer les fixtures).

### Phase 2 — **Sécurité des tokens** (priorité critique)
**Module visé** : `:core:datastore`
**Tests à créer** :
1. `SecurePreferencesManagerTest.kt` (test ou androidTest avec Robolectric) — vérifie :
   - Round-trip set/get pour plexToken, jellyfinTokens, tmdbApiKey, omdbApiKey.
   - Migration from DataStore → EncryptedSharedPreferences (first run).
   - Synchronisation thread-safe (concurrent writes).
   - Fallback en cas de keystore corruption (exception → clear).
2. `SettingsDataStoreTest.kt` — round-trip pour chaque clé, default values, flow emissions.

**Rationale** : Si SecurePreferencesManager casse silencieusement, tous les users sont logout. Il n'a aucun test. Critique de sécurité et UX.

**Effort** : 2-3 jours (Robolectric + MockKeystoreHelper).

### Phase 3 — **Repositories cruciaux** (haute priorité)
**Module visé** : `:data`
**Tests à créer** (ordonnés par impact) :
1. `MediaDetailRepositoryImplTest.kt` — touche tous les flows enrichment + playback.
2. `PlaybackRepositoryImplTest.kt` — watchHistory, markAsWatched, progress.
3. `AuthRepositoryImplTest.kt` — getServers, switchUser, 401 handling.
4. `HubsRepositoryImplTest.kt` — cache invalidation, aggregation multi-source.
5. `FavoritesRepositoryImplTest.kt` — toggle, isFavorite flow.
6. `LibraryRepositoryImplTest.kt` — paging integration.

**Rationale** : 1 / 24 repositories testé. Chaque repo représente une vertical de l'app. Les repos ci-dessus touchent directement l'UX perçu.

**Effort** : 7-10 jours, ~1 jour par repo.

### Phase 4 — **Workers** (priorité moyenne)
**Module visé** : `:app/work/`
**Tests à créer** :
1. `LibrarySyncWorkerTest.kt` (androidTest avec `WorkManagerTestInitHelper`) — teste Plex + Jellyfin sync paths, error handling, retry.
2. `UnifiedRebuildWorkerTest.kt` — rebuild de `media_unified`.
3. `RatingSyncWorkerTest.kt` — TMDb/OMDb fetch + displayRating update.
4. `CollectionSyncWorkerTest.kt`.
5. `CachePurgeWorkerTest.kt`.
6. `ChannelSyncWorkerTest.kt`.

**Rationale** : Les workers s'exécutent en background, les bugs sont invisibles. 0 test aujourd'hui. Une migration Jellyfin ratée (pageOffset unique index, cf. MEMORY.md) peut wiper la DB.

**Effort** : 5-7 jours.

### Phase 5 — **Core UI composants critiques** (priorité moyenne)
**Module visé** : `:core:ui`
**Tests à créer** (instrumented Compose) :
1. `NetflixMediaCardFocusTest.kt` — focus reporting, elevation on focus, D-Pad navigation.
2. `NetflixContentRowTest.kt` — horizontal scroll, focus restore.
3. `OverscanSafeAreaTest.kt` — insets TV overscan.
4. `FocusUtilsTest.kt` — saveFocus / restoreFocus logic.

**Rationale** : Focus TV cassé = app inutilisable sur D-Pad. Zero test UI pour ces composants critiques.

**Effort** : 3-4 jours.

### Phase 6 — **Use cases riches** (priorité moyenne)
**Module visé** : `:domain`
**Tests à enrichir** :
1. `EnrichMediaItemUseCaseTest.kt` — étendre de ~20% à ~80% coverage (Room-first path, network fallback, in-flight dedup, cache expiration, tree traversal).
2. `PreparePlaybackUseCaseTest.kt` — pipeline complet.
3. `GetUnifiedHomeContentUseCaseTest.kt` — agrégation multi-source.

**Rationale** : Tests existent mais trop légers. Prérequis : AUDIT-4-014 (Turbine) pour tester les Flows propreement.

**Effort** : 3-5 jours.

### Phase 7 — **Stabiliser `:core:common`, `:core:model`** (priorité basse)
1. `PerformanceTrackerTest.kt`.
2. `CacheManagerTest.kt`.
3. `ContentRatingHelperTest.kt`.
4. `MediaItemTest.kt` (UnificationId calculation edge cases).

**Effort** : 2 jours.

### Total estimé
- **Phase 1 (DB)** : 3-5 j — bloquant toute release sans risque data loss.
- **Phase 2 (Secure prefs)** : 2-3 j — bloquant toute release touchant l'auth.
- **Phase 3 (Repos)** : 7-10 j — rampe progressive.
- **Phase 4 (Workers)** : 5-7 j.
- **Phase 5 (UI)** : 3-4 j.
- **Phase 6 (Use cases)** : 3-5 j.
- **Phase 7 (Common)** : 2 j.
- **Total** : **~25-36 jours-dev** pour passer de 7.6% à ~30-35% coverage by file.

**Prérequis transverse** :
- Ajouter **Turbine** à `libs.versions.toml` (AUDIT-4-014) — **Jour 0**.
- Ajouter **Room testing** + **WorkManager testing** libs.
- Ajouter **Konsist** (AUDIT-4-024) — active **avant** la fin de Phase 3 pour enforcer les règles archi.

---

**Fin du livrable Phase 4 — Architecture.**
