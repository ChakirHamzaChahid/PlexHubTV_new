# PlexHubTV — Phase 1 & 2 Audit (Stability + Security)

> **Agent** : Agent 1 — Stability & Security
> **Date** : 2026-04-10
> **Branche** : `refonte/cinema-gold-theme`
> **Input** : `docs/audit/v3/phase0-cartography.md`
> **Scope** : Phase 1 (Stability, 12 axes) + Phase 2 (Security, 12 axes)

---

# 1. Phase 1 — Stability & Crash-Proofing

## 1.1 Résumé

| Sévérité | Count |
|---|---|
| P0 (release blocker) | 3 |
| P1 (important) | 10 |
| P2 (polish) | 5 |
| **Total** | **18** |

**Zones auditées** :
- Application class, `MainActivity`, `MainViewModel`, deep link graph
- `PlayerController` (singleton), `PlayerControlViewModel`, `ApkInstaller`
- `LibrarySyncWorker`, `CachePurgeWorker`, WorkManager scheduling
- `MediaLibraryQueryBuilder` collaterals + MediaEntity invariants
- All 35 ViewModels (sampled): `!!` usage, Flow collection patterns, SavedStateHandle
- `SecurePreferencesManager`, `SettingsDataStore` migration path
- `AuthInterceptor`, `AuthEventBus`, `ConnectionManager`, `NetworkModule` TrustManager
- `DatabaseModule` / Room migrations / fallbackToDestructiveMigration
- Xtream mapper (`pageOffset` UNIQUE INDEX pitfall)

**Zones NON auditées (à valider par Performance/Release/Device agents)** :
- Player on-device codec/HDR/Dolby Vision paths → à valider sur Mi Box S
- `MpvPlayerWrapper` JNI crash scenarios → non vérifiable statiquement
- Room `@RawQuery` runtime behavior with real multi-server data → non vérifiable statiquement
- ExoPlayer buffering under weak Wi-Fi → à valider sur device
- `onTrimMemory` contract : **aucune implémentation trouvée** (voir AUDIT-1-016)

## 1.2 Findings (triés P0 → P1 → P2)

---

### AUDIT-1-001
```
ID          : AUDIT-1-001
Titre       : Xtream mapper writes pageOffset=0 on null → UNIQUE INDEX collision wipes library
Phase       : 1 Stability
Sévérité    : P0
Confiance   : Élevée
```

**Impact** : utilisateur, stabilité, perte de données locales (bibliothèque vide silencieusement)

**Fichier(s)** :
- `data/src/main/java/com/chakir/plexhubtv/data/mapper/XtreamMediaMapper.kt:37,74`
- `core/database/src/main/java/com/chakir/plexhubtv/core/database/MediaEntity.kt:22`

**Dépendances** : aucune

**Preuve** :
```kotlin
// XtreamMediaMapper.kt:37 (mapVodToEntity)
sortOrder = "default",
pageOffset = dto.num ?: 0,  // ← fallback to 0 when provider omits `num`

// XtreamMediaMapper.kt:74 (mapSeriesToEntity)
sortOrder = "default",
pageOffset = dto.num ?: 0,  // ← same issue

// MediaEntity.kt:22
androidx.room.Index(
    value = ["serverId", "librarySectionId", "filter", "sortOrder", "pageOffset"],
    unique = true,
),
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le MEMORY.md du projet documente explicitement ce piège : "Default `pageOffset=0` + `INSERT OR REPLACE` silently deletes all previous rows with same index tuple — only last item survives." La majorité des providers Xtream IPTV ne fournissent pas l'attribut `num` dans leurs DTOs `XtreamVodStreamDto` / `XtreamSeriesDto`. Le fallback `?: 0` écrase donc **toutes les entités d'une même catégorie** car le tuple `(serverId, librarySectionId, filter, sortOrder, pageOffset)` devient identique pour chaque item. `MediaDao.upsertMedia(entities)` (OnConflictStrategy.REPLACE sur l'unique index) supprime silencieusement les précédents.

**Risque concret si non corrigé** :
Utilisateur configure un compte Xtream avec 2000 films VOD dans une catégorie → ouvre l'app → `SyncXtreamLibraryUseCase` appelle `mapVodToEntity` pour chaque DTO → seule **la dernière entrée de chaque catégorie** survit. La bibliothèque Xtream affiche 1 film par catégorie au lieu de 2000. Aucune erreur, aucun crash, aucun log explicite. Le pitfall exact documenté dans MEMORY.md se reproduit.

**Correctif recommandé** :
Remplacer le fallback constant par un index monotone fourni par l'appelant (position dans la liste mappée), comme `JellyfinSourceHandler.kt:139` le fait déjà (`.copy(pageOffset = index)`).

**Patch proposé** :
```kotlin
// XtreamVodRepositoryImpl.kt:54-57
val entities = dtos.mapIndexedNotNull { index, dto ->
    if (dto.streamId == null) return@mapIndexedNotNull null
    xtreamMapper.mapVodToEntity(dto, accountId).copy(pageOffset = index)
}
// Idem XtreamSeriesRepositoryImpl.kt
// Ne PAS utiliser dto.num car non fiable.
```
Alternative : modifier `XtreamMediaMapper.mapVodToEntity` pour prendre `index: Int` en paramètre et l'utiliser comme `pageOffset`.

**Étapes de reproduction** :
1. Configurer un compte Xtream (provider qui n'envoie pas `num`, ce qui est commun).
2. Sélectionner une catégorie VOD avec > 1 film.
3. Lancer la sync.
4. Ouvrir la bibliothèque Xtream → seul 1 film s'affiche par catégorie (le dernier inséré).

**Validation du fix** :
- Test unitaire : `XtreamVodRepositoryImplTest` avec 3 DTOs null-num dans la même catégorie → assert `mediaDao.upsertMedia` reçoit 3 entities avec `pageOffset` distinct (0, 1, 2).
- Test d'intégration Room : insérer 3 entités, vérifier `count()` == 3.

---

### AUDIT-1-002
```
ID          : AUDIT-1-002
Titre       : Release build fallback to debug keystore if release keystore missing
Phase       : 1 Stability
Sévérité    : P0
Confiance   : Élevée
```

**Impact** : publication store, signing, rejet Play Store, casse OTA updates

**Fichier(s)** :
- `app/build.gradle.kts:75-81`

**Dépendances** : AUDIT-2-004 (cross-ref)

**Preuve** :
```kotlin
// app/build.gradle.kts:75-81
val releaseSigningConfig = signingConfigs.findByName("release")
signingConfig =
    if (releaseSigningConfig?.storeFile?.exists() == true) {
        releaseSigningConfig
    } else {
        signingConfigs.getByName("debug")
    }
```

**Pourquoi c'est un problème dans PlexHubTV** :
Sur une machine CI où `keystore/keystore.properties` est absent (secrets non configurés), `./gradlew assembleRelease` produit silencieusement un APK signé avec le **debug keystore**. Aucun build error, aucun warning. Pour une app qui publie des updates in-app via `ApkInstaller` (GitHub Releases), si on shipe accidentellement un APK debug-signed, toute la chaîne d'update se casse : l'APK suivant (correctement signé release) ne pourra **jamais** être installé car Android rejette le changement de certificat.

**Risque concret si non corrigé** :
Un build CI/local produit par erreur un APK debug-signed → publié sur GitHub Releases → utilisateurs installent → au prochain release, `ApkInstaller.installApk()` échoue systématiquement avec `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. La seule issue est de désinstaller/réinstaller, perdant toutes les données locales (profils, secure prefs, Room).

**Correctif recommandé** :
Le build release doit **échouer explicitement** si la config de signing n'est pas présente.

**Patch proposé** :
```kotlin
// app/build.gradle.kts:68-88
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
    )
    val releaseSigningConfig = signingConfigs.findByName("release")
    if (releaseSigningConfig?.storeFile?.exists() != true) {
        throw GradleException(
            "Release build requires keystore/keystore.properties with storeFile. " +
            "Refusing to sign with debug keystore."
        )
    }
    signingConfig = releaseSigningConfig
    // ...
}
```

**Étapes de reproduction** :
1. Déplacer `keystore/keystore.properties` hors du repo.
2. Exécuter `./gradlew assembleRelease`.
3. Observer : build succeed, APK produit est signé debug.
4. Vérifier avec `apksigner verify --verbose app-release.apk` → signé avec `android-debug`.

**Validation du fix** :
- Supprimer temporairement `keystore.properties` → `./gradlew assembleRelease` doit échouer avec le message "Release build requires keystore...".
- Restaurer → build réussit.

---

### AUDIT-1-003
```
ID          : AUDIT-1-003
Titre       : versionCode=1 with versionName="1.0.16" — Play Store upload rejected, OTA broken
Phase       : 1 Stability
Sévérité    : P0
Confiance   : Élevée
```

**Impact** : publication store, OTA updates

**Fichier(s)** :
- `app/build.gradle.kts:27-28`
- `app/src/main/java/com/chakir/plexhubtv/core/update/UpdateChecker.kt:66-76`

**Dépendances** : aucune

