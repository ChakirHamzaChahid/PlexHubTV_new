package com.chakir.plexhubtv.feature.history

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.chakir.plexhubtv.feature.common.BaseViewModel
import com.chakir.plexhubtv.domain.usecase.GetWatchHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel pour l'historique de visionnage.
 * Utilise [GetWatchHistoryUseCase] et expose un Flow de PagingData.
 */
@HiltViewModel
class HistoryViewModel
    @Inject
    constructor(
        getWatchHistoryUseCase: GetWatchHistoryUseCase,
    ) : BaseViewModel() {
        val pagedHistory = getWatchHistoryUseCase()
            .cachedIn(viewModelScope)

        init {
            Timber.d("SCREEN [History]: Opened")
        }
    }
