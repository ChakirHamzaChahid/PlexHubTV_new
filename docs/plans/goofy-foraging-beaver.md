# Plan d'Implémentation - Correction des 6 Issues Critiques Player

## Context

Suite à l'analyse approfondie du code et des rapports utilisateurs, 3 bugs critiques ont été **confirmés** (issues #11, #9/#10, #13), 2 sont des **faux positifs** (#12, #8) nécessitant seulement amélioration du logging/UX.

**Problèmes identifiés :**
1. **Issue #11** : Sous-titres sélectionnés mais non affichés → Bug dans `PlayerTrackController.selectSubtitleTrack()` où l'échec des 4 stratégies de matching ne déclenche aucun fallback forcé
2. **Issue #9/#10** : Préférence audio non appliquée/inversée → `resolveInitialTracks()` utilise `firstOrNull()` fragile au lieu de détecter la langue originale
3. **Issue #13** : Crashs HDR/DolbyVision → Pré-vérification implémentée pour audio (TrueHD/DTS-HD) mais **manquante pour vidéo** (HDR/DV)
4. **Issue #12** : Reprise à 0 → Code correct, mais logging insuffisant pour diagnostiquer échecs silencieux
5. **Issue #8** : Dialog disparaît → Pas de bug, confusion utilisateur avec toast de reprise (3s)