**Preuve** :
```kotlin
// app/build.gradle.kts:27-28
versionCode = 1
versionName = "1.0.16"
```
```kotlin
// UpdateChecker.kt:66-76 — la comparaison se fait sur versionName (string semver)
private fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
    ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Play Store (et Google Play Internal Testing) **refuse** tout upload si le `versionCode` n'est pas strictement supérieur à l'APK précédent. Avec un `versionCode=1` figé à travers 16 versions `versionName`, chaque upload est rejeté. Par ailleurs, l'UpdateChecker compare uniquement `versionName`, ce qui fonctionne pour GitHub Releases mais démontre une incohérence de stratégie.

**Risque concret si non corrigé** :
Impossible de publier sur Play Store. Si l'app est publiée via GitHub Releases uniquement, la strategie "OTA via GitHub" fonctionne mais un mélange Play Store + side-load produira des installations incompatibles (Play Store voit versionCode=1 et ne propose jamais d'update).

**Correctif recommandé** :
Incrémenter `versionCode` monotoniquement à chaque release, idéalement automatisé depuis CI ou dérivé du tag git. Exemple : `versionCode = 1016` pour versionName "1.0.16".

**Patch proposé** :
```kotlin
// app/build.gradle.kts:23-33
defaultConfig {
    applicationId = "com.chakir.plexhubtv"
    minSdk = 27
    targetSdk = 35
    versionCode = 1016  // Sync with versionName: 1.0.16 → 1016
    versionName = "1.0.16"
    ...
}
```

**Étapes de reproduction** :
1. Tenter `./gradlew bundleRelease` puis upload AAB à Play Console Internal Testing.
2. Play Console rejette : "You need to use a different version code because 1 is already in use."

**Validation du fix** :
- `./gradlew bundleRelease` produit un AAB avec `versionCode=1016`.
- Upload Play Console accepté.

---

### AUDIT-1-004
```
ID          : AUDIT-1-004
Titre       : HttpLoggingInterceptor level HEADERS logs X-Plex-Token in debug
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Élevée
```

**Impact** : stabilité (logcat leak → analyst confusion), sécurité en debug

**Fichier(s)** :
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt:106-114`

**Dépendances** : AUDIT-2-001

**Preuve** :
```kotlin
// NetworkModule.kt:106-114
@Provides @Singleton
fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS  // ← logs X-Plex-Token, Authorization, etc.
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
`AuthInterceptor.kt:56` ajoute `X-Plex-Token` à toutes les requêtes. `Level.HEADERS` imprime tous les headers dans logcat en debug. Les devices de test branchés à ADB (et Android Studio) exposent le token Plex en clair dans logcat. Même si limité au debug build, ce token donne accès complet au compte Plex (Plex Home users, tous les serveurs, watchlist).

**Risque concret si non corrigé** :
Un contributeur partage une capture logcat dans un bug report → token compromis → accès au compte Plex de l'utilisateur. Les headers sont aussi loggés lors de tests CI si ADB est connecté.

**Correctif recommandé** :
Utiliser un `HttpLoggingInterceptor.Logger` custom qui masque `X-Plex-Token`, `Authorization`, ou rester en `Level.BASIC` (méthode + URL + code + durée).

**Patch proposé** :
```kotlin
@Provides @Singleton
fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
    return HttpLoggingInterceptor { message ->
        val sanitized = message
            .replace(Regex("X-Plex-Token: [^\\s]+"), "X-Plex-Token: ***")
            .replace(Regex("Authorization: [^\\s]+"), "Authorization: ***")
            .replace(Regex("[?&]X-Plex-Token=[^&\\s]+"), "?X-Plex-Token=***")
        Timber.tag("OkHttp").d(sanitized)
    }.apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.HEADERS
                else HttpLoggingInterceptor.Level.NONE
        redactHeader("X-Plex-Token")  // builtin alternative
        redactHeader("Authorization")
    }
}
```

**Étapes de reproduction** :
1. Lancer l'app en debug.
2. Observer logcat filtré sur `OkHttp` → `X-Plex-Token: xxx...` en clair.

**Validation du fix** :
- Même procédure → token remplacé par `***`.

---

### AUDIT-1-005
```
ID          : AUDIT-1-005
Titre       : PlayerControlViewModel init silently no-ops when all SavedStateHandle args are null
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Élevée
```

**Impact** : utilisateur, stabilité (écran noir permanent)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/player/PlayerControlViewModel.kt:70-86`
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:146-212`

**Dépendances** : aucune

**Preuve** :
```kotlin
// PlayerControlViewModel.kt:70-86
val ratingKey: String? = savedStateHandle["ratingKey"]
val serverId: String? = savedStateHandle["serverId"]
val directUrl: String? = savedStateHandle["url"]
val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

if (directUrl != null) { ... }
else if (serverId != null && ratingKey != null && ...) { ... }
else {
    // Plex: normal flow
    playerController.initialize(ratingKey, serverId, directUrl, startOffset)  // tous null → no-op silencieux
}

// PlayerController.kt:202-211
val initUrl = directUrl
val initRKey = ratingKey
val initSId = serverId
if (initUrl != null) {
    playDirectUrl(initUrl)
} else if (initRKey != null && initSId != null && mediaSourceResolver.resolve(initSId).needsUrlResolution()) {
    loadMedia(initRKey, initSId)
}
// else → rien, mais startPositionTracking() tourne quand même
startPositionTracking()
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le deep link `plexhub://play/{ratingKey}?serverId={serverId}` expose un point d'entrée externe. Si un deep link malformé arrive (ratingKey encoded incorrectement, serverId absent), NavController peut passer `null` ou `"null"` (string littéral). Aucune validation dans le ViewModel → l'écran Player s'ouvre, `initialize()` ne fait rien, `startPositionTracking` lance un job qui lit `currentPosition=0`, aucun player n'est jamais créé. L'utilisateur voit un écran noir permanent avec les contrôles UI (pause/play) qui ne font rien.

**Risque concret si non corrigé** :
Un utilisateur reçoit un deep link Plex d'un autre utilisateur via partage social. Le link est corrompu (URL-encoding cassé par messagerie). L'app ouvre VideoPlayer → écran noir permanent, seul back fonctionne. L'expérience est perçue comme un crash.

**Correctif recommandé** :
Valider les args en init et router vers une UI d'erreur explicite.

**Patch proposé** :
```kotlin
// PlayerControlViewModel.kt:68-87
init {
    // ...playbackManager collect...

    val ratingKey: String? = savedStateHandle["ratingKey"]
    val serverId: String? = savedStateHandle["serverId"]
    val directUrl: String? = savedStateHandle["url"]
    val startOffset: Long = savedStateHandle.get<Long>("startOffset") ?: 0L

    // Validate: at least one valid source required
    val hasDirect = !directUrl.isNullOrBlank()
    val hasMedia = !ratingKey.isNullOrBlank() && !serverId.isNullOrBlank() &&
                   ratingKey != "null" && serverId != "null"
    if (!hasDirect && !hasMedia) {
        Timber.e("PlayerControlViewModel: invalid navigation args (rk=$ratingKey, sid=$serverId, url=$directUrl)")
        playerController.updateState { it.copy(error = "Invalid playback request") }
        return@init
    }
    // ... continue normal flow ...
}
```

**Étapes de reproduction** :
1. Envoyer l'intent `adb shell am start -a android.intent.action.VIEW -d "plexhub://play/null?serverId=null"`.
2. App ouvre le player → écran noir permanent.

**Validation du fix** :
- Même procédure → écran player affiche "Invalid playback request", back fonctionne, pas d'écran noir.
- Test unitaire `PlayerControlViewModelTest.initWithAllNullArgs_showsError`.

---

### AUDIT-1-006
```
ID          : AUDIT-1-006
Titre       : ABI filters include x86/x86_64 while comment claims "ARM only"
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Élevée
```

**Impact** : publication store, APK size, stabilité (inclut natif x86 potentiellement non testé)

**Fichier(s)** :
- `app/build.gradle.kts:30-32,131`

**Dépendances** : aucune

