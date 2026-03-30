# Feature: Metadata Language Preference (FR/EN)

## Context
Les appels TMDB (detail acteur, mise a jour unitaire d'un media) renvoient les metadonnees en anglais par defaut. L'utilisateur veut pouvoir choisir entre francais et anglais pour ces appels. L'API TMDB supporte un parametre `language` (ex: `fr-FR`, `en-US`).

**Scope**: Uniquement les appels TMDB (person detail + refresh metadata). PAS les appels Plex (qui retournent les metadonnees dans la langue configuree cote serveur).

---

## Implementation (6 etapes)

### Step 1: DataStore — nouvelle preference `metadataLanguage`
**Fichier:** [SettingsDataStore.kt](core/datastore/src/main/java/com/chakir/plexhubtv/core/datastore/SettingsDataStore.kt)

Ajouter apres `PREF_SUBTITLE_LANG` (~ligne 513):
```kotlin
private val PREF_METADATA_LANG = stringPreferencesKey("pref_metadata_lang")

val metadataLanguage: Flow<String> =
    dataStore.data.map { it[PREF_METADATA_LANG] ?: "fr" }

suspend fun saveMetadataLanguage(lang: String) {
    dataStore.edit { it[PREF_METADATA_LANG] = lang }
}
```
Default: `"fr"` (francais).

### Step 2: SettingsRepository — exposer la preference
**Fichier:** [SettingsRepository.kt](domain/src/main/java/com/chakir/plexhubtv/domain/repository/SettingsRepository.kt)

Ajouter dans l'interface (~apres `preferredSubtitleLanguage`):
```kotlin
val metadataLanguage: Flow<String> // "fr" or "en"
suspend fun setMetadataLanguage(lang: String)
```

**Fichier:** [SettingsRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/SettingsRepositoryImpl.kt)

Ajouter l'implementation:
```kotlin
override val metadataLanguage: Flow<String> = settingsDataStore.metadataLanguage
override suspend fun setMetadataLanguage(lang: String) { settingsDataStore.saveMetadataLanguage(lang) }
```

### Step 3: Settings UI — ajouter le picker
**Fichier:** [SettingsUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsUiState.kt)

- Ajouter `val metadataLanguage: String = "fr"` dans `SettingsUiState`
- Ajouter `data class ChangeMetadataLanguage(val language: String) : SettingsAction`

**Fichier:** [SettingsViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/SettingsViewModel.kt)

- Collecter `settingsRepository.metadataLanguage` dans l'init
- Handler `ChangeMetadataLanguage` → `settingsRepository.setMetadataLanguage(action.language)`

**Fichier:** [GeneralSettingsScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/settings/categories/GeneralSettingsScreen.kt)

Ajouter une section "Language" apres "Appearance" avec un `SettingsTile` + dialog:
```
Section: "Language"
  Tile: "Metadata Language" / subtitle: "Francais" ou "English"
  Dialog: 2 options — "Francais" (fr), "English" (en)
```

### Step 4: TmdbApiService — ajouter parametre `language`
**Fichier:** [TmdbApiService.kt](core/network/src/main/java/com/chakir/plexhubtv/core/network/TmdbApiService.kt)

Ajouter `@Query("language") language: String? = null` a chaque endpoint:
- `getTvDetails()` — utilise lors du refresh metadata
- `getMovieDetails()` — utilise lors du refresh metadata
- `searchPerson()` — utilise pour chercher l'acteur
- `getPersonDetails()` — utilise pour le detail acteur (biography, credits)

### Step 5: PersonDetailViewModel — passer la langue
**Fichier:** [PersonDetailViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/details/PersonDetailViewModel.kt)

- Injecter `SettingsRepository` dans le constructeur
- Dans `loadPerson()`, lire `settingsRepository.metadataLanguage.first()` pour obtenir la langue
- Convertir `"fr"` → `"fr-FR"`, `"en"` → `"en-US"`
- Passer aux appels `searchPerson(apiKey, personName, language)` et `getPersonDetails(id, apiKey, language = language)`

### Step 6: MediaDetailRepositoryImpl — passer la langue au refresh
**Fichier:** [MediaDetailRepositoryImpl.kt](data/src/main/java/com/chakir/plexhubtv/data/repository/MediaDetailRepositoryImpl.kt)

- Injecter `SettingsDataStore` (ou `SettingsRepository`) dans le constructeur
- Dans `refreshMetadataFromTmdb()` (~ligne 398), lire `settingsDataStore.metadataLanguage.first()`
- Convertir en locale TMDB et passer aux appels:
  - `tmdbApiService.getMovieDetails(tmdbId, tmdbKey, language = tmdbLang)`
  - `tmdbApiService.getTvDetails(tmdbId, tmdbKey, language = tmdbLang)`

---

## Fichiers modifies (resume)

| Fichier | Changement |
|---|---|
| `SettingsDataStore.kt` | +preference `metadataLanguage` |
| `SettingsRepository.kt` | +interface `metadataLanguage` / `setMetadataLanguage` |
| `SettingsRepositoryImpl.kt` | +implementation |
| `SettingsUiState.kt` | +`metadataLanguage` state + `ChangeMetadataLanguage` action |
| `SettingsViewModel.kt` | +collect + handler |
| `GeneralSettingsScreen.kt` | +section Language + dialog |
| `TmdbApiService.kt` | +`language` param sur 4 endpoints |
| `PersonDetailViewModel.kt` | +inject SettingsRepository, passer langue aux appels TMDB |
| `MediaDetailRepositoryImpl.kt` | +inject SettingsDataStore, passer langue au refresh |
| `strings.xml` | +3 nouvelles string resources |

---

## Verification

### Tests manuels
1. Settings → General → Language → choisir "Francais" → ouvrir un acteur → biography en FR, filmographie en FR
2. Settings → choisir "English" → ouvrir le meme acteur → biography en EN
3. Detail d'un film → refresh metadata → overview + titre rafraichis dans la langue choisie
4. Verifier que le changement de langue est persiste apres redemarrage de l'app