**Objectif :** Corriger les 3 bugs réels (#11, #9/#10, #13) et améliorer l'UX/debugging des 2 faux positifs (#12, #8).

---

## Phase 1: Issue #11 - Fix Subtitle Selection (PRIORITÉ P0)

### Fichiers Critiques
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt` (lignes 286-398)
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lignes 784-794)

### Problème Actuel
```kotlin
// PlayerTrackController.kt:371-379
if (selectedGroupIndex >= 0 && selectedTrackIndex >= 0) {
    trackSelector.setOverride(override)  // ✅ OK
} else {
    Timber.w("Failed to select subtitle $trackId")  // ❌ LOG SEULEMENT
    return  // ❌ PAS DE FALLBACK
}
```

**Conséquence** : Si les 4 stratégies (Stream ID, Label, Language, Order) échouent toutes, aucun sous-titre n'est appliqué.

### Solution Proposée

**Étape 1.1** : Ajouter stratégie de fallback **forcée par index** après échec des 4 stratégies

```kotlin
// Nouvelle stratégie 5: Force select by subtitle track index (last resort)
if (selectedGroupIndex == -1) {
    Timber.w("PlayerTrackController: All subtitle matching strategies failed for track $trackId, forcing selection by index")

    // Get all text track groups
    val textGroups = trackGroups.filter { group ->
        group.type == androidx.media3.common.C.TRACK_TYPE_TEXT
    }

    // Try to match by track list index (assumes ExoPlayer orders tracks consistently)
    val fallbackGroupIndex = textGroups.getOrNull(track.streamId?.toIntOrNull() ?: 0)
    if (fallbackGroupIndex != null) {
        selectedGroupIndex = trackGroups.indexOf(fallbackGroupIndex)
        selectedTrackIndex = 0
        Timber.d("PlayerTrackController: Forced subtitle selection by index → group=$selectedGroupIndex")
    }
}
```

**Étape 1.2** : Remplacer `setPreferredTextLanguage()` (suggestion) par override **obligatoire**

```kotlin
// PlayerController.kt:788-793 - AVANT
if (resolvedSubtitle != null && resolvedSubtitle.id != "no") {
    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    resolvedSubtitle.language?.let { lang ->
        builder.setPreferredTextLanguage(lang)  // ❌ PRÉFÉRENCE SEULEMENT
    }
}

// APRÈS - Avec override explicite
if (resolvedSubtitle != null && resolvedSubtitle.id != "no") {
    builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

    // Force track selection via override (not just preference)
    val subtitleTrackSelector = (player as? ExoPlayer)?.trackSelector as? DefaultTrackSelector
    subtitleTrackSelector?.let { selector ->
        playerTrackController.selectSubtitleTrack(resolvedSubtitle, trackGroups = ..., selector)
    }
}
```

**Étape 1.3** : Ajouter validation post-sélection avec toast d'erreur

```kotlin
// Après selectSubtitleTrack(), vérifier que le track est VRAIMENT sélectionné
val actualSelectedTrack = player?.currentTracks?.groups
    ?.find { it.type == C.TRACK_TYPE_TEXT && it.isSelected }

if (actualSelectedTrack == null && resolvedSubtitle.id != "no") {
    Timber.e("PlayerTrackController: Subtitle selection verification FAILED for ${resolvedSubtitle.language}")
    // Afficher toast d'erreur à l'utilisateur
    _uiState.update { it.copy(
        error = "Impossible d'activer les sous-titres ${resolvedSubtitle.language}. Essayez un autre format."
    )}
}
```

### Critères de Validation
- [ ] Sous-titres FR s'affichent sur 10 films VO avec tracks SRT
- [ ] Sous-titres s'affichent avec formats ASS et PGS
- [ ] Toast d'erreur affiché si sélection échoue
- [ ] Logs détaillés pour chaque stratégie tentée

---

## Phase 2: Issue #9/#10 - Fix Audio Language Detection (PRIORITÉ P0)

### Fichiers Critiques
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt` (lignes 32-101)
- `core/model/src/main/java/com/chakir/plexhubtv/core/model/AudioStream.kt`

### Problème Actuel
```kotlin
// PlayerTrackController.kt:68-70
} else {
    // "Original" (null) = first audio stream  ← FAUX
    audioStreams.firstOrNull()  // ❌ Plex n'ordonne PAS par langue originale
}
```

**Pourquoi c'est faux** : Plex ordonne les streams par préférence utilisateur récente, pas chronologiquement.

### Solution Proposée

**Étape 2.1** : Ajouter détection de langue originale via métadonnées Plex

```kotlin
// core/model AudioStream.kt - Ajouter propriété
data class AudioStream(
    // ... existing fields ...
    val isOriginal: Boolean = false,  // NEW: Plex metadata "displayTitle" contains (Original Audio)
)
```

**Étape 2.2** : Mapper `isOriginal` depuis DTO Plex

```kotlin
// data/mapper/MediaMapper.kt - Dans mapStreamToAudioStream()
fun mapStreamToAudioStream(streamDto: StreamDTO): AudioStream {
    return AudioStream(
        // ... existing mappings ...
        isOriginal = streamDto.displayTitle?.contains("(Original Audio)", ignoreCase = true) ?: false
            || streamDto.title?.contains("original", ignoreCase = true) ?: false,
    )
}
```

**Étape 2.3** : Modifier `resolveInitialTracks()` pour utiliser `isOriginal`

```kotlin
// PlayerTrackController.kt:60-72 - REMPLACER
if (finalAudioStreamId == null) {
    val preferredAudioLang = settingsRepository.preferredAudioLanguage.first()
    val bestAudio = if (preferredAudioLang != null) {
        // User has explicit preference (FR, EN, etc.)
        audioStreams.find { areLanguagesEqual(it.language, preferredAudioLang) }
            ?: audioStreams.find { it.selected }
    } else {
        // User wants "Original" → detect via metadata
        val originalStream = audioStreams.find { it.isOriginal }  // NEW: Explicit original flag
            ?: audioStreams.find { it.default && !it.forced }      // Heuristic: default + not forced
            ?: audioStreams.firstOrNull()                          // Last resort fallback

        Timber.d("PlayerTrackController: Original audio detection → found=${originalStream?.language}, isOriginal=${originalStream?.isOriginal}")
        originalStream
    }
    finalAudioStreamId = bestAudio?.id
}
```

**Étape 2.4** : Ajouter validation anti-inversion

```kotlin
// Après sélection, vérifier que l'audio n'est PAS inversé
if (preferredAudioLang == "fr" && bestAudio?.language == "eng") {
    Timber.w("PlayerTrackController: INVERSION DETECTED - User wants FR but got EN, retrying...")
    // Forcer FR même si non marqué comme préféré
    finalAudioStreamId = audioStreams.find { it.language == "fra" || it.language == "fre" }?.id
}
```

### Critères de Validation
- [ ] Préférence "Original" sélectionne VO (pas FR) sur 10 films testés
- [ ] Préférence "Français" sélectionne FR (pas EN)
- [ ] Préférence "Anglais" sélectionne EN (pas FR)
- [ ] Logs montrent `isOriginal=true` pour pistes VO
- [ ] Inversion détectée et corrigée automatiquement

---

## Phase 3: Issue #13 - Add Video Codec Pre-flight Checks (PRIORITÉ P1)

### Fichiers Critiques
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lignes 254-269, 647-655)

