package com.chakir.plexhubtv.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestionnaire de préférences sécurisées utilisant EncryptedSharedPreferences.
 *
 * Utilise AES-256-GCM pour chiffrer les clés et valeurs sensibles (tokens, API keys).
 * Les données sont stockées dans un fichier chiffré avec une clé master gérée par Android Keystore.
 *
 * Thread-safe avec synchronization sur les opérations d'écriture.
 */
@Singleton
class SecurePreferencesManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private companion object {
            const val PREFS_FILE = "plex_secure_prefs"
            const val KEY_PLEX_TOKEN = "plex_token"
            const val KEY_CLIENT_ID = "client_id"
            const val KEY_TMDB_API_KEY = "tmdb_api_key"
            const val KEY_OMDB_API_KEY = "omdb_api_key"
        }

        private val encryptedPrefs: SharedPreferences by lazy {
            try {
                val masterKey =
                    MasterKey
                        .Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular SharedPreferences")
                // Fallback to regular SharedPreferences if encryption fails (shouldn't happen on API 27+)
                context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            }
        }

        // Flows for reactive data
        private val _plexToken = MutableStateFlow<String?>(null)
        val plexToken: Flow<String?> = _plexToken.asStateFlow()

        private val _clientId = MutableStateFlow<String?>(null)
        val clientId: Flow<String?> = _clientId.asStateFlow()

        private val _tmdbApiKey = MutableStateFlow<String?>(null)
        val tmdbApiKey: Flow<String?> = _tmdbApiKey.asStateFlow()

        private val _omdbApiKey = MutableStateFlow<String?>(null)
        val omdbApiKey: Flow<String?> = _omdbApiKey.asStateFlow()

        init {
            // Load initial values
            _plexToken.value = encryptedPrefs.getString(KEY_PLEX_TOKEN, null)
            _clientId.value = encryptedPrefs.getString(KEY_CLIENT_ID, null)
            _tmdbApiKey.value = encryptedPrefs.getString(KEY_TMDB_API_KEY, null)
            _omdbApiKey.value = encryptedPrefs.getString(KEY_OMDB_API_KEY, null)
        }

        /**
         * Saves the Plex authentication token securely.
         * Thread-safe with synchronized block.
         */
        fun savePlexToken(token: String) {
            synchronized(this) {
                encryptedPrefs.edit().putString(KEY_PLEX_TOKEN, token).apply()
                _plexToken.value = token
                Timber.d("Plex token saved securely")
            }
        }

        /**
         * Retrieves the Plex token synchronously.
         */
        fun getPlexToken(): String? {
            return encryptedPrefs.getString(KEY_PLEX_TOKEN, null)
        }

        /**
         * Clears the Plex token.
         */
        fun clearPlexToken() {
            synchronized(this) {
                encryptedPrefs.edit().remove(KEY_PLEX_TOKEN).apply()
                _plexToken.value = null
                Timber.d("Plex token cleared")
            }
        }

        /**
         * Saves the Client ID securely.
         */
        fun saveClientId(clientId: String) {
            synchronized(this) {
                encryptedPrefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
                _clientId.value = clientId
            }
        }

        /**
         * Retrieves the Client ID synchronously.
         */
        fun getClientId(): String? {
            return encryptedPrefs.getString(KEY_CLIENT_ID, null)
        }

        /**
         * Saves TMDB API key securely.
         */
        fun saveTmdbApiKey(key: String) {
            synchronized(this) {
                if (key.isBlank()) {
                    encryptedPrefs.edit().remove(KEY_TMDB_API_KEY).apply()
                    _tmdbApiKey.value = null
                } else {
                    encryptedPrefs.edit().putString(KEY_TMDB_API_KEY, key).apply()
                    _tmdbApiKey.value = key
                }
            }
        }

        /**
         * Retrieves TMDB API key synchronously.
         */
        fun getTmdbApiKey(): String? {
            return encryptedPrefs.getString(KEY_TMDB_API_KEY, null)
        }

        /**
         * Saves OMDB API key securely.
         */
        fun saveOmdbApiKey(key: String) {
            synchronized(this) {
                if (key.isBlank()) {
                    encryptedPrefs.edit().remove(KEY_OMDB_API_KEY).apply()
                    _omdbApiKey.value = null
                } else {
                    encryptedPrefs.edit().putString(KEY_OMDB_API_KEY, key).apply()
                    _omdbApiKey.value = key
                }
            }
        }

        /**
         * Retrieves OMDB API key synchronously.
         */
        fun getOmdbApiKey(): String? {
            return encryptedPrefs.getString(KEY_OMDB_API_KEY, null)
        }

        /**
         * Clears all secure preferences.
         */
        fun clearAll() {
            synchronized(this) {
                encryptedPrefs.edit().clear().apply()
                _plexToken.value = null
                _clientId.value = null
                _tmdbApiKey.value = null
                _omdbApiKey.value = null
                Timber.d("All secure preferences cleared")
            }
        }
    }
