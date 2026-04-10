# PlexHubTV — Audit Production-Ready v3.0
> Prompt optimisé pour Claude Code · Architecture Parent / Sous-agents · Format ticketable

---

## Vue d'ensemble

```xml
<contexte>
  <projet>PlexHubTV — client Plex pour Android TV, UI Netflix-like</projet>
  <cible>Production-ready + vendable sur Google Play Store</cible>
  <stack>Kotlin · Jetpack Compose · Clean Architecture · Hilt · ExoPlayer/MPV · Room · Coil</stack>
  <hardware-cible>Xiaomi Mi Box S (2 Go RAM, GPU Mali-450) — device de référence</hardware-cible>
  <persona-auditeur>Expert Android TV senior, spécialisé Kotlin, Jetpack Compose, Clean Architecture, UX TV</persona-auditeur>
</contexte>
```

---

## Architecture des agents

Ce prompt est conçu pour être exécuté en **équipe d'agents spécialisés** (Option B recommandée).  
Chaque agent est autonome sur son périmètre, mais **tous attendent le rapport de Phase 0** avant de démarrer.

```xml
<agents>
  <agent id="0" nom="Cartography Agent"   phases="0"       prerequis="aucun — premier à s'exécuter" />
  <agent id="1" nom="Stability Agent"     phases="1+2"     prerequis="rapport Phase 0" />
  <agent id="2" nom="Performance Agent"   phases="3"       prerequis="rapport Phase 0" />
  <agent id="3" nom="Architecture Agent"  phases="4"       prerequis="rapport Phase 0" />
  <agent id="4" nom="UX Agent"            phases="5+6"     prerequis="rapport Phase 0" />
  <agent id="5" nom="Release Agent"       phases="7+8+A-D" prerequis="rapports agents 1-4" />
</agents>
```

> **Règle inter-agents :** tout finding avec confiance "Faible" doit être validé par un second agent avant inclusion au rapport final.

---

## FORMAT OBLIGATOIRE — Finding

```xml
<contraintes>
  <regle>Chaque problème trouvé, dans TOUTES les phases, utilise impérativement ce format.</regle>
  <regle>Aucun finding sans preuve de code (extrait, config, ou absence constatée explicite).</regle>
  <regle>Jamais de recommandation de refactor sans définir l'architecture cible.</regle>
</contraintes>
```

```
ID          : AUDIT-[PHASE]-[NNN]          ex: AUDIT-1-003
Titre       : [titre court et précis]
Phase       : [numéro et nom]
Sévérité    : P0 (bloquant vente) | P1 (important) | P2 (amélioration)
Confiance   : Élevée | Moyenne | Faible

Impact      : utilisateur | sécurité | stabilité | publication store | business
Fichier(s)  : [chemin exact, lignes si possible]

Dépendances : [AUDIT-X-NNN, AUDIT-Y-NNN] ou "aucune"
              → findings liés ou prérequis pour la correction

Preuve      :
  [extrait de code incriminé, ou configuration, ou absence constatée]

Pourquoi c'est un problème dans PlexHubTV :
  [explication contextuelle — jamais générique]

Risque concret si non corrigé :
  [scénario d'impact réel]

Correctif recommandé :
  [description précise de la solution]

Patch proposé :
  [code Kotlin / Gradle / XML minimal, ou pseudo-diff]

Étapes de reproduction (si applicable) :
  [préconditions → actions → résultat attendu vs actuel]

Validation du fix :
  [test unitaire | test intégration | test UI | mesure métrique | vérification manuelle]
```

---

## PHASE 0 — Cartographie du projet *(Cartography Agent)*

```xml
<instructions>
  <priorite>Lire en premier : ARCHITECTURE.md, MISSING_TESTS.md, build.gradle.kts (racine), gradle/libs.versions.toml</priorite>
  <regle>Ne formuler AUCUN jugement définitif avant la fin de cette phase.</regle>
  <regle>Si un comportement ne peut pas être vérifié sans exécution runtime → "→ à valider sur device"</regle>
  <regle>Si une hypothèse ne peut pas être prouvée dans le code → "→ non vérifié"</regle>
  <regle>Si un fichier est trop long pour être analysé en détail → "→ échantillonné"</regle>
</instructions>
```