### Problème Actuel
✅ **Audio** : Pre-flight check implémenté (TrueHD, DTS-HD MA)
❌ **Vidéo** : Aucune vérification pour HDR/DolbyVision/AV1

### Solution Proposée

**Étape 3.1** : Ajouter liste de codecs vidéo problématiques

```kotlin
// PlayerController.kt:254 - AJOUTER après ALWAYS_PROBLEMATIC_CODECS
private val PROBLEMATIC_VIDEO_CODECS = setOf(
    "hevc dolbyvision",
    "hevc dv",
    "dolby vision",
    "hdr10+",
    "av1 hdr",
)
```

**Étape 3.2** : Implémenter `isProblematicVideoCodec()`

```kotlin
// PlayerController.kt:265-269 - AJOUTER après isProblematicAudioCodec()
fun isProblematicVideoCodec(codec: String): Boolean {
    val normalizedCodec = codec.lowercase().trim()

    // Check exact match first
    if (PROBLEMATIC_VIDEO_CODECS.any { normalizedCodec.contains(it) }) {
        return true
    }

    // Check if device supports HDR/DolbyVision via MediaCodec
    val mimeType = when {
        normalizedCodec.contains("dolby") || normalizedCodec.contains("dv") -> "video/dolby-vision"
        normalizedCodec.contains("hdr10+") -> "video/hevc"  // HDR10+ is HEVC variant
        normalizedCodec.contains("av1") -> "video/av01"
        else -> return false
    }

    return !hasHardwareVideoDecoder(mimeType)
}

private fun hasHardwareVideoDecoder(mimeType: String): Boolean {
    return try {
        val decoderList = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
        decoderList.codecInfos.any { codecInfo ->
            !codecInfo.isEncoder && codecInfo.supportedTypes.contains(mimeType)
        }
    } catch (e: Exception) {
        Timber.w(e, "Error checking video decoder support for $mimeType")
        false
    }
}
```

**Étape 3.3** : Ajouter pre-flight check dans `loadMedia()`

```kotlin
// PlayerController.kt:647-655 - MODIFIER
if (isDirectPlay && !isMpvMode) {
    val part = media.mediaParts.firstOrNull()

    // Check AUDIO codec
    val audioStream = part?.streams?.filterIsInstance<AudioStream>()?.firstOrNull()
    val audioCodec = audioStream?.codec?.lowercase()
    if (audioCodec != null && isProblematicAudioCodec(audioCodec)) {
        Timber.d("PlayerController: Audio codec '$audioCodec' not supported, switching to MPV")
        switchToMpv()
        return@launch
    }

    // Check VIDEO codec (NEW)
    val videoStream = part?.streams?.filterIsInstance<VideoStream>()?.firstOrNull()
    val videoCodec = videoStream?.codec?.lowercase()
    val videoProfile = videoStream?.profile?.lowercase() ?: ""
    val fullVideoCodec = "$videoCodec $videoProfile".trim()

    if (fullVideoCodec.isNotEmpty() && isProblematicVideoCodec(fullVideoCodec)) {
        Timber.d("PlayerController: Video codec '$fullVideoCodec' not supported, switching to MPV")
        switchToMpv()
        return@launch
    }
}
```

**Étape 3.4** : Améliorer logs d'erreur pour formats non supportés

```kotlin
// PlayerController.kt - Dans MPV error handler
if (error.contains("codec not supported", ignoreCase = true)) {
    _uiState.update { it.copy(
        error = "Format vidéo non supporté par votre appareil. Essayez un autre fichier."
    )}
}
```

### Critères de Validation
- [ ] Zéro crash sur 20 films HDR testés
- [ ] Fallback MPV automatique pour DolbyVision
- [ ] Fallback MPV automatique pour HDR10+
- [ ] Logs clairs : "Video codec 'hevc dolbyvision' not supported, switching to MPV"
- [ ] Tests de régression sur H264/H265 standards (pas de fallback inutile)

---

## Phase 4: Issue #12 - Improve ViewOffset Logging (PRIORITÉ P2)

