# Design: Trailers & Extras Feature

## Context

PlexHubTV's detail screen currently shows Episodes, More Like This, and Collections tabs. Trailers are already planned (`DetailTab.Trailers` is commented out in `NetflixDetailTabs.kt`), and the Plex API call in `PlexClient.getMetadata()` already includes `includeExtras=1` — the server is returning extras data, but it's being silently discarded by the DTO/mapper layers.

## Approach: Parse existing API data + new "TRAILERS & MORE" tab

**No new API endpoint needed.** The `/library/metadata/{id}?includeExtras=1` query is already sent. We just need to:
1. Add `Extras` field to DTOs to parse the JSON
2. Add domain model for extras
3. Map DTO → Domain in MediaMapper
4. Thread extras through the data flow (Repository → UseCase → ViewModel → UI)
5. Uncomment and implement the "TRAILERS & MORE" tab in the detail screen

## Plex API — Extras Response Format

When `includeExtras=1` is set, the MetadataDTO response includes an `Extras` field containing a `MediaContainer`-like wrapper with `Metadata` items. Each extra item has:
- `ratingKey` — unique key for the extra clip
- `key` — API path to stream the extra (e.g., `/library/metadata/{ratingKey}`)
- `title` — title of the extra (e.g., "Official Trailer", "Behind the Scenes")
- `type` — always `"clip"`
- `subtype` — categorizes the extra: `trailer`, `behindTheScenes`, `sceneOrSample`, `deletedScene`, `interview`, `featurette`, etc.
- `thumb` — poster/thumbnail for the clip
- `duration` — duration in milliseconds
- `media` / `Part` — playback info (same structure as regular media)
- `year`, `originallyAvailableAt`, etc.

