# Plan: Person Credit Click → Navigate to Local Media Detail

## Context

The person detail screen (`PersonDetailScreen.kt`) displays an actor's filmography from TMDB. Currently, `CreditCard` composables are **focusable but not clickable** — D-pad focus works, but pressing Select/Enter does nothing.

Goal: when a credit is clicked, look up the TMDB ID in the local Plex library. If found, navigate to `MediaDetail` using `ratingKey + serverId`. If not found, silently ignore.

For multi-server duplicates, pick the row with the highest `metadataScore` (same winner logic as unified library queries).

## Files to Modify

| # | File | Change |
|---|------|--------|
| 1 | `core/database/.../MediaDao.kt` | Add `findRefByTmdbId()` returning lightweight `MediaRef` |
| 2 | `core/database/.../MediaDao.kt` | Add `MediaRef` data class (ratingKey + serverId only) |
| 3 | `domain/.../MediaDetailRepository.kt` | Add `findLocalMediaRefByTmdbId()` interface method |
| 4 | `data/.../MediaDetailRepositoryImpl.kt` | Implement it (type mapping + DAO call) |
| 5 | `feature/details/PersonDetailViewModel.kt` | Inject repo, add `onCreditClicked()`, add navigation SharedFlow |
| 6 | `feature/details/PersonDetailScreen.kt` | Replace `focusable` with `clickable` on `CreditCard`, thread callbacks |
| 7 | `app/.../MainActivity.kt` | Wire `onNavigateToDetail` in PersonDetail composable route |

## Implementation Steps

### Step 1 — Lightweight DAO query (`MediaDao.kt`)

Add a minimal data class and query that returns ONLY what navigation needs:

```kotlin
data class MediaRef(
    val ratingKey: String,
    val serverId: String,
)

@Query("""
    SELECT ratingKey, serverId FROM media
    WHERE tmdbId = :tmdbId AND type = :type AND isHidden = 0
    ORDER BY metadataScore DESC
    LIMIT 1
""")
suspend fun findRefByTmdbId(tmdbId: String, type: String): MediaRef?
```

- Uses existing composite index `(type, tmdbId)` — fast
- `metadataScore DESC` picks best server instance (same as unified queries)
- Returns 2 fields only — no mapper, no overhead on Mi Box S

### Step 2 — Repository interface (`MediaDetailRepository.kt`)

```kotlin
suspend fun findLocalMediaRefByTmdbId(tmdbId: Int, mediaType: String): MediaRef?
```

Returns `MediaRef` from the DAO module directly (domain layer can reference database types for simple value objects, or we re-export `MediaRef` — depends on module boundaries). If module boundary is strict, define a parallel `Pair<String, String>` or a domain-level `MediaNavTarget(ratingKey, serverId)`.

### Step 3 — Repository implementation (`MediaDetailRepositoryImpl.kt`)

```kotlin
override suspend fun findLocalMediaRefByTmdbId(tmdbId: Int, mediaType: String): MediaRef? {
    val type = if (mediaType == "tv") "show" else mediaType
    return mediaDao.findRefByTmdbId(tmdbId.toString(), type)
}
```

No mapping, no URL resolution — just type conversion and delegation.

### Step 4 — ViewModel (`PersonDetailViewModel.kt`)

**4a.** Inject `MediaDetailRepository`:
```kotlin
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tmdbApiService: TmdbApiService,
    private val apiKeyManager: ApiKeyManager,
    private val personFavoriteRepository: PersonFavoriteRepository,
    private val settingsRepository: SettingsRepository,
    private val mediaDetailRepository: MediaDetailRepository,  // NEW
) : ViewModel()
```

