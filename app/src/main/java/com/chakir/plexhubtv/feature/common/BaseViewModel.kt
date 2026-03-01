package com.chakir.plexhubtv.feature.common

import androidx.lifecycle.ViewModel
import com.chakir.plexhubtv.core.model.AppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Base ViewModel providing a standardized error event channel.
 * All ViewModels that emit errors via snackbar should extend this class.
 */
abstract class BaseViewModel : ViewModel() {

    protected val _errorEvents = Channel<AppError>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()

    protected suspend fun emitError(error: AppError) {
        _errorEvents.send(error)
    }
}
