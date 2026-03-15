package com.chakir.plexhubtv.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.chakir.plexhubtv.core.di.ApplicationScope
import com.chakir.plexhubtv.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper autour de DataStore Preferences pour un accès typé aux paramètres de l'application.
 *
 * Gère :
 * - Authentification (Plex Token, Client ID) - SÉCURISÉ via EncryptedSharedPreferences
 * - Préférences UI (Thème, Affichage Hero)
 * - Configuration Lecture (Qualité, Moteur)
 * - État de synchro (Dernière synchro)
 *
 * Les données sensibles (tokens, API keys) sont stockées dans [SecurePreferencesManager]
 * avec chiffrement AES-256-GCM. Les autres préférences utilisent DataStore.
 */
@Singleton
class SettingsDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        private val securePrefs: SecurePreferencesManager,
        @ApplicationScope private val appScope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        // Deprecated: kept for migration only
        private val PLEX_TOKEN = stringPreferencesKey("plex_token")
        private val CLIENT_ID = stringPreferencesKey("client_id")
        private val SERVER_QUALITY = stringPreferencesKey("server_quality")
        private val CURRENT_USER_UUID = stringPreferencesKey("current_user_uuid")
        private val CURRENT_USER_NAME = stringPreferencesKey("current_user_name")
        private val SHOW_HERO_SECTION = stringPreferencesKey("show_hero_section")
        private val EPISODE_POSTER_MODE = stringPreferencesKey("episode_poster_mode")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val SHOW_YEAR_ON_CARDS = stringPreferencesKey("show_year_on_cards")
        private val GRID_COLUMNS_COUNT = stringPreferencesKey("grid_columns_count")
        private val CACHE_ENABLED = stringPreferencesKey("cache_enabled")
        private val DEFAULT_SERVER = stringPreferencesKey("default_server")
        private val PLAYER_ENGINE = stringPreferencesKey("player_engine")
        private val DEINTERLACE_MODE = stringPreferencesKey("deinterlace_mode")
        private val AUTO_PLAY_NEXT = stringPreferencesKey("auto_play_next")
        private val SKIP_INTRO_MODE = stringPreferencesKey("skip_intro_mode")
        private val SKIP_CREDITS_MODE = stringPreferencesKey("skip_credits_mode")
        private val THEME_SONG_ENABLED = stringPreferencesKey("theme_song_enabled")
        private val THEME_SONG_VOLUME = stringPreferencesKey("theme_song_volume")
        private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val FIRST_SYNC_COMPLETE = stringPreferencesKey("first_sync_complete")
        private val EXCLUDED_SERVER_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("excluded_server_ids")
        private val IPTV_PLAYLIST_URL = stringPreferencesKey("iptv_playlist_url")
        private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        private val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")

        // Library Selection Configuration
        private val SELECTED_LIBRARY_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("selected_library_ids")
        private val LIBRARY_SELECTION_COMPLETE = stringPreferencesKey("library_selection_complete")

        // Xtream Category Selection Configuration
        private val SELECTED_XTREAM_CATEGORY_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("selected_xtream_category_ids")

        // Rating Sync Configuration
        private val RATING_SYNC_SOURCE = stringPreferencesKey("rating_sync_source") // "tmdb" or "omdb"
        private val RATING_SYNC_DELAY = stringPreferencesKey("rating_sync_delay") // delay in ms
        private val RATING_SYNC_BATCHING_ENABLED = stringPreferencesKey("rating_sync_batching_enabled")
        private val RATING_SYNC_DAILY_LIMIT = stringPreferencesKey("rating_sync_daily_limit")
        private val RATING_SYNC_PROGRESS_SERIES = stringPreferencesKey("rating_sync_progress_series")
        private val RATING_SYNC_PROGRESS_MOVIES = stringPreferencesKey("rating_sync_progress_movies")
        private val RATING_SYNC_LAST_RUN_DATE = stringPreferencesKey("rating_sync_last_run_date")

        // TV Channels Configuration
        private val TV_CHANNELS_ENABLED = stringPreferencesKey("tv_channels_enabled")

        // Subtitle Style Preferences
        private val SUBTITLE_FONT_SIZE = stringPreferencesKey("subtitle_font_size")
        private val SUBTITLE_FONT_COLOR = stringPreferencesKey("subtitle_font_color")
        private val SUBTITLE_BG_COLOR = stringPreferencesKey("subtitle_bg_color")
        private val SUBTITLE_EDGE_TYPE = stringPreferencesKey("subtitle_edge_type")
        private val SUBTITLE_EDGE_COLOR = stringPreferencesKey("subtitle_edge_color")

        // Auto-Update Preferences
        private val AUTO_CHECK_UPDATES = stringPreferencesKey("auto_check_updates")

        // Screensaver Preferences
        private val SCREENSAVER_ENABLED = stringPreferencesKey("screensaver_enabled")
        private val SCREENSAVER_INTERVAL_SECONDS = stringPreferencesKey("screensaver_interval_seconds")
        private val SCREENSAVER_SHOW_CLOCK = stringPreferencesKey("screensaver_show_clock")

        // Home Row Visibility & Order
        private val SHOW_CONTINUE_WATCHING = stringPreferencesKey("show_continue_watching")
        private val SHOW_MY_LIST = stringPreferencesKey("show_my_list")
        private val SHOW_SUGGESTIONS = stringPreferencesKey("show_suggestions")
        private val HOME_ROW_ORDER = stringPreferencesKey("home_row_order")

        // Library Filter Preferences
        private val LIBRARY_SORT = stringPreferencesKey("library_sort")
        private val LIBRARY_SORT_DESCENDING = stringPreferencesKey("library_sort_descending")
        private val LIBRARY_GENRE = stringPreferencesKey("library_genre")
        private val LIBRARY_SERVER_FILTER = stringPreferencesKey("library_server_filter")

        init {
            // Migration: move sensitive data from DataStore to EncryptedSharedPreferences
            appScope.launch(ioDispatcher) {
                try {
                    val prefs = dataStore.data.first()

                    // Migrate plex token
                    prefs[PLEX_TOKEN]?.let { token ->
                        if (token.isNotBlank() && securePrefs.getPlexToken() == null) {
                            securePrefs.savePlexToken(token)
                            dataStore.edit { it.remove(PLEX_TOKEN) }
                            Timber.d("Migrated Plex token to EncryptedSharedPreferences")
                        }
                    }

                    // Migrate client ID
                    prefs[CLIENT_ID]?.let { id ->
                        if (id.isNotBlank() && securePrefs.getClientId() == null) {
                            securePrefs.saveClientId(id)
                            dataStore.edit { it.remove(CLIENT_ID) }
                            Timber.d("Migrated Client ID to EncryptedSharedPreferences")
                        }
                    }

                    // Migrate TMDB API key
                    prefs[TMDB_API_KEY]?.let { key ->
                        if (key.isNotBlank() && securePrefs.getTmdbApiKey() == null) {
                            securePrefs.saveTmdbApiKey(key)
                            dataStore.edit { it.remove(TMDB_API_KEY) }
                            Timber.d("Migrated TMDB API key to EncryptedSharedPreferences")
                        }
                    }

                    // Migrate OMDB API key
                    prefs[OMDB_API_KEY]?.let { key ->
                        if (key.isNotBlank() && securePrefs.getOmdbApiKey() == null) {
                            securePrefs.saveOmdbApiKey(key)
                            dataStore.edit { it.remove(OMDB_API_KEY) }
                            Timber.d("Migrated OMDB API key to EncryptedSharedPreferences")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to migrate sensitive data to EncryptedSharedPreferences")
                }
            }
        }

        // Delegated to SecurePreferencesManager for encryption
        val plexToken: Flow<String?> = securePrefs.plexToken

        val clientId: Flow<String?> = securePrefs.clientId

        val currentUserUuid: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[CURRENT_USER_UUID] }

        val currentUserName: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[CURRENT_USER_NAME] }

        val showHeroSection: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[SHOW_HERO_SECTION]?.toBoolean() ?: true }

        val episodePosterMode: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[EPISODE_POSTER_MODE] ?: "series" }

        val appTheme: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[APP_THEME] ?: "MonoDark" }

        val showYearOnCards: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[SHOW_YEAR_ON_CARDS]?.toBoolean() ?: false }

        val gridColumnsCount: Flow<Int> =
            dataStore.data
                .map { preferences -> preferences[GRID_COLUMNS_COUNT]?.toIntOrNull() ?: 6 }

        val videoQuality: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[SERVER_QUALITY] ?: "Original" }

        val isCacheEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[CACHE_ENABLED]?.toBoolean() ?: true }

        val defaultServer: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[DEFAULT_SERVER] ?: "all" }

        val playerEngine: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[PLAYER_ENGINE] ?: "ExoPlayer" }

        val deinterlaceMode: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[DEINTERLACE_MODE] ?: "auto" }

        val autoPlayNextEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[AUTO_PLAY_NEXT]?.toBoolean() ?: true }

        val skipIntroMode: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[SKIP_INTRO_MODE] ?: "ask" }

        val skipCreditsMode: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[SKIP_CREDITS_MODE] ?: "ask" }

        val themeSongEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[THEME_SONG_ENABLED]?.toBoolean() ?: false }

        val themeSongVolume: Flow<Float> =
            dataStore.data
                .map { preferences -> preferences[THEME_SONG_VOLUME]?.toFloatOrNull() ?: 0.3f }

        val lastSyncTime: Flow<Long> =
            dataStore.data
                .map { preferences -> preferences[LAST_SYNC_TIME]?.toLongOrNull() ?: 0L }

        val isFirstSyncComplete: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[FIRST_SYNC_COMPLETE]?.toBoolean() ?: false }

        val excludedServerIds: Flow<Set<String>> =
            dataStore.data
                .map { preferences -> preferences[EXCLUDED_SERVER_IDS] ?: emptySet() }

        // Library Selection Configuration Flows
        val selectedLibraryIds: Flow<Set<String>> =
            dataStore.data
                .map { preferences -> preferences[SELECTED_LIBRARY_IDS] ?: emptySet() }

        val selectedXtreamCategoryIds: Flow<Set<String>> =
            dataStore.data
                .map { preferences -> preferences[SELECTED_XTREAM_CATEGORY_IDS] ?: emptySet() }

        val isLibrarySelectionComplete: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[LIBRARY_SELECTION_COMPLETE]?.toBoolean() ?: false }

        val iptvPlaylistUrl: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[IPTV_PLAYLIST_URL] }

        // Delegated to SecurePreferencesManager for encryption
        val tmdbApiKey: Flow<String?> = securePrefs.tmdbApiKey

        val omdbApiKey: Flow<String?> = securePrefs.omdbApiKey

        val openSubtitlesApiKey: Flow<String?> = securePrefs.openSubtitlesApiKey

        // Rating Sync Configuration Flows
        val ratingSyncSource: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_SOURCE] ?: "tmdb" } // Default to TMDb

        val ratingSyncDelay: Flow<Long> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_DELAY]?.toLongOrNull() ?: 250L } // Default 250ms

        val ratingSyncBatchingEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_BATCHING_ENABLED]?.toBoolean() ?: false }

        val ratingSyncDailyLimit: Flow<Int> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_DAILY_LIMIT]?.toIntOrNull() ?: 900 } // Default 900/day

        val ratingSyncProgressSeries: Flow<Int> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_PROGRESS_SERIES]?.toIntOrNull() ?: 0 }

        val ratingSyncProgressMovies: Flow<Int> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_PROGRESS_MOVIES]?.toIntOrNull() ?: 0 }

        val ratingSyncLastRunDate: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[RATING_SYNC_LAST_RUN_DATE] }

        // TV Channels Configuration Flow
        val isTvChannelsEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[TV_CHANNELS_ENABLED]?.toBoolean() ?: true }

        // Subtitle Style Preferences Flows
        val subtitleFontSize: Flow<Int> =
            dataStore.data.map { it[SUBTITLE_FONT_SIZE]?.toIntOrNull() ?: 22 }

        val subtitleFontColor: Flow<Long> =
            dataStore.data.map { it[SUBTITLE_FONT_COLOR]?.toLongOrNull() ?: 0xFFFFFFFF }

        val subtitleBgColor: Flow<Long> =
            dataStore.data.map { it[SUBTITLE_BG_COLOR]?.toLongOrNull() ?: 0x80000000 }

        val subtitleEdgeType: Flow<Int> =
            dataStore.data.map { it[SUBTITLE_EDGE_TYPE]?.toIntOrNull() ?: 0 }

        val subtitleEdgeColor: Flow<Long> =
            dataStore.data.map { it[SUBTITLE_EDGE_COLOR]?.toLongOrNull() ?: 0xFF000000 }

        // Auto-Update Preferences Flow
        val autoCheckUpdates: Flow<Boolean> =
            dataStore.data.map { it[AUTO_CHECK_UPDATES]?.toBoolean() ?: true }

        // Home Row Visibility Flows
        val showContinueWatching: Flow<Boolean> =
            dataStore.data.map { it[SHOW_CONTINUE_WATCHING]?.toBoolean() ?: true }

        val showMyList: Flow<Boolean> =
            dataStore.data.map { it[SHOW_MY_LIST]?.toBoolean() ?: true }

        val showSuggestions: Flow<Boolean> =
            dataStore.data.map { it[SHOW_SUGGESTIONS]?.toBoolean() ?: true }

        suspend fun saveShowContinueWatching(show: Boolean) {
            dataStore.edit { it[SHOW_CONTINUE_WATCHING] = show.toString() }
        }

        suspend fun saveShowMyList(show: Boolean) {
            dataStore.edit { it[SHOW_MY_LIST] = show.toString() }
        }

        suspend fun saveShowSuggestions(show: Boolean) {
            dataStore.edit { it[SHOW_SUGGESTIONS] = show.toString() }
        }

        // Home Row Order
        companion object {
            const val DEFAULT_HOME_ROW_ORDER = "continue_watching,my_list,suggestions"
        }

        val homeRowOrder: Flow<List<String>> =
            dataStore.data.map { prefs ->
                val raw = prefs[HOME_ROW_ORDER] ?: DEFAULT_HOME_ROW_ORDER
                raw.split(",").filter { it.isNotBlank() }
            }

        suspend fun saveHomeRowOrder(order: List<String>) {
            dataStore.edit { it[HOME_ROW_ORDER] = order.joinToString(",") }
        }

        suspend fun saveAutoCheckUpdates(enabled: Boolean) {
            dataStore.edit { it[AUTO_CHECK_UPDATES] = enabled.toString() }
        }

        suspend fun saveToken(token: String) {
            // Use SecurePreferencesManager for encrypted storage
            securePrefs.savePlexToken(token)
        }

        suspend fun saveClientId(id: String) {
            // Use SecurePreferencesManager for encrypted storage
            securePrefs.saveClientId(id)
        }

        suspend fun saveUser(
            uuid: String,
            name: String,
        ) {
            dataStore.edit { preferences ->
                preferences[CURRENT_USER_UUID] = uuid
                preferences[CURRENT_USER_NAME] = name
            }
        }

        suspend fun saveShowHeroSection(show: Boolean) {
            dataStore.edit { preferences ->
                preferences[SHOW_HERO_SECTION] = show.toString()
            }
        }

        suspend fun saveEpisodePosterMode(mode: String) {
            dataStore.edit { preferences ->
                preferences[EPISODE_POSTER_MODE] = mode
            }
        }

        suspend fun saveAppTheme(theme: String) {
            dataStore.edit { preferences ->
                preferences[APP_THEME] = theme
            }
        }

        suspend fun saveShowYearOnCards(show: Boolean) {
            dataStore.edit { preferences ->
                preferences[SHOW_YEAR_ON_CARDS] = show.toString()
            }
        }

        suspend fun saveGridColumnsCount(count: Int) {
            dataStore.edit { preferences ->
                preferences[GRID_COLUMNS_COUNT] = count.toString()
            }
        }

        suspend fun clearToken() {
            // Use SecurePreferencesManager
            securePrefs.clearPlexToken()
        }

        suspend fun clearUser() {
            dataStore.edit { preferences ->
                preferences.remove(CURRENT_USER_UUID)
                preferences.remove(CURRENT_USER_NAME)
            }
        }

        suspend fun saveVideoQuality(quality: String) {
            dataStore.edit { preferences ->
                preferences[SERVER_QUALITY] = quality
            }
        }

        suspend fun saveCacheEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[CACHE_ENABLED] = enabled.toString()
            }
        }

        suspend fun saveDefaultServer(server: String) {
            dataStore.edit { preferences ->
                preferences[DEFAULT_SERVER] = server
            }
        }

        suspend fun savePlayerEngine(engine: String) {
            dataStore.edit { preferences ->
                preferences[PLAYER_ENGINE] = engine
            }
        }

        suspend fun saveDeinterlaceMode(mode: String) {
            dataStore.edit { preferences ->
                preferences[DEINTERLACE_MODE] = mode
            }
        }

        suspend fun saveAutoPlayNext(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[AUTO_PLAY_NEXT] = enabled.toString()
            }
        }

        suspend fun saveSkipIntroMode(mode: String) {
            dataStore.edit { preferences ->
                preferences[SKIP_INTRO_MODE] = mode
            }
        }

        suspend fun saveSkipCreditsMode(mode: String) {
            dataStore.edit { preferences ->
                preferences[SKIP_CREDITS_MODE] = mode
            }
        }

        suspend fun saveThemeSongEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[THEME_SONG_ENABLED] = enabled.toString()
            }
        }

        suspend fun saveThemeSongVolume(volume: Float) {
            dataStore.edit { preferences ->
                preferences[THEME_SONG_VOLUME] = volume.toString()
            }
        }

        suspend fun saveLastSyncTime(time: Long) {
            dataStore.edit { preferences ->
                preferences[LAST_SYNC_TIME] = time.toString()
            }
        }

        suspend fun saveFirstSyncComplete(complete: Boolean) {
            dataStore.edit { preferences ->
                preferences[FIRST_SYNC_COMPLETE] = complete.toString()
            }
        }

        suspend fun toggleServerExclusion(serverId: String) {
            dataStore.edit { preferences ->
                val current = preferences[EXCLUDED_SERVER_IDS] ?: emptySet()
                if (current.contains(serverId)) {
                    preferences[EXCLUDED_SERVER_IDS] = current - serverId
                } else {
                    preferences[EXCLUDED_SERVER_IDS] = current + serverId
                }
            }
        }

        private val CACHED_CONNECTIONS = stringPreferencesKey("cached_connections")

        private val PREF_AUDIO_LANG = stringPreferencesKey("pref_audio_lang")
        private val PREF_SUBTITLE_LANG = stringPreferencesKey("pref_subtitle_lang")

        val preferredAudioLanguage: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[PREF_AUDIO_LANG] }

        val preferredSubtitleLanguage: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[PREF_SUBTITLE_LANG] }

        suspend fun savePreferredAudioLanguage(lang: String?) {
            dataStore.edit { preferences ->
                if (lang != null) {
                    preferences[PREF_AUDIO_LANG] = lang
                } else {
                    preferences.remove(PREF_AUDIO_LANG)
                }
            }
        }

        suspend fun savePreferredSubtitleLanguage(lang: String?) {
            dataStore.edit { preferences ->
                if (lang != null) {
                    preferences[PREF_SUBTITLE_LANG] = lang
                } else {
                    preferences.remove(PREF_SUBTITLE_LANG)
                }
            }
        }

        suspend fun saveSelectedLibraryIds(ids: Set<String>) {
            dataStore.edit { preferences ->
                preferences[SELECTED_LIBRARY_IDS] = ids
            }
        }

        suspend fun saveSelectedXtreamCategoryIds(ids: Set<String>) {
            dataStore.edit { preferences ->
                preferences[SELECTED_XTREAM_CATEGORY_IDS] = ids
            }
        }

        suspend fun saveLibrarySelectionComplete(complete: Boolean) {
            dataStore.edit { preferences ->
                preferences[LIBRARY_SELECTION_COMPLETE] = complete.toString()
            }
        }

        suspend fun saveIptvPlaylistUrl(url: String) {
            dataStore.edit { preferences ->
                preferences[IPTV_PLAYLIST_URL] = url
            }
        }

        suspend fun saveTmdbApiKey(key: String) {
            // Use SecurePreferencesManager for encrypted storage
            securePrefs.saveTmdbApiKey(key)
        }

        suspend fun saveOmdbApiKey(key: String) {
            // Use SecurePreferencesManager for encrypted storage
            securePrefs.saveOmdbApiKey(key)
        }

        /**
         * Persisted connection URLs per server (serverId -> baseUrl).
         * Restored on cold start so ConnectionManager has working URLs immediately.
         */
        val cachedConnections: Flow<Map<String, String>>
            get() = dataStore.data.map { prefs ->
                val json = prefs[CACHED_CONNECTIONS] ?: return@map emptyMap()
                try {
                    // Simple key=value pairs separated by "|", e.g. "id1=url1|id2=url2"
                    json.split("|").filter { it.contains("=") }.associate { entry ->
                        val (key, value) = entry.split("=", limit = 2)
                        key to value
                    }
                } catch (e: Exception) {
                    emptyMap()
                }
            }

        suspend fun saveCachedConnections(connections: Map<String, String>) {
            dataStore.edit { prefs ->
                val serialized = connections.entries.joinToString("|") { "${it.key}=${it.value}" }
                prefs[CACHED_CONNECTIONS] = serialized
            }
        }

        // Rating Sync Configuration Save Functions
        suspend fun saveRatingSyncSource(source: String) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_SOURCE] = source
            }
        }

        suspend fun saveRatingSyncDelay(delayMs: Long) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_DELAY] = delayMs.toString()
            }
        }

        suspend fun saveRatingSyncBatchingEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_BATCHING_ENABLED] = enabled.toString()
            }
        }

        suspend fun saveRatingSyncDailyLimit(limit: Int) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_DAILY_LIMIT] = limit.toString()
            }
        }

        suspend fun saveRatingSyncProgressSeries(progress: Int) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_PROGRESS_SERIES] = progress.toString()
            }
        }

        suspend fun saveRatingSyncProgressMovies(progress: Int) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_PROGRESS_MOVIES] = progress.toString()
            }
        }

        suspend fun saveRatingSyncLastRunDate(date: String) {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_LAST_RUN_DATE] = date
            }
        }

        suspend fun resetRatingSyncProgress() {
            dataStore.edit { preferences ->
                preferences[RATING_SYNC_PROGRESS_SERIES] = "0"
                preferences[RATING_SYNC_PROGRESS_MOVIES] = "0"
            }
        }

        suspend fun setTvChannelsEnabled(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[TV_CHANNELS_ENABLED] = enabled.toString()
            }
        }

        // Subtitle Style Save Functions
        suspend fun saveSubtitleFontSize(size: Int) {
            dataStore.edit { it[SUBTITLE_FONT_SIZE] = size.toString() }
        }

        suspend fun saveSubtitleFontColor(color: Long) {
            dataStore.edit { it[SUBTITLE_FONT_COLOR] = color.toString() }
        }

        suspend fun saveSubtitleBgColor(color: Long) {
            dataStore.edit { it[SUBTITLE_BG_COLOR] = color.toString() }
        }

        suspend fun saveSubtitleEdgeType(type: Int) {
            dataStore.edit { it[SUBTITLE_EDGE_TYPE] = type.toString() }
        }

        suspend fun saveSubtitleEdgeColor(color: Long) {
            dataStore.edit { it[SUBTITLE_EDGE_COLOR] = color.toString() }
        }

        // Screensaver Preferences Flows
        val screensaverEnabled: Flow<Boolean> =
            dataStore.data.map { it[SCREENSAVER_ENABLED]?.toBoolean() ?: true }

        val screensaverIntervalSeconds: Flow<Int> =
            dataStore.data.map { it[SCREENSAVER_INTERVAL_SECONDS]?.toIntOrNull() ?: 15 }

        val screensaverShowClock: Flow<Boolean> =
            dataStore.data.map { it[SCREENSAVER_SHOW_CLOCK]?.toBoolean() ?: true }

        suspend fun saveScreensaverEnabled(enabled: Boolean) {
            dataStore.edit { it[SCREENSAVER_ENABLED] = enabled.toString() }
        }

        suspend fun saveScreensaverIntervalSeconds(seconds: Int) {
            dataStore.edit { it[SCREENSAVER_INTERVAL_SECONDS] = seconds.toString() }
        }

        suspend fun saveScreensaverShowClock(show: Boolean) {
            dataStore.edit { it[SCREENSAVER_SHOW_CLOCK] = show.toString() }
        }

        // Library Filter Preferences Flows
        val librarySort: Flow<String> =
            dataStore.data.map { it[LIBRARY_SORT] ?: "Title" }

        val librarySortDescending: Flow<Boolean> =
            dataStore.data.map { it[LIBRARY_SORT_DESCENDING]?.toBoolean() ?: false }

        val libraryGenre: Flow<String?> =
            dataStore.data.map { it[LIBRARY_GENRE] }

        val libraryServerFilter: Flow<String?> =
            dataStore.data.map { it[LIBRARY_SERVER_FILTER] }

        suspend fun saveLibrarySort(sort: String, isDescending: Boolean) {
            dataStore.edit { preferences ->
                preferences[LIBRARY_SORT] = sort
                preferences[LIBRARY_SORT_DESCENDING] = isDescending.toString()
            }
        }

        suspend fun saveLibraryGenre(genre: String?) {
            dataStore.edit { preferences ->
                if (genre != null) {
                    preferences[LIBRARY_GENRE] = genre
                } else {
                    preferences.remove(LIBRARY_GENRE)
                }
            }
        }

        suspend fun saveLibraryServerFilter(serverName: String?) {
            dataStore.edit { preferences ->
                if (serverName != null) {
                    preferences[LIBRARY_SERVER_FILTER] = serverName
                } else {
                    preferences.remove(LIBRARY_SERVER_FILTER)
                }
            }
        }

        suspend fun clearAll() {
            dataStore.edit { it.clear() }
            securePrefs.clearAll()
        }
    }