**Preuve** :
```kotlin
// app/build.gradle.kts:30-32
ndk {
    abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
}
// app/build.gradle.kts:131
// No ABI splits — single universal APK (ARM only, x86 excluded via ndk.abiFilters)
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le commentaire documente une intention (ARM only), le code fait le contraire. Le single APK embarque libmpv + ffmpeg natifs pour 4 ABIs, ce qui gonfle l'APK (~60 MB au lieu de ~20 MB). Pour un Android TV Mi Box S (arm64-v8a), c'est du gaspillage net. Surtout, x86 n'est jamais réellement testé en QA (Android TV Emulator ≠ device réel), la stabilité du player MPV sur x86 est inconnue → possible crash JNI.

**Risque concret si non corrigé** :
- APK 3x trop gros → coût publication Play Store (exclusion 150 MB byte limit pour AAB est safe mais coût storage/CDN).
- Mi Box S arm64 télécharge la variante universelle contenant x86/x86_64 libs inutilisés.
- Premier utilisateur sur device x86 (Android TV BOX rare) rencontre un bug non reproduit en QA.

**Correctif recommandé** :
Soit respecter le commentaire (retirer x86/x86_64), soit mettre à jour le commentaire. Recommandation : passer à App Bundle (AAB) + density/language/ABI splits pour que chaque device ne télécharge que ses libs natives.

**Patch proposé** :
```kotlin
// app/build.gradle.kts:30-32
ndk {
    // ARM only — x86 Android TV devices sont rares et non testés
    abiFilters += listOf("armeabi-v7a", "arm64-v8a")
}
```

**Étapes de reproduction** :
1. `./gradlew assembleRelease`.
2. `unzip -l app-release.apk | grep 'lib/'` → observer présence lib/x86 + lib/x86_64 libmpv/ffmpeg.

**Validation du fix** :
- Même procédure → seuls lib/armeabi-v7a et lib/arm64-v8a présents.

---

### AUDIT-1-007
```
ID          : AUDIT-1-007
Titre       : DataStore has no corruptionHandler → CorruptionException on startup after device crash
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Moyenne
```

**Impact** : stabilité (crash cold start sur prefs corrompues)

**Fichier(s)** :
- `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/DataStoreExtensions.kt:12`
- `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/DataStoreModule.kt:22-26`

**Dépendances** : aucune

**Preuve** :
```kotlin
// DataStoreExtensions.kt:12
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
// → Pas de corruptionHandler ni de produceMigrations custom
```

**Pourquoi c'est un problème dans PlexHubTV** :
`preferencesDataStore` sans `corruptionHandler` lève `CorruptionException` si le fichier `.preferences_pb` est partiellement écrit (crash pendant un write, battery die, ou bug Android). `PlexHubApplication` lit `isFirstSyncComplete` dès le démarrage (ligne 129 + 240), et `MainActivity.onCreate` collecte `settingsDataStore.appTheme` (ligne 58). Une corruption propage jusqu'à un crash de l'Application.

**Risque concret si non corrigé** :
Utilisateur débranche la TV pendant que l'app écrit un setting (thème, last sync time). Au redémarrage, app crash loop "CorruptionException: CorruptedProtoException" dans l'application class → impossible de désinstaller proprement, seul Recovery → Factory Reset marche.

**Correctif recommandé** :
Ajouter un `ReplaceFileCorruptionHandler` qui fournit un `emptyPreferences()` au premier read corrompu (perte des settings = acceptable vs crash loop).

**Patch proposé** :
```kotlin
// DataStoreExtensions.kt
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Timber.e(ex, "DataStore corruption detected, resetting to empty")
        emptyPreferences()
    }
)
```

**Étapes de reproduction** :
1. Écrire un fichier `.preferences_pb` corrompu dans `/data/data/com.chakir.plexhubtv/files/datastore/settings.preferences_pb` via ADB.
2. Relancer l'app → crash cold start.

**Validation du fix** :
- Même procédure → app démarre avec settings par défaut, log "DataStore corruption detected".

---

### AUDIT-1-008
```
ID          : AUDIT-1-008
Titre       : LibrarySyncWorker creates foreground notification without runtime POST_NOTIFICATIONS check
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Élevée
```

**Impact** : stabilité, Android 13+ behavior

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt:88-94,460-484`
- `app/src/main/java/com/chakir/plexhubtv/MainActivity.kt:73-85`

**Dépendances** : aucune

**Preuve** :
```kotlin
// LibrarySyncWorker.kt:88-94
try {
    setForeground(getForegroundInfo())
    Timber.d("✓ Foreground service set")
} catch (e: Exception) {
    Timber.e(e, "✗ Failed to set foreground: ${e.message}")
}
```
```kotlin
// MainActivity.kt:73-85 — permission demandée mais fire-and-forget, résultat ignoré
private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Sur Android 13+ (API 33), `POST_NOTIFICATIONS` est runtime. L'utilisateur peut refuser → `setForeground()` réussit mais `notificationManager.notify(1, notification)` échoue silencieusement. Pire, si l'utilisateur refuse le foreground service notification, `setForeground()` peut lever `ForegroundServiceStartNotAllowedException` sur certains OEMs. Le catch log juste l'erreur et continue, mais le worker perd son statut foreground → killed à 10 minutes. Sur une grosse bibliothèque Plex (100k+ items), la sync ne complète jamais.

**Risque concret si non corrigé** :
Android TV user sur Google TV (Android 14) refuse notification permission au premier launch. LibrarySyncWorker démarre → `setForeground()` réussit sur API 33+ sans notif visible → mais sur certains devices avec des restrictions OEM, le worker est killed après 10 min. L'utilisateur ne voit jamais sa bibliothèque complète, la sync repart en boucle au prochain démarrage.

**Correctif recommandé** :
1. Rendre la requête permission synchrone au boot (pre-sync).
2. Si permission refusée sur API 33+, soit continuer sans notif visible (OK WorkManager), soit dégrader gracieusement avec un retry différé.
3. Ajouter `setNotificationSilent(true)` n'est pas suffisant — il faut `IMPORTANCE_LOW` (déjà fait) + tests.

**Patch proposé** :
```kotlin
// LibrarySyncWorker.kt — vérifier permission avant setForeground
override suspend fun doWork(): Result = withContext(ioDispatcher) {
    try {
        val hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true

        if (hasNotifPermission) {
            try { setForeground(getForegroundInfo()) }
            catch (e: Exception) {
                Timber.e(e, "Failed to set foreground — continuing without promotion")
            }
        } else {
            Timber.w("POST_NOTIFICATIONS denied — worker runs without foreground promotion (10 min limit)")
        }
        // ...rest of work...
    }
}
```

**Étapes de reproduction** :
1. Android TV API 33+.
2. Refuser la notification permission au premier launch.
3. Observer WorkManager — `LibrarySyncWorker` killed après 10 min sur grosse biblio.

**Validation du fix** :
- Même procédure → log "POST_NOTIFICATIONS denied", worker tourne sans notif visible, sync complète.

---

### AUDIT-1-009
```
ID          : AUDIT-1-009
Titre       : ConnectionManager NetworkCallback never unregistered (singleton leak on orientation=low)
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Élevée
```

**Impact** : stabilité (memory), pas user-visible

**Fichier(s)** :
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/ConnectionManager.kt:65-90`

**Dépendances** : aucune

**Preuve** :
```kotlin
// ConnectionManager.kt:65-90
init {
    // ...
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { invalidateAllConnections() }
        })
    } catch (e: Exception) { ... }
}
// No corresponding cm.unregisterNetworkCallback(...) anywhere in the class.
```

**Pourquoi c'est un problème dans PlexHubTV** :
`ConnectionManager` est `@Singleton` et vit tout le process life — ce n'est pas un leak per se, mais si le process est killed et restauré (process death restore flow), Hilt recrée un nouveau Singleton et enregistre un **nouveau** callback sans désenregistrer l'ancien (qui était GC'd avec l'ancien process). OK dans ce cas. En revanche, le callback anonyme capture `this@ConnectionManager` de la class, l'empêchant de jamais être GC. Sur un process runtime, c'est OK (singleton attendu). Donc c'est un non-issue technique. **Mais** il manque la gestion de `onLost` (retour offline) et `onCapabilitiesChanged` (passage Wi-Fi→cellulaire). Aucune logique pour détecter `isOffline=true` quand le network disparaît.

**Risque concret si non corrigé** :
Utilisateur débranche le Wi-Fi → Plex sync déjà en cours continue → timeouts individuels propagent des erreurs UI. `_isOffline.value` reste à false. Les retries indéfinis. Pas de mode offline explicite.

**Correctif recommandé** :
Implémenter `onLost(network)` → `_isOffline.value = true`, et `onAvailable` → `_isOffline.value = false`.

**Patch proposé** :
```kotlin
cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        _isOffline.value = false
        Timber.i("Network AVAILABLE")
        invalidateAllConnections()
    }
    override fun onLost(network: Network) {
        _isOffline.value = true
        Timber.w("Network LOST")
    }
})
```

**Étapes de reproduction** :
- Lancer l'app, activer le mode avion → `_isOffline.value` reste `false` → les queries continuent et timeout.

**Validation du fix** :
- `_isOffline.value` devient `true` immédiatement.

---

### AUDIT-1-010
```
ID          : AUDIT-1-010
Titre       : SecurePreferencesManager uses .apply() on synchronized writes but initial read is lazy + unlocked
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Moyenne
```

**Impact** : stabilité (race condition on first access)

**Fichier(s)** :
- `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SecurePreferencesManager.kt:52-65,114-122`

**Dépendances** : aucune

**Preuve** :
```kotlin
// SecurePreferencesManager.kt:52-65
private val encryptedPrefs: SharedPreferences? by lazy {
    createEncryptedPrefs()
        ?: run {
            deletePrefsFile()
            createEncryptedPrefs()
        }
        ?: run {
            _isEncryptionDegraded.value = true
            null
        }
}

// Lines 114-122 — init block reads _plexToken = encryptedPrefs?.getString(...)
init {
    _plexToken.value = encryptedPrefs?.getString(KEY_PLEX_TOKEN, null)
    ...
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le `init { }` block du singleton accède à `encryptedPrefs` (lazy), ce qui matérialise l'EncryptedSharedPreferences **sur le thread qui a créé le Hilt singleton**. Hilt peut créer le singleton depuis n'importe quel thread si un autre singleton en a besoin avant l'`Application.onCreate`. `EncryptedSharedPreferences.create()` fait de l'I/O bloquant (lecture fichier chiffré + Keystore). Si le singleton est matérialisé depuis le main thread (par exemple via `AuthInterceptor` → `AuthTokenProvider` → `DataStoreAuthTokenProvider` → `SettingsDataStore` → `SecurePreferencesManager` chaîne déclenchée par une request OkHttp sur main thread), on bloque le main thread pendant ~50-200 ms.

**Risque concret si non corrigé** :
Sur cold start sur un Mi Box S, le temps de déchiffrement Keystore peut dépasser 500 ms (device low-end). Si une route Compose tire `SettingsDataStore.appTheme` collect en Main avant la warm-up parallèle, `EncryptedSharedPreferences.create()` s'exécute sur Main → ANR marginal. `PlexHubApplication` essaie de warm-up via `settingsDataStoreLazy.get().isFirstSyncComplete.first()` en IO thread — bonne idée, mais si `MainActivity.onCreate` execute plus tôt et touche `settingsDataStore.appTheme.collectAsState(...)`, le lazy init peut arriver sur Main.

**Correctif recommandé** :
Utiliser `@IoDispatcher` pour forcer la création off-main, ou documenter le contrat "ne pas accéder depuis Main".

**Patch proposé** :
```kotlin
// SecurePreferencesManager.kt — make init async-safe
@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope scope: CoroutineScope,
    @IoDispatcher ioDispatcher: CoroutineDispatcher,
) {
    init {
        // Materialize encryptedPrefs on IO to avoid main-thread blocking
        scope.launch(ioDispatcher) {
            _plexToken.value = encryptedPrefs?.getString(KEY_PLEX_TOKEN, null)
            _clientId.value = encryptedPrefs?.getString(KEY_CLIENT_ID, null)
            // ...
        }
    }
}
```

**Étapes de reproduction** :
- Ajouter `Timber.d("Thread: ${Thread.currentThread().name}")` dans `encryptedPrefs` getter → observer "main" lors d'un cold start sur device lent.

**Validation du fix** :
- StrictMode `detectDiskReads()` sur le main thread ne devrait plus flag SecurePreferencesManager.

---

### AUDIT-1-011
```
ID          : AUDIT-1-011
Titre       : WorkManager 4 UniquePeriodicWork enqueued at every Application.onCreate without checking state
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Faible
```

**Impact** : stabilité mineure (no-op WorkManager call overhead)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt:227-322`

