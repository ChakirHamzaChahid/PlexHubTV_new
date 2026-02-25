package com.chakir.plexhubtv.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Safely collects a Flow in the given scope with built-in error handling.
 *
 * This extension prevents Flow exceptions from canceling the CoroutineScope,
 * which is critical for ViewModels where scope cancellation would "freeze" the UI.
 *
 * @param scope The CoroutineScope to launch the collection in (typically viewModelScope)
 * @param onError Error handler called when the Flow throws an exception.
 *                Defaults to logging via Timber.
 * @param onEach Handler called for each emitted value
 *
 * @return The launched Job for this collection
 *
 * Example usage in ViewModel:
 * ```
 * repository.observeHome().safeCollectIn(
 *     scope = viewModelScope,
 *     onError = { e ->
 *         Timber.e(e, "HomeViewModel: observeHome failed")
 *         _uiState.update { it.copy(error = UiError.Generic) }
 *     }
 * ) { state ->
 *     _uiState.value = state
 * }
 * ```
 */
fun <T> Flow<T>.safeCollectIn(
    scope: CoroutineScope,
    onError: (Throwable) -> Unit = { Timber.e(it, "Flow error") },
    onEach: suspend (T) -> Unit
) = scope.launch {
    this@safeCollectIn
        .catch { e -> onError(e) }
        .collect { value -> onEach(value) }
}
