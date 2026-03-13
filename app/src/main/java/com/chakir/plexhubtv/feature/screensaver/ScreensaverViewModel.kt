package com.chakir.plexhubtv.feature.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.database.MediaDao
import com.chakir.plexhubtv.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for the screensaver. Constructed manually in [PlexHubDreamService]
 * because Hilt's ViewModelFactory is not available in DreamService context.
 */
class ScreensaverViewModel(
    private val mediaDao: MediaDao,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _artworkUrls = MutableStateFlow<List<String>>(emptyList())
    val artworkUrls: StateFlow<List<String>> = _artworkUrls.asStateFlow()

    val showClock: StateFlow<Boolean> = settingsRepository.screensaverShowClock
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val intervalSeconds: StateFlow<Int> = settingsRepository.screensaverIntervalSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    init {
        loadArtwork()
    }

    private fun loadArtwork() {
        viewModelScope.launch {
            try {
                val urls = mediaDao.getRandomArtworkUrls(30)
                _artworkUrls.value = urls
                Timber.d("[Screensaver] Loaded ${urls.size} artwork URLs")
            } catch (e: Exception) {
                Timber.e(e, "[Screensaver] Failed to load artwork")
            }
        }
    }
}