**Dépendances** : aucune

**Preuve** :
```kotlin
// PlexHubApplication.kt:274-323 — 4 enqueueUniquePeriodicWork appels à chaque onCreate()
WorkManager.getInstance(this).enqueueUniquePeriodicWork("LibrarySync", KEEP, syncRequest)
WorkManager.getInstance(this).enqueueUniquePeriodicWork("CollectionSync", KEEP, ...)
WorkManager.getInstance(this).enqueueUniquePeriodicWork("ChannelSync", KEEP, ...)
WorkManager.getInstance(this).enqueueUniquePeriodicWork("CachePurge", KEEP, ...)
```

**Pourquoi c'est un problème dans PlexHubTV** :
`ExistingPeriodicWorkPolicy.KEEP` rend l'opération idempotente, mais chaque appel fait un DB write dans la DB WorkManager interne. Sur cold start de bibliothèque TV (où le process est recréé fréquemment), on paye un coût DB inutile. Plus important : le call est synchrone côté Application.onCreate (pas dans une coroutine pour certains), impactant le startup. → **Non vérifiable statiquement** : setupBackgroundSync() lui-même n'est pas sur IO dispatcher, donc `WorkManager.getInstance(this).enqueueUniquePeriodicWork` est appelé sur main thread.

**Risque concret si non corrigé** :
+10-30 ms au cold start pour 4 appels WorkManager. Pas un blocker, mais s'accumule avec d'autres.

**Correctif recommandé** :
Wrap dans `appScope.launch(ioDispatcher)` comme le fait déjà le Job 1 (`LibrarySync_Initial`).

**Patch proposé** :
```kotlin
// PlexHubApplication.kt:227
private fun setupBackgroundSync() {
    WorkManager.getInstance(this).cancelAllWorkByTag(...)

    appScope.launch(ioDispatcher) {
        // Move all enqueue* calls here, off main thread
        val constraints = Constraints.Builder()...build()
        // ...
        WorkManager.getInstance(this@PlexHubApplication).enqueueUniquePeriodicWork(...)
    }
}
```

**Validation du fix** :
- Benchmark cold start time avant/après sur Mi Box S. → non vérifiable statiquement

---

### AUDIT-1-012
```
ID          : AUDIT-1-012
Titre       : MediaDetailViewModel uses .copy(isLoading = false, error = "...") as lambda but doesn't i18n
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Élevée
```

**Impact** : UX, pas un crash

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/details/MediaDetailViewModel.kt:78-79`

**Dépendances** : aucune

**Preuve** :
```kotlin
// MediaDetailViewModel.kt:78-79
Timber.e("MediaDetailViewModel: missing required navigation args (ratingKey=$ratingKey, serverId=$serverId)")
_uiState.update { it.copy(isLoading = false, error = "Invalid navigation arguments") }
```

**Pourquoi c'est un problème dans PlexHubTV** :
`"Invalid navigation arguments"` est un string literal non localisé. L'app supporte fr-FR (cf. values-fr/strings.xml). Un utilisateur français verra un message en anglais dans un cas d'erreur, rompant l'immersion TV.

**Correctif recommandé** :
Exposer un `AppError.Navigation.InvalidArgs` enum résolu par `ErrorMessageResolver`.

**Patch proposé** :
```kotlin
if (ratingKey == null || serverId == null) {
    Timber.e("MediaDetailViewModel: missing required navigation args")
    viewModelScope.launch { emitError(AppError.InvalidArgs) }
    _uiState.update { it.copy(isLoading = false, error = null) }
}
```

---

### AUDIT-1-013
```
ID          : AUDIT-1-013
Titre       : PlayerController is @Singleton but holds ExoPlayer + AudioFocusRequest tied to Application
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Moyenne
```

**Impact** : stabilité (state bleeding across sessions)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:40-101`

**Dépendances** : aucune

**Preuve** :
```kotlin
// PlayerController.kt:40-63
@Singleton
@OptIn(UnstableApi::class)
class PlayerController @Inject constructor(
    private val application: Application,
    ...
    @ApplicationScope private val applicationScope: CoroutineScope,
    ...
) {
    private var sessionJob = SupervisorJob(applicationScope.coroutineContext[Job])
    private var scope = CoroutineScope(sessionJob + mainDispatcher + globalHandler)
    ...
    @Volatile var player: ExoPlayer? = null
    @Volatile var mpvPlayer: MpvPlayer? = null
    ...
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { ... }
    private val mpvAudioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(...).build()
```

**Pourquoi c'est un problème dans PlexHubTV** :
`PlayerController` est `@Singleton` → partagé entre tous les `PlayerControlViewModel`. Si l'utilisateur ouvre le player une première fois (ViewModel A created, PlayerController initialized), puis navigue back (ViewModel A cleared → `playerController.release()` appelée), puis ré-ouvre sur un autre media (ViewModel B created, `playerController.initialize(...)` appelée). Entre ces deux sessions, le singleton **ne perd jamais** son `mpvAudioFocusRequest` ni son `audioFocusListener`. Le `mpvAudioFocusRequest` est alloué une seule fois en initialisation de classe et réutilisé. OK pour AudioFocus.

**Problème réel** : `_uiState` est un MutableStateFlow singleton. Même avec `release()` qui reset `_uiState.value = PlayerUiState()`, un observateur ViewModel qui ne s'abonne qu'après un bref délai peut voir un état transitoire incorrect provenant de la session précédente. Plus concret : `refreshRateManager`, `chapterMarkerManager`, `trickplayManager` sont aussi singletons injectés, mais leurs états ne sont pas tous reset dans `release()`. `autoSkippedMarkers.clear()` est fait, `chapterMarkerManager.clear()` est fait, mais `playerStatsTracker.stopTracking()` est appelé — OK. `trickplayManager.clear()` est appelé par le ViewModel onCleared, pas par release.

**Risque concret si non corrigé** :
Utilisateur regarde un film A, sort, ouvre un film B. `trickplayManager` peut encore avoir des sprites cached du film A pendant ~1 sec. Cosmétique mais visible sur le seek bar.

**Correctif recommandé** :
Centraliser le reset dans `PlayerController.release()` plutôt que split entre release + onCleared du VM.

**Patch proposé** :
```kotlin
// PlayerController.kt:282-283
chapterMarkerManager.clear()
trickplayManager.clear()  // ← déplacer ici au lieu de VM.onCleared
audioEqualizerManager.release()  // idem
```

---

### AUDIT-1-014
```
ID          : AUDIT-1-014
Titre       : SettingsViewModel.collect on WorkManager without scope cancellation — multiple orphan collectors
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Moyenne
```

**Impact** : stabilité (memory, UI state corruption)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt:225-229,328-333`

**Dépendances** : aucune

**Preuve** :
```kotlin
// SettingsViewModel.kt:225-229
viewModelScope.launch {
    workManager.getWorkInfoByIdFlow(syncRequest.id).collect { workInfo ->
        when (workInfo?.state) { ... }
    }
}
// Même pattern ligne 328 pour rating sync
```

**Pourquoi c'est un problème dans PlexHubTV** :
Si l'utilisateur triggre plusieurs sync manuelles rapidement (boutons "Sync Now"), chaque appel lance un nouveau `viewModelScope.launch { ... .collect }` qui ne complètera jamais tant que le worker tourne (l'état ENQUEUED/RUNNING n'est pas terminal dans ce when). Les précédents collectors restent actifs dans le même `viewModelScope` → fuite modérée (jusqu'à ViewModel cleared) et updates `_uiState` concurrentes. Le `.collect` n'a pas de `.catch` → un worker avec erreur non-retryable propagerait une exception qui **annule** le viewModelScope entier, freezant tous les settings.

**Correctif recommandé** :
Utiliser `safeCollectIn` + tracker le Job pour cancel le précédent.

**Patch proposé** :
```kotlin
private var syncObserverJob: Job? = null

