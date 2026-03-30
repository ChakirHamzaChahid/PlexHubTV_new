# Plan: Fix Audio/Subtitle Track Selection Regression

## Context

The "VO/English audio + French subtitles" combo doesn't work reliably. Root cause: **language format mismatch** at multiple levels in the track selection pipeline.

- **Settings** store ISO 639-3 codes: `"eng"`, `"fra"`, `"deu"` (from `PlaybackSettingsScreen`)
- **Plex API** returns `language = "English"` (full name) + `languageCode = "eng"` (ISO code)
- **`resolveInitialTracks()`** compares settings against `stream.language` (full name), ignoring `stream.languageCode` (ISO)
- **`areLanguagesEqual("English", "eng")`** fails: `Locale("english").getISO3Language()` throws `MissingResourceException`, falls through to `"english" == "eng"` which is false
- **ExoPlayer** receives `setPreferredAudioLanguage("English")` instead of `"eng"` — expects ISO/BCP47 codes
- **French B/T variants**: Plex sometimes returns `languageCode = "fre"` (bibliographic) while settings store `"fra"` (terminology) — `areLanguagesEqual` doesn't bridge them

## Critical Review Findings

### Issue found in existing tests
The test `falls back to Plex defaults (selected flag) if no pref` (line 117) **expects `result.second = "s2"`** (French forced sub), but tracing the code path:
- `preferredSubtitleLanguage = null` → smart device-locale matching
- `chosenAudio.language = "en"`, `deviceLang = Locale.getDefault().language` (= `"en"` on JVM)
- `audioMatchesDevice = true` → returns `null` → `finalSubtitleStreamId = null`

This test assertion appears incorrect — it expects the Plex `selected` flag to be used for subtitles, but the code uses smart locale matching instead. Must verify and fix.

### Test data is unrealistic
All test streams use `language = "en"/"fr"` (2-letter ISO codes), but real Plex sends `language = "English"/"French"` (full names). The tests pass trivially via string equality and don't catch the real bug.

### Unnecessary complexity concern
Adding `languageCode` to UI models is strictly needed because `syncTracksWithExoPlayer()`, `selectAudioTrack()`, and `selectSubtitleTrack()` operate on `AudioTrack`/`SubtitleTrack` (not `AudioStream`). No simpler alternative exists.

However, `resolveInitialTracks()` operates on `AudioStream` which already has `languageCode` — Steps 1-2 are NOT prerequisites for Step 4. They're only needed for Steps 5-7.

---

## Files to Modify

| File | Changes |
|------|---------|
| [PlayerTracks.kt](core/model/src/main/java/com/chakir/plexhubtv/core/model/PlayerTracks.kt) | Add `languageCode` field to `AudioTrack` + `SubtitleTrack` |
| [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) | Fix matching logic, improve `areLanguagesEqual`, populate `languageCode`, add logs |
| [PlayerController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt) | Fix `setPreferredAudioLanguage`/`setPreferredTextLanguage` to use ISO codes |
| [PlayerTrackControllerTest.kt](app/src/test/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackControllerTest.kt) | Fix unrealistic test data, fix broken assertion, add B/T tests |

---

## Implementation Steps

### Step 1: Improve `areLanguagesEqual` with ISO 639-2 B/T mapping
**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) lines 530-558

Replace current implementation:
- Add private `ISO_639_2_BT_MAP` constant (B→T mapping: `"fre"→"fra"`, `"ger"→"deu"`, etc., ~20 entries)
- Add `normalizeIso639()` helper: returns canonical T-form code
- Fast path: `normalizeIso639(a) == normalizeIso639(b)` before Locale fallback
- Remove broken double-catch that retries the same `Locale("english")` call
- Separate `toLocale()` helper: 2-letter → `Locale(lang)`, 3-letter → `Locale.forLanguageTag(lang)`, else → `Locale(lang)`
- Keep `companion object` scope (not worth extracting to a utility class for 1 usage site)

### Step 2: Add `languageCode` to UI track models
**File:** [PlayerTracks.kt](core/model/src/main/java/com/chakir/plexhubtv/core/model/PlayerTracks.kt)
- Add `val languageCode: String? = null` to `AudioTrack` (after `language` field)
- Add `val languageCode: String? = null` to `SubtitleTrack` (after `language` field)
- Update `SubtitleTrack.OFF` companion to include `languageCode = null`

### Step 3: Populate `languageCode` in `populateTracks()`
**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) lines 452-481
- Add `languageCode = stream.languageCode` to both `AudioTrack()` and `SubtitleTrack()` constructors