The extras are NOT local media files — they're typically hosted on Plex's CDN or linked from online sources (YouTube trailers via Plex's metadata agents).

## Architecture

### Layer 1: Network DTOs (`core/network`)

**File: `PlexResponse.kt`**
```kotlin
// Add to MetadataDTO:
@SerializedName("Extras") val extras: ExtrasContainerDTO? = null,

// New DTOs:
data class ExtrasContainerDTO(
    val size: Int = 0,
    @SerializedName("Metadata") val metadata: List<MetadataDTO>? = null,
)
```

The extras items reuse the existing `MetadataDTO` structure — they have the same fields (ratingKey, key, title, type, thumb, media/Part, etc.) plus a `subtype` field.

**Also add to MetadataDTO:**
```kotlin
val subtype: String? = null, // trailer, behindTheScenes, sceneOrSample, etc.
```

### Layer 2: Domain Model (`core/model`)

**File: `MediaItem.kt`** — Add new data class:
```kotlin
data class Extra(
    val ratingKey: String,
    val title: String,
    val subtype: ExtraType,
    val thumbUrl: String? = null,
    val durationMs: Long? = null,
    val year: Int? = null,
    val playbackKey: String? = null, // API path for streaming
    val mediaParts: List<MediaPart> = emptyList(),
    val baseUrl: String? = null,
    val accessToken: String? = null,
)

enum class ExtraType {
    Trailer,
    BehindTheScenes,
    SceneOrSample,
    DeletedScene,
    Interview,
    Featurette,
    Unknown;

    companion object {
        fun fromPlex(subtype: String?): ExtraType = when (subtype) {
            "trailer" -> Trailer
            "behindTheScenes" -> BehindTheScenes
            "sceneOrSample" -> SceneOrSample
            "deletedScene" -> DeletedScene
            "interview" -> Interview
            "featurette" -> Featurette
            else -> Unknown
        }
    }
}
```

**Also add to `MediaItem`:**
```kotlin
val extras: List<Extra> = emptyList(),
```

### Layer 3: Mapper (`data/mapper`)

**File: `MediaMapper.kt`** — Add extras mapping in `mapDtoToDomain()`:
```kotlin
// In mapDtoToDomain, add to MediaItem constructor:
extras = dto.extras?.metadata?.map { extraDto ->
    Extra(
        ratingKey = extraDto.ratingKey,
        title = extraDto.title,
        subtype = ExtraType.fromPlex(extraDto.subtype),
        thumbUrl = extraDto.thumb?.let { "$baseUrl$it?X-Plex-Token=$accessToken" },
        durationMs = extraDto.duration,
        year = extraDto.year,
        playbackKey = extraDto.key,
        mediaParts = /* map parts same as regular media */,
        baseUrl = baseUrl,
        accessToken = accessToken,
    )
} ?: emptyList(),
```

### Layer 4: UI State

**File: `MediaDetailUiState.kt`** — No changes needed. Extras are already part of `MediaItem.extras`, available via `state.media?.extras`.

### Layer 5: Detail Screen UI

**File: `NetflixDetailTabs.kt`** — Uncomment and enable the tab:
```kotlin
enum class DetailTab(val title: String) {
    Episodes("EPISODES"),
    MoreLikeThis("MORE LIKE THIS"),
    Collections("COLLECTIONS"),
    Trailers("TRAILERS & MORE"), // Uncomment
}

// In NetflixDetailTabs composable, add condition:
val tabs = buildList {
    if (showEpisodes) add(DetailTab.Episodes)
    add(DetailTab.MoreLikeThis)
    if (showCollections) add(DetailTab.Collections)
    if (showTrailers) add(DetailTab.Trailers) // New
}
```

**File: `NetflixDetailScreen.kt`** — Add tab content:
```kotlin
DetailTab.Trailers -> {
    val extras = media.extras
    if (extras.isNotEmpty()) {
        item(key = "detail_trailers_row") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(end = 50.dp),
                modifier = Modifier.focusGroup()
            ) {
                items(extras, key = { it.ratingKey }) { extra ->
                    ExtraCard(
                        extra = extra,
                        onClick = { onAction(MediaDetailEvent.PlayExtra(extra)) },
                    )
                }
            }
        }
    } else {
        item(key = "detail_no_trailers") {
            Text("No trailers available", color = NetflixLightGray)
        }
    }
}
```

**New Composable: `ExtraCard`** (in `NetflixDetailScreen.kt`):
- Landscape card (16:9 aspect ratio, ~280dp wide)
- Thumbnail with play icon overlay
- Title below thumbnail
- Duration badge (e.g., "2:34")
- Subtype label (e.g., "Trailer", "Behind the Scenes")
- Focus animation (scale + border) matching existing `CollectionCard` pattern

### Layer 6: Playback — Two Approaches

**Approach A (Recommended): In-app playback via existing player**
- Extras have `Media` → `Part` → `key` fields, same as regular media
- Create `MediaItem` from `Extra` data and play through `PlaybackManager`
- Reuses entire player infrastructure (MPV, controls, scrobbling skipped for extras)
- New event: `MediaDetailEvent.PlayExtra(extra: Extra)` handled in ViewModel

**Approach B: Direct URL playback (fallback)**
- Some extras (especially online trailers) may have a direct HTTP URL in `Part.key`
- If `Part.key` starts with `http`, play directly without transcode
- If it starts with `/`, use normal `PlexClient.getPlaybackUrl()` path

**ViewModel handling:**
```kotlin
is MediaDetailEvent.PlayExtra -> {
    val extra = event.extra
    val media = _uiState.value.media ?: return
    // Build a lightweight MediaItem from the extra for playback
    val extraItem = MediaItem(
        id = "extra_${extra.ratingKey}",
        ratingKey = extra.ratingKey,
        serverId = media.serverId,
        title = extra.title,
        type = MediaType.Clip,
        thumbUrl = extra.thumbUrl,
        durationMs = extra.durationMs,
        mediaParts = extra.mediaParts,
        baseUrl = extra.baseUrl,
        accessToken = extra.accessToken,
    )
    playbackManager.play(extraItem, listOf(extraItem))
    _navigationEvents.send(NavigateToPlayer(extraItem.ratingKey, extraItem.serverId))
}
```

## String Resources

Existing strings already available:
- `watch_trailer_1`: "Watch trailer" / "Regarder la bande-annonce"
- `watch_trailer_2`: "FREE" / "GRATUIT"

New strings needed:
- `tab_trailers`: "TRAILERS & MORE" / "BANDES-ANNONCES"
- `extra_type_trailer`: "Trailer" / "Bande-annonce"
- `extra_type_behind_the_scenes`: "Behind the Scenes" / "Making-of"
- `extra_type_scene_sample`: "Scene" / "Extrait"
- `extra_type_deleted_scene`: "Deleted Scene" / "Scène coupée"
- `extra_type_interview`: "Interview" / "Interview"
- `extra_type_featurette`: "Featurette" / "Featurette"
- `no_trailers_available`: "No trailers available" / "Aucune bande-annonce disponible"

## Files to Modify (Summary)

| File | Change |
|------|--------|
| `core/network/.../PlexResponse.kt` | Add `ExtrasContainerDTO`, `subtype` field to `MetadataDTO` |
| `core/model/.../MediaItem.kt` | Add `Extra` data class, `ExtraType` enum, `extras` field to `MediaItem` |
| `data/mapper/MediaMapper.kt` | Map extras DTO → domain in `mapDtoToDomain()` |
| `feature/details/NetflixDetailTabs.kt` | Uncomment `Trailers` tab, add `showTrailers` param |
| `feature/details/NetflixDetailScreen.kt` | Add `Trailers` tab content, `ExtraCard` composable |
| `feature/details/MediaDetailUiState.kt` | Add `PlayExtra` event |
| `feature/details/MediaDetailViewModel.kt` | Handle `PlayExtra` event |
| `app/src/main/res/values/strings.xml` | Add trailer/extras strings |
| `app/src/main/res/values-fr/strings.xml` | Add French translations |
| `core/ui/src/main/res/values/strings.xml` | Add trailer/extras strings |
| `core/ui/src/main/res/values-fr/strings.xml` | Add French translations |

## What This Does NOT Include

- **No Room/DB storage for extras** — extras are transient, fetched with metadata, no need to cache
- **No separate API call** — extras come embedded in the existing metadata response
- **No auto-play of trailers** — user must explicitly click to play
- **No YouTube integration** — only Plex-hosted extras (Plex handles YouTube proxying server-side)

## Verification

1. Build: `./gradlew :app:compileDebugKotlin`
2. Navigate to a movie/show detail that has extras (most popular movies do)
3. Verify "TRAILERS & MORE" tab appears
4. Verify extras display with thumbnails, titles, subtypes, durations
5. Click a trailer → verify it plays in the existing player
6. Verify tab doesn't show when media has no extras
7. Test with both movies and shows