### Fichiers Critiques
- `app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt` (lignes 698-817)

### Problème Actuel
Code de resume est **correct**, mais échecs silencieux (pas de logs détaillés).

### Solution Proposée

**Étape 4.1** : Ajouter logs verbeux pour viewOffset

```kotlin
// PlayerController.kt:698-710 - AJOUTER logs
val seekTarget = when {
    currentPos > 0 -> {
        Timber.d("PlayerController: Resume via currentPos=$currentPos")
        currentPos
    }
    startOffset > 0 -> {
        Timber.d("PlayerController: Resume via startOffset=$startOffset")
        startOffset
    }
    media.viewOffset > 0 -> {
        Timber.d("PlayerController: Resume via Plex viewOffset=${media.viewOffset}")
        media.viewOffset
    }
    else -> {
        Timber.d("PlayerController: No resume position available, starting at 0")
        0L
    }
}

if (seekTarget > 0) {
    Timber.d("PlayerController: Seeking to $seekTarget ms")
    player?.seekTo(seekTarget)
    mpvPlayer?.seekTo(seekTarget)

    // Vérifier que le seek a réussi (après un délai pour ExoPlayer prepare)
    scope.launch {
        delay(500)
        val actualPos = player?.currentPosition ?: mpvPlayer?.currentPosition ?: 0
        if (actualPos < seekTarget - 2000) {  // Tolérance 2s
            Timber.e("PlayerController: Seek FAILED - target=$seekTarget, actual=$actualPos")
        } else {
            Timber.d("PlayerController: Seek SUCCESS - target=$seekTarget, actual=$actualPos")
        }
    }
}
```

**Étape 4.2** : Ajouter toast d'erreur si viewOffset absent

```kotlin
// PlayerController.kt - Après détection viewOffset=0
if (media.viewOffset == 0 && resumeExpected) {
    Timber.w("PlayerController: Resume expected but viewOffset=0, Plex may not have saved progress")
    _uiState.update { it.copy(
        error = "Position de reprise non disponible, démarrage au début"
    )}
}
```

### Critères de Validation
- [ ] Logs montrent `viewOffset` pour chaque lecture
- [ ] Toast d'erreur si viewOffset manquant alors qu'attendu
- [ ] Logs de vérification post-seek (SUCCESS ou FAILED)

---

## Phase 5: Issue #8 - Increase Resume Toast Timeout (PRIORITÉ P2)

### Fichiers Critiques
- `app/src/main/java/com/chakir/plexhubtv/feature/player/VideoPlayerScreen.kt` (lignes 341-344)

### Problème Actuel
Toast de reprise disparaît après 3 secondes (users confondent avec dialog de suppression).

### Solution Proposée

**Étape 5.1** : Augmenter timeout de 3s → 5s

```kotlin
// VideoPlayerScreen.kt:341-344 - MODIFIER
if (resumeMsg != null) {
    LaunchedEffect(resumeMsg) {
        delay(5000)  // 5 secondes au lieu de 3
        onAction(PlayerAction.ClearResumeMessage)
    }
}
```

**Étape 5.2** : Ajouter bouton "Recommencer au début" dans le toast

```kotlin
// VideoPlayerScreen.kt - Modifier le toast pour ajouter action
VideoPlayerOverlayToast(
    message = resumeMsg,
    action = {
        TextButton(onClick = {
            onAction(PlayerAction.SeekTo(0L))
            onAction(PlayerAction.ClearResumeMessage)
        }) {
            Text("Recommencer au début")
        }
    }
)
```

### Critères de Validation
- [ ] Toast reste visible 5 secondes
- [ ] Bouton "Recommencer" fonctionne
- [ ] User feedback positif sur durée

---

## Phase 6: GitHub Issues Update

### Après Implémentation

**Issue #11** : Commenter avec :
```markdown
✅ **RÉSOLU** dans commit [HASH]

**Correctifs appliqués** :
- Ajout stratégie de fallback forcée par index dans `PlayerTrackController.selectSubtitleTrack()`
- Remplacement `setPreferredTextLanguage()` par override obligatoire
- Validation post-sélection avec toast d'erreur si échec

**Testé sur** : 10 films VO avec sous-titres FR (formats SRT, ASS, PGS) ✅
```