**Cartographier et documenter :**

- Arborescence réelle des modules (`:app`, `:data`, `:domain`, `:presentation`, etc.)
- Points d'entrée (Activity, Service, BroadcastReceiver, WorkManager)
- Liste exhaustive des écrans / composables principaux
- Liste des ViewModels, repositories, use cases, sources de données
- Dépendances critiques (ExoPlayer/MPV, Plex API, Room, Hilt, Coil, etc.)
- Pipeline de build (`build.gradle.kts`, flavors, signing config, règles ProGuard)
- Fichiers de configuration sensibles (DataStore, secrets, API keys)
- Zones non auditées en profondeur (fichiers générés, ressources, assets)

**Livrable :** carte complète du projet, partagée avec tous les agents avant leur démarrage.

---

## PHASE 1 — Stabilité & Crash-Proofing *(Stability Agent)*

```xml
<instructions>
  <livrable>Liste complète des crash vectors, format FINDING, classés P0 → P1 → P2</livrable>
</instructions>
```

| # | Axe d'audit | Points de contrôle |
|---|---|---|
| 1 | **Null safety & edge cases** | `!!` non justifiés, casts non sécurisés, accès index sans vérification |
| 2 | **Lifecycle leaks** | Coroutines survivant au ViewModel/Activity, listeners non nettoyés |
| 3 | **Race conditions** | Mutations concurrentes sur StateFlow, accès SharedPreferences/Room hors thread |
| 4 | **Process death** | `SavedStateHandle` correctement utilisé ? Survie après process kill ? |
| 5 | **Memory leaks** | Singletons retenant un `Context`, bitmaps non recyclés, listeners non déregistrés |
| 6 | **Error handling** | `try/catch` manquants, Flow sans `.catch`, Channel non consommés |
| 7 | **Player robustness** | ExoPlayer/MPV : erreurs codec, timeout réseau, perte de focus audio |
| 8 | **ANR (Application Not Responding)** | Opérations bloquantes sur main thread : appels réseau synchrones, queries Room sans `suspend`, I/O fichier, `SharedPreferences.commit()` au lieu de `apply()` |
| 9 | **Deep link / Intent malformé** | Intent externe ou notification avec extras null/invalides, navigation vers écran avec arguments manquants |
| 10 | **Configuration changes** | Rotation (rare TV mais possible), changement de densité, mode picture-in-picture si supporté, changement de langue système |
| 11 | **Navigation backstack** | Crash sur double-tap Back rapide, état navigation incohérent après process death, backstack corrompu après deep link |
| 12 | **OOM sur device limité** | Comportement sous pression mémoire (Mi Box S = 2 Go RAM), `onTrimMemory` correctement géré ? Réaction au `ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW` ? |

---

## PHASE 2 — Sécurité & Protection des données *(Stability Agent)*

```xml
<instructions>
  <referentiel>OWASP Mobile Top 10 (M1–M10) — indiquer la catégorie OWASP pour chaque finding</referentiel>
  <regle>Si non vérifiable en statique → indiquer explicitement "→ non vérifiable statiquement"</regle>
  <livrable>Rapport de sécurité avec niveaux de risque et catégories OWASP</livrable>
</instructions>
```

