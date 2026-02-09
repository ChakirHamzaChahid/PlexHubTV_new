package com.chakir.plexhubtv.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Wrapper autour de DataStore Preferences pour un accès typé aux paramètres de l'application.
 *
 * Gère :
 * - Authentification (Plex Token, Client ID)
 * - Préférences UI (Thème, Affichage Hero)
 * - Configuration Lecture (Qualité, Moteur)
 * - État de synchro (Dernière synchro)
 */
class SettingsDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        private val PLEX_TOKEN = stringPreferencesKey("plex_token")
        private val CLIENT_ID = stringPreferencesKey("client_id")
        private val SERVER_QUALITY = stringPreferencesKey("server_quality")
        private val CURRENT_USER_UUID = stringPreferencesKey("current_user_uuid")
        private val CURRENT_USER_NAME = stringPreferencesKey("current_user_name")
        private val SHOW_HERO_SECTION = stringPreferencesKey("show_hero_section")
        private val EPISODE_POSTER_MODE = stringPreferencesKey("episode_poster_mode")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val CACHE_ENABLED = stringPreferencesKey("cache_enabled")
        private val DEFAULT_SERVER = stringPreferencesKey("default_server")
        private val PLAYER_ENGINE = stringPreferencesKey("player_engine")
        private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val FIRST_SYNC_COMPLETE = stringPreferencesKey("first_sync_complete")
        private val EXCLUDED_SERVER_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("excluded_server_ids")
        private val IPTV_PLAYLIST_URL = stringPreferencesKey("iptv_playlist_url")
        private val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
        private val OMDB_API_KEY = stringPreferencesKey("omdb_api_key")

        val plexToken: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[PLEX_TOKEN] }

        val clientId: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[CLIENT_ID] }

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

        val videoQuality: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[SERVER_QUALITY] ?: "Original" }

        val isCacheEnabled: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[CACHE_ENABLED]?.toBoolean() ?: true }

        val defaultServer: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[DEFAULT_SERVER] ?: "MyServer" }

        val playerEngine: Flow<String> =
            dataStore.data
                .map { preferences -> preferences[PLAYER_ENGINE] ?: "ExoPlayer" }

        val lastSyncTime: Flow<Long> =
            dataStore.data
                .map { preferences -> preferences[LAST_SYNC_TIME]?.toLongOrNull() ?: 0L }

        val isFirstSyncComplete: Flow<Boolean> =
            dataStore.data
                .map { preferences -> preferences[FIRST_SYNC_COMPLETE]?.toBoolean() ?: false }

        val excludedServerIds: Flow<Set<String>> =
            dataStore.data
                .map { preferences -> preferences[EXCLUDED_SERVER_IDS] ?: emptySet() }

        val iptvPlaylistUrl: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[IPTV_PLAYLIST_URL] }

        val tmdbApiKey: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[TMDB_API_KEY] }

        val omdbApiKey: Flow<String?> =
            dataStore.data
                .map { preferences -> preferences[OMDB_API_KEY] }

        suspend fun saveToken(token: String) {
            dataStore.edit { preferences ->
                preferences[PLEX_TOKEN] = token
            }
        }

        suspend fun saveClientId(id: String) {
            dataStore.edit { preferences ->
                preferences[CLIENT_ID] = id
            }
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

        suspend fun clearToken() {
            dataStore.edit { preferences ->
                preferences.remove(PLEX_TOKEN)
            }
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

        suspend fun saveIptvPlaylistUrl(url: String) {
            dataStore.edit { preferences ->
                preferences[IPTV_PLAYLIST_URL] = url
            }
        }

        suspend fun saveTmdbApiKey(key: String) {
            dataStore.edit { preferences ->
                if (key.isBlank()) {
                    preferences.remove(TMDB_API_KEY)
                } else {
                    preferences[TMDB_API_KEY] = key
                }
            }
        }

        suspend fun saveOmdbApiKey(key: String) {
            dataStore.edit { preferences ->
                if (key.isBlank()) {
                    preferences.remove(OMDB_API_KEY)
                } else {
                    preferences[OMDB_API_KEY] = key
                }
            }
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

        suspend fun clearAll() {
            dataStore.edit { it.clear() }
        }
    }
