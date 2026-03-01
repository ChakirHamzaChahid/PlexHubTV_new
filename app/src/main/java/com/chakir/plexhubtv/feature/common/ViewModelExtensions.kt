package com.chakir.plexhubtv.feature.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Standard loading pattern for ViewModels:
 * 1. [onStart] — typically sets loading=true
 * 2. [block] — suspending operation returning Result<T>
 * 3. [onSuccess] or [onFailure] callback
 *
 * Reduces the `viewModelScope.launch { state=loading; result.fold(...) }` boilerplate.
 */
inline fun <T> ViewModel.launchLoading(
    crossinline onStart: () -> Unit = {},
    crossinline block: suspend () -> Result<T>,
    crossinline onSuccess: (T) -> Unit,
    crossinline onFailure: (Throwable) -> Unit = {},
): Job = viewModelScope.launch {
    onStart()
    block().fold(
        onSuccess = { onSuccess(it) },
        onFailure = { onFailure(it) },
    )
}