| # | Axe d'audit | Points de contrôle |
|---|---|---|
| 1 | **Secrets exposés** | Clés API hardcodées, tokens en clair dans logs/DataStore, BuildConfig en release |
| 2 | **Stockage sensible** | `EncryptedSharedPreferences` correct ? Fallback si Keystore corrompu ? |
| 3 | **Réseau** | Certificate pinning ? SSL trust trop large ? Requêtes HTTP non chiffrées ? |
| 4 | **ProGuard/R8** | Règles suffisantes ? API keys obfusquées ? Mapping file sauvegardé ? |
| 5 | **Permissions** | Permissions minimales ? Permissions runtime gérées ? |
| 6 | **Content validation** | Injection via réponses API Plex malformées ? XSS dans WebView ? |
| 7 | **Token management** | Rotation, expiration, refresh, logout propre (nettoyage complet) |
| 8 | **Android Backup** | `android:allowBackup=true` par défaut expose tokens/prefs via ADB. Vérifier `android:allowBackup="false"` ou `android:fullBackupContent` avec exclusions des données sensibles |
| 9 | **Exported components** | Activities/Services/Receivers avec `exported=true` sans attribut `permission` = surface d'attaque. Scanner le Manifest pour chaque composant exporté |
| 10 | **WebView security (approfondi)** | `setJavaScriptEnabled`, `addJavascriptInterface`, `setAllowFileAccess`, `setAllowContentAccess`, `setAllowFileAccessFromFileURLs` — chacun audité individuellement |
| 11 | **Logging en release** | Vérifier que Timber n'a pas de `DebugTree` actif en release build. Rechercher `Log.d`, `Log.v`, `println` qui pourraient exposer tokens, URLs, données utilisateur |
| 12 | **Clipboard leakage** | Si l'app copie des tokens, URLs serveur, ou données sensibles dans le presse-papier (accessible par toutes les apps sur API < 33) |

---

## PHASE 3 — Performance & Optimisation *(Performance Agent)*

```xml
<instructions>
  <regle>Distinguer explicitement pour chaque finding : Mesuré | Inféré | Suspecté</regle>
  <regle>Device de référence : Mi Box S (2 Go RAM, Mali-450, Android 9)</regle>
  <livrable>Top 20 bottlenecks classés par impact utilisateur + solutions concrètes</livrable>
</instructions>
```

| # | Axe d'audit | Points de contrôle |
|---|---|---|
| 1 | **Compose recomposition** | Lambdas instables, `List` au lieu d'`ImmutableList`, `key` manquant dans `LazyColumn` |
| 2 | **Startup time** | Init Hilt, ouverture Room DB, sync workers au démarrage. Objectif : cold start < 2s |
| 3 | **Mémoire** | Taille cache Coil, `PagingConfig` (pageSize, prefetchDistance, maxSize), bitmaps |
| 4 | **Réseau** | Requêtes redondantes, absence de caching HTTP, pas de batching multi-serveur |
| 5 | **Database** | Requêtes Room lentes, index manquants, configuration WAL |
| 6 | **Scrolling** | Jank dans les `LazyRow`/`LazyColumn` de l'écran Home |
| 7 | **APK size** | ABI splits configurés, libs natives (FFmpeg, MPV) optimisées ? |
| 8 | **Player** | Buffer ExoPlayer, transition entre épisodes, performance du seek |
| 9 | **Frame rendering** | Temps de rendu par frame (objectif ≤ 16ms / 60fps). Identifier les composables qui dépassent le budget frame. Utiliser `FrameTimingMetric` ou trace Systrace |
| 10 | **Wake locks / background work** | WorkManager, services ou wake locks qui empêchent la veille de l'appareil TV, drainage batterie/énergie inutile |
| 11 | **DNS & connection pooling** | Latence réseau initiale sur box TV avec Wi-Fi moyen (souvent le vrai bottleneck). OkHttp connection pool configuré ? DNS pre-resolve ? |
| 12 | **Room migrations** | Performance des migrations de schéma sur base existante avec données volumineuses. Fallback destructif configuré ? Migration testée ? |
| 13 | **Coil cache policy** | Ratio disk cache vs memory cache, politique d'éviction, taille max adaptée à 2 Go RAM. `MemoryCache.maxSizePercent` et `DiskCache.maxSizeBytes` vérifiés ? |
| 14 | **Baseline Profiles** | Présence et configuration d'un Baseline Profile pour améliorer le cold start de 20-30%. `ProfileInstaller` inclus ? Profils générés via Macrobenchmark ? |

