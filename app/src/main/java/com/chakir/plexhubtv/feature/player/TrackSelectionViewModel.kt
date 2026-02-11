package com.chakir.plexhubtv.feature.player

import androidx.lifecycle.ViewModel
import com.chakir.plexhubtv.feature.player.controller.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TrackSelectionViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    val uiState = playerController.uiState

    fun onAction(action: PlayerAction) {
        when (action) {
            is PlayerAction.SelectAudioTrack -> {
                playerController.updateState { it.copy(showAudioSelection = false) }
                playerController.selectAudioTrack(action.track)
            }
            is PlayerAction.SelectSubtitleTrack -> {
                playerController.updateState { it.copy(showSubtitleSelection = false) }
                playerController.selectSubtitleTrack(action.track)
            }
            is PlayerAction.ShowAudioSelector -> {
                playerController.updateState { it.copy(showAudioSelection = true, showSettings = false, showSubtitleSelection = false) }
            }
            is PlayerAction.ShowSubtitleSelector -> {
                playerController.updateState { it.copy(showSubtitleSelection = true, showSettings = false, showAudioSelection = false) }
            }
            else -> {}
        }
    }
}