### Step 4: Fix `resolveInitialTracks()` — use `languageCode` for matching
**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) lines 60-114

This method operates on `AudioStream`/`SubtitleStream` which already have `languageCode`.

- **Audio** (line 65): `audioStreams.find { areLanguagesEqual(it.languageCode, pref) } ?: audioStreams.find { areLanguagesEqual(it.language, pref) }`
- **Subtitle** (lines 93-94): same `languageCode`-first pattern with `!it.forced` priority
- **Device-locale auto-sub** (lines 101-107): check `chosenAudio.languageCode` then `.language`
- **Inversion log** (line 80): check both fields

### Step 5: Fix ExoPlayer language hints — pass ISO codes
**File:** [PlayerController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerController.kt) lines 1052-1060
- `setPreferredAudioLanguage(resolvedAudio?.languageCode ?: resolvedAudio?.language)`
- `setPreferredTextLanguage(resolvedSubtitle.languageCode ?: resolvedSubtitle.language)`

**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) lines 231, 441
- Transcoding `selectAudioTrack`: `track.languageCode ?: track.language`
- Transcoding `selectSubtitleTrack`: same

### Step 6: Fix `syncTracksWithExoPlayer` + `selectAudioTrack`/`selectSubtitleTrack` matching
**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt)
- `syncTracksWithExoPlayer` (lines 509-521): try `uiTrack.languageCode` first, fallback to `uiTrack.language`
- `selectAudioTrack` Strategy 1 (line 193): `areLanguagesEqual(format.language, track.languageCode) || areLanguagesEqual(format.language, track.language)`
- `selectSubtitleTrack` Strategy 3 (line 364): same

### Step 7: Add diagnostic logging
**File:** [PlayerTrackController.kt](app/src/main/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackController.kt) in `resolveInitialTracks()`
- At entry: enumerate all audio streams `[id] lang='X' code='Y' codec=Z ch=N selected=B original=B`
- At entry: enumerate all subtitle streams `[id] lang='X' code='Y' codec=Z forced=B selected=B ext=B`
- Log settings values: `preferredAudio='eng', preferredSub='fra'`
- Log which resolution level matched: `LEVEL=args|db|settings|smart-default`
- At exit: log final choice `audio=a1(eng), sub=s2(fra)`

### Step 8: Fix and extend tests
**File:** [PlayerTrackControllerTest.kt](app/src/test/java/com/chakir/plexhubtv/feature/player/controller/PlayerTrackControllerTest.kt)

**Fix existing test data** (lines 30-38):
- Change `language = "en"` → `language = "English"` (realistic Plex data)
- Change `language = "fr"` → `language = "French"`
- Keep `languageCode = "eng"/"fre"` as-is (already realistic)

**Fix broken test** (line 117-138):
- `falls back to Plex defaults` test: when `preferredSubtitleLanguage = null` and audio matches device locale, expected subtitle should be `null` (not `"s2"`)
- OR: change the test setup so audio language doesn't match device locale to trigger the smart-default path

**Update settings test** (line 92-114):
- Change `flowOf("fr")` → `flowOf("fra")` and `flowOf("en")` → `flowOf("eng")` (realistic 3-letter codes matching `PlaybackSettingsScreen` options)

**Add new tests:**
- `areLanguagesEqual` unit tests: `("fre","fra")→true`, `("ger","deu")→true`, `("eng","eng")→true`, `("en","eng")→true`
- Full-name vs ISO: stream with `languageCode="eng"` + setting `"eng"` → match
- B/T variant: stream with `languageCode="fre"` + setting `"fra"` → match

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| `languageCode` null on some streams | Low | Fallback chain: `languageCode` → `language` |
| B/T map missing rare languages | Low | Map covers all 20 known B/T pairs; Locale fallback handles rest |
| Broken test `Plex defaults` may reveal other logic issues | Medium | Verify the correct behavior manually, update assertion |
| Mi Box S perf | None | All changes are in-memory string comparisons + HashMap lookups on <10 items |

## Verification

1. Run `PlayerTrackControllerTest` — all tests pass
2. Build the project successfully
3. On device: settings `audio=eng, sub=fra` + file with `language="English"/"French"` → English audio + French subs
4. On device: file with `languageCode="fre"` + setting `"fra"` → French subs selected (B/T bridging)
5. On device: Plex server configured in FR (French audio `selected=true`) + PlexHubTV pref = English → English audio wins
6. Logcat: verify diagnostic logs show stream enumeration and resolution level
7. Large file (118GB LOTR): no startup delay regression
