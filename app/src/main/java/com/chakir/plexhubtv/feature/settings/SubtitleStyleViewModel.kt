package com.chakir.plexhubtv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.SubtitlePreferences
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubtitleStyleViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _prefs = MutableStateFlow(SubtitlePreferences())
    val prefs: StateFlow<SubtitlePreferences> = _prefs.asStateFlow()

    init {
        combine(
            settingsRepository.subtitleFontSize,
            settingsRepository.subtitleFontColor,
            settingsRepository.subtitleBgColor,
            settingsRepository.subtitleEdgeType,
            settingsRepository.subtitleEdgeColor,
        ) { fontSize, fontColor, bgColor, edgeType, edgeColor ->
            SubtitlePreferences(fontSize, fontColor, bgColor, edgeType, edgeColor)
        }
            .onEach { prefs -> _prefs.update { prefs } }
            .launchIn(viewModelScope)
    }

    fun setFontSize(size: Int) {
        _prefs.update { it.copy(fontSize = size) }
        viewModelScope.launch { settingsRepository.saveSubtitleFontSize(size) }
    }

    fun setFontColor(color: Long) {
        _prefs.update { it.copy(fontColor = color) }
        viewModelScope.launch { settingsRepository.saveSubtitleFontColor(color) }
    }

    fun setBgColor(color: Long) {
        _prefs.update { it.copy(backgroundColor = color) }
        viewModelScope.launch { settingsRepository.saveSubtitleBgColor(color) }
    }

    fun setEdgeType(type: Int) {
        _prefs.update { it.copy(edgeType = type) }
        viewModelScope.launch { settingsRepository.saveSubtitleEdgeType(type) }
    }

    fun setEdgeColor(color: Long) {
        _prefs.update { it.copy(edgeColor = color) }
        viewModelScope.launch { settingsRepository.saveSubtitleEdgeColor(color) }
    }
}
