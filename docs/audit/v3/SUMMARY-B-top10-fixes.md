# Sortie B — Top 10 Fixes (ROI)

Classés par ROI = impact / effort. Ces 10 items sont à prioriser avant tout le reste du backlog.

| # | ID | Titre | Sévérité | Effort | Impact | Dépendances | Risque de régression si non corrigé |
|---|---|---|---|---|---|---|---|
| 1 | [AUDIT-1-001](phase1-2-stability-security.md) | Xtream mapper `pageOffset=0` → UNIQUE INDEX wipe library | P0 | S | Critique | — | Bibliothèque Xtream silencieusement vide |
| 2 | [AUDIT-1-002](phase1-2-stability-security.md) | Release fallback debug keystore | P0 | S | Critique | — | OTA permanently broken sur cohorte concernée |
| 3 | [AUDIT-1-003](phase1-2-stability-security.md) | versionCode=1 figé | P0 | S | Critique | — | Upload Play Store refusé |
| 4 | [AUDIT-1-005](phase1-2-stability-security.md) | PlayerControlViewModel no-op silencieux args null | P1 | S | Élevé | — | Deep link malformé → écran noir permanent |
| 5 | [AUDIT-3-001](phase3-performance.md) | ExoPlayer buffer 30 s ~300 MB OOM Mi Box S | P0 | S | Critique | — | Crash lowmemorykiller pendant 4K HEVC |
| 6 | [AUDIT-3-004](phase3-performance.md) | `NetflixHomeContent` `scrollToItem` sur chaque focus change | P0 | S | Élevé | — | Scroll snap parasite, autres rails disparaissent |
| 7 | [AUDIT-2-001 / 1-004](phase1-2-stability-security.md) | `HttpLoggingInterceptor HEADERS` expose X-Plex-Token | P0 / P1 | S | Élevé | — | Token leak logcat / compromission compte Plex |
| 8 | [AUDIT-5-028](phase5-6-ux-features.md) | `OverscanSafeArea` défini mais jamais utilisé | P0 | M | Critique | — | Contenu coupé sur overscan TV → « l'app est cassée » |
| 9 | [AUDIT-5-029](phase5-6-ux-features.md) | Typographie trop petite pour 3 m (< 14 sp) | P0 | S | Critique | — | Illisible depuis le canapé → Play Store 1★ |
| 10 | [AUDIT-3-006](phase3-performance.md) | Aucun Baseline Profile (cible Android 9) | P0 | M | Élevé | AUDIT-3-005 | Cold start +20-30 % Mi Box S |

## Rationales

### 1. AUDIT-1-001 — Xtream mapper pageOffset wipe
ROI infini. 10 lignes de code (`mapIndexedNotNull { index, dto -> ... copy(pageOffset = index) }`), impact = toute la bibliothèque Xtream (segment IPTV = base utilisateurs la plus large probable). Le pitfall est documenté dans `MEMORY.md` mais pas fixé. Priority 0 absolue.

### 2. AUDIT-1-002 — Release signing fallback
5 lignes Gradle pour transformer un fallback silencieux en `GradleException`. Évite un désastre irréversible où une cohorte entière ne peut plus recevoir d'update.

### 3. AUDIT-1-003 — versionCode figé
1 ligne, `versionCode = 1016`. Débloque Play Store immédiatement.

### 4. AUDIT-1-005 — PlayerControlViewModel args null
Validation des args dans `init` du ViewModel. Évite qu'un deep link corrompu ne crée une expérience « app cassée » permanente.

### 5. AUDIT-3-001 — ExoPlayer LoadControl adaptatif
`ActivityManager.isLowRamDevice()` + profil buffer réduit. Résout le crash central sur le device cible déclaré. Le commentaire du code reconnaît déjà le problème — aucune ambiguïté.

### 6. AUDIT-3-004 — Home scroll-on-focus
Le `focusVersion++` + `scrollToItem` sur chaque D-pad déclenche un recompose de tous les rails → autres rails disparaissent. Fix : ne scroll que sur changement de ROW, pas de COLUMN.

### 7. AUDIT-2-001 / 1-004 — Token redaction
`HttpLoggingInterceptor.redactHeader("X-Plex-Token")`. Une ligne. Empêche la fuite du token dans logcat et les bug reports.

### 8. AUDIT-5-028 — OverscanSafeArea global
L'Overscan est défini dans `core/ui/OverscanSafeArea.kt` mais aucun écran ne l'applique. Wrapping des routes principales dans `OverscanSafeArea { ... }` corrige le problème d'un coup.

### 9. AUDIT-5-029 — Typographie ≥ 14 sp
Remplacer les tailles 13/11/9 sp par 16/14/12 sp dans `core/designsystem/Type.kt`. Impact direct sur la perception « app pro vs projet étudiant ».

### 10. AUDIT-3-006 — Baseline Profile
Créer un module `:baselineprofile` avec le template macrobenchmark. Générer sur le parcours Home → Library → Detail → Play. Cold start réduit de 20-30 % sur Android 9 — différence palpable pour l'utilisateur Mi Box S.