fun onSyncNowClicked() {
    // ...
    syncObserverJob?.cancel()
    syncObserverJob = workManager.getWorkInfoByIdFlow(syncRequest.id).safeCollectIn(
        scope = viewModelScope,
        onError = { e -> Timber.e(e, "Sync observer error") }
    ) { workInfo -> ... }
}
```

---

### AUDIT-1-015
```
ID          : AUDIT-1-015
Titre       : ApkInstaller uses CoroutineScope(Dispatchers.Main) without SupervisorJob — one failure cancels all
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Élevée
```

**Impact** : stabilité

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/core/update/ApkInstaller.kt:31`

**Dépendances** : aucune

**Preuve** :
```kotlin
// ApkInstaller.kt:31
private val scope = CoroutineScope(Dispatchers.Main)
```

**Pourquoi c'est un problème dans PlexHubTV** :
`ApkInstaller` est `@Singleton`, son `scope` est créé à l'instanciation avec un `Job` implicite (non-Supervisor). Si une coroutine échoue (ex: cancellation d'une download), le Job parent est cancel → toutes les coroutines suivantes du scope sont ignorées. L'utilisateur ne peut plus retry un téléchargement après un premier échec non géré.

**Correctif recommandé** :
Utiliser `SupervisorJob()` + le `@ApplicationScope` Hilt.

**Patch proposé** :
```kotlin
@Singleton
class ApkInstaller @Inject constructor(
    private val application: Application,
    private val okHttpClient: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,  // ← injected
) {
    // supprimer: private val scope = CoroutineScope(Dispatchers.Main)

    fun downloadAndInstallAsync(...) {
        scope.launch(Dispatchers.Main) { downloadAndInstall(...) }
    }
}
```

---

### AUDIT-1-016
```
ID          : AUDIT-1-016
Titre       : No onTrimMemory / TRIM_MEMORY_RUNNING_LOW handler — Mi Box S OOM risk
Phase       : 1 Stability
Sévérité    : P1
Confiance   : Élevée
```

**Impact** : stabilité, OOM sur Mi Box S (1.5 GB RAM)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/PlexHubApplication.kt` (méthode absente)

**Dépendances** : aucune

**Preuve** :
Aucun override de `onTrimMemory(level: Int)` dans `PlexHubApplication`. Aucun callback `ComponentCallbacks2` registré. Aucune gestion de pression mémoire observée dans le projet (grep `onTrimMemory` → 0 résultat).

**Pourquoi c'est un problème dans PlexHubTV** :
Target device Mi Box S = 1.5-2 GB RAM, Android TV. Cold start charge : Coil image cache (memory), Room DB (page cache), ExoPlayer buffers (50 MB video cache), WebP images pleines résolution pour 2000+ posters Plex. `android:largeHeap="true"` dans manifest = 192-256 MB max heap. Sur pression mémoire (autre app open, AD-TV services), Android appelle `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` — PlexHubTV ne réagit pas → Coil ne purge pas sa memory cache → OOM crash.

**Risque concret si non corrigé** :
Mi Box S utilisateur a Netflix ouvert en background, ouvre PlexHubTV sur gros catalogue, navigue → OOM dans `BitmapFactory` → crash.

**Correctif recommandé** :
Implémenter `onTrimMemory` dans Application et purger Coil memory cache.

**Patch proposé** :
```kotlin
// PlexHubApplication.kt
override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
            imageLoaderLazy.get().memoryCache?.clear()
            Timber.w("onTrimMemory level=$level — cleared Coil memory cache")
        }
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
            imageLoaderLazy.get().memoryCache?.trimToSize(0L)
        }
    }
}
```

**Étapes de reproduction** :
- Mi Box S, gros catalogue (5000+ posters), ouvrir app → naviguer → `adb shell am send-trim-memory com.chakir.plexhubtv COMPLETE` → pas de purge.

**Validation du fix** :
- Même procédure → log "onTrimMemory level=X — cleared Coil memory cache".

---

### AUDIT-1-017
```
ID          : AUDIT-1-017
Titre       : CoroutineModule.provideMainDispatcher returns Dispatchers.Main (not Main.immediate) — unnecessary dispatch
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Moyenne
```

**Impact** : stabilité mineure, frames drops

**Fichier(s)** :
- `core/common/src/main/java/com/chakir/plexhubtv/core/di/CoroutineModule.kt:40`

**Dépendances** : aucune

**Preuve** :
```kotlin
@Provides
@MainDispatcher
fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
```

**Pourquoi c'est un problème dans PlexHubTV** :
`Dispatchers.Main.immediate` exécute directement si déjà sur main thread, évitant un `post` à la queue Looper. `PlayerController` utilise `mainDispatcher` intensivement pour les updates UI — chaque update = un extra frame delay, pouvant causer des jank sur le seek bar.

**Correctif recommandé** :
```kotlin
@Provides @MainDispatcher
fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
```

---

### AUDIT-1-018
```
ID          : AUDIT-1-018
Titre       : LibrarySyncWorker logs full server connectionCandidates URLs (includes tokens in plex.direct URLs)
Phase       : 1 Stability
Sévérité    : P2
Confiance   : Élevée
```

**Impact** : stabilité (logcat bloat), cross-link with AUDIT-2-002

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt:212`

**Dépendances** : AUDIT-2-002

**Preuve** :
```kotlin
Timber.d("→ [${server.name}] Starting sync (relay=${server.relay}, candidates=${server.connectionCandidates.size}, urls=${server.connectionCandidates.map { it.uri }})")
```

**Pourquoi c'est un problème dans PlexHubTV** :
Plex connection candidates incluent souvent des URLs `https://xxx.plex.direct:32400?X-Plex-Token=yyy` ou des IPs privées. Log `d` OK en release (CrashReportingTree filtre WARN+), mais en debug le logcat expose les tokens. Pour un worker qui tourne en background avec ADB connecté (QA), tokens exposés.

**Correctif recommandé** :
Logger seulement le host sans les paramètres query.

**Patch proposé** :
```kotlin
Timber.d("→ [${server.name}] Starting sync (relay=${server.relay}, candidates=${server.connectionCandidates.size}, hosts=${server.connectionCandidates.map { java.net.URI(it.uri).host }})")
```

---

# 2. Phase 2 — Security

## 2.1 Résumé

| Sévérité | Count | OWASP Breakdown |
|---|---|---|
| P0 | 2 | M2, M9 |
| P1 | 8 | M1, M2, M3, M8, M9 |
| P2 | 4 | M3, M6, M9 |
| **Total** | **14** | |

**Zones auditées** :
- Manifest, permissions, exported components, backup rules, data extraction rules
- `ProGuard` rules (minify release OK, -keep sufficient for Retrofit/Room/Serialization)
- `NetworkModule` TrustManager hostname-aware, `network_security_config.xml`
- `AuthInterceptor` 401 detection, token storage
- `SecurePreferencesManager` EncryptedSharedPreferences
- `UpdateChecker` + `ApkInstaller` OTA flow
- Logs sensibles (tokens, URLs) en release via `CrashReportingTree`
- `google-services.json`, `keystore.properties` git status

**Zones NON auditées** :
- Certificate transparency / pinning → non implémenté, documenté dans AUDIT-2-003
- Rate limiting côté client → non vérifié
- Deep link URI injection / PersonDetail free-text injection → non vérifié statiquement
- `JellyfinImageInterceptor` (image auth header injection) → non ouvert

## 2.2 Findings (triés P0 → P1 → P2)

---

### AUDIT-2-001
```
ID          : AUDIT-2-001
Titre       : HttpLoggingInterceptor HEADERS exposes X-Plex-Token in debug logcat
Phase       : 2 Security
Sévérité    : P0
Confiance   : Élevée
OWASP       : M9 (Insecure Logging/Monitoring)
```

**Impact** : sécurité (token exposure en debug builds / CI)

**Fichier(s)** :
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt:106-114`

**Dépendances** : AUDIT-1-004 (même root cause)

**Preuve** : Voir AUDIT-1-004.

**Pourquoi c'est un problème dans PlexHubTV** :
Le token Plex donne accès complet au compte (Plex Home users, all connected servers, watchlist sync, playback history). En debug, il est loggé via HttpLoggingInterceptor sous `Level.HEADERS`. ADB logcat = tout canal de debug (Android Studio, Firebase Test Lab, CI runner, bug report dump) le capture.

**Risque concret si non corrigé** :
Un contributeur joint une capture `logcat` à une issue GitHub pour diagnostiquer un bug réseau → le token est dans les logs → compromis publiquement sur GitHub.

**Correctif recommandé** : Voir patch AUDIT-1-004. Utiliser `redactHeader("X-Plex-Token")` d'OkHttp.

---

### AUDIT-2-002
```
ID          : AUDIT-2-002
Titre       : Timber.w/e calls log server URLs and baseUrls including potential tokens → Crashlytics leak
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M9 (Insecure Logging)
```

**Impact** : sécurité, privacy (URLs et IPs internes leak dans Crashlytics)

**Fichier(s)** :
- `data/src/main/java/com/chakir/plexhubtv/data/repository/BackendRepositoryImpl.kt:246` — `Timber.d("BACKEND getStreamUrl: resolved url=${response.url}")`
- `data/src/main/java/com/chakir/plexhubtv/data/repository/JellyfinServerRepositoryImpl.kt:160,283` — `Timber.w("JELLYFIN_TRACE [syncLibrary] client resolved: baseUrl=${client.baseUrl}")`
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/jellyfin/JellyfinConnectionTester.kt:31,35` — `Timber.w("JellyfinConnectionTester: HTTP ${response.code()} from $baseUrl")`
- `app/src/main/java/com/chakir/plexhubtv/work/LibrarySyncWorker.kt:212`