---

## PHASE 4 — Qualité de code & Architecture *(Architecture Agent)*

```xml
<instructions>
  <regle>Pour chaque refactor proposé : définir l'architecture CIBLE, pas seulement le problème actuel</regle>
  <livrable>Score de santé architectural A–F par module + actions correctives avec architecture cible</livrable>
</instructions>
```

| # | Axe d'audit | Points de contrôle |
|---|---|---|
| 1 | **Clean Architecture violations** | Dépendances inversées, domain qui importe du framework Android |
| 2 | **Module boundaries** | `:app` contient-il du code qui devrait être dans `:data` ou `:domain` ? |
| 3 | **Duplication** | Code dupliqué entre ViewModels, composables, mappers |
| 4 | **Naming consistency** | Conventions incohérentes entre modules |
| 5 | **Dead code** | Imports inutilisés, fonctions non appelées, entités Room orphelines |
| 6 | **Dependency health** | Dépendances deprecated, versions obsolètes, vulnérabilités connues |
| 7 | **Error handling patterns** | Utilisation cohérente d'`AppError` ? Mix `try/catch` et `Result` ? |
| 8 | **Test infrastructure** | 73% des tests supprimés — prioriser la restauration dans quel ordre ? |
| 9 | **Mapper proliferation** | Chaîne Entity → Model → UiState avec mappers quasi identiques. Chaque couche de mapping est-elle justifiée ? Certains mappers peuvent-ils être fusionnés sans violer Clean Archi ? |
| 10 | **DI scope leaks** | `@Singleton` qui devraient être `@ViewModelScoped` ou `@ActivityRetainedScoped`. Objets lourds vivant plus longtemps que nécessaire via un scope trop large |
| 11 | **Repository caching strategy** | Chaque repository a-t-il une stratégie cache explicite et cohérente ? (network-first, cache-first, stale-while-revalidate). Incohérences entre repos ? |
| 12 | **UseCase justification** | UseCases "pass-through" qui ne font que `return repository.getData()` sans logique métier = couche morte. Identifier et documenter les UseCases à supprimer ou justifier |
| 13 | **Gradle build performance** | Temps de build incrémental, configuration cache activée ?, build scans, tâches inutiles à chaque build, dépendances non nécessaires ralentissant la résolution |

---

## PHASE 5 — UX & Design TV *(UX Agent)*

```xml
<instructions>
  <regle>Tester mentalement chaque parcours avec UNIQUEMENT : directions D-Pad + OK + Back</regle>
  <regle>Pour chaque problème UX : parcours affecté | problème dans le code | amélioration proposée | critères d'acceptation</regle>
  <livrable>Liste des problèmes UX par écran (parcours, problème, solution, critères d'acceptation)</livrable>
</instructions>
```