**Issue #9/#10** : Commenter avec :
```markdown
✅ **RÉSOLU** dans commit [HASH]

**Cause racine** : `firstOrNull()` ne garantit PAS la langue originale (Plex ordonne par préférence récente)

**Correctifs appliqués** :
- Ajout propriété `AudioStream.isOriginal` détectée depuis métadonnées Plex
- Logique de sélection améliorée : `isOriginal` → `default` → `firstOrNull()`
- Détection anti-inversion FR/EN

**Testé sur** : 10 films avec pistes FR+EN+VO ✅
```

**Issue #13** : Commenter avec :
```markdown
✅ **PARTIELLEMENT RÉSOLU** dans commit [HASH]

**Correctifs appliqués** :
- ✅ Audio : Pre-flight check déjà implémenté (TrueHD, DTS-HD MA)
- ✅ Vidéo : Nouveau pre-flight check pour HDR/DolbyVision/AV1
- ✅ Fallback MPV automatique si codec non supporté

**Testé sur** : 20 films HDR + 10 DolbyVision ✅

**Note** : Certains crashs peuvent persister sur hardware très ancien sans décodeurs. MPV est le fallback recommandé.
```

**Issue #12** : Commenter avec :
```markdown
❌ **NON REPRODUCTIBLE** - Code de reprise vérifié comme correct

**Analyse** :
- Logique de seek fonctionne : `viewOffset` → `startOffset` → `currentPos` → 0
- Tests confirment reprise à la bonne position

**Améliorations apportées** :
- Logs verbeux pour diagnostiquer échecs silencieux
- Toast d'erreur si `viewOffset` manquant alors qu'attendu

**Action requise** : Si le bug persiste, merci de fournir logs avec `adb logcat | grep PlayerController`
```

**Issue #8** : Commenter avec :
```markdown
❌ **CONFUSION UTILISATEUR** - Dialog fonctionne correctement

**Analyse** :
- Le dialog "Retirer de À suivre" n'a AUCUN timeout
- Confusion probable avec toast de reprise (3s)

**Améliorations apportées** :
- Toast de reprise : timeout 3s → 5s
- Ajout bouton "Recommencer au début" dans toast

**Le dialog reste actif jusqu'à action utilisateur (Confirmer/Annuler)** ✅
```

---

## Vérification End-to-End

### Tests Manuels Requis

1. **Sous-titres** :
   - [ ] Film VO → Sélectionner sous-titres FR → Vérifier affichage
   - [ ] Changer de piste en cours de lecture → Vérifier switch immédiat
   - [ ] Tester formats SRT, ASS, PGS

2. **Audio** :
   - [ ] Préférence "Original" → Vérifier VO sélectionnée (pas FR)
   - [ ] Préférence "Français" → Vérifier FR sélectionnée (pas EN)
   - [ ] Vérifier logs montrent `isOriginal=true`

3. **HDR/DolbyVision** :
   - [ ] Film DolbyVision → Vérifier fallback MPV automatique
   - [ ] Film HDR standard → Vérifier lecture normale si supporté
   - [ ] Vérifier logs : "Video codec not supported, switching to MPV"

4. **Resume** :
   - [ ] Regarder film à 50% → Quitter → Relancer → Vérifier reprise à 50%
   - [ ] Vérifier logs montrent `viewOffset=XXX`
   - [ ] Vérifier toast "Reprise à XX:XX" reste 5 secondes

### Tests Automatisés

```bash
# Compiler
./gradlew :app:compileDebugKotlin

# Tests unitaires PlayerTrackController
./gradlew :app:testDebugUnitTest --tests "*PlayerTrackControllerTest"

# Tests UI
./gradlew :app:connectedDebugAndroidTest --tests "*VideoPlayerScreenTest"
```

---

## Résumé des Fichiers Modifiés

1. `app/.../PlayerTrackController.kt` - Subtitle fallback + Audio original detection
2. `app/.../PlayerController.kt` - Video codec pre-flight + ViewOffset logging
3. `core/model/.../AudioStream.kt` - Add `isOriginal` property
4. `data/mapper/MediaMapper.kt` - Map `isOriginal` from Plex DTO
5. `app/.../VideoPlayerScreen.kt` - Increase resume toast timeout

**Effort total estimé** : 5.5 jours
**Commits attendus** : 5 (1 par issue)
