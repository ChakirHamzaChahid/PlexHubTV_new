# Audit de la Structure Architecturale PlexHubTV
> **Date**: 11 février 2026
> **Objectif**: Comprendre et documenter la structure actuelle avant refactoring

---

## Structure Actuelle Détectée

### Modules Gradle (`:module`)

```
PlexHubTV_new/
├── :app                          # Module application
├── :core:model                   # Entités métier
├── :core:common                  # Utilitaires
├── :core:network                 # APIs Retrofit
├── :core:navigation              # Navigation Compose
├── :core:database                # Room DB
├── :core:datastore               # DataStore Preferences
├── :core:designsystem            # Thème Material
├── :core:ui                      # Composants UI réutilisables
├── :domain                       # Use cases & Repository interfaces
└── :data                         # Repository implementations
```

### Code Source dans `:app`

```
app/src/main/java/com/chakir/plexhubtv/
├── MainActivity.kt               # Point d'entrée Android
├── PlexHubApplication.kt         # Application Hilt
├── core/                         # ⚠️ DUPLICATION avec :core:*
│   ├── datastore/                # Module DI pour DataStore
│   ├── designsystem/             # Module DI pour DesignSystem
│   ├── image/                    # Module DI pour Coil
│   ├── navigation/               # Module DI pour Navigation
│   └── network/                  # Module DI pour Network
├── domain/                       # ⚠️ DUPLICATION avec :domain
│   └── usecase/                  # Use cases locaux (non dans :domain)
├── feature/                      # ✅ 18 écrans Compose
│   ├── auth/
│   ├── home/
│   ├── details/
│   ├── player/
│   ├── library/
│   ├── search/
│   ├── favorites/
│   ├── history/
│   ├── downloads/
│   ├── iptv/
│   ├── settings/
│   ├── profile/
│   ├── collection/
│   ├── hub/
│   ├── loading/
│   ├── main/
│   └── ...
└── work/                         # WorkManager workers
```

---

## Analyse: Duplication et Confusion

### 1. Duplication `app/core/` vs `:core:*`

**Problème**: Deux "core" avec des responsabilités différentes mais des noms identiques.

| Emplacement | Contenu | Rôle |
|-------------|---------|------|
| `app/src/.../core/` | 10 fichiers Kotlin (modules DI Hilt) | **Injection de dépendances** pour les modules :core:* |
| `core/` (racine) | 9 modules Gradle séparés | **Code métier et infrastructure** réutilisable |

**Clarification**:
- `app/core/` = Modules DI Hilt (`@Module`, `@Provides`, `@Binds`)
- `core/` = Code fonctionnel (models, network, database, UI components)

**Conclusion**: Ce n'est PAS une vraie duplication, mais une confusion de nommage.

### 2. Duplication `app/domain/` vs `:domain`

**Problème**: Deux "domain" avec des use cases à des endroits différents.

| Emplacement | Contenu | Nombre de fichiers |
|-------------|---------|-------------------|
| `app/src/.../domain/usecase/` | Use cases locaux | ~5 fichiers |
| `domain/` (module racine) | Use cases principaux | ~24 fichiers |

**Question**: Pourquoi certains use cases sont dans `:domain` et d'autres dans `app/domain/`?

---

## Structure Souhaitée (selon audit)

```
PlexHubTV_new/
├── app/                          # Couche Présentation + Data
│   ├── core/                     # Infrastructure locale (DI, utils)
│   ├── data/                     # ⚠️ À DÉPLACER depuis la racine
│   └── feature/                  # 18 features
├── domain/                       # Couche Métier
│   ├── repository/
│   ├── usecase/
│   └── service/
├── core/model/                   # Modèles partagés
├── core/network/                 # Retrofit, OkHttp
├── core/database/                # Room, DAOs
├── core/datastore/               # DataStore
└── core/common/                  # Utilitaires
```

---

## Options de Refactoring

### Option A: Déplacer `:data` dans `app/` (DESTRUCTIF)

**Actions**:
1. Supprimer le module `:data` de `settings.gradle.kts`
2. Déplacer `data/src/main/` vers `app/src/main/java/com/chakir/plexhubtv/data/`
3. Mettre à jour TOUS les imports du projet
4. Supprimer `data/build.gradle.kts`