| # | Axe d'audit | Points de contrôle |
|---|---|---|
| 1 | **Navigation D-Pad** | Focus initial, retour de focus après navigation, gestion des bords de grille |
| 2 | **Focus indicators** | Visibles, cohérents, animés |
| 3 | **Conformité Android TV** | Respect des recommandations officielles Google TV / Leanback |
| 4 | **Loading states** | Shimmer/skeleton screens ou simple spinner ? |
| 5 | **Empty states** | Pas de serveur, biblio vide, recherche sans résultat, pas de favoris, pas de connexion |
| 6 | **Error states** | Messages user-friendly, boutons retry, aucune stacktrace visible |
| 7 | **Transitions** | Animations entre écrans, fade-in des images, hero animation sur les détails |
| 8 | **Overscan safe area** | Contenu dans les marges TV (5% inset minimum) |
| 9 | **Typography & spacing** | Lisible à 3m, contraste WCAG AA minimum |
| 10 | **Remote control UX** | Long press, double tap gérés ou ignorés |
| 11 | **Onboarding** | Premier lancement clair pour ajouter un serveur et naviguer |
| 12 | **Player UX** | Contrôles visibles, seek preview, épisode suivant, skip intro/credits |
| 13 | **Remote basique** | Télécommande sans gestures avancées (4 directions + OK + Back + Home) |
| 14 | **Focus restore après retour** | Quand l'utilisateur revient sur Home depuis Detail, le focus revient-il sur le bon item dans la bonne row ? `rememberLazyListState` + `saveable` correctement câblé ? |
| 15 | **Remote control variantes** | Certaines télécommandes n'ont pas de bouton Menu. Boutons media physiques (Play/Pause/Stop) correctement interceptés via `KeyEvent.KEYCODE_MEDIA_*` ? |
| 16 | **Loading states granulaires** | Distinguer : chargement initial (skeleton plein écran), refresh (indicateur discret), pagination (loader en bas de liste), erreur réseau (état dédié avec retry). Chacun a-t-il un état visuel propre ? |
| 17 | **Temps de réponse au focus** | Le highlight D-pad doit suivre en < 100ms. Latence perceptible = UX TV cassée. Vérifier que les recompositions Compose ne bloquent pas le feedback de focus |

---

## PHASE 6 — Features pour la vente *(UX Agent)*

```xml
<instructions>
  <regle>Proposer 5 features MAXIMUM — les plus crédibles avec l'architecture actuelle</regle>
  <livrable>Tableau des 5 features classées par ROI = (impact utilisateur × impact vente) / effort</livrable>
</instructions>
```

**Critères par feature :**

- Pourquoi crédible avec l'architecture actuelle
- Effort : `S` < 1j | `M` 1–3j | `L` 3–7j | `XL` > 7j
- Impact utilisateur (1–5) · Impact conversion/vente (1–5)
- Dépendances techniques sur le code existant

**Pool de candidates à évaluer :**

- Profils utilisateur avec avatars personnalisés
- "Continue Watching" cross-device
- Recommandations personnalisées locales (sans IA cloud)
- Mode enfant (filtrage par rating, profil restreint)
- Android TV Channels / Widgets (On Deck, Recommended)
- Screensaver personnalisé avec affiches de la bibliothèque
- Statistiques de visionnage (heures, genres, séries terminées)
- Version freemium + Google Play Billing

---

## PHASE 7 — Production Readiness Checklist *(Release Agent)*

```xml
<instructions>
  <regle>Pour chaque item, indiquer le statut RÉEL constaté dans le code</regle>
  <statuts>✅ Fait | ⚠️ Partiel | ❌ Non fait | 🔍 Non vérifiable statiquement</statuts>
</instructions>
```

### Build & Release
- [ ] Release signing configuré et fonctionnel
- [ ] ProGuard/R8 minification + shrink sans crash
- [ ] Version code/name strategy (SemVer)
- [ ] ABI splits pour réduire la taille
- [ ] Mapping file archivé pour crash reports
- [ ] Pas de debug code/logs en release (Timber release tree)

### Google Play Compliance
- [ ] Privacy policy URL
- [ ] Data safety form rempli
- [ ] `targetSdk 35` à jour
- [ ] Permissions justifiées
- [ ] Content rating questionnaire
- [ ] App category : "Entertainment" / "Video Players"

### Android TV Specific
- [ ] Banner icon 320×180 fourni (`android:banner` dans le manifest)
- [ ] Leanback launcher intent filter (`android.intent.category.LEANBACK_LAUNCHER`)
- [ ] `android:isGame="false"` dans le manifest
- [ ] `uses-feature android:name="android.software.leanback" android:required="true"`
- [ ] `uses-feature android:name="android.hardware.touchscreen" android:required="false"`
- [ ] Catégorie TV correcte dans le manifest

### Crash Reporting & Analytics
- [ ] Firebase Crashlytics (ou alternative)
- [ ] ANR detection
- [ ] Analytics events sur les actions clés
- [ ] Performance monitoring (startup, screen render)

