package com.chakir.plexhubtv.feature.iptv

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chakir.plexhubtv.core.common.safeCollectIn
import com.chakir.plexhubtv.core.model.Category
import com.chakir.plexhubtv.core.model.EpgEntry
import com.chakir.plexhubtv.core.model.IptvChannel
import com.chakir.plexhubtv.core.model.LiveChannel
import com.chakir.plexhubtv.core.model.XtreamAccount
import com.chakir.plexhubtv.domain.repository.BackendRepository
import com.chakir.plexhubtv.domain.repository.CategoryRepository
import com.chakir.plexhubtv.domain.repository.IptvRepository
import com.chakir.plexhubtv.domain.repository.XtreamAccountRepository
import com.chakir.plexhubtv.feature.player.PlayerFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

enum class SourceMode { M3U, Backend }

@Immutable
data class LiveTvUiState(
    val sourceMode: SourceMode = SourceMode.M3U,
    val isLoading: Boolean = false,
    val error: String? = null,
    // M3U mode
    val m3uChannels: List<IptvChannel> = emptyList(),
    // Backend mode
    val backendChannels: List<LiveChannel> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    // Shared
    val searchQuery: String = "",
    val showUrlDialog: Boolean = false,
    val showSourceSelector: Boolean = false,
    val hasBackend: Boolean = false,
    // 3-column layout state (Backend mode)
    val selectedChannel: LiveChannel? = null,
    val channelEpg: List<EpgEntry> = emptyList(),
    val isLoadingEpg: Boolean = false,
    val streamUrl: String? = null,
    val isResolvingStream: Boolean = false,
    val categorySearchQuery: String = "",
    val filteredCategories: List<Category> = emptyList(),
)

sealed interface LiveTvAction {
    data class ChangeSourceMode(val mode: SourceMode) : LiveTvAction
    data class OnSearchQueryChange(val query: String) : LiveTvAction
    data class SelectCategory(val categoryId: String?) : LiveTvAction
    data object LoadMore : LiveTvAction
    data object Refresh : LiveTvAction
    data class SaveUrl(val url: String) : LiveTvAction
    data object ShowUrlDialog : LiveTvAction
    data object DismissUrlDialog : LiveTvAction
    data object ShowSourceSelector : LiveTvAction
    data object DismissSourceSelector : LiveTvAction
    data class PlayBackendChannel(val channel: LiveChannel) : LiveTvAction
    data class SelectChannel(val channel: LiveChannel) : LiveTvAction
    data object GoFullscreen : LiveTvAction
    data class OnCategorySearchChange(val query: String) : LiveTvAction
}

/** One-shot navigation event for playing a resolved stream URL. */
data class PlayStreamEvent(val url: String, val title: String)

/** Pattern matching separator/header channels like "##### BEIN #####", "=== Sports ===" */
private val SEPARATOR_PATTERN = Regex("^[#=\\-*|]{3,}.*[#=\\-*|]{3,}$")