**4b.** Add navigation sealed class + SharedFlow (follows same pattern as `MediaDetailViewModel`, `HomeViewModel`, `HubViewModel`, `SearchViewModel`):
```kotlin
sealed interface PersonDetailNavigationEvent {
    data class NavigateToMediaDetail(val ratingKey: String, val serverId: String) : PersonDetailNavigationEvent
}

private val _navigationEvents = MutableSharedFlow<PersonDetailNavigationEvent>()
val navigationEvents = _navigationEvents.asSharedFlow()
```

**4c.** Add click handler:
```kotlin
fun onCreditClicked(credit: PersonCredit) {
    if (credit.id <= 0) return
    viewModelScope.launch {
        val ref = mediaDetailRepository.findLocalMediaRefByTmdbId(credit.id, credit.mediaType)
        if (ref != null) {
            _navigationEvents.emit(
                PersonDetailNavigationEvent.NavigateToMediaDetail(ref.ratingKey, ref.serverId)
            )
        }
    }
}
```

### Step 5 — Screen UI (`PersonDetailScreen.kt`)

**5a.** Update `PersonDetailRoute` signature and collect navigation events:
```kotlin
@Composable
fun PersonDetailRoute(
    viewModel: PersonDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (ratingKey: String, serverId: String) -> Unit,  // NEW
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is PersonDetailNavigationEvent.NavigateToMediaDetail ->
                    onNavigateToDetail(event.ratingKey, event.serverId)
            }
        }
    }
    // ... rest unchanged, but pass viewModel::onCreditClicked down
}
```

**5b.** Thread `onCreditClick` through `PersonDetailScreen` → `CreditRow` → `CreditCard`.

**5c.** Fix `CreditCard` click handling for Android TV (**critical**):

Replace the current `focusable` with `clickable` using the **same `interactionSource`** — this is the established TV pattern (`SettingsCategoryCard`, `AppProfileSelectionScreen`):

```kotlin
// BEFORE (broken — focusable but not clickable):
.focusable(interactionSource = interactionSource)
.scale(if (isFocused) 1.05f else 1f)

// AFTER (correct TV pattern — single modifier handles both focus + click):
.scale(if (isFocused) 1.05f else 1f)
.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = { onCreditClick(credit) },
)
```

Do NOT add `.clickable` alongside `.focusable` — that creates two focusable nodes and the D-pad will "double-stop" on the card.

### Step 6 — Navigation wiring (`MainActivity.kt` ~line 344)

```kotlin
PersonDetailRoute(
    onNavigateBack = { navController.popBackStack() },
    onNavigateToDetail = { ratingKey, serverId ->
        navController.navigate(Screen.MediaDetail.createRoute(ratingKey, serverId))
    },
)
```

## Module boundary note

`MediaRef` is defined in `core/database`. If the `domain` module cannot depend on `core/database` types, define a simple `data class MediaNavTarget(val ratingKey: String, val serverId: String)` in `domain/model/` and map in the repository impl. Check existing imports in `MediaDetailRepository.kt` to see which pattern is used.

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Double focus node if `.clickable` added alongside `.focusable` | **HIGH** | Replace `.focusable` with `.clickable` (same interactionSource) |
| TMDB `"tv"` vs local `"show"` type mismatch | Medium | Explicit mapping in repository |
| `PersonCredit.id` = 0 for rare TMDB entries | Low | Guard: `if (credit.id <= 0) return` |
| Module boundary: domain can't reference database types | Medium | Define `MediaNavTarget` in domain layer if needed |

## Verification

1. **D-pad navigation**: Focus a `CreditCard` with D-pad → visual feedback (scale + border). Press Select → navigates if in library, nothing if not.
2. **Match found**: Actor with a movie in library → click → MediaDetail opens.
3. **No match**: Click credit not in library → no crash, no navigation.
4. **TV show**: Click TV credit → `"tv"` → `"show"` mapping works.
5. **Multi-server**: Same movie on 2 servers → navigates to highest `metadataScore`.
6. **Hidden media**: Hidden item → credit click does nothing.
7. **No double-focus**: D-pad through credit row → each card receives focus exactly once (no "stuck" focus).