### Accessibilité
- [ ] `ContentDescription` sur images/boutons
- [ ] Navigation TalkBack fonctionnelle
- [ ] Contraste suffisant (WCAG AA)
- [ ] Taille de texte scalable

### Localisation
- [ ] Strings externalisées (aucun hardcoded)
- [ ] Support FR/EN minimum
- [ ] Formats date/nombre localisés

### Testing
- [ ] Tests unitaires logique critique (> 60% coverage domain/data)
- [ ] Tests d'intégration Room
- [ ] Tests UI de base (Maestro ou Compose UI tests)
- [ ] Tests sur device réel Android TV
- [ ] Tests avec connexion lente / offline
- [ ] Tests multi-serveur

### Tooling Debug (non livré en release)
- [ ] Baseline Profiles générés et inclus via `ProfileInstaller`
- [ ] StrictMode activé en debug (détection disk/network sur main thread)
- [ ] LeakCanary intégré en debug pour détecter les memory leaks
- [ ] Firebase Test Lab ou équivalent pour tests automatisés sur vrais devices Android TV

---

## PHASE 8 — Plan d'action priorisé *(Release Agent)*

```xml
<instructions>
  <regle>Chaque item du plan indique : fichier(s) à modifier, effort estimé, dépendances (y compris inter-findings via le champ Dépendances du format FINDING)</regle>
</instructions>
```

### Sprint 1 — Stabilité critique *(1–2 semaines)*
- Corriger tous les P0 des phases 1–4
- Restaurer les tests P0 (cf. `MISSING_TESTS.md`)
- Crash-proof le player
- Corriger les ANR identifiés (main thread blocking)

### Sprint 2 — Sécurité & Performance *(1–2 semaines)*
- Corriger tous les P0 et P1 de la phase 2
- Optimiser les 5 plus gros bottlenecks perf
- Configurer Crashlytics
- Générer et intégrer les Baseline Profiles

### Sprint 3 — UX Polish *(2–3 semaines)*
- Implémenter les améliorations UX prioritaires
- Skeleton screens, empty states, error states
- Focus management complet + focus restore + conformité Android TV guidelines
- Valider le temps de réponse focus < 100ms

### Sprint 4 — Production Release *(1–2 semaines)*
- Google Play compliance complète (y compris manifest TV-specific)
- Localisation FR/EN
- Release build testée sur device réel (Mi Box S obligatoire)
- Store listing complet
- StrictMode + LeakCanary : zéro violation résiduelle

### Sprint 5+ — Features Premium *(ongoing)*
- Implémenter les features de différenciation sélectionnées en Phase 6
- Monétisation (freemium / Google Play Billing)

---

## Sorties finales obligatoires *(Release Agent)*

### Sortie A — Executive Summary

```xml
<instructions>
  <contenu>
    - État global du projet (forces + faiblesses principales)
    - Note production readiness /10
    - Note vendabilité / expérience utilisateur /10
    - Top 5 risques critiques
    - Nombre de findings par sévérité (P0 / P1 / P2) et par phase
    - Graphe de dépendances entre findings critiques (basé sur le champ Dépendances)
  </contenu>
</instructions>
```

### Sortie B — Top 10 Fixes en priorité

```xml
<instructions>
  <regle>Classés par ROI réel = impact / effort</regle>
  <colonnes>ID · Titre · Sévérité · Effort · Impact · Dépendances (IDs prérequis) · Risque de régression si non corrigé</colonnes>
</instructions>
```

### Sortie C — Backlog priorisé *(format ticket-ready)*

```xml
<instructions>
  <regle>1 item = 1 action concrète</regle>
  <colonnes>
    ID finding source · Titre ticket · Fichier(s) à modifier ·
    Effort estimé · Dépendances (IDs prérequis) · Critères d'acceptation
  </colonnes>
</instructions>
```