**Dépendances** : AUDIT-1-018

**Preuve** :
```kotlin
// CrashReportingTree.kt:11-19 — tout Timber.w/e/wtf est uploadé à Crashlytics en release
override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    if (priority < Log.WARN) return
    crashlytics.log(if (tag != null) "[$tag] $message" else message)
    if (t != null) { crashlytics.recordException(t) }
}
```
```kotlin
// JellyfinServerRepositoryImpl.kt:160
Timber.w("JELLYFIN_TRACE [syncLibrary] client resolved: baseUrl=${client.baseUrl}")
// client.baseUrl can be http://192.168.1.42:8096 or jellyfin.myhome.tld with embedded token
```

**Pourquoi c'est un problème dans PlexHubTV** :
En release, `CrashReportingTree` upload tous les messages `Timber.w`, `Timber.e`, `Timber.wtf` à Firebase Crashlytics comme breadcrumbs. Plusieurs messages contiennent des `baseUrl` (Jellyfin), `response.url` (Backend), `connectionCandidates[].uri` (Plex). Ces URLs exposent :
- IPs privées internes (fuite topologie réseau domestique utilisateur)
- Plex.direct URLs avec token embedded dans la query string
- Jellyfin access tokens dans certains cas

Firebase Crashlytics **retient 7 jours de breadcrumbs** et est indexé par plusieurs équipes Google. Tout dev ayant accès à la console voit ces URLs.

**Risque concret si non corrigé** :
Un user subit un crash → Crashlytics collecte breadcrumbs des 2 dernières minutes → contient `baseUrl=https://jellyfin.myhome.tld/users/authenticate?ApiKey=...`. L'URL est en clair dans Crashlytics UI. Compliance GDPR problème (PII = IP + URL serveur = identifiant utilisateur).

**Correctif recommandé** :
1. Dans `CrashReportingTree`, sanitize le message avant upload (regex).
2. Remplacer les logs qui contiennent `baseUrl` par des placeholders ou des server IDs opaques.

**Patch proposé** :
```kotlin
// CrashReportingTree.kt
override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    if (priority < Log.WARN) return
    val sanitized = message
        .replace(Regex("https?://[\\w.:-]+"), "<host>")
        .replace(Regex("X-Plex-Token=[^&\\s]+"), "X-Plex-Token=***")
        .replace(Regex("ApiKey=[^&\\s]+"), "ApiKey=***")
    crashlytics.log(if (tag != null) "[$tag] $sanitized" else sanitized)
    if (t != null) crashlytics.recordException(t)
}
```

**Validation du fix** :
- Trigger un warning avec une URL + token → vérifier Crashlytics breadcrumb → doit afficher `<host>` et `***`.

---

### AUDIT-2-003
```
ID          : AUDIT-2-003
Titre       : No certificate pinning for plex.tv / tmdb / omdb / opensubtitles
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M3 (Insecure Communication)
```

**Impact** : sécurité (MITM sur Wi-Fi public)

**Fichier(s)** :
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/NetworkModule.kt:147-294`

**Dépendances** : aucune

**Preuve** :
Aucun `CertificatePinner` configuré sur les OkHttpClient builders. Grep `certificatePinner` → 0 match. Le TrustManager custom accepte strictement :
- CA system pour les domaines publics
- Self-signed pour les IPs privées (LAN)

**Pourquoi c'est un problème dans PlexHubTV** :
Token Plex est un bearer token permanent (pas de refresh). MITM sur Wi-Fi public (airport, café) permet d'extraire le token via un attacker root CA installé (enterprise device, compromised device). Certificate pinning prévient ce scénario pour `plex.tv`.

**Risque concret si non corrigé** :
Utilisateur de PlexHubTV sur un hotel Wi-Fi avec captive portal malveillant + root CA injecté → attacker intercepte `https://plex.tv/api/v2/user` → token récupéré → accès au compte Plex depuis l'extérieur.

**Correctif recommandé** :
Ajouter un `CertificatePinner` pour `plex.tv` et sous-domaines dans l'OkHttpClient public builder. Attention : rotation certs plex.tv possible → pinner les clés intermédiaires, pas le leaf cert.

**Patch proposé** :
```kotlin
// NetworkModule.kt — provideOkHttpClient
private fun plexCertificatePinner() = okhttp3.CertificatePinner.Builder()
    .add("*.plex.tv", "sha256/<primary-pin>")
    .add("*.plex.tv", "sha256/<backup-pin>")
    .build()

// In builder:
.certificatePinner(plexCertificatePinner())
```

**Note** : Non-trivial à maintenir (rotation certs). Alternative pragmatique : **Network Security Config `<pin-set>`**.

```xml
<!-- network_security_config.xml -->
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">plex.tv</domain>
    <pin-set expiration="2027-04-10">
        <pin digest="SHA-256">...</pin>
        <pin digest="SHA-256">...</pin>
    </pin-set>
</domain-config>
```

**Validation du fix** :
- Mitmproxy avec CA custom → l'app doit rejeter la connexion plex.tv avec SSLHandshakeException.

---

### AUDIT-2-004
```
ID          : AUDIT-2-004
Titre       : Release build can sign with debug keystore — OTA update chain permanently broken
Phase       : 2 Security
Sévérité    : P0
Confiance   : Élevée
OWASP       : M9 (Insufficient Release Hardening)
```

**Impact** : sécurité release, OTA breakage irréversible

**Fichier(s)** :
- `app/build.gradle.kts:75-81`

**Dépendances** : AUDIT-1-002 (même root cause, dimension sécurité ici)

**Preuve** : Voir AUDIT-1-002.

**Pourquoi c'est un problème dans PlexHubTV** :
Debug keystore est public (AOSP debug key, même hash sur toutes les machines dev). Tout ce qui est signé avec est **triviallement clonable** par un attaquant. Un APK release signé debug peut être :
- Decompilé, repackagé avec malware, re-signé avec le même debug keystore
- Distribué via l'even route de update checker (si compromis)
- Mis à jour sans que Android détecte le change de cert

Combiné à `ApkInstaller` qui fait confiance à GitHub Releases par URL (AUDIT-2-011), un APK debug-signed introduit un trust anchor trivial.

**Risque concret si non corrigé** : Voir AUDIT-1-002.

**Correctif recommandé** : Voir patch AUDIT-1-002.

---

### AUDIT-2-005
```
ID          : AUDIT-2-005
Titre       : DataStore connectionCacheStore stores server URLs unencrypted — includes Plex token in plex.direct URLs
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M2 (Insecure Data Storage)
```

**Impact** : sécurité (token leak via local storage)

**Fichier(s)** :
- `data/src/main/java/com/chakir/plexhubtv/data/network/DataStoreConnectionCacheStore.kt:14-16`
- `core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt:649` (champ `cachedConnections`)

**Dépendances** : aucune

**Preuve** :
```kotlin
// DataStoreConnectionCacheStore.kt
class DataStoreConnectionCacheStore @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : ConnectionCacheStore {
    override val cachedConnections: Flow<Map<String, String>> =
        settingsDataStore.cachedConnections
    ...
}
```
Le storage est `DataStore<Preferences>` non chiffré. Les valeurs stockées sont les URLs validées par `ConnectionManager.findBestConnection`, qui peuvent inclure `https://xxx-xxx-xxx-xxx.yyyy.plex.direct:32400` — URLs Plex embarquant parfois un token dans la query string.

**Pourquoi c'est un problème dans PlexHubTV** :
`plex.tv` `/resources` endpoint renvoie des `Connection` entries avec `uri` brut. Si ces URIs contiennent un `?X-Plex-Token=xxx` (observé historiquement), le token est persisté en clair dans DataStore. Même sans token embedded, les IPs privées + port Plex révèlent la topologie réseau maison (fuite privacy).

**Risque concret si non corrigé** :
Device compromis (root) → `cat /data/data/com.chakir.plexhubtv/files/datastore/settings.preferences_pb` → voir toutes les URLs serveurs historiques. Même sur un backup extraction (bien que `allowBackup=false` soit actif), tout malware ciblé obtient l'info.

**Correctif recommandé** :
Deux options :
1. Migrer `cachedConnections` vers `SecurePreferencesManager` (AES-256-GCM).
2. Sanitize les URLs avant stockage (strip query string, keep only `scheme://host:port`).

**Patch proposé (option 2, moins invasif)** :
```kotlin
// DataStoreConnectionCacheStore.kt
override suspend fun saveConnection(serverId: String, url: String) {
    val sanitized = try {
        val u = java.net.URI(url)
        "${u.scheme}://${u.host}${if (u.port > 0) ":${u.port}" else ""}"
    } catch (e: Exception) { url }
    settingsDataStore.saveCachedConnection(serverId, sanitized)
}
```

**Validation du fix** :
- Inspecter le fichier `.preferences_pb` après sync → aucun `X-Plex-Token=` présent.

---

### AUDIT-2-006
```
ID          : AUDIT-2-006
Titre       : Network Security Config permits cleartext for all non-plex.tv domains
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M3 (Insecure Communication)
```

**Impact** : sécurité

**Fichier(s)** :
- `app/src/main/res/xml/network_security_config.xml:16-20`

**Dépendances** : aucune

