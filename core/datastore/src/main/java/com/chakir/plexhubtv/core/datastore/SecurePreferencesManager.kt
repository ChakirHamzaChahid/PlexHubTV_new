package com.chakir.plexhubtv.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
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

        private val _isEncryptionDegraded = MutableStateFlow(false)

        /** True when encryption init failed — UI should prompt re-login. */
        val isEncryptionDegraded: StateFlow<Boolean> = _isEncryptionDegraded.asStateFlow()

        /**
         * Encrypted prefs or null if encryption is unavailable.
         * On first failure: deletes corrupted file and retries once (handles backup/restore corruption).
         * On second failure: stays null — all read/write ops become no-ops to prevent plaintext storage.
         */
        private val encryptedPrefs: SharedPreferences? by lazy {
            createEncryptedPrefs()
                ?: run {
                    // Recovery: delete corrupted prefs file and retry once
                    Timber.w("Attempting recovery: deleting corrupted prefs file")
                    deletePrefsFile()
                    createEncryptedPrefs()
                }
                ?: run {
                    Timber.e("EncryptedSharedPreferences irrecoverable — secrets storage disabled")
                    _isEncryptionDegraded.value = true
                    null
                }
        }

        private fun createEncryptedPrefs(): SharedPreferences? =
            try {
                val masterKey = MasterKey.Builder(context)
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
                Timber.e(e, "Failed to create EncryptedSharedPreferences")
                null
            }

        private fun deletePrefsFile() {
            try {
                // EncryptedSharedPreferences uses the file name directly in the shared_prefs directory
                val prefsFile = File(context.filesDir.parentFile, "shared_prefs/$PREFS_FILE.xml")
                if (prefsFile.exists()) prefsFile.delete()
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete corrupted prefs file")
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
            // Load initial values (null-safe: returns null when degraded)
            _plexToken.value = encryptedPrefs?.getString(KEY_PLEX_TOKEN, null)
            _clientId.value = encryptedPrefs?.getString(KEY_CLIENT_ID, null)
            _tmdbApiKey.value = encryptedPrefs?.getString(KEY_TMDB_API_KEY, null)
            _omdbApiKey.value = encryptedPrefs?.getString(KEY_OMDB_API_KEY, null)
        }

        fun savePlexToken(token: String) {
            synchronized(this) {
                encryptedPrefs?.edit()?.putString(KEY_PLEX_TOKEN, token)?.apply()
                    ?: Timber.w("Cannot save Plex token: encryption unavailable")
                _plexToken.value = token
            }
        }

        fun getPlexToken(): String? =
            encryptedPrefs?.getString(KEY_PLEX_TOKEN, null)

        fun clearPlexToken() {
            synchronized(this) {
                encryptedPrefs?.edit()?.remove(KEY_PLEX_TOKEN)?.apply()
                _plexToken.value = null
            }
        }

        fun saveClientId(clientId: String) {
            synchronized(this) {
                encryptedPrefs?.edit()?.putString(KEY_CLIENT_ID, clientId)?.apply()
                    ?: Timber.w("Cannot save client ID: encryption unavailable")
                _clientId.value = clientId
            }
        }

        fun getClientId(): String? =
            encryptedPrefs?.getString(KEY_CLIENT_ID, null)

        fun saveTmdbApiKey(key: String) {
            synchronized(this) {
                if (key.isBlank()) {
                    encryptedPrefs?.edit()?.remove(KEY_TMDB_API_KEY)?.apply()
                    _tmdbApiKey.value = null
                } else {
                    encryptedPrefs?.edit()?.putString(KEY_TMDB_API_KEY, key)?.apply()
                        ?: Timber.w("Cannot save TMDB key: encryption unavailable")
                    _tmdbApiKey.value = key
                }
            }
        }

        fun getTmdbApiKey(): String? =
            encryptedPrefs?.getString(KEY_TMDB_API_KEY, null)

        fun saveOmdbApiKey(key: String) {
            synchronized(this) {
                if (key.isBlank()) {
                    encryptedPrefs?.edit()?.remove(KEY_OMDB_API_KEY)?.apply()
                    _omdbApiKey.value = null
                } else {
                    encryptedPrefs?.edit()?.putString(KEY_OMDB_API_KEY, key)?.apply()
                        ?: Timber.w("Cannot save OMDB key: encryption unavailable")
                    _omdbApiKey.value = key
                }
            }
        }

        fun getOmdbApiKey(): String? =
            encryptedPrefs?.getString(KEY_OMDB_API_KEY, null)

        fun putSecret(key: String, value: String) {
            synchronized(this) {
                encryptedPrefs?.edit()?.putString(key, value)?.apply()
                    ?: Timber.w("Cannot store secret '$key': encryption unavailable")
            }
        }

        fun getSecret(key: String): String? =
            encryptedPrefs?.getString(key, null)

        fun removeSecret(key: String) {
            synchronized(this) {
                encryptedPrefs?.edit()?.remove(key)?.apply()
            }
        }

        fun clearAll() {
            synchronized(this) {
                encryptedPrefs?.edit()?.clear()?.apply()
                _plexToken.value = null
                _clientId.value = null
                _tmdbApiKey.value = null
                _omdbApiKey.value = null
                Timber.d("All secure preferences cleared")
            }
        }
    }
