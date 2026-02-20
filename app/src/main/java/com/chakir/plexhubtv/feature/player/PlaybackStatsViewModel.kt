package com.chakir.plexhubtv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import com.chakir.plexhubtv.feature.player.controller.PlayerStatsTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaybackStatsViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val playerStatsTracker: PlayerStatsTracker
) : ViewModel() {

    val uiState = playerController.uiState

    fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.TogglePerformanceOverlay -> {
                val newState = !uiState.value.showPerformanceOverlay
                playerController.updateState { it.copy(showPerformanceOverlay = newState) }
                
                if (newState) {
                     playerStatsTracker.stats.safeCollectIn(
                         scope = viewModelScope,
                         onError = { e ->
                             timber.log.Timber.e(e, "PlaybackStatsViewModel: stats collection failed")
                             playerController.updateState { it.copy(showPerformanceOverlay = false, playerStats = null) }
                         }
                     ) { stats ->
                         playerController.updateState { it.copy(playerStats = stats) }
                     }
                } else {
                    playerController.updateState { it.copy(playerStats = null) }
                }
            }
            else -> {}
        }
    }
}
