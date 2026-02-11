package com.chakir.plexhubtv.di.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Extension Kotlin pour initialiser le DataStore "settings" sur le Context.
 * Singleton par défaut pour l'accès aux préférences de l'application.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