@OptIn(FlowPreview::class)
@HiltViewModel
class IptvViewModel @Inject constructor(
    private val iptvRepository: IptvRepository,
    private val backendRepository: BackendRepository,
    private val categoryRepository: CategoryRepository,
    private val xtreamAccountRepository: XtreamAccountRepository,
    val playerFactory: PlayerFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    // One-shot navigation event channel
    private val _playStreamEvent = Channel<PlayStreamEvent>(Channel.BUFFERED)
    val playStreamEvent = _playStreamEvent.receiveAsFlow()

    // M3U in-memory cache for local filtering
    private var allM3uChannels: List<IptvChannel> = emptyList()

    // Backend pagination state
    private var backendOffset = 0
    private val backendPageSize = 500

    // Active backend info (resolved at init)
    private var activeBackendId: String? = null
    private var backendAccounts: List<XtreamAccount> = emptyList()
    private var serverIdToLabel: Map<String, String> = emptyMap()

    // Profile-allowed live category IDs (null = no filter, show all)
    private var allowedLiveCategoryIds: Set<String>? = null

    // Search debounce
    private val searchQueryFlow = MutableStateFlow("")
    private val categorySearchFlow = MutableStateFlow("")

    // All loaded categories (unfiltered) for COL 1 search
    private var allBackendCategories: List<Category> = emptyList()

    init {
        // Debounce search: 300ms delay before triggering load
        searchQueryFlow
            .debounce(300)
            .onEach { query ->
                when (_uiState.value.sourceMode) {
                    SourceMode.M3U -> filterM3uChannels(query)
                    SourceMode.Backend -> {
                        backendOffset = 0
                        loadBackendChannels(reset = true)
                    }
                }
            }
            .launchIn(viewModelScope)

        // Debounce category search: 200ms for local filtering
        categorySearchFlow
            .debounce(200)
            .onEach { query -> filterCategories(query) }
            .launchIn(viewModelScope)

        // Auto-reload when M3U URL changes (e.g. saved from Settings screen)
        iptvRepository.observeM3uUrl()
            .distinctUntilChanged()
            .drop(1) // Skip initial emission — we load on init anyway
            .onEach { url ->
                if (_uiState.value.sourceMode == SourceMode.M3U && !url.isNullOrBlank()) {
                    Timber.d("LiveTV: M3U URL changed, reloading channels from: ${url.take(60)}")
                    _uiState.update { it.copy(isLoading = true, error = null) }
                    fetchM3uChannels(url)
                }
            }
            .launchIn(viewModelScope)

        // Detect if backend is available, then load appropriate source
        viewModelScope.launch {
            resolveBackend()
            val hasBackend = activeBackendId != null && backendAccounts.isNotEmpty()
            val initialMode = if (hasBackend) SourceMode.Backend else SourceMode.M3U
            _uiState.update { it.copy(hasBackend = hasBackend, sourceMode = initialMode) }

            if (initialMode == SourceMode.Backend) {
                loadBackendCategories()
                loadBackendChannels(reset = true)
            } else {
                loadM3uChannels()
            }
        }
    }

    fun onAction(action: LiveTvAction) {
        when (action) {
            is LiveTvAction.ChangeSourceMode -> switchSource(action.mode)
            is LiveTvAction.OnSearchQueryChange -> {
                _uiState.update { it.copy(searchQuery = action.query) }
                searchQueryFlow.value = action.query
            }
            is LiveTvAction.SelectCategory -> {
                _uiState.update { it.copy(selectedCategoryId = action.categoryId) }
                backendOffset = 0
                viewModelScope.launch { loadBackendChannels(reset = true) }
            }
            is LiveTvAction.LoadMore -> {
                if (_uiState.value.hasMore && !_uiState.value.isLoadingMore) {
                    viewModelScope.launch { loadBackendChannels(reset = false) }
                }
            }
            is LiveTvAction.Refresh -> refresh()
            is LiveTvAction.SaveUrl -> saveM3uUrl(action.url)
            is LiveTvAction.ShowUrlDialog -> _uiState.update { it.copy(showUrlDialog = true) }
            is LiveTvAction.DismissUrlDialog -> _uiState.update { it.copy(showUrlDialog = false) }
            is LiveTvAction.ShowSourceSelector -> _uiState.update { it.copy(showSourceSelector = true) }
            is LiveTvAction.DismissSourceSelector -> _uiState.update { it.copy(showSourceSelector = false) }
            is LiveTvAction.PlayBackendChannel -> playBackendChannel(action.channel)
            is LiveTvAction.SelectChannel -> selectChannel(action.channel)
            is LiveTvAction.GoFullscreen -> goFullscreen()
            is LiveTvAction.OnCategorySearchChange -> {
                _uiState.update { it.copy(categorySearchQuery = action.query) }
                categorySearchFlow.value = action.query
            }
        }
    }

    // ── Source switching ─────────────────────────────────────────────

    private fun switchSource(mode: SourceMode) {
        if (mode == _uiState.value.sourceMode) return
        _uiState.update {
            it.copy(
                sourceMode = mode,
                error = null,
                searchQuery = "",
                showSourceSelector = false,
            )
        }
        searchQueryFlow.value = ""

        viewModelScope.launch {
            when (mode) {
                SourceMode.M3U -> loadM3uChannels()
                SourceMode.Backend -> {
                    loadBackendCategories()
                    backendOffset = 0
                    loadBackendChannels(reset = true)
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            when (_uiState.value.sourceMode) {
                SourceMode.M3U -> {
                    val url = iptvRepository.getM3uUrl()
                    if (url != null) {
                        _uiState.update { it.copy(isLoading = true, error = null) }
                        val result = iptvRepository.refreshChannels(url)
                        if (result.isFailure) {
                            _uiState.update {
                                it.copy(isLoading = false, error = result.exceptionOrNull()?.message)
                            }
                        }
                    } else {
                        _uiState.update { it.copy(showUrlDialog = true) }
                    }
                }
                SourceMode.Backend -> {
                    backendOffset = 0
                    loadBackendChannels(reset = true)
                }
            }
        }
    }

    // ── M3U mode ─────────────────────────────────────────────────────

    private fun loadM3uChannels() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            iptvRepository.getChannels().safeCollectIn(
                scope = viewModelScope,
                onError = { e ->
                    Timber.e(e, "LiveTvViewModel: loadM3uChannels failed")
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                },
            ) { channels ->
                if (channels.isEmpty()) {
                    val url = iptvRepository.getM3uUrl()
                    if (url != null) {
                        fetchM3uChannels(url)
                    } else {
                        _uiState.update { it.copy(isLoading = false, showUrlDialog = true) }
                    }
                } else {
                    allM3uChannels = channels
                    filterM3uChannels(_uiState.value.searchQuery)
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    private fun saveM3uUrl(url: String) {
        viewModelScope.launch {
            if (url.isNotBlank()) {
                iptvRepository.saveM3uUrl(url)
                _uiState.update { it.copy(showUrlDialog = false, isLoading = true) }
                fetchM3uChannels(url)
            }
        }
    }

    private suspend fun fetchM3uChannels(url: String) {
        val result = iptvRepository.refreshChannels(url)
        if (result.isFailure) {
            _uiState.update { it.copy(isLoading = false, error = result.exceptionOrNull()?.message) }
        }
    }

    private fun filterM3uChannels(query: String) {
        val filtered = if (query.isBlank()) {
            allM3uChannels
        } else {
            allM3uChannels.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (it.group?.contains(query, ignoreCase = true) == true)
            }
        }
        _uiState.update { it.copy(m3uChannels = filtered) }
    }

    // ── Backend mode ─────────────────────────────────────────────────

    private suspend fun resolveBackend() {
        val accounts = xtreamAccountRepository.observeAccounts().first()
        val managedAccounts = accounts.filter { it.isBackendManaged }
        if (managedAccounts.isNotEmpty()) {
            activeBackendId = managedAccounts.first().backendId
            backendAccounts = managedAccounts
            // Build serverId → accountLabel mapping (backend uses "xtream_{accountId}")
            serverIdToLabel = managedAccounts.associate { "xtream_${it.id}" to it.label }
            Timber.d("LiveTV: resolved ${managedAccounts.size} backend accounts, serverIdMap=$serverIdToLabel")
        }
    }

    private suspend fun loadBackendCategories() {
        if (backendAccounts.isEmpty()) return

        // Use categoryRepository which applies the profile-specific filter
        val allCategories = mutableListOf<Category>()
        for (account in backendAccounts) {
            val result = categoryRepository.getCategories(account.id)
            if (result.isSuccess) {
                val liveCategories = result.getOrThrow().categories.filter { it.categoryType == "live" }
                allCategories.addAll(liveCategories)
            } else {
                Timber.w(result.exceptionOrNull(), "Failed to load live categories for account ${account.label}")
            }
        }
        // Deduplicate by categoryId (same category may appear in multiple accounts)
        val uniqueCategories = allCategories.distinctBy { it.categoryId }

        // Track allowed category IDs for channel filtering (profile filter applied)
        val allowed = uniqueCategories.filter { it.isAllowed }.map { it.categoryId }.toSet()
        allowedLiveCategoryIds = if (allowed.size == uniqueCategories.size) null else allowed  // null = all allowed
        Timber.d("LiveTV: loaded ${uniqueCategories.size} live categories, ${allowed.size} allowed by profile filter")

        // Only show allowed categories in the UI chips
        val visibleCategories = uniqueCategories.filter { cat -> cat.isAllowed }
        allBackendCategories = visibleCategories
        _uiState.update {
            it.copy(
                categories = visibleCategories,
                filteredCategories = visibleCategories,
                categorySearchQuery = "",
            )
        }
    }

    private suspend fun loadBackendChannels(reset: Boolean) {
        val backendId = activeBackendId ?: return
        val state = _uiState.value

        if (reset) {
            backendOffset = 0
            _uiState.update { it.copy(isLoading = true, error = null) }
        } else {
            _uiState.update { it.copy(isLoadingMore = true) }
        }

        val result = backendRepository.getLiveChannels(
            backendId = backendId,
            limit = backendPageSize,
            offset = backendOffset,
            sort = "name_asc",
            categoryId = state.selectedCategoryId,
            search = state.searchQuery.takeIf { it.isNotBlank() },
        )

        if (result.isSuccess) {
            val (rawChannels, hasMore) = result.getOrThrow()

            // Filter separator channels, apply profile category filter, add account labels
            val profileFilter = allowedLiveCategoryIds
            val channels = rawChannels
                .filter { !isSeparatorChannel(it.name) }
                .let { list ->
                    if (profileFilter != null) {
                        list.filter { ch -> ch.categoryId != null && ch.categoryId in profileFilter }
                    } else list
                }
                .map { ch -> ch.copy(accountLabel = serverIdToLabel[ch.serverId]) }

            // Merge EPG "now playing" in background
            val enrichedChannels = mergeCurrentEpg(backendId, channels)

            val allChannels = if (reset) enrichedChannels
            else state.backendChannels + enrichedChannels

            backendOffset += rawChannels.size // Offset by raw count (including separators) for API pagination

            _uiState.update {
                it.copy(
                    backendChannels = allChannels,
                    hasMore = hasMore,
                    isLoading = false,
                    isLoadingMore = false,
                    error = null,
                )
            }
        } else {
            val error = result.exceptionOrNull()
            Timber.e(error, "Failed to load backend channels")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    error = error?.message ?: "Failed to load channels",
                )
            }
        }
    }

    // ── 3-column layout: channel selection, EPG, fullscreen ────────

    private fun selectChannel(channel: LiveChannel) {
        val backendId = activeBackendId ?: return
        _uiState.update {
            it.copy(
                selectedChannel = channel,
                isResolvingStream = true,
                channelEpg = emptyList(),
                isLoadingEpg = true,
            )
        }
        // Resolve stream URL and load EPG in parallel
        viewModelScope.launch {
            val result = backendRepository.getLiveStream(backendId, channel.serverId, channel.streamId)
            if (result.isSuccess) {
                _uiState.update { it.copy(streamUrl = result.getOrThrow(), isResolvingStream = false) }
            } else {
                Timber.e(result.exceptionOrNull(), "Failed to resolve stream for ${channel.name}")
                _uiState.update { it.copy(isResolvingStream = false, error = "Failed to load stream") }
            }
        }
        viewModelScope.launch {
            val result = backendRepository.getChannelEpg(backendId, channel.serverId, channel.streamId)
            _uiState.update {
                it.copy(
                    channelEpg = if (result.isSuccess) result.getOrThrow() else emptyList(),
                    isLoadingEpg = false,
                )
            }
        }
    }

    private fun goFullscreen() {
        val state = _uiState.value
        val url = state.streamUrl ?: return
        val title = state.selectedChannel?.name ?: "Live"
        viewModelScope.launch {
            _playStreamEvent.send(PlayStreamEvent(url, title))
        }
    }

    private fun filterCategories(query: String) {
        val filtered = if (query.isBlank()) {
            allBackendCategories
        } else {
            allBackendCategories.filter {
                it.categoryName.contains(query, ignoreCase = true)
            }
        }
        _uiState.update { it.copy(filteredCategories = filtered) }
    }

    private fun playBackendChannel(channel: LiveChannel) {
        val backendId = activeBackendId ?: return
        viewModelScope.launch {
            val result = backendRepository.getLiveStream(backendId, channel.serverId, channel.streamId)
            if (result.isSuccess) {
                _playStreamEvent.send(PlayStreamEvent(result.getOrThrow(), channel.name))
            } else {
                Timber.e(result.exceptionOrNull(), "Failed to get stream URL for ${channel.name}")
                _uiState.update { it.copy(error = "Failed to load stream: ${result.exceptionOrNull()?.message}") }
            }
        }
    }

    private suspend fun mergeCurrentEpg(
        backendId: String,
        channels: List<LiveChannel>,
    ): List<LiveChannel> {
        if (channels.isEmpty()) return channels

        // Get unique serverIds from this batch
        val serverIds = channels.map { it.serverId }.distinct()
        Timber.d("LiveTV EPG: merging EPG for ${channels.size} channels across serverIds=$serverIds")

        // Merge EPG for each serverId
        val epgMap = mutableMapOf<Int, com.chakir.plexhubtv.core.model.EpgEntry>()
        for (serverId in serverIds) {
            val epgResult = backendRepository.getCurrentEpg(backendId, serverId)
            if (epgResult.isSuccess) {
                val entries = epgResult.getOrThrow()
                Timber.d("LiveTV EPG: serverId=$serverId returned ${entries.size} EPG entries")
                epgMap.putAll(entries)
            } else {
                Timber.w(epgResult.exceptionOrNull(), "LiveTV EPG: failed for serverId=$serverId")
            }
        }

        if (epgMap.isEmpty()) {
            Timber.d("LiveTV EPG: no EPG data available")
            return channels
        }

        Timber.d("LiveTV EPG: total ${epgMap.size} entries, matching against ${channels.size} channels")
        var matched = 0
        val result = channels.map { channel ->
            val nowPlaying = epgMap[channel.streamId]
            if (nowPlaying != null) {
                matched++
                channel.copy(nowPlaying = nowPlaying)
            } else {
                channel
            }
        }
        Timber.d("LiveTV EPG: matched $matched/${channels.size} channels with now-playing data")
        return result
    }

    companion object {
        fun isSeparatorChannel(name: String): Boolean {
            val trimmed = name.trim()
            return SEPARATOR_PATTERN.matches(trimmed)
        }
    }
}
