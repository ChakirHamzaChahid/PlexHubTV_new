package com.chakir.plexhubtv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.model.Hub
import com.chakir.plexhubtv.core.model.MediaItem
import com.chakir.plexhubtv.domain.usecase.GetMediaCollectionsUseCase
import com.chakir.plexhubtv.domain.usecase.GetSimilarMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class MediaEnrichmentUiState(
    val similarItems: List<MediaItem> = emptyList(),
    val collections: List<Hub> = emptyList(),
    val isLoadingSimilar: Boolean = false,
    val isLoadingCollections: Boolean = false
)

@HiltViewModel
class MediaEnrichmentViewModel @Inject constructor(
    private val getSimilarMediaUseCase: GetSimilarMediaUseCase,
    private val getMediaCollectionsUseCase: GetMediaCollectionsUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val ratingKey: String = checkNotNull(savedStateHandle["ratingKey"])
    private val serverId: String = checkNotNull(savedStateHandle["serverId"])

    private val _uiState = MutableStateFlow(MediaEnrichmentUiState())
    val uiState: StateFlow<MediaEnrichmentUiState> = _uiState.asStateFlow()

    init {
        loadSimilarItems()
    }

    // Called by UI or Parent VM when primary media is loaded
    fun loadEnrichment(media: MediaItem) {
      // TODO a revoir   loadCollections(media)
    }

    private fun loadSimilarItems() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSimilar = true) }
            getSimilarMediaUseCase(ratingKey, serverId)
                .onSuccess { items ->
                    _uiState.update { it.copy(similarItems = items, isLoadingSimilar = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingSimilar = false) }
                }
        }
    }

  /*  private suspend fun loadCollection() {
        Timber.d("VM: Loading collections (multi-server aggregation)")
        try {
            val media = _uiState.value.media ?: run {
                Timber.w("VM: Cannot load collections - media is null")
                return
            }

            // Build list of all servers to query: primary + remote sources
            val serversToQuery = buildList {
                add(Pair(media.serverId, media.ratingKey))
                media.remoteSources.forEach { source ->
                    add(Pair(source.serverId, source.ratingKey))
                }
            }.distinctBy { it.first } // Deduplicate by serverId

            Timber.i("VM: Querying ${serversToQuery.size} server(s) for collections")
            serversToQuery.forEach { (sid, rkey) ->
                Timber.d("   - Server $sid with ratingKey $rkey")
            }

            // Query all servers in parallel
            val allCollections = serversToQuery.map { (sid, rkey) ->
                viewModelScope.async {
                    try {
                        Timber.d("VM: Fetching collections from server $sid...")
                        val result = getMediaCollectionsUseCase(rkey, sid).first()
                        Timber.d("VM: ✓ Got ${result.size} collection(s) from server $sid")
                        result
                    } catch (e: Exception) {
                        Timber.w(e, "VM: Failed to load collections from server $sid")
                        emptyList()
                    }
                }
            }.awaitAll().flatten()

            // Deduplicate collections by (title + serverId) to keep distinct collections
            // Note: Same title on different servers = different collections (correct behavior)
            val uniqueCollections = allCollections.distinctBy { "${it.title}|${it.serverId}" }

            if (uniqueCollections.isNotEmpty()) {
                Timber.i("VM: ✅ Aggregated ${uniqueCollections.size} unique collection(s) from ${serversToQuery.size} server(s)")
                uniqueCollections.forEach { col ->
                    Timber.d("   - '${col.title}' (${col.items.size} items, server=${col.serverId})")
                }
            } else {
                Timber.w("VM: ⚠️ No collections found across any server")
            }

            _uiState.update { it.copy(collections = uniqueCollections) }
        } catch (e: Exception) {
            Timber.e(e, "VM: ❌ Exception during multi-server collection aggregation")
        }
    }*/

}