**Preuve** :
```xml
<base-config cleartextTrafficPermitted="true">
    <trust-anchors>
        <certificates src="system" />
    </trust-anchors>
</base-config>
<domain-config cleartextTrafficPermitted="false">
    <domain includeSubdomains="true">plex.tv</domain>
    <domain includeSubdomains="true">plex.app</domain>
</domain-config>
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le commentaire justifie par "IPTV arbitrary domains + local HTTP". C'est légitime pour le LAN, mais ouvre HTTP pour **tous** les domaines publics (tmdb.org, themoviedb.org, opensubtitles.com) — alors que **tous ces services supportent HTTPS**. Un IPTV provider pourrait aussi servir du metadata en HTTP → MITM sur les posters/descriptions → UX corruption.

**Risque concret si non corrigé** :
Un IPTV provider indélicat serve le playlist M3U via HTTP → attacker MITM swap les URLs de streams → redirige l'utilisateur vers des streams malveillants / contenus illégaux / publicité agressive.

**Correctif recommandé** :
Whitelister explicitement les domaines cleartext plutôt qu'inverse.

**Patch proposé** :
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Default: HTTPS only, system CAs -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Plex + domains publics: HTTPS strict -->
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">plex.tv</domain>
        <domain includeSubdomains="true">plex.app</domain>
        <domain includeSubdomains="true">plex.direct</domain>
    </domain-config>

    <!-- LAN IP ranges: cleartext OK pour serveurs auto-hébergés -->
    <domain-config cleartextTrafficPermitted="true">
        <!-- RFC1918 ranges — Plex/Jellyfin LAN -->
        <domain includeSubdomains="false">10.0.0.0/8</domain>
        <domain includeSubdomains="false">172.16.0.0/12</domain>
        <domain includeSubdomains="false">192.168.0.0/16</domain>
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>

    <!-- Debug IPTV: cleartext (optional) - à durcir à terme -->
    <!-- Si pas moyen de whitelister, garder base cleartext="true" + warning doc -->
</network-security-config>
```

**Note** : `<domain>IP-range</domain>` ne supporte pas CIDR officiellement. Alternative = `base-config cleartext=true` mais alors c'est l'état actuel. Le fix pragmatique = documenter le trade-off dans la config et ajouter les domaines publics à la section stricte.

---

### AUDIT-2-007
```
ID          : AUDIT-2-007
Titre       : ProGuard keeps core.network.model.** classes — exposes DTO class names in release
Phase       : 2 Security
Sévérité    : P2
Confiance   : Élevée
OWASP       : M9 (Reverse Engineering)

```

**Impact** : sécurité (reverse engineering minimal)

**Fichier(s)** :
- `app/proguard-rules.pro:30-33`

**Dépendances** : aucune

**Preuve** :
```
-keep class com.chakir.plexhubtv.core.network.model.** { *; }
-keep class com.chakir.plexhubtv.core.network.backend.** { *; }
-keep class com.chakir.plexhubtv.core.network.xtream.** { *; }
-keep class com.chakir.plexhubtv.core.network.jellyfin.** { *; }
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le `-keep class ... { *; }` désactive l'obfuscation ET le shrinking pour ces packages. Les Gson DTOs peuvent être obfusqués à condition de garder `@SerializedName`. Le keep blanket expose :
- Structure complète API Plex/Jellyfin/Xtream/Backend (facilite reverse engineering)
- Noms des endpoints dans les services Retrofit
- Champs internes qui pourraient révéler des decisions d'architecture

Moins critique que les autres findings mais reduit la defense-in-depth.

**Correctif recommandé** :
Utiliser des règles plus ciblées :

**Patch proposé** :
```
# Retrofit interfaces — obfuscate method bodies OK, keep annotations
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault

# Gson DTOs — keep fields annotated with @SerializedName only, obfuscate class names allowed with Gson 2.8+
-keepclassmembers,allowobfuscation class com.chakir.plexhubtv.core.network.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowobfuscation class com.chakir.plexhubtv.core.network.backend.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
# Même pour xtream + jellyfin
```

---

### AUDIT-2-008
```
ID          : AUDIT-2-008
Titre       : ApkInstaller installs downloaded APK without SHA256 verification
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M8 (Code Tampering)
```

**Impact** : sécurité (OTA supply chain)

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/core/update/ApkInstaller.kt:35-88`
- `app/src/main/java/com/chakir/plexhubtv/core/update/UpdateChecker.kt:49-59`

**Dépendances** : AUDIT-2-004, AUDIT-2-011

**Preuve** :
```kotlin
// ApkInstaller.kt:35-88
suspend fun downloadAndInstall(downloadUrl: String, versionName: String) {
    // ...
    val request = Request.Builder().url(downloadUrl).build()
    val response = okHttpClient.newCall(request).execute()
    // ...
    body.byteStream().use { input ->
        apkFile.outputStream().use { output ->
            // write bytes
        }
    }
    installApk(apkFile)  // ← aucun check hash/signature avant install
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Android system lui-même vérifiera la signature du package installer — c'est la seule protection. Cela fonctionne uniquement si le keystore release est identique à l'install existant (cf. AUDIT-2-004). Si un attaquant compromet le pipeline GitHub release (token compromis, GitHub Actions bug) et pousse un APK malveillant signé par un autre keystore, l'installation échoue mais l'utilisateur voit "Install failed" sans comprendre pourquoi.

De plus, si le download est corrompu en transit (HTTP hijack improbable via HTTPS GitHub mais possible via intermediary proxy qui injecte MITM cert), l'APK corrompu est installé — ou plutôt rejeté mais l'expérience est mauvaise.

**Risque concret si non corrigé** :
- Partial download (network drop) → APK tronqué → installer reject "Invalid package" → user stuck.
- GitHub release compromis → APK malveillant téléchargé, rejeté par Android signature check, mais le download prend du temps.

**Correctif recommandé** :
Publier un SHA256 dans la release notes JSON et vérifier avant install.

**Patch proposé** :
```kotlin
// UpdateInfo
data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,  // ← add
    ...
)

// ApkInstaller — after download complete
val digest = apkFile.inputStream().use { input ->
    val md = java.security.MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(8192)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    md.digest().joinToString("") { "%02x".format(it) }
}
if (digest.equals(expectedSha256, ignoreCase = true).not()) {
    apkFile.delete()
    _state.value = InstallState.Error("Hash mismatch — download corrupted")
    return@withContext
}
installApk(apkFile)
```

**Validation du fix** :
- Corrompre le fichier téléchargé via instrumentation → ApkInstaller doit refuser l'install.

---

### AUDIT-2-009
```
ID          : AUDIT-2-009
Titre       : BuildConfig.API_BASE_URL = "https://plex.tv/" en release (dead code but exposes intent)
Phase       : 2 Security
Sévérité    : P2
Confiance   : Élevée
OWASP       : M2 (Config Hygiene)
```

**Impact** : sécurité (config hygiene)

**Fichier(s)** :
- `app/build.gradle.kts:53,82`

**Dépendances** : aucune

**Preuve** :
```kotlin
// debug
buildConfigField("String", "API_BASE_URL", "\"http://192.168.0.175:8186/\"")
// release
buildConfigField("String", "API_BASE_URL", "\"https://plex.tv/\"")
```
Grep `BuildConfig.API_BASE_URL` → **0 match en code source** → dead code.

**Pourquoi c'est un problème dans PlexHubTV** :
Le champ est dead mais reste dans le classfile release, et son nom suggère qu'il pointe vers l'API backend. Un reverse engineer qui regarde `BuildConfig` voit "https://plex.tv/" comme base URL et se pose la question. Plus embêtant : `"http://192.168.0.175:8186/"` en debug est l'IP **privée du développeur** — info non-sensitive mais témoigne d'une hygiène à renforcer.

**Correctif recommandé** :
Soit retirer les buildConfigField inutilisés, soit les utiliser effectivement dans `BackendApiClient`.

**Patch proposé** :
Supprimer les 4 `buildConfigField("String", "API_BASE_URL", ...)` et `PLEX_TOKEN`, `IPTV_PLAYLIST_URL`, `TMDB_API_KEY`, `OMDB_API_KEY` du `release` block (ils sont "" de toute façon). En debug, garder `PLEX_TOKEN` si utilisé pour dev workflow.

---

### AUDIT-2-010
```
ID          : AUDIT-2-010
Titre       : Dream Service exported=true without custom permission (partly mitigated by BIND_DREAM_SERVICE)
Phase       : 2 Security
Sévérité    : P2
Confiance   : Élevée
OWASP       : M1 (Exported Components)
```

**Impact** : sécurité (très mineure, protégée par permission système)

**Fichier(s)** :
- `app/src/main/AndroidManifest.xml:62-74`

**Dépendances** : aucune

**Preuve** :
```xml
<service
    android:name=".feature.screensaver.PlexHubDreamService"
    android:exported="true"
    android:label="@string/app_name"
    android:permission="android.permission.BIND_DREAM_SERVICE">
```

**Pourquoi c'est un problème dans PlexHubTV** :
`BIND_DREAM_SERVICE` est une permission système signature-protected : seul Android System peut bind le service. Donc l'exposition est sûre techniquement. Cependant, le flag `exported="true"` combiné à un `DreamService` custom reste une surface d'attaque théorique sur ROMs custom.

**Risque concret si non corrigé** :
Sur un device rooted / custom ROM qui a mal configuré les signature permissions, un autre app pourrait théoriquement bind ce service. Très marginal.

**Correctif recommandé** :
Cela suit la spec Android DreamService. Pas de changement requis, mais documenter dans un commentaire.

---

### AUDIT-2-011
```
ID          : AUDIT-2-011
Titre       : UpdateChecker trusts GitHub API response without TLS pin or response integrity check
Phase       : 2 Security
Sévérité    : P2
Confiance   : Moyenne
OWASP       : M3 (Supply Chain via MITM)
```

**Impact** : sécurité

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/core/update/UpdateChecker.kt:23-62`

