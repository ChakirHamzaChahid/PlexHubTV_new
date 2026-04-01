# PlexHubTV - Analyse Complète de l'Application

> **Version analysée** : 1.0.16 | **Date** : Avril 2026  
> **Plateforme** : Android TV (API 27+ / Android 8.1+)  
> **Stack** : Kotlin, Jetpack Compose for TV, Material 3, Clean Architecture

---

## Table des matières

1. [Fonctionnalités](#1-fonctionnalités-complètes)
2. [Limites et faiblesses](#2-limites-et-faiblesses)
3. [Préconisations](#3-préconisations)
4. [Comparatif concurrentiel](#4-comparatif-avec-les-solutions-existantes)

---

## 1. Fonctionnalités complètes

### 1.1 Authentification & Multi-sources

| Fonctionnalité | Détail |
|---|---|
| **Authentification Plex** | Flow PIN (plex.tv) + token, stockage chiffré AES-256-GCM |
| **Plex Home** | Switch entre utilisateurs Plex, PIN protégé, watchlist par utilisateur |
| **Multi-serveurs Plex** | Connexion simultanée à N serveurs, découverte automatique, fallback local/remote/relay |
| **Jellyfin** | Authentification username/password, navigation et lecture intégrées |
| **Xtream Codes** | Comptes IPTV avec live TV, VOD, séries, catégories |
| **IPTV M3U** | Chargement de playlists M3U depuis URL |
| **Backend PlexHub** | Backend custom pour gestion centralisée IPTV |
| **Profils applicatifs** | Profils locaux indépendants de Plex Home (CRUD, filtres par catégorie) |

### 1.2 Lecture vidéo avancée

| Fonctionnalité | Détail |
|---|---|
| **Double moteur** | ExoPlayer (principal) + MPV (fallback pour codecs étendus) |
| **Streaming adaptatif** | HLS/DASH, sélection de qualité auto ou manuelle |
| **Reprise de lecture** | Bookmark automatique, synchronisation avec le serveur |
| **Skip intro/crédits** | Détection automatique via marqueurs Plex |
| **Chapitres** | Navigation par chapitre, overlay visuel |
| **Sous-titres** | Sélection multi-langue, style personnalisable (taille, couleur, fond, contour), OpenSubtitles intégré, support ASS/SSA |
| **Audio** | Pistes multi-langues, détection codec, surround sound |
| **Contrôles Netflix-style** | Barre auto-hide, skip 10s, vitesse de lecture, trickplay (thumbnails) |
| **File de lecture** | Queue d'épisodes pour séries |
| **Égaliseur audio** | Presets audio intégrés |
| **Deinterlacing** | Modes de désentrelacement configurables |

### 1.3 Navigation & Découverte de contenu

| Fonctionnalité | Détail |
|---|---|
| **Écran d'accueil** | Hero billboard, Continue Watching, Recently Added, hubs dynamiques |
| **Bibliothèques** | Films, séries, musique, photos — navigation avec filtres genre/année/serveur/tri |
| **Recherche universelle** | FTS4 (Full-Text Search), multi-serveurs, debounce 400ms, recherche par titre/année/type/personne |
| **Collections** | Collections Plex navigables |
| **Playlists** | CRUD complet (création, modification, suppression) |
| **Favoris** | Médias et acteurs favoris |
| **Watchlist** | Intégration Watchlist cloud Plex |
| **Historique** | Historique de visionnage complet avec timestamps |
| **Recommandations** | Suggestions basées sur les habitudes de visionnage |
| **Sidebar alphabétique** | Navigation rapide A-Z dans les grandes listes |
| **Pagination** | Paging 3 pour bibliothèques 10K+ items |

### 1.4 Détails & Métadonnées enrichies

| Fonctionnalité | Détail |
|---|---|
| **Fiche média** | Synopsis, casting, année, durée, résolution, codec, bitrate |
| **TMDB** | Métadonnées et notes importées depuis The Movie Database |
| **OMDb** | Notes IMDB et métadonnées complémentaires |
| **Déduplication cross-serveurs** | Unification via IMDB ID / TMDB ID / GUID (IdBridge) |
| **Multi-badges** | Indication de disponibilité sur chaque serveur |
| **Filmographie acteurs** | Fiche acteur avec tous ses films/séries |

### 1.5 Intégration Android TV

| Fonctionnalité | Détail |
|---|---|
| **Continue Watching Channel** | Jusqu'à 15 items synchronisés vers le launcher Android TV |
| **Watch Next** | Carte de reprise dans le launcher |
| **Deep linking** | `plexhub://play/{ratingKey}?serverId={serverId}` |
| **Screensaver** | DreamService personnalisé avec rotation de backdrops |
| **D-Pad/Télécommande** | Navigation complète au D-Pad optimisée |

### 1.6 Performances & Cache

| Fonctionnalité | Détail |
|---|---|
| **Cache HTTP** | OkHttp 50 Mo, TTL 5-10 min, intercepteur custom |
| **Cache images** | Coil adaptatif (20% heap RAM, 32-256 Mo) + 256 Mo disque |
| **Requêtes parallèles** | `async/awaitAll` pour multi-serveurs |
| **Détection connectivité** | StateFlow réseau, mode dégradé |
| **Workers background** | 6 workers : LibrarySync, ChannelSync, RatingSync, CollectionSync, UnifiedRebuild, CachePurge |
| **Sync périodique** | Toutes les 3h + immédiat post-lecture |

### 1.7 Sécurité

| Fonctionnalité | Détail |
|---|---|
| **Chiffrement credentials** | AES-256-GCM via EncryptedSharedPreferences |
| **HTTPS forcé** | Pour plex.tv et plex.app (HTTP autorisé uniquement LAN/IPTV) |
| **Backup exclusion** | Données sensibles exclues du backup Android |
| **ProGuard/R8** | Obfuscation et minification en release |
| **Conscrypt** | Provider SSL/TLS moderne |
| **AuthInterceptor** | Gestion non-bloquante des tokens (AtomicReference) |

### 1.8 Personnalisation & Réglages (45+ options)

| Catégorie | Options |
|---|---|
| **Apparence** | Thème (Plex, Blue, Green, Orange, Purple), dark/light, colonnes grille (2-6) |
| **Lecture** | Moteur (ExoPlayer/MPV), auto-play next, modes skip intro/crédits, volume theme song |
| **Sous-titres** | Taille, couleur, fond, contour, position |
| **Contenu** | Restrictions d'âge (G, PG, PG-13, R, NC-17), filtres genre |
| **Serveurs** | Ajout/suppression, gestion connexions Jellyfin/Xtream/IPTV |
| **TV Channels** | Activation/désactivation sync launcher |
| **Screensaver** | Activation, intervalle, affichage horloge |
| **Home** | Toggle Continue Watching, My List, Suggestions |
| **Cache** | Gestion manuelle du cache |
| **Mises à jour** | Auto-update toggle |

### 1.9 Localisation

| Langue | Couverture |
|---|---|
| **Anglais (EN)** | Complète (947 strings) |
| **Français (FR)** | Complète (867 strings) |

### 1.10 Monitoring & CI/CD

| Fonctionnalité | Détail |
|---|---|
| **Firebase Crashlytics** | Crash reporting automatique |
| **Firebase Analytics** | Tracking d'événements |
| **Firebase Performance** | Monitoring performance app |
| **GitHub Actions** | Build, Detekt, tests unitaires automatisés sur PR |
| **Artefacts** | APK + rapports retenus 7 jours |

### 1.11 Statistiques du projet

| Métrique | Valeur |
|---|---|
| Fichiers Kotlin | 200+ |
| ViewModels | 35 |
| Use Cases | 29 |
| Repositories | 24 impl. / 25 interfaces |
| Entités DB | 24 |
| DAOs | 21 |
| Routes navigation | 31 |
| Intégrations API | 7+ (Plex, TMDB, OMDb, OpenSubtitles, Jellyfin, Xtream, PlexHub) |
| Workers background | 6 |
| Modules Gradle | 11 |
| Lignes de code | 50 000+ |

---

## 2. Limites et faiblesses

### 2.1 Critiques

| # | Limite | Impact | Fichiers concernés |
|---|---|---|---|
| C1 | **Gestion d'erreurs incomplète** — Plusieurs repositories/ViewModels sans try-catch ni Result wrapper | Crashs silencieux, UX dégradée | `data/repository/*.kt`, `feature/*/ViewModel.kt` |
| C2 | **Aucun rate limiting API** — Pas de RateLimiter sur les clients HTTP | Risque de ban Plex, instabilité | `NetworkModule.kt` |
| C3 | **Validation d'entrées insuffisante** — URLs serveur, tokens, recherche non validés | Risque sécurité, erreurs inattendues | `feature/auth/*`, `feature/settings/*` |
| C4 | **Download non implémenté** — Bouton UI présent mais feature entièrement stubée | Feature annoncée mais absente, confusion utilisateur | `feature/downloads/`, `feature/details/MediaDetailScreen.kt` |

### 2.2 Importantes

| # | Limite | Impact |
|---|---|---|
| I1 | **Offline très limité** — Pas de navigation hors-ligne, recherche réseau-only, pas de fallback Room | Inutilisable sans réseau (hors contenu téléchargé) |
| I2 | **Accessibilité (a11y) quasi absente** — `contentDescription = null` sur 33+ écrans sur 35 | Non conforme WCAG, inaccessible aux lecteurs d'écran |
| I3 | **Couverture de tests faible** — 4 tests UI, pas de tests d'intégration ni E2E, pas de tests de migration DB | Risque de régression élevé |
| I4 | **Migrations DB non documentées** — Version 47 sans historique clair (18+ sauts de version) | Risque de perte de données lors des mises à jour |
| I5 | **Pas de système de notation** — Notes Plex/TMDB affichables mais pas de notation utilisateur locale | Feature manquante vs concurrents |
| I6 | **Pas de fonctionnalités sociales** — Pas de partage, recommandations sociales, activité | Engagement limité |
| I7 | **N+1 query** dans `OfflineWatchSyncRepositoryImpl` (lignes 312-339) — boucle séquentielle sur `getMetadata` | Performance dégradée en sync background |

### 2.3 Mineures

| # | Limite | Impact |
|---|---|---|
| M1 | IP de debug hardcodée (`192.168.0.175:8186`) dans `build.gradle.kts` | Fuite d'info développeur |
| M2 | Pas de Dependabot / scanning sécurité automatisé | Dépendances potentiellement vulnérables |
| M3 | Pas de code coverage dans CI (JaCoCo/Codecov) | Pas de visibilité qualité |
| M4 | README minimal (87 lignes), pas de CONTRIBUTING.md | Onboarding difficile pour contributeurs |
| M5 | Pas de `.env.example` / `local.properties.template` | Setup initial opaque |
| M6 | Pas de release automatisée (Play Store, semantic versioning, changelog) | Distribution manuelle |
| M7 | Pas de tests de charge (100K+ items, 10+ serveurs, mémoire soutenue) | Scalabilité non vérifiée |
| M8 | ProGuard rules trop larges pour certaines libs (`androidx.media3.**`) | APK potentiellement plus gros |
| M9 | Firebase sans politique de rétention documentée ni notice RGPD | Conformité à vérifier |
| M10 | Logging level `HEADERS` en debug sans vérification en release | Risque fuite info en prod |

---

## 3. Préconisations

### 3.1 Priorité 1 — Corrections critiques (1-2 semaines)

1. **Implémenter un type `Result<T>`** (sealed class Success/Error) et l'appliquer à tous les repositories
   - Envelopper les appels réseau dans des try-catch systématiques
   - Mapper les exceptions en `AppError` typés
2. **Ajouter un `RateLimitingInterceptor`** dans la chaîne OkHttp
   - Limiter les requêtes par fenêtre temporelle
   - Gérer le retry avec backoff exponentiel
3. **Valider toutes les entrées utilisateur**
   - Format URL (scheme HTTP/HTTPS)
   - Longueur et format token
   - Longueur et caractères de la recherche
4. **Documenter les migrations DB** de v1 à v47
   - Créer les fichiers `Migration` explicites manquants
   - Ajouter des tests d'intégration de migration

### 3.2 Priorité 2 — Améliorations majeures (2-4 semaines)

5. **Améliorer le mode offline**
   - Cache-first pour la navigation bibliothèque via Room
   - Recherche locale FTS4 quand offline
   - Écran d'accueil avec contenu mis en cache
6. **Accessibilité complète**
   - Ajouter `contentDescription` pertinent sur tous les écrans
   - Vérifier la navigation D-Pad pour tous les éléments interactifs
   - Tester avec TalkBack
7. **Augmenter la couverture de tests**
   - Objectif : 30+ tests UI Compose
   - Tests d'intégration pour auth flow, multi-serveur sync
   - Tests de migration DB
8. **Implémenter ou retirer Download**
   - Soit compléter l'implémentation avec WorkManager
   - Soit retirer le bouton UI pour éviter la confusion

### 3.3 Priorité 3 — Nice-to-have (4+ semaines)

9. Intégrer **JaCoCo + Codecov** dans CI (objectif 70%+ coverage)
10. Ajouter **Dependabot + SAST** (scanning sécurité automatisé)
11. Écrire **CONTRIBUTING.md**, doc d'architecture, schéma DB
12. **Automatiser la release** : semantic versioning, changelog, APK signé, Play Store
13. Déplacer l'IP de debug dans `local.properties`
14. Ajouter une **politique RGPD** pour Firebase
15. Implémenter des **feature flags** pour les rollouts progressifs

---

## 4. Comparatif avec les solutions existantes

### 4.1 Tableau comparatif détaillé

| Critère | **PlexHubTV** | **Plex (officiel)** | **Jellyfin** | **Emby** | **Kodi** | **Stremio** |
|---------|:---:|:---:|:---:|:---:|:---:|:---:|
| **Prix** | Gratuit | Freemium ($250 lifetime) | Gratuit | Freemium ($119 lifetime) | Gratuit | Gratuit |
| **Open Source** | Privé | Non | Oui | Non | Oui | Partiellement |
| **Android TV natif** | Oui (Compose TV) | Oui (Leanback) | Oui (basique) | Oui | Oui (skins) | Oui |
| **Multi-serveurs Plex** | **Oui** | Oui | Non | Non | Via plugin | Non |
| **Support Jellyfin** | **Oui** | Non | Natif | Non | Via plugin | Non |
| **IPTV / Xtream** | **Oui (natif)** | Non | Via plugin | Avec Premiere | Via PVR addons | Via addons |
| **Interface moderne** | **Compose TV + M3** | Leanback (ancien) | Web-based | Web-based | Skins custom | Standard |
| **Lecture locale** | Non (client) | Non (client) | Non (client) | Non (client) | **Oui** | Non |
| **Transcoding** | Via serveur | Via serveur | Via serveur | Via serveur | Non | Non |
| **Sous-titres avancés** | Oui (ASS/SSA) | Oui | Oui | Oui | **Excellent** | Oui (OpenSub) |
| **Live TV / DVR** | IPTV | Avec Pass | Oui | **Excellent** | Via PVR | Non |
| **Téléchargement offline** | Stubé | Avec Pass | Oui | Avec Premiere | Local | Non |
| **Recherche multi-serveurs** | **Oui (FTS4)** | Oui | Non | Non | Non | Via addons |
| **Métadonnées TMDB/OMDB** | **Oui (double)** | Oui | Oui | Oui | Via scrapers | Oui |
| **Profils utilisateurs** | Oui (Plex Home + locaux) | Oui | Oui | Oui | Via profiles | Oui (Trakt) |
| **Monitoring Firebase** | **Oui** | Interne | Non | Non | Non | Non |
| **Personnalisation UI** | 5 thèmes | Limitée | Thèmes CSS | Thèmes | **Skins illimités** | Limitée |
| **Communauté** | Naissante | Très large | Large (OSS) | Moyenne | **Très large** | Large |
| **Documentation** | Minimale | Excellente | Bonne | Bonne | **Exhaustive** | Bonne |

### 4.2 Avantages concurrentiels de PlexHubTV

1. **Client unifié Plex + Jellyfin + IPTV/Xtream** — aucune autre app ne combine nativement ces 3 sources dans un seul client
2. **Interface Jetpack Compose for TV + Material 3** — la plus moderne du marché vs Leanback vieillissant (Plex officiel) ou web-based (Jellyfin/Emby)
3. **Multi-serveurs Plex natif** avec recherche unifiée FTS4 et déduplication cross-serveurs (IdBridge)
4. **100% gratuit sans restriction** — pas de paywall comme Plex Pass ($250) ou Emby Premiere ($119)
5. **Architecture Clean Architecture** solide (11 modules, 24 repositories) — maintenable et extensible
6. **Performance optimisée** — cache HTTP/images, Paging 3, requêtes parallèles async
7. **Double moteur vidéo** (ExoPlayer + MPV) — flexibilité codec inégalée pour un client Plex

### 4.3 Faiblesses par rapport à la concurrence

| Concurrent | PlexHubTV manque de... |
|---|---|
| **Plex officiel** | Downloads fonctionnels, stabilité éprouvée, support officiel, base utilisateurs massive, documentation riche |
| **Jellyfin** | Communauté open-source, écosystème de plugins, documentation exhaustive, support multi-plateforme (web, iOS, desktop) |
| **Emby** | Live TV/DVR avancé, interface web polie, support multi-plateforme, stabilité entreprise |
| **Kodi** | Lecture locale, personnalisation illimitée (skins), écosystème d'addons massif (2000+), communauté énorme |
| **Stremio** | Facilité d'installation, découverte de contenu streaming, catalogue addons, OpenSubtitles natif, multi-plateforme |

### 4.4 Positionnement stratégique

```
                    Multi-sources
                         |
              PlexHubTV  *
                         |
     Personnalisation ---+--- Simplicité
                         |
          Kodi *         |         * Stremio
                         |
                    Mono-source
                   
        Plex *     Jellyfin *     Emby *
```

**PlexHubTV** occupe une niche unique : **client Android TV tout-en-un** qui agrège Plex + Jellyfin + IPTV dans une interface moderne. Aucun concurrent ne couvre ce spectre. Le principal défi est de transformer cet avantage technique en produit mature (robustesse, offline, tests, documentation).

### 4.5 Recommandation de positionnement

> **"Le hub multimédia ultime pour Android TV"**  
> Cibler les power users qui possèdent un serveur Plex ET un serveur Jellyfin ET/OU un abonnement IPTV, et qui veulent une seule interface unifiée, moderne et gratuite.

---

## Sources

- [Top 7 Plex Alternatives (2026) - RapidSeedbox](https://www.rapidseedbox.com/blog/plex-alternatives)
- [Best Plex Alternatives of 2026 - WunderTech](https://www.wundertech.net/what-are-the-best-plex-alternatives/)
- [Jellyfin vs Plex (2026) - RapidSeedbox](https://www.rapidseedbox.com/blog/jellyfin-vs-plex)
- [Kodi vs Plex vs Jellyfin vs Emby - DiyMediaServer](https://diymediaserver.com/post/kodi-vs-plex-vs-jellyfin-vs-emby-the-ultimate-media-playback-software-showdown/)
- [6 Reasons I Use Kodi Instead of Plex or Jellyfin - XDA](https://www.xda-developers.com/6-reasons-i-use-kodi-instead-of-plex-or-jellyfin/)
- [Plex Alternatives for Android - AlternativeTo](https://alternativeto.net/software/plex/?platform=android)
