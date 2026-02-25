# PlexHubTV — Production-Ready Audit & Improvement Prompt

> Copy-paste this prompt into a new Claude Code session (or use it with Agent Teams).

---

## THE PROMPT

```
Tu es un expert Android TV senior spécialisé en Kotlin, Jetpack Compose, Clean Architecture et UX TV. Tu audites PlexHubTV — un client Plex pour Android TV avec UI Netflix-like — pour le rendre **production-ready et vendable**.

Lis d'abord ARCHITECTURE.md, MISSING_TESTS.md, et le build.gradle.kts principal pour comprendre le projet.

Effectue un audit exhaustif en 8 phases. Pour chaque phase, produis un rapport structuré avec :
- Sévérité (P0 = bloquant vente, P1 = important, P2 = amélioration)
- Fichier(s) concerné(s) avec chemin exact
- Problème précis + code incriminé si applicable
- Solution concrète (pas de vague "améliorer X")

---

### PHASE 1 — STABILITÉ & CRASH-PROOFING

Auditer toute l'app pour les crashes potentiels :

1. **Null safety & edge cases** — trouver les `!!`, les cast non sécurisés, les accès index sans vérification
2. **Lifecycle leaks** — coroutines qui survivent au ViewModel/Activity, listeners non nettoyés
3. **Race conditions** — StateFlow mutations concurrentes, accès SharedPreferences/Room depuis threads multiples
4. **Process death** — est-ce que SavedStateHandle est utilisé correctement ? L'app survit-elle à un process kill ?
5. **Memory leaks** — singletons qui retiennent des Context, bitmaps non recyclés, listeners non déregistrés
6. **Error handling** — les try/catch manquants, les Flow qui crashent sans catch, les Channel non consommés
7. **Player robustness** — ExoPlayer/MPV : gestion des erreurs de codec, timeout réseau, perte de focus audio

Livrable : liste de tous les crash vectors classés par probabilité et impact.

---

### PHASE 2 — SÉCURITÉ & PROTECTION DES DONNÉES

Vérifier :

1. **Secrets exposés** — clés API hardcodées, tokens en clair dans les logs/DataStore, BuildConfig en release
2. **Stockage sensible** — EncryptedSharedPreferences correctement implémenté ? Fallback si Keystore corrompu ?
3. **Réseau** — certificat pinning ? SSL trust trop large ? Requêtes HTTP non chiffrées ?
4. **ProGuard/R8** — les règles sont-elles suffisantes ? API keys obfusquées ? Mapping file sauvegardé ?
5. **Permissions** — permissions demandées minimales ? Permissions runtime gérées ?
6. **Content validation** — injection via réponses API Plex malformées ? XSS dans WebView (si présent) ?
7. **Token management** — rotation, expiration, refresh, logout propre (nettoyage complet)

Livrable : rapport de sécurité avec niveaux de risque.

---

### PHASE 3 — PERFORMANCE & OPTIMISATION

Analyser :

1. **Compose recomposition** — trouver les recompositions excessives (lambda unstable, List au lieu d'ImmutableList, missing key dans LazyColumn)
2. **Startup time** — Hilt initialization, Room DB opening, sync workers au démarrage. Objectif : cold start < 2s
3. **Mémoire** — taille du cache Coil, Paging3 PagingConfig (pageSize, prefetchDistance, maxSize), bitmaps
4. **Réseau** — requêtes redondantes, manque de caching HTTP, pas de batching pour multi-serveur
5. **Database** — requêtes Room lentes (EXPLAIN QUERY PLAN), index manquants, WAL configuration
6. **Scrolling** — jank dans les LazyRow/LazyColumn de l'écran Home (mesurer avec Compose metrics si possible)
7. **APK size** — ABI splits configurés, mais les libs natives (FFmpeg, MPV) sont-elles optimisées ?
8. **Player** — buffer configuration ExoPlayer, transition entre épisodes, seek performance

Livrable : top 10 bottlenecks classés par impact utilisateur + solutions.

---

### PHASE 4 — QUALITÉ DE CODE & ARCHITECTURE

Évaluer :

1. **Clean Architecture violations** — dépendances inversées, domain qui importe du framework Android, data leak dans presentation
2. **Module boundaries** — est-ce que :app contient du code qui devrait être dans :data ou :domain ?
3. **Duplication** — code dupliqué entre ViewModels, entre Screen composables, entre mappers
4. **Naming consistency** — conventions de nommage incohérentes entre modules
5. **Dead code** — imports inutilisés, fonctions non appelées, entités Room non utilisées
6. **Dependency health** — dépendances deprecated, versions obsolètes, vulnérabilités connues
7. **Error handling patterns** — utilisation cohérente de AppError partout ? Ou mix de try/catch et Result ?
8. **Test infrastructure** — 73% des tests supprimés. Prioriser lesquels restaurer et comment.

Livrable : score de santé architectural (A-F) par module + actions correctives.

---

### PHASE 5 — UX & DESIGN TV (Netflix-level polish)

Auditer avec un oeil designer TV :

1. **Navigation D-Pad** — focus management : est-ce que chaque écran gère correctement le focus initial, le retour de focus après navigation, les bords de grille ? Tester mentalement chaque parcours utilisateur avec UNIQUEMENT les touches directionnelles + OK + Back
2. **Focus indicators** — sont-ils visibles, cohérents, animés ? L'utilisateur sait-il toujours où il est ?
3. **Loading states** — shimmer/skeleton screens ou juste un spinner ? Proposer des skeletons Netflix-like pour Home, Library, Details
4. **Empty states** — que voit l'utilisateur quand : pas de serveur, bibliothèque vide, recherche sans résultat, pas de favoris, pas d'historique, pas de connexion ?
5. **Error states** — messages user-friendly ? Boutons retry ? Pas de stacktrace visible ?
6. **Transitions** — animations entre écrans, fade-in des images, hero animation sur les détails
7. **Overscan safe area** — le contenu respecte-t-il les marges TV (5% inset) ?
8. **Typography & spacing** — taille de texte lisible à 3m, contraste suffisant (WCAG AA minimum), espacement TV-friendly
9. **Remote control UX** — long press, double tap, gestures : sont-ils gérés ou ignorés ?
10. **Onboarding** — premier lancement : l'utilisateur comprend-il comment ajouter un serveur, se connecter, naviguer ?
11. **Player UX** — contrôles visibles au bon moment, seek preview, épisode suivant (countdown Netflix), skip intro/credits smooth

Livrable : wireframes textuels des améliorations pour chaque écran + priorité.

---

### PHASE 6 — PROPOSITIONS DE FEATURES POUR LA VENTE

Proposer des features qui différencient PlexHubTV d'un simple client Plex :

1. **Features "wow" pour les acheteurs** :
   - Profils utilisateur avec avatars personnalisés
   - "Continue Watching" cross-device intelligent
   - Recommandations personnalisées (basées sur l'historique local, pas d'IA cloud)
   - Mode enfant (filtrage par rating, profil restreint)
   - Widgets TV (channels Android TV pour On Deck, Recommended)
   - Screensaver personnalisé avec affiches de la bibliothèque
   - Statistiques de visionnage (heures, genres, séries terminées)

2. **Features de monétisation** :
   - Version gratuite limitée (1 serveur, pas d'IPTV) vs Premium
   - Système de licence / activation
   - Google Play Billing integration
   - Trial period

3. **Features de rétention** :
   - Notifications push (nouveau contenu sur serveur)
   - Listes personnalisées (au-delà des favoris)
   - Notes/reviews personnelles sur le contenu

Pour chaque feature : effort estimé (S/M/L/XL), impact utilisateur, priorité.

---

### PHASE 7 — PRODUCTION READINESS CHECKLIST

Vérifier que l'app est prête pour le Google Play Store :

1. **Build & Release**
   - [ ] Release signing configuré et fonctionnel
   - [ ] ProGuard/R8 minification + shrink sans crash
   - [ ] Version code/name strategy (SemVer)
   - [ ] ABI splits pour réduire la taille
   - [ ] Mapping file archivé pour crash reports
   - [ ] Pas de debug code/logs en release (Timber release tree)

2. **Google Play compliance**
   - [ ] Privacy policy URL
   - [ ] Data safety form rempli
   - [ ] Target API level à jour (targetSdk 35)
   - [ ] Permissions justifiées
   - [ ] Content rating questionnaire
   - [ ] App category : "Entertainment" / "Video Players"

3. **Crash reporting & analytics**
   - [ ] Firebase Crashlytics (ou alternative)
   - [ ] ANR detection
   - [ ] Analytics events sur les actions clés
   - [ ] Performance monitoring (startup, screen render)

4. **Accessibility**
   - [ ] ContentDescription sur les images/boutons
   - [ ] TalkBack navigation fonctionnelle
   - [ ] Contraste suffisant
   - [ ] Taille de texte scalable

5. **Localization**
   - [ ] Strings externalisées (pas de hardcoded)
   - [ ] Support FR/EN minimum
   - [ ] RTL support (si pertinent)
   - [ ] Formats date/nombre localisés

6. **Testing**
   - [ ] Tests unitaires sur la logique critique (>60% coverage sur domain/data)
   - [ ] Tests d'intégration Room
   - [ ] Tests UI de base (Maestro ou Compose UI tests)
   - [ ] Tests sur device réel Android TV
   - [ ] Tests avec connexion lente / offline
   - [ ] Tests multi-serveur

Livrable : checklist complète avec statut actuel (fait/pas fait/partiel).

---

### PHASE 8 — PLAN D'ACTION PRIORISÉ

Synthétiser toutes les phases en un plan d'action unique :

**Sprint 1 (1-2 semaines) — Stabilité critique**
- Fixer tous les P0 des phases 1-4
- Restaurer les tests P0 (MISSING_TESTS.md)
- Crash-proof le player

**Sprint 2 (1-2 semaines) — Sécurité & Performance**
- Fixer les problèmes de sécurité
- Optimiser les 5 plus gros bottlenecks perf
- Configurer Crashlytics

**Sprint 3 (2-3 semaines) — UX Polish**
- Implémenter les améliorations UX prioritaires
- Skeleton screens, empty states, error states
- Focus management complet

**Sprint 4 (1-2 semaines) — Production Release**
- Google Play compliance
- Localisation FR/EN
- Release build testée
- Store listing

**Sprint 5+ (ongoing) — Features Premium**
- Implémenter les features de différenciation
- Monétisation

Pour chaque item du plan, indiquer : fichier(s) à modifier, effort, dépendances.

---

## RÈGLES D'AUDIT

1. **Sois concret** — cite les fichiers, les lignes, le code. Pas de généralités.
2. **Sois honnête** — si quelque chose est bien fait, dis-le. Ne cherche pas des problèmes là où il n'y en a pas.
3. **Priorise** — un P0 non fixé = app invendable. Focus dessus.
4. **Pense utilisateur final** — quelqu'un qui achète cette app sur Android TV veut une expérience Netflix-like fluide.
5. **Pense développeur** — le code doit être maintenable pour itérer rapidement après la release.
6. **Commence par lire le code** — ne fais AUCUNE supposition. Lis chaque fichier avant de commenter.
```

---

## UTILISATION

### Option A — Session unique (long mais complet)
Copie le prompt ci-dessus et colle-le dans une nouvelle session Claude Code.

### Option B — Agent Teams (recommandé, plus rapide)
```
Crée une équipe d'agents pour auditer PlexHubTV. Voici les rôles :

1. **Stability Agent** — Phases 1 + 2 (stabilité + sécurité)
2. **Performance Agent** — Phase 3 (performance + optimisation)
3. **Architecture Agent** — Phase 4 (qualité code + architecture)
4. **UX Agent** — Phases 5 + 6 (design TV + features)
5. **Release Agent** — Phases 7 + 8 (production readiness + plan d'action)

Chaque agent doit lire ARCHITECTURE.md et les fichiers pertinents avant de commencer.
Les agents doivent se challenger mutuellement sur leurs findings.
Le chef synthétise le rapport final.
```

### Option C — Phase par phase
Exécute chaque phase séparément en copiant uniquement la section concernée.