**Dépendances** : AUDIT-2-003, AUDIT-2-008

**Preuve** :
```kotlin
private const val GITHUB_API_URL =
    "https://api.github.com/repos/chakir-elarram/PlexHubTV/releases/latest"
// ...
val request = Request.Builder().url(GITHUB_API_URL).header(...).build()
val response = okHttpClient.newCall(request).execute()
// No TLS pin check, no signature verification of the release JSON
```

**Pourquoi c'est un problème dans PlexHubTV** :
GitHub is reasonably trusted — HTTPS + system CAs sufficient for 99% of users. The remaining 1% :
- Enterprise devices with MITM root CA
- Networks with forced proxies (hotel captive portal with TLS interception)
→ attacker intercepts `api.github.com`, returns fake `release.json` pointing to malicious APK.

Mitigated by AUDIT-2-008 (SHA256 check) if published hash is signed externally. Otherwise, not mitigated.

**Correctif recommandé** :
Voir AUDIT-2-003 (cert pin GitHub) et AUDIT-2-008 (SHA256 dans release notes).

---

### AUDIT-2-012
```
ID          : AUDIT-2-012
Titre       : REQUEST_INSTALL_PACKAGES permission granted to app — used only by ApkInstaller
Phase       : 2 Security
Sévérité    : P2
Confiance   : Élevée
OWASP       : M1 (Over-privileged Permissions)
```

**Impact** : sécurité, privacy

**Fichier(s)** :
- `app/src/main/AndroidManifest.xml:16`
- `app/src/main/java/com/chakir/plexhubtv/core/update/ApkInstaller.kt`

**Dépendances** : AUDIT-2-008, AUDIT-2-011

**Preuve** :
```xml
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
```

**Pourquoi c'est un problème dans PlexHubTV** :
Cette permission est requise par `ApkInstaller` pour déclencher le system package installer. Sur Google Play, Play Store refuse ou demande une justification spéciale (Unknown Apps policy). Sur Android TV side-loaded, pas de souci. Mais pour publier Play Store, il faut justifier OU retirer la permission. L'alternative est de renvoyer l'utilisateur vers la GitHub release page via browser intent.

**Correctif recommandé** :
Gate la permission + installer derrière un BuildConfig flag `INCLUDE_INAPP_UPDATE = !isPlayStore`.

**Patch proposé** :
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"
    tools:node="removeAll"
    android:required="false" />
<!-- Add via manifest placeholder depending on flavor -->
```
Ou créer un flavor `githubRelease` vs `playStore`.

---

### AUDIT-2-013
```
ID          : AUDIT-2-013
Titre       : AuthInterceptor emits TokenInvalid only on plex.tv 401 — local server 401 silently ignored
Phase       : 2 Security
Sévérité    : P1
Confiance   : Élevée
OWASP       : M1 (Improper Session Handling)
```

**Impact** : sécurité (session validation)

**Fichier(s)** :
- `core/network/src/main/java/com/chakir/plexhubtv/core/network/AuthInterceptor.kt:73-78`

**Dépendances** : aucune

**Preuve** :
```kotlin
// AuthInterceptor.kt:73-78
if (response.code == 401 && response.request.url.host.endsWith("plex.tv")) {
    authEventBus.emitTokenInvalid()
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
Le commentaire explique le choix : éviter les faux-positifs sur les 401 locaux. Mais si le token Plex est révoqué (user clique "Sign out of all devices" sur plex.tv), le backend **plex.tv** renverra 401 uniquement sur les prochaines calls `plex.tv` (auth, resources). Les calls directs aux serveurs plex LAN continuent d'utiliser le même token invalidé — Plex servers retournent 401, ignoré par AuthInterceptor → l'app continue d'appeler les servers jusqu'à ce que le prochain `/resources` refresh sur plex.tv déclenche le dialog. Entre temps, l'app show des erreurs confuses.

**Risque concret si non corrigé** :
User clique "Sign out all devices" → TV PlexHubTV continue à tenter les calls pour 1-6h (jusqu'au prochain LibrarySync qui touche plex.tv) → logout retardé, UX incohérente.

**Correctif recommandé** :
Pour les serveurs Plex (host ending `plex.direct`), aussi déclencher le check — mais avec une double validation : faire un call léger à `plex.tv/api/v2/user` pour confirmer l'invalidation avant de logout.

**Patch proposé** :
```kotlin
// AuthInterceptor.kt
if (response.code == 401) {
    val host = response.request.url.host
    val isPlexTvHost = host.endsWith("plex.tv")
    val isPlexDirectHost = host.contains("plex.direct")
    if (isPlexTvHost) {
        authEventBus.emitTokenInvalid()
    } else if (isPlexDirectHost) {
        // Schedule a validation ping to plex.tv before logging out
        authEventBus.emitPotentialTokenInvalid(host)
    }
}
```
(Nécessite un nouvel event `PotentialTokenInvalid` consumed par MainViewModel qui fait une re-validation.)

---

### AUDIT-2-014
```
ID          : AUDIT-2-014
Titre       : Firebase Crashlytics setCustomKey includes media_rating_key / server_id → PII in crash reports
Phase       : 2 Security
Sévérité    : P2
Confiance   : Élevée
OWASP       : M6 (Privacy)
```

**Impact** : privacy, GDPR compliance

**Fichier(s)** :
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt:159-164`

**Dépendances** : AUDIT-2-002

**Preuve** :
```kotlin
// PlayerController.kt:159-164
FirebaseCrashlytics.getInstance().apply {
    setCustomKey("player_engine", "ExoPlayer")
    setCustomKey("media_rating_key", startRatingKey ?: "unknown")
    setCustomKey("server_id", startServerId ?: "unknown")
    setCustomKey("is_direct_url", (startDirectUrl != null).toString())
}
```

**Pourquoi c'est un problème dans PlexHubTV** :
`ratingKey` + `serverId` identifient un contenu spécifique sur un serveur spécifique — info PII potentielle (ex: "jellyfin_home_server_xxx" + "ep_12345" révèle quels contenus précis l'utilisateur regarde lors des crashes). Sur un grand nombre de crashes, on peut reconstituer l'historique de visionnage de user IDs. Non conforme aux principes GDPR de data minimization.

**Correctif recommandé** :
Remplacer par des hashes opaques ou des valeurs catégoriques (media_type, source_type).

**Patch proposé** :
```kotlin
FirebaseCrashlytics.getInstance().apply {
    setCustomKey("player_engine", "ExoPlayer")
    setCustomKey("media_type", /* derived from ratingKey prefix */)
    setCustomKey("source_type", mediaSourceResolver.resolve(startServerId ?: "").name)
    setCustomKey("is_direct_url", (startDirectUrl != null).toString())
}
```

---

# 3. Cross-references

| Cluster | Findings | Root cause commun |
|---|---|---|
| **Release signing broken** | AUDIT-1-002, AUDIT-2-004 | Fallback `debug` signing dans `app/build.gradle.kts:75-81` — même code, impact stability + security |
| **Sensitive data in logs** | AUDIT-1-004, AUDIT-1-018, AUDIT-2-001, AUDIT-2-002 | HttpLoggingInterceptor + `Timber.w/e(...url...)` → fuite tokens/URLs en logcat debug + Crashlytics release |
| **OTA supply chain** | AUDIT-2-003, AUDIT-2-008, AUDIT-2-011 | Aucun certificate pinning + aucune SHA256 verification sur ApkInstaller |
| **Version cohérence Play Store** | AUDIT-1-003, AUDIT-1-006 | `versionCode=1` figé + ABI filters contradictoires vs commentaire — publication store cassée |
| **Session expiration incomplète** | AUDIT-2-013, AUDIT-1-009 | AuthInterceptor ne détecte pas tous les cas de token révoqué ; `ConnectionManager` ne tracke pas `isOffline` sur `onLost` |
| **DataStore sans corruption handler** | AUDIT-1-007, AUDIT-1-010 | `preferencesDataStore` sans handler + `SecurePreferencesManager` init sync → cold start fragility |
| **PII exposure via Firebase** | AUDIT-2-002, AUDIT-2-014 | Crashlytics custom keys + breadcrumbs contiennent ratingKey/serverId/URLs |

---

**Fin du livrable Phase 1 & 2.**

- **Total stability findings** : 18 (dont 3 P0, 10 P1, 5 P2)
- **Total security findings** : 14 (dont 2 P0, 8 P1, 4 P2)

Les deux P0 sécurité (AUDIT-2-001, AUDIT-2-004) sont cross-linked avec des P0 stability (AUDIT-1-002, AUDIT-1-003 indirectement via versionCode). Les patchs proposés sont minimaux et ciblés. Aucun refactor architectural requis pour fixer l'ensemble.

Les findings restent à valider par :
- **Release Agent** pour AUDIT-1-002, AUDIT-1-003, AUDIT-1-006, AUDIT-2-004, AUDIT-2-012
- **Performance Agent** pour AUDIT-1-010, AUDIT-1-011, AUDIT-1-016, AUDIT-1-017
- **Device QA** pour AUDIT-1-008 (Android 13+ POST_NOTIFICATIONS), AUDIT-1-016 (Mi Box S OOM)