### Sortie D — Quick Wins vs Chantiers lourds

| Catégorie | Critère | Description |
|---|---|---|
| **Quick wins** | < 1 jour | Fix immédiat, impact direct |
| **Medium** | 1–3 jours | Amélioration significative |
| **Heavy** | > 3 jours | Refactor profond ou feature majeure |

---

## Règles d'audit globales

```xml
<contraintes>
  <regle id="1">Être concret — citer fichiers, lignes, code. Jamais de généralités.</regle>
  <regle id="2">Être honnête — signaler aussi les zones de qualité, pas seulement les problèmes.</regle>
  <regle id="3">Distinguer certitude et hypothèse — utiliser les niveaux de confiance systématiquement.</regle>
  <regle id="4">Prioriser — un P0 non corrigé = app invendable.</regle>
  <regle id="5">Penser utilisateur final — expérience Netflix-like fluide sur TV.</regle>
  <regle id="6">Penser développeur — code maintenable pour itérer rapidement après la release.</regle>
  <regle id="7">Commencer par lire le code — parcourir tout le repo, lire entièrement les fichiers critiques.</regle>
  <regle id="8">Signaler les zones non auditées — indiquer explicitement les parties non inspectées en profondeur.</regle>
  <regle id="9">Distinguer "bloquant vente" et "bloquant publication store" — deux logiques de priorité différentes.</regle>
  <regle id="10">Ne jamais recommander un refactor sans définir l'architecture cible.</regle>
  <regle id="11">Chaîner les findings interdépendants — utiliser le champ Dépendances pour relier les corrections qui doivent être faites dans un ordre précis.</regle>
  <regle id="12">Toujours considérer le device cible (Mi Box S, 2 Go RAM, Mali-450) — une optimisation qui fonctionne sur Pixel ne vaut rien si elle plante sur la box de l'utilisateur.</regle>
</contraintes>
```

---

## Modes d'utilisation

### Option A — Session unique
Copier l'intégralité de ce prompt dans une nouvelle session Claude Code. Long mais exhaustif.

### Option B — Agent Teams *(recommandé)*
Instancier les 6 agents définis dans la section "Architecture des agents" en haut de ce document.

### Option C — Phase par phase
Exécuter chaque phase séparément en copiant la section concernée + le bloc FORMAT FINDING.

---

## Changelog v3.0 vs v2.0

| Ajout | Raison |
|---|---|
| Champ `Dépendances` dans le format FINDING | Permet de chaîner les corrections interdépendantes (ex: leak mémoire + cache Coil) |
| Device cible explicite (Mi Box S) dans le contexte | Toutes les décisions perf/mémoire doivent viser ce hardware |
| Phase 1 : +5 axes (ANR, Intent, Config changes, Backstack, OOM) | Vecteurs de crash courants sur Android TV absents de v2 |
| Phase 2 : +5 axes (Backup, Exported, WebView avancé, Logging, Clipboard) | Surfaces d'attaque critiques oubliées |
| Phase 3 : +6 axes (Frame rendering, Wake locks, DNS, Migrations, Coil cache, Baseline Profiles) | Métriques TV-spécifiques et optimisations concrètes |
| Phase 4 : +5 axes (Mappers, DI scopes, Repo cache, UseCase justification, Gradle perf) | Angles morts architecturaux Clean Archi |
| Phase 5 : +4 axes (Focus restore, Remote variantes, Loading granulaire, Latence focus) | Points UX TV essentiels manquants |
| Phase 7 : section Android TV Specific + Tooling Debug | Prérequis manifest TV + outils debug obligatoires |
| Règle globale #11 : chaînage findings | Évite les corrections dans le désordre |
| Règle globale #12 : device cible | Ancre chaque décision dans la réalité hardware |
| Sortie A : graphe de dépendances findings | Vision macro des chaînes de corrections |
| Sortie B : colonne Dépendances ajoutée | Ordre de correction visible dans le Top 10 |