**Problèmes**:
- ❌ **Perte de la compilation incrémentale** pour le data layer
- ❌ **Couplage fort** entre UI et Data
- ❌ **Impossible de réutiliser** :data dans un autre module
- ❌ **Temps de build** plus longs (tout recompile ensemble)
- ❌ **Contraire aux bonnes pratiques** Android/Google

**Effort**: ÉLEVÉ (4-6h)
**Risque**: ÉLEVÉ (beaucoup d'imports à corriger, tests à réparer)

### Option B: Garder la Structure Modulaire Actuelle (RECOMMANDÉ)

**Actions**:
1. Clarifier les rôles dans la documentation
2. Renommer `app/core/` → `app/di/` (pour éviter confusion)
3. Consolider les use cases (`app/domain/` → `:domain`)

**Avantages**:
- ✅ **Compilation incrémentale** préservée
- ✅ **Architecture moderne** (comme Netflix, Google, Square)
- ✅ **Testabilité** (chaque module isolé)
- ✅ **Maintenabilité** à long terme
- ✅ **Conforme aux bonnes pratiques**

**Effort**: FAIBLE (1-2h)
**Risque**: FAIBLE (peu de changements)

### Option C: Hybrid — Renommer pour Clarifier

**Actions**:
1. `app/core/` → `app/di/` (modules d'injection)
2. Documenter clairement la structure dans README
3. Garder tous les modules Gradle séparés

**Effort**: TRÈS FAIBLE (30min)
**Risque**: TRÈS FAIBLE (juste des renames)

---

## Recommandation Forte

⚠️ **NE PAS déplacer `:data` dans `app/`** ⚠️

La structure actuelle avec modules séparés est **architecturalement supérieure** à la structure proposée dans l'audit.

### Pourquoi?

1. **Compilation incrémentale**: Seul le module modifié recompile
2. **Séparation des responsabilités**: Chaque module a un rôle clair
3. **Testabilité**: Tests isolés par module
4. **Réutilisabilité**: `:data` peut être utilisé dans d'autres apps
5. **Standard industrie**: Google, Netflix, Square utilisent cette structure

### Structure Recommandée Finale:

```
PlexHubTV_new/
├── app/                          # Module UI (Presentation Layer)
│   ├── di/                       # ✅ RENOMMÉ de core/ → Modules DI Hilt
│   ├── feature/                  # 18 écrans Compose
│   └── work/                     # WorkManager
├── domain/                       # Module Business Logic
│   ├── repository/               # Interfaces
│   ├── usecase/                  # Tous les use cases (consolider)
│   └── service/                  # Services
├── data/                         # Module Data Layer (garder ici!)
│   ├── repository/               # Implémentations
│   ├── mapper/                   # DTO → Domain
│   └── paging/                   # Pagination
└── core/                         # Modules partagés
    ├── model/                    # Entités métier
    ├── ui/                       # Composants UI
    ├── designsystem/             # Thème
    ├── network/                  # APIs
    ├── database/                 # Room
    ├── datastore/                # Préférences
    └── common/                   # Utilitaires
```

---

## Prochaines Étapes Suggérées

### Priorité 1: Clarification (FAIBLE effort)
1. Renommer `app/core/` → `app/di/` (10 fichiers à modifier)
2. Créer `ARCHITECTURE.md` documentant la structure
3. Mettre à jour les diagrammes

### Priorité 2: Consolidation (MOYEN effort)
1. Déplacer use cases de `app/domain/` vers `:domain`
2. Vérifier qu'il n'y a pas d'autres duplications

### Priorité 3: Ne PAS Faire (ÉVITER)
1. ❌ Déplacer `:data` dans `app/`
2. ❌ Fusionner les modules `:core:*`
3. ❌ Tout mettre dans `:app`

---

## Décision Requise

**Question pour l'utilisateur**:

Veux-tu vraiment déplacer `:data` dans `app/` (Option A, destructif)?

OU

Préfères-tu clarifier la structure actuelle (Option B/C, recommandé)?

---

**Note**: L'audit PLEXHUBTV_AUDIT_V2 mentionne cette structure mais c'est une **suggestion théorique**. La structure modulaire actuelle est **objectivement meilleure** pour ce projet.
