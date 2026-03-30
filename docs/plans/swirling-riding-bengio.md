# Fix: Library Screen Focus Lost After Back Navigation

## Context

When the user navigates from the Library grid to a media detail screen and presses Back, the focus is lost — the grid resets to position 0 instead of returning to the previously selected item.

**Root cause**: The navigation architecture (app-level NavHost: `Main → MediaDetail`) disposes the entire `MainScreen` composable on forward navigation. On back, everything recomposes from scratch. `collectAsLazyPagingItems()` loads data asynchronously via `LaunchedEffect`, so `itemCount = 0` on the first frame — the grid can't restore its scroll position, the focused item is never composed, and focus restoration silently fails.

---

## Files to Modify

| File | Changes |
|------|---------|
| [LibraryUiState.kt](app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryUiState.kt) | Add `pendingScrollRestore` to `LibraryScrollState`, add `firstVisibleItemIndex` param to `OpenMedia`, add `ConsumeScrollRestore` action |
| [LibraryViewModel.kt](app/src/main/java/com/chakir/plexhubtv/feature/library/LibraryViewModel.kt) | Save/restore scroll position in `init` + `onAction(OpenMedia)`, handle `ConsumeScrollRestore`, guard filter/sort actions |
| [LibrariesScreen.kt](app/src/main/java/com/chakir/plexhubtv/feature/library/LibrariesScreen.kt) | Add scroll restoration `LaunchedEffect` with `snapshotFlow`, pass scroll index to `OpenMedia`, add focus restoration to List view |

---

## Implementation Steps

### Step 1: Update `LibraryScrollState` and actions (`LibraryUiState.kt`)

Add `pendingScrollRestore` field to track the grid position to restore after back-navigation:

```kotlin
data class LibraryScrollState(
    val rawOffset: Int = 0,
    val offset: Int = 0,
    val endOfReached: Boolean = false,
    val initialScrollIndex: Int? = null,
    val lastFocusedId: String? = null,
    val pendingScrollRestore: Int? = null,  // NEW
)
```

Add scroll index to `OpenMedia` so the composable can report current position:

```kotlin
data class OpenMedia(val media: MediaItem, val firstVisibleItemIndex: Int = 0) : LibraryAction
```

Add action to consume the restore after it fires:

```kotlin
object ConsumeScrollRestore : LibraryAction
```

### Step 2: Save scroll position on navigation (`LibraryViewModel.kt`)

In `onAction(OpenMedia)` — save both `lastFocusedId` AND scroll position:

```kotlin
is LibraryAction.OpenMedia -> {
    _uiState.update { it.copy(scroll = it.scroll.copy(lastFocusedId = action.media.ratingKey)) }
    savedStateHandle["lastFocusedId"] = action.media.ratingKey
    savedStateHandle["initialScrollIndex"] = action.firstVisibleItemIndex
    savedStateHandle["pendingScrollRestore"] = action.firstVisibleItemIndex
    viewModelScope.launch {
        _navigationEvents.send(LibraryNavigationEvent.NavigateToDetail(action.media.ratingKey, action.media.serverId))
    }
}
```

- `initialScrollIndex` → tells PagingSource where to start loading (first page includes the target area)
- `pendingScrollRestore` → tells the grid where to scroll after data loads

### Step 3: Restore from SavedStateHandle on ViewModel recreation (`LibraryViewModel.kt`)

In `init`, also restore `pendingScrollRestore`:

```kotlin
val restoredPendingScroll = savedStateHandle.get<Int>("pendingScrollRestore")

_uiState.update {
    it.copy(
        // ... existing ...
        scroll = it.scroll.copy(
            initialScrollIndex = restoredItemIndex,
            lastFocusedId = restoredFocusId,
            pendingScrollRestore = restoredPendingScroll,
        ),
    )
}
```

### Step 4: Handle `ConsumeScrollRestore` action (`LibraryViewModel.kt`)

```kotlin
is LibraryAction.ConsumeScrollRestore -> {
    savedStateHandle.remove<Int>("pendingScrollRestore")
    _uiState.update { it.copy(scroll = it.scroll.copy(pendingScrollRestore = null)) }
}
```

### Step 5: Clear pending restore on filter/sort/jump changes (`LibraryViewModel.kt`)

Prevent stale scroll restoration when the user changes filters before navigating back (edge case with process death):

- In `ApplySort`: add `savedStateHandle.remove<Int>("pendingScrollRestore")` + update `_uiState`
- In `JumpToLetter` (before existing logic): clear `pendingScrollRestore`
- In `SelectGenre`, `SelectServerFilter`: clear `pendingScrollRestore`

### Step 6: Pass scroll index from composable to ViewModel (`LibrariesScreen.kt`)

In `LibrariesScreen`, change the `onItemClick` lambda to include the current scroll position:

```kotlin
onItemClick = { item ->
    val scrollIndex = when (state.display.viewMode) {
        LibraryViewMode.Grid, LibraryViewMode.Compact -> gridState.firstVisibleItemIndex
        LibraryViewMode.List -> listState.firstVisibleItemIndex
    }
    onAction(LibraryAction.OpenMedia(item, scrollIndex))
},
```

### Step 7: Add scroll restoration `LaunchedEffect` (`LibrariesScreen.kt`)

After `gridState`/`listState` declarations, add the scroll restoration effect:

```kotlin
// Restore scroll position after back-navigation (wait for paging data to load)
val pendingScrollRestore = state.scroll.pendingScrollRestore
LaunchedEffect(pendingScrollRestore) {
    if (pendingScrollRestore != null && pendingScrollRestore > 0) {
        // Wait until paging has loaded enough items
        snapshotFlow { pagedItems.itemCount }
            .first { it > pendingScrollRestore }
        // Scroll to saved position
        when (state.display.viewMode) {
            LibraryViewMode.Grid, LibraryViewMode.Compact ->
                gridState.scrollToItem(pendingScrollRestore)
            LibraryViewMode.List ->
                listState.scrollToItem(pendingScrollRestore)
        }
        onAction(LibraryAction.ConsumeScrollRestore)
    }
}
```

Add `import androidx.compose.runtime.snapshotFlow` at the top.

### Step 8: Add focus restoration to List view mode (`LibrariesScreen.kt`)

The List view currently has no focus tracking. Add the same pattern used in Grid/Compact:

```kotlin
LibraryViewMode.List -> {
    LazyColumn(...) {
        items(...) { index ->
            val item = pagedItems[index]
            if (item != null) {
                val shouldRestoreFocus = !hasRestoredFocus && lastFocusedId != null && item.ratingKey == lastFocusedId
                LaunchedEffect(shouldRestoreFocus) {
                    if (shouldRestoreFocus) {
                        delay(150)
                        try {
                            focusRestorationRequester.requestFocus()
                            hasRestoredFocus = true
                        } catch (_: Exception) { }
                    }
                }
                Row(
                    modifier = Modifier
                        .then(if (shouldRestoreFocus) Modifier.focusRequester(focusRestorationRequester) else Modifier)
                        .clickable { onItemClick(item) }
                        // ... rest unchanged
                )
            }
        }
    }
}
```

---

## Risks

| Risk | Mitigation |
|------|------------|
| **Timing**: `snapshotFlow` might suspend indefinitely if itemCount never exceeds `pendingScrollRestore` (e.g., items deleted) | Add a 3-second timeout: `withTimeoutOrNull(3000) { snapshotFlow... }` |
| **Perf**: `snapshotFlow { pagedItems.itemCount }` reads snapshot state in a coroutine — low overhead, fires only on count changes | Already the idiomatic Compose pattern |
| **Conflict with JumpToLetter**: Both use `initialScrollIndex` | `pendingScrollRestore` is consumed immediately; JumpToLetter clears it first |
| **Conflict with sort/filter scroll-to-top**: `isFirstComposition` guard skips first composition | Correctly resets to `true` on recomposition — no conflict |

---

## Verification

### Manual Tests (Android TV / emulator with D-pad)

1. **Basic restoration**: Open Movies → scroll to row ~5 → select a movie → Back → verify grid returns to same position AND focus is on the selected movie
2. **Same test for TV Shows**
3. **List view**: Switch to List mode → scroll down → select item → Back → verify scroll + focus
4. **Compact view**: Same test in Compact mode
5. **First page items**: Select an item on the first visible page (no scroll needed) → Back → verify focus restores
6. **JumpToLetter after restore**: Back-navigate → scroll restores → use alphabet sidebar → verify JumpToLetter still works
7. **Filter change**: Back-navigate → change sort order → verify grid scrolls to top (not to old position)
8. **Process death**: Open movie → adb kill → relaunch → verify scroll + focus restores from SavedStateHandle
